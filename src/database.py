"""
数据库连接和会话管理
"""
import logging
from contextlib import contextmanager
from typing import Generator, Optional
from sqlalchemy import create_engine, event
from sqlalchemy.orm import sessionmaker, Session
from sqlalchemy.pool import QueuePool
from sqlalchemy.exc import SQLAlchemyError

from .config import get_config
from .models import Base

logger = logging.getLogger(__name__)


class DatabaseManager:
    """数据库管理器"""
    
    def __init__(self):
        self.config = get_config().database
        self.engine = None
        self.SessionLocal = None
        self._initialize()
    
    def _initialize(self):
        """初始化数据库连接"""
        try:
            # 创建数据库引擎
            self.engine = create_engine(
                self.config.url,
                poolclass=QueuePool,
                pool_size=self.config.pool_size,
                max_overflow=self.config.max_overflow,
                pool_timeout=self.config.pool_timeout,
                pool_recycle=self.config.pool_recycle,
                pool_pre_ping=True,  # 连接前检查连接是否有效
                echo=get_config().debug,  # 是否打印SQL语句
            )
            
            # 添加连接事件监听器
            @event.listens_for(self.engine, "connect")
            def set_sqlite_pragma(dbapi_connection, connection_record):
                """设置数据库连接参数"""
                if "mysql" in self.config.url:
                    # MySQL特定设置
                    cursor = dbapi_connection.cursor()
                    cursor.execute("SET SESSION sql_mode='STRICT_TRANS_TABLES'")
                    cursor.execute("SET SESSION time_zone='+00:00'")
                    cursor.close()
            
            # 创建会话工厂
            self.SessionLocal = sessionmaker(
                autocommit=False,
                autoflush=False,
                bind=self.engine
            )
            
            logger.info(f"Database initialized successfully: {self.config.host}:{self.config.port}")
            
        except Exception as e:
            logger.error(f"Failed to initialize database: {e}")
            raise
    
    def create_tables(self):
        """创建数据库表"""
        try:
            Base.metadata.create_all(bind=self.engine)
            logger.info("Database tables created successfully")
        except Exception as e:
            logger.error(f"Failed to create database tables: {e}")
            raise
    
    def drop_tables(self):
        """删除数据库表"""
        try:
            Base.metadata.drop_all(bind=self.engine)
            logger.info("Database tables dropped successfully")
        except Exception as e:
            logger.error(f"Failed to drop database tables: {e}")
            raise
    
    @contextmanager
    def get_session(self) -> Generator[Session, None, None]:
        """获取数据库会话上下文管理器"""
        session = self.SessionLocal()
        try:
            yield session
            session.commit()
        except Exception as e:
            session.rollback()
            logger.error(f"Database session error: {e}")
            raise
        finally:
            session.close()
    
    def get_session_sync(self) -> Session:
        """获取同步数据库会话"""
        return self.SessionLocal()
    
    def health_check(self) -> bool:
        """数据库健康检查"""
        try:
            with self.get_session() as session:
                session.execute("SELECT 1")
                return True
        except Exception as e:
            logger.error(f"Database health check failed: {e}")
            return False
    
    def close(self):
        """关闭数据库连接"""
        if self.engine:
            self.engine.dispose()
            logger.info("Database connections closed")


# 全局数据库管理器实例
db_manager = DatabaseManager()


def get_db() -> Generator[Session, None, None]:
    """FastAPI依赖注入用的数据库会话获取函数"""
    session = db_manager.get_session_sync()
    try:
        yield session
        session.commit()
    except Exception as e:
        session.rollback()
        raise
    finally:
        session.close()


def init_database():
    """初始化数据库"""
    try:
        db_manager.create_tables()
        logger.info("Database initialization completed")
    except Exception as e:
        logger.error(f"Database initialization failed: {e}")
        raise


def close_database():
    """关闭数据库连接"""
    db_manager.close()


class DatabaseService:
    """数据库服务类"""
    
    def __init__(self, session: Optional[Session] = None):
        self.session = session or db_manager.get_session_sync()
        self._should_close = session is None
    
    def __enter__(self):
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        if self._should_close:
            if exc_type:
                self.session.rollback()
            else:
                self.session.commit()
            self.session.close()
    
    def commit(self):
        """提交事务"""
        try:
            self.session.commit()
        except SQLAlchemyError as e:
            self.session.rollback()
            logger.error(f"Failed to commit transaction: {e}")
            raise
    
    def rollback(self):
        """回滚事务"""
        self.session.rollback()
    
    def refresh(self, instance):
        """刷新实例"""
        self.session.refresh(instance)
    
    def add(self, instance):
        """添加实例"""
        self.session.add(instance)
    
    def delete(self, instance):
        """删除实例"""
        self.session.delete(instance)
    
    def query(self, *entities):
        """查询"""
        return self.session.query(*entities)
    
    def execute(self, statement):
        """执行SQL语句"""
        return self.session.execute(statement)
    
    def bulk_insert_mappings(self, mapper, mappings):
        """批量插入"""
        self.session.bulk_insert_mappings(mapper, mappings)
    
    def bulk_update_mappings(self, mapper, mappings):
        """批量更新"""
        self.session.bulk_update_mappings(mapper, mappings)