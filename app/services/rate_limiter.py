import asyncio
import time
import logging
from typing import Dict, Optional
from collections import defaultdict, deque

logger = logging.getLogger(__name__)

class RateLimiter:
    """速率限制器"""
    
    def __init__(self, max_requests: int = 100, time_window: int = 60):
        """
        Args:
            max_requests: 时间窗口内最大请求数
            time_window: 时间窗口（秒）
        """
        self.max_requests = max_requests
        self.time_window = time_window
        self.requests = defaultdict(deque)
        self.locks = defaultdict(asyncio.Lock)
    
    async def is_allowed(self, key: str) -> bool:
        """检查是否允许请求"""
        async with self.locks[key]:
            now = time.time()
            request_times = self.requests[key]
            
            # 清理过期请求
            while request_times and request_times[0] <= now - self.time_window:
                request_times.popleft()
            
            # 检查是否超过限制
            if len(request_times) >= self.max_requests:
                logger.warning(f"Rate limit exceeded for {key}")
                return False
            
            # 记录当前请求
            request_times.append(now)
            return True
    
    def get_remaining_requests(self, key: str) -> int:
        """获取剩余请求数"""
        now = time.time()
        request_times = self.requests[key]
        
        # 清理过期请求
        while request_times and request_times[0] <= now - self.time_window:
            request_times.popleft()
        
        return max(0, self.max_requests - len(request_times))

class CallRateLimiter:
    """通话速率限制器"""
    
    def __init__(self, max_concurrent_calls: int = 10, max_calls_per_minute: int = 30):
        self.max_concurrent_calls = max_concurrent_calls
        self.max_calls_per_minute = max_calls_per_minute
        
        # 并发通话限制
        self.active_calls = set()
        self.concurrent_lock = asyncio.Lock()
        
        # 每分钟通话限制
        self.call_rate_limiter = RateLimiter(max_calls_per_minute, 60)
    
    async def can_start_call(self, call_id: str) -> tuple[bool, str]:
        """检查是否可以开始新通话"""
        # 检查并发限制
        async with self.concurrent_lock:
            if len(self.active_calls) >= self.max_concurrent_calls:
                return False, f"Maximum concurrent calls ({self.max_concurrent_calls}) exceeded"
        
        # 检查速率限制
        if not await self.call_rate_limiter.is_allowed("global_calls"):
            return False, f"Call rate limit exceeded ({self.max_calls_per_minute} calls/minute)"
        
        return True, "OK"
    
    async def start_call(self, call_id: str):
        """标记通话开始"""
        async with self.concurrent_lock:
            self.active_calls.add(call_id)
        logger.info(f"Call {call_id} started. Active calls: {len(self.active_calls)}")
    
    async def end_call(self, call_id: str):
        """标记通话结束"""
        async with self.concurrent_lock:
            self.active_calls.discard(call_id)
        logger.info(f"Call {call_id} ended. Active calls: {len(self.active_calls)}")
    
    def get_stats(self) -> dict:
        """获取统计信息"""
        return {
            "active_calls": len(self.active_calls),
            "max_concurrent_calls": self.max_concurrent_calls,
            "remaining_calls_per_minute": self.call_rate_limiter.get_remaining_requests("global_calls"),
            "max_calls_per_minute": self.max_calls_per_minute
        }

class TranscriptionRateLimiter:
    """转写速率限制器"""
    
    def __init__(self, max_transcriptions_per_second: int = 5):
        self.max_transcriptions_per_second = max_transcriptions_per_second
        self.transcription_times = deque()
        self.lock = asyncio.Lock()
    
    async def can_transcribe(self) -> bool:
        """检查是否可以执行转写"""
        async with self.lock:
            now = time.time()
            
            # 清理1秒前的记录
            while self.transcription_times and self.transcription_times[0] <= now - 1.0:
                self.transcription_times.popleft()
            
            # 检查是否超过限制
            if len(self.transcription_times) >= self.max_transcriptions_per_second:
                return False
            
            # 记录当前转写
            self.transcription_times.append(now)
            return True
    
    def get_current_rate(self) -> float:
        """获取当前转写速率"""
        now = time.time()
        # 清理过期记录
        while self.transcription_times and self.transcription_times[0] <= now - 1.0:
            self.transcription_times.popleft()
        
        return len(self.transcription_times)