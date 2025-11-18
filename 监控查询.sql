-- ============================================
-- 性能监控和诊断查询
-- ============================================

-- 1. 查看表的索引信息
SHOW INDEX FROM ks_index_user_performance_details;

-- 2. 查看表的统计信息
SELECT 
    TABLE_NAME,
    TABLE_ROWS,
    DATA_LENGTH / 1024 / 1024 AS 'Data Size (MB)',
    INDEX_LENGTH / 1024 / 1024 AS 'Index Size (MB)',
    (DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024 AS 'Total Size (MB)'
FROM 
    information_schema.TABLES
WHERE 
    TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ks_index_user_performance_details';

-- 3. 查看 DELETE 语句的执行计划
EXPLAIN DELETE FROM ks_index_user_performance_details 
WHERE index_id = 123 AND index_params = 'test';

-- 4. 查看慢查询日志（需要开启慢查询日志）
SELECT 
    start_time,
    query_time,
    lock_time,
    rows_examined,
    rows_sent,
    sql_text
FROM 
    mysql.slow_log
WHERE 
    sql_text LIKE '%ks_index_user_performance_details%'
    AND sql_text LIKE '%DELETE%'
ORDER BY 
    start_time DESC
LIMIT 10;

-- 5. 查看当前锁等待情况
SHOW ENGINE INNODB STATUS\G

-- 6. 查看当前正在执行的查询
SHOW PROCESSLIST;

-- 7. 查看死锁信息
SELECT 
    *
FROM 
    information_schema.INNODB_LOCKS;

SELECT 
    *
FROM 
    information_schema.INNODB_LOCK_WAITS;

-- 8. 查看索引使用统计（MySQL 5.7+）
SELECT 
    OBJECT_SCHEMA,
    OBJECT_NAME,
    INDEX_NAME,
    COUNT_FETCH,
    COUNT_INSERT,
    COUNT_UPDATE,
    COUNT_DELETE
FROM 
    performance_schema.table_io_waits_summary_by_index_usage
WHERE 
    OBJECT_SCHEMA = DATABASE()
    AND OBJECT_NAME = 'ks_index_user_performance_details'
ORDER BY 
    COUNT_FETCH DESC;

-- 9. 检查索引选择性（帮助判断索引是否有效）
SELECT 
    COUNT(DISTINCT index_id) AS distinct_index_id,
    COUNT(DISTINCT index_params) AS distinct_index_params,
    COUNT(DISTINCT CONCAT(index_id, '_', index_params)) AS distinct_combination,
    COUNT(*) AS total_rows,
    COUNT(DISTINCT CONCAT(index_id, '_', index_params)) / COUNT(*) AS selectivity
FROM 
    ks_index_user_performance_details;

-- 10. 查看表碎片情况
SELECT 
    TABLE_NAME,
    ROUND(((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024), 2) AS 'Size (MB)',
    ROUND((DATA_FREE / 1024 / 1024), 2) AS 'Free Space (MB)',
    ROUND((DATA_FREE / (DATA_LENGTH + INDEX_LENGTH + DATA_FREE)) * 100, 2) AS 'Fragmentation %'
FROM 
    information_schema.TABLES
WHERE 
    TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'ks_index_user_performance_details'
    AND DATA_FREE > 0;
