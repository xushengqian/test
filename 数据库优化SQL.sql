-- ============================================
-- 死锁问题优化SQL脚本
-- ============================================

-- 1. 检查当前索引情况
SHOW INDEX FROM ks_index_user_performance_details;

-- 2. 创建/优化索引（根据实际查询模式选择）

-- 方案A：创建复合索引（推荐，如果经常同时使用这两个字段查询）
CREATE INDEX idx_index_id_params 
ON ks_index_user_performance_details(index_id, index_params);

-- 方案B：如果两个字段独立查询较多，创建单独索引
CREATE INDEX idx_index_id 
ON ks_index_user_performance_details(index_id);

CREATE INDEX idx_index_params 
ON ks_index_user_performance_details(index_params);

-- 3. 分析表，更新统计信息
ANALYZE TABLE ks_index_user_performance_details;

-- 4. 检查表锁情况（用于诊断）
SHOW PROCESSLIST;
SHOW ENGINE INNODB STATUS;

-- 5. 查看死锁日志（MySQL 5.6+）
-- 在my.cnf中设置：
-- innodb_print_all_deadlocks = ON
-- 然后查看错误日志

-- 6. 优化建议：
-- - 确保事务尽可能短
-- - 避免在事务中进行耗时操作
-- - 考虑使用READ COMMITTED隔离级别（如果业务允许）
-- - 对于大批量删除，使用分批删除策略
