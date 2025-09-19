package com.example.aviator;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 示例用法 - 展示如何使用优化的AviatorScript执行器
 */
public class ExampleUsage {
    
    public static void main(String[] args) {
        System.out.println("=== AviatorScript 优化执行器示例 ===\n");
        
        // 1. 基本使用
        basicUsage();
        
        // 2. 批量执行
        batchExecution();
        
        // 3. 异步执行
        asyncExecution();
        
        // 4. 性能对比
        performanceComparison();
        
        // 5. 缓存统计
        cacheStatistics();
    }
    
    /**
     * 基本使用示例
     */
    private static void basicUsage() {
        System.out.println("1. 基本使用示例:");
        System.out.println("-----------------");
        
        OptimizedAviatorExecutor executor = new OptimizedAviatorExecutor();
        
        // 数学表达式
        Object result = executor.execute("1 + 2 * 3");
        System.out.println("1 + 2 * 3 = " + result);
        
        // 字符串操作
        result = executor.execute("'Hello' + ' ' + 'AviatorScript'");
        System.out.println("字符串拼接: " + result);
        
        // 逻辑表达式
        result = executor.execute("(10 > 5) && (20 < 30)");
        System.out.println("逻辑运算: " + result);
        
        // 复杂表达式
        result = executor.execute("let sum = 0; for i in range(1, 11) { sum = sum + i }; sum");
        System.out.println("1到10的和: " + result);
        
        System.out.println();
    }
    
    /**
     * 批量执行示例
     */
    private static void batchExecution() {
        System.out.println("2. 批量执行示例:");
        System.out.println("-----------------");
        
        OptimizedAviatorExecutor executor = new OptimizedAviatorExecutor();
        
        String[] expressions = {
            "math.pi",
            "math.sqrt(100)",
            "math.pow(2, 8)",
            "math.abs(-42)",
            "math.max(10, 20, 30)"
        };
        
        Object[] results = executor.executeBatch(expressions);
        
        for (int i = 0; i < expressions.length; i++) {
            System.out.printf("%s = %s%n", expressions[i], results[i]);
        }
        
        System.out.println();
    }
    
    /**
     * 异步执行示例
     */
    private static void asyncExecution() {
        System.out.println("3. 异步执行示例:");
        System.out.println("-----------------");
        
        OptimizedAviatorExecutor executor = new OptimizedAviatorExecutor();
        
        // 创建多个异步任务
        CompletableFuture<Object> future1 = executor.executeAsync("seq.list(1,2,3,4,5)");
        CompletableFuture<Object> future2 = executor.executeAsync("seq.map('a':1, 'b':2, 'c':3)");
        CompletableFuture<Object> future3 = executor.executeAsync("string.length('AviatorScript')");
        
        // 等待所有任务完成
        CompletableFuture.allOf(future1, future2, future3).join();
        
        try {
            System.out.println("列表创建: " + future1.get());
            System.out.println("Map创建: " + future2.get());
            System.out.println("字符串长度: " + future3.get());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.out.println();
    }
    
    /**
     * 性能对比示例
     */
    private static void performanceComparison() {
        System.out.println("4. 性能对比示例:");
        System.out.println("-----------------");
        
        OptimizedAviatorExecutor optimized = new OptimizedAviatorExecutor(true, true, 1000, 1000);
        
        String expression = "math.sqrt(math.pow(3, 2) + math.pow(4, 2))";
        
        // 预热
        for (int i = 0; i < 100; i++) {
            optimized.execute(expression);
        }
        
        // 测试优化版本
        long startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            optimized.execute(expression);
        }
        long optimizedTime = System.nanoTime() - startTime;
        
        // 测试原始版本
        startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            com.googlecode.aviator.AviatorEvaluator.execute(expression);
        }
        long originalTime = System.nanoTime() - startTime;
        
        System.out.printf("优化版本执行10000次: %.2f ms%n", optimizedTime / 1_000_000.0);
        System.out.printf("原始版本执行10000次: %.2f ms%n", originalTime / 1_000_000.0);
        System.out.printf("性能提升: %.2fx%n", (double) originalTime / optimizedTime);
        
        System.out.println();
    }
    
    /**
     * 缓存统计示例
     */
    private static void cacheStatistics() {
        System.out.println("5. 缓存统计示例:");
        System.out.println("-----------------");
        
        OptimizedAviatorExecutor executor = new OptimizedAviatorExecutor(true, true, 100, 100);
        
        // 执行一些表达式
        String[] expressions = {
            "1 + 1",
            "2 + 2",
            "3 + 3",
            "1 + 1",  // 重复
            "2 + 2",  // 重复
            "4 + 4",
            "1 + 1"   // 重复
        };
        
        for (String expr : expressions) {
            executor.execute(expr);
        }
        
        // 获取统计信息
        OptimizedAviatorExecutor.CacheStats stats = executor.getCacheStats();
        System.out.println("缓存统计: " + stats);
        
        // 获取执行统计
        Map<String, Long> execStats = executor.getExecutionStats();
        System.out.println("\n执行时间统计:");
        execStats.forEach((expr, time) -> 
            System.out.printf("  %s: %.3f ms%n", expr, time / 1_000_000.0)
        );
        
        System.out.println();
    }
}