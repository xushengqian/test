#!/usr/bin/env python3
"""
UUID Bridge 使用示例

演示 uuid_bridge 库的各种用法
"""

from uuid_bridge import UUIDBridge, UUIDConverter, UUIDMapper
import uuid


def example_uuid_generation():
    """示例 1: UUID 生成"""
    print("=" * 60)
    print("示例 1: UUID 生成")
    print("=" * 60)
    
    bridge = UUIDBridge()
    
    # 生成 UUID v4 (随机)
    uuid_v4 = bridge.generate_v4()
    print(f"UUID v4 (随机): {uuid_v4}")
    
    # 生成 UUID v1 (基于时间戳)
    uuid_v1 = bridge.generate_v1()
    print(f"UUID v1 (时间戳): {uuid_v1}")
    
    # 生成 UUID v5 (确定性)
    uuid_v5 = bridge.generate_v5("my-resource")
    print(f"UUID v5 (确定性): {uuid_v5}")
    
    # 验证 UUID
    print(f"\n验证 UUID:")
    print(f"  '{uuid_v4}' 是否有效: {bridge.is_valid(str(uuid_v4))}")
    print(f"  'invalid-uuid' 是否有效: {bridge.is_valid('invalid-uuid')}")
    
    # 获取版本
    print(f"\nUUID 版本:")
    print(f"  {uuid_v4} 的版本: {bridge.get_version(uuid_v4)}")
    print(f"  {uuid_v1} 的版本: {bridge.get_version(uuid_v1)}")
    
    print()


def example_format_conversion():
    """示例 2: 格式转换"""
    print("=" * 60)
    print("示例 2: 格式转换")
    print("=" * 60)
    
    # 创建一个 UUID
    u = uuid.uuid4()
    print(f"原始 UUID: {u}")
    
    # 转换为各种格式
    print(f"\n转换为各种格式:")
    print(f"  标准格式: {UUIDConverter.to_standard(u)}")
    print(f"  十六进制: {UUIDConverter.to_hex(u)}")
    print(f"  Base64: {UUIDConverter.to_base64(u)}")
    print(f"  URN: {UUIDConverter.to_urn(u)}")
    print(f"  整数: {UUIDConverter.to_int(u)}")
    print(f"  短 ID (8位): {UUIDConverter.to_short_id(u, 8)}")
    print(f"  短 ID (12位): {UUIDConverter.to_short_id(u, 12)}")
    
    # 从各种格式还原
    base64_str = UUIDConverter.to_base64(u)
    int_val = UUIDConverter.to_int(u)
    
    print(f"\n从格式还原:")
    print(f"  从 Base64 还原: {UUIDConverter.from_base64(base64_str)}")
    print(f"  从整数还原: {UUIDConverter.from_int(int_val)}")
    
    # 验证还原
    assert UUIDConverter.from_base64(base64_str) == u
    assert UUIDConverter.from_int(int_val) == u
    print("  ✓ 还原验证通过")
    
    print()


def example_mapping():
    """示例 3: 双向映射"""
    print("=" * 60)
    print("示例 3: 双向映射")
    print("=" * 60)
    
    # 创建映射器
    mapper = UUIDMapper(auto_generate=True)
    
    # 注册映射
    user_uuid = mapper.register(
        "user:123",
        metadata={"name": "张三", "role": "admin", "email": "zhangsan@example.com"}
    )
    print(f"注册用户: user:123 -> {user_uuid}")
    
    # 自动生成模式
    order_uuid = mapper.get_uuid("order:456")
    print(f"自动生成: order:456 -> {order_uuid}")
    
    # 双向查询
    print(f"\n双向查询:")
    print(f"  根据 key 获取 UUID: {mapper.get_uuid('user:123')}")
    print(f"  根据 UUID 获取 key: {mapper.get_key(user_uuid)}")
    
    # 元数据操作
    print(f"\n元数据操作:")
    metadata = mapper.get_metadata("user:123")
    print(f"  获取元数据: {metadata}")
    
    mapper.update_metadata("user:123", last_login="2024-01-01", status="active")
    updated_metadata = mapper.get_metadata("user:123")
    print(f"  更新后元数据: {updated_metadata}")
    
    # 遍历映射
    print(f"\n所有映射:")
    for key in mapper:
        uuid_val = mapper.get_uuid(key)
        meta = mapper.get_metadata(key)
        print(f"  {key} -> {uuid_val} (元数据: {meta})")
    
    print()


def example_persistence():
    """示例 4: 持久化"""
    print("=" * 60)
    print("示例 4: 持久化")
    print("=" * 60)
    
    # 创建并填充映射器
    mapper1 = UUIDMapper()
    mapper1.register("user:1", metadata={"name": "用户1"})
    mapper1.register("user:2", metadata={"name": "用户2"})
    mapper1.register("order:1", metadata={"amount": 100.0})
    
    # 保存到文件
    filepath = "example_mappings.json"
    mapper1.save(filepath)
    print(f"已保存映射到: {filepath}")
    
    # 从文件加载
    mapper2 = UUIDMapper()
    mapper2.load(filepath)
    print(f"已从文件加载映射")
    
    # 验证加载的数据
    print(f"\n加载的映射:")
    for key in mapper2:
        uuid_val = mapper2.get_uuid(key)
        meta = mapper2.get_metadata(key)
        print(f"  {key} -> {uuid_val} (元数据: {meta})")
    
    print()


def example_uuid_v5_deterministic():
    """示例 5: UUID v5 的确定性特性"""
    print("=" * 60)
    print("示例 5: UUID v5 的确定性特性")
    print("=" * 60)
    
    bridge = UUIDBridge()
    
    # 相同输入产生相同输出
    url1 = "https://example.com/api/v1/users/123"
    url2 = "https://example.com/api/v1/users/123"
    
    uuid1 = bridge.generate_v5(url1)
    uuid2 = bridge.generate_v5(url2)
    
    print(f"URL: {url1}")
    print(f"第一次生成: {uuid1}")
    print(f"第二次生成: {uuid2}")
    print(f"是否相同: {uuid1 == uuid2} ✓")
    
    # 不同输入产生不同输出
    url3 = "https://example.com/api/v1/users/456"
    uuid3 = bridge.generate_v5(url3)
    
    print(f"\n不同 URL: {url3}")
    print(f"生成的 UUID: {uuid3}")
    print(f"与之前的 UUID 不同: {uuid3 != uuid1} ✓")
    
    print()


def example_short_id_usage():
    """示例 6: 短 ID 的使用"""
    print("=" * 60)
    print("示例 6: 短 ID 的使用")
    print("=" * 60)
    
    mapper = UUIDMapper()
    
    # 创建一些资源
    resources = [
        ("user:1", {"name": "用户1"}),
        ("user:2", {"name": "用户2"}),
        ("order:1", {"amount": 100}),
    ]
    
    print("资源列表 (使用短 ID 显示):")
    for key, meta in resources:
        uuid_val = mapper.register(key, metadata=meta)
        short_id = UUIDConverter.to_short_id(uuid_val, length=12)
        print(f"  {key}:")
        print(f"    UUID: {uuid_val}")
        print(f"    短 ID: {short_id}")
        print(f"    元数据: {meta}")
    
    print()


if __name__ == "__main__":
    print("\n" + "=" * 60)
    print("UUID Bridge 使用示例")
    print("=" * 60 + "\n")
    
    # 运行所有示例
    example_uuid_generation()
    example_format_conversion()
    example_mapping()
    example_persistence()
    example_uuid_v5_deterministic()
    example_short_id_usage()
    
    print("=" * 60)
    print("所有示例运行完成！")
    print("=" * 60)
