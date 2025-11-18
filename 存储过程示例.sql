-- ============================================
-- 删除操作的存储过程（可选方案）
-- ============================================

DELIMITER $$

-- 存储过程：分批删除，避免长时间锁表
CREATE PROCEDURE sp_delete_performance_details(
    IN p_index_id BIGINT,
    IN p_index_params VARCHAR(255),
    IN p_batch_size INT
)
BEGIN
    DECLARE v_deleted_count INT DEFAULT 1;
    DECLARE v_total_deleted INT DEFAULT 0;
    
    -- 循环删除，直到没有数据可删除
    WHILE v_deleted_count > 0 DO
        -- 分批删除
        DELETE FROM ks_index_user_performance_details
        WHERE index_id = p_index_id 
          AND index_params = p_index_params
        LIMIT p_batch_size;
        
        -- 获取删除的行数
        SET v_deleted_count = ROW_COUNT();
        SET v_total_deleted = v_total_deleted + v_deleted_count;
        
        -- 短暂延迟，释放锁
        SELECT SLEEP(0.05);
    END WHILE;
    
    -- 返回删除的总数
    SELECT v_total_deleted AS total_deleted;
END$$

DELIMITER ;

-- 使用示例
-- CALL sp_delete_performance_details(123, 'param_value', 1000);
