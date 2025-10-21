package com.example.repository;

import com.example.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户数据访问层
 * 提供各种查询方法用于测试并发性能
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据用户名查找用户
     */
    List<User> findByUsername(String username);

    /**
     * 根据邮箱查找用户
     */
    User findByEmail(String email);

    /**
     * 查找活跃用户
     */
    List<User> findByIsActiveTrue();

    /**
     * 根据创建时间范围查找用户
     */
    List<User> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 分页查询活跃用户
     */
    Page<User> findByIsActiveTrue(Pageable pageable);

    /**
     * 查找登录次数大于指定值的用户
     */
    List<User> findByLoginCountGreaterThan(Integer count);

    /**
     * 自定义查询：根据用户名模糊搜索
     */
    @Query("SELECT u FROM User u WHERE u.username LIKE %:username% AND u.isActive = true")
    List<User> findActiveUsersByUsernameContaining(@Param("username") String username);

    /**
     * 自定义查询：统计活跃用户数量
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    Long countActiveUsers();

    /**
     * 自定义查询：获取最近登录的用户
     */
    @Query("SELECT u FROM User u WHERE u.loginCount > 0 ORDER BY u.updatedAt DESC")
    List<User> findRecentlyActiveUsers(Pageable pageable);

    /**
     * 自定义查询：复杂统计查询（模拟耗时操作）
     */
    @Query(value = "SELECT u.*, " +
                   "(SELECT COUNT(*) FROM users u2 WHERE u2.created_at < u.created_at) as rank_order " +
                   "FROM users u WHERE u.is_active = true ORDER BY u.login_count DESC", 
           nativeQuery = true)
    List<Object[]> findUserStatistics();

    /**
     * 批量更新用户登录次数
     */
    @Query("UPDATE User u SET u.loginCount = u.loginCount + 1, u.updatedAt = CURRENT_TIMESTAMP WHERE u.id IN :ids")
    int incrementLoginCountForUsers(@Param("ids") List<Long> ids);
}