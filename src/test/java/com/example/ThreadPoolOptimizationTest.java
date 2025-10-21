package com.example;

import com.example.service.ConcurrentQueryService;
import com.example.service.DynamicThreadPoolManager;
import com.example.service.PerformanceMonitor;
import com.example.service.PerformanceMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 线程池优化测试类
 */
@SpringBootTest
@ActiveProfiles("test")
public class ThreadPoolOptimizationTest {

    @Autowired
    private ConcurrentQueryService concurrentQueryService;

    @Autowired
    private DynamicThreadPoolManager threadPoolManager;

    @Autowired
    private PerformanceMonitor performanceMonitor;

    @Test
    public void testThreadPoolConfiguration() {
        // 测试线程池是否正确配置
        DynamicThreadPoolManager.ThreadPoolStatus status = threadPoolManager.getCurrentStatus();
        
        assertNotNull(status);
        assertTrue(status.getCorePoolSize() > 0);
        assertTrue(status.getMaximumPoolSize() >= status.getCorePoolSize());
        
        System.out.println("线程池配置测试通过: " + status);
    }

    @Test
    public void testConcurrentQueries() throws InterruptedException {
        // 初始化测试数据
        concurrentQueryService.initializeTestData();
        
        // 执行并发查询测试
        long startTime = System.currentTimeMillis();
        concurrentQueryService.performBatchConcurrentQueries();
        long endTime = System.currentTimeMillis();
        
        // 验证查询执行时间合理
        long executionTime = endTime - startTime;
        assertTrue(executionTime < 30000, "并发查询执行时间应该在30秒内: " + executionTime + "ms");
        
        // 等待异步任务完成
        Thread.sleep(2000);
        
        // 验证性能指标
        PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        assertTrue(metrics.getTotalQueries() > 0, "应该有查询记录");
        
        System.out.println("并发查询测试通过，执行时间: " + executionTime + "ms");
        System.out.println("性能指标: " + metrics.getTotalQueries() + " 个查询");
    }

    @Test
    public void testPerformanceMonitoring() {
        // 测试性能监控功能
        PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        
        assertNotNull(metrics);
        assertTrue(metrics.getCpuUsage() >= 0);
        assertTrue(metrics.getActiveThreads() >= 0);
        
        // 测试数据库连接
        boolean dbConnected = performanceMonitor.testDatabaseConnection();
        assertTrue(dbConnected, "数据库连接应该正常");
        
        System.out.println("性能监控测试通过");
    }

    @Test
    public void testStressTest() {
        // 初始化测试数据
        concurrentQueryService.initializeTestData();
        
        // 执行压力测试
        long startTime = System.currentTimeMillis();
        concurrentQueryService.performStressTest(20); // 较小的并发数用于测试
        long endTime = System.currentTimeMillis();
        
        long executionTime = endTime - startTime;
        assertTrue(executionTime < 60000, "压力测试执行时间应该在60秒内: " + executionTime + "ms");
        
        System.out.println("压力测试通过，执行时间: " + executionTime + "ms");
    }
}