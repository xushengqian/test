package com.example.aviator;

import java.util.HashMap;
import java.util.Map;

/**
 * AviatorScript优化演示和性能测试
 */
public class AviatorOptimizationDemo {
    
    public static void main(String[] args) {
        // 运行功能演示
        runDemo();
        
        // 运行简单性能测试
        runSimpleBenchmark();
    }
    
    /**
     * 运行简单性能测试
     */
    private static void runSimpleBenchmark() {
        System.out.println("\n=== 简单性能测试 ===");
        
        OptimizedAviatorExecutor executor = new OptimizedAviatorExecutor();
        String testExpression = "sqrt(2**2 + 3**2) + max(1, 2, 3)";
        
        // 预热
        executor.execute(testExpression);
        
        // 测试优化版本
        long startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            executor.execute(testExpression);
        }
        long optimizedTime = System.nanoTime() - startTime;
        
        System.out.printf("优化版本执行10000次耗时: %d ms (平均: %d ns)\n", 
            optimizedTime / 1_000_000, optimizedTime / 10000);
        System.out.println("缓存统计: " + executor.getCacheSize());
    }
    
    /**
     * 运行功能演示
     */
    private static void runDemo() {
        System.out.println("=== AviatorScript 优化演示 ===\n");
        
        OptimizedAviatorExecutor executor = new OptimizedAviatorExecutor();
        
        // 演示1: 基本数学运算
        System.out.println("1. 基本数学运算:");
        String[] mathExpressions = {
            "1 + 2 * 3",
            "sqrt(16) + 2**3",
            "sin(3.14159/2) + cos(0)",
            "max(10, 20, 30)",
            "min(5, 3, 8, 2)"
        };
        
        for (String expr : mathExpressions) {
            long startTime = System.nanoTime();
            Object result = executor.execute(expr);
            long endTime = System.nanoTime();
            System.out.printf("  %s = %s (耗时: %d ns)\n", expr, result, (endTime - startTime));
        }
        
        // 演示2: 字符串操作
        System.out.println("\n2. 字符串操作:");
        String[] stringExpressions = {
            "'Hello ' + 'World'",
            "upper('hello world')",
            "lower('HELLO WORLD')",
            "trim('  hello  ') + '!'"
        };
        
        for (String expr : stringExpressions) {
            long startTime = System.nanoTime();
            Object result = executor.execute(expr);
            long endTime = System.nanoTime();
            System.out.printf("  %s = %s (耗时: %d ns)\n", expr, result, (endTime - startTime));
        }
        
        // 演示3: 复杂表达式
        System.out.println("\n3. 复杂表达式:");
        String[] complexExpressions = {
            "3.14 * 2 * 5",
            "sqrt(2**2 + 3**2)",
            "abs(-15) + max(1, 2, 3)",
            "sqrt(144) + 2**4"
        };
        
        for (String expr : complexExpressions) {
            long startTime = System.nanoTime();
            Object result = executor.execute(expr);
            long endTime = System.nanoTime();
            System.out.printf("  %s = %s (耗时: %d ns)\n", expr, result, (endTime - startTime));
        }
        
        // 演示4: 缓存效果
        System.out.println("\n4. 缓存效果演示:");
        String repeatedExpr = "sqrt(144) + 2**4";
        
        // 第一次执行（编译+执行）
        long startTime1 = System.nanoTime();
        Object result1 = executor.execute(repeatedExpr);
        long endTime1 = System.nanoTime();
        System.out.printf("  第一次执行: %s = %s (耗时: %d ns)\n", repeatedExpr, result1, (endTime1 - startTime1));
        
        // 第二次执行（从缓存获取）
        long startTime2 = System.nanoTime();
        Object result2 = executor.execute(repeatedExpr);
        long endTime2 = System.nanoTime();
        System.out.printf("  第二次执行: %s = %s (耗时: %d ns)\n", repeatedExpr, result2, (endTime2 - startTime2));
        
        System.out.printf("  性能提升: %.2f倍\n", (double)(endTime1 - startTime1) / (endTime2 - startTime2));
        
        // 演示5: 带参数的表达式
        System.out.println("\n5. 带参数的表达式:");
        Map<String, Object> env = new HashMap<>();
        env.put("x", 10);
        env.put("y", 20);
        env.put("name", "AviatorScript");
        
        String[] paramExpressions = {
            "x + y",
            "x * y / 2",
            "'Hello ' + name"
        };
        
        for (String expr : paramExpressions) {
            long startTime = System.nanoTime();
            Object result = executor.execute(expr, env);
            long endTime = System.nanoTime();
            System.out.printf("  %s = %s (耗时: %d ns)\n", expr, result, (endTime - startTime));
        }
        
        System.out.println("\n缓存大小: " + executor.getCacheSize());
    }
}