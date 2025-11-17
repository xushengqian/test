#!/usr/bin/env python3
"""
在指定列之后添加新列的工具函数
支持多种数据结构：列表、字典列表、pandas DataFrame等
"""

from typing import List, Dict, Any, Optional, Union


def add_column_after_list(
    data: List[List[Any]], 
    after_index: int, 
    new_column: List[Any],
    column_name: Optional[str] = None
) -> List[List[Any]]:
    """
    在列表的列表中，在指定索引之后添加新列
    
    Args:
        data: 二维列表数据
        after_index: 在此索引之后添加新列（-1表示在开头添加）
        new_column: 新列的数据
        column_name: 列名（可选，用于第一行）
    
    Returns:
        添加新列后的数据
    
    Example:
        >>> data = [['A', 'B', 'C'], [1, 2, 3], [4, 5, 6]]
        >>> new_col = ['D', 7, 8]
        >>> result = add_column_after_list(data, 1, new_col)
        >>> print(result)
        [['A', 'B', 'D', 'C'], [1, 2, 7, 3], [4, 5, 8, 6]]
    """
    if len(data) != len(new_column):
        raise ValueError(f"新列的长度({len(new_column)})必须与数据行数({len(data)})相同")
    
    result = []
    insert_index = after_index + 1
    
    for i, row in enumerate(data):
        new_row = row.copy()
        new_row.insert(insert_index, new_column[i])
        result.append(new_row)
    
    return result


def add_column_after_dict(
    data: List[Dict[str, Any]], 
    after_column: str, 
    new_column_name: str,
    new_column_values: Union[List[Any], Any],
    default_value: Any = None
) -> List[Dict[str, Any]]:
    """
    在字典列表中，在指定列之后添加新列
    
    Args:
        data: 字典列表数据
        after_column: 在此列之后添加新列（None表示在开头添加）
        new_column_name: 新列的名称
        new_column_values: 新列的值（可以是列表或单个值）
        default_value: 默认值（当new_column_values为单个值时使用）
    
    Returns:
        添加新列后的数据
    
    Example:
        >>> data = [{'name': 'Alice', 'age': 25}, {'name': 'Bob', 'age': 30}]
        >>> result = add_column_after_dict(data, 'name', 'city', ['NY', 'LA'])
        >>> print(result)
        [{'name': 'Alice', 'city': 'NY', 'age': 25}, 
         {'name': 'Bob', 'city': 'LA', 'age': 30}]
    """
    if not data:
        return data
    
    # 如果new_column_values不是列表，将其转换为列表
    if not isinstance(new_column_values, list):
        values = [new_column_values] * len(data)
    else:
        if len(new_column_values) != len(data):
            raise ValueError(f"新列值的数量({len(new_column_values)})必须与数据行数({len(data)})相同")
        values = new_column_values
    
    result = []
    for i, row in enumerate(data):
        # 创建新的有序字典
        new_row = {}
        inserted = False
        
        for key, value in row.items():
            new_row[key] = value
            # 在指定列之后插入新列
            if key == after_column:
                new_row[new_column_name] = values[i]
                inserted = True
        
        # 如果after_column为None或未找到，添加到末尾
        if not inserted:
            new_row[new_column_name] = values[i]
        
        result.append(new_row)
    
    return result


def add_column_after_pandas(df, after_column: str, new_column_name: str, new_column_values):
    """
    在pandas DataFrame中，在指定列之后添加新列
    
    Args:
        df: pandas DataFrame
        after_column: 在此列之后添加新列
        new_column_name: 新列的名称
        new_column_values: 新列的值
    
    Returns:
        添加新列后的DataFrame
    
    Example:
        >>> import pandas as pd
        >>> df = pd.DataFrame({'A': [1, 2], 'B': [3, 4], 'C': [5, 6]})
        >>> result = add_column_after_pandas(df, 'A', 'D', [7, 8])
        >>> print(result)
           A  D  B  C
        0  1  7  3  5
        1  2  8  4  6
    """
    try:
        import pandas as pd
    except ImportError:
        raise ImportError("需要安装pandas库才能使用此功能")
    
    if after_column not in df.columns:
        raise ValueError(f"列'{after_column}'不存在于DataFrame中")
    
    # 获取列的位置
    columns = df.columns.tolist()
    after_index = columns.index(after_column)
    
    # 创建新的DataFrame副本
    result = df.copy()
    
    # 添加新列
    result[new_column_name] = new_column_values
    
    # 重新排列列顺序
    new_columns = columns[:after_index + 1] + [new_column_name] + columns[after_index + 1:]
    result = result[new_columns]
    
    return result


def add_column_after(
    data: Union[List[List[Any]], List[Dict[str, Any]], Any],
    after: Union[int, str],
    new_column_name: str,
    new_column_values: Union[List[Any], Any],
    **kwargs
):
    """
    通用函数：在指定位置之后添加新列
    自动检测数据类型并调用相应的函数
    
    Args:
        data: 数据（列表、字典列表或DataFrame）
        after: 在此列/索引之后添加（int表示索引，str表示列名）
        new_column_name: 新列的名称
        new_column_values: 新列的值
        **kwargs: 其他参数
    
    Returns:
        添加新列后的数据
    """
    # 检测pandas DataFrame
    try:
        import pandas as pd
        if isinstance(data, pd.DataFrame):
            if not isinstance(after, str):
                raise ValueError("对于DataFrame，after参数必须是列名（字符串）")
            return add_column_after_pandas(data, after, new_column_name, new_column_values)
    except ImportError:
        pass
    
    # 检测字典列表
    if isinstance(data, list) and len(data) > 0 and isinstance(data[0], dict):
        if not isinstance(after, str):
            raise ValueError("对于字典列表，after参数必须是列名（字符串）")
        return add_column_after_dict(data, after, new_column_name, new_column_values, **kwargs)
    
    # 检测二维列表
    if isinstance(data, list) and len(data) > 0 and isinstance(data[0], list):
        if not isinstance(after, int):
            raise ValueError("对于二维列表，after参数必须是索引（整数）")
        return add_column_after_list(data, after, new_column_values, new_column_name)
    
    raise TypeError(f"不支持的数据类型: {type(data)}")


if __name__ == "__main__":
    # 示例用法
    print("=== 示例 1: 二维列表 ===")
    data_list = [
        ['姓名', '年龄', '城市'],
        ['张三', 25, '北京'],
        ['李四', 30, '上海']
    ]
    new_col = ['性别', '男', '女']
    result = add_column_after_list(data_list, 0, new_col)  # 在索引0（姓名）之后添加
    for row in result:
        print(row)
    
    print("\n=== 示例 2: 字典列表 ===")
    data_dict = [
        {'name': 'Alice', 'age': 25, 'city': 'NY'},
        {'name': 'Bob', 'age': 30, 'city': 'LA'}
    ]
    result = add_column_after_dict(data_dict, 'name', 'gender', ['Female', 'Male'])
    for row in result:
        print(row)
    
    print("\n=== 示例 3: 使用通用函数 ===")
    result = add_column_after(data_dict, 'age', 'country', ['USA', 'USA'])
    for row in result:
        print(row)
