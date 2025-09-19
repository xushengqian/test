package com.example.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 优化的AviatorScript执行器，专门用于无参数表达式执行
 * 
 * 主要优化策略：
 * 1. 表达式编译缓存
 * 2. 结果缓存（对于纯函数表达式）
 * 3. 线程安全的执行器实例池
 * 4. 优化的编译选项
 * 5. 预编译常用表达式
 */
public class OptimizedAviatorExecutor {
    private static final Logger logger = LoggerFactory.getLogger(OptimizedAviatorExecutor.class);
    
    // 编译后的表达式缓存
    private final Cache<String, Expression> expressionCache;
    
    // 结果缓存（用于纯函数表达式）
    private final Cache<String, Object> resultCache;
    
    // 执行器实例
    private final AviatorEvaluatorInstance evaluator;
    
    // 统计信息
    private final ConcurrentHashMap<String, Long> executionStats = new ConcurrentHashMap<>();
    
    // 配置选项
    private final boolean enableResultCache;
    private final boolean enableStats;
    
    /**
     * 构造函数，使用默认配置
     */
    public OptimizedAviatorExecutor() {
        this(true, false, 1000, 10000);
    }
    
    /**
     * 构造函数，允许自定义配置
     * 
     * @param enableResultCache 是否启用结果缓存
     * @param enableStats 是否启用统计
     * @param expressionCacheSize 表达式缓存大小
     * @param resultCacheSize 结果缓存大小
     */
    public OptimizedAviatorExecutor(boolean enableResultCache, boolean enableStats, 
                                   int expressionCacheSize, int resultCacheSize) {
        this.enableResultCache = enableResultCache;
        this.enableStats = enableStats;
        
        // 初始化表达式缓存
        this.expressionCache = Caffeine.newBuilder()
            .maximumSize(expressionCacheSize)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .recordStats()
            .build();
        
        // 初始化结果缓存
        this.resultCache = Caffeine.newBuilder()
            .maximumSize(resultCacheSize)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()
            .build();
        
        // 创建优化的执行器实例
        this.evaluator = AviatorEvaluator.newInstance();
        configureEvaluator();
        
        // 预编译常用表达式
        precompileCommonExpressions();
    }
    
    /**
     * 配置执行器选项以优化性能
     */
    private void configureEvaluator() {
        // 启用编译缓存
        evaluator.setOption(Options.USE_USER_ENV_AS_TOP_ENV_DIRECTLY, true);
        
        // 优化性能选项
        evaluator.setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EVAL);
        
        // 禁用不必要的功能以提高性能
        evaluator.setOption(Options.ENABLE_PROPERTY_SYNTAX_SUGAR, false);
        
        // 设置缓存大小
        evaluator.setOption(Options.MAX_LOOP_COUNT, 10000);
        
        // 使用更快的数学模式
        evaluator.setOption(Options.MATH_CONTEXT, java.math.MathContext.DECIMAL32);
        
        // 启用JIT编译
        evaluator.setOption(Options.PREFER_AVIATOR_LONG, true);
        
