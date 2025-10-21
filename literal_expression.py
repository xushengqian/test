"""
LiteralExpression 模块
用于处理各种字面量表达式，包括特殊值如 Infinity
"""

import math
import json
from typing import Any, Union, Optional
from dataclasses import dataclass


@dataclass
class LiteralExpression:
    """字面量表达式类，用于表示和评估字面量值"""
    
    value: Any
    
    def __init__(self, value: Any):
        """
        初始化字面量表达式
        
        Args:
            value: 字面量值，可以是数字、字符串、布尔值或特殊值（如 Infinity）
        """
        self.value = self._parse_value(value)
        self.result = self._evaluate()
    
    def _parse_value(self, value: Any) -> Any:
        """
        解析输入值，处理特殊情况
        
        Args:
            value: 输入值
            
        Returns:
            解析后的值
        """
        # 如果是字符串，尝试解析特殊值
        if isinstance(value, str):
            value_lower = value.lower()
            if value_lower == 'infinity' or value_lower == 'inf':
                return float('inf')
            elif value_lower == '-infinity' or value_lower == '-inf':
                return float('-inf')
            elif value_lower == 'nan':
                return float('nan')
            elif value_lower == 'true':
                return True
            elif value_lower == 'false':
                return False
            elif value_lower == 'null' or value_lower == 'none':
                return None
            else:
                # 尝试解析为数字
                try:
                    # 尝试整数
                    if '.' not in value and 'e' not in value.lower():
                        return int(value)
                    else:
                        return float(value)
                except ValueError:
                    # 保持为字符串
                    return value
        
        # 如果已经是 float 类型的 inf
        elif isinstance(value, float):
            return value
        
        # 其他类型直接返回
        return value
    
    def _evaluate(self) -> Any:
        """
        评估字面量表达式
        
        Returns:
            评估结果
        """
        # 对于字面量表达式，结果就是值本身
        return self.value
    
    def is_infinity(self) -> bool:
        """
        检查值是否为无穷大
        
        Returns:
            如果值是正无穷或负无穷，返回 True
        """
        return isinstance(self.result, float) and math.isinf(self.result)
    
    def is_positive_infinity(self) -> bool:
        """
        检查值是否为正无穷大
        
        Returns:
            如果值是正无穷，返回 True
        """
        return isinstance(self.result, float) and self.result == float('inf')
    
    def is_negative_infinity(self) -> bool:
        """
        检查值是否为负无穷大
        
        Returns:
            如果值是负无穷，返回 True
        """
        return isinstance(self.result, float) and self.result == float('-inf')
    
    def is_nan(self) -> bool:
        """
        检查值是否为 NaN
        
        Returns:
            如果值是 NaN，返回 True
        """
        return isinstance(self.result, float) and math.isnan(self.result)
    
    def to_json(self) -> str:
        """
        将表达式转换为 JSON 字符串
        
        Returns:
            JSON 表示
        """
        if self.is_positive_infinity():
            return '"Infinity"'
        elif self.is_negative_infinity():
            return '"-Infinity"'
        elif self.is_nan():
            return '"NaN"'
        elif self.result is None:
            return 'null'
        else:
            return json.dumps(self.result)
    
    def __str__(self) -> str:
        """字符串表示"""
        if self.is_positive_infinity():
            return "LiteralExpression [result=Infinity]"
        elif self.is_negative_infinity():
            return "LiteralExpression [result=-Infinity]"
        elif self.is_nan():
            return "LiteralExpression [result=NaN]"
        else:
            return f"LiteralExpression [result={self.result}]"
    
    def __repr__(self) -> str:
        """详细字符串表示"""
        return f"LiteralExpression(value={self.value!r}, result={self.result!r})"
    
    def __eq__(self, other) -> bool:
        """相等性比较"""
        if not isinstance(other, LiteralExpression):
            return False
        
        # 特殊处理 NaN (NaN != NaN)
        if self.is_nan() and other.is_nan():
            return True
        
        return self.result == other.result
    
    def __hash__(self) -> int:
        """哈希值"""
        # 对于 NaN 和 Infinity，使用特殊的哈希值
        if self.is_nan():
            return hash("NaN")
        elif self.is_positive_infinity():
            return hash("Infinity")
        elif self.is_negative_infinity():
            return hash("-Infinity")
        else:
            return hash(self.result)


class ExpressionEvaluator:
    """表达式评估器，可以处理更复杂的表达式"""
    
    @staticmethod
    def evaluate(expression: str) -> LiteralExpression:
        """
        评估字符串表达式
        
        Args:
            expression: 要评估的表达式字符串
            
        Returns:
            LiteralExpression 对象
        """
        # 清理表达式
        expression = expression.strip()
        
        # 特殊值的处理
        special_values = {
            'Infinity': float('inf'),
            'infinity': float('inf'),
            'inf': float('inf'),
            '-Infinity': float('-inf'),
            '-infinity': float('-inf'),
            '-inf': float('-inf'),
            'NaN': float('nan'),
            'nan': float('nan'),
        }
        
        if expression in special_values:
            return LiteralExpression(special_values[expression])
        
        # 尝试使用 eval 评估表达式（在安全的环境中）
        try:
            # 创建安全的评估环境
            safe_dict = {
                'Infinity': float('inf'),
                'inf': float('inf'),
                'NaN': float('nan'),
                'nan': float('nan'),
                'true': True,
                'false': False,
                'null': None,
                'math': math,
            }
            
            result = eval(expression, {"__builtins__": {}}, safe_dict)
            return LiteralExpression(result)
        except:
            # 如果评估失败，将其作为字符串字面量
            return LiteralExpression(expression)


# 示例用法
if __name__ == "__main__":
    # 测试 Infinity
    expr1 = LiteralExpression("Infinity")
    print(expr1)  # LiteralExpression [result=Infinity]
    print(f"Is infinity? {expr1.is_infinity()}")  # True
    print(f"JSON: {expr1.to_json()}")  # "Infinity"
    
    # 测试负无穷
    expr2 = LiteralExpression("-Infinity")
    print(expr2)  # LiteralExpression [result=-Infinity]
    
    # 测试 NaN
    expr3 = LiteralExpression("NaN")
    print(expr3)  # LiteralExpression [result=NaN]
    
    # 测试普通数字
    expr4 = LiteralExpression(42)
    print(expr4)  # LiteralExpression [result=42]
    
    # 测试字符串
    expr5 = LiteralExpression("hello")
    print(expr5)  # LiteralExpression [result=hello]
    
    # 使用评估器
    evaluator = ExpressionEvaluator()
    expr6 = evaluator.evaluate("Infinity")
    print(expr6)  # LiteralExpression [result=Infinity]
    
    # 测试数学表达式
    expr7 = evaluator.evaluate("math.inf")
    print(expr7)  # LiteralExpression [result=Infinity]