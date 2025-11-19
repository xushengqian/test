-- ============================================
-- MySQL 锁等待超时 - 快速诊断脚本
-- ============================================
-- 执行此脚本可以快速定位锁等待超时问题的原因
-- ============================================

-- 1. 检查当前锁等待超时设置
SELECT 
    '当前锁等待超时设置' AS description,
    @@innodb_lock_wait_timeout AS value,
    '秒' AS unit;

-- 2. 查看所有正在运行的事务
SELECT 
    '正在运行的事务' AS description,
    trx_id,
    trx_state,
    trx_started,
    trx_mysql_thread_id AS thread_id,
    TIMESTAMPDIFF(SECOND, trx_started, NOW()) AS duration_seconds,
    LEFT(trx_query, 100) AS query_preview
FROM information_schema.innodb_trx
ORDER BY trx_started;

-- 3. 查看锁等待情况（关键！）
SELECT 
    '锁等待情况' AS description,
    r.trx_id AS waiting_trx_id,
    r.trx_mysql_thread_id AS waiting_thread_id,
    b.trx_id AS blocking_trx_id,
    b.trx_mysql_thread_id AS blocking_thread_id,
    TIMESTAMPDIFF(SECOND, r.trx_wait_started, NOW()) AS wait_seconds,
    LEFT(r.trx_query, 100) AS waiting_query,
    LEFT(b.trx_query, 100) AS blocking_query
FROM information_schema.innodb_lock_waits w
INNER JOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_trx_id
INNER JOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_trx_id;

-- 4. 查找长时间运行的事务（超过 30 秒）
SELECT 
    '长时间运行的事务（>30秒）' AS description,
    trx_id,
    trx_mysql_thread_id AS thread_id,
    trx_started,
    TIMESTAMPDIFF(SECOND, trx_started, NOW()) AS duration_seconds,
    trx_state,
    LEFT(trx_query, 200) AS query_preview
FROM information_schema.innodb_trx
WHERE TIMESTAMPDIFF(SECOND, trx_started, NOW()) > 30
ORDER BY trx_started;

-- 5. 检查 ks_index_user_performance 表的索引
SELECT 
    '表索引信息' AS description,
    TABLE_NAME,
    INDEX_NAME,
    COLUMN_NAME,
    SEQ_IN_INDEX,
    NON_UNIQUE,
    INDEX_TYPE
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ks_index_user_performance'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- 6. 检查表的状态
SELECT 
    '表状态' AS description,
    TABLE_NAME,
    TABLE_ROWS,
    DATA_LENGTH,
    INDEX_LENGTH,
    DATA_FREE,
    AUTO_INCREMENT,
    CREATE_TIME,
    UPDATE_TIME
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'ks_index_user_performance';

-- 7. 查看当前连接和线程状态
SELECT 
    '当前连接状态' AS description,
    ID,
    USER,
    HOST,
    DB,
    COMMAND,
    TIME,
    STATE,
    LEFT(INFO, 100) AS query_preview
FROM information_schema.PROCESSLIST
WHERE COMMAND != 'Sleep'
ORDER BY TIME DESC;

-- ============================================
-- 紧急处理：如果需要终止阻塞的事务
-- ============================================
-- 注意：执行前请确认业务影响！
-- 
-- 步骤 1: 找到阻塞的线程 ID
-- SELECT trx_mysql_thread_id 
-- FROM information_schema.innodb_trx 
-- WHERE trx_id = '<blocking_trx_id>';
--
-- 步骤 2: 终止线程（替换 <thread_id>）
-- KILL <thread_id>;
-- ============================================
