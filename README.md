# LiteralExpression - 字面量表达式处理器

一个强大的 Python 模块，用于处理各种字面量表达式，特别是对特殊值（如 `Infinity`、`-Infinity` 和 `NaN`）的支持。

## 功能特性

- ✅ 完整支持 `Infinity`（正无穷）和 `-Infinity`（负无穷）
- ✅ 正确处理 `NaN`（非数字）
- ✅ 支持布尔值、null 值和普通数字
- ✅ 智能的类型解析和转换
- ✅ JSON 序列化支持
- ✅ 表达式评估器
- ✅ 完整的测试覆盖

## 快速开始

```python
from literal_expression import LiteralExpression

# 创建一个 Infinity 表达式
expr = LiteralExpression("Infinity")
print(expr)  # 输出: LiteralExpression [result=Infinity]

# 检查是否为无穷大
print(expr.is_infinity())  # True

# 获取 JSON 表示
print(expr.to_json())  # "Infinity"
```

## 主要组件

### LiteralExpression 类

处理各种字面量值的核心类：

```python
# 支持多种输入格式
expr1 = LiteralExpression("Infinity")
expr2 = LiteralExpression(float('inf'))
expr3 = LiteralExpression(42)
expr4 = LiteralExpression("hello")
```

### ExpressionEvaluator 类

用于评估字符串表达式：

```python
from literal_expression import ExpressionEvaluator

evaluator = ExpressionEvaluator()
expr = evaluator.evaluate("Infinity")
print(expr)  # LiteralExpression [result=Infinity]
```

## 特殊值处理

| 输入 | 结果 | 说明 |
|------|------|------|
| `"Infinity"`, `"inf"` | `float('inf')` | 正无穷大 |
| `"-Infinity"`, `"-inf"` | `float('-inf')` | 负无穷大 |
| `"NaN"`, `"nan"` | `float('nan')` | 非数字 |
| `"null"`, `"none"` | `None` | 空值 |
| `"true"`, `"false"` | `True`, `False` | 布尔值 |

## 文件结构

- `literal_expression.py` - 主模块文件
- `test_literal_expression.py` - 完整的单元测试
- `demo_infinity.py` - Infinity 处理演示脚本
- `README.md` - 本文档

## 运行测试

```bash
# 运行所有测试
python3 -m unittest test_literal_expression -v

# 运行演示脚本
python3 demo_infinity.py

# 运行主模块示例
python3 literal_expression.py
```

## 使用场景

这个模块特别适用于：

1. **数学计算** - 处理可能产生无穷大的计算
2. **数据序列化** - 正确序列化特殊值到 JSON
3. **表达式解析** - 安全地评估包含特殊值的表达式
4. **科学计算** - 处理极限值和特殊情况

## 示例：完整工作流

```python
# 创建表达式
expr = LiteralExpression("Infinity")

# 检查类型
if expr.is_positive_infinity():
    print("这是正无穷大")

# 序列化为 JSON
json_str = expr.to_json()  # "Infinity"

# 比较操作
expr2 = LiteralExpression(float('inf'))
print(expr == expr2)  # True
```

## 注意事项

- NaN 的相等性比较遵循特殊规则（在本实现中 NaN == NaN 返回 True）
- JSON 序列化遵循 JavaScript 标准（Infinity 序列化为字符串 "Infinity"）
- 表达式评估器使用安全的评估环境，避免执行危险代码