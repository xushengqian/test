from prometheus_client import Counter, Histogram, Gauge, CollectorRegistry, generate_latest
from prometheus_client.exposition import MetricsHandler
from typing import Dict, Any
import time
from functools import wraps
from loguru import logger

# 创建自定义注册表
registry = CollectorRegistry()

# 定义指标
# 计数器指标
metric_executions_total = Counter(
    'metric_executions_total',
    'Total number of metric executions',
    ['company_code', 'metric_code', 'status'],
    registry=registry
)

task_queue_operations_total = Counter(
    'task_queue_operations_total',
    'Total number of task queue operations',
    ['operation', 'queue'],
    registry=registry
)

database_operations_total = Counter(
    'database_operations_total',
    'Total number of database operations',
    ['operation', 'status'],
    registry=registry
)

circuit_breaker_operations_total = Counter(
    'circuit_breaker_operations_total',
    'Total number of circuit breaker operations',
    ['state', 'operation'],
    registry=registry
)

rate_limit_operations_total = Counter(
    'rate_limit_operations_total',
    'Total number of rate limit operations',
    ['status'],
    registry=registry
)

# 直方图指标（用于测量延迟）
metric_execution_duration_seconds = Histogram(
    'metric_execution_duration_seconds',
    'Time spent executing metrics',
    ['company_code', 'metric_code'],
    buckets=[0.1, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0],
    registry=registry
)

database_operation_duration_seconds = Histogram(
    'database_operation_duration_seconds',
    'Time spent on database operations',
    ['operation'],
    buckets=[0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0],
    registry=registry
)

task_queue_wait_duration_seconds = Histogram(
    'task_queue_wait_duration_seconds',
    'Time spent waiting in task queue',
    ['queue'],
    buckets=[0.1, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0, 60.0, 300.0],
    registry=registry
)

# 仪表指标（用于测量当前值）
active_database_connections = Gauge(
    'active_database_connections',
    'Number of active database connections',
    registry=registry
)

database_pool_size = Gauge(
    'database_pool_size',
    'Database connection pool size',
    registry=registry
)

active_tasks = Gauge(
    'active_tasks',
    'Number of active tasks',
    ['queue'],
    registry=registry
)

system_cpu_usage_percent = Gauge(
    'system_cpu_usage_percent',
    'System CPU usage percentage',
    registry=registry
)

system_memory_usage_percent = Gauge(
    'system_memory_usage_percent',
    'System memory usage percentage',
    registry=registry
)

system_disk_usage_percent = Gauge(
    'system_disk_usage_percent',
    'System disk usage percentage',
    registry=registry
)

circuit_breaker_failure_count = Gauge(
    'circuit_breaker_failure_count',
    'Circuit breaker failure count',
    ['service'],
    registry=registry
)

rate_limiter_tokens = Gauge(
    'rate_limiter_tokens',
    'Available tokens in rate limiter',
    ['limiter'],
    registry=registry
)


