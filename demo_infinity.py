#!/usr/bin/env python3
"""
演示 LiteralExpression 处理 Infinity 的示例
"""

from literal_expression import LiteralExpression, ExpressionEvaluator


def main():
    """主演示函数"""
    
    print("=" * 60)
    print("LiteralExpression 处理 Infinity 的演示")
    print("=" * 60)
    
    # 创建一个 Infinity 表达式
    expr = LiteralExpression("Infinity")
    print(f"\n创建表达式: LiteralExpression('Infinity')")
    print(f"输出: {expr}")
    print(f"内部值: {expr.result}")
    print(f"是否为无穷大: {expr.is_infinity()}")
    print(f"是否为正无穷大: {expr.is_positive_infinity()}")
    print(f"JSON 表示: {expr.to_json()}")
    
    print("\n" + "-" * 60)
    
    # 不同方式创建 Infinity
    print("\n不同方式创建 Infinity:")
    ways = [
        ("'Infinity'", LiteralExpression("Infinity")),
        ("'infinity'", LiteralExpression("infinity")),
        ("'inf'", LiteralExpression("inf")),
        ("float('inf')", LiteralExpression(float('inf'))),
    ]
    
    for description, expr in ways:
        print(f"  {description:20} -> {expr}")
    
    print("\n" + "-" * 60)
    
    # 负无穷大
    print("\n负无穷大:")
    neg_inf = LiteralExpression("-Infinity")
    print(f"  表达式: {neg_inf}")
    print(f"  是否为负无穷大: {neg_inf.is_negative_infinity()}")
    
    print("\n" + "-" * 60)
    
    # 使用评估器
    print("\n使用 ExpressionEvaluator:")
    evaluator = ExpressionEvaluator()
    
    expressions = [
        "Infinity",
        "math.inf",
        "1/0" if False else "Infinity",  # 避免实际的除零错误
        "float('inf')" if False else "Infinity",
    ]
    
    for expr_str in expressions:
        result = evaluator.evaluate(expr_str)
        print(f"  评估 '{expr_str}' -> {result}")
    
    print("\n" + "-" * 60)
    
    # 特殊值比较
    print("\n特殊值的比较:")
    inf1 = LiteralExpression("Infinity")
    inf2 = LiteralExpression(float('inf'))
    ninf = LiteralExpression("-Infinity")
    nan = LiteralExpression("NaN")
    
    print(f"  Infinity == Infinity: {inf1 == inf2}")
    print(f"  Infinity == -Infinity: {inf1 == ninf}")
    print(f"  Infinity == NaN: {inf1 == nan}")
    
    print("\n" + "-" * 60)
    
    # JSON 序列化
    print("\n JSON 序列化示例:")
    values = [
        ("Infinity", LiteralExpression("Infinity")),
        ("-Infinity", LiteralExpression("-Infinity")),
        ("NaN", LiteralExpression("NaN")),
        ("42", LiteralExpression(42)),
        ("null", LiteralExpression("null")),
    ]
    
    for name, expr in values:
        print(f"  {name:12} -> JSON: {expr.to_json()}")
    
    print("\n" + "=" * 60)
    print("演示完成！")
    print("=" * 60)


if __name__ == "__main__":
    main()