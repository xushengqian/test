"""
Redis SETNX 分布式锁实现
使用 Redis 的 SETNX 命令实现分布式锁，确保在分布式环境中只有一个进程可以执行关键代码段
"""

import time
import uuid
import redis
from typing import Optional
from contextlib import contextmanager


class RedisLock:
    """基于 Redis SETNX 的分布式锁"""
    
    def __init__(self, redis_client: redis.Redis, key: str, timeout: int = 10, retry_interval: float = 0.1):
        """
        初始化分布式锁
        
        Args:
            redis_client: Redis 客户端实例
            key: 锁的键名
            timeout: 锁的超时时间（秒），防止死锁
            retry_interval: 获取锁失败时的重试间隔（秒）
        """
        self.redis_client = redis_client
        self.key = key
        self.timeout = timeout
        self.retry_interval = retry_interval
        self.identifier = str(uuid.uuid4())  # 唯一标识符，用于安全释放锁
    
    def acquire(self, blocking: bool = True, timeout: Optional[float] = None) -> bool:
        """
        获取锁
        
        Args:
            blocking: 是否阻塞等待获取锁
            timeout: 阻塞超时时间（秒），None 表示无限等待
        
        Returns:
            bool: 是否成功获取锁
        """
        end_time = time.time() + (timeout if timeout else float('inf'))
        
        while True:
            # 使用 SETNX 命令尝试设置锁
            # SET key value NX EX timeout
            # NX: 只有当键不存在时才设置
            # EX: 设置过期时间
            if self.redis_client.set(self.key, self.identifier, nx=True, ex=self.timeout):
                return True
            
            if not blocking:
                return False
            
            if time.time() > end_time:
                return False
            
            time.sleep(self.retry_interval)
    
    def release(self) -> bool:
        """
        释放锁
        
        使用 Lua 脚本确保只有锁的持有者才能释放锁
        
        Returns:
            bool: 是否成功释放锁
        """
        lua_script = """
        if redis.call("get", KEYS[1]) == ARGV[1] then
            return redis.call("del", KEYS[1])
        else
            return 0
        end
        """
        
        try:
            result = self.redis_client.eval(lua_script, 1, self.key, self.identifier)
            return bool(result)
        except Exception as e:
            print(f"释放锁时出错: {e}")
            return False
    
    def __enter__(self):
        """上下文管理器入口"""
        if not self.acquire():
            raise RuntimeError(f"无法获取锁: {self.key}")
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        """上下文管理器出口，自动释放锁"""
        self.release()
        return False
    
    @contextmanager
    def lock(self, blocking: bool = True, timeout: Optional[float] = None):
        """
        上下文管理器方式使用锁
        
        Args:
            blocking: 是否阻塞等待获取锁
            timeout: 阻塞超时时间（秒）
        
        Yields:
            RedisLock: 锁实例
        """
        acquired = self.acquire(blocking=blocking, timeout=timeout)
        if not acquired:
            raise RuntimeError(f"无法获取锁: {self.key}")
        
        try:
            yield self
        finally:
            self.release()


def create_redis_client(host: str = 'localhost', port: int = 6379, db: int = 0, 
                       password: Optional[str] = None) -> redis.Redis:
    """
    创建 Redis 客户端
    
    Args:
        host: Redis 服务器地址
        port: Redis 服务器端口
        db: 数据库编号
        password: Redis 密码
    
    Returns:
        redis.Redis: Redis 客户端实例
    """
    return redis.Redis(
        host=host,
        port=port,
        db=db,
        password=password,
        decode_responses=True  # 自动解码响应为字符串
    )
