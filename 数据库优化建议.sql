-- ============================================
-- 死锁问题优化建议 - SQL脚本
-- ============================================

-- 1. 创建复合索引（提高查询效率，减少锁范围）
-- 建议在 index_id 和 index_params 上创建复合索引
CREATE INDEX idx_index_id_params 
ON ks_index_user_performance_details(index_id, index_params);

-- 如果 index_id 已经有索引，可以考虑只创建复合索引
-- 注意：需要根据实际查询模式决定索引策略

-- 2. 查看表结构和现有索引
SHOW CREATE TABLE ks_index_user_performance_details;
SHOW INDEX FROM ks_index_user_performance_details;

-- 3. 分析查询执行计划（确保使用了索引）
EXPLAIN DELETE FROM ks_index_user_performance_details 
WHERE index_id = 349 AND index_params = '2025-11@202502258001' 
LIMIT 300;

-- 4. 查看死锁信息（MySQL 5.7+）
SHOW ENGINE INNODB STATUS;

-- 5. 查看当前锁信息
SELECT * FROM information_schema.INNODB_LOCKS;
SELECT * FROM information_schema.INNODB_LOCK_WAITS;

-- 6. 优化建议：
--    - 确保 WHERE 条件中的字段都有索引
--    - 考虑使用覆盖索引
--    - 定期分析表：ANALYZE TABLE ks_index_user_performance_details;
--    - 如果数据量很大，考虑分区表

-- 7. 如果业务允许，可以考虑调整隔离级别（在连接级别）
-- SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- 8. 监控慢查询
-- SET GLOBAL slow_query_log = 'ON';
-- SET GLOBAL long_query_time = 1;
