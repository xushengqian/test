-- MySQL CONCAT 函数示例
-- CONCAT 函数用于连接两个或多个字符串

-- 基本语法
-- CONCAT(str1, str2, str3, ...)

-- ============================================
-- 示例 1: 基本字符串连接
-- ============================================
SELECT CONCAT('Hello', ' ', 'World') AS result;
-- 结果: 'Hello World'

-- ============================================
-- 示例 2: 连接表中的字段
-- ============================================
-- 假设有一个 users 表，包含 first_name 和 last_name 字段
-- SELECT CONCAT(first_name, ' ', last_name) AS full_name FROM users;

-- ============================================
-- 示例 3: 连接多个字段和字符串
-- ============================================
-- SELECT CONCAT(first_name, ' ', last_name, ' (', email, ')') AS user_info FROM users;

-- ============================================
-- 示例 4: 处理 NULL 值
-- ============================================
-- CONCAT 函数会忽略 NULL 值，但可以使用 IFNULL 或 COALESCE 处理
SELECT CONCAT('Hello', IFNULL(NULL, ''), 'World') AS result;
-- 结果: 'HelloWorld'

SELECT CONCAT('Hello', COALESCE(NULL, ''), 'World') AS result;
-- 结果: 'HelloWorld'

-- ============================================
-- 示例 5: 连接数字和字符串
-- ============================================
SELECT CONCAT('用户ID: ', 12345) AS result;
-- 结果: '用户ID: 12345'

-- ============================================
-- 示例 6: 使用 CONCAT_WS (带分隔符的连接)
-- ============================================
-- CONCAT_WS(separator, str1, str2, ...) - 用指定分隔符连接字符串
SELECT CONCAT_WS(' - ', '2024', '01', '15') AS date_string;
-- 结果: '2024 - 01 - 15'

SELECT CONCAT_WS(', ', 'John', 'Doe', 'john@example.com') AS user_info;
-- 结果: 'John, Doe, john@example.com'

-- CONCAT_WS 会自动忽略 NULL 值
SELECT CONCAT_WS(' - ', 'Hello', NULL, 'World') AS result;
-- 结果: 'Hello - World'

-- ============================================
-- 示例 7: 实际应用场景
-- ============================================

-- 场景 1: 生成完整的地址
-- SELECT CONCAT_WS(', ', 
--     address_line1, 
--     address_line2, 
--     city, 
--     state, 
--     postal_code
-- ) AS full_address FROM addresses;

-- 场景 2: 生成文件路径
-- SELECT CONCAT('/var/www/', folder_name, '/', file_name) AS file_path FROM files;

-- 场景 3: 生成显示名称（带前缀或后缀）
-- SELECT CONCAT('Mr. ', first_name, ' ', last_name) AS formal_name FROM users;

-- ============================================
-- 示例 8: 与其他函数结合使用
-- ============================================
-- SELECT CONCAT(
--     UPPER(first_name), 
--     ' ', 
--     UPPER(last_name)
-- ) AS full_name_upper FROM users;

-- SELECT CONCAT(
--     LEFT(first_name, 1), 
--     '. ', 
--     last_name
-- ) AS abbreviated_name FROM users;

-- ============================================
-- 注意事项
-- ============================================
-- 1. CONCAT 函数可以接受多个参数（MySQL 5.7+）
-- 2. 如果所有参数都是 NULL，CONCAT 返回 NULL
-- 3. CONCAT_WS 会自动忽略 NULL 值，更适合处理可能为空的字段
-- 4. 在 MySQL 8.0+ 中，可以使用 || 运算符作为 CONCAT 的替代（需要设置 sql_mode）
