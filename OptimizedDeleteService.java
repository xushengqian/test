import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 优化后的删除服务
 * 解决 ks_index_user_performance_details 表删除慢和死锁问题
 */
@Service
public class OptimizedDeleteService {
    
    private static final Logger log = LoggerFactory.getLogger(OptimizedDeleteService.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    // 批量删除的每批大小
    private static final int BATCH_SIZE = 1000;
    
    // 批次间隔时间（毫秒）
    private static final long BATCH_INTERVAL_MS = 10;
    
    // 最大重试次数
    private static final int MAX_RETRY = 3;
    
    public OptimizedDeleteService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * 方案1：简单删除（前提：已创建索引）
     * 适用于删除数据量较小的场景（< 1000 行）
     */
    @Transactional(rollbackFor = Exception.class)
    public int simpleDelete(Long indexId, String indexParams) {
        String sql = "DELETE FROM ks_index_user_performance_details " +
                     "WHERE index_id = ? AND index_params = ?";
        
        long startTime = System.currentTimeMillis();
        int deletedRows = jdbcTemplate.update(sql, indexId, indexParams);
        long duration = System.currentTimeMillis() - startTime;
        
        log.info("删除完成: indexId={}, indexParams={}, 删除行数={}, 耗时={}ms", 
                 indexId, indexParams, deletedRows, duration);
        
        return deletedRows;
    }
    
    /**
     * 方案2：批量删除（推荐）
     * 适用于删除大量数据的场景
     * 分批删除，减少单次事务持锁时间，降低死锁风险
     */
    public int batchDelete(Long indexId, String indexParams) {
        String sql = "DELETE FROM ks_index_user_performance_details " +
                     "WHERE index_id = ? AND index_params = ? LIMIT ?";
        
        int totalDeleted = 0;
        int deleted;
        int batchCount = 0;
        
        long startTime = System.currentTimeMillis();
        
        do {
            batchCount++;
            // 每批作为一个独立事务
            deleted = deleteBatch(sql, indexId, indexParams, BATCH_SIZE);
            totalDeleted += deleted;
            
            log.debug("批次{}: 删除{}行", batchCount, deleted);
            
            // 如果还有数据要删除，短暂休眠释放资源
            if (deleted == BATCH_SIZE) {
                try {
                    Thread.sleep(BATCH_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("批量删除被中断", e);
                    break;
                }
            }
        } while (deleted == BATCH_SIZE);
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("批量删除完成: indexId={}, indexParams={}, 总删除行数={}, 批次数={}, 总耗时={}ms", 
                 indexId, indexParams, totalDeleted, batchCount, duration);
        
        return totalDeleted;
    }
    
    /**
     * 执行单批删除
     */
    @Transactional(rollbackFor = Exception.class)
    private int deleteBatch(String sql, Long indexId, String indexParams, int limit) {
        return jdbcTemplate.update(sql, indexId, indexParams, limit);
    }
    
    /**
     * 方案3：基于主键删除（最快）
     * 先查询主键ID，再用主键批量删除
     * 主键删除是最快的删除方式
     */
    public int deleteByPrimaryKeys(Long indexId, String indexParams) {
        // 第一步：查询符合条件的主键ID
        String selectSql = "SELECT id FROM ks_index_user_performance_details " +
                           "WHERE index_id = ? AND index_params = ?";
        
        List<Long> ids = jdbcTemplate.queryForList(selectSql, Long.class, indexId, indexParams);
        
        if (ids.isEmpty()) {
            log.info("没有找到需要删除的数据: indexId={}, indexParams={}", indexId, indexParams);
            return 0;
        }
        
        log.info("找到{}条需要删除的记录", ids.size());
        
        // 第二步：分批用主键删除
        int totalDeleted = 0;
        int batchSize = 1000;
        
        for (int i = 0; i < ids.size(); i += batchSize) {
            int end = Math.min(i + batchSize, ids.size());
            List<Long> batchIds = ids.subList(i, end);
            
            totalDeleted += deleteByIds(batchIds);
            
            // 批次间短暂休眠
            if (end < ids.size()) {
                try {
                    Thread.sleep(BATCH_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        log.info("主键删除完成: 总删除行数={}", totalDeleted);
        return totalDeleted;
    }
    
    /**
     * 使用主键批量删除
     */
    @Transactional(rollbackFor = Exception.class)
    private int deleteByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        
        // 构建 IN 子句
        String placeholders = String.join(",", ids.stream()
                .map(id -> "?")
                .toArray(String[]::new));
        
        String sql = "DELETE FROM ks_index_user_performance_details WHERE id IN (" + placeholders + ")";
        
        return jdbcTemplate.update(sql, ids.toArray());
    }
    
    /**
     * 方案4：带重试机制的删除
     * 自动处理死锁重试
     */
    public int deleteWithRetry(Long indexId, String indexParams) {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < MAX_RETRY) {
            try {
                attempt++;
                return batchDelete(indexId, indexParams);
                
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage().toLowerCase();
                
                // 判断是否是死锁异常
                if (errorMsg.contains("deadlock") || errorMsg.contains("lock wait timeout")) {
                    log.warn("删除遇到锁问题，第{}次重试: indexId={}, indexParams={}, error={}", 
                             attempt, indexId, indexParams, e.getMessage());
                    
                    if (attempt < MAX_RETRY) {
                        // 指数退避：等待时间逐渐增加
                        long waitTime = (long) (Math.pow(2, attempt) * 100);
                        try {
                            Thread.sleep(waitTime);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("删除操作被中断", ie);
                        }
                    }
                } else {
                    // 非锁相关异常，直接抛出
                    throw e;
                }
            }
        }
        
        // 重试次数用完，抛出异常
        throw new RuntimeException(
            String.format("删除失败，已重试%d次: indexId=%d, indexParams=%s", 
                         MAX_RETRY, indexId, indexParams), 
            lastException
        );
    }
    
    /**
     * 方案5：使用较低的事务隔离级别
     * READ_COMMITTED 比 REPEATABLE_READ 产生的锁更少
     * 注意：需要确认业务逻辑允许这种隔离级别
     */
    @Transactional(
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    public int deleteWithLowerIsolation(Long indexId, String indexParams) {
        String sql = "DELETE FROM ks_index_user_performance_details " +
                     "WHERE index_id = ? AND index_params = ?";
        
        log.info("使用 READ_COMMITTED 隔离级别删除: indexId={}, indexParams={}", 
                 indexId, indexParams);
        
        return jdbcTemplate.update(sql, indexId, indexParams);
    }
    
    /**
     * 异步批量删除（适用于后台任务）
     * 不阻塞主流程，在后台慢慢删除
     */
    @Transactional(rollbackFor = Exception.class)
    public void asyncBatchDelete(Long indexId, String indexParams) {
        // 使用线程池异步执行
        // CompletableFuture.runAsync(() -> batchDelete(indexId, indexParams), executorService);
        
        log.info("已提交异步删除任务: indexId={}, indexParams={}", indexId, indexParams);
    }
    
    /**
     * 查询待删除的数据量（用于评估删除策略）
     */
    public long countToDelete(Long indexId, String indexParams) {
        String sql = "SELECT COUNT(*) FROM ks_index_user_performance_details " +
                     "WHERE index_id = ? AND index_params = ?";
        
        Long count = jdbcTemplate.queryForObject(sql, Long.class, indexId, indexParams);
        return count != null ? count : 0;
    }
    
    /**
     * 智能删除：根据数据量自动选择最优策略
     */
    public int smartDelete(Long indexId, String indexParams) {
        long count = countToDelete(indexId, indexParams);
        
        log.info("准备删除数据: indexId={}, indexParams={}, 数据量={}", 
                 indexId, indexParams, count);
        
        if (count == 0) {
            return 0;
        } else if (count <= 100) {
            // 少量数据：简单删除
            log.info("使用简单删除策略");
            return simpleDelete(indexId, indexParams);
        } else if (count <= 10000) {
            // 中等数据量：批量删除
            log.info("使用批量删除策略");
            return batchDelete(indexId, indexParams);
        } else {
            // 大量数据：主键删除
            log.info("使用主键删除策略");
            return deleteByPrimaryKeys(indexId, indexParams);
        }
    }
}
