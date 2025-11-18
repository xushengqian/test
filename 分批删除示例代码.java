package com.example.service;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ks_index_user_performance_details 删除服务
 * 优化方案：分批删除 + 死锁重试
 */
@Service
public class UserPerformanceDetailsService {

    @Autowired
    private UserPerformanceDetailsMapper mapper;

    /**
     * 分批删除，避免长时间锁定和死锁
     * 
     * @param indexId 索引ID
     * @param indexParams 索引参数
     * @return 删除的总记录数
     */
    @Transactional
    public int deleteInBatches(Long indexId, String indexParams) {
        int batchSize = 1000; // 每批删除1000条
        int totalDeleted = 0;
        int deletedCount;

        do {
            try {
                deletedCount = mapper.deleteBatch(indexId, indexParams, batchSize);
                totalDeleted += deletedCount;

                // 如果还有数据，短暂休眠释放锁
                if (deletedCount > 0) {
                    Thread.sleep(10); // 10毫秒
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("删除操作被中断", e);
            }
        } while (deletedCount > 0);

        return totalDeleted;
    }

    /**
     * 带重试的删除操作（处理死锁）
     * 
     * @param indexId 索引ID
     * @param indexParams 索引参数
     * @return 删除的总记录数
     */
    @Retryable(
        value = {DeadlockLoserDataAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional
    public int deleteWithRetry(Long indexId, String indexParams) {
        return deleteInBatches(indexId, indexParams);
    }

    /**
     * 手动重试实现（不使用Spring Retry时）
     */
    public int deleteWithManualRetry(Long indexId, String indexParams) {
        int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                return deleteInBatches(indexId, indexParams);
            } catch (DeadlockLoserDataAccessException e) {
                attempt++;
                if (attempt >= maxRetries) {
                    throw new RuntimeException("删除操作失败，已达到最大重试次数", e);
                }
                
                // 指数退避
                try {
                    Thread.sleep(100 * (long) Math.pow(2, attempt - 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试等待被中断", ie);
                }
            }
        }

        return 0;
    }
}

/**
 * MyBatis Mapper接口
 */
interface UserPerformanceDetailsMapper {
    
    /**
     * 分批删除
     * 注意：MySQL的DELETE语句使用LIMIT时，需要确保有合适的索引
     */
    @Delete("DELETE FROM ks_index_user_performance_details " +
            "WHERE index_id = #{indexId} AND index_params = #{indexParams} " +
            "LIMIT #{batchSize}")
    int deleteBatch(@Param("indexId") Long indexId, 
                    @Param("indexParams") String indexParams, 
                    @Param("batchSize") int batchSize);
    
    /**
     * 查询待删除的记录数（用于预估）
     */
    @Select("SELECT COUNT(*) FROM ks_index_user_performance_details " +
            "WHERE index_id = #{indexId} AND index_params = #{indexParams}")
    int countToDelete(@Param("indexId") Long indexId, 
                      @Param("indexParams") String indexParams);
}
