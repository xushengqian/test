from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession, async_sessionmaker
from sqlalchemy.orm import declarative_base
from sqlalchemy.pool import QueuePool
from sqlalchemy import event
from contextlib import asynccontextmanager
from typing import AsyncGenerator
import asyncio
import time
from loguru import logger

from .config import settings

# 创建数据库引擎，配置连接池
engine = create_async_engine(
    settings.database_url,
    poolclass=QueuePool,
    pool_size=settings.database_pool_size,
    max_overflow=settings.database_max_overflow,
    pool_timeout=settings.database_pool_timeout,
    pool_recycle=settings.database_pool_recycle,
    pool_pre_ping=True,  # 连接前检查连接是否有效
    echo=False,  # 生产环境关闭SQL日志
)

# 创建会话工厂
AsyncSessionLocal = async_sessionmaker(
    engine,
    class_=AsyncSession,
    expire_on_commit=False,
    autoflush=True,
    autocommit=False,
)

Base = declarative_base()


class DatabaseConnectionManager:
    """数据库连接管理器，提供连接池监控和保护"""
    
    def __init__(self):
        self.active_connections = 0
        self.max_connections = settings.database_pool_size + settings.database_max_overflow
        self._connection_semaphore = asyncio.Semaphore(self.max_connections)
        
    async def get_connection_info(self) -> dict:
        """获取连接池信息"""
        pool = engine.pool
        return {
            "pool_size": pool.size(),
            "checked_in": pool.checkedin(),
            "checked_out": pool.checkedout(),
            "overflow": pool.overflow(),
            "invalid": pool.invalid(),
            "active_connections": self.active_connections,
            "max_connections": self.max_connections,
        }
    
    @asynccontextmanager
    async def get_session(self) -> AsyncGenerator[AsyncSession, None]:
        """获取数据库会话，带连接数限制"""
        async with self._connection_semaphore:
            self.active_connections += 1
            start_time = time.time()
            
            try:
                async with AsyncSessionLocal() as session:
                    logger.debug(f"Database session created, active connections: {self.active_connections}")
                    yield session
            except Exception as e:
                logger.error(f"Database session error: {e}")
                raise
            finally:
                self.active_connections -= 1
                duration = time.time() - start_time
                logger.debug(f"Database session closed, duration: {duration:.2f}s, active connections: {self.active_connections}")


# 全局数据库连接管理器
db_manager = DatabaseConnectionManager()


# 数据库会话依赖
async def get_db_session() -> AsyncGenerator[AsyncSession, None]:
    """FastAPI依赖：获取数据库会话"""
    async with db_manager.get_session() as session:
        yield session


# 监听连接池事件
@event.listens_for(engine.sync_engine, "connect")
def set_sqlite_pragma(dbapi_connection, connection_record):
    """连接时设置数据库参数"""
    logger.debug("New database connection established")


@event.listens_for(engine.sync_engine, "checkout")
def receive_checkout(dbapi_connection, connection_record, connection_proxy):
    """连接检出时的回调"""
    logger.debug("Database connection checked out from pool")


@event.listens_for(engine.sync_engine, "checkin")
def receive_checkin(dbapi_connection, connection_record):
    """连接检入时的回调"""
    logger.debug("Database connection checked in to pool")


async def init_database():
    """初始化数据库"""
    try:
        async with engine.begin() as conn:
            # 创建所有表
            await conn.run_sync(Base.metadata.create_all)
        logger.info("Database initialized successfully")
    except Exception as e:
        logger.error(f"Failed to initialize database: {e}")
        raise


async def close_database():
    """关闭数据库连接"""
    try:
        await engine.dispose()
        logger.info("Database connections closed")
    except Exception as e:
        logger.error(f"Error closing database connections: {e}")


# 数据库健康检查
async def check_database_health() -> bool:
    """检查数据库健康状态"""
    try:
        async with db_manager.get_session() as session:
            await session.execute("SELECT 1")
            return True
    except Exception as e:
        logger.error(f"Database health check failed: {e}")
        return False