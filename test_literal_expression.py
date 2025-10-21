"""
测试 LiteralExpression 模块
"""

import unittest
import math
import json
from literal_expression import LiteralExpression, ExpressionEvaluator


class TestLiteralExpression(unittest.TestCase):
    """测试 LiteralExpression 类"""
    
    def test_positive_infinity(self):
        """测试正无穷大"""
        # 多种创建方式
        expr1 = LiteralExpression("Infinity")
        expr2 = LiteralExpression("infinity")
        expr3 = LiteralExpression("inf")
        expr4 = LiteralExpression(float('inf'))
        
        # 验证结果
        self.assertTrue(expr1.is_infinity())
        self.assertTrue(expr1.is_positive_infinity())
        self.assertFalse(expr1.is_negative_infinity())
        self.assertFalse(expr1.is_nan())
        
        # 验证字符串表示
        self.assertEqual(str(expr1), "LiteralExpression [result=Infinity]")
        
        # 验证 JSON 表示
        self.assertEqual(expr1.to_json(), '"Infinity"')
        
        # 验证相等性
        self.assertEqual(expr1, expr2)
        self.assertEqual(expr2, expr3)
        self.assertEqual(expr3, expr4)
    
    def test_negative_infinity(self):
        """测试负无穷大"""
        expr1 = LiteralExpression("-Infinity")
        expr2 = LiteralExpression("-infinity")
        expr3 = LiteralExpression(float('-inf'))
        
        # 验证结果
        self.assertTrue(expr1.is_infinity())
        self.assertFalse(expr1.is_positive_infinity())
        self.assertTrue(expr1.is_negative_infinity())
        self.assertFalse(expr1.is_nan())
        
        # 验证字符串表示
        self.assertEqual(str(expr1), "LiteralExpression [result=-Infinity]")
        
        # 验证 JSON 表示
        self.assertEqual(expr1.to_json(), '"-Infinity"')
        
        # 验证相等性
        self.assertEqual(expr1, expr2)
        self.assertEqual(expr2, expr3)
    
    def test_nan(self):
        """测试 NaN"""
        expr1 = LiteralExpression("NaN")
        expr2 = LiteralExpression("nan")
        expr3 = LiteralExpression(float('nan'))
        
        # 验证结果
        self.assertFalse(expr1.is_infinity())
        self.assertFalse(expr1.is_positive_infinity())
        self.assertFalse(expr1.is_negative_infinity())
        self.assertTrue(expr1.is_nan())
        
        # 验证字符串表示
        self.assertEqual(str(expr1), "LiteralExpression [result=NaN]")
        
        # 验证 JSON 表示
        self.assertEqual(expr1.to_json(), '"NaN"')
        
        # NaN 的特殊相等性（在我们的实现中，NaN == NaN）
        self.assertEqual(expr1, expr2)
        self.assertEqual(expr2, expr3)
    
    def test_regular_numbers(self):
        """测试普通数字"""
        # 整数
        expr_int = LiteralExpression(42)
        self.assertEqual(expr_int.result, 42)
        self.assertEqual(str(expr_int), "LiteralExpression [result=42]")
        self.assertEqual(expr_int.to_json(), "42")
        
        # 浮点数
        expr_float = LiteralExpression(3.14)
        self.assertEqual(expr_float.result, 3.14)
        self.assertEqual(str(expr_float), "LiteralExpression [result=3.14]")
        
        # 字符串数字
        expr_str_int = LiteralExpression("123")
        self.assertEqual(expr_str_int.result, 123)
        
        expr_str_float = LiteralExpression("3.14")
        self.assertEqual(expr_str_float.result, 3.14)
    
    def test_boolean_values(self):
        """测试布尔值"""
        expr_true = LiteralExpression("true")
        self.assertEqual(expr_true.result, True)
        self.assertEqual(expr_true.to_json(), "true")
        
        expr_false = LiteralExpression("false")
        self.assertEqual(expr_false.result, False)
        self.assertEqual(expr_false.to_json(), "false")
    
    def test_null_values(self):
        """测试 null 值"""
        expr_null = LiteralExpression("null")
        self.assertIsNone(expr_null.result)
        self.assertEqual(expr_null.to_json(), "null")
        
        expr_none = LiteralExpression("none")
        self.assertIsNone(expr_none.result)
    
    def test_string_literals(self):
        """测试字符串字面量"""
        expr = LiteralExpression("hello world")
        self.assertEqual(expr.result, "hello world")
        self.assertEqual(str(expr), "LiteralExpression [result=hello world]")
        self.assertEqual(expr.to_json(), '"hello world"')
    
    def test_hash_consistency(self):
        """测试哈希一致性"""
        # 相同的值应该有相同的哈希
        expr1 = LiteralExpression("Infinity")
        expr2 = LiteralExpression(float('inf'))
        self.assertEqual(hash(expr1), hash(expr2))
        
        # NaN 的哈希应该一致
        nan1 = LiteralExpression("NaN")
        nan2 = LiteralExpression(float('nan'))
        self.assertEqual(hash(nan1), hash(nan2))


