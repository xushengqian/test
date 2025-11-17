"""
并行指标调度器配置
"""
import os
from dataclasses import dataclass
from typing import Optional


@dataclass
class MySQLConfig:
    """MySQL配置"""
    host: str = os.getenv('MYSQL_HOST', 'localhost')
    port: int = int(os.getenv('MYSQL_PORT', '3306'))
    user: str = os.getenv('MYSQL_USER', 'root')
    password: str = os.getenv('MYSQL_PASSWORD', '')
    database: str = os.getenv('MYSQL_DATABASE', 'metric_scheduler')
    pool_size: int = int(os.getenv('MYSQL_POOL_SIZE', '20'))
    max_overflow: int = int(os.getenv('MYSQL_MAX_OVERFLOW', '40'))
    pool_recycle: int = int(os.getenv('MYSQL_POOL_RECYCLE', '3600'))


@dataclass
class RedisConfig:
    """Redis配置"""
    host: str = os.getenv('REDIS_HOST', 'localhost')
    port: int = int(os.getenv('REDIS_PORT', '6379'))
    password: Optional[str] = os.getenv('REDIS_PASSWORD')
    db: int = int(os.getenv('REDIS_DB', '0'))


@dataclass
class ParallelSchedulerConfig:
    """并行调度器配置"""
    # 作业扫描配置
    job_scan_interval: int = int(os.getenv('JOB_SCAN_INTERVAL', '30'))  # 作业扫描间隔(秒)
    job_scan_workers: int = int(os.getenv('JOB_SCAN_WORKERS', '4'))  # 并行扫描工作线程数
    
    # 作业执行配置
    max_concurrent_jobs: int = int(os.getenv('MAX_CONCURRENT_JOBS', '50'))  # 最大并发作业数
    executor_workers: int = int(os.getenv('EXECUTOR_WORKERS', '20'))  # 执行器工作线程数
    
    # 批处理配置
    batch_size: int = int(os.getenv('BATCH_SIZE', '100'))  # 批量处理大小
    batch_create_workers: int = int(os.getenv('BATCH_CREATE_WORKERS', '5'))  # 批量创建作业的并行度
    
    # 超时和重试配置
    job_timeout_check_interval: int = int(os.getenv('JOB_TIMEOUT_CHECK_INTERVAL', '60'))
    default_job_timeout: int = int(os.getenv('DEFAULT_JOB_TIMEOUT', '3600'))
    max_retry_times: int = int(os.getenv('MAX_RETRY_TIMES', '3'))
    retry_interval: int = int(os.getenv('RETRY_INTERVAL', '300'))
    
    # 分布式锁配置
    lock_timeout: int = int(os.getenv('LOCK_TIMEOUT', '300'))  # 分布式锁超时时间(秒)
    lock_retry_times: int = int(os.getenv('LOCK_RETRY_TIMES', '3'))  # 获取锁重试次数
    
    # 性能监控配置
    enable_metrics: bool = os.getenv('ENABLE_METRICS', 'true').lower() == 'true'
    metrics_interval: int = int(os.getenv('METRICS_INTERVAL', '60'))  # 性能指标收集间隔
    
    # 日志配置
    log_level: str = os.getenv('LOG_LEVEL', 'INFO')
    log_file: str = os.getenv('LOG_FILE', 'logs/parallel_scheduler.log')


@dataclass
class Config:
    """全局配置"""
    mysql: MySQLConfig = MySQLConfig()
    redis: RedisConfig = RedisConfig()
    scheduler: ParallelSchedulerConfig = ParallelSchedulerConfig()


# 全局配置实例
config = Config()
