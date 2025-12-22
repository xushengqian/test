"""
Redis SETNX 实现示例

SETNX (SET if Not eXists) 是 Redis 的原子操作，用于：
1. 仅在键不存在时设置值
2. 实现分布式锁
3. 防止重复操作
"""

import redis
import time
import uuid
from contextlib import contextmanager
from typing import Optional


class RedisClient:
    """Redis 客户端封装"""

    def __init__(self, host: str = "localhost", port: int = 6379, db: int = 0):
        self.client = redis.Redis(host=host, port=port, db=db, decode_responses=True)

    def setnx(self, key: str, value: str) -> bool:
        """
        SETNX - 仅在键不存在时设置值

        Args:
            key: Redis 键
            value: 要设置的值

        Returns:
            bool: True 表示设置成功（键之前不存在），False 表示键已存在
        """
        return self.client.setnx(key, value)

    def set_with_nx(
        self, key: str, value: str, ex: Optional[int] = None, px: Optional[int] = None
    ) -> bool:
        """
        使用 SET 命令的 NX 选项（推荐方式）

        Args:
            key: Redis 键
            value: 要设置的值
            ex: 过期时间（秒）
            px: 过期时间（毫秒）

        Returns:
            bool: True 表示设置成功，False 表示键已存在
        """
        result = self.client.set(key, value, nx=True, ex=ex, px=px)
        return result is not None

    def get(self, key: str) -> Optional[str]:
        """获取键的值"""
        return self.client.get(key)

    def delete(self, key: str) -> int:
        """删除键"""
        return self.client.delete(key)

    def close(self):
        """关闭连接"""
        self.client.close()


class DistributedLock:
    """
    基于 Redis SETNX 的分布式锁实现

    特点：
    1. 使用 SET key value NX EX 原子命令
    2. 使用唯一标识符防止误删其他进程的锁
    3. 支持自动续期（可选）
    """

    def __init__(
        self,
        redis_client: RedisClient,
        lock_name: str,
        expire_seconds: int = 30,
    ):
        self.redis = redis_client
        self.lock_name = f"lock:{lock_name}"
        self.expire_seconds = expire_seconds
        self.lock_value = str(uuid.uuid4())  # 唯一标识符
        self._locked = False

    def acquire(self, blocking: bool = True, timeout: float = -1) -> bool:
        """
        获取锁

        Args:
            blocking: 是否阻塞等待
            timeout: 阻塞超时时间（秒），-1 表示无限等待

        Returns:
            bool: 是否成功获取锁
        """
        start_time = time.time()

        while True:
            # 使用 SET NX EX 原子操作
            if self.redis.set_with_nx(
                self.lock_name, self.lock_value, ex=self.expire_seconds
            ):
                self._locked = True
                return True

            if not blocking:
                return False

            if timeout >= 0 and (time.time() - start_time) >= timeout:
                return False

            # 短暂休眠后重试
            time.sleep(0.1)

    def release(self) -> bool:
        """
        释放锁

        使用 Lua 脚本确保只释放自己持有的锁（原子操作）

        Returns:
            bool: 是否成功释放锁
        """
        if not self._locked:
            return False

        # Lua 脚本：只有当锁的值等于我们的唯一标识时才删除
        lua_script = """
        if redis.call("get", KEYS[1]) == ARGV[1] then
            return redis.call("del", KEYS[1])
        else
            return 0
        end
        """
        result = self.redis.client.eval(lua_script, 1, self.lock_name, self.lock_value)
        self._locked = False
        return result == 1

    def extend(self, additional_seconds: int) -> bool:
        """
        延长锁的过期时间

        Args:
            additional_seconds: 额外的秒数

        Returns:
            bool: 是否成功延长
        """
        if not self._locked:
            return False

        # Lua 脚本：只有当锁的值等于我们的唯一标识时才延长过期时间
        lua_script = """
        if redis.call("get", KEYS[1]) == ARGV[1] then
            return redis.call("expire", KEYS[1], ARGV[2])
        else
            return 0
        end
        """
        result = self.redis.client.eval(
            lua_script, 1, self.lock_name, self.lock_value, additional_seconds
        )
        return result == 1

    @property
    def locked(self) -> bool:
        """检查当前实例是否持有锁"""
        return self._locked


