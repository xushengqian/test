package com.example.optimization;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 分批删除完整示例
 * 包含多种实现方式和最佳实践
 */
@Service
public class BatchDeleteCompleteExample {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    
    // 批次大小配置
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int DELAY_MS = 50; // 延迟时间（毫秒）
    
    public BatchDeleteCompleteExample(JdbcTemplate jdbcTemplate, 
                                      TransactionTemplate transactionTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    // ==================== 方式1：使用 LIMIT + 独立事务（推荐） ====================
    
    /**
     * 推荐方式：每批使用独立事务，自动提交
     */
    public int deleteInBatches(Long indexId, String indexParams) {
        return deleteInBatches(indexId, indexParams, DEFAULT_BATCH_SIZE);
    }
    
    public int deleteInBatches(Long indexId, String indexParams, int batchSize) {
        int totalDeleted = 0;
        int deletedCount;
        
        do {
            // 每次调用 deleteOneBatch 都会创建新事务并自动提交
            deletedCount = deleteOneBatch(indexId, indexParams, batchSize);
            totalDeleted += deletedCount;
            
            // 短暂延迟，释放锁
            if (deletedCount > 0) {
                sleep(DELAY_MS);
            }
        } while (deletedCount > 0);
        
        return totalDeleted;
    }
    
    /**
     * 单批删除，使用 REQUIRES_NEW 确保每次都是新事务
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private int deleteOneBatch(Long indexId, String indexParams, int batchSize) {
        return jdbcTemplate.update(
            "DELETE FROM ks_index_user_performance_details " +
            "WHERE index_id = ? AND index_params = ? " +
            "LIMIT ?",
            indexId, indexParams, batchSize
        );
    }

    // ==================== 方式2：使用编程式事务管理 ====================
    
    /**
     * 使用 TransactionTemplate 手动控制事务
     * 优点：更灵活，可以精确控制事务边界
     */
    public int deleteInBatchesWithTransactionTemplate(Long indexId, String indexParams, int batchSize) {
        int totalDeleted = 0;
        int deletedCount;
        
        do {
            // 每次循环创建新事务
            deletedCount = transactionTemplate.execute(status -> {
                return jdbcTemplate.update(
                    "DELETE FROM ks_index_user_performance_details " +
                    "WHERE index_id = ? AND index_params = ? " +
                    "LIMIT ?",
                    indexId, indexParams, batchSize
                );
            });
            
            totalDeleted += deletedCount;
            
            if (deletedCount > 0) {
                sleep(DELAY_MS);
            }
        } while (deletedCount > 0);
        
        return totalDeleted;
    }

    // ==================== 方式3：先查询ID再分批删除 ====================
    
    /**
     * 先查询所有ID，再分批删除
     * 优点：可以记录删除的数据，事务更短
     * 缺点：需要两次数据库操作，内存占用较大
     */
    public int deleteByIdsInBatches(Long indexId, String indexParams, int batchSize) {
        // 第一步：查询所有要删除的ID（使用索引，速度快）
        List<Long> ids = jdbcTemplate.queryForList(
            "SELECT id FROM ks_index_user_performance_details " +
            "WHERE index_id = ? AND index_params = ?",
            Long.class, indexId, indexParams
        );
        
        if (ids.isEmpty()) {
            return 0;
        }
        
        int totalDeleted = 0;
        
        // 第二步：分批删除ID
        for (int i = 0; i < ids.size(); i += batchSize) {
            int end = Math.min(i + batchSize, ids.size());
            List<Long> batchIds = ids.subList(i, end);
            
            int deleted = deleteBatchByIds(batchIds);
            totalDeleted += deleted;
            
            sleep(DELAY_MS);
        }
        
        return totalDeleted;
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private int deleteBatchByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        
        String placeholders = ids.stream()
            .map(id -> "?")
            .collect(Collectors.joining(","));
        
        return jdbcTemplate.update(
            "DELETE FROM ks_index_user_performance_details " +
            "WHERE id IN (" + placeholders + ")",
            ids.toArray()
        );
    }

    // ==================== 方式4：带进度回调 ====================
    
    /**
     * 分批删除，带进度回调
     */
    public int deleteInBatchesWithProgress(
            Long indexId, 
            String indexParams, 
            int batchSize,
            ProgressCallback callback) {
        
        int totalDeleted = 0;
        int deletedCount;
        int batchNumber = 0;
        
        do {
            batchNumber++;
            deletedCount = deleteOneBatch(indexId, indexParams, batchSize);
            totalDeleted += deletedCount;
            
            // 回调进度
            if (callback != null) {
                callback.onProgress(batchNumber, deletedCount, totalDeleted);
            }
            
            if (deletedCount > 0) {
                sleep(DELAY_MS);
            }
        } while (deletedCount > 0);
        
        return totalDeleted;
    }
    
    @FunctionalInterface
    public interface ProgressCallback {
        /**
         * 进度回调
         * @param batchNumber 当前批次号
         * @param deletedInBatch 本批次删除的数量
         * @param totalDeleted 累计删除的数量
         */
        void onProgress(int batchNumber, int deletedInBatch, int totalDeleted);
    }

    // ==================== 方式5：带错误处理和重试 ====================
    
    /**
     * 分批删除，带错误处理和重试机制
     */
    public int deleteWithRetry(Long indexId, String indexParams, int batchSize) {
        int totalDeleted = 0;
        int deletedCount;
        int consecutiveFailures = 0;
        final int MAX_RETRIES = 3;
        
        do {
            try {
                deletedCount = deleteOneBatch(indexId, indexParams, batchSize);
                totalDeleted += deletedCount;
                consecutiveFailures = 0; // 重置失败计数
                
                if (deletedCount > 0) {
                    sleep(DELAY_MS);
                }
            } catch (Exception e) {
                consecutiveFailures++;
                System.err.println(String.format(
                    "删除批次失败，重试次数: %d/%d, 错误: %s", 
                    consecutiveFailures, MAX_RETRIES, e.getMessage()
                ));
                
                if (consecutiveFailures >= MAX_RETRIES) {
                    throw new RuntimeException("连续失败 " + MAX_RETRIES + " 次，停止删除", e);
                }
                
                // 指数退避
                sleep((int)(100 * Math.pow(2, consecutiveFailures)));
            }
        } while (deletedCount > 0 || consecutiveFailures > 0);
        
        return totalDeleted;
    }

    // ==================== 方式6：使用游标（适用于超大数据量） ====================
    
    /**
     * 使用流式查询，避免一次性加载所有ID到内存
     * 适用于数据量非常大的场景（千万级以上）
     */
    public int deleteUsingCursor(Long indexId, String indexParams, int batchSize) {
        List<Long> batchIds = new ArrayList<>();
        int totalDeleted = 0;
        
        // 使用流式查询
        jdbcTemplate.query(
            "SELECT id FROM ks_index_user_performance_details " +
            "WHERE index_id = ? AND index_params = ?",
            new Object[]{indexId, indexParams},
            rs -> {
                while (rs.next()) {
                    batchIds.add(rs.getLong("id"));
                    
                    // 达到批次大小时，执行删除
                    if (batchIds.size() >= batchSize) {
                        int deleted = deleteBatchByIds(new ArrayList<>(batchIds));
                        totalDeleted += deleted;
                        batchIds.clear();
                        
                        sleep(DELAY_MS);
                    }
                }
            }
        );
        
        // 删除剩余的数据
        if (!batchIds.isEmpty()) {
            totalDeleted += deleteBatchByIds(batchIds);
        }
        
        return totalDeleted;
    }

    // ==================== 辅助方法 ====================
    
    /**
     * 线程安全睡眠
     */
    private void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("删除操作被中断", e);
        }
    }
    
    /**
     * 获取待删除的记录总数（用于进度显示）
     */
    public long getTotalCount(Long indexId, String indexParams) {
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM ks_index_user_performance_details " +
            "WHERE index_id = ? AND index_params = ?",
            Long.class, indexId, indexParams
        );
        return count != null ? count : 0;
    }
}
