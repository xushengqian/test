package com.example.repository;

import com.example.entity.UserPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * 用户绩效数据访问层
 */
@Repository
public interface UserPerformanceRepository extends JpaRepository<UserPerformance, Long> {

    /**
     * 使用原生 SQL 执行 INSERT ... ON DUPLICATE KEY UPDATE
     * 这是最高效的方式，但需要确保有合适的唯一索引
     */
    @Modifying
    @Query(value = "INSERT INTO ks_index_user_performance (month, user_id, index_params, kfzj_deal_amount) " +
           "VALUES (:month, :userId, :indexParams, :amount) " +
           "ON DUPLICATE KEY UPDATE kfzj_deal_amount = :amount", 
           nativeQuery = true)
    int insertOrUpdate(@Param("month") String month, 
                      @Param("userId") String userId,
                      @Param("indexParams") String indexParams,
                      @Param("amount") BigDecimal amount);

    /**
     * 根据月份和用户ID查询（用于悲观锁方案）
     */
    @Query("SELECT u FROM UserPerformance u WHERE u.month = :month AND u.userId = :userId")
    Optional<UserPerformance> findByMonthAndUserId(@Param("month") String month, 
                                                   @Param("userId") String userId);

    /**
     * 使用悲观锁查询（SELECT ... FOR UPDATE）
     * 在事务中会锁定该行，直到事务提交
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserPerformance u WHERE u.month = :month AND u.userId = :userId")
    Optional<UserPerformance> findByMonthAndUserIdForUpdate(@Param("month") String month, 
                                                            @Param("userId") String userId);
}
