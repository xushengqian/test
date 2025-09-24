from celery import Celery
from celery.schedules import crontab
import redis.asyncio as redis
from kombu import Queue
from .config import settings

# 创建Celery应用
celery_app = Celery(
    "metrics_scheduler",
    broker=settings.redis_url,
    backend=settings.redis_url,
    include=[
        "app.tasks.metric_tasks",
        "app.tasks.system_tasks",
    ]
)

# Celery配置
celery_app.conf.update(
    # 任务序列化
    task_serializer="json",
    accept_content=["json"],
    result_serializer="json",
    timezone="Asia/Shanghai",
    enable_utc=True,
    
    # 任务路由
    task_routes={
        "app.tasks.metric_tasks.execute_company_metric": {"queue": "metrics"},
        "app.tasks.metric_tasks.batch_execute_metrics": {"queue": "metrics"},
        "app.tasks.system_tasks.cleanup_old_executions": {"queue": "system"},
        "app.tasks.system_tasks.collect_system_metrics": {"queue": "system"},
    },
    
    # 队列配置
    task_default_queue="default",
    task_queues=(
        Queue("default", routing_key="default"),
        Queue("metrics", routing_key="metrics"),
        Queue("system", routing_key="system"),
        Queue("priority", routing_key="priority"),
    ),
    
    # 任务执行配置
    task_acks_late=True,
    worker_prefetch_multiplier=1,
    task_reject_on_worker_lost=True,
    
    # 结果配置
    result_expires=3600,  # 结果保存1小时
    result_backend_transport_options={
        "master_name": "mymaster",
        "visibility_timeout": 3600,
    },
    
    # 重试配置
    task_default_retry_delay=60,
    task_max_retries=3,
    
    # 监控配置
    worker_send_task_events=True,
    task_send_sent_event=True,
)

# 定时任务配置
celery_app.conf.beat_schedule = {
    # 每分钟执行高优先级指标
    "execute-priority-metrics": {
        "task": "app.tasks.metric_tasks.schedule_priority_metrics",
        "schedule": crontab(minute="*"),  # 每分钟
    },
    
    # 每5分钟执行日常指标
    "execute-daily-metrics": {
        "task": "app.tasks.metric_tasks.schedule_daily_metrics",
        "schedule": crontab(minute="*/5"),  # 每5分钟
    },
    
    # 每小时执行周度指标
    "execute-weekly-metrics": {
        "task": "app.tasks.metric_tasks.schedule_weekly_metrics",
        "schedule": crontab(minute=0),  # 每小时
    },
    
    # 每天凌晨2点清理旧数据
    "cleanup-old-data": {
        "task": "app.tasks.system_tasks.cleanup_old_executions",
        "schedule": crontab(hour=2, minute=0),  # 每天凌晨2点
    },
    
    # 每分钟收集系统指标
    "collect-system-metrics": {
        "task": "app.tasks.system_tasks.collect_system_metrics",
        "schedule": crontab(minute="*"),  # 每分钟
    },
}


# Redis连接池
redis_pool = redis.ConnectionPool.from_url(
    settings.redis_url,
    max_connections=settings.redis_max_connections,
    retry_on_timeout=True,
    socket_keepalive=True,
    socket_keepalive_options={},
)

redis_client = redis.Redis(connection_pool=redis_pool)