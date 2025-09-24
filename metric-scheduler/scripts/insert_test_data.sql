-- 插入测试数据
USE metric_scheduler;

-- 插入更多示例指标
INSERT INTO metrics (metric_code, metric_name, metric_type, metric_sql, description) VALUES
('HOURLY_SALES_AMOUNT', '小时销售额', 'SQL', 
 'SELECT SUM(amount) as value, DATE_FORMAT(sale_time, "%Y-%m-%d %H:00:00") as calc_time, category FROM sales WHERE sale_time >= NOW() - INTERVAL 1 HOUR GROUP BY DATE_FORMAT(sale_time, "%Y-%m-%d %H:00:00"), category',
 '统计每小时各品类销售额'),
 
('USER_ACTIVITY_SCRIPT', '用户活跃度脚本指标', 'SCRIPT',
 NULL,
 '通过脚本计算用户活跃度指标');

UPDATE metrics SET script_path = '/workspace/metric-scheduler/scripts/example_metric.py' 
WHERE metric_code = 'USER_ACTIVITY_SCRIPT';

-- 插入更多调度配置  
INSERT INTO schedules (schedule_code, schedule_name, schedule_type, cron_expression, fixed_rate_seconds) VALUES
('EVERY_30MIN', '每30分钟执行', 'FIXED_RATE', NULL, 1800),
('DAILY_6AM', '每日早上6点执行', 'CRON', '0 0 6 * * ?', NULL);

-- 绑定指标和调度
INSERT INTO metric_schedules (metric_id, schedule_id, priority, max_retry_times, timeout_seconds) 
SELECT m.id, s.id, 10, 3, 1800
FROM metrics m, schedules s
WHERE m.metric_code = 'HOURLY_SALES_AMOUNT' AND s.schedule_code = 'HOURLY';

INSERT INTO metric_schedules (metric_id, schedule_id, priority, max_retry_times, timeout_seconds) 
SELECT m.id, s.id, 5, 2, 3600
FROM metrics m, schedules s
WHERE m.metric_code = 'USER_ACTIVITY_SCRIPT' AND s.schedule_code = 'DAILY_6AM';

-- 创建测试用的数据表
CREATE TABLE IF NOT EXISTS user_login_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    login_time DATETIME NOT NULL,
    INDEX idx_login_time (login_time),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL,
    user_id BIGINT NOT NULL,
    order_amount DECIMAL(10,2) NOT NULL,
    create_time DATETIME NOT NULL,
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sales (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sale_no VARCHAR(50) NOT NULL,
    category VARCHAR(50) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    sale_time DATETIME NOT NULL,
    INDEX idx_sale_time (sale_time),
    INDEX idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 插入测试数据
DELIMITER //
CREATE PROCEDURE insert_test_data()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE cur_date DATETIME;
    
    -- 插入登录日志数据
    WHILE i < 1000 DO
        SET cur_date = NOW() - INTERVAL FLOOR(RAND() * 7) DAY - INTERVAL FLOOR(RAND() * 24) HOUR;
        INSERT INTO user_login_logs (user_id, login_time) 
        VALUES (FLOOR(RAND() * 10000) + 1, cur_date);
        SET i = i + 1;
    END WHILE;
    
    -- 插入订单数据
    SET i = 0;
    WHILE i < 500 DO
        SET cur_date = NOW() - INTERVAL FLOOR(RAND() * 7) DAY - INTERVAL FLOOR(RAND() * 24) HOUR;
        INSERT INTO orders (order_no, user_id, order_amount, create_time) 
        VALUES (
            CONCAT('ORD', LPAD(i + 1, 6, '0')),
            FLOOR(RAND() * 10000) + 1,
            ROUND(RAND() * 1000 + 10, 2),
            cur_date
        );
        SET i = i + 1;
    END WHILE;
    
    -- 插入销售数据
    SET i = 0;
    WHILE i < 800 DO
        SET cur_date = NOW() - INTERVAL FLOOR(RAND() * 7) DAY - INTERVAL FLOOR(RAND() * 24) HOUR;
        INSERT INTO sales (sale_no, category, amount, sale_time) 
        VALUES (
            CONCAT('SALE', LPAD(i + 1, 6, '0')),
            ELT(FLOOR(RAND() * 5) + 1, '电子产品', '服装', '食品', '图书', '家居'),
            ROUND(RAND() * 500 + 10, 2),
            cur_date
        );
        SET i = i + 1;
    END WHILE;
END//
DELIMITER ;

-- 执行插入测试数据
CALL insert_test_data();