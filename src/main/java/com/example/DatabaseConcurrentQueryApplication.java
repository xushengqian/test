package com.example;

import com.example.service.ConcurrentQueryService;
import com.example.service.DynamicThreadPoolManager;
import com.example.service.PerformanceMonitor;
import com.example.service.PerformanceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 数据库并发查询优化演示应用
 * 
 * 本应用演示了如何优化Java中数据库并发查询的线程数设置，包括：
 * 1. 基于CPU核心数和数据库连接池的线程池优化配置
 * 2. 动态线程数调整机制
 * 3. 性能监控和指标收集
 * 4. 实际的并发查询测试场景
 */
@SpringBootApplication
@EnableScheduling
public class DatabaseConcurrentQueryApplication implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConcurrentQueryApplication.class);

    private final ConcurrentQueryService concurrentQueryService;
    private final DynamicThreadPoolManager threadPoolManager;
    private final PerformanceMonitor performanceMonitor;

    public DatabaseConcurrentQueryApplication(ConcurrentQueryService concurrentQueryService,
                                            DynamicThreadPoolManager threadPoolManager,
                                            PerformanceMonitor performanceMonitor) {
        this.concurrentQueryService = concurrentQueryService;
        this.threadPoolManager = threadPoolManager;
        this.performanceMonitor = performanceMonitor;
    }

    public static void main(String[] args) {
        logger.info("启动数据库并发查询优化演示应用...");
        SpringApplication.run(DatabaseConcurrentQueryApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("=== 数据库并发查询线程优化演示 ===");
        
        // 显示系统信息
        showSystemInfo();
        
        // 初始化测试数据
        concurrentQueryService.initializeTestData();
        
        // 等待一段时间让系统稳定
        Thread.sleep(2000);
        
        // 显示初始线程池状态
        showThreadPoolStatus("初始状态");
        
        // 执行并发查询测试
        runConcurrentQueryTests();
        
        // 等待一段时间观察动态调整
        logger.info("等待30秒观察线程池动态调整...");
        Thread.sleep(30000);
        
        // 显示调整后的线程池状态
        showThreadPoolStatus("动态调整后");
        
        // 执行压力测试
        runStressTests();
        
        // 最终状态报告
        Thread.sleep(5000);
        showFinalReport();
        
        logger.info("演示完成！应用将继续运行以展示监控功能...");
    }

    /**
     * 显示系统信息
     */
    private void showSystemInfo() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        long maxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        long totalMemory = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        
        logger.info("=== 系统信息 ===");
        logger.info("CPU核心数: {}", cpuCores);
        logger.info("最大内存: {} MB", maxMemory);
        logger.info("当前内存: {} MB", totalMemory);
        logger.info("================");
    }

    /**
     * 显示线程池状态
     */
    private void showThreadPoolStatus(String phase) {
        DynamicThreadPoolManager.ThreadPoolStatus status = threadPoolManager.getCurrentStatus();
        logger.info("=== 线程池状态 ({}) ===", phase);
        logger.info(status.toString());
        logger.info("========================");
    }

    /**
     * 运行并发查询测试
     */
    private void runConcurrentQueryTests() {
        logger.info("=== 开始并发查询测试 ===");
        
        // 测试1：基本并发查询
        logger.info("测试1: 基本并发查询");
        concurrentQueryService.performBatchConcurrentQueries();
        
        // 等待一段时间
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 测试2：中等负载测试
        logger.info("测试2: 中等负载测试 (50个并发查询)");
        concurrentQueryService.performStressTest(50);
        
        logger.info("=== 并发查询测试完成 ===");
    }

    /**
     * 运行压力测试
     */
    private void runStressTests() {
        logger.info("=== 开始压力测试 ===");
        
        // 压力测试1：高并发查询
        logger.info("压力测试1: 高并发查询 (100个并发)");
        concurrentQueryService.performStressTest(100);
        
        // 等待系统恢复
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 压力测试2：极限并发查询
        logger.info("压力测试2: 极限并发查询 (200个并发)");
        concurrentQueryService.performStressTest(200);
        
        logger.info("=== 压力测试完成 ===");
    }

    /**
     * 显示最终报告
     */
    private void showFinalReport() {
        logger.info("=== 最终性能报告 ===");
        
        // 获取性能指标
        PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
        
        logger.info("总查询数: {}", metrics.getTotalQueries());
        logger.info("慢查询数: {}", metrics.getSlowQueries());
        logger.info("平均查询时间: {} ms", metrics.getAverageQueryTime());
        logger.info("连接错误数: {}", metrics.getConnectionErrors());
        logger.info("当前CPU使用率: {:.2f}%", metrics.getCpuUsage() * 100);
        logger.info("活跃线程数: {}", metrics.getActiveThreads());
        logger.info("队列大小: {}", metrics.getQueueSize());
        logger.info("活跃连接数: {}", metrics.getActiveConnections());
        
        // 显示最终线程池状态
        showThreadPoolStatus("最终状态");
        
        logger.info("===================");
        
        // 测试数据库连接
        boolean dbConnected = performanceMonitor.testDatabaseConnection();
        logger.info("数据库连接状态: {}", dbConnected ? "正常" : "异常");
    }
}