-- ============================================
-- ks_index_user_performance_details 表优化脚本
-- ============================================

-- 1. 检查现有索引
SHOW INDEX FROM ks_index_user_performance_details;

-- 2. 查看表结构
SHOW CREATE TABLE ks_index_user_performance_details;

-- 3. 创建联合索引（优化 DELETE 性能）
-- 注意：如果表数据量很大，建议在低峰期执行
CREATE INDEX IF NOT EXISTS idx_index_id_params 
ON ks_index_user_performance_details(index_id, index_params);

-- 4. 如果 index_params 字段很长，考虑使用前缀索引
-- CREATE INDEX idx_index_id_params_prefix 
-- ON ks_index_user_performance_details(index_id, index_params(50));

-- 5. 分析表，更新统计信息（帮助优化器选择最佳执行计划）
ANALYZE TABLE ks_index_user_performance_details;

-- 6. 查看索引使用情况（执行计划）
EXPLAIN DELETE FROM ks_index_user_performance_details 
WHERE index_id = ? AND index_params = ?;

-- 7. 检查索引大小
SELECT 
    TABLE_NAME,
    INDEX_NAME,
    ROUND(STAT_VALUE * @@innodb_page_size / 1024 / 1024, 2) AS 'Index Size (MB)'
FROM 
    mysql.innodb_index_stats
WHERE 
    TABLE_NAME = 'ks_index_user_performance_details'
    AND STAT_NAME = 'size'
ORDER BY 
    STAT_VALUE DESC;
