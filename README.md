# Redis SETNX 分布式锁实现

基于 Redis SETNX 命令实现的分布式锁，用于在分布式环境中确保只有一个进程可以执行关键代码段。

## 特性

- ✅ 基于 Redis SETNX 命令实现
- ✅ 自动过期机制，防止死锁
- ✅ 安全的锁释放（只有锁持有者才能释放）
- ✅ 支持阻塞和非阻塞模式
- ✅ 支持超时设置
- ✅ 上下文管理器支持，自动释放锁
- ✅ 线程安全

## 安装

```bash
pip install -r requirements.txt
```

## 快速开始

### 基本使用

```python
import redis
from redis_lock import RedisLock, create_redis_client

# 创建 Redis 客户端
redis_client = create_redis_client(host='localhost', port=6379)

# 创建锁
lock = RedisLock(redis_client, "my_lock", timeout=10)

# 使用上下文管理器（推荐）
with lock:
    # 执行关键代码
    print("执行关键业务逻辑...")
    # 锁会自动释放
```

### 手动获取和释放

```python
lock = RedisLock(redis_client, "my_lock", timeout=10)

if lock.acquire(blocking=True, timeout=5):
    try:
        # 执行关键代码
        print("执行关键业务逻辑...")
    finally:
        lock.release()
```

### 非阻塞模式

```python
lock = RedisLock(redis_client, "my_lock", timeout=10)

if lock.acquire(blocking=False):
    try:
        # 执行关键代码
        print("执行关键业务逻辑...")
    finally:
        lock.release()
else:
    print("锁已被占用，无法获取")
```

## API 文档

### RedisLock 类

#### 初始化参数

- `redis_client`: Redis 客户端实例（redis.Redis）
- `key`: 锁的键名（字符串）
- `timeout`: 锁的超时时间（秒），默认 10 秒
- `retry_interval`: 获取锁失败时的重试间隔（秒），默认 0.1 秒

#### 方法

##### `acquire(blocking=True, timeout=None) -> bool`

获取锁。

- `blocking`: 是否阻塞等待获取锁，默认 True
- `timeout`: 阻塞超时时间（秒），None 表示无限等待
- 返回: 是否成功获取锁

##### `release() -> bool`

释放锁。只有锁的持有者才能释放锁。

- 返回: 是否成功释放锁

##### `lock(blocking=True, timeout=None)`

上下文管理器方式使用锁。

- `blocking`: 是否阻塞等待获取锁
- `timeout`: 阻塞超时时间（秒）

## 实现原理

### SETNX 命令

SETNX（SET if Not eXists）是 Redis 的一个原子操作命令：
- 当键不存在时，设置键值对并返回 1
- 当键已存在时，不执行任何操作并返回 0

### 分布式锁实现

1. **获取锁**: 使用 `SET key value NX EX timeout` 命令
   - `NX`: 只有当键不存在时才设置
   - `EX`: 设置过期时间，防止死锁

2. **释放锁**: 使用 Lua 脚本确保安全释放
   - 检查锁的值是否匹配（确保是锁的持有者）
   - 只有匹配时才删除锁

3. **唯一标识符**: 每个锁实例使用 UUID 作为唯一标识符
   - 防止误释放其他进程的锁
   - 确保锁释放的安全性

## 使用示例

运行示例代码：

```bash
python example.py
```

示例包括：
1. 基本使用示例
2. 手动获取和释放锁
3. 非阻塞获取锁
4. 分布式场景模拟
5. 锁超时机制演示

## 注意事项

1. **Redis 连接**: 确保 Redis 服务器正在运行且可访问
2. **超时设置**: 合理设置锁的超时时间，既要防止死锁，又要给业务逻辑足够的执行时间
3. **网络延迟**: 在分布式环境中，考虑网络延迟对锁获取的影响
4. **时钟同步**: 虽然使用了过期时间，但在极端情况下，时钟不同步可能影响锁的行为

## 应用场景

- 分布式任务调度
- 防止重复处理
- 资源访问控制
- 分布式系统中的临界区保护

## 许可证

MIT License
