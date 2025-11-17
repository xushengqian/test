"""
测试 add_column_after 功能
"""

from add_column_after import add_column_after_sql, add_column_after_postgresql, add_column_after_sqlite


def test_mysql():
    """测试 MySQL 语法"""
    sql = add_column_after_sql(
        table_name="products",
        new_column_name="price",
        column_definition="DECIMAL(10, 2) NOT NULL DEFAULT 0.00",
        after_column_name="name"
    )
    expected = "ALTER TABLE `products` ADD COLUMN `price` DECIMAL(10, 2) NOT NULL DEFAULT 0.00 AFTER `name`;"
    assert sql == expected, f"Expected: {expected}, Got: {sql}"
    print("✓ MySQL test passed")
    print(f"  SQL: {sql}")


def test_postgresql():
    """测试 PostgreSQL 语法"""
    sql = add_column_after_postgresql(
        table_name="products",
        new_column_name="price",
        column_definition="DECIMAL(10, 2) NOT NULL DEFAULT 0.00",
        after_column_name="name"
    )
    assert "ADD COLUMN" in sql, "PostgreSQL SQL should contain ADD COLUMN"
    print("✓ PostgreSQL test passed")
    print(f"  SQL: {sql}")


def test_sqlite():
    """测试 SQLite 语法"""
    sql = add_column_after_sqlite(
        table_name="products",
        new_column_name="price",
        column_definition="DECIMAL(10, 2) NOT NULL DEFAULT 0.00",
        after_column_name="name"
    )
    assert "ADD COLUMN" in sql, "SQLite SQL should contain ADD COLUMN"
    print("✓ SQLite test passed")
    print(f"  SQL: {sql}")


if __name__ == "__main__":
    print("Running tests for add_column_after functionality...\n")
    test_mysql()
    print()
    test_postgresql()
    print()
    test_sqlite()
    print("\n所有测试通过！")
