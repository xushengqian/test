"""
配置管理模块
"""
import os
from typing import Optional, Dict, Any
from pydantic import BaseSettings, Field
from dotenv import load_dotenv

load_dotenv()


class DatabaseConfig(BaseSettings):
    """数据库配置"""
    host: str = Field(default="localhost", env="DB_HOST")
    port: int = Field(default=3306, env="DB_PORT")
    username: str = Field(default="root", env="DB_USERNAME")
    password: str = Field(default="", env="DB_PASSWORD")
    database: str = Field(default="metric_scheduler", env="DB_DATABASE")
    charset: str = Field(default="utf8mb4", env="DB_CHARSET")
    pool_size: int = Field(default=20, env="DB_POOL_SIZE")
    max_overflow: int = Field(default=30, env="DB_MAX_OVERFLOW")
    pool_timeout: int = Field(default=30, env="DB_POOL_TIMEOUT")
    pool_recycle: int = Field(default=3600, env="DB_POOL_RECYCLE")

    @property
    def url(self) -> str:
        return f"mysql+pymysql://{self.username}:{self.password}@{self.host}:{self.port}/{self.database}?charset={self.charset}"


class RedisConfig(BaseSettings):
    """Redis配置"""
    host: str = Field(default="localhost", env="REDIS_HOST")
    port: int = Field(default=6379, env="REDIS_PORT")
    password: Optional[str] = Field(default=None, env="REDIS_PASSWORD")
    db: int = Field(default=0, env="REDIS_DB")
    max_connections: int = Field(default=50, env="REDIS_MAX_CONNECTIONS")

    @property
    def url(self) -> str:
        auth = f":{self.password}@" if self.password else ""
        return f"redis://{auth}{self.host}:{self.port}/{self.db}"


class SchedulerConfig(BaseSettings):
    """调度器配置"""
    enabled: bool = Field(default=True, env="SCHEDULER_ENABLED")
    scan_interval: int = Field(default=10, env="SCHEDULER_SCAN_INTERVAL")
    max_concurrent_tasks: int = Field(default=100, env="SCHEDULER_MAX_CONCURRENT_TASKS")
    task_timeout: int = Field(default=300, env="SCHEDULER_TASK_TIMEOUT")
    retry_count: int = Field(default=3, env="SCHEDULER_RETRY_COUNT")
    queue_batch_size: int = Field(default=50, env="SCHEDULER_QUEUE_BATCH_SIZE")
    worker_heartbeat_interval: int = Field(default=30, env="SCHEDULER_WORKER_HEARTBEAT_INTERVAL")
    worker_timeout: int = Field(default=120, env="SCHEDULER_WORKER_TIMEOUT")


class ExecutorConfig(BaseSettings):
    """执行器配置"""
    max_workers: int = Field(default=10, env="EXECUTOR_MAX_WORKERS")
    default_timeout: int = Field(default=300, env="EXECUTOR_DEFAULT_TIMEOUT")
    max_retry_count: int = Field(default=3, env="EXECUTOR_MAX_RETRY_COUNT")
    result_cache_ttl: int = Field(default=3600, env="EXECUTOR_RESULT_CACHE_TTL")


class LogConfig(BaseSettings):
    """日志配置"""
    level: str = Field(default="INFO", env="LOG_LEVEL")
    format: str = Field(
        default="{time:YYYY-MM-DD HH:mm:ss} | {level} | {name}:{function}:{line} | {message}",
        env="LOG_FORMAT"
    )
    rotation: str = Field(default="100 MB", env="LOG_ROTATION")
    retention: str = Field(default="30 days", env="LOG_RETENTION")
    log_dir: str = Field(default="logs", env="LOG_DIR")


class APIConfig(BaseSettings):
    """API配置"""
    host: str = Field(default="0.0.0.0", env="API_HOST")
    port: int = Field(default=8000, env="API_PORT")
    debug: bool = Field(default=False, env="API_DEBUG")
    secret_key: str = Field(default="your-secret-key", env="API_SECRET_KEY")
    access_token_expire_minutes: int = Field(default=30, env="API_ACCESS_TOKEN_EXPIRE_MINUTES")


class MonitorConfig(BaseSettings):
    """监控配置"""
    enabled: bool = Field(default=True, env="MONITOR_ENABLED")
    metrics_port: int = Field(default=9090, env="MONITOR_METRICS_PORT")
    health_check_interval: int = Field(default=60, env="MONITOR_HEALTH_CHECK_INTERVAL")


class Config(BaseSettings):
    """主配置类"""
    # 环境配置
    environment: str = Field(default="development", env="ENVIRONMENT")
    debug: bool = Field(default=False, env="DEBUG")
    
    # 节点配置
    node_id: str = Field(default="node-1", env="NODE_ID")
    node_name: str = Field(default="Metric Scheduler Node", env="NODE_NAME")
    
    # 子配置
    database: DatabaseConfig = DatabaseConfig()
    redis: RedisConfig = RedisConfig()
    scheduler: SchedulerConfig = SchedulerConfig()
    executor: ExecutorConfig = ExecutorConfig()
    log: LogConfig = LogConfig()
    api: APIConfig = APIConfig()
    monitor: MonitorConfig = MonitorConfig()

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


# 全局配置实例
config = Config()


def get_config() -> Config:
    """获取配置实例"""
    return config


def update_config(updates: Dict[str, Any]) -> None:
    """更新配置"""
    global config
    for key, value in updates.items():
        if hasattr(config, key):
            setattr(config, key, value)