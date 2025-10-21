package com.example.service;

import com.example.entity.User;
import com.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 并发查询服务
 * 演示如何使用优化的线程池进行高效的数据库并发查询
 */
@Service
public class ConcurrentQueryService {

    private static final Logger logger = LoggerFactory.getLogger(ConcurrentQueryService.class);

    private final UserRepository userRepository;
    private final PerformanceMonitor performanceMonitor;
    private final Executor databaseQueryExecutor;

    public ConcurrentQueryService(UserRepository userRepository,
                                PerformanceMonitor performanceMonitor,
                                @Qualifier("databaseQueryExecutor") Executor databaseQueryExecutor) {
        this.userRepository = userRepository;
        this.performanceMonitor = performanceMonitor;
        this.databaseQueryExecutor = databaseQueryExecutor;
    }

    /**
     * 初始化测试数据
     */
    @Transactional
    public void initializeTestData() {
        logger.info("开始初始化测试数据...");
        
        List<User> users = IntStream.range(1, 1001)
                .mapToObj(i -> new User(
                    "user" + i,
                    "user" + i + "@example.com",
                    "User " + i
                ))
                .collect(Collectors.toList());

        // 设置一些用户为非活跃状态
        for (int i = 0; i < users.size(); i += 10) {
            users.get(i).setIsActive(false);
        }

        // 设置登录次数
        for (int i = 0; i < users.size(); i++) {
            users.get(i).setLoginCount((int) (Math.random() * 100));
        }

        userRepository.saveAll(users);
        logger.info("测试数据初始化完成，共创建 {} 个用户", users.size());
    }

    /**
     * 并发查询活跃用户
     * 使用多个线程同时执行不同的查询任务
     */
    @Async("databaseQueryExecutor")
    public CompletableFuture<List<User>> findActiveUsersAsync() {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.debug("开始查询活跃用户 - 线程: {}", Thread.currentThread().getName());
            
            List<User> activeUsers = userRepository.findByIsActiveTrue();
            
            long executionTime = System.currentTimeMillis() - startTime;
            performanceMonitor.recordQuery(executionTime);
            
            logger.debug("活跃用户查询完成 - 线程: {}, 耗时: {}ms, 结果数: {}", 
                        Thread.currentThread().getName(), executionTime, activeUsers.size());
            
            return CompletableFuture.completedFuture(activeUsers);
            
        } catch (Exception e) {
            logger.error("查询活跃用户时发生错误", e);
            performanceMonitor.recordConnectionError();
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    /**
     * 并发查询用户统计信息
     */
    @Async("databaseQueryExecutor")
    public CompletableFuture<Long> countActiveUsersAsync() {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.debug("开始统计活跃用户数量 - 线程: {}", Thread.currentThread().getName());
            
            Long count = userRepository.countActiveUsers();
            
            long executionTime = System.currentTimeMillis() - startTime;
            performanceMonitor.recordQuery(executionTime);
            
            logger.debug("活跃用户统计完成 - 线程: {}, 耗时: {}ms, 数量: {}", 
                        Thread.currentThread().getName(), executionTime, count);
            
            return CompletableFuture.completedFuture(count);
            
        } catch (Exception e) {
            logger.error("统计活跃用户数量时发生错误", e);
            performanceMonitor.recordConnectionError();
            return CompletableFuture.completedFuture(0L);
        }
    }

