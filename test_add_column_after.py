#!/usr/bin/env python3
"""
测试add_column_after功能
"""

import sys
from add_column_after import (
    add_column_after_list,
    add_column_after_dict,
    add_column_after
)


def test_add_column_after_list():
    """测试二维列表添加列功能"""
    print("测试 1: 二维列表 - 在中间位置添加列")
    data = [
        ['A', 'B', 'C'],
        [1, 2, 3],
        [4, 5, 6]
    ]
    new_col = ['D', 7, 8]
    result = add_column_after_list(data, 1, new_col)
    expected = [
        ['A', 'B', 'D', 'C'],
        [1, 2, 7, 3],
        [4, 5, 8, 6]
    ]
    assert result == expected, f"预期 {expected}, 得到 {result}"
    print("✓ 通过")
    
    print("\n测试 2: 二维列表 - 在开头添加列")
    result = add_column_after_list(data, -1, new_col)
    expected = [
        ['D', 'A', 'B', 'C'],
        [7, 1, 2, 3],
        [8, 4, 5, 6]
    ]
    assert result == expected, f"预期 {expected}, 得到 {result}"
    print("✓ 通过")
    
    print("\n测试 3: 二维列表 - 在末尾添加列")
    result = add_column_after_list(data, 2, new_col)
    expected = [
        ['A', 'B', 'C', 'D'],
        [1, 2, 3, 7],
        [4, 5, 6, 8]
    ]
    assert result == expected, f"预期 {expected}, 得到 {result}"
    print("✓ 通过")


def test_add_column_after_dict():
    """测试字典列表添加列功能"""
    print("\n测试 4: 字典列表 - 在指定列之后添加")
    data = [
        {'name': 'Alice', 'age': 25, 'city': 'NY'},
        {'name': 'Bob', 'age': 30, 'city': 'LA'}
    ]
    result = add_column_after_dict(data, 'name', 'gender', ['Female', 'Male'])
    
    # 检查键的顺序
    assert list(result[0].keys()) == ['name', 'gender', 'age', 'city'], \
        f"列顺序不正确: {list(result[0].keys())}"
    assert result[0]['gender'] == 'Female', "第一行性别应为Female"
    assert result[1]['gender'] == 'Male', "第二行性别应为Male"
    print("✓ 通过")
    
    print("\n测试 5: 字典列表 - 使用单个值")
    result = add_column_after_dict(data, 'age', 'country', 'USA')
    assert result[0]['country'] == 'USA', "国家应为USA"
    assert result[1]['country'] == 'USA', "国家应为USA"
    print("✓ 通过")


def test_add_column_after_generic():
    """测试通用函数"""
    print("\n测试 6: 通用函数 - 自动检测二维列表")
    data = [['A', 'B'], [1, 2], [3, 4]]
    result = add_column_after(data, 0, 'C', ['C', 5, 6])
    assert result[0] == ['A', 'C', 'B'], f"预期 ['A', 'C', 'B'], 得到 {result[0]}"
    print("✓ 通过")
    
    print("\n测试 7: 通用函数 - 自动检测字典列表")
    data = [{'x': 1, 'y': 2}, {'x': 3, 'y': 4}]
    result = add_column_after(data, 'x', 'z', [5, 6])
    assert result[0]['z'] == 5, "z列的值应为5"
    assert list(result[0].keys()) == ['x', 'z', 'y'], "列顺序不正确"
    print("✓ 通过")


def test_error_handling():
    """测试错误处理"""
    print("\n测试 8: 错误处理 - 列长度不匹配")
    data = [['A', 'B'], [1, 2]]
    try:
        add_column_after_list(data, 0, ['C'])  # 长度不匹配
        assert False, "应该抛出ValueError"
    except ValueError as e:
        print(f"✓ 正确捕获错误: {e}")
    
    print("\n测试 9: 错误处理 - 字典列表值数量不匹配")
    data = [{'x': 1}, {'x': 2}]
    try:
        add_column_after_dict(data, 'x', 'y', [1, 2, 3])  # 数量不匹配
        assert False, "应该抛出ValueError"
    except ValueError as e:
        print(f"✓ 正确捕获错误: {e}")


def test_real_world_example():
    """真实场景示例"""
    print("\n测试 10: 真实场景 - 员工数据表")
    employees = [
        {'employee_id': 'E001', 'name': '张三', 'department': '技术部', 'salary': 15000},
        {'employee_id': 'E002', 'name': '李四', 'department': '销售部', 'salary': 12000},
        {'employee_id': 'E003', 'name': '王五', 'department': '技术部', 'salary': 18000},
    ]
    
    # 在name之后添加email列
    emails = ['zhangsan@company.com', 'lisi@company.com', 'wangwu@company.com']
    result = add_column_after_dict(employees, 'name', 'email', emails)
    
    print("添加email列后的结果:")
    for emp in result:
        print(f"  {emp}")
    
    assert result[0]['email'] == 'zhangsan@company.com', "email不正确"
    assert list(result[0].keys())[2] == 'email', "email应该在name之后"
    print("✓ 通过")


def run_all_tests():
    """运行所有测试"""
    print("=" * 60)
    print("开始运行测试...")
    print("=" * 60)
    
    try:
        test_add_column_after_list()
        test_add_column_after_dict()
        test_add_column_after_generic()
        test_error_handling()
        test_real_world_example()
        
        print("\n" + "=" * 60)
        print("✓ 所有测试通过！")
        print("=" * 60)
        return True
    except AssertionError as e:
        print(f"\n✗ 测试失败: {e}")
        return False
    except Exception as e:
        print(f"\n✗ 发生错误: {e}")
        import traceback
        traceback.print_exc()
        return False


if __name__ == "__main__":
    success = run_all_tests()
    sys.exit(0 if success else 1)
