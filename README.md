# Add Column After

在指定位置后添加列的功能实现。

## 功能说明

这个项目提供了在数据结构中指定列后添加新列的功能，支持以下数据类型：

- **字典 (dict)**: 在指定键后添加新的键值对
- **列表 (list)**: 列表中每个字典元素在指定列后添加新列
- **pandas DataFrame**: 在指定列后插入新列

## 安装依赖

```bash
pip install -r requirements.txt
```

## 使用方法

### 基本用法

```python
from add_column_after import add_column_after

# 字典示例
data = {"name": "张三", "age": 25, "city": "北京"}
result = add_column_after(data, "email", "age", "example@email.com")
# 结果: {"name": "张三", "age": 25, "email": "example@email.com", "city": "北京"}

# 列表示例
data_list = [
    {"name": "张三", "age": 25},
    {"name": "李四", "age": 30}
]
result = add_column_after(data_list, "email", "age", "example@email.com")
```

### 函数说明

- `add_column_after(data, column_name, after_column_name, default_value=None)`: 通用函数，自动识别数据类型
- `add_column_after_dict(data_dict, column_name, after_column_name, default_value=None)`: 专门处理字典
- `add_column_after_list(data, column_name, after_column_name, default_value=None)`: 专门处理列表
- `add_column_after_dataframe(df, column_name, after_column_name, default_value=None)`: 专门处理 pandas DataFrame

## 运行测试

```bash
python test_add_column_after.py
```

## 运行示例

```bash
python add_column_after.py
```