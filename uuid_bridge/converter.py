"""
UUID 转换器模块 - 提供 UUID 格式转换功能
"""

import uuid
import base64
from typing import Union


class UUIDConverter:
    """
    UUID 格式转换器
    
    支持的格式:
    - 标准格式: 8-4-4-4-12 (如: 550e8400-e29b-41d4-a716-446655440000)
    - 无连字符: 32位十六进制字符串
    - Base64: URL 安全的 Base64 编码
    - 整数: 128 位整数表示
    - 字节: 16 字节表示
    - URN: urn:uuid:格式
    """
    
    @staticmethod
    def _ensure_uuid(u: Union[uuid.UUID, str]) -> uuid.UUID:
        """确保输入是 UUID 对象"""
        if isinstance(u, str):
            # 处理无连字符格式
            cleaned = u.strip()
            if len(cleaned) == 32 and "-" not in cleaned:
                cleaned = f"{cleaned[:8]}-{cleaned[8:12]}-{cleaned[12:16]}-{cleaned[16:20]}-{cleaned[20:]}"
            return uuid.UUID(cleaned)
        return u
    
    @classmethod
    def to_standard(cls, u: Union[uuid.UUID, str]) -> str:
        """
        转换为标准格式 (8-4-4-4-12)
        
        Args:
            u: UUID 对象或字符串
            
        Returns:
            str: 标准格式的 UUID 字符串
        """
        return str(cls._ensure_uuid(u))
    
    @classmethod
    def to_hex(cls, u: Union[uuid.UUID, str]) -> str:
        """
        转换为无连字符的十六进制格式
        
        Args:
            u: UUID 对象或字符串
            
        Returns:
            str: 32位十六进制字符串
        """
        return cls._ensure_uuid(u).hex
    
    @classmethod
    def to_base64(cls, u: Union[uuid.UUID, str], url_safe: bool = True) -> str:
        """
        转换为 Base64 编码
        
        Args:
            u: UUID 对象或字符串
            url_safe: 是否使用 URL 安全的 Base64 编码
            
        Returns:
            str: Base64 编码的字符串（无填充）
        """
        uid = cls._ensure_uuid(u)
        if url_safe:
            return base64.urlsafe_b64encode(uid.bytes).decode('ascii').rstrip('=')
        return base64.b64encode(uid.bytes).decode('ascii').rstrip('=')
    
    @classmethod
    def from_base64(cls, encoded: str, url_safe: bool = True) -> uuid.UUID:
        """
        从 Base64 编码转换为 UUID
        
        Args:
            encoded: Base64 编码的字符串
            url_safe: 是否是 URL 安全的 Base64 编码
            
        Returns:
            uuid.UUID: 解码后的 UUID 对象
        """
        # 添加填充
        padding = 4 - (len(encoded) % 4)
        if padding != 4:
            encoded += '=' * padding
        
        if url_safe:
            decoded = base64.urlsafe_b64decode(encoded)
        else:
            decoded = base64.b64decode(encoded)
        
        return uuid.UUID(bytes=decoded)
    
    @classmethod
    def to_int(cls, u: Union[uuid.UUID, str]) -> int:
        """
        转换为 128 位整数
        
        Args:
            u: UUID 对象或字符串
            
        Returns:
            int: UUID 的整数表示
        """
        return cls._ensure_uuid(u).int
    
    @classmethod
    def from_int(cls, value: int) -> uuid.UUID:
        """
        从整数转换为 UUID
        
        Args:
            value: 128 位整数
            
        Returns:
            uuid.UUID: UUID 对象
        """
        return uuid.UUID(int=value)
    
    @classmethod
    def to_bytes(cls, u: Union[uuid.UUID, str]) -> bytes:
        """
        转换为 16 字节表示
        
        Args:
            u: UUID 对象或字符串
            
        Returns:
            bytes: 16 字节的二进制数据
        """
        return cls._ensure_uuid(u).bytes
    
    @classmethod
    def from_bytes(cls, data: bytes) -> uuid.UUID:
        """
        从字节转换为 UUID
        
        Args:
            data: 16 字节的二进制数据
            
        Returns:
            uuid.UUID: UUID 对象
        """
        return uuid.UUID(bytes=data)
    
    @classmethod
    def to_urn(cls, u: Union[uuid.UUID, str]) -> str:
        """
        转换为 URN 格式
        
        Args:
            u: UUID 对象或字符串
            
        Returns:
            str: URN 格式的字符串 (urn:uuid:...)
        """
        return cls._ensure_uuid(u).urn
    
    @classmethod
    def from_urn(cls, urn: str) -> uuid.UUID:
        """
        从 URN 格式转换为 UUID
        
        Args:
            urn: URN 格式的字符串
            
        Returns:
            uuid.UUID: UUID 对象
        """
        return uuid.UUID(urn)
    
    @classmethod
    def to_uppercase(cls, u: Union[uuid.UUID, str]) -> str:
        """
        转换为大写的标准格式
        
        Args:
            u: UUID 对象或字符串
            
        Returns:
            str: 大写的标准格式 UUID 字符串
        """
        return str(cls._ensure_uuid(u)).upper()
    
    @classmethod
    def to_short_id(cls, u: Union[uuid.UUID, str], length: int = 8) -> str:
        """
        生成短 ID（取 UUID 的前 N 个字符）
        
        警告: 短 ID 可能存在冲突，仅用于显示目的
        
        Args:
            u: UUID 对象或字符串
            length: 短 ID 的长度（默认 8）
            
        Returns:
            str: 短 ID 字符串
        """
        return cls._ensure_uuid(u).hex[:length]
