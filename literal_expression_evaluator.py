#!/usr/bin/env python3
"""
字面表达式评估器 - 处理可能导致无穷大结果的表达式

这个模块演示了如何安全地评估字面表达式，避免产生意外的无穷大结果。
"""

import math
import ast
import operator
from typing import Union, Any
from decimal import Decimal, InvalidOperation


class LiteralExpressionEvaluator:
    """安全的字面表达式评估器"""
    
    # 支持的操作符
    OPERATORS = {
        ast.Add: operator.add,
        ast.Sub: operator.sub,
        ast.Mult: operator.mul,
        ast.Div: operator.truediv,
        ast.FloorDiv: operator.floordiv,
        ast.Mod: operator.mod,
        ast.Pow: operator.pow,
        ast.USub: operator.neg,
        ast.UAdd: operator.pos,
    }
    
    def __init__(self, max_value: float = 1e308, use_decimal: bool = False):
        """
        初始化评估器
        
        Args:
            max_value: 允许的最大值，超过此值将被视为无穷大
            use_decimal: 是否使用 Decimal 进行高精度计算
        """
        self.max_value = max_value
        self.use_decimal = use_decimal
    
    def evaluate(self, expression: str) -> Union[float, int, str]:
        """
        安全地评估字面表达式
        
        Args:
            expression: 要评估的表达式字符串
            
        Returns:
            评估结果，如果结果为无穷大则返回错误信息
        """
        try:
            # 解析表达式
            tree = ast.parse(expression, mode='eval')
            result = self._evaluate_node(tree.body)
            
            # 检查结果是否为无穷大
            if self._is_infinity(result):
                return f"错误: 表达式 '{expression}' 的结果为无穷大"
            
            # 检查结果是否超过最大值
            if isinstance(result, (int, float)) and abs(result) > self.max_value:
                return f"错误: 表达式 '{expression}' 的结果超过最大允许值 ({self.max_value})"
            
            return result
            
        except ZeroDivisionError:
            return f"错误: 表达式 '{expression}' 包含除零操作"
        except OverflowError:
            return f"错误: 表达式 '{expression}' 导致数值溢出"
        except (ValueError, TypeError, SyntaxError) as e:
            return f"错误: 无法评估表达式 '{expression}': {str(e)}"
        except Exception as e:
            return f"错误: 评估表达式 '{expression}' 时发生未知错误: {str(e)}"
    
    def _evaluate_node(self, node: ast.AST) -> Union[float, int]:
        """递归评估 AST 节点"""
        if isinstance(node, ast.Constant):
            return node.value
        elif isinstance(node, ast.Num):  # Python < 3.8 兼容性
            return node.n
        elif isinstance(node, ast.BinOp):
            left = self._evaluate_node(node.left)
            right = self._evaluate_node(node.right)
            
            # 特殊处理除法操作
            if isinstance(node.op, ast.Div) and right == 0:
                raise ZeroDivisionError("除零错误")
            
            # 特殊处理模运算
            if isinstance(node.op, ast.Mod) and right == 0:
                raise ZeroDivisionError("模运算除零错误")
            
            # 特殊处理幂运算
            if isinstance(node.op, ast.Pow):
                if abs(left) > 1 and right > 100:  # 防止过大的幂运算
                    raise OverflowError("幂运算结果过大")
            
            operator_func = self.OPERATORS[type(node.op)]
            result = operator_func(left, right)
            
            # 检查中间结果
            if self._is_infinity(result):
                raise OverflowError("中间计算结果为无穷大")
            
            return result
            
        elif isinstance(node, ast.UnaryOp):
            operand = self._evaluate_node(node.operand)
            operator_func = self.OPERATORS[type(node.op)]
            return operator_func(operand)
        else:
            raise ValueError(f"不支持的节点类型: {type(node)}")
    
    def _is_infinity(self, value: Any) -> bool:
        """检查值是否为无穷大"""
        if isinstance(value, float):
            return math.isinf(value)
        return False


def demonstrate_infinity_cases():
    """演示各种导致无穷大结果的情况"""
    evaluator = LiteralExpressionEvaluator()
    
    test_cases = [
        "1 / 0",           # 除零
        "1.0 / 0.0",       # 浮点除零
        "2 ** 1000",       # 过大的幂运算
        "1e308 * 10",      # 数值溢出
        "float('inf')",    # 直接的无穷大（这个会被语法检查拒绝）
        "10 / 0.0",        # 混合类型除零
        "(-5) ** 1001",    # 负数的大幂运算
        "1e200 * 1e200",   # 大数相乘
        "100 % 0",         # 模运算除零
        "5 + 3",           # 正常情况
        "10.5 * 2",        # 正常浮点运算
        "-42",             # 负数
    ]
    
    print("字面表达式评估结果:")
    print("=" * 50)
    
    for expression in test_cases:
        result = evaluator.evaluate(expression)
        print(f"表达式: {expression:15} => {result}")
    
    print("\n" + "=" * 50)


def safe_literal_evaluation_example():
    """展示安全的字面表达式评估"""
    print("\n安全的字面表达式评估示例:")
    print("=" * 50)
    
    # 创建一个更严格的评估器
    strict_evaluator = LiteralExpressionEvaluator(max_value=1e6)
    
    expressions = [
        "1000000",      # 在限制内
        "1000001",      # 超过限制
        "999 * 999",    # 计算结果在限制内
        "1000 * 1000",  # 计算结果超过限制
    ]
    
    for expr in expressions:
        result = strict_evaluator.evaluate(expr)
        print(f"表达式: {expr:15} => {result}")


if __name__ == "__main__":
    demonstrate_infinity_cases()
    safe_literal_evaluation_example()