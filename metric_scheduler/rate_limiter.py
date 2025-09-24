"""Rate limiter implementation for database access control."""
import asyncio
import time
from collections import deque
from typing import Optional, Dict, Callable, Any
from contextlib import asynccontextmanager
import redis
from loguru import logger
from .config import RateLimiterConfig


class TokenBucket:
    """Token bucket algorithm implementation for rate limiting."""
    
    def __init__(self, rate: float, capacity: int):
        self.rate = rate  # tokens per second
        self.capacity = capacity
        self.tokens = capacity
        self.last_update = time.time()
        self._lock = asyncio.Lock()
    
    async def acquire(self, tokens: int = 1) -> bool:
        """Try to acquire tokens from the bucket."""
        async with self._lock:
            now = time.time()
            elapsed = now - self.last_update
            self.tokens = min(self.capacity, self.tokens + elapsed * self.rate)
            self.last_update = now
            
            if self.tokens >= tokens:
                self.tokens -= tokens
                return True
            return False
    
    async def wait_for_token(self, tokens: int = 1) -> None:
        """Wait until tokens are available."""
        while not await self.acquire(tokens):
            wait_time = tokens / self.rate
            await asyncio.sleep(wait_time)


class SlidingWindowRateLimiter:
    """Sliding window rate limiter implementation."""
    
    def __init__(self, window_size: int, max_requests: int):
        self.window_size = window_size  # in seconds
        self.max_requests = max_requests
        self.requests = deque()
        self._lock = asyncio.Lock()
    
    async def is_allowed(self) -> bool:
        """Check if request is allowed within the rate limit."""
        async with self._lock:
            now = time.time()
            # Remove old requests outside the window
            while self.requests and self.requests[0] <= now - self.window_size:
                self.requests.popleft()
            
            if len(self.requests) < self.max_requests:
                self.requests.append(now)
                return True
            return False


class DistributedRateLimiter:
    """Distributed rate limiter using Redis."""
    
    def __init__(self, redis_client: redis.Redis, config: RateLimiterConfig):
        self.redis = redis_client
        self.config = config
        self.key_prefix = "rate_limiter"
        
        # Token bucket for per-second limiting
        self.token_bucket = TokenBucket(
            rate=config.max_queries_per_second,
            capacity=int(config.max_queries_per_second * config.burst_multiplier)
        )
        
        # Sliding window for per-minute limiting
        self.sliding_window = SlidingWindowRateLimiter(
            window_size=60,
            max_requests=config.max_queries_per_minute
        )
        
        # Semaphore for concurrent query limiting
        self.concurrent_semaphore = asyncio.Semaphore(config.max_concurrent_queries)
        
        # Metrics
        self.metrics = {
            'total_requests': 0,
            'allowed_requests': 0,
            'rejected_requests': 0,
            'current_concurrent': 0
        }
    
    async def acquire(self, resource_id: str = "default", weight: int = 1) -> bool:
        """Try to acquire permission for a database query."""
        self.metrics['total_requests'] += 1
        
        # Check token bucket (per-second limit)
        if not await self.token_bucket.acquire(weight):
            self.metrics['rejected_requests'] += 1
            logger.warning(f"Rate limit exceeded (per-second) for resource: {resource_id}")
            return False
        
        # Check sliding window (per-minute limit)
        if not await self.sliding_window.is_allowed():
            self.metrics['rejected_requests'] += 1
            logger.warning(f"Rate limit exceeded (per-minute) for resource: {resource_id}")
            return False
        
        # Check distributed rate limit using Redis
        key = f"{self.key_prefix}:{resource_id}:minute"
        pipe = self.redis.pipeline()
        pipe.incr(key)
        pipe.expire(key, 60)
        count, _ = pipe.execute()
        
        if count > self.config.max_queries_per_minute:
            self.metrics['rejected_requests'] += 1
            logger.warning(f"Distributed rate limit exceeded for resource: {resource_id}")
            # Decrement the counter since we're rejecting
            self.redis.decr(key)
            return False
        
        self.metrics['allowed_requests'] += 1
        return True
    
    @asynccontextmanager
    async def limit_concurrent(self):
        """Context manager for limiting concurrent database queries."""
        await self.concurrent_semaphore.acquire()
        self.metrics['current_concurrent'] += 1
        try:
            yield
        finally:
            self.metrics['current_concurrent'] -= 1
            self.concurrent_semaphore.release()
    
    async def wait_and_acquire(self, resource_id: str = "default", weight: int = 1) -> None:
        """Wait until rate limit allows the request."""
        while not await self.acquire(resource_id, weight):
            await asyncio.sleep(0.1)
    
    def get_metrics(self) -> Dict[str, Any]:
        """Get rate limiter metrics."""
        return {
            **self.metrics,
            'token_bucket_tokens': self.token_bucket.tokens,
            'concurrent_available': self.concurrent_semaphore._value,
            'config': {
                'max_qps': self.config.max_queries_per_second,
                'max_qpm': self.config.max_queries_per_minute,
                'max_concurrent': self.config.max_concurrent_queries
            }
        }


class RateLimiterDecorator:
    """Decorator for applying rate limiting to functions."""
    
    def __init__(self, rate_limiter: DistributedRateLimiter):
        self.rate_limiter = rate_limiter
    
    def limit(self, resource_id: str = "default", weight: int = 1, wait: bool = True):
        """Decorator to apply rate limiting to a function."""
        def decorator(func: Callable) -> Callable:
            async def wrapper(*args, **kwargs):
                if wait:
                    await self.rate_limiter.wait_and_acquire(resource_id, weight)
                else:
                    if not await self.rate_limiter.acquire(resource_id, weight):
                        raise Exception(f"Rate limit exceeded for resource: {resource_id}")
                
                async with self.rate_limiter.limit_concurrent():
                    return await func(*args, **kwargs)
            
            return wrapper
        return decorator