@contextmanager
def distributed_lock(
    redis_client: RedisClient,
    lock_name: str,
    expire_seconds: int = 30,
    blocking: bool = True,
    timeout: float = -1,
):
    """
    分布式锁的上下文管理器

    用法:
        with distributed_lock(redis_client, "my_lock") as lock:
            if lock.locked:
                # 执行需要加锁的操作
                pass
    """
    lock = DistributedLock(redis_client, lock_name, expire_seconds)
    try:
        lock.acquire(blocking=blocking, timeout=timeout)
        yield lock
    finally:
        lock.release()


# ============== 使用示例 ==============


def example_basic_setnx():
    """基本 SETNX 示例"""
    print("=" * 50)
    print("基本 SETNX 示例")
    print("=" * 50)

    client = RedisClient()

    # 清理测试键
    client.delete("test_key")

    # 第一次设置 - 应该成功
    result1 = client.setnx("test_key", "value1")
    print(f"第一次 SETNX: {result1}")  # True

    # 第二次设置 - 应该失败（键已存在）
    result2 = client.setnx("test_key", "value2")
    print(f"第二次 SETNX: {result2}")  # False

    # 获取值 - 应该是第一次设置的值
    value = client.get("test_key")
    print(f"当前值: {value}")  # value1

    # 清理
    client.delete("test_key")
    client.close()


def example_setnx_with_expiry():
    """带过期时间的 SETNX 示例"""
    print("\n" + "=" * 50)
    print("带过期时间的 SETNX 示例")
    print("=" * 50)

    client = RedisClient()

    # 清理测试键
    client.delete("test_key_ex")

    # 设置带过期时间的键（5秒后过期）
    result = client.set_with_nx("test_key_ex", "temp_value", ex=5)
    print(f"设置成功: {result}")  # True

    # 立即尝试再次设置 - 应该失败
    result2 = client.set_with_nx("test_key_ex", "new_value", ex=5)
    print(f"再次设置: {result2}")  # False

    print("等待键过期...")
    time.sleep(6)

    # 键过期后再次设置 - 应该成功
    result3 = client.set_with_nx("test_key_ex", "new_value", ex=5)
    print(f"过期后设置: {result3}")  # True

    # 清理
    client.delete("test_key_ex")
    client.close()


def example_distributed_lock():
    """分布式锁示例"""
    print("\n" + "=" * 50)
    print("分布式锁示例")
    print("=" * 50)

    client = RedisClient()

    # 使用上下文管理器
    with distributed_lock(client, "resource_1", expire_seconds=10) as lock:
        if lock.locked:
            print("成功获取锁，执行业务逻辑...")
            time.sleep(2)
            print("业务逻辑执行完成")
        else:
            print("获取锁失败")

    print("锁已自动释放")

    # 手动使用锁
    lock = DistributedLock(client, "resource_2", expire_seconds=10)

    if lock.acquire(blocking=False):
        try:
            print("手动获取锁成功")
            # 延长锁的有效期
            lock.extend(additional_seconds=30)
            print("锁已延长")
        finally:
            lock.release()
            print("锁已释放")
    else:
        print("获取锁失败")

    client.close()


if __name__ == "__main__":
    print("Redis SETNX 示例程序")
    print("请确保 Redis 服务器正在运行于 localhost:6379\n")

    try:
        example_basic_setnx()
        example_setnx_with_expiry()
        example_distributed_lock()
    except redis.ConnectionError:
        print("错误: 无法连接到 Redis 服务器")
        print("请确保 Redis 正在运行: redis-server")
