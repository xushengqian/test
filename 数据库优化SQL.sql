-- ============================================
-- MySQL 锁等待超时问题 - 数据库优化 SQL
-- ============================================

-- 1. 检查当前锁等待超时设置
SHOW VARIABLES LIKE 'innodb_lock_wait_timeout';

-- 2. 临时增加锁等待超时时间（会话级别）
SET innodb_lock_wait_timeout = 120;

-- 3. 全局设置锁等待超时时间（需要重启或重新连接生效）
SET GLOBAL innodb_lock_wait_timeout = 120;

-- ============================================
-- 4. 检查表结构和索引
-- ============================================

-- 查看表结构
SHOW CREATE TABLE ks_index_user_performance;

-- 查看表的索引
SHOW INDEX FROM ks_index_user_performance;

-- ============================================
-- 5. 创建/优化索引（确保唯一索引存在）
-- ============================================

-- 方案 A：如果 month 和 user_id 组合是唯一的
ALTER TABLE ks_index_user_performance 
ADD UNIQUE INDEX idx_month_user (month, user_id);

-- 方案 B：如果 index_params 是唯一的（推荐，因为 SQL 中使用了它）
ALTER TABLE ks_index_user_performance 
ADD UNIQUE INDEX idx_index_params (index_params);

-- 方案 C：如果两个都需要
ALTER TABLE ks_index_user_performance 
ADD UNIQUE INDEX idx_month_user (month, user_id),
ADD UNIQUE INDEX idx_index_params (index_params);

-- ============================================
-- 6. 检查当前锁等待情况
-- ============================================

-- 查看所有正在运行的事务
SELECT 
    trx_id,
    trx_state,
    trx_started,
    trx_mysql_thread_id,
    trx_query,
    TIMESTAMPDIFF(SECOND, trx_started, NOW()) as duration_seconds
FROM information_schema.innodb_trx
ORDER BY trx_started;

-- 查看锁等待情况
SELECT 
    r.trx_id AS waiting_trx_id,
    r.trx_mysql_thread_id AS waiting_thread,
    r.trx_query AS waiting_query,
    b.trx_id AS blocking_trx_id,
    b.trx_mysql_thread_id AS blocking_thread,
    b.trx_query AS blocking_query,
    TIMESTAMPDIFF(SECOND, r.trx_wait_started, NOW()) AS wait_seconds
FROM information_schema.innodb_lock_waits w
INNER JOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_trx_id
INNER JOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_trx_id;

-- 查看所有锁信息
SELECT 
    lock_id,
    lock_trx_id,
    lock_mode,
    lock_type,
    lock_table,
    lock_index,
    lock_space,
    lock_page,
    lock_rec,
    lock_data
FROM information_schema.innodb_locks;

-- ============================================
-- 7. 查找长时间运行的事务（可能需要终止）
-- ============================================

-- 查找运行超过 60 秒的事务
SELECT 
    trx_id,
    trx_mysql_thread_id,
    trx_started,
    TIMESTAMPDIFF(SECOND, trx_started, NOW()) AS duration_seconds,
    trx_query
FROM information_schema.innodb_trx
WHERE TIMESTAMPDIFF(SECOND, trx_started, NOW()) > 60
ORDER BY trx_started;

-- ============================================
-- 8. 终止阻塞的事务（谨慎使用！）
-- ============================================

-- 首先查看需要终止的线程 ID
SELECT trx_mysql_thread_id 
FROM information_schema.innodb_trx 
WHERE trx_id = '<blocking_trx_id>';

-- 终止线程（替换 <thread_id> 为实际的线程 ID）
-- KILL <thread_id>;

-- ============================================
-- 9. 优化表（如果表碎片化严重）
-- ============================================

-- 分析表
ANALYZE TABLE ks_index_user_performance;

-- 优化表（会锁表，在低峰期执行）
-- OPTIMIZE TABLE ks_index_user_performance;

-- ============================================
-- 10. 检查表状态和性能
-- ============================================

-- 查看表状态
SHOW TABLE STATUS LIKE 'ks_index_user_performance';

-- 查看慢查询（如果开启了慢查询日志）
-- SELECT * FROM mysql.slow_log WHERE sql_text LIKE '%ks_index_user_performance%';

-- ============================================
-- 11. 测试 SQL 执行计划
-- ============================================

-- 查看执行计划，确保使用了索引
EXPLAIN 
INSERT INTO ks_index_user_performance (month, user_id, index_params, kfzj_deal_amount) 
VALUES ('2025-12', '202508118005', '2025-12@202508118005', '3884400.00') 
ON DUPLICATE KEY UPDATE kfzj_deal_amount = '3884400.00';

-- ============================================
-- 12. 监控建议
-- ============================================

-- 定期执行以下查询监控锁等待情况
-- 可以创建定时任务或监控脚本

-- 监控锁等待数量
SELECT COUNT(*) AS lock_wait_count
FROM information_schema.innodb_lock_waits;

-- 监控平均等待时间
SELECT 
    AVG(TIMESTAMPDIFF(SECOND, r.trx_wait_started, NOW())) AS avg_wait_seconds
FROM information_schema.innodb_lock_waits w
INNER JOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_trx_id;
