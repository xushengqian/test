package com.example.optimization;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DELETE 操作优化示例
 * 解决 ks_index_user_performance_details 表删除慢和死锁问题
 */
@Service
public class PerformanceDetailsDeleteService {

    private final JdbcTemplate jdbcTemplate;
    
    // 批次大小，可根据实际情况调整
    private static final int BATCH_SIZE = 1000;
    
    public PerformanceDetailsDeleteService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 方案1：分批删除（推荐）
     * 优点：避免长时间持有锁，减少死锁概率
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteInBatches(Long indexId, String indexParams) {
        int totalDeleted = 0;
        int deletedCount;
        
        do {
            // 使用 LIMIT 分批删除
            deletedCount = jdbcTemplate.update(
                "DELETE FROM ks_index_user_performance_details " +
                "WHERE index_id = ? AND index_params = ? " +
                "LIMIT ?",
                indexId, indexParams, BATCH_SIZE
            );
            
            totalDeleted += deletedCount;
            
            // 每批删除后提交事务，释放锁
            // 注意：如果使用 @Transactional，需要手动提交
            // 或者使用编程式事务管理
            
            // 短暂延迟，避免过度占用数据库资源
            if (deletedCount > 0) {
                try {
                    Thread.sleep(50); // 50ms 延迟
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } while (deletedCount > 0);
        
        return totalDeleted;
    }

    /**
     * 方案2：先查询ID再删除（适用于需要记录删除的数据）
     * 优点：可以记录删除的数据，事务更短
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int deleteByIds(Long indexId, String indexParams) {
        // 先查询要删除的记录ID（使用索引，速度快）
        List<Long> ids = jdbcTemplate.queryForList(
            "SELECT id FROM ks_index_user_performance_details " +
            "WHERE index_id = ? AND index_params = ?",
            Long.class, indexId, indexParams
        );
        
        if (ids.isEmpty()) {
            return 0;
        }
        
        // 分批删除ID列表
        int totalDeleted = 0;
        for (int i = 0; i < ids.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, ids.size());
            List<Long> batchIds = ids.subList(i, end);
            
            // 使用 IN 子句批量删除
            String placeholders = String.join(",", 
                batchIds.stream().map(id -> "?").toArray(String[]::new));
            
            int deleted = jdbcTemplate.update(
                "DELETE FROM ks_index_user_performance_details " +
                "WHERE id IN (" + placeholders + ")",
                batchIds.toArray()
            );
            
            totalDeleted += deleted;
            
            // 短暂延迟
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        return totalDeleted;
    }

    /**
     * 方案3：使用软删除（如果业务允许）
     * 优点：性能最好，不会锁表，可以恢复数据
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int softDelete(Long indexId, String indexParams) {
        return jdbcTemplate.update(
            "UPDATE ks_index_user_performance_details " +
            "SET deleted = 1, deleted_time = NOW() " +
            "WHERE index_id = ? AND index_params = ? AND deleted = 0",
            indexId, indexParams
        );
    }

    /**
     * 方案4：使用存储过程（MySQL）
     * 在数据库中创建存储过程，减少网络往返
     */
    public int deleteUsingStoredProcedure(Long indexId, String indexParams) {
        return jdbcTemplate.update(
            "CALL sp_delete_performance_details(?, ?)",
            indexId, indexParams
        );
    }
}
