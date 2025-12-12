"""UUID 转换器测试"""

import uuid
import pytest
from uuid_bridge import UUIDConverter


class TestUUIDConverter:
    """UUIDConverter 类测试"""
    
    @pytest.fixture
    def sample_uuid(self):
        """测试用的固定 UUID"""
        return uuid.UUID("550e8400-e29b-41d4-a716-446655440000")
    
    def test_to_standard(self, sample_uuid):
        """测试转换为标准格式"""
        result = UUIDConverter.to_standard(sample_uuid)
        assert result == "550e8400-e29b-41d4-a716-446655440000"
    
    def test_to_standard_from_string(self):
        """测试从字符串转换为标准格式"""
        result = UUIDConverter.to_standard("550e8400e29b41d4a716446655440000")
        assert result == "550e8400-e29b-41d4-a716-446655440000"
    
    def test_to_hex(self, sample_uuid):
        """测试转换为十六进制格式"""
        result = UUIDConverter.to_hex(sample_uuid)
        assert result == "550e8400e29b41d4a716446655440000"
        assert "-" not in result
    
    def test_to_base64(self, sample_uuid):
        """测试转换为 Base64"""
        result = UUIDConverter.to_base64(sample_uuid)
        assert isinstance(result, str)
        assert "=" not in result  # 无填充
    
    def test_from_base64_roundtrip(self, sample_uuid):
        """测试 Base64 往返转换"""
        encoded = UUIDConverter.to_base64(sample_uuid)
        decoded = UUIDConverter.from_base64(encoded)
        assert decoded == sample_uuid
    
    def test_base64_non_url_safe(self, sample_uuid):
        """测试非 URL 安全的 Base64"""
        encoded = UUIDConverter.to_base64(sample_uuid, url_safe=False)
        decoded = UUIDConverter.from_base64(encoded, url_safe=False)
        assert decoded == sample_uuid
    
    def test_to_int(self, sample_uuid):
        """测试转换为整数"""
        result = UUIDConverter.to_int(sample_uuid)
        assert isinstance(result, int)
        assert result == sample_uuid.int
    
    def test_from_int_roundtrip(self, sample_uuid):
        """测试整数往返转换"""
        int_value = UUIDConverter.to_int(sample_uuid)
        decoded = UUIDConverter.from_int(int_value)
        assert decoded == sample_uuid
    
    def test_to_bytes(self, sample_uuid):
        """测试转换为字节"""
        result = UUIDConverter.to_bytes(sample_uuid)
        assert isinstance(result, bytes)
        assert len(result) == 16
    
    def test_from_bytes_roundtrip(self, sample_uuid):
        """测试字节往返转换"""
        byte_value = UUIDConverter.to_bytes(sample_uuid)
        decoded = UUIDConverter.from_bytes(byte_value)
        assert decoded == sample_uuid
    
    def test_to_urn(self, sample_uuid):
        """测试转换为 URN"""
        result = UUIDConverter.to_urn(sample_uuid)
        assert result.startswith("urn:uuid:")
        assert result == "urn:uuid:550e8400-e29b-41d4-a716-446655440000"
    
    def test_from_urn_roundtrip(self, sample_uuid):
        """测试 URN 往返转换"""
        urn = UUIDConverter.to_urn(sample_uuid)
        decoded = UUIDConverter.from_urn(urn)
        assert decoded == sample_uuid
    
    def test_to_uppercase(self, sample_uuid):
        """测试转换为大写"""
        result = UUIDConverter.to_uppercase(sample_uuid)
        assert result == "550E8400-E29B-41D4-A716-446655440000"
        assert result.isupper()
    
    def test_to_short_id_default(self, sample_uuid):
        """测试生成默认长度短 ID"""
        result = UUIDConverter.to_short_id(sample_uuid)
        assert len(result) == 8
        assert result == "550e8400"
    
    def test_to_short_id_custom_length(self, sample_uuid):
        """测试生成自定义长度短 ID"""
        result = UUIDConverter.to_short_id(sample_uuid, length=12)
        assert len(result) == 12
    
    def test_ensure_uuid_from_string(self):
        """测试从字符串确保 UUID"""
        result = UUIDConverter.to_hex("550e8400-e29b-41d4-a716-446655440000")
        assert result == "550e8400e29b41d4a716446655440000"
