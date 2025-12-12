"""
UUID Bridge 核心模块 - 提供 UUID 生成和基础操作功能
"""

import uuid
from typing import Optional, Union


class UUIDBridge:
    """
    UUID 桥接核心类，提供 UUID 的生成和基础操作
    
    支持的 UUID 版本:
    - UUID v1: 基于时间戳和 MAC 地址
    - UUID v4: 随机生成
    - UUID v5: 基于命名空间和名称的 SHA-1 哈希
    """
    
    # 预定义的命名空间
    NAMESPACE_DNS = uuid.NAMESPACE_DNS
    NAMESPACE_URL = uuid.NAMESPACE_URL
    NAMESPACE_OID = uuid.NAMESPACE_OID
    NAMESPACE_X500 = uuid.NAMESPACE_X500
    
    def __init__(self, default_namespace: Optional[uuid.UUID] = None):
        """
        初始化 UUID 桥接器
        
        Args:
            default_namespace: 用于 UUID v5 生成的默认命名空间
        """
        self._default_namespace = default_namespace or uuid.NAMESPACE_DNS
    
    @staticmethod
    def generate_v1() -> uuid.UUID:
        """
        生成 UUID v1 (基于时间戳)
        
        Returns:
            uuid.UUID: 生成的 UUID v1
        """
        return uuid.uuid1()
    
    @staticmethod
    def generate_v4() -> uuid.UUID:
        """
        生成 UUID v4 (随机)
        
        Returns:
            uuid.UUID: 生成的 UUID v4
        """
        return uuid.uuid4()
    
    def generate_v5(self, name: str, namespace: Optional[uuid.UUID] = None) -> uuid.UUID:
        """
        生成 UUID v5 (基于命名空间和名称)
        
        Args:
            name: 用于生成 UUID 的名称
            namespace: 命名空间 UUID，默认使用实例的默认命名空间
            
        Returns:
            uuid.UUID: 生成的 UUID v5
        """
        ns = namespace or self._default_namespace
        return uuid.uuid5(ns, name)
    
    @staticmethod
    def parse(uuid_string: str) -> uuid.UUID:
        """
        从字符串解析 UUID
        
        Args:
            uuid_string: UUID 字符串（支持多种格式）
            
        Returns:
            uuid.UUID: 解析后的 UUID 对象
            
        Raises:
            ValueError: 如果字符串不是有效的 UUID 格式
        """
        # 移除可能的前缀和空白
        cleaned = uuid_string.strip()
        
        # 处理无连字符格式
        if len(cleaned) == 32 and "-" not in cleaned:
            cleaned = f"{cleaned[:8]}-{cleaned[8:12]}-{cleaned[12:16]}-{cleaned[16:20]}-{cleaned[20:]}"
        
        return uuid.UUID(cleaned)
    
    @staticmethod
    def is_valid(uuid_string: str) -> bool:
        """
        检查字符串是否是有效的 UUID
        
        Args:
            uuid_string: 要检查的字符串
            
        Returns:
            bool: 如果是有效的 UUID 返回 True，否则返回 False
        """
        try:
            UUIDBridge.parse(uuid_string)
            return True
        except (ValueError, AttributeError):
            return False
    
    @staticmethod
    def get_version(u: Union[uuid.UUID, str]) -> int:
        """
        获取 UUID 的版本号
        
        Args:
            u: UUID 对象或字符串
            
        Returns:
            int: UUID 版本号 (1-5)
        """
        if isinstance(u, str):
            u = UUIDBridge.parse(u)
        return u.version
    
    @staticmethod
    def get_timestamp(u: Union[uuid.UUID, str]) -> Optional[int]:
        """
        获取 UUID v1 的时间戳
        
        Args:
            u: UUID 对象或字符串
            
        Returns:
            Optional[int]: UUID v1 的时间戳，如果不是 v1 则返回 None
        """
        if isinstance(u, str):
            u = UUIDBridge.parse(u)
        
        if u.version == 1:
            return u.time
        return None
    
    def set_default_namespace(self, namespace: uuid.UUID) -> None:
        """
        设置默认命名空间
        
        Args:
            namespace: 新的默认命名空间
        """
        self._default_namespace = namespace
    
    @property
    def default_namespace(self) -> uuid.UUID:
        """获取当前默认命名空间"""
        return self._default_namespace
