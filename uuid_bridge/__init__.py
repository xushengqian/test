"""
UUID Bridge - 一个用于 UUID 生成、转换和映射的桥接模块

主要功能:
- 生成各种版本的 UUID (v1, v4, v5)
- 在不同格式之间转换 UUID (标准格式、无连字符、Base64 等)
- 双向映射 UUID 到自定义标识符
"""

from .core import UUIDBridge
from .converter import UUIDConverter
from .mapper import UUIDMapper

__version__ = "1.0.0"
__all__ = ["UUIDBridge", "UUIDConverter", "UUIDMapper"]
