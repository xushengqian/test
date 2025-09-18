package com.example.aviator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * AviatorScript 优化使用示例
 */
public class AviatorOptimizationExample {
    
    private static final Logger logger = LoggerFactory.getLogger(AviatorOptimizationExample.class);
    
    public static void main(String[] args) {
        // 基本优化示例
        basicOptimizationExample();
        
        // 高级优化示例
        advancedOptimizationExample();
        
        // 性能对比示例
        performanceComparisonExample();
        
        // 异步执行示例
        asyncExecutionExample();
    }
    
    /**
     * 基本优化示例
     */
    public static void basicOptimizationExample() {
        logger.info("=== 基本优化示例 ===");
        
        AviatorOptimizer optimizer = AviatorOptimizer.getInstance();
        
        // 常用表达式
        String[] commonExpressions = {
            "1 + 2 * 3",
            "Math.sin(3.14159 / 2)",
            "Math.pow(2, 10)",
            "Math.sqrt(16)",
            "Math.log(Math.E)"
        };
        
        // 预热缓存
        optimizer.warmupCache(commonExpressions);
        
        // 执行表达式
        for (String expr : commonExpressions) {
            try {
                Object result = optimizer.execute(expr);
                logger.info("表达式: {} = {}", expr, result);
            } catch (Exception e) {
                logger.error("执行失败: {}", expr, e);
            }
        }
        
        // 批量执行
        Object[] results = optimizer.executeBatch(commonExpressions);
        logger.info("批量执行结果: {}", results);
        
        // 显示缓存统计
        Map<String, Object> stats = optimizer.getCacheStats();
        logger.info("缓存统计: {}", stats);
    }
    
    /**
     * 高级优化示例
     */
    public static void advancedOptimizationExample() {
        logger.info("=== 高级优化示例 ===");
        
        AdvancedAviatorOptimizer optimizer = AdvancedAviatorOptimizer.getInstance();
        
        // 复杂表达式
        String[] complexExpressions = {
            "Math.sin(Math.PI / 4) + Math.cos(Math.PI / 4)",
            "Math.pow(Math.E, Math.log(10))",
            "Math.sqrt(Math.pow(3, 2) + Math.pow(4, 2))",
            "Math.floor(Math.random() * 100)",
            "System.currentTimeMillis() % 1000"
        };
        
        // 预热缓存
        optimizer.warmupCache(complexExpressions);
        
        // 执行表达式
        for (String expr : complexExpressions) {
            try {
                Object result = optimizer.execute(expr);
                logger.info("表达式: {} = {}", expr, result);
            } catch (Exception e) {
                logger.error("执行失败: {}", expr, e);
            }
        }
        
        // 显示性能统计
        Map<String, Object> stats = optimizer.getPerformanceStats();
        logger.info("性能统计: {}", stats);
    }
    
    /**
     * 性能对比示例
     */
    public static void performanceComparisonExample() {
        logger.info("=== 性能对比示例 ===");
        
        String expression = "Math.sin(Math.PI / 2) + Math.cos(Math.PI / 2)";
        int iterations = 10000;
        
        // 测试基本优化器
        AviatorOptimizer basicOptimizer = AviatorOptimizer.getInstance();
        basicOptimizer.warmupCache(new String[]{expression});
        
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            basicOptimizer.execute(expression);
        }
        long basicTime = System.nanoTime() - startTime;
        
        // 测试高级优化器
        AdvancedAviatorOptimizer advancedOptimizer = AdvancedAviatorOptimizer.getInstance();
        advancedOptimizer.warmupCache(new String[]{expression});
        
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            advancedOptimizer.execute(expression);
        }
        long advancedTime = System.nanoTime() - startTime;
        
        logger.info("基本优化器执行{}次耗时: {}ms", iterations, basicTime / 1_000_000);
        logger.info("高级优化器执行{}次耗时: {}ms", iterations, advancedTime / 1_000_000);
        logger.info("性能提升: {:.2f}%", (double)(basicTime - advancedTime) / basicTime * 100);
    }
    
    /**
     * 异步执行示例
     */
    public static void asyncExecutionExample() {
        logger.info("=== 异步执行示例 ===");
        
        AdvancedAviatorOptimizer optimizer = AdvancedAviatorOptimizer.getInstance();
        
        String[] expressions = {
            "Math.sin(Math.PI / 4)",
            "Math.cos(Math.PI / 4)",
            "Math.tan(Math.PI / 4)",
            "Math.sqrt(2)",
            "Math.log(2)"
        };
        
        // 异步执行单个表达式
        CompletableFuture<Object> future = optimizer.executeAsync("Math.sin(Math.PI / 2)");
        future.thenAccept(result -> {
            logger.info("异步执行结果: {}", result);
        });
        
        // 异步批量执行
        CompletableFuture<Object[]> batchFuture = optimizer.executeBatchAsync(expressions);
        batchFuture.thenAccept(results -> {
            logger.info("异步批量执行结果: {}", results);
        });
        
        // 等待异步执行完成
        try {
            CompletableFuture.allOf(future, batchFuture).get(5, TimeUnit.SECONDS);
            logger.info("所有异步执行完成");
        } catch (Exception e) {
            logger.error("异步执行失败", e);
        }
        
        // 显示性能统计
        Map<String, Object> stats = optimizer.getPerformanceStats();
        logger.info("异步执行性能统计: {}", stats);
    }
}