# UUID Bridge 使用指南

## 目录
1. [简介](#简介)
2. [安装](#安装)
3. [快速开始](#快速开始)
4. [详细使用说明](#详细使用说明)
5. [实际应用场景](#实际应用场景)
6. [常见问题](#常见问题)

## 简介

`uuid_bridge` 是一个功能完整的 Python UUID 桥接库，提供以下三大核心功能：

- **UUID 生成**: 支持 UUID v1 (基于时间戳)、v4 (随机)、v5 (基于命名空间)
- **格式转换**: 标准格式、无连字符、Base64、整数、字节、URN 等多种格式互转
- **双向映射**: 线程安全的 UUID 与自定义标识符双向映射，支持元数据和持久化

## 安装

```bash
# 安装依赖
pip install -r requirements.txt

# 或者直接安装包
pip install -e .
```

## 快速开始

### 1. UUID 生成

```python
from uuid_bridge import UUIDBridge

# 创建桥接器实例
bridge = UUIDBridge()

# 生成 UUID v4 (随机) - 最常用
uuid_v4 = bridge.generate_v4()
print(uuid_v4)  # 例如: 550e8400-e29b-41d4-a716-446655440000

# 生成 UUID v1 (基于时间戳和 MAC 地址)
uuid_v1 = bridge.generate_v1()
print(uuid_v1)  # 例如: 6ba7b810-9dad-11d1-80b4-00c04fd430c8

# 生成 UUID v5 (基于命名空间和名称) - 相同输入产生相同 UUID
uuid_v5 = bridge.generate_v5("my-resource")
print(uuid_v5)  # 每次调用相同参数都会产生相同的 UUID

# 使用自定义命名空间
from uuid_bridge import UUIDBridge
import uuid

bridge = UUIDBridge(default_namespace=uuid.NAMESPACE_URL)
uuid_v5_custom = bridge.generate_v5("https://example.com/user/123")

# 验证 UUID
is_valid = bridge.is_valid("550e8400-e29b-41d4-a716-446655440000")  # True
is_invalid = bridge.is_valid("invalid-uuid")  # False

# 解析 UUID (支持多种格式)
parsed1 = bridge.parse("550e8400-e29b-41d4-a716-446655440000")  # 标准格式
parsed2 = bridge.parse("550e8400e29b41d4a716446655440000")      # 无连字符格式

# 获取 UUID 版本
version = bridge.get_version(uuid_v4)  # 4

# 获取 UUID v1 的时间戳
timestamp = bridge.get_timestamp(uuid_v1)  # 时间戳值
```

### 2. 格式转换

```python
from uuid_bridge import UUIDConverter
import uuid

# 创建一个 UUID
u = uuid.uuid4()

# 转换为标准格式 (8-4-4-4-12)
standard = UUIDConverter.to_standard(u)
print(standard)  # 550e8400-e29b-41d4-a716-446655440000

# 转换为无连字符的十六进制格式
hex_str = UUIDConverter.to_hex(u)
print(hex_str)  # 550e8400e29b41d4a716446655440000

# 转换为 Base64 编码 (URL 安全)
base64_str = UUIDConverter.to_base64(u)
print(base64_str)  # VQ6EAOKbQdSnFkRmVUQAAA

# 转换为 URN 格式
urn = UUIDConverter.to_urn(u)
print(urn)  # urn:uuid:550e8400-e29b-41d4-a716-446655440000

# 转换为整数 (128 位)
int_val = UUIDConverter.to_int(u)
print(int_val)  # 113059749145936325402354257176981405696

# 转换为字节
bytes_data = UUIDConverter.to_bytes(u)
print(bytes_data)  # b'U\x0e\x84\x00\xe2\x9bA\xd4\xa7\x16DfUD\x00\x00'

# 生成短 ID (取前 8 个字符)
short_id = UUIDConverter.to_short_id(u, length=8)
print(short_id)  # 550e8400

# 从各种格式还原
from_base64 = UUIDConverter.from_base64(base64_str)
from_int = UUIDConverter.from_int(int_val)
from_urn = UUIDConverter.from_urn(urn)
from_bytes = UUIDConverter.from_bytes(bytes_data)

# 验证还原后的 UUID 是否相同
assert from_base64 == u
assert from_int == u
assert from_urn == u
assert from_bytes == u
```

### 3. 双向映射

```python
from uuid_bridge import UUIDMapper

# 创建映射器 (auto_generate=True 表示自动生成 UUID)
mapper = UUIDMapper(auto_generate=True)

# 方式 1: 注册映射 (手动指定 UUID)
user_uuid = mapper.register(
    "user:123", 
    metadata={"name": "张三", "role": "admin", "email": "zhangsan@example.com"}
)
print(user_uuid)  # 生成的 UUID

# 方式 2: 自动生成模式 (获取不存在的 key 会自动创建)
order_uuid = mapper.get_uuid("order:456")
print(order_uuid)  # 自动生成的 UUID

# 双向查询
uuid_from_key = mapper.get_uuid("user:123")
key_from_uuid = mapper.get_key(user_uuid)
print(f"Key: {key_from_uuid}, UUID: {uuid_from_key}")

# 元数据操作
# 获取元数据
metadata = mapper.get_metadata("user:123")
print(metadata)  # {'name': '张三', 'role': 'admin', 'email': 'zhangsan@example.com'}

# 更新元数据
mapper.update_metadata("user:123", last_login="2024-01-01", status="active")

# 设置完整元数据
mapper.set_metadata("user:123", {"name": "李四", "role": "user"})

# 检查映射是否存在
if "user:123" in mapper:
    print("用户已注册")

if mapper.contains_key("user:123"):
    print("Key 存在")

if mapper.contains_uuid(user_uuid):
    print("UUID 存在")

# 遍历所有映射
for key in mapper:
    uuid_val = mapper.get_uuid(key)
    print(f"{key} -> {uuid_val}")

# 获取所有 keys 和 UUIDs
all_keys = mapper.keys()
all_uuids = mapper.uuids()
all_items = mapper.items()  # [(key, uuid), ...]

# 取消注册
removed_uuid = mapper.unregister("user:123")
# 或通过 UUID 取消注册
removed_key = mapper.unregister_by_uuid(user_uuid)

# 持久化
mapper.save("mappings.json")

# 加载
new_mapper = UUIDMapper()
new_mapper.load("mappings.json")

# 导出为字典
data = mapper.to_dict()

# 从字典创建
mapper2 = UUIDMapper.from_dict(data)
```

## 详细使用说明

### UUIDBridge 类

#### 初始化

```python
from uuid_bridge import UUIDBridge
import uuid

# 使用默认命名空间 (NAMESPACE_DNS)
bridge1 = UUIDBridge()

# 使用自定义命名空间
bridge2 = UUIDBridge(default_namespace=uuid.NAMESPACE_URL)

# 设置默认命名空间
bridge1.set_default_namespace(uuid.NAMESPACE_OID)
```

#### 预定义命名空间

```python
UUIDBridge.NAMESPACE_DNS   # DNS 命名空间
UUIDBridge.NAMESPACE_URL   # URL 命名空间
UUIDBridge.NAMESPACE_OID   # OID 命名空间
UUIDBridge.NAMESPACE_X500  # X.500 命名空间
```

#### UUID v5 的使用场景

UUID v5 基于 SHA-1 哈希，相同输入总是产生相同输出，适合用于：

- 资源标识符映射
- 内容寻址
- 确定性 UUID 生成

```python
bridge = UUIDBridge()

# 为 URL 生成固定 UUID
url_uuid = bridge.generate_v5("https://example.com/api/v1/users/123")
# 每次调用都会产生相同的 UUID

# 为文件内容生成 UUID
file_content = "Hello, World!"
content_uuid = bridge.generate_v5(file_content)
```

### UUIDConverter 类

#### 支持的转换格式

| 格式 | 方法 | 示例 |
|------|------|------|
| 标准格式 | `to_standard()` | `550e8400-e29b-41d4-a716-446655440000` |
| 十六进制 | `to_hex()` | `550e8400e29b41d4a716446655440000` |
| Base64 | `to_base64()` | `VQ6EAOKbQdSnFkRmVUQAAA` |
| 整数 | `to_int()` | `113059749145936325402354257176981405696` |
| 字节 | `to_bytes()` | `b'U\x0e\x84...'` |
| URN | `to_urn()` | `urn:uuid:550e8400-e29b-41d4-a716-446655440000` |
| 短 ID | `to_short_id()` | `550e8400` |

#### Base64 编码选项

```python
# URL 安全的 Base64 (默认)
base64_url = UUIDConverter.to_base64(u, url_safe=True)

# 标准 Base64
base64_std = UUIDConverter.to_base64(u, url_safe=False)

# 解码时也要指定相同的选项
uuid_from_url = UUIDConverter.from_base64(base64_url, url_safe=True)
uuid_from_std = UUIDConverter.from_base64(base64_std, url_safe=False)
```

### UUIDMapper 类

#### 线程安全

`UUIDMapper` 是线程安全的，可以在多线程环境中使用：

```python
import threading
from uuid_bridge import UUIDMapper

mapper = UUIDMapper()

def worker(key):
    uuid_val = mapper.get_uuid(key)
    print(f"{key} -> {uuid_val}")

# 多线程安全操作
threads = []
for i in range(10):
    t = threading.Thread(target=worker, args=(f"key_{i}",))
    threads.append(t)
    t.start()

for t in threads:
    t.join()
```

#### 自动生成模式

```python
# 启用自动生成 (默认)
mapper_auto = UUIDMapper(auto_generate=True)
uuid1 = mapper_auto.get_uuid("new_key")  # 自动创建并返回 UUID

# 禁用自动生成
mapper_manual = UUIDMapper(auto_generate=False)
uuid2 = mapper_manual.get_uuid("new_key")  # 返回 None
```

#### 持久化格式

保存的 JSON 文件格式：

```json
{
  "mappings": {
    "user:123": "550e8400-e29b-41d4-a716-446655440000",
    "order:456": "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
  },
  "metadata": {
    "user:123": {
      "name": "张三",
      "role": "admin"
    }
  }
}
```

## 实际应用场景

### 场景 1: 用户系统

```python
from uuid_bridge import UUIDMapper

class UserService:
    def __init__(self):
        self.mapper = UUIDMapper(auto_generate=True)
        # 加载已有映射
        try:
            self.mapper.load("users.json")
        except FileNotFoundError:
            pass
    
    def create_user(self, user_id: str, name: str, email: str):
        """创建用户并生成 UUID"""
        uuid = self.mapper.register(
            f"user:{user_id}",
            metadata={
                "name": name,
                "email": email,
                "created_at": "2024-01-01"
            }
        )
        self.mapper.save("users.json")
        return uuid
    
    def get_user_uuid(self, user_id: str):
        """根据用户 ID 获取 UUID"""
        return self.mapper.get_uuid(f"user:{user_id}")
    
    def get_user_info(self, user_id: str):
        """获取用户信息"""
        return self.mapper.get_metadata(f"user:{user_id}")
```

### 场景 2: API 响应中使用短 ID

```python
from uuid_bridge import UUIDConverter, UUIDMapper

mapper = UUIDMapper()

def create_order(order_id: str):
    """创建订单"""
    uuid = mapper.get_uuid(f"order:{order_id}")
    # 使用短 ID 在 API 响应中
    short_id = UUIDConverter.to_short_id(uuid, length=12)
    return {
        "order_id": order_id,
        "uuid": str(uuid),
        "short_id": short_id  # 更友好的显示格式
    }
```

### 场景 3: 数据库主键映射

```python
from uuid_bridge import UUIDMapper

class DatabaseMapper:
    def __init__(self):
        self.mapper = UUIDMapper()
        self.mapper.load("db_mappings.json")
    
    def get_uuid_for_record(self, table: str, record_id: int):
        """为数据库记录生成 UUID"""
        key = f"{table}:{record_id}"
        return self.mapper.get_uuid(key)
    
    def save_mappings(self):
        """保存映射"""
        self.mapper.save("db_mappings.json")
```

### 场景 4: 文件内容标识

```python
from uuid_bridge import UUIDBridge
import hashlib

bridge = UUIDBridge()

def get_file_uuid(file_path: str):
    """为文件内容生成确定性 UUID"""
    with open(file_path, 'rb') as f:
        content = f.read()
    
    # 使用文件内容的哈希生成 UUID v5
    content_hash = hashlib.sha256(content).hexdigest()
    return bridge.generate_v5(content_hash)
```

## 常见问题

### Q1: UUID v1、v4、v5 有什么区别？

- **UUID v1**: 基于时间戳和 MAC 地址，包含时间信息，可以排序
- **UUID v4**: 完全随机生成，最常用，适合大多数场景
- **UUID v5**: 基于命名空间和名称的 SHA-1 哈希，相同输入产生相同输出

### Q2: 什么时候使用 UUID v5？

当需要为相同的资源生成相同的 UUID 时使用，例如：
- 为 URL 生成固定标识符
- 为文件内容生成确定性 ID
- 资源映射场景

### Q3: 短 ID 会冲突吗？

是的，短 ID 只是 UUID 的前 N 个字符，可能存在冲突。仅用于显示目的，不要用于唯一性判断。

### Q4: 映射器是线程安全的吗？

是的，`UUIDMapper` 使用 `threading.RLock()` 保证线程安全。

### Q5: 如何批量导入映射？

```python
mapper = UUIDMapper()

# 批量注册
for i in range(1000):
    mapper.register(f"item:{i}")

# 保存
mapper.save("batch_mappings.json")
```

### Q6: 如何清空所有映射？

```python
mapper.clear()  # 清空所有映射和元数据
```

### Q7: 映射文件很大怎么办？

可以考虑：
- 使用数据库存储映射
- 按需加载部分映射
- 使用压缩存储

## 总结

`uuid_bridge` 提供了完整的 UUID 处理解决方案：

1. **UUIDBridge**: 生成和验证 UUID
2. **UUIDConverter**: 在不同格式间转换 UUID
3. **UUIDMapper**: 管理 UUID 与业务标识符的映射

根据你的需求选择合适的模块和功能即可。
