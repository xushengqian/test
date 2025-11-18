-- ================================================================
-- DELETE 性能优化 SQL 脚本
-- 表: ks_index_user_performance_details
-- 优化目标: 提升 WHERE index_id = ? AND index_params = ? 的删除性能
-- ================================================================

-- ----------------------------------------------------------------
-- 第一步：诊断现状
-- ----------------------------------------------------------------

-- 1. 查看表结构和现有索引
SHOW CREATE TABLE ks_index_user_performance_details;
SHOW INDEX FROM ks_index_user_performance_details;

-- 2. 查看表数据量
SELECT COUNT(*) as total_rows 
FROM ks_index_user_performance_details;

-- 3. 检查 index_id 和 index_params 字段的数据分布
SELECT 
    COUNT(DISTINCT index_id) as distinct_index_ids,
    COUNT(DISTINCT index_params) as distinct_index_params,
    AVG(LENGTH(index_params)) as avg_params_length
FROM ks_index_user_performance_details;

-- 4. 查看执行计划（替换 1 和 'test' 为实际值）
EXPLAIN DELETE FROM ks_index_user_performance_details 
WHERE index_id = 1 AND index_params = 'test';

-- ----------------------------------------------------------------
-- 第二步：创建优化索引（核心解决方案）
-- ----------------------------------------------------------------

-- 方案 A：复合索引（推荐）
-- 适用于 index_params 字段不是太大的情况
CREATE INDEX idx_index_id_params 
ON ks_index_user_performance_details(index_id, index_params)
ALGORITHM=INPLACE, LOCK=NONE;

-- 方案 B：如果 index_params 是 VARCHAR 且很长，使用前缀索引
-- 注意：根据实际情况调整前缀长度（50-200）
-- CREATE INDEX idx_index_id_params 
-- ON ks_index_user_performance_details(index_id, index_params(100))
-- ALGORITHM=INPLACE, LOCK=NONE;

-- 方案 C：如果 index_params 是 TEXT/BLOB 类型，只建 index_id 索引
-- CREATE INDEX idx_index_id 
-- ON ks_index_user_performance_details(index_id)
-- ALGORITHM=INPLACE, LOCK=NONE;

-- ----------------------------------------------------------------
-- 第三步：验证索引创建成功
-- ----------------------------------------------------------------

-- 1. 确认索引已创建
SHOW INDEX FROM ks_index_user_performance_details;

-- 2. 查看索引统计信息
SELECT 
    INDEX_NAME,
    SEQ_IN_INDEX,
    COLUMN_NAME,
    CARDINALITY,
    SUB_PART
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ks_index_user_performance_details'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 3. 再次查看执行计划，确认使用了新索引
EXPLAIN DELETE FROM ks_index_user_performance_details 
WHERE index_id = 1 AND index_params = 'test';
-- 期望看到: type=ref 或 range, key=idx_index_id_params

-- ----------------------------------------------------------------
-- 第四步：性能测试（可选）
-- ----------------------------------------------------------------

-- 测试删除性能（请根据实际数据修改参数）
-- 注意：先在测试环境执行
SET PROFILING = 1;

DELETE FROM ks_index_user_performance_details 
WHERE index_id = 1 AND index_params = 'test_value';

SHOW PROFILES;
SHOW PROFILE FOR QUERY 1;

SET PROFILING = 0;

-- ----------------------------------------------------------------
-- 第五步：死锁监控和诊断
-- ----------------------------------------------------------------

-- 1. 查看当前锁等待情况
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

-- 2. 查看最近的死锁信息
SHOW ENGINE INNODB STATUS;
-- 在输出中查找 "LATEST DETECTED DEADLOCK" 部分

-- MySQL 8.0+ 可以使用这些视图
-- SELECT * FROM performance_schema.data_locks;
-- SELECT * FROM performance_schema.data_lock_waits;

-- ----------------------------------------------------------------
-- 第六步：数据库配置优化（可选）
-- ----------------------------------------------------------------

-- 查看当前锁等待超时设置
SHOW VARIABLES LIKE 'innodb_lock_wait_timeout';

-- 设置锁等待超时（会话级别，不影响其他连接）
-- SET innodb_lock_wait_timeout = 5;

-- 查看事务隔离级别
SELECT @@transaction_isolation;

-- ----------------------------------------------------------------
-- 维护命令
-- ----------------------------------------------------------------

-- 更新表统计信息（定期执行，帮助优化器选择更好的执行计划）
ANALYZE TABLE ks_index_user_performance_details;

-- 如果需要删除索引（回滚操作）
-- DROP INDEX idx_index_id_params ON ks_index_user_performance_details;

-- ----------------------------------------------------------------
-- 批量删除示例（针对大量数据删除场景）
-- ----------------------------------------------------------------

-- 如果需要删除大量数据，使用 LIMIT 分批删除
-- 示例：每次删除 1000 条，循环执行直到 affected_rows = 0

/*
DELIMITER $$

CREATE PROCEDURE batch_delete_performance_details(
    IN p_index_id BIGINT,
    IN p_index_params VARCHAR(500),
    IN p_batch_size INT
)
BEGIN
    DECLARE deleted_rows INT DEFAULT 0;
    
    REPEAT
        DELETE FROM ks_index_user_performance_details 
        WHERE index_id = p_index_id 
          AND index_params = p_index_params
        LIMIT p_batch_size;
        
        SET deleted_rows = ROW_COUNT();
        
        -- 短暂休眠，释放锁资源
        IF deleted_rows > 0 THEN
            DO SLEEP(0.01);  -- 休眠 10ms
        END IF;
        
    UNTIL deleted_rows = 0 END REPEAT;
END$$

DELIMITER ;

-- 调用存储过程
-- CALL batch_delete_performance_details(1, 'test_value', 1000);
*/

-- ================================================================
-- 执行检查清单
-- ================================================================
/*
□ 1. 在生产环境执行前，先在测试环境验证
□ 2. 确认索引创建完成且没有错误
□ 3. 验证执行计划使用了新索引
□ 4. 测试删除操作的性能提升
□ 5. 监控一段时间，确认死锁问题解决
□ 6. 定期执行 ANALYZE TABLE 更新统计信息
*/

-- ================================================================
-- 预期改善效果
-- ================================================================
/*
✓ DELETE 执行时间: 从秒级 -> 毫秒级
✓ 锁等待时间: 显著减少
✓ 死锁发生频率: 大幅降低或消除
✓ 数据库 CPU 和 IO: 明显下降
*/
