# Add Column After - 在指定位置之后添加列

一个强大且灵活的Python工具，用于在数据结构中的指定位置之后添加新列。

## 功能特性

✨ **多种数据结构支持**
- 二维列表 (List of Lists)
- 字典列表 (List of Dictionaries)
- Pandas DataFrame（可选）

🎯 **灵活的插入位置**
- 在任意列之后插入
- 支持在开头或末尾插入
- 自动保持列顺序

🛡️ **健壮的错误处理**
- 自动类型检测
- 数据验证
- 清晰的错误提示

## 安装

无需额外依赖！只需克隆或下载本仓库即可使用。

```bash
git clone <repository-url>
cd add-column-after
```

如果需要使用pandas支持，可以安装：

```bash
pip install pandas
```

## 快速开始

### 1. 二维列表

```python
from add_column_after import add_column_after_list

data = [
    ['姓名', '年龄', '城市'],
    ['张三', 25, '北京'],
    ['李四', 30, '上海']
]

# 在第一列（索引0）之后添加新列
new_col = ['性别', '男', '女']
result = add_column_after_list(data, 0, new_col)

# 结果：
# [['姓名', '性别', '年龄', '城市'],
#  ['张三', '男', 25, '北京'],
#  ['李四', '女', 30, '上海']]
```

### 2. 字典列表

```python
from add_column_after import add_column_after_dict

data = [
    {'name': 'Alice', 'age': 25, 'city': 'NY'},
    {'name': 'Bob', 'age': 30, 'city': 'LA'}
]

# 在'name'列之后添加'gender'列
result = add_column_after_dict(data, 'name', 'gender', ['Female', 'Male'])

# 结果：
# [{'name': 'Alice', 'gender': 'Female', 'age': 25, 'city': 'NY'},
#  {'name': 'Bob', 'gender': 'Male', 'age': 30, 'city': 'LA'}]
```

### 3. 通用函数（自动检测类型）

```python
from add_column_after import add_column_after

# 自动检测并处理二维列表
data = [['A', 'B'], [1, 2], [3, 4]]
result = add_column_after(data, 0, 'C', ['C', 5, 6])

# 自动检测并处理字典列表
data = [{'x': 1, 'y': 2}, {'x': 3, 'y': 4}]
result = add_column_after(data, 'x', 'z', [5, 6])
```

### 4. Pandas DataFrame

```python
from add_column_after import add_column_after_pandas
import pandas as pd

df = pd.DataFrame({'A': [1, 2], 'B': [3, 4], 'C': [5, 6]})
result = add_column_after_pandas(df, 'A', 'D', [7, 8])

# 结果：
#    A  D  B  C
# 0  1  7  3  5
# 1  2  8  4  6
```

## 详细用法

### add_column_after_list

在二维列表中添加列。

**参数：**
- `data` (List[List[Any]]): 二维列表数据
- `after_index` (int): 在此索引之后添加新列（-1表示在开头）
- `new_column` (List[Any]): 新列的数据
- `column_name` (Optional[str]): 列名（可选）

**返回：** List[List[Any]] - 添加新列后的数据

### add_column_after_dict

在字典列表中添加列。

**参数：**
- `data` (List[Dict[str, Any]]): 字典列表数据
- `after_column` (str): 在此列之后添加新列
- `new_column_name` (str): 新列的名称
- `new_column_values` (Union[List[Any], Any]): 新列的值（列表或单个值）
- `default_value` (Any): 默认值（可选）

**返回：** List[Dict[str, Any]] - 添加新列后的数据

### add_column_after

通用函数，自动检测数据类型。

**参数：**
- `data` (Union[...]): 任何支持的数据类型
- `after` (Union[int, str]): 列索引或列名
- `new_column_name` (str): 新列的名称
- `new_column_values` (Union[List[Any], Any]): 新列的值

**返回：** 与输入相同类型的数据

## 运行示例

```bash
# 运行内置示例
python3 add_column_after.py

# 运行测试套件
python3 test_add_column_after.py
```

## 真实场景示例

### 场景1：员工数据管理

```python
employees = [
    {'employee_id': 'E001', 'name': '张三', 'department': '技术部', 'salary': 15000},
    {'employee_id': 'E002', 'name': '李四', 'department': '销售部', 'salary': 12000},
]

# 在name之后添加email列
emails = ['zhangsan@company.com', 'lisi@company.com']
result = add_column_after_dict(employees, 'name', 'email', emails)
```

### 场景2：数据处理管道

```python
# CSV数据处理
csv_data = [
    ['产品', '价格', '库存'],
    ['笔记本', 5000, 100],
    ['手机', 3000, 200]
]

# 添加计算列：总价值 = 价格 × 库存
values = ['总价值', 500000, 600000]
result = add_column_after_list(csv_data, 1, values)  # 在价格之后添加
```

### 场景3：数据增强

```python
# 为现有数据添加派生列
data = [
    {'product': 'A', 'price': 100, 'quantity': 10},
    {'product': 'B', 'price': 200, 'quantity': 5}
]

# 在quantity之后添加total列
totals = [100*10, 200*5]
result = add_column_after_dict(data, 'quantity', 'total', totals)
```

## 错误处理

工具包含完整的错误处理：

```python
# 列长度不匹配
try:
    data = [['A'], ['B']]
    add_column_after_list(data, 0, ['C'])  # 长度不匹配
except ValueError as e:
    print(f"错误: {e}")

# 列名不存在
try:
    data = [{'x': 1}]
    add_column_after_dict(data, 'nonexistent', 'y', [2])
except ValueError as e:
    print(f"错误: {e}")
```

## 测试

运行完整的测试套件：

```bash
python3 test_add_column_after.py
```

测试涵盖：
- ✅ 各种插入位置（开头、中间、末尾）
- ✅ 不同数据类型
- ✅ 错误处理
- ✅ 真实场景
- ✅ 边界情况

## 贡献

欢迎提交问题和拉取请求！

## 许可证

MIT License

## 作者

开发于 2025-11-17
