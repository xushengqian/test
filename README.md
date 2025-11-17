# Add Column After

在指定列之后添加新列的功能实现。

## 功能说明

这个项目实现了在数据库表中指定列之后添加新列的功能，支持多种数据库系统：

- **MySQL/MariaDB**: 使用 `AFTER` 语法直接支持
- **PostgreSQL**: 需要特殊处理（PostgreSQL 不直接支持 AFTER）
- **SQLite**: 不支持列位置指定，只能添加到末尾

## 使用方法

```python
from add_column_after import add_column_after_sql

# MySQL 示例
sql = add_column_after_sql(
    table_name="users",
    new_column_name="email",
    column_definition="VARCHAR(255) NOT NULL",
    after_column_name="username"
)
```

## 运行测试

```bash
python test_add_column.py
```

## 文件说明

- `add_column_after.py`: 主要功能实现
- `test_add_column.py`: 测试文件