class TestExpressionEvaluator(unittest.TestCase):
    """测试 ExpressionEvaluator 类"""
    
    def setUp(self):
        self.evaluator = ExpressionEvaluator()
    
    def test_evaluate_infinity(self):
        """测试评估 Infinity"""
        expr = self.evaluator.evaluate("Infinity")
        self.assertTrue(expr.is_positive_infinity())
        self.assertEqual(str(expr), "LiteralExpression [result=Infinity]")
    
    def test_evaluate_math_inf(self):
        """测试评估 math.inf"""
        expr = self.evaluator.evaluate("math.inf")
        self.assertTrue(expr.is_positive_infinity())
    
    def test_evaluate_expressions(self):
        """测试评估表达式"""
        # 简单算术
        expr = self.evaluator.evaluate("2 + 2")
        self.assertEqual(expr.result, 4)
        
        # 使用 math 函数
        expr = self.evaluator.evaluate("math.pi")
        self.assertAlmostEqual(expr.result, 3.141592653589793)
    
    def test_evaluate_invalid_expression(self):
        """测试评估无效表达式"""
        # 无法评估的表达式会被当作字符串
        expr = self.evaluator.evaluate("not a valid expression!")
        self.assertEqual(expr.result, "not a valid expression!")


class TestEdgeCases(unittest.TestCase):
    """测试边缘情况"""
    
    def test_infinity_arithmetic(self):
        """测试无穷大的算术运算"""
        inf = float('inf')
        
        # Infinity + 数字 = Infinity
        self.assertEqual(inf + 100, inf)
        
        # Infinity - Infinity = NaN
        self.assertTrue(math.isnan(inf - inf))
        
        # Infinity * 0 = NaN
        self.assertTrue(math.isnan(inf * 0))
        
        # Infinity / Infinity = NaN
        self.assertTrue(math.isnan(inf / inf))
    
    def test_special_comparisons(self):
        """测试特殊值的比较"""
        expr_inf = LiteralExpression("Infinity")
        expr_ninf = LiteralExpression("-Infinity")
        expr_nan = LiteralExpression("NaN")
        expr_num = LiteralExpression(42)
        
        # 不同类型的表达式不相等
        self.assertNotEqual(expr_inf, expr_ninf)
        self.assertNotEqual(expr_inf, expr_nan)
        self.assertNotEqual(expr_inf, expr_num)
    
    def test_json_serialization(self):
        """测试 JSON 序列化的正确性"""
        test_cases = [
            ("Infinity", '"Infinity"'),
            ("-Infinity", '"-Infinity"'),
            ("NaN", '"NaN"'),
            ("null", "null"),
            ("true", "true"),
            ("false", "false"),
            (42, "42"),
            (3.14, "3.14"),
            ("hello", '"hello"'),
        ]
        
        for value, expected_json in test_cases:
            expr = LiteralExpression(value)
            self.assertEqual(expr.to_json(), expected_json)


class TestIntegration(unittest.TestCase):
    """集成测试"""
    
    def test_complete_workflow(self):
        """测试完整的工作流程"""
        # 创建一个 Infinity 表达式
        expr = LiteralExpression("Infinity")
        
        # 验证字符串表示（这是用户期望看到的）
        self.assertEqual(str(expr), "LiteralExpression [result=Infinity]")
        
        # 验证内部状态
        self.assertEqual(expr.result, float('inf'))
        self.assertTrue(expr.is_infinity())
        self.assertTrue(expr.is_positive_infinity())
        
        # 验证 JSON 序列化
        json_str = expr.to_json()
        self.assertEqual(json_str, '"Infinity"')
        
        # 验证可以通过评估器创建相同的结果
        evaluator = ExpressionEvaluator()
        expr2 = evaluator.evaluate("Infinity")
        self.assertEqual(expr, expr2)
        self.assertEqual(str(expr2), "LiteralExpression [result=Infinity]")


if __name__ == "__main__":
    # 运行所有测试
    unittest.main(verbosity=2)