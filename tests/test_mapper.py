"""UUID 映射器测试"""

import uuid
import json
import tempfile
from pathlib import Path
import pytest
from uuid_bridge import UUIDMapper


class TestUUIDMapper:
    """UUIDMapper 类测试"""
    
    @pytest.fixture
    def mapper(self):
        """创建测试映射器"""
        return UUIDMapper(auto_generate=False)
    
    @pytest.fixture
    def auto_mapper(self):
        """创建自动生成的映射器"""
        return UUIDMapper(auto_generate=True)
    
    def test_register_with_auto_uuid(self, mapper):
        """测试自动生成 UUID 注册"""
        uid = mapper.register("test_key")
        assert isinstance(uid, uuid.UUID)
        assert mapper.get_uuid("test_key") == uid
    
    def test_register_with_uuid_object(self, mapper):
        """测试使用 UUID 对象注册"""
        uid = uuid.uuid4()
        result = mapper.register("test_key", uid)
        assert result == uid
        assert mapper.get_uuid("test_key") == uid
    
    def test_register_with_uuid_string(self, mapper):
        """测试使用 UUID 字符串注册"""
        uid_str = "550e8400-e29b-41d4-a716-446655440000"
        result = mapper.register("test_key", uid_str)
        assert str(result) == uid_str
    
    def test_register_duplicate_key(self, mapper):
        """测试注册重复 key"""
        mapper.register("test_key")
        with pytest.raises(ValueError, match="already registered"):
            mapper.register("test_key")
    
    def test_register_duplicate_uuid(self, mapper):
        """测试注册重复 UUID"""
        uid = uuid.uuid4()
        mapper.register("key1", uid)
        with pytest.raises(ValueError, match="already registered"):
            mapper.register("key2", uid)
    
    def test_get_uuid_not_found(self, mapper):
        """测试获取不存在的 key"""
        result = mapper.get_uuid("nonexistent")
        assert result is None
    
    def test_get_uuid_auto_generate(self, auto_mapper):
        """测试自动生成模式"""
        uid = auto_mapper.get_uuid("auto_key")
        assert isinstance(uid, uuid.UUID)
        
        # 再次获取应该返回相同的 UUID
        uid2 = auto_mapper.get_uuid("auto_key")
        assert uid == uid2
    
    def test_get_key(self, mapper):
        """测试根据 UUID 获取 key"""
        uid = mapper.register("test_key")
        key = mapper.get_key(uid)
        assert key == "test_key"
    
    def test_get_key_from_string(self, mapper):
        """测试从字符串获取 key"""
        uid = mapper.register("test_key")
        key = mapper.get_key(str(uid))
        assert key == "test_key"
    
    def test_get_key_not_found(self, mapper):
        """测试获取不存在的 UUID"""
        result = mapper.get_key(uuid.uuid4())
        assert result is None
    
    def test_metadata(self, mapper):
        """测试元数据功能"""
        metadata = {"name": "Test", "version": 1}
        mapper.register("test_key", metadata=metadata)
        
        result = mapper.get_metadata("test_key")
        assert result == metadata
    
    def test_set_metadata(self, mapper):
        """测试设置元数据"""
        mapper.register("test_key")
        mapper.set_metadata("test_key", {"name": "Updated"})
        
        result = mapper.get_metadata("test_key")
        assert result == {"name": "Updated"}
    
    def test_set_metadata_key_not_found(self, mapper):
        """测试对不存在的 key 设置元数据"""
        with pytest.raises(KeyError):
            mapper.set_metadata("nonexistent", {"name": "Test"})
    
    def test_update_metadata(self, mapper):
        """测试更新元数据"""
        mapper.register("test_key", metadata={"name": "Test"})
        mapper.update_metadata("test_key", version=2, active=True)
        
        result = mapper.get_metadata("test_key")
        assert result == {"name": "Test", "version": 2, "active": True}
    
    def test_unregister(self, mapper):
        """测试取消注册"""
        uid = mapper.register("test_key")
        result = mapper.unregister("test_key")
        
        assert result == uid
        assert mapper.get_uuid("test_key") is None
        assert mapper.get_key(uid) is None
    
    def test_unregister_not_found(self, mapper):
        """测试取消注册不存在的 key"""
        result = mapper.unregister("nonexistent")
        assert result is None
    
    def test_unregister_by_uuid(self, mapper):
        """测试根据 UUID 取消注册"""
        uid = mapper.register("test_key")
        result = mapper.unregister_by_uuid(uid)
        
        assert result == "test_key"
        assert mapper.get_uuid("test_key") is None
    
    def test_contains_key(self, mapper):
        """测试 key 存在检查"""
        mapper.register("test_key")
        
        assert mapper.contains_key("test_key")
        assert not mapper.contains_key("nonexistent")
    
    def test_contains_uuid(self, mapper):
        """测试 UUID 存在检查"""
        uid = mapper.register("test_key")
        
        assert mapper.contains_uuid(uid)
        assert not mapper.contains_uuid(uuid.uuid4())
    
    def test_len(self, mapper):
        """测试长度"""
        assert len(mapper) == 0
        mapper.register("key1")
        assert len(mapper) == 1
        mapper.register("key2")
        assert len(mapper) == 2
    
    def test_contains(self, mapper):
        """测试 in 操作符"""
        mapper.register("test_key")
        
        assert "test_key" in mapper
        assert "nonexistent" not in mapper
    
    def test_iter(self, mapper):
        """测试迭代"""
        mapper.register("key1")
        mapper.register("key2")
        mapper.register("key3")
        
        keys = list(mapper)
        assert sorted(keys) == ["key1", "key2", "key3"]
    
    def test_keys(self, mapper):
        """测试获取所有 key"""
        mapper.register("key1")
        mapper.register("key2")
        
        keys = mapper.keys()
        assert sorted(keys) == ["key1", "key2"]
    
    def test_uuids(self, mapper):
        """测试获取所有 UUID"""
        uid1 = mapper.register("key1")
        uid2 = mapper.register("key2")
        
        uuids = mapper.uuids()
        assert len(uuids) == 2
        assert uid1 in uuids
        assert uid2 in uuids
    
    def test_items(self, mapper):
        """测试获取所有项"""
        uid1 = mapper.register("key1")
        uid2 = mapper.register("key2")
        
        items = mapper.items()
        assert len(items) == 2
        assert ("key1", uid1) in items
        assert ("key2", uid2) in items
    
    def test_clear(self, mapper):
        """测试清空"""
        mapper.register("key1")
        mapper.register("key2")
        
        mapper.clear()
        
        assert len(mapper) == 0
        assert mapper.get_uuid("key1") is None
    
    def test_save_and_load(self, mapper):
        """测试保存和加载"""
        uid = mapper.register("test_key", metadata={"name": "Test"})
        
        with tempfile.TemporaryDirectory() as tmpdir:
            filepath = Path(tmpdir) / "mappings.json"
            mapper.save(filepath)
            
            # 验证文件内容
            with open(filepath) as f:
                data = json.load(f)
            assert "mappings" in data
            assert "test_key" in data["mappings"]
            
            # 加载到新的映射器
            new_mapper = UUIDMapper(auto_generate=False)
            new_mapper.load(filepath)
            
            assert new_mapper.get_uuid("test_key") == uid
            assert new_mapper.get_metadata("test_key") == {"name": "Test"}
    
    def test_to_dict(self, mapper):
        """测试导出为字典"""
        mapper.register("test_key", metadata={"name": "Test"})
        
        data = mapper.to_dict()
        
        assert "mappings" in data
        assert "metadata" in data
        assert "test_key" in data["mappings"]
    
    def test_from_dict(self):
        """测试从字典创建"""
        uid = str(uuid.uuid4())
        data = {
            "mappings": {"test_key": uid},
            "metadata": {"test_key": {"name": "Test"}}
        }
        
        mapper = UUIDMapper.from_dict(data)
        
        assert str(mapper.get_uuid("test_key")) == uid
        assert mapper.get_metadata("test_key") == {"name": "Test"}
