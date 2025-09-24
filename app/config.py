from pydantic_settings import BaseSettings
from typing import Optional
import os


class Settings(BaseSettings):
    # 数据库配置
    database_url: str = "postgresql://postgres:password@localhost:5432/metrics_db"
    database_pool_size: int = 10
    database_max_overflow: int = 20
    database_pool_timeout: int = 30
    database_pool_recycle: int = 3600
    
    # Redis配置
    redis_url: str = "redis://localhost:6379/0"
    redis_max_connections: int = 50
    
    # 任务调度配置
    max_concurrent_tasks: int = 5  # 最大并发任务数
    task_timeout: int = 300  # 任务超时时间（秒）
    max_retry_attempts: int = 3  # 最大重试次数
    retry_delay: int = 60  # 重试延迟（秒）
    
    # 限流配置
    rate_limit_per_second: int = 10  # 每秒最大请求数
    rate_limit_burst: int = 20  # 突发请求数
    
    # 熔断器配置
    circuit_breaker_failure_threshold: int = 5  # 失败阈值
    circuit_breaker_timeout: int = 60  # 熔断超时时间（秒）
    circuit_breaker_expected_exception: tuple = (Exception,)
    
    # 批处理配置
    batch_size: int = 100  # 批处理大小
    batch_timeout: int = 30  # 批处理超时时间（秒）
    
    # 监控配置
    metrics_port: int = 9090  # Prometheus指标端口
    log_level: str = "INFO"
    log_file: str = "logs/app.log"
    
    # 企业指标配置
    default_metric_frequency: str = "daily"
    metric_execution_history_days: int = 30  # 保留执行历史天数
    
    class Config:
        env_file = ".env"
        case_sensitive = False


# 全局配置实例
settings = Settings()