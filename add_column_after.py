"""
在指定列之后添加新列的功能
支持 SQL ALTER TABLE 操作
"""

def add_column_after_sql(table_name, new_column_name, column_definition, after_column_name):
    """
    生成在指定列之后添加新列的 SQL 语句
    
    参数:
        table_name: 表名
        new_column_name: 新列名
        column_definition: 列定义（数据类型和约束）
        after_column_name: 在此列之后添加新列
    
    返回:
        SQL ALTER TABLE 语句
    """
    # MySQL/MariaDB 语法
    sql = f"ALTER TABLE `{table_name}` ADD COLUMN `{new_column_name}` {column_definition} AFTER `{after_column_name}`;"
    return sql


def add_column_after_postgresql(table_name, new_column_name, column_definition, after_column_name):
    """
    PostgreSQL 不支持 AFTER 语法，需要使用其他方法
    这里提供一个使用系统表的方法
    """
    # PostgreSQL 需要先添加列，然后重新排序
    # 注意：PostgreSQL 不直接支持 AFTER，需要重建表或使用其他方法
    sql_add = f"ALTER TABLE {table_name} ADD COLUMN {new_column_name} {column_definition};"
    
    # 获取当前列的位置信息（需要查询系统表）
    # 这是一个简化的示例，实际实现需要更复杂的逻辑
    return sql_add


def add_column_after_sqlite(table_name, new_column_name, column_definition, after_column_name):
    """
    SQLite 不支持 AFTER 语法
    需要重建表来实现列的顺序
    """
    # SQLite 不支持直接指定列位置
    # 只能添加列到末尾
    sql = f"ALTER TABLE {table_name} ADD COLUMN {new_column_name} {column_definition};"
    return sql


# 示例使用
if __name__ == "__main__":
    # MySQL 示例
    mysql_sql = add_column_after_sql(
        table_name="users",
        new_column_name="email",
        column_definition="VARCHAR(255) NOT NULL",
        after_column_name="username"
    )
    print("MySQL SQL:")
    print(mysql_sql)
    
    print("\n" + "="*50 + "\n")
    
    # PostgreSQL 示例
    pg_sql = add_column_after_postgresql(
        table_name="users",
        new_column_name="email",
        column_definition="VARCHAR(255) NOT NULL",
        after_column_name="username"
    )
    print("PostgreSQL SQL:")
    print(pg_sql)
    
    print("\n" + "="*50 + "\n")
    
    # SQLite 示例
    sqlite_sql = add_column_after_sqlite(
        table_name="users",
        new_column_name="email",
        column_definition="VARCHAR(255) NOT NULL",
        after_column_name="username"
    )
    print("SQLite SQL:")
    print(sqlite_sql)
