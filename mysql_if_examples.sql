-- MySQL IF 函数示例

-- 1. 基本语法：IF(condition, value_if_true, value_if_false)
-- 如果条件为真，返回第一个值；否则返回第二个值

-- 示例1: 简单的条件判断
SELECT 
    name,
    age,
    IF(age >= 18, '成年人', '未成年人') AS age_group
FROM users;

-- 示例2: 数值比较
SELECT 
    product_name,
    price,
    IF(price > 100, '高价', '普通价格') AS price_category
FROM products;

-- 示例3: 嵌套IF语句
SELECT 
    score,
    IF(score >= 90, '优秀',
        IF(score >= 80, '良好',
            IF(score >= 60, '及格', '不及格')
        )
    ) AS grade
FROM exam_results;

-- 示例4: 在UPDATE语句中使用IF
UPDATE products 
SET stock = IF(stock > 0, stock - 1, 0)
WHERE product_id = 123;

-- 示例5: 在WHERE子句中使用IF
SELECT * 
FROM orders 
WHERE IF(status = 'pending', created_at > DATE_SUB(NOW(), INTERVAL 7 DAY), TRUE);

-- 示例6: 计算字段中使用IF
SELECT 
    order_id,
    total_amount,
    IF(total_amount > 1000, total_amount * 0.9, total_amount) AS discounted_amount
FROM orders;

-- 示例7: 与CASE语句对比（IF更简洁，但CASE更灵活）
-- 使用IF
SELECT 
    status,
    IF(status = 'active', 1, 0) AS is_active
FROM accounts;

-- 使用CASE（多条件时更清晰）
SELECT 
    status,
    CASE 
        WHEN status = 'active' THEN 1
        WHEN status = 'inactive' THEN 0
        ELSE -1
    END AS is_active
FROM accounts;

-- 示例8: IFNULL函数（IF的特殊情况）
-- IFNULL(expr1, expr2) 等价于 IF(expr1 IS NULL, expr2, expr1)
SELECT 
    name,
    IFNULL(email, '未提供邮箱') AS email_display
FROM users;

-- 示例9: 在聚合函数中使用IF
SELECT 
    COUNT(*) AS total_users,
    SUM(IF(status = 'active', 1, 0)) AS active_users,
    SUM(IF(status = 'inactive', 1, 0)) AS inactive_users
FROM users;

-- 示例10: 日期判断
SELECT 
    order_date,
    IF(DATEDIFF(NOW(), order_date) <= 7, '最近一周', '更早') AS time_category
FROM orders;
