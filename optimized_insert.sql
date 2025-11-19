-- 优化后的插入/更新SQL示例

-- ============================================
-- 方案1：带事务控制和超时设置
-- ============================================

START TRANSACTION;

-- 设置会话级别的锁等待超时（60秒）
SET SESSION innodb_lock_wait_timeout = 60;

-- 执行插入/更新
INSERT INTO ks_index_user_performance (month, user_id, index_params, kfzj_deal_amount) 
VALUES ('2025-12', '202508118005', '2025-12@202508118005', '3884400.00') 
ON DUPLICATE KEY UPDATE kfzj_deal_amount = '3884400.00';

COMMIT;

-- ============================================
-- 方案2：先检查后操作（减少锁竞争）
-- ============================================

-- 注意：这种方法在高并发下仍可能有问题，但可以减少部分锁竞争
START TRANSACTION;

SELECT COUNT(*) INTO @exists 
FROM ks_index_user_performance 
WHERE index_params = '2025-12@202508118005';

IF @exists > 0 THEN
    UPDATE ks_index_user_performance 
    SET kfzj_deal_amount = '3884400.00'
    WHERE index_params = '2025-12@202508118005';
ELSE
    INSERT INTO ks_index_user_performance (month, user_id, index_params, kfzj_deal_amount) 
    VALUES ('2025-12', '202508118005', '2025-12@202508118005', '3884400.00');
END IF;

COMMIT;

-- ============================================
-- 方案3：使用REPLACE INTO（注意：会删除后重新插入）
-- ============================================

-- ⚠️ 警告：REPLACE INTO 会先删除旧记录再插入，可能影响自增ID和外键
-- 仅在确认可以接受此行为时使用

REPLACE INTO ks_index_user_performance (month, user_id, index_params, kfzj_deal_amount) 
VALUES ('2025-12', '202508118005', '2025-12@202508118005', '3884400.00');

-- ============================================
-- 方案4：批量操作优化
-- ============================================

-- 如果有多条记录需要插入/更新，使用批量操作
INSERT INTO ks_index_user_performance (month, user_id, index_params, kfzj_deal_amount) 
VALUES 
    ('2025-12', '202508118005', '2025-12@202508118005', '3884400.00'),
    ('2025-12', '202508118006', '2025-12@202508118006', '3884401.00'),
    ('2025-12', '202508118007', '2025-12@202508118007', '3884402.00')
ON DUPLICATE KEY UPDATE 
    kfzj_deal_amount = VALUES(kfzj_deal_amount);

-- ============================================
-- 方案5：使用存储过程（减少网络往返）
-- ============================================

DELIMITER //

CREATE PROCEDURE upsert_user_performance(
    IN p_month VARCHAR(7),
    IN p_user_id VARCHAR(20),
    IN p_index_params VARCHAR(50),
    IN p_kfzj_deal_amount DECIMAL(15,2)
)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    
    START TRANSACTION;
    
    INSERT INTO ks_index_user_performance (month, user_id, index_params, kfzj_deal_amount) 
    VALUES (p_month, p_user_id, p_index_params, p_kfzj_deal_amount) 
    ON DUPLICATE KEY UPDATE kfzj_deal_amount = p_kfzj_deal_amount;
    
    COMMIT;
END //

DELIMITER ;

-- 调用存储过程
-- CALL upsert_user_performance('2025-12', '202508118005', '2025-12@202508118005', '3884400.00');
