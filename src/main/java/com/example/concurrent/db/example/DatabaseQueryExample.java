package com.example.concurrent.db.example;

import com.example.concurrent.db.config.DatabaseConfig;
import com.example.concurrent.db.core.AdaptiveThreadPoolManager;
import com.example.concurrent.db.core.ConcurrentQueryExecutor;
import com.example.concurrent.db.monitor.PerformanceMonitor;
import com.example.concurrent.db.utils.OptimalThreadCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 数据库并发查询示例
 * 演示如何使用最优线程数进行高效的数据库查询
 */
public class DatabaseQueryExample {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseQueryExample.class);
    
    public static void main(String[] args) {
        // 示例1: 使用最优线程数计算器
        demonstrateOptimalThreadCalculation();
        
        // 示例2: 配置和使用数据库连接池
        demonstrateDatabasePoolConfiguration();
        
        // 示例3: 并发查询执行
        demonstrateConcurrentQueries();
        
        // 示例4: 自适应线程池
        demonstrateAdaptiveThreadPool();
        
        // 示例5: 性能监控和优化
        demonstratePerformanceMonitoring();
    }
    
    /**
     * 示例1: 演示最优线程数计算
     */
    private static void demonstrateOptimalThreadCalculation() {
        logger.info("========== 最优线程数计算示例 ==========");
        
        // 获取系统信息
        int cpuCores = OptimalThreadCalculator.getCpuCores();
        logger.info("系统CPU核心数: {}", cpuCores);
        
        // CPU密集型任务
        int cpuIntensiveThreads = OptimalThreadCalculator.calculateForCpuIntensive();
        logger.info("CPU密集型任务推荐线程数: {}", cpuIntensiveThreads);
        
        // IO密集型任务
        int ioIntensiveThreads = OptimalThreadCalculator.calculateForIoIntensive();
        logger.info("IO密集型任务推荐线程数: {}", ioIntensiveThreads);
        
        // 根据阻塞系数计算
        double blockingCoefficient = 0.8; // 80%的时间在等待IO
        int blockingThreads = OptimalThreadCalculator.calculateWithBlockingCoefficient(blockingCoefficient);
        logger.info("阻塞系数{}时推荐线程数: {}", blockingCoefficient, blockingThreads);
        
        // 数据库查询场景
        int connectionPoolSize = 20;
        long queryResponseTime = 100; // 100ms
        long processingTime = 10;     // 10ms
        int dbQueryThreads = OptimalThreadCalculator.calculateForDatabaseQuery(
            connectionPoolSize, queryResponseTime, processingTime);
        logger.info("数据库查询推荐线程数: {}", dbQueryThreads);
        
        // 获取不同任务类型的推荐配置
        for (OptimalThreadCalculator.TaskType taskType : OptimalThreadCalculator.TaskType.values()) {
            OptimalThreadCalculator.ThreadPoolConfig config = 
                OptimalThreadCalculator.getRecommendedConfig(taskType);
            logger.info("任务类型[{}]推荐配置: {}", taskType.getDescription(), config);
        }
        
        logger.info("");
    }
    
    /**
     * 示例2: 演示数据库连接池配置
     */
    private static void demonstrateDatabasePoolConfiguration() {
        logger.info("========== 数据库连接池配置示例 ==========");
        
        // 创建数据库配置
        DatabaseConfig dbConfig = new DatabaseConfig();
        
        // 配置MySQL数据源（示例）
        dbConfig.configureMySql("localhost", 3306, "testdb", "root", "password")
                .setPoolSize(10, 30)
                .setTimeouts(30000, 600000, 1800000)
                .optimizePoolSize(100, 50, 60);
        
        // 构建数据源
        // 注意：实际使用时需要确保数据库可用
        // DataSource dataSource = dbConfig.build();
        
        logger.info("数据库连接池配置完成");
        logger.info("");
    }
    
    /**
     * 示例3: 演示并发查询执行
     */
    private static void demonstrateConcurrentQueries() {
        logger.info("========== 并发查询执行示例 ==========");
        
        // 使用H2内存数据库作为示例
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.configureMySql("mem", 0, "test", "sa", "")
                .setPoolSize(5, 20);
        
        // 注意：这里使用模拟的数据源，实际使用时请配置真实的数据库
        // DataSource dataSource = dbConfig.build();
        
        // 创建并发查询执行器
        // ConcurrentQueryExecutor executor = new ConcurrentQueryExecutor(dataSource);
        
        // 模拟查询任务
        List<QuerySimulation> simulations = createQuerySimulations(10);
        
        logger.info("创建了{}个模拟查询任务", simulations.size());
        
        // 使用最优线程数执行
        int optimalThreads = OptimalThreadCalculator.calculateForDatabaseQuery(20, 100, 10);
        logger.info("使用{}个线程执行并发查询", optimalThreads);
        
        // 执行查询（示例代码，不实际执行）
        /*
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (QuerySimulation sim : simulations) {
            CompletableFuture<String> future = executor.executeQueryAsync(
                sim.sql,
                rs -> processResultSet(rs),
                sim.params
            );
            futures.add(future);
        }
        
        // 等待所有查询完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        */
        
        logger.info("");
    }
    
    /**
     * 示例4: 演示自适应线程池
     */
    private static void demonstrateAdaptiveThreadPool() {
        logger.info("========== 自适应线程池示例 ==========");
        
        // 创建自适应线程池管理器
        AdaptiveThreadPoolManager manager = new AdaptiveThreadPoolManager();
        
        // 提交一批任务
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        
        for (int i = 0; i < 50; i++) {
            final int taskId = i;
            CompletableFuture<Integer> future = manager.submit(() -> {
                // 模拟数据库查询
                Thread.sleep((long) (Math.random() * 100));
                logger.debug("任务{}完成", taskId);
                return taskId;
            });
            futures.add(future);
        }
        
        // 等待一段时间让自适应调整生效
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 获取统计信息
        AdaptiveThreadPoolManager.ThreadPoolStats stats = manager.getStats();
        logger.info("线程池统计: {}", stats);
        
        // 关闭管理器
        manager.shutdown();
        
        logger.info("");
    }
    
    /**
     * 示例5: 演示性能监控
     */
    private static void demonstratePerformanceMonitoring() {
        logger.info("========== 性能监控示例 ==========");
        
        // 创建性能监控器
        PerformanceMonitor monitor = new PerformanceMonitor();
        
        // 模拟记录查询性能
        for (int i = 0; i < 100; i++) {
            long duration = (long) (Math.random() * 200);
            boolean success = Math.random() > 0.05; // 95%成功率
            monitor.recordQuery("SELECT", duration, success);
        }
        
        // 等待一段时间收集数据
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 获取优化建议
        PerformanceMonitor.OptimizationAdvice advice = 
            monitor.getOptimizationAdvice(10, 20);
        logger.info("优化建议: {}", advice);
        
        // 获取性能报告
        PerformanceMonitor.PerformanceReport report = monitor.getPerformanceReport();
        logger.info("性能报告: {}", report);
        
        // 关闭监控器
        monitor.close();
        
        logger.info("");
    }
    
    /**
     * 创建模拟查询
     */
    private static List<QuerySimulation> createQuerySimulations(int count) {
        List<QuerySimulation> simulations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            simulations.add(new QuerySimulation(
                "SELECT * FROM users WHERE id = ?",
                new Object[]{i}
            ));
        }
        return simulations;
    }
    
    /**
     * 处理结果集（示例）
     */
    private static String processResultSet(ResultSet rs) {
        try {
            StringBuilder result = new StringBuilder();
            while (rs.next()) {
                result.append(rs.getString(1)).append(",");
            }
            return result.toString();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 查询模拟类
     */
    private static class QuerySimulation {
        final String sql;
        final Object[] params;
        
        QuerySimulation(String sql, Object[] params) {
            this.sql = sql;
            this.params = params;
        }
    }
}