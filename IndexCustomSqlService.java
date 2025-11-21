package org.springblade.modules.index.service;

import lombok.extern.slf4j.Slf4j;
import org.springblade.modules.index.mapper.IndexCustomSqlMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 索引自定义SQL服务
 * 包含死锁重试机制
 */
@Slf4j
@Service
public class IndexCustomSqlService {

    @Autowired
    private IndexCustomSqlMapper indexCustomSqlMapper;

    // 默认重试配置
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100; // 初始延迟100ms
    private static final int DEFAULT_BATCH_SIZE = 100; // 默认批次大小

    /**
     * 方法1：使用Spring Retry注解（需要添加spring-retry依赖）
     * 自动重试死锁异常
     */
    @Retryable(
        value = {DeadlockLoserDataAccessException.class},
        maxAttempts = MAX_RETRY_ATTEMPTS,
        backoff = @Backoff(delay = RETRY_DELAY_MS, multiplier = 2)
    )
    @Transactional(rollbackFor = Exception.class)
    public int updateCustomSqlWithRetry(Long indexId, String indexParams, Integer limit) {
        log.info("执行删除操作: indexId={}, indexParams={}, limit={}", indexId, indexParams, limit);
        return indexCustomSqlMapper.updateCustomSql(indexId, indexParams, limit);
    }

    /**
     * 方法2：手动重试机制（推荐，不依赖额外依赖）
     * 更灵活，可以自定义重试逻辑
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateCustomSqlWithManualRetry(Long indexId, String indexParams, Integer limit) {
        int attempts = 0;
        long delay = RETRY_DELAY_MS;
        
        while (attempts < MAX_RETRY_ATTEMPTS) {
            try {
                log.info("尝试删除操作 (第{}次): indexId={}, indexParams={}, limit={}", 
                    attempts + 1, indexId, indexParams, limit);
                
                int deletedRows = indexCustomSqlMapper.updateCustomSql(indexId, indexParams, limit);
                log.info("删除成功，删除行数: {}", deletedRows);
                return deletedRows;
                
            } catch (DeadlockLoserDataAccessException e) {
                attempts++;
                if (attempts >= MAX_RETRY_ATTEMPTS) {
                    log.error("删除操作失败，已达到最大重试次数: {}", attempts, e);
                    throw e;
                }
                
                log.warn("检测到死锁，等待{}ms后重试 (第{}次/共{}次)", delay, attempts, MAX_RETRY_ATTEMPTS);
                try {
                    Thread.sleep(delay);
                    delay *= 2; // 指数退避
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试等待被中断", ie);
                }
            }
        }
        
        throw new RuntimeException("删除操作失败，已重试" + MAX_RETRY_ATTEMPTS + "次");
    }

    /**
     * 方法3：小批量删除（推荐用于大数据量删除）
     * 通过减少单次删除数量来降低死锁风险
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteInBatches(Long indexId, String indexParams, Integer totalLimit) {
        int batchSize = DEFAULT_BATCH_SIZE;
        int totalDeleted = 0;
        int deletedInBatch;
        
        log.info("开始批量删除: indexId={}, indexParams={}, 目标删除={}", 
            indexId, indexParams, totalLimit);
        
        do {
            deletedInBatch = updateCustomSqlWithManualRetry(indexId, indexParams, batchSize);
            totalDeleted += deletedInBatch;
            
            log.info("批次删除完成，本次删除: {}, 累计删除: {}", deletedInBatch, totalDeleted);
            
            // 如果已达到目标数量或本次删除为0，停止
            if (totalDeleted >= totalLimit || deletedInBatch == 0) {
                break;
            }
            
            // 如果接近目标，调整批次大小
            if (totalDeleted + batchSize > totalLimit) {
                batchSize = totalLimit - totalDeleted;
            }
            
        } while (totalDeleted < totalLimit && deletedInBatch > 0);
        
        log.info("批量删除完成，总删除行数: {}", totalDeleted);
        return totalDeleted;
    }

    /**
     * 方法4：带超时的删除操作
     * 如果操作时间过长，自动终止
     */
    @Transactional(rollbackFor = Exception.class, timeout = 30) // 30秒超时
    public int updateCustomSqlWithTimeout(Long indexId, String indexParams, Integer limit) {
        return updateCustomSqlWithManualRetry(indexId, indexParams, limit);
    }
}
