"""Database connection pooling and management."""
import asyncio
from typing import Dict, Any, Optional, List, Union
from contextlib import asynccontextmanager
from sqlalchemy import create_engine, text, pool
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, AsyncEngine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import NullPool, QueuePool
import aiomysql
import asyncpg
from loguru import logger
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type
from .config import DatabaseConfig


class DatabaseConnectionPool:
    """Manages database connection pooling with support for multiple database types."""
    
    def __init__(self, config: DatabaseConfig, db_type: str = "mysql"):
        self.config = config
        self.db_type = db_type
        self._engine: Optional[AsyncEngine] = None
        self._sync_engine = None
        self._session_factory = None
        self._pool_metrics = {
            'connections_created': 0,
            'connections_closed': 0,
            'connections_in_use': 0,
            'connection_errors': 0,
            'queries_executed': 0,
            'query_errors': 0
        }
    
    async def initialize(self):
        """Initialize the connection pool."""
        try:
            if self.db_type == "mysql":
                connection_string = (
                    f"mysql+aiomysql://{self.config.username}:{self.config.password}"
                    f"@{self.config.host}:{self.config.port}/{self.config.database}"
                )
            elif self.db_type == "postgresql":
                connection_string = (
                    f"postgresql+asyncpg://{self.config.username}:{self.config.password}"
                    f"@{self.config.host}:{self.config.port}/{self.config.database}"
                )
            else:
                raise ValueError(f"Unsupported database type: {self.db_type}")
            
            # Create async engine with connection pooling
            self._engine = create_async_engine(
                connection_string,
                pool_size=self.config.pool_size,
                max_overflow=self.config.max_overflow,
                pool_timeout=self.config.pool_timeout,
                pool_recycle=self.config.pool_recycle,
                pool_pre_ping=True,  # Verify connections before using
                echo=False,
                poolclass=QueuePool
            )
            
            # Create session factory
            self._session_factory = sessionmaker(
                self._engine,
                class_=AsyncSession,
                expire_on_commit=False
            )
            
            # Test connection
            async with self._engine.connect() as conn:
                await conn.execute(text("SELECT 1"))
            
            logger.info(f"Database connection pool initialized for {self.db_type}")
            self._pool_metrics['connections_created'] = self.config.pool_size
            
        except Exception as e:
            logger.error(f"Failed to initialize database connection pool: {e}")
            raise
    
    async def close(self):
        """Close all connections in the pool."""
        if self._engine:
            await self._engine.dispose()
            self._pool_metrics['connections_closed'] = self._pool_metrics['connections_created']
            logger.info("Database connection pool closed")
    
    @asynccontextmanager
    async def get_session(self):
        """Get a database session from the pool."""
        if not self._session_factory:
            raise RuntimeError("Database connection pool not initialized")
        
        async with self._session_factory() as session:
            self._pool_metrics['connections_in_use'] += 1
            try:
                yield session
                await session.commit()
            except Exception as e:
                await session.rollback()
                self._pool_metrics['connection_errors'] += 1
                raise
            finally:
                self._pool_metrics['connections_in_use'] -= 1
    
    @retry(
        stop=stop_after_attempt(3),
        wait=wait_exponential(multiplier=1, min=1, max=10),
        retry=retry_if_exception_type((aiomysql.Error, asyncpg.PostgresError))
    )
    async def execute_query(self, query: str, params: Optional[Dict[str, Any]] = None) -> List[Dict[str, Any]]:
        """Execute a query with automatic retry and connection management."""
        self._pool_metrics['queries_executed'] += 1
        
        try:
            async with self.get_session() as session:
                result = await session.execute(text(query), params or {})
                
                # For SELECT queries, fetch all results
                if query.strip().upper().startswith('SELECT'):
                    rows = result.fetchall()
                    columns = result.keys()
                    return [dict(zip(columns, row)) for row in rows]
                else:
                    # For non-SELECT queries, return affected row count
                    return [{'affected_rows': result.rowcount}]
                    
        except Exception as e:
            self._pool_metrics['query_errors'] += 1
            logger.error(f"Query execution failed: {e}")
            raise
    
    async def execute_many(self, query: str, params_list: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """Execute multiple queries in a batch."""
        results = []
        async with self.get_session() as session:
            for params in params_list:
                try:
                    result = await session.execute(text(query), params)
                    if query.strip().upper().startswith('SELECT'):
                        rows = result.fetchall()
                        columns = result.keys()
                        results.extend([dict(zip(columns, row)) for row in rows])
                    else:
                        results.append({'affected_rows': result.rowcount})
                except Exception as e:
                    logger.error(f"Batch query execution failed for params {params}: {e}")
                    results.append({'error': str(e)})
        
        return results
    
    def get_pool_status(self) -> Dict[str, Any]:
        """Get current connection pool status."""
        status = {
            'metrics': self._pool_metrics,
            'config': {
                'pool_size': self.config.pool_size,
                'max_overflow': self.config.max_overflow,
                'total_size': self.config.pool_size + self.config.max_overflow
            }
        }
        
        if self._engine and hasattr(self._engine.pool, 'size'):
            status['runtime'] = {
                'size': self._engine.pool.size(),
                'checked_in': self._engine.pool.checkedin(),
                'checked_out': self._engine.pool.checkedout(),
                'overflow': self._engine.pool.overflow(),
                'total': self._engine.pool.size() + self._engine.pool.overflow()
            }
        
        return status


class ConnectionPoolManager:
    """Manages multiple database connection pools for different databases."""
    
    def __init__(self):
        self.pools: Dict[str, DatabaseConnectionPool] = {}
        self._lock = asyncio.Lock()
    
    async def add_pool(self, name: str, config: DatabaseConfig, db_type: str = "mysql"):
        """Add a new connection pool."""
        async with self._lock:
            if name in self.pools:
                logger.warning(f"Connection pool '{name}' already exists")
                return
            
            pool = DatabaseConnectionPool(config, db_type)
            await pool.initialize()
            self.pools[name] = pool
            logger.info(f"Added connection pool '{name}'")
    
    async def remove_pool(self, name: str):
        """Remove and close a connection pool."""
        async with self._lock:
            if name in self.pools:
                await self.pools[name].close()
                del self.pools[name]
                logger.info(f"Removed connection pool '{name}'")
    
    def get_pool(self, name: str) -> DatabaseConnectionPool:
        """Get a connection pool by name."""
        if name not in self.pools:
            raise KeyError(f"Connection pool '{name}' not found")
        return self.pools[name]
    
    async def close_all(self):
        """Close all connection pools."""
        async with self._lock:
            for name, pool in self.pools.items():
                await pool.close()
            self.pools.clear()
            logger.info("All connection pools closed")
    
    def get_all_status(self) -> Dict[str, Any]:
        """Get status of all connection pools."""
        return {
            name: pool.get_pool_status()
            for name, pool in self.pools.items()
        }