class PrometheusMetrics:
    """Prometheus指标收集器"""
    
    def __init__(self):
        self.registry = registry
    
    def record_metric_execution(self, company_code: str, metric_code: str, status: str, duration_seconds: float):
        """记录指标执行"""
        metric_executions_total.labels(
            company_code=company_code,
            metric_code=metric_code,
            status=status
        ).inc()
        
        if duration_seconds > 0:
            metric_execution_duration_seconds.labels(
                company_code=company_code,
                metric_code=metric_code
            ).observe(duration_seconds)
    
    def record_database_operation(self, operation: str, status: str, duration_seconds: float):
        """记录数据库操作"""
        database_operations_total.labels(
            operation=operation,
            status=status
        ).inc()
        
        if duration_seconds > 0:
            database_operation_duration_seconds.labels(
                operation=operation
            ).observe(duration_seconds)
    
    def record_task_queue_operation(self, operation: str, queue: str, wait_duration_seconds: float = 0):
        """记录任务队列操作"""
        task_queue_operations_total.labels(
            operation=operation,
            queue=queue
        ).inc()
        
        if wait_duration_seconds > 0:
            task_queue_wait_duration_seconds.labels(
                queue=queue
            ).observe(wait_duration_seconds)
    
    def record_circuit_breaker_operation(self, state: str, operation: str):
        """记录熔断器操作"""
        circuit_breaker_operations_total.labels(
            state=state,
            operation=operation
        ).inc()
    
    def record_rate_limit_operation(self, status: str):
        """记录限流操作"""
        rate_limit_operations_total.labels(status=status).inc()
    
    def update_database_metrics(self, active_connections: int, pool_size: int):
        """更新数据库指标"""
        active_database_connections.set(active_connections)
        database_pool_size.set(pool_size)
    
    def update_task_metrics(self, queue: str, active_count: int):
        """更新任务指标"""
        active_tasks.labels(queue=queue).set(active_count)
    
    def update_system_metrics(self, cpu_percent: float, memory_percent: float, disk_percent: float):
        """更新系统指标"""
        system_cpu_usage_percent.set(cpu_percent)
        system_memory_usage_percent.set(memory_percent)
        system_disk_usage_percent.set(disk_percent)
    
    def update_circuit_breaker_metrics(self, service: str, failure_count: int):
        """更新熔断器指标"""
        circuit_breaker_failure_count.labels(service=service).set(failure_count)
    
    def update_rate_limiter_metrics(self, limiter: str, available_tokens: float):
        """更新限流器指标"""
        rate_limiter_tokens.labels(limiter=limiter).set(available_tokens)
    
    def get_metrics(self) -> str:
        """获取Prometheus格式的指标"""
        return generate_latest(self.registry).decode('utf-8')


# 全局指标收集器实例
prometheus_metrics = PrometheusMetrics()


def monitor_execution_time(metric_name: str = None):
    """监控执行时间的装饰器"""
    def decorator(func):
        @wraps(func)
        async def async_wrapper(*args, **kwargs):
            start_time = time.time()
            status = "success"
            
            try:
                result = await func(*args, **kwargs)
                return result
            except Exception as e:
                status = "error"
                raise e
            finally:
                duration = time.time() - start_time
                name = metric_name or func.__name__
                
                # 记录到Prometheus
                database_operation_duration_seconds.labels(
                    operation=name
                ).observe(duration)
                
                database_operations_total.labels(
                    operation=name,
                    status=status
                ).inc()
                
                logger.debug(f"Function {name} executed in {duration:.3f}s with status {status}")
        
        @wraps(func)
        def sync_wrapper(*args, **kwargs):
            start_time = time.time()
            status = "success"
            
            try:
                result = func(*args, **kwargs)
                return result
            except Exception as e:
                status = "error"
                raise e
            finally:
                duration = time.time() - start_time
                name = metric_name or func.__name__
                
                # 记录到Prometheus
                database_operation_duration_seconds.labels(
                    operation=name
                ).observe(duration)
                
                database_operations_total.labels(
                    operation=name,
                    status=status
                ).inc()
                
                logger.debug(f"Function {name} executed in {duration:.3f}s with status {status}")
        
        # 根据函数类型返回对应的包装器
        import asyncio
        if asyncio.iscoroutinefunction(func):
            return async_wrapper
        else:
            return sync_wrapper
    
    return decorator


def monitor_metric_execution(func):
    """监控指标执行的装饰器"""
    @wraps(func)
    async def wrapper(*args, **kwargs):
        start_time = time.time()
        company_code = "unknown"
        metric_code = "unknown"
        status = "success"
        
        try:
            # 尝试从参数中提取企业和指标信息
            if len(args) >= 2:
                company_id, metric_id = args[0], args[1]
                # 这里可以根据ID查询对应的code，简化处理直接使用ID
                company_code = f"company_{company_id}"
                metric_code = f"metric_{metric_id}"
            
            result = await func(*args, **kwargs)
            return result
        except Exception as e:
            status = "error"
            raise e
        finally:
            duration = time.time() - start_time
            
            # 记录指标执行
            prometheus_metrics.record_metric_execution(
                company_code=company_code,
                metric_code=metric_code,
                status=status,
                duration_seconds=duration
            )
    
    return wrapper