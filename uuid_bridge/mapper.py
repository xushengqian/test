"""
UUID 映射器模块 - 提供 UUID 与自定义标识符的双向映射功能
"""

import uuid
import json
import threading
from typing import Dict, Optional, Union, Any, Iterator
from pathlib import Path


class UUIDMapper:
    """
    UUID 映射器 - 管理 UUID 与自定义标识符之间的双向映射
    
    特性:
    - 线程安全的双向映射
    - 支持持久化到 JSON 文件
    - 支持自动生成 UUID
    - 支持元数据存储
    """
    
    def __init__(self, auto_generate: bool = True):
        """
        初始化映射器
        
        Args:
            auto_generate: 当查询不存在的 key 时是否自动生成 UUID
        """
        self._uuid_to_key: Dict[str, str] = {}
        self._key_to_uuid: Dict[str, str] = {}
        self._metadata: Dict[str, Dict[str, Any]] = {}
        self._auto_generate = auto_generate
        self._lock = threading.RLock()
    
    def register(
        self, 
        key: str, 
        u: Optional[Union[uuid.UUID, str]] = None,
        metadata: Optional[Dict[str, Any]] = None
    ) -> uuid.UUID:
        """
        注册一个 key 与 UUID 的映射
        
        Args:
            key: 自定义标识符
            u: UUID 对象或字符串，如果为 None 则自动生成
            metadata: 可选的元数据
            
        Returns:
            uuid.UUID: 关联的 UUID
            
        Raises:
            ValueError: 如果 key 或 UUID 已存在映射
        """
        with self._lock:
            if key in self._key_to_uuid:
                raise ValueError(f"Key '{key}' already registered")
            
            if u is None:
                uid = uuid.uuid4()
            elif isinstance(u, str):
                uid = uuid.UUID(u)
            else:
                uid = u
            
            uuid_str = str(uid)
            
            if uuid_str in self._uuid_to_key:
                raise ValueError(f"UUID '{uuid_str}' already registered")
            
            self._key_to_uuid[key] = uuid_str
            self._uuid_to_key[uuid_str] = key
            
            if metadata:
                self._metadata[key] = metadata
            
            return uid
    
    def get_uuid(self, key: str) -> Optional[uuid.UUID]:
        """
        根据 key 获取 UUID
        
        Args:
            key: 自定义标识符
            
        Returns:
            Optional[uuid.UUID]: 关联的 UUID，如果不存在且 auto_generate 为 True 则自动注册
        """
        with self._lock:
            if key in self._key_to_uuid:
                return uuid.UUID(self._key_to_uuid[key])
            
            if self._auto_generate:
                return self.register(key)
            
            return None
    
    def get_key(self, u: Union[uuid.UUID, str]) -> Optional[str]:
        """
        根据 UUID 获取 key
        
        Args:
            u: UUID 对象或字符串
            
        Returns:
            Optional[str]: 关联的 key，如果不存在返回 None
        """
        uuid_str = str(u) if isinstance(u, uuid.UUID) else str(uuid.UUID(u))
        with self._lock:
            return self._uuid_to_key.get(uuid_str)
    
    def get_metadata(self, key: str) -> Optional[Dict[str, Any]]:
        """
        获取 key 的元数据
        
        Args:
            key: 自定义标识符
            
        Returns:
            Optional[Dict[str, Any]]: 元数据字典
        """
        with self._lock:
            return self._metadata.get(key)
    
    def set_metadata(self, key: str, metadata: Dict[str, Any]) -> None:
        """
        设置 key 的元数据
        
        Args:
            key: 自定义标识符
            metadata: 元数据字典
            
        Raises:
            KeyError: 如果 key 不存在
        """
        with self._lock:
            if key not in self._key_to_uuid:
                raise KeyError(f"Key '{key}' not found")
            self._metadata[key] = metadata
    
    def update_metadata(self, key: str, **kwargs) -> None:
        """
        更新 key 的元数据
        
        Args:
            key: 自定义标识符
            **kwargs: 要更新的元数据键值对
        """
        with self._lock:
            if key not in self._key_to_uuid:
                raise KeyError(f"Key '{key}' not found")
            if key not in self._metadata:
                self._metadata[key] = {}
            self._metadata[key].update(kwargs)
    
    def unregister(self, key: str) -> Optional[uuid.UUID]:
        """
        取消注册一个 key
        
        Args:
            key: 自定义标识符
            
        Returns:
            Optional[uuid.UUID]: 被移除的 UUID，如果 key 不存在返回 None
        """
        with self._lock:
            if key not in self._key_to_uuid:
                return None
            
            uuid_str = self._key_to_uuid.pop(key)
            del self._uuid_to_key[uuid_str]
            self._metadata.pop(key, None)
            
            return uuid.UUID(uuid_str)
    
    def unregister_by_uuid(self, u: Union[uuid.UUID, str]) -> Optional[str]:
        """
        根据 UUID 取消注册
        
        Args:
            u: UUID 对象或字符串
            
        Returns:
            Optional[str]: 被移除的 key，如果 UUID 不存在返回 None
        """
        uuid_str = str(u) if isinstance(u, uuid.UUID) else str(uuid.UUID(u))
        with self._lock:
            if uuid_str not in self._uuid_to_key:
                return None
            
            key = self._uuid_to_key.pop(uuid_str)
            del self._key_to_uuid[key]
            self._metadata.pop(key, None)
            
            return key
    
    def contains_key(self, key: str) -> bool:
        """检查 key 是否已注册"""
        with self._lock:
            return key in self._key_to_uuid
    
    def contains_uuid(self, u: Union[uuid.UUID, str]) -> bool:
        """检查 UUID 是否已注册"""
        uuid_str = str(u) if isinstance(u, uuid.UUID) else str(uuid.UUID(u))
        with self._lock:
            return uuid_str in self._uuid_to_key
    
    def __len__(self) -> int:
        """返回映射数量"""
        with self._lock:
            return len(self._key_to_uuid)
    
    def __contains__(self, key: str) -> bool:
        """检查 key 是否存在"""
        return self.contains_key(key)
    
    def __iter__(self) -> Iterator[str]:
        """迭代所有 key"""
        with self._lock:
            return iter(list(self._key_to_uuid.keys()))
    
    def keys(self) -> list:
        """返回所有 key 的列表"""
        with self._lock:
            return list(self._key_to_uuid.keys())
    
    def uuids(self) -> list:
        """返回所有 UUID 的列表"""
        with self._lock:
            return [uuid.UUID(u) for u in self._uuid_to_key.keys()]
    
    def items(self) -> list:
        """返回所有 (key, uuid) 元组的列表"""
        with self._lock:
            return [(k, uuid.UUID(v)) for k, v in self._key_to_uuid.items()]
    
    def clear(self) -> None:
        """清空所有映射"""
        with self._lock:
            self._key_to_uuid.clear()
            self._uuid_to_key.clear()
            self._metadata.clear()
    
    def save(self, filepath: Union[str, Path]) -> None:
        """
        保存映射到 JSON 文件
        
        Args:
            filepath: 文件路径
        """
        with self._lock:
            data = {
                "mappings": self._key_to_uuid,
                "metadata": self._metadata
            }
            
            path = Path(filepath)
            path.parent.mkdir(parents=True, exist_ok=True)
            
            with open(path, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
    
    def load(self, filepath: Union[str, Path]) -> None:
        """
        从 JSON 文件加载映射
        
        Args:
            filepath: 文件路径
        """
        path = Path(filepath)
        
        with open(path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        with self._lock:
            self._key_to_uuid = data.get("mappings", {})
            self._uuid_to_key = {v: k for k, v in self._key_to_uuid.items()}
            self._metadata = data.get("metadata", {})
    
    def to_dict(self) -> Dict[str, Any]:
        """
        导出为字典
        
        Returns:
            Dict[str, Any]: 包含所有映射和元数据的字典
        """
        with self._lock:
            return {
                "mappings": dict(self._key_to_uuid),
                "metadata": dict(self._metadata)
            }
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any], auto_generate: bool = True) -> "UUIDMapper":
        """
        从字典创建映射器
        
        Args:
            data: 包含映射和元数据的字典
            auto_generate: 是否自动生成 UUID
            
        Returns:
            UUIDMapper: 新的映射器实例
        """
        mapper = cls(auto_generate=auto_generate)
        mapper._key_to_uuid = data.get("mappings", {})
        mapper._uuid_to_key = {v: k for k, v in mapper._key_to_uuid.items()}
        mapper._metadata = data.get("metadata", {})
        return mapper
