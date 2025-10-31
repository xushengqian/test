-- MySQL 查询：字段为空时取另一个字段，不为空时取原字段
-- 使用 COALESCE 或 IFNULL 函数实现

-- 方法1: 使用 COALESCE (推荐，支持多个字段)
-- 语法: COALESCE(字段1, 字段2, 字段3, ...)
-- 返回第一个非 NULL 的值
SELECT 
    id,
    COALESCE(field1, field2) AS result_field
FROM your_table;

-- 方法2: 使用 IFNULL (MySQL 专用)
-- 语法: IFNULL(字段1, 字段2)
-- 如果字段1为NULL，返回字段2；否则返回字段1
SELECT 
    id,
    IFNULL(field1, field2) AS result_field
FROM your_table;

-- 示例：实际应用场景
-- 假设有一个用户表，name 字段可能为空，如果为空则使用 username
SELECT 
    id,
    COALESCE(name, username) AS display_name,
    email
FROM users;

-- 示例：处理空字符串（注意：空字符串不等于 NULL）
-- 如果需要同时处理 NULL 和空字符串，可以使用 CASE WHEN
SELECT 
    id,
    CASE 
        WHEN field1 IS NULL OR field1 = '' THEN field2
        ELSE field1
    END AS result_field
FROM your_table;

-- 示例：多字段优先级
-- 如果 field1 为空，取 field2；如果 field2 也为空，取 field3
SELECT 
    id,
    COALESCE(field1, field2, field3, '默认值') AS result_field
FROM your_table;
