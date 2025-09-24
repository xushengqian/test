import os
from dotenv import load_dotenv
from pydantic import BaseSettings, Field

# 加载环境变量
load_dotenv()


class MySQLConfig(BaseSettings):
    """MySQL数据库配置"""
    host: str = Field(default="localhost", env="MYSQL_HOST")
    port: int = Field(default=3306, env="MYSQL_PORT")
    user: str = Field(default="root", env="MYSQL_USER")
    password: str = Field(default="", env="MYSQL_PASSWORD")
    database: str = Field(default="metric_scheduler", env="MYSQL_DATABASE")
    
    @property
    def connection_string(self) -> str:
        return f"mysql+pymysql://{self.user}:{self.password}@{self.host}:{self.port}/{self.database}?charset=utf8mb4"


class RedisConfig(BaseSettings):
    """Redis配置"""
    host: str = Field(default="localhost", env="REDIS_HOST")
    port: int = Field(default=6379, env="REDIS_PORT")
    password: str = Field(default="", env="REDIS_PASSWORD")
    db: int = Field(default=0, env="REDIS_DB")


class SchedulerConfig(BaseSettings):
    """调度器配置"""
    max_concurrent_jobs: int = Field(default=10, env="MAX_CONCURRENT_JOBS")
    job_scan_interval: int = Field(default=5, env="JOB_SCAN_INTERVAL")
    job_timeout_check_interval: int = Field(default=60, env="JOB_TIMEOUT_CHECK_INTERVAL")
    default_job_timeout: int = Field(default=3600, env="DEFAULT_JOB_TIMEOUT")
    max_retry_times: int = Field(default=3, env="MAX_RETRY_TIMES")
    retry_interval: int = Field(default=300, env="RETRY_INTERVAL")


class LogConfig(BaseSettings):
    """日志配置"""
    log_level: str = Field(default="INFO", env="LOG_LEVEL")
    log_file: str = Field(default="logs/metric_scheduler.log", env="LOG_FILE")


class Config:
    """全局配置"""
    mysql = MySQLConfig()
    redis = RedisConfig()
    scheduler = SchedulerConfig()
    log = LogConfig()


config = Config()