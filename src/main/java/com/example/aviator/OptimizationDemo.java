package com.example.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * AviatorScript 优化演示
 * 展示不同优化策略的性能差异
 */
public class OptimizationDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(OptimizationDemo.class);
    
    public static void main(String[] args) {
        logger.info("=== AviatorScript 优化演示 ===");
        
        // 测试表达式（使用简单的数学表达式）
        String[] expressions = {
            "1 + 2 * 3 - 4 / 2",
            "2 * 3 + 4 - 1",
            "3.14159 * 2",
            "100 / 5 + 10",
            "(1 + 2) * (3 + 4) / 5"
        };
        
        int iterations = 10000;
        
        // 1. 原生 AviatorEvaluator 性能测试
        testNativePerformance(expressions, iterations);
        
        // 2. 优化执行器性能测试
        testOptimizedExecutorPerformance(expressions, iterations);
        
        // 3. 表达式池性能测试
        testExpressionPoolPerformance(expressions, iterations);
        
        // 4. 展示缓存效果
        demonstrateCacheEffects();
        
        // 5. 批量执行演示
        demonstrateBatchExecution(expressions);
    }
    
    /**
     * 测试原生 AviatorEvaluator 性能
     */
    private static void testNativePerformance(String[] expressions, int iterations) {
        logger.info("\n=== 原生 AviatorEvaluator 性能测试 ===");
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < iterations; i++) {
            for (String expr : expressions) {
                AviatorEvaluator.execute(expr, Collections.emptyMap());
            }
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        logger.info("执行 {} 次，共 {} 个表达式", iterations, expressions.length);
        logger.info("总耗时: {} ms", totalTime);
        logger.info("平均每次执行: {} μs", (totalTime * 1000L) / (iterations * expressions.length));
    }
    
    /**
     * 测试优化执行器性能
     */
    private static void testOptimizedExecutorPerformance(String[] expressions, int iterations) {
        logger.info("\n=== 优化执行器性能测试 ===");
        
        OptimizedAviatorExecutor executor = OptimizedAviatorExecutor.getInstance();
        
        // 预编译表达式
        executor.precompileExpressions(expressions);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < iterations; i++) {
            for (String expr : expressions) {
                executor.executeWithoutParams(expr);
            }
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        logger.info("执行 {} 次，共 {} 个表达式", iterations, expressions.length);
        logger.info("总耗时: {} ms", totalTime);
        logger.info("平均每次执行: {} μs", (totalTime * 1000L) / (iterations * expressions.length));
        logger.info("缓存统计: {}", executor.getCacheStats());
    }
    
    /**
     * 测试表达式池性能
     */
    private static void testExpressionPoolPerformance(String[] expressions, int iterations) {
        logger.info("\n=== 表达式池性能测试 ===");
        
        ExpressionPool pool = ExpressionPool.getInstance();
        
        // 初始化表达式池
        pool.initPools(expressions);
        
        // 预热
        pool.warmUp(expressions);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < iterations; i++) {
            for (String expr : expressions) {
                pool.execute(expr);
            }
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        logger.info("执行 {} 次，共 {} 个表达式", iterations, expressions.length);
        logger.info("总耗时: {} ms", totalTime);
        logger.info("平均每次执行: {} μs", (totalTime * 1000L) / (iterations * expressions.length));
        logger.info("池统计信息:\n{}", pool.getPoolStats());
    }
    
    /**
     * 演示缓存效果
     */
    private static void demonstrateCacheEffects() {
        logger.info("\n=== 缓存效果演示 ===");
        
        OptimizedAviatorExecutor executor = OptimizedAviatorExecutor.getInstance();
        executor.clearCache(); // 清除之前的缓存
        
        String expression = "3.14159 * 2.71828 + 1.414";
        
        // 第一次执行（无缓存）
        long start1 = System.nanoTime();
        Object result1 = executor.executeWithoutParams(expression);
        long time1 = System.nanoTime() - start1;
        
        // 第二次执行（有缓存）
        long start2 = System.nanoTime();
        Object result2 = executor.executeWithoutParams(expression);
        long time2 = System.nanoTime() - start2;
        
        logger.info("表达式: {}", expression);
        logger.info("第一次执行（无缓存）: {} ns, 结果: {}", time1, result1);
        logger.info("第二次执行（有缓存）: {} ns, 结果: {}", time2, result2);
        logger.info("性能提升: {:.2f}x", String.format("%.2f", (double) time1 / time2));
    }
    
    /**
     * 演示批量执行
     */
    private static void demonstrateBatchExecution(String[] expressions) {
        logger.info("\n=== 批量执行演示 ===");
        
        OptimizedAviatorExecutor executor = OptimizedAviatorExecutor.getInstance();
        
        // 批量执行
        long startTime = System.nanoTime();
        Object[] results = executor.batchExecute(expressions);
        long totalTime = System.nanoTime() - startTime;
        
        logger.info("批量执行 {} 个表达式，总耗时: {} μs", expressions.length, totalTime / 1000);
        
        for (int i = 0; i < expressions.length; i++) {
            logger.info("表达式: {} = {}", expressions[i], results[i]);
        }
    }
}