package com.example.aviator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AviatorScript优化使用示例
 */
public class UsageExample {
    
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== AviatorScript 无参数表达式优化示例 ===\n");
        
        // 1. 基本使用示例
        basicUsageExample();
        
        // 2. 性能对比示例
        performanceComparisonExample();
        
        // 3. 并发执行示例
        concurrentExecutionExample();
        
        // 4. 缓存效果示例
        cacheEffectExample();
    }
    
    /**
     * 基本使用示例
     */
    private static void basicUsageExample() {
        System.out.println("1. 基本使用示例:");
        
        NoParamExpressionOptimizer optimizer = new NoParamExpressionOptimizer();
        
        // 数学表达式
        String[] mathExpressions = {
            "1 + 2 * 3",
            "sqrt(16) + 8",
            "sin(3.14159/2) + cos(0)",
            "max(10, 20, 30)",
            "min(5, 3, 8, 2)",
            "abs(-15) + max(1, 2, 3)",
            "sqrt(100)"
        };
        
        System.out.println("数学表达式执行结果:");
        for (String expr : mathExpressions) {
            long startTime = System.nanoTime();
            Object result = optimizer.execute(expr);
            long endTime = System.nanoTime();
            System.out.printf("  %s = %s (耗时: %d ns)\n", expr, result, (endTime - startTime));
        }
        
        System.out.println("\n缓存统计: " + optimizer.getStatistics());
        System.out.println();
    }
    
    /**
     * 性能对比示例
     */
    private static void performanceComparisonExample() {
        System.out.println("2. 性能对比示例:");
        
        NoParamExpressionOptimizer optimizer = new NoParamExpressionOptimizer();
        String testExpression = "sqrt(4 + 9) + max(1, 2, 3)";
        
        // 预热
        optimizer.execute(testExpression);
        
        // 测试优化版本
        long startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            optimizer.execute(testExpression);
        }
        long optimizedTime = System.nanoTime() - startTime;
        
        // 测试非优化版本（每次都重新编译）
        startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            com.googlecode.aviator.AviatorEvaluator.execute(testExpression);
        }
        long nonOptimizedTime = System.nanoTime() - startTime;
        
        System.out.printf("优化版本耗时: %d ns (平均: %d ns)\n", 
            optimizedTime, optimizedTime / 10000);
        System.out.printf("非优化版本耗时: %d ns (平均: %d ns)\n", 
            nonOptimizedTime, nonOptimizedTime / 10000);
        System.out.printf("性能提升: %.2f倍\n", 
            (double) nonOptimizedTime / optimizedTime);
        System.out.println();
    }
    
    /**
     * 并发执行示例
     */
    private static void concurrentExecutionExample() throws InterruptedException {
        System.out.println("3. 并发执行示例:");
        
        NoParamExpressionOptimizer optimizer = new NoParamExpressionOptimizer();
        String[] expressions = {
            "1 + 2 * 3",
            "sqrt(16) + 8",
            "sin(3.14159/2)",
            "max(10, 20, 30)",
            "min(5, 3, 8, 2)"
        };
        
        int threadCount = 10;
        int executionsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicLong totalExecutions = new AtomicLong(0);
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < executionsPerThread; j++) {
                    String expr = expressions[j % expressions.length];
                    optimizer.execute(expr);
                    totalExecutions.incrementAndGet();
                }
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        long endTime = System.nanoTime();
        long totalTime = endTime - startTime;
        
        System.out.printf("并发执行完成: %d 次执行，耗时: %d ms\n", 
            totalExecutions.get(), totalTime / 1_000_000);
        System.out.printf("平均执行时间: %d ns\n", totalTime / totalExecutions.get());
        System.out.println("缓存统计: " + optimizer.getStatistics());
        System.out.println();
    }
    
    /**
     * 缓存效果示例
     */
    private static void cacheEffectExample() {
        System.out.println("4. 缓存效果示例:");
        
        NoParamExpressionOptimizer optimizer = new NoParamExpressionOptimizer();
        String[] expressions = {
            "sqrt(144) + 16",
            "sin(3.14159/2) + cos(0)",
            "max(10, 20, 30) * min(5, 3, 8)",
            "sqrt(100) + abs(-5)"
        };
        
        // 第一次执行（编译+执行）
        System.out.println("第一次执行（编译+执行）:");
        long firstExecutionTime = 0;
        for (String expr : expressions) {
            long startTime = System.nanoTime();
            Object result = optimizer.execute(expr);
            long endTime = System.nanoTime();
            long executionTime = endTime - startTime;
            firstExecutionTime += executionTime;
            System.out.printf("  %s = %s (耗时: %d ns)\n", expr, result, executionTime);
        }
        
        // 第二次执行（从缓存获取）
        System.out.println("\n第二次执行（从缓存获取）:");
        long secondExecutionTime = 0;
        for (String expr : expressions) {
            long startTime = System.nanoTime();
            Object result = optimizer.execute(expr);
            long endTime = System.nanoTime();
            long executionTime = endTime - startTime;
            secondExecutionTime += executionTime;
            System.out.printf("  %s = %s (耗时: %d ns)\n", expr, result, executionTime);
        }
        
        System.out.printf("\n第一次执行总耗时: %d ns\n", firstExecutionTime);
        System.out.printf("第二次执行总耗时: %d ns\n", secondExecutionTime);
        System.out.printf("性能提升: %.2f倍\n", (double) firstExecutionTime / secondExecutionTime);
        System.out.println("缓存统计: " + optimizer.getStatistics());
    }
}