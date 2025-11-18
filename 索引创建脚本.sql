-- ============================================
-- ks_index_user_performance_details 表优化脚本
-- ============================================

-- 1. 查看表结构和现有索引
SHOW CREATE TABLE ks_index_user_performance_details;
SHOW INDEX FROM ks_index_user_performance_details;

-- 2. 分析当前删除语句的执行计划
EXPLAIN DELETE FROM ks_index_user_performance_details 
WHERE index_id = ? AND index_params = ?;

-- 3. 创建联合索引（推荐方案）
-- 注意：根据实际表结构调整，如果index_params是TEXT类型，可能需要使用前缀索引
CREATE INDEX idx_index_id_params 
ON ks_index_user_performance_details(index_id, index_params);

-- 如果index_params是VARCHAR/TEXT且很长，使用前缀索引：
-- CREATE INDEX idx_index_id_params 
-- ON ks_index_user_performance_details(index_id, index_params(50));

-- 4. 验证索引创建成功
SHOW INDEX FROM ks_index_user_performance_details 
WHERE Key_name = 'idx_index_id_params';

-- 5. 再次分析执行计划，确认使用新索引
EXPLAIN DELETE FROM ks_index_user_performance_details 
WHERE index_id = ? AND index_params = ?;

-- 6. 查看表统计信息（MySQL 8.0+）
-- ANALYZE TABLE ks_index_user_performance_details;

-- ============================================
-- 性能监控查询
-- ============================================

-- 查看当前正在执行的删除操作
SELECT 
    id,
    user,
    host,
    db,
    command,
    time,
    state,
    info
FROM information_schema.PROCESSLIST
WHERE info LIKE '%ks_index_user_performance_details%'
  AND info LIKE '%DELETE%';

-- 查看表锁等待情况
SELECT 
    r.trx_id waiting_trx_id,
    r.trx_mysql_thread_id waiting_thread,
    r.trx_query waiting_query,
    b.trx_id blocking_trx_id,
    b.trx_mysql_thread_id blocking_thread,
    b.trx_query blocking_query
FROM information_schema.innodb_lock_waits w
INNER JOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_trx_id
INNER JOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_trx_id;

-- 查看死锁信息（需要在MySQL错误日志中查看，或执行）
SHOW ENGINE INNODB STATUS\G
