#!/usr/bin/env python3
"""
字面表达式评估器的测试用例

测试各种可能导致无穷大结果的情况以及解决方案的有效性。
"""

import unittest
import math
from literal_expression_evaluator import LiteralExpressionEvaluator


class TestLiteralExpressionEvaluator(unittest.TestCase):
    """字面表达式评估器测试类"""
    
    def setUp(self):
        """设置测试环境"""
        self.evaluator = LiteralExpressionEvaluator()
        self.strict_evaluator = LiteralExpressionEvaluator(max_value=1000)
    
    def test_division_by_zero(self):
        """测试除零情况"""
        test_cases = [
            "1 / 0",
            "1.0 / 0.0",
            "10 / 0",
            "-5 / 0.0"
        ]
        
        for expression in test_cases:
            with self.subTest(expression=expression):
                result = self.evaluator.evaluate(expression)
                self.assertIsInstance(result, str)
                self.assertIn("除零", result)
    
    def test_modulo_by_zero(self):
        """测试模运算除零情况"""
        test_cases = [
            "10 % 0",
            "5.5 % 0.0",
            "-3 % 0"
        ]
        
        for expression in test_cases:
            with self.subTest(expression=expression):
                result = self.evaluator.evaluate(expression)
                self.assertIsInstance(result, str)
                self.assertIn("除零", result)
    
    def test_overflow_cases(self):
        """测试数值溢出情况"""
        test_cases = [
            "2 ** 1000",      # 大幂运算
            "10 ** 500",      # 另一个大幂运算
            "1e308 * 10",     # 直接溢出
        ]
        
        for expression in test_cases:
            with self.subTest(expression=expression):
                result = self.evaluator.evaluate(expression)
                self.assertIsInstance(result, str)
                self.assertTrue("溢出" in result or "无穷大" in result or "最大允许值" in result)
    
    def test_valid_expressions(self):
        """测试有效的表达式"""
        test_cases = [
            ("5 + 3", 8),
            ("10 - 4", 6),
            ("6 * 7", 42),
            ("15 / 3", 5.0),
            ("2 ** 3", 8),
            ("-5", -5),
            ("10.5 * 2", 21.0),
            ("17 % 5", 2),
        ]
        
        for expression, expected in test_cases:
            with self.subTest(expression=expression):
                result = self.evaluator.evaluate(expression)
                self.assertAlmostEqual(result, expected, places=10)
    
    def test_max_value_limit(self):
        """测试最大值限制"""
        # 测试超过限制的情况
        result = self.strict_evaluator.evaluate("1001")
        self.assertIsInstance(result, str)
        self.assertIn("超过最大允许值", result)
        
        # 测试在限制内的情况
        result = self.strict_evaluator.evaluate("999")
        self.assertEqual(result, 999)
        
        # 测试计算结果超过限制的情况
        result = self.strict_evaluator.evaluate("50 * 50")
        self.assertIsInstance(result, str)
        self.assertIn("超过最大允许值", result)
    
    def test_invalid_expressions(self):
        """测试无效的表达式"""
        test_cases = [
            "invalid_expression",
            "5 +",
            "* 3",
            "5 & 3",  # 不支持的操作符
        ]
        
        for expression in test_cases:
            with self.subTest(expression=expression):
                result = self.evaluator.evaluate(expression)
                self.assertIsInstance(result, str)
                self.assertIn("错误", result)
    
    def test_complex_expressions(self):
        """测试复杂表达式"""
        test_cases = [
            ("(5 + 3) * 2", 16),
            ("10 / (2 + 3)", 2.0),
            ("2 ** (3 + 1)", 16),
            ("-(5 + 3)", -8),
        ]
        
        for expression, expected in test_cases:
            with self.subTest(expression=expression):
                result = self.evaluator.evaluate(expression)
                self.assertAlmostEqual(result, expected, places=10)
    
    def test_edge_cases(self):
        """测试边界情况"""
        # 测试非常小的数
        result = self.evaluator.evaluate("1e-100")
        self.assertAlmostEqual(result, 1e-100)
        
        # 测试零
        result = self.evaluator.evaluate("0")
        self.assertEqual(result, 0)
        
        # 测试负零
        result = self.evaluator.evaluate("-0")
        self.assertEqual(result, 0)


class TestInfinityDetection(unittest.TestCase):
    """无穷大检测测试类"""
    
    def setUp(self):
        self.evaluator = LiteralExpressionEvaluator()
    
    def test_infinity_detection(self):
        """测试无穷大检测功能"""
        # 这些表达式应该被检测为产生无穷大结果
        infinity_expressions = [
            "1.0 / 0.0",
            "float('inf')" if False else "1e400",  # 避免直接使用 float('inf')
        ]
        
        for expression in infinity_expressions:
            if expression == "1e400":  # 这个会产生溢出
                result = self.evaluator.evaluate(expression)
                self.assertIsInstance(result, str)
                self.assertTrue("溢出" in result or "无穷大" in result)


def run_comprehensive_tests():
    """运行综合测试"""
    print("运行字面表达式评估器测试...")
    print("=" * 60)
    
    # 创建测试套件
    test_suite = unittest.TestSuite()
    
    # 添加所有测试类
    test_classes = [TestLiteralExpressionEvaluator, TestInfinityDetection]
    
    for test_class in test_classes:
        tests = unittest.TestLoader().loadTestsFromTestCase(test_class)
        test_suite.addTests(tests)
    
    # 运行测试
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(test_suite)
    
    # 输出结果摘要
    print("\n" + "=" * 60)
    print(f"测试结果摘要:")
    print(f"运行测试数: {result.testsRun}")
    print(f"失败数: {len(result.failures)}")
    print(f"错误数: {len(result.errors)}")
    
    if result.failures:
        print("\n失败的测试:")
        for test, traceback in result.failures:
            print(f"- {test}: {traceback}")
    
    if result.errors:
        print("\n错误的测试:")
        for test, traceback in result.errors:
            print(f"- {test}: {traceback}")
    
    return result.wasSuccessful()


if __name__ == "__main__":
    success = run_comprehensive_tests()
    if success:
        print("\n✅ 所有测试通过！")
    else:
        print("\n❌ 部分测试失败！")