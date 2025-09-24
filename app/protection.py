import asyncio
import time
from typing import Callable, Any, Optional, Dict
from functools import wraps
from contextlib import asynccontextmanager
from dataclasses import dataclass
from enum import Enum
import redis.asyncio as redis
from loguru import logger

from .config import settings


class CircuitBreakerState(Enum):
    """熔断器状态"""
    CLOSED = "closed"      # 正常状态
    OPEN = "open"          # 熔断状态
    HALF_OPEN = "half_open"  # 半开状态


@dataclass
class CircuitBreakerConfig:
    """熔断器配置"""
    failure_threshold: int = 5  # 失败阈值
    timeout: int = 60  # 熔断超时时间（秒）
    expected_exception: tuple = (Exception,)


class CircuitBreaker:
    """熔断器实现"""
    
    def __init__(self, config: CircuitBreakerConfig):
        self.config = config
        self.state = CircuitBreakerState.CLOSED
        self.failure_count = 0
        self.last_failure_time = None
        self.success_count = 0
        
    def _should_attempt_reset(self) -> bool:
        """判断是否应该尝试重置熔断器"""
        return (
            self.state == CircuitBreakerState.OPEN and
            self.last_failure_time and
            time.time() - self.last_failure_time >= self.config.timeout
        )
    
    def _record_success(self):
        """记录成功"""
        self.failure_count = 0
        self.success_count += 1
        if self.state == CircuitBreakerState.HALF_OPEN:
            self.state = CircuitBreakerState.CLOSED
            logger.info("Circuit breaker reset to CLOSED state")
    
    def _record_failure(self):
        """记录失败"""
        self.failure_count += 1
        self.last_failure_time = time.time()
        
        if self.failure_count >= self.config.failure_threshold:
            if self.state == CircuitBreakerState.CLOSED:
                self.state = CircuitBreakerState.OPEN
                logger.warning(f"Circuit breaker opened due to {self.failure_count} failures")
            elif self.state == CircuitBreakerState.HALF_OPEN:
                self.state = CircuitBreakerState.OPEN
                logger.warning("Circuit breaker reopened from HALF_OPEN state")
    
    async def call(self, func: Callable, *args, **kwargs):
        """执行被保护的函数"""
        if self.state == CircuitBreakerState.OPEN:
            if self._should_attempt_reset():
                self.state = CircuitBreakerState.HALF_OPEN
                logger.info("Circuit breaker moved to HALF_OPEN state")
            else:
                raise Exception("Circuit breaker is OPEN")
        
        try:
            result = await func(*args, **kwargs) if asyncio.iscoroutinefunction(func) else func(*args, **kwargs)
            self._record_success()
            return result
        except self.config.expected_exception as e:
            self._record_failure()
            raise e


class RateLimiter:
    """基于令牌桶的限流器"""
    
    def __init__(self, rate: int, burst: int, redis_client: Optional[redis.Redis] = None):
        self.rate = rate  # 每秒令牌数
        self.burst = burst  # 桶容量
        self.redis_client = redis_client
        self._local_tokens = burst
        self._last_refill = time.time()
        self._lock = asyncio.Lock()
    
    async def _refill_tokens(self):
        """补充令牌"""
        now = time.time()
        elapsed = now - self._last_refill
        tokens_to_add = elapsed * self.rate
        
        self._local_tokens = min(self.burst, self._local_tokens + tokens_to_add)
        self._last_refill = now
    
    async def acquire(self, tokens: int = 1) -> bool:
        """获取令牌"""
        async with self._lock:
            await self._refill_tokens()
            
            if self._local_tokens >= tokens:
                self._local_tokens -= tokens
                return True
            return False
    
    @asynccontextmanager
    async def limit(self, tokens: int = 1):
        """限流上下文管理器"""
        if not await self.acquire(tokens):
            raise Exception("Rate limit exceeded")
        try:
            yield
        finally:
            pass


class DatabaseProtection:
    """数据库保护机制"""
    
    def __init__(self):
        self.circuit_breaker = CircuitBreaker(
            CircuitBreakerConfig(
                failure_threshold=settings.circuit_breaker_failure_threshold,
                timeout=settings.circuit_breaker_timeout,
                expected_exception=settings.circuit_breaker_expected_exception
            )
        )
        self.rate_limiter = RateLimiter(
            rate=settings.rate_limit_per_second,
            burst=settings.rate_limit_burst
        )
        self.concurrent_tasks = asyncio.Semaphore(settings.max_concurrent_tasks)
        
    @asynccontextmanager
    async def protect(self, operation_name: str = "database_operation"):
        """数据库操作保护"""
        start_time = time.time()
        
        try:
            # 限流检查
            async with self.rate_limiter.limit():
                # 并发控制
                async with self.concurrent_tasks:
                    logger.debug(f"Starting protected operation: {operation_name}")
                    yield
                    
        except Exception as e:
            logger.error(f"Protected operation failed: {operation_name}, error: {e}")
            raise
        finally:
            duration = time.time() - start_time
            logger.debug(f"Protected operation completed: {operation_name}, duration: {duration:.2f}s")
    
    async def execute_with_circuit_breaker(self, func: Callable, *args, **kwargs):
        """使用熔断器执行函数"""
        return await self.circuit_breaker.call(func, *args, **kwargs)


class TaskThrottler:
    """任务节流器"""
    
    def __init__(self, max_concurrent: int, rate_per_second: int):
        self.semaphore = asyncio.Semaphore(max_concurrent)
        self.rate_limiter = RateLimiter(rate_per_second, rate_per_second * 2)
        self.active_tasks = 0
        self._lock = asyncio.Lock()
    
    @asynccontextmanager
    async def throttle(self, task_name: str = "task"):
        """任务节流"""
        async with self.rate_limiter.limit():
            async with self.semaphore:
                async with self._lock:
                    self.active_tasks += 1
                    logger.debug(f"Task started: {task_name}, active tasks: {self.active_tasks}")
                
                try:
                    yield
                finally:
                    async with self._lock:
                        self.active_tasks -= 1
                        logger.debug(f"Task completed: {task_name}, active tasks: {self.active_tasks}")
    
    def get_stats(self) -> Dict[str, Any]:
        """获取节流器统计信息"""
        return {
            "active_tasks": self.active_tasks,
            "available_slots": self.semaphore._value,
            "max_concurrent": self.semaphore._bound_value,
        }


# 全局保护实例
db_protection = DatabaseProtection()
task_throttler = TaskThrottler(
    max_concurrent=settings.max_concurrent_tasks,
    rate_per_second=settings.rate_limit_per_second
)


def circuit_breaker_decorator(config: Optional[CircuitBreakerConfig] = None):
    """熔断器装饰器"""
    if config is None:
        config = CircuitBreakerConfig()
    
    breaker = CircuitBreaker(config)
    
    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            return await breaker.call(func, *args, **kwargs)
        return wrapper
    return decorator


def rate_limit_decorator(rate: int, burst: int):
    """限流装饰器"""
    limiter = RateLimiter(rate, burst)
    
    def decorator(func):
        @wraps(func)
        async def wrapper(*args, **kwargs):
            async with limiter.limit():
                return await func(*args, **kwargs)
        return wrapper
    return decorator