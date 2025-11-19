-- MySQL 锁等待超时诊断脚本
-- 用于诊断和解决 INSERT ... ON DUPLICATE KEY UPDATE 的锁等待超时问题

-- ============================================
-- 1. 检查当前正在运行的事务和锁
-- ============================================

-- 查看当前所有事务
SELECT 
    trx_id,
    trx_state,
    trx_started,
    trx_requested_lock_id,
    trx_wait_started,
    trx_weight,
    trx_mysql_thread_id,
    trx_query,
    trx_tables_locked,
    trx_rows_locked,
    trx_rows_modified
FROM 
    information_schema.INNODB_TRX
ORDER BY 
    trx_started;

-- 查看当前锁等待情况
SELECT 
    r.trx_id waiting_trx_id,
    r.trx_mysql_thread_id waiting_thread,
    r.trx_query waiting_query,
    b.trx_id blocking_trx_id,
    b.trx_mysql_thread_id blocking_thread,
    b.trx_query blocking_query,
    l.lock_table,
    l.lock_index,
    l.lock_type,
    l.lock_mode
FROM 
    information_schema.INNODB_LOCK_WAITS w
    INNER JOIN information_schema.INNODB_TRX b ON b.trx_id = w.blocking_trx_id
    INNER JOIN information_schema.INNODB_TRX r ON r.trx_id = w.requesting_trx_id
    INNER JOIN information_schema.INNODB_LOCKS l ON l.lock_id = w.requested_lock_id;

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
FROM 
    information_schema.INNODB_LOCKS
ORDER BY 
    lock_trx_id;

-- ============================================
-- 2. 检查表结构和索引
-- ============================================

-- 查看表结构（需要替换表名）
-- SHOW CREATE TABLE ks_index_user_performance;

-- 查看表的索引信息
-- SHOW INDEX FROM ks_index_user_performance;

-- ============================================
-- 3. 检查长时间运行的事务
-- ============================================

-- 查看运行时间超过10秒的事务
SELECT 
    trx_id,
    trx_state,
    trx_started,
    TIMESTAMPDIFF(SECOND, trx_started, NOW()) as duration_seconds,
    trx_mysql_thread_id,
    trx_query
FROM 
    information_schema.INNODB_TRX
WHERE 
    TIMESTAMPDIFF(SECOND, trx_started, NOW()) > 10
ORDER BY 
    trx_started;

-- ============================================
-- 4. 检查进程列表
-- ============================================

-- 查看所有连接和查询
SHOW PROCESSLIST;

-- 查看特定表的锁等待
SELECT 
    OBJECT_SCHEMA,
    OBJECT_NAME,
    INDEX_NAME,
    LOCK_TYPE,
    LOCK_MODE,
    LOCK_STATUS,
    LOCK_DATA
FROM 
    performance_schema.data_locks
WHERE 
    OBJECT_NAME = 'ks_index_user_performance';

-- ============================================
-- 5. 终止阻塞的事务（谨慎使用）
-- ============================================

-- 查看需要终止的线程ID（从上面的查询结果中获取 blocking_thread）
-- KILL <thread_id>;

-- ============================================
-- 6. 检查表锁
-- ============================================

-- 查看表级锁
SELECT 
    OBJECT_SCHEMA,
    OBJECT_NAME,
    LOCK_TYPE,
    LOCK_DURATION,
    LOCK_STATUS
FROM 
    performance_schema.table_handles
WHERE 
    OBJECT_NAME = 'ks_index_user_performance';
