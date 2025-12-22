#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Map作为Key的判重示例
演示如何使用map/dict作为key进行判重
"""

import json
from typing import List, Dict, Any


def deduplicate_by_json_key(maps: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    方法1: 将dict序列化为JSON字符串作为key进行判重
    优点: 通用性强，适用于任意结构的dict
    缺点: 有序列化开销
    """
    seen = set()
    result = []
    
    for m in maps:
        # 将dict序列化为JSON字符串，sort_keys确保相同内容的dict生成相同的字符串
        key = json.dumps(m, sort_keys=True, ensure_ascii=False)
        if key not in seen:
            seen.add(key)
            result.append(m)
    
    return result


def deduplicate_by_frozenset(maps: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    方法2: 将dict转换为frozenset作为key进行判重
    优点: 性能好，无需序列化
    缺点: 只适用于值可哈希的情况（不能有list、dict等不可哈希的值）
    """
    seen = set()
    result = []
    
    for m in maps:
        # 将dict的items转换为frozenset
        key = frozenset(m.items())
        if key not in seen:
            seen.add(key)
            result.append(m)
    
    return result


def deduplicate_by_tuple_key(maps: List[Dict[str, Any]], keys: List[str]) -> List[Dict[str, Any]]:
    """
    方法3: 使用指定的键组成tuple作为key进行判重
    优点: 性能最好，只比较关心的字段
    缺点: 需要预先知道要比较的键
    """
    seen = set()
    result = []
    
    for m in maps:
        # 使用指定的键组成tuple
        key = tuple(m.get(k) for k in keys)
        if key not in seen:
            seen.add(key)
            result.append(m)
    
    return result


def deduplicate_by_custom_hash(maps: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    方法4: 自定义hash函数
    优点: 灵活，可以自定义判重逻辑
    """
    seen = set()
    result = []
    
    def make_hashable(d: Dict[str, Any]) -> tuple:
        """将dict转换为可哈希的tuple"""
        def convert_value(v):
            if isinstance(v, dict):
                return tuple(sorted((k, convert_value(v)) for k, v in v.items()))
            elif isinstance(v, list):
                return tuple(convert_value(item) for item in v)
            else:
                return v
        
        return tuple(sorted((k, convert_value(v)) for k, v in d.items()))
    
    for m in maps:
        key = make_hashable(m)
        if key not in seen:
            seen.add(key)
            result.append(m)
    
    return result


# 测试示例
if __name__ == "__main__":
    # 测试数据：包含重复的map
    test_data = [
        {"name": "张三", "age": 25, "city": "北京"},
        {"name": "李四", "age": 30, "city": "上海"},
        {"name": "张三", "age": 25, "city": "北京"},  # 重复
        {"name": "王五", "age": 28, "city": "广州"},
        {"name": "李四", "age": 30, "city": "上海"},  # 重复
    ]
    
    print("原始数据:")
    for i, item in enumerate(test_data, 1):
        print(f"{i}. {item}")
    
    print("\n方法1 - JSON序列化判重:")
    result1 = deduplicate_by_json_key(test_data)
    for i, item in enumerate(result1, 1):
        print(f"{i}. {item}")
    
    print("\n方法2 - Frozenset判重:")
    result2 = deduplicate_by_frozenset(test_data)
    for i, item in enumerate(result2, 1):
        print(f"{i}. {item}")
    
    print("\n方法3 - Tuple key判重 (基于name和age):")
    result3 = deduplicate_by_tuple_key(test_data, ["name", "age"])
    for i, item in enumerate(result3, 1):
        print(f"{i}. {item}")
    
    print("\n方法4 - 自定义hash判重:")
    result4 = deduplicate_by_custom_hash(test_data)
    for i, item in enumerate(result4, 1):
        print(f"{i}. {item}")
    
    # 测试嵌套dict的情况
    print("\n\n测试嵌套dict的情况:")
    nested_data = [
        {"user": {"name": "张三", "age": 25}, "score": 90},
        {"user": {"name": "李四", "age": 30}, "score": 85},
        {"user": {"name": "张三", "age": 25}, "score": 90},  # 重复
    ]
    
    print("原始嵌套数据:")
    for i, item in enumerate(nested_data, 1):
        print(f"{i}. {item}")
    
    print("\n使用JSON序列化判重:")
    nested_result = deduplicate_by_json_key(nested_data)
    for i, item in enumerate(nested_result, 1):
        print(f"{i}. {item}")
    
    print("\n使用自定义hash判重:")
    nested_result2 = deduplicate_by_custom_hash(nested_data)
    for i, item in enumerate(nested_result2, 1):
        print(f"{i}. {item}")
