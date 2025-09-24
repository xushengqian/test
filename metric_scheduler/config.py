"""Configuration management for the metric scheduler."""
import os
from typing import Dict, List, Optional
from pydantic import BaseModel, Field, validator
from dotenv import load_dotenv

load_dotenv()


class DatabaseConfig(BaseModel):
    """Database configuration."""
    host: str = Field(default="localhost")
    port: int = Field(default=3306)
    username: str = Field(default="root")
    password: str = Field(default="")
    database: str = Field(default="metrics")
    pool_size: int = Field(default=10, ge=1, le=100)
    max_overflow: int = Field(default=20, ge=0, le=200)
    pool_timeout: int = Field(default=30, ge=1)
    pool_recycle: int = Field(default=3600, ge=60)
    
    @validator('password')
    def password_must_not_be_empty_in_production(cls, v):
        if os.getenv('ENV') == 'production' and not v:
            raise ValueError('Password cannot be empty in production')
        return v


class RedisConfig(BaseModel):
    """Redis configuration for distributed locking and caching."""
    host: str = Field(default="localhost")
    port: int = Field(default=6379)
    password: Optional[str] = Field(default=None)
    db: int = Field(default=0)
    decode_responses: bool = Field(default=True)
    max_connections: int = Field(default=50)


class RateLimiterConfig(BaseModel):
    """Rate limiter configuration."""
    max_queries_per_second: int = Field(default=100, ge=1)
    max_queries_per_minute: int = Field(default=5000, ge=1)
    max_concurrent_queries: int = Field(default=50, ge=1)
    burst_multiplier: float = Field(default=1.5, ge=1.0, le=3.0)
    
    @validator('max_queries_per_minute')
    def validate_rates(cls, v, values):
        max_per_second = values.get('max_queries_per_second', 100)
        if v < max_per_second * 60:
            raise ValueError('max_queries_per_minute should be at least max_queries_per_second * 60')
        return v


class SchedulerConfig(BaseModel):
    """Scheduler configuration."""
    timezone: str = Field(default="UTC")
    max_instances: int = Field(default=3, ge=1)
    misfire_grace_time: int = Field(default=60, ge=1)
    coalesce: bool = Field(default=True)
    max_workers: int = Field(default=10, ge=1)
    job_defaults: Dict = Field(default_factory=lambda: {
        'coalesce': True,
        'max_instances': 3,
        'misfire_grace_time': 60
    })


class MetricConfig(BaseModel):
    """Individual metric configuration."""
    name: str
    query: str
    schedule: str  # Cron expression
    timeout: int = Field(default=300, ge=1)
    priority: int = Field(default=5, ge=1, le=10)
    retry_count: int = Field(default=3, ge=0)
    retry_delay: int = Field(default=60, ge=1)
    enabled: bool = Field(default=True)
    tags: List[str] = Field(default_factory=list)


class MonitoringConfig(BaseModel):
    """Monitoring configuration."""
    enable_prometheus: bool = Field(default=True)
    prometheus_port: int = Field(default=8000)
    enable_logging: bool = Field(default=True)
    log_level: str = Field(default="INFO")
    alert_thresholds: Dict = Field(default_factory=lambda: {
        'query_error_rate': 0.1,  # 10% error rate
        'query_latency_p99': 5.0,  # 5 seconds
        'db_connection_usage': 0.8,  # 80% of pool
        'queue_backlog': 1000  # 1000 pending tasks
    })


class Config(BaseModel):
    """Main configuration class."""
    database: DatabaseConfig = Field(default_factory=DatabaseConfig)
    redis: RedisConfig = Field(default_factory=RedisConfig)
    rate_limiter: RateLimiterConfig = Field(default_factory=RateLimiterConfig)
    scheduler: SchedulerConfig = Field(default_factory=SchedulerConfig)
    monitoring: MonitoringConfig = Field(default_factory=MonitoringConfig)
    metrics: List[MetricConfig] = Field(default_factory=list)
    
    @classmethod
    def from_env(cls) -> 'Config':
        """Load configuration from environment variables."""
        return cls(
            database=DatabaseConfig(
                host=os.getenv('DB_HOST', 'localhost'),
                port=int(os.getenv('DB_PORT', '3306')),
                username=os.getenv('DB_USERNAME', 'root'),
                password=os.getenv('DB_PASSWORD', ''),
                database=os.getenv('DB_DATABASE', 'metrics'),
                pool_size=int(os.getenv('DB_POOL_SIZE', '10')),
                max_overflow=int(os.getenv('DB_MAX_OVERFLOW', '20'))
            ),
            redis=RedisConfig(
                host=os.getenv('REDIS_HOST', 'localhost'),
                port=int(os.getenv('REDIS_PORT', '6379')),
                password=os.getenv('REDIS_PASSWORD'),
                db=int(os.getenv('REDIS_DB', '0'))
            ),
            rate_limiter=RateLimiterConfig(
                max_queries_per_second=int(os.getenv('RATE_LIMIT_QPS', '100')),
                max_queries_per_minute=int(os.getenv('RATE_LIMIT_QPM', '5000')),
                max_concurrent_queries=int(os.getenv('RATE_LIMIT_CONCURRENT', '50'))
            ),
            scheduler=SchedulerConfig(
                timezone=os.getenv('SCHEDULER_TIMEZONE', 'UTC'),
                max_workers=int(os.getenv('SCHEDULER_MAX_WORKERS', '10'))
            ),
            monitoring=MonitoringConfig(
                enable_prometheus=os.getenv('ENABLE_PROMETHEUS', 'true').lower() == 'true',
                prometheus_port=int(os.getenv('PROMETHEUS_PORT', '8000')),
                log_level=os.getenv('LOG_LEVEL', 'INFO')
            )
        )
    
    def to_dict(self) -> Dict:
        """Convert configuration to dictionary."""
        return self.dict()