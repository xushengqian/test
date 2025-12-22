"""
Redis SETNX 分布式锁使用示例
"""

import time
import redis
from redis_lock import RedisLock, create_redis_client


def example_basic_usage():
    """基本使用示例"""
    print("=" * 50)
    print("示例 1: 基本使用")
    print("=" * 50)
    
    # 创建 Redis 客户端
    redis_client = create_redis_client()
    
    # 创建锁
    lock = RedisLock(redis_client, "my_lock", timeout=5)
    
    # 方式 1: 使用上下文管理器（推荐）
    try:
        with lock:
            print("已获取锁，执行关键代码...")
            time.sleep(2)  # 模拟业务逻辑
            print("关键代码执行完成")
    except RuntimeError as e:
        print(f"错误: {e}")
    
    print()


def example_manual_acquire_release():
    """手动获取和释放锁示例"""
    print("=" * 50)
    print("示例 2: 手动获取和释放锁")
    print("=" * 50)
    
    redis_client = create_redis_client()
    lock = RedisLock(redis_client, "manual_lock", timeout=5)
    
    if lock.acquire(blocking=True, timeout=3):
        try:
            print("已获取锁，执行关键代码...")
            time.sleep(1)
            print("关键代码执行完成")
        finally:
            lock.release()
            print("锁已释放")
    else:
        print("获取锁失败或超时")
    
    print()


def example_non_blocking():
    """非阻塞获取锁示例"""
    print("=" * 50)
    print("示例 3: 非阻塞获取锁")
    print("=" * 50)
    
    redis_client = create_redis_client()
    lock = RedisLock(redis_client, "non_blocking_lock", timeout=5)
    
    if lock.acquire(blocking=False):
        try:
            print("已获取锁，执行关键代码...")
            time.sleep(1)
        finally:
            lock.release()
    else:
        print("锁已被其他进程占用，无法获取")
    
    print()


def example_distributed_scenario():
    """模拟分布式场景"""
    print("=" * 50)
    print("示例 4: 模拟分布式场景")
    print("=" * 50)
    
    redis_client = create_redis_client()
    
    def worker(worker_id: int):
        """模拟工作进程"""
        lock = RedisLock(redis_client, "distributed_task", timeout=3)
        
        print(f"工作进程 {worker_id}: 尝试获取锁...")
        
        try:
            with lock:
                print(f"工作进程 {worker_id}: 已获取锁，开始处理任务...")
                time.sleep(1)
                print(f"工作进程 {worker_id}: 任务处理完成")
        except RuntimeError:
            print(f"工作进程 {worker_id}: 获取锁失败")
    
    # 模拟多个进程同时尝试获取锁
    import threading
    
    threads = []
    for i in range(3):
        t = threading.Thread(target=worker, args=(i + 1,))
        threads.append(t)
        t.start()
    
    # 等待所有线程完成
    for t in threads:
        t.join()
    
    print()


def example_lock_timeout():
    """锁超时示例"""
    print("=" * 50)
    print("示例 5: 锁超时机制")
    print("=" * 50)
    
    redis_client = create_redis_client()
    
    # 创建一个超时时间很短的锁
    lock = RedisLock(redis_client, "timeout_lock", timeout=2)
    
    print("获取锁（超时时间 2 秒）...")
    if lock.acquire():
        print("已获取锁")
        print("模拟长时间任务（5秒）...")
        time.sleep(5)
        print("任务完成，尝试释放锁...")
        # 此时锁可能已经自动过期
        released = lock.release()
        if released:
            print("锁已释放")
        else:
            print("锁已自动过期或被其他进程获取")
    
    print()


if __name__ == "__main__":
    try:
        # 测试 Redis 连接
        redis_client = create_redis_client()
        redis_client.ping()
        print("Redis 连接成功！\n")
        
        # 运行示例
        example_basic_usage()
        example_manual_acquire_release()
        example_non_blocking()
        example_distributed_scenario()
        example_lock_timeout()
        
    except redis.ConnectionError:
        print("错误: 无法连接到 Redis 服务器")
        print("请确保 Redis 服务器正在运行，或修改连接参数")
    except Exception as e:
        print(f"发生错误: {e}")
