-- MySQL 条件字段值获取示例
-- 场景：如果字段A为空，则取字段B的值；如果字段A不为空，则取字段A的值

-- 方法1: 使用 COALESCE() 函数（推荐）
-- COALESCE 返回第一个非NULL的值
-- 语法: COALESCE(field1, field2, field3, ...)
SELECT 
    COALESCE(field_a, field_b) AS result_field
FROM your_table;

-- 示例：
SELECT 
    id,
    field_a,
    field_b,
    COALESCE(field_a, field_b) AS final_value
FROM your_table;


-- 方法2: 使用 IFNULL() 函数
-- IFNULL 只支持两个参数：如果第一个为NULL则返回第二个
-- 语法: IFNULL(expr1, expr2)
SELECT 
    IFNULL(field_a, field_b) AS result_field
FROM your_table;

-- 示例：
SELECT 
    id,
    field_a,
    field_b,
    IFNULL(field_a, field_b) AS final_value
FROM your_table;


-- 方法3: 使用 CASE WHEN 语句（更灵活，可处理复杂条件）
SELECT 
    CASE 
        WHEN field_a IS NULL OR field_a = '' THEN field_b
        ELSE field_a
    END AS result_field
FROM your_table;

-- 示例：
SELECT 
    id,
    field_a,
    field_b,
    CASE 
        WHEN field_a IS NULL OR field_a = '' THEN field_b
        ELSE field_a
    END AS final_value
FROM your_table;


-- 方法4: 如果字段A不为空且不为空字符串，取字段A，否则取字段B
SELECT 
    CASE 
        WHEN field_a IS NOT NULL AND field_a != '' THEN field_a
        ELSE field_b
    END AS result_field
FROM your_table;


-- 实际应用示例：
-- 假设有一个用户表，有手机号和邮箱字段
-- 如果手机号为空，则显示邮箱；否则显示手机号
CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50),
    phone VARCHAR(20),
    email VARCHAR(100)
);

-- 查询示例：
SELECT 
    id,
    username,
    phone,
    email,
    COALESCE(phone, email) AS contact_info,
    IFNULL(phone, email) AS contact_info2
FROM users;


-- 多个字段的级联选择：
-- 如果field_a不为空取field_a，否则如果field_b不为空取field_b，否则取field_c
SELECT 
    COALESCE(field_a, field_b, field_c) AS result_field
FROM your_table;
