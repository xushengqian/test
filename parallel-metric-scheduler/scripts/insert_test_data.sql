-- 插入测试数据
USE metric_scheduler;

-- 插入指标定义
INSERT INTO metrics (metric_code, metric_name, metric_type, metric_sql, description, is_active) VALUES
('DAILY_USER_COUNT', '每日用户数统计', 'SQL', 'SELECT COUNT(DISTINCT user_id) as value, CURDATE() as calc_date FROM users WHERE DATE(created_at) = CURDATE()', '统计每日新增用户数', TRUE),
('DAILY_ORDER_AMOUNT', '每日订单金额统计', 'SQL', 'SELECT SUM(amount) as value, CURDATE() as calc_date FROM orders WHERE DATE(order_time) = CURDATE()', '统计每日订单总金额', TRUE),
('HOURLY_ACTIVE_USERS', '每小时活跃用户数', 'SQL', 'SELECT COUNT(DISTINCT user_id) as value, DATE_FORMAT(NOW(), "%Y-%m-%d %H:00:00") as calc_time FROM user_activities WHERE activity_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)', '统计每小时活跃用户数', TRUE);

-- 插入调度配置
INSERT INTO schedules (schedule_code, schedule_name, schedule_type, cron_expression, is_active) VALUES
('DAILY_0AM', '每日凌晨执行', 'CRON', '0 0 0 * * ?', TRUE),
('HOURLY', '每小时执行', 'CRON', '0 0 * * * ?', TRUE),
('EVERY_5MIN', '每5分钟执行', 'CRON', '0 */5 * * * ?', TRUE);

-- 插入指标调度关联
INSERT INTO metric_schedules (metric_id, schedule_id, max_retry_times, timeout_seconds, priority, is_active)
SELECT m.id, s.id, 3, 1800, 10, TRUE
FROM metrics m, schedules s
WHERE m.metric_code = 'DAILY_USER_COUNT' AND s.schedule_code = 'DAILY_0AM';

INSERT INTO metric_schedules (metric_id, schedule_id, max_retry_times, timeout_seconds, priority, is_active)
SELECT m.id, s.id, 3, 1800, 10, TRUE
FROM metrics m, schedules s
WHERE m.metric_code = 'DAILY_ORDER_AMOUNT' AND s.schedule_code = 'DAILY_0AM';

INSERT INTO metric_schedules (metric_id, schedule_id, max_retry_times, timeout_seconds, priority, 5, TRUE)
SELECT m.id, s.id, 2, 600, 5, TRUE
FROM metrics m, schedules s
WHERE m.metric_code = 'HOURLY_ACTIVE_USERS' AND s.schedule_code = 'HOURLY';