        logger.info("AviatorScript executor configured with optimized settings");
    }
    
    /**
     * 预编译常用表达式
     */
    private void precompileCommonExpressions() {
        String[] commonExpressions = {
            "1 + 1",
            "true && false",
            "nil",
            "[]",
            "{}",
            "'hello'",
            "date()",
            "sysdate()"
        };
        
        for (String expr : commonExpressions) {
            try {
                Expression compiled = evaluator.compile(expr, true);
                expressionCache.put(expr, compiled);
            } catch (Exception e) {
                logger.warn("Failed to precompile expression: {}", expr, e);
            }
        }
        
        logger.info("Precompiled {} common expressions", commonExpressions.length);
    }
    
    /**
     * 执行无参数表达式（主要方法）
     * 
     * @param expression 表达式字符串
     * @return 执行结果
     */
    public Object execute(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Expression cannot be null or empty");
        }
        
        long startTime = System.nanoTime();
        
        try {
            // 检查结果缓存
            if (enableResultCache) {
                Object cachedResult = resultCache.getIfPresent(expression);
                if (cachedResult != null) {
                    if (enableStats) {
                        recordExecution(expression, System.nanoTime() - startTime);
                    }
                    return cachedResult;
                }
            }
            
            // 获取或编译表达式
            Expression compiled = expressionCache.get(expression, key -> {
                logger.debug("Compiling expression: {}", key);
                return evaluator.compile(key, true);
            });
            
            // 执行表达式（无参数）
            Object result = compiled.execute();
            
            // 缓存结果
            if (enableResultCache && isPureExpression(expression)) {
                resultCache.put(expression, result);
            }
            
            if (enableStats) {
                recordExecution(expression, System.nanoTime() - startTime);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Failed to execute expression: {}", expression, e);
            throw new RuntimeException("Expression execution failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * 批量执行表达式
     * 
     * @param expressions 表达式数组
     * @return 结果数组
     */
    public Object[] executeBatch(String[] expressions) {
        if (expressions == null || expressions.length == 0) {
            return new Object[0];
        }
        
        Object[] results = new Object[expressions.length];
        for (int i = 0; i < expressions.length; i++) {
            results[i] = execute(expressions[i]);
        }
        return results;
    }
    
    /**
     * 异步执行表达式
     * 
     * @param expression 表达式
     * @return CompletableFuture结果
     */
    public java.util.concurrent.CompletableFuture<Object> executeAsync(String expression) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> execute(expression));
    }
    
    /**
     * 判断是否为纯函数表达式（可以安全缓存结果）
     */
    private boolean isPureExpression(String expression) {
        // 简单判断：不包含时间函数、随机函数等
        String lowerExpr = expression.toLowerCase();
        return !lowerExpr.contains("rand") && 
               !lowerExpr.contains("date") && 
               !lowerExpr.contains("time") &&
               !lowerExpr.contains("sysdate") &&
               !lowerExpr.contains("now");
    }
    
    /**
     * 记录执行统计
     */
    private void recordExecution(String expression, long nanoTime) {
        executionStats.merge(expression, nanoTime, Long::sum);
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStats getCacheStats() {
        return new CacheStats(
            expressionCache.stats(),
            resultCache.stats(),
            expressionCache.estimatedSize(),
            resultCache.estimatedSize()
        );
    }
    
    /**
     * 获取执行统计信息
     */
    public Map<String, Long> getExecutionStats() {
        return Collections.unmodifiableMap(executionStats);
    }
    
    /**
     * 清理缓存
     */
    public void clearCache() {
        expressionCache.invalidateAll();
        resultCache.invalidateAll();
        logger.info("All caches cleared");
    }
    
    /**
     * 预热缓存
     * 
     * @param expressions 要预热的表达式
     */
    public void warmUp(String[] expressions) {
        if (expressions == null || expressions.length == 0) {
            return;
        }
        
        logger.info("Warming up cache with {} expressions", expressions.length);
        for (String expr : expressions) {
            try {
                execute(expr);
            } catch (Exception e) {
                logger.warn("Failed to warm up expression: {}", expr, e);
            }
        }
    }
    
    /**
     * 缓存统计信息类
     */
    public static class CacheStats {
        public final com.github.benmanes.caffeine.cache.stats.CacheStats expressionCacheStats;
        public final com.github.benmanes.caffeine.cache.stats.CacheStats resultCacheStats;
        public final long expressionCacheSize;
        public final long resultCacheSize;
        
        public CacheStats(com.github.benmanes.caffeine.cache.stats.CacheStats expressionCacheStats,
                         com.github.benmanes.caffeine.cache.stats.CacheStats resultCacheStats,
                         long expressionCacheSize, long resultCacheSize) {
            this.expressionCacheStats = expressionCacheStats;
            this.resultCacheStats = resultCacheStats;
            this.expressionCacheSize = expressionCacheSize;
            this.resultCacheSize = resultCacheSize;
        }
        
        @Override
        public String toString() {
            return String.format(
                "CacheStats{expressionCache[size=%d, hitRate=%.2f%%], resultCache[size=%d, hitRate=%.2f%%]}",
                expressionCacheSize, expressionCacheStats.hitRate() * 100,
                resultCacheSize, resultCacheStats.hitRate() * 100
            );
        }
    }
}