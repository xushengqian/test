# Redis SETNX 实现

## 简介

SETNX（SET if Not eXists）是 Redis 的原子操作命令，仅在键不存在时设置值。这是实现分布式锁的基础。

## 功能特性

- ✅ 基本 SETNX 操作
- ✅ 带过期时间的 SET NX（推荐方式）
- ✅ 分布式锁实现
- ✅ 锁的自动续期
- ✅ 使用 Lua 脚本确保原子性释放锁
- ✅ 上下文管理器支持

## 安装依赖

```bash
pip install -r requirements.txt
```

## 使用方法

### 1. 基本 SETNX

```python
from redis_setnx import RedisClient

client = RedisClient()

# 仅在键不存在时设置值
if client.setnx("my_key", "my_value"):
    print("设置成功")
else:
    print("键已存在")
```

### 2. 带过期时间的 SETNX

```python
# 设置值，10秒后自动过期
client.set_with_nx("my_key", "my_value", ex=10)
```

### 3. 分布式锁

```python
from redis_setnx import RedisClient, distributed_lock

client = RedisClient()

# 使用上下文管理器（推荐）
with distributed_lock(client, "resource_name", expire_seconds=30) as lock:
    if lock.locked:
        # 执行需要加锁的操作
        do_something()
```

### 4. 手动管理锁

```python
from redis_setnx import RedisClient, DistributedLock

client = RedisClient()
lock = DistributedLock(client, "resource_name", expire_seconds=30)

if lock.acquire(blocking=True, timeout=5):
    try:
        # 执行业务逻辑
        do_something()
        # 可以延长锁的有效期
        lock.extend(additional_seconds=30)
    finally:
        lock.release()
```

## 运行示例

```bash
# 确保 Redis 服务器正在运行
redis-server

# 运行示例
python redis_setnx.py
```

## SETNX vs SET NX

| 命令 | 说明 |
|------|------|
| `SETNX key value` | 旧命令，不支持过期时间 |
| `SET key value NX EX seconds` | 推荐，原子设置值和过期时间 |

## 分布式锁注意事项

1. **唯一标识**: 每个锁持有者使用 UUID 作为值，防止误删其他进程的锁
2. **过期时间**: 防止死锁，锁会自动过期
3. **原子释放**: 使用 Lua 脚本确保只释放自己的锁
4. **续期机制**: 长时间任务可以延长锁的有效期

## 参考资料

- [Redis SET 命令](https://redis.io/commands/set/)
- [Redis 分布式锁](https://redis.io/docs/manual/patterns/distributed-locks/)
