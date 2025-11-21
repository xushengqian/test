package org.springblade.modules.index.service;

import lombok.extern.slf4j.Slf4j;
import org.springblade.modules.index.mapper.IndexCustomSqlMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 索引自定义SQL服务类
 * 包含死锁重试机制
 */
@Slf4j
@Service
public class IndexCustomSqlService {
    
    @Autowired
    private IndexCustomSqlMapper indexCustomSqlMapper;
    
    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_TIMES = 3;
    
    /**
     * 重试间隔（毫秒）
     */
    private static final long RETRY_INTERVAL_MS = 100;
    
    /**
     * 更新自定义SQL（带死锁重试机制）
     * 
     * @param indexId 索引ID
     * @param indexParams 索引参数
     * @param limit 删除数量限制
     * @return 删除的行数
     */
    public int updateCustomSqlWithRetry(Long indexId, String indexParams, Integer limit) {
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount < MAX_RETRY_TIMES) {
            try {
                // 使用REQUIRES_NEW传播级别，确保每次重试都是新事务
                return updateCustomSqlInternal(indexId, indexParams, limit);
            } catch (DeadlockLoserDataAccessException e) {
                lastException = e;
                retryCount++;
                log.warn("检测到死锁，准备重试。重试次数: {}/{}", retryCount, MAX_RETRY_TIMES);
                
                if (retryCount < MAX_RETRY_TIMES) {
                    try {
                        // 等待一段时间后重试，避免立即重试导致再次死锁
                        Thread.sleep(RETRY_INTERVAL_MS * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试过程被中断", ie);
                    }
                }
            } catch (Exception e) {
                // 非死锁异常直接抛出
                log.error("执行删除操作时发生异常", e);
                throw e;
            }
        }
        
        // 重试次数用尽，抛出最后一次的异常
        log.error("重试{}次后仍然失败", MAX_RETRY_TIMES);
        throw new RuntimeException("删除操作失败，已重试" + MAX_RETRY_TIMES + "次", lastException);
    }
    
    /**
     * 内部删除方法（使用新事务）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int updateCustomSqlInternal(Long indexId, String indexParams, Integer limit) {
        return indexCustomSqlMapper.updateCustomSql(indexId, indexParams, limit);
    }
    
    /**
     * 分批删除（推荐用于大数据量场景）
     * 
     * @param indexId 索引ID
     * @param indexParams 索引参数
     * @param batchSize 每批删除数量
     * @param maxBatches 最大批次数（防止无限循环）
     * @return 总共删除的行数
     */
    public int updateCustomSqlInBatches(Long indexId, String indexParams, 
                                       Integer batchSize, Integer maxBatches) {
        int totalDeleted = 0;
        int batchCount = 0;
        
        while (batchCount < maxBatches) {
            int deleted = updateCustomSqlWithRetry(indexId, indexParams, batchSize);
            totalDeleted += deleted;
            
            // 如果本次删除的行数小于批次大小，说明已经删除完毕
            if (deleted < batchSize) {
                break;
            }
            
            batchCount++;
            
            // 批次之间短暂休眠，减少锁竞争
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log.info("分批删除完成，共删除{}条记录，分{}批执行", totalDeleted, batchCount);
        return totalDeleted;
    }
}
