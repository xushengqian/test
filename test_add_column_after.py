"""
测试 add_column_after 功能
"""

import unittest
from add_column_after import (
    add_column_after_list,
    add_column_after_dict,
    add_column_after,
)


class TestAddColumnAfter(unittest.TestCase):
    
    def test_add_column_after_dict(self):
        """测试字典中添加列"""
        data = {"name": "张三", "age": 25, "city": "北京"}
        result = add_column_after_dict(data, "email", "age", "test@example.com")
        
        # 检查新列是否存在
        self.assertIn("email", result)
        self.assertEqual(result["email"], "test@example.com")
        
        # 检查列的顺序（在 age 之后）
        keys = list(result.keys())
        age_index = keys.index("age")
        email_index = keys.index("email")
        self.assertEqual(email_index, age_index + 1)
    
    def test_add_column_after_list(self):
        """测试列表中添加列"""
        data = [
            {"name": "张三", "age": 25, "city": "北京"},
            {"name": "李四", "age": 30, "city": "上海"}
        ]
        result = add_column_after_list(data, "email", "age", "test@example.com")
        
        # 检查所有行都有新列
        for row in result:
            self.assertIn("email", row)
            self.assertEqual(row["email"], "test@example.com")
            
            # 检查列的顺序
            keys = list(row.keys())
            age_index = keys.index("age")
            email_index = keys.index("email")
            self.assertEqual(email_index, age_index + 1)
    
    def test_add_column_after_generic(self):
        """测试通用函数"""
        # 测试字典
        data_dict = {"a": 1, "b": 2, "c": 3}
        result_dict = add_column_after(data_dict, "d", "b", 4)
        self.assertIn("d", result_dict)
        
        # 测试列表
        data_list = [{"a": 1, "b": 2}, {"a": 3, "b": 4}]
        result_list = add_column_after(data_list, "c", "a", 5)
        for row in result_list:
            self.assertIn("c", row)
    
    def test_error_handling(self):
        """测试错误处理"""
        data = {"name": "张三", "age": 25}
        
        # 测试不存在的列
        with self.assertRaises(ValueError):
            add_column_after_dict(data, "email", "nonexistent", "test@example.com")
        
        # 测试不支持的类型
        with self.assertRaises(TypeError):
            add_column_after([1, 2, 3], "col", "after", "value")


if __name__ == "__main__":
    unittest.main()