    /**
     * 并发查询最近活跃用户
     */
    @Async("databaseQueryExecutor")
    public CompletableFuture<List<User>> findRecentlyActiveUsersAsync(int limit) {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.debug("开始查询最近活跃用户 - 线程: {}", Thread.currentThread().getName());
            
            List<User> recentUsers = userRepository.findRecentlyActiveUsers(
                org.springframework.data.domain.PageRequest.of(0, limit)
            );
            
            long executionTime = System.currentTimeMillis() - startTime;
            performanceMonitor.recordQuery(executionTime);
            
            logger.debug("最近活跃用户查询完成 - 线程: {}, 耗时: {}ms, 结果数: {}", 
                        Thread.currentThread().getName(), executionTime, recentUsers.size());
            
            return CompletableFuture.completedFuture(recentUsers);
            
        } catch (Exception e) {
            logger.error("查询最近活跃用户时发生错误", e);
            performanceMonitor.recordConnectionError();
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    /**
     * 并发执行复杂统计查询（模拟耗时操作）
     */
    @Async("databaseQueryExecutor")
    public CompletableFuture<List<Object[]>> getUserStatisticsAsync() {
        long startTime = System.currentTimeMillis();
        
        try {
            logger.debug("开始执行用户统计查询 - 线程: {}", Thread.currentThread().getName());
            
            // 模拟复杂查询的延迟
            Thread.sleep(100); // 模拟100ms的查询时间
            
            List<Object[]> statistics = userRepository.findUserStatistics();
            
            long executionTime = System.currentTimeMillis() - startTime;
            performanceMonitor.recordQuery(executionTime);
            
            logger.debug("用户统计查询完成 - 线程: {}, 耗时: {}ms, 结果数: {}", 
                        Thread.currentThread().getName(), executionTime, statistics.size());
            
            return CompletableFuture.completedFuture(statistics);
            
        } catch (Exception e) {
            logger.error("执行用户统计查询时发生错误", e);
            performanceMonitor.recordConnectionError();
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
    }

    /**
     * 批量并发查询演示
     * 同时执行多个不同类型的查询任务
     */
    public void performBatchConcurrentQueries() {
        logger.info("开始执行批量并发查询测试...");
        long startTime = System.currentTimeMillis();

        try {
            // 创建多个并发查询任务
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // 任务1：查询活跃用户
            for (int i = 0; i < 5; i++) {
                CompletableFuture<Void> future = findActiveUsersAsync()
                    .thenAccept(users -> logger.debug("活跃用户查询结果: {} 个用户", users.size()));
                futures.add(future);
            }

            // 任务2：统计用户数量
            for (int i = 0; i < 3; i++) {
                CompletableFuture<Void> future = countActiveUsersAsync()
                    .thenAccept(count -> logger.debug("用户统计结果: {} 个活跃用户", count));
                futures.add(future);
            }

            // 任务3：查询最近活跃用户
            for (int i = 0; i < 4; i++) {
                CompletableFuture<Void> future = findRecentlyActiveUsersAsync(10)
                    .thenAccept(users -> logger.debug("最近活跃用户查询结果: {} 个用户", users.size()));
                futures.add(future);
            }

            // 任务4：复杂统计查询
            for (int i = 0; i < 2; i++) {
                CompletableFuture<Void> future = getUserStatisticsAsync()
                    .thenAccept(stats -> logger.debug("统计查询结果: {} 条记录", stats.size()));
                futures.add(future);
            }

            // 等待所有任务完成
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );

            allFutures.join(); // 等待所有任务完成

            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("批量并发查询测试完成，总耗时: {}ms，共执行 {} 个查询任务", totalTime, futures.size());

        } catch (Exception e) {
            logger.error("批量并发查询测试失败", e);
        }
    }

    /**
     * 压力测试：大量并发查询
     */
    public void performStressTest(int concurrentQueries) {
        logger.info("开始执行压力测试，并发查询数: {}", concurrentQueries);
        long startTime = System.currentTimeMillis();

        try {
            List<CompletableFuture<Void>> futures = IntStream.range(0, concurrentQueries)
                .mapToObj(i -> {
                    // 随机选择查询类型
                    int queryType = i % 4;
                    switch (queryType) {
                        case 0:
                            return findActiveUsersAsync().thenAccept(users -> {});
                        case 1:
                            return countActiveUsersAsync().thenAccept(count -> {});
                        case 2:
                            return findRecentlyActiveUsersAsync(5).thenAccept(users -> {});
                        default:
                            return getUserStatisticsAsync().thenAccept(stats -> {});
                    }
                })
                .collect(Collectors.toList());

            // 等待所有查询完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            long totalTime = System.currentTimeMillis() - startTime;
            logger.info("压力测试完成，并发查询数: {}, 总耗时: {}ms, 平均耗时: {}ms", 
                       concurrentQueries, totalTime, totalTime / concurrentQueries);

        } catch (Exception e) {
            logger.error("压力测试失败", e);
        }
    }
}