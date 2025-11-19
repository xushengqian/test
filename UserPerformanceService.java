package com.example.service;

import com.example.entity.UserPerformance;
import com.example.repository.UserPerformanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户绩效服务类
 * 处理 MySQL 锁等待超时问题的优化实现
 */
@Service
public class UserPerformanceService {

    @Autowired
    private UserPerformanceRepository repository;

    /**
     * 方案 1：使用重试机制处理锁等待超时
     * 当遇到锁等待超时时，自动重试最多 3 次
     */
    @Retryable(
        value = {com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    @Transactional(
        isolation = Isolation.READ_COMMITTED,  // 使用较低的隔离级别
        propagation = Propagation.REQUIRES_NEW, // 使用新事务，避免外层事务影响
        timeout = 30  // 设置事务超时时间
    )
    public void insertOrUpdateUserPerformance(String month, String userId, BigDecimal amount) {
        try {
            // 使用原生 SQL 执行 INSERT ... ON DUPLICATE KEY UPDATE
            repository.insertOrUpdate(month, userId, month + "@" + userId, amount);
        } catch (Exception e) {
            // 记录日志
            System.err.println("更新用户绩效失败: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 方案 2：先查询再更新，使用悲观锁
     * 适用于并发不高的场景
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateUserPerformanceWithLock(String month, String userId, BigDecimal amount) {
        // 先查询是否存在，使用 FOR UPDATE 加锁
        UserPerformance existing = repository.findByMonthAndUserIdForUpdate(month, userId)
            .orElse(null);
        
        if (existing != null) {
            // 更新现有记录
            existing.setKfzjDealAmount(amount);
            repository.save(existing);
        } else {
            // 插入新记录
            UserPerformance newRecord = new UserPerformance();
            newRecord.setMonth(month);
            newRecord.setUserId(userId);
            newRecord.setIndexParams(month + "@" + userId);
            newRecord.setKfzjDealAmount(amount);
            repository.save(newRecord);
        }
    }

    /**
     * 方案 3：批量处理，减少事务次数
     * 适用于需要批量更新的场景
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void batchUpdateUserPerformance(List<UserPerformance> records) {
        for (UserPerformance record : records) {
            try {
                repository.insertOrUpdate(
                    record.getMonth(),
                    record.getUserId(),
                    record.getIndexParams(),
                    record.getKfzjDealAmount()
                );
            } catch (Exception e) {
                // 记录失败记录，继续处理其他记录
                System.err.println("批量更新失败: " + record.getUserId() + ", " + e.getMessage());
            }
        }
    }

    /**
     * 方案 4：使用乐观锁（需要添加 @Version 字段）
     * 适用于读多写少的场景
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateUserPerformanceOptimistic(String month, String userId, BigDecimal amount) {
        UserPerformance existing = repository.findByMonthAndUserId(month, userId)
            .orElse(null);
        
        if (existing != null) {
            existing.setKfzjDealAmount(amount);
            try {
                repository.save(existing);
            } catch (org.springframework.optimisticlocking.OptimisticLockingFailureException e) {
                // 乐观锁冲突，重试
                updateUserPerformanceOptimistic(month, userId, amount);
            }
        } else {
            UserPerformance newRecord = new UserPerformance();
            newRecord.setMonth(month);
            newRecord.setUserId(userId);
            newRecord.setIndexParams(month + "@" + userId);
            newRecord.setKfzjDealAmount(amount);
            repository.save(newRecord);
        }
    }
}
