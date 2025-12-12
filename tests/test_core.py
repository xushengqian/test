"""UUID Bridge 核心模块测试"""

import uuid
import pytest
from uuid_bridge import UUIDBridge


class TestUUIDBridge:
    """UUIDBridge 类测试"""
    
    def test_generate_v1(self):
        """测试生成 UUID v1"""
        u = UUIDBridge.generate_v1()
        assert isinstance(u, uuid.UUID)
        assert u.version == 1
    
    def test_generate_v4(self):
        """测试生成 UUID v4"""
        u = UUIDBridge.generate_v4()
        assert isinstance(u, uuid.UUID)
        assert u.version == 4
    
    def test_generate_v5(self):
        """测试生成 UUID v5"""
        bridge = UUIDBridge()
        u = bridge.generate_v5("test")
        assert isinstance(u, uuid.UUID)
        assert u.version == 5
        
        # 相同输入应该生成相同的 UUID
        u2 = bridge.generate_v5("test")
        assert u == u2
    
    def test_generate_v5_with_namespace(self):
        """测试使用自定义命名空间生成 UUID v5"""
        bridge = UUIDBridge()
        u1 = bridge.generate_v5("test", uuid.NAMESPACE_DNS)
        u2 = bridge.generate_v5("test", uuid.NAMESPACE_URL)
        assert u1 != u2
    
    def test_parse_standard_format(self):
        """测试解析标准格式 UUID"""
        uuid_str = "550e8400-e29b-41d4-a716-446655440000"
        u = UUIDBridge.parse(uuid_str)
        assert str(u) == uuid_str
    
    def test_parse_no_dashes(self):
        """测试解析无连字符格式 UUID"""
        uuid_str = "550e8400e29b41d4a716446655440000"
        u = UUIDBridge.parse(uuid_str)
        assert str(u) == "550e8400-e29b-41d4-a716-446655440000"
    
    def test_parse_with_whitespace(self):
        """测试解析带空白的 UUID"""
        uuid_str = "  550e8400-e29b-41d4-a716-446655440000  "
        u = UUIDBridge.parse(uuid_str)
        assert str(u) == "550e8400-e29b-41d4-a716-446655440000"
    
    def test_parse_invalid(self):
        """测试解析无效 UUID"""
        with pytest.raises(ValueError):
            UUIDBridge.parse("invalid-uuid")
    
    def test_is_valid_true(self):
        """测试有效 UUID 验证"""
        assert UUIDBridge.is_valid("550e8400-e29b-41d4-a716-446655440000")
        assert UUIDBridge.is_valid("550e8400e29b41d4a716446655440000")
    
    def test_is_valid_false(self):
        """测试无效 UUID 验证"""
        assert not UUIDBridge.is_valid("invalid")
        assert not UUIDBridge.is_valid("550e8400-e29b-41d4-a716")
        assert not UUIDBridge.is_valid("")
    
    def test_get_version(self):
        """测试获取 UUID 版本"""
        u1 = UUIDBridge.generate_v1()
        u4 = UUIDBridge.generate_v4()
        
        assert UUIDBridge.get_version(u1) == 1
        assert UUIDBridge.get_version(u4) == 4
        assert UUIDBridge.get_version(str(u4)) == 4
    
    def test_get_timestamp_v1(self):
        """测试获取 UUID v1 时间戳"""
        u = UUIDBridge.generate_v1()
        timestamp = UUIDBridge.get_timestamp(u)
        assert timestamp is not None
        assert isinstance(timestamp, int)
    
    def test_get_timestamp_v4(self):
        """测试 UUID v4 没有时间戳"""
        u = UUIDBridge.generate_v4()
        timestamp = UUIDBridge.get_timestamp(u)
        assert timestamp is None
    
    def test_default_namespace(self):
        """测试默认命名空间"""
        bridge = UUIDBridge()
        assert bridge.default_namespace == uuid.NAMESPACE_DNS
        
        bridge.set_default_namespace(uuid.NAMESPACE_URL)
        assert bridge.default_namespace == uuid.NAMESPACE_URL
    
    def test_custom_default_namespace(self):
        """测试自定义默认命名空间"""
        custom_ns = uuid.uuid4()
        bridge = UUIDBridge(default_namespace=custom_ns)
        assert bridge.default_namespace == custom_ns
