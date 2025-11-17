"""
在指定位置后添加列的功能实现
支持多种数据结构：列表、字典、pandas DataFrame等
"""


def add_column_after_list(data, column_name, after_column_name, default_value=None):
    """
    在列表中指定列名后添加新列
    
    Args:
        data: 列表数据，每个元素是字典
        column_name: 要添加的新列名
        after_column_name: 在此列名后添加
        default_value: 新列的默认值
    
    Returns:
        修改后的数据列表
    """
    if not data:
        return data
    
    # 获取所有列名
    columns = list(data[0].keys())
    
    # 查找插入位置
    if after_column_name not in columns:
        raise ValueError(f"列 '{after_column_name}' 不存在")
    
    insert_index = columns.index(after_column_name) + 1
    
    # 添加新列
    for row in data:
        # 创建新的有序字典
        new_row = {}
        for i, col in enumerate(columns):
            new_row[col] = row[col]
            if i == insert_index - 1:  # 在指定列后插入
                new_row[column_name] = default_value
        # 如果插入位置在最后
        if insert_index == len(columns):
            new_row[column_name] = default_value
        
        row.clear()
        row.update(new_row)
    
    return data


def add_column_after_dict(data_dict, column_name, after_column_name, default_value=None):
    """
    在字典中指定键后添加新键值对
    
    Args:
        data_dict: 字典数据
        column_name: 要添加的新键名
        after_column_name: 在此键后添加
        default_value: 新键的默认值
    
    Returns:
        修改后的字典
    """
    if after_column_name not in data_dict:
        raise ValueError(f"键 '{after_column_name}' 不存在")
    
    # 创建新字典，保持顺序
    new_dict = {}
    inserted = False
    
    for key, value in data_dict.items():
        new_dict[key] = value
        if key == after_column_name and not inserted:
            new_dict[column_name] = default_value
            inserted = True
    
    return new_dict


def add_column_after_dataframe(df, column_name, after_column_name, default_value=None):
    """
    在 pandas DataFrame 中指定列后添加新列
    
    Args:
        df: pandas DataFrame
        column_name: 要添加的新列名
        after_column_name: 在此列后添加
        default_value: 新列的默认值
    
    Returns:
        修改后的 DataFrame
    """
    try:
        import pandas as pd
    except ImportError:
        raise ImportError("需要安装 pandas: pip install pandas")
    
    if after_column_name not in df.columns:
        raise ValueError(f"列 '{after_column_name}' 不存在")
    
    # 获取列的位置
    columns = list(df.columns)
    insert_index = columns.index(after_column_name) + 1
    
    # 插入新列
    df.insert(insert_index, column_name, default_value)
    
    return df


def add_column_after(data, column_name, after_column_name, default_value=None):
    """
    通用函数：在指定位置后添加列
    自动识别数据类型并调用相应的处理函数
    
    Args:
        data: 数据（列表、字典或 pandas DataFrame）
        column_name: 要添加的新列名
        after_column_name: 在此列后添加
        default_value: 新列的默认值
    
    Returns:
        修改后的数据
    """
    try:
        import pandas as pd
        if isinstance(data, pd.DataFrame):
            return add_column_after_dataframe(data, column_name, after_column_name, default_value)
    except ImportError:
        pass
    
    if isinstance(data, dict):
        return add_column_after_dict(data, column_name, after_column_name, default_value)
    elif isinstance(data, list) and len(data) > 0 and isinstance(data[0], dict):
        return add_column_after_list(data, column_name, after_column_name, default_value)
    else:
        raise TypeError(f"不支持的数据类型: {type(data)}")


if __name__ == "__main__":
    # 示例用法
    print("=== 示例1: 字典操作 ===")
    data_dict = {"name": "张三", "age": 25, "city": "北京"}
    result = add_column_after_dict(data_dict, "email", "age", "example@email.com")
    print(f"原数据: {data_dict}")
    print(f"添加后: {result}")
    
    print("\n=== 示例2: 列表操作 ===")
    data_list = [
        {"name": "张三", "age": 25, "city": "北京"},
        {"name": "李四", "age": 30, "city": "上海"}
    ]
    result_list = add_column_after_list(data_list, "email", "age", "example@email.com")
    print(f"添加后: {result_list}")
    
    print("\n=== 示例3: 通用函数 ===")
    data_dict2 = {"name": "王五", "age": 28, "city": "广州"}
    result2 = add_column_after(data_dict2, "phone", "age", "13800138000")
    print(f"添加后: {result2}")
