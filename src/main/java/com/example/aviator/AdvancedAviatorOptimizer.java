package com.example.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 高级AviatorScript优化器
 * 提供更高级的缓存策略、异步执行和性能监控
 */
public class AdvancedAviatorOptimizer {
    
    private static final Logger logger = LoggerFactory.getLogger(AdvancedAviatorOptimizer.class);
    
    // 表达式缓存
    private final Map<String, CachedExpression> expressionCache = new ConcurrentHashMap<>();
    
    // 执行统计
    private final AtomicLong totalExecutions = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    // 线程池用于异步执行
    private final ExecutorService executorService;
    
    // 缓存配置
    private final int maxCacheSize;
    private final long cacheExpireTimeMs;
    
    // 单例模式
    private static volatile AdvancedAviatorOptimizer instance;
    
    private AdvancedAviatorOptimizer() {
        this.maxCacheSize = 1000;
        this.cacheExpireTimeMs = TimeUnit.HOURS.toMillis(1);
        this.executorService = new ThreadPoolExecutor(
            2, 10, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadFactory() {
                private final AtomicLong threadNumber = new AtomicLong(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "AviatorExecutor-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            }
        );
        
        configureAviator();
        startCacheCleanupTask();
    }
    
    public static AdvancedAviatorOptimizer getInstance() {
        if (instance == null) {
            synchronized (AdvancedAviatorOptimizer.class) {
                if (instance == null) {
                    instance = new AdvancedAviatorOptimizer();
                }
            }
        }
        return instance;
    }
    
    /**
     * 配置AviatorScript
     */
    private void configureAviator() {
        // 启用所有优化选项
        AviatorEvaluator.setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EVAL);
        AviatorEvaluator.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, false);
        AviatorEvaluator.setOption(Options.ALWAYS_PARSE_INTEGRAL_NUMBER_INTO_DECIMAL, false);
        AviatorEvaluator.setOption(Options.COMPILE_OPTIMIZE_LEVEL, 3);
        
        // 启用常量折叠
        AviatorEvaluator.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, false);
        
        logger.info("高级AviatorScript优化器初始化完成");
    }
    
    /**
     * 执行表达式（同步）
     */
    public Object execute(String expression) {
        return execute(expression, null);
    }
    
    /**
     * 执行表达式（带参数）
     */
    public Object execute(String expression, Map<String, Object> params) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        
        long startTime = System.nanoTime();
        totalExecutions.incrementAndGet();
        
        try {
            CachedExpression cachedExpr = getOrCompileExpression(expression);
            Object result = cachedExpr.execute(params);
            
            long duration = System.nanoTime() - startTime;
            logger.debug("表达式执行完成: {} (耗时: {}ns)", expression, duration);
            
            return result;
            
        } catch (Exception e) {
            logger.error("执行表达式失败: {}", expression, e);
            throw new RuntimeException("表达式执行失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 异步执行表达式
     */
    public CompletableFuture<Object> executeAsync(String expression) {
        return executeAsync(expression, null);
    }
    
    /**
     * 异步执行表达式（带参数）
     */
    public CompletableFuture<Object> executeAsync(String expression, Map<String, Object> params) {
        if (expression == null || expression.trim().isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("表达式不能为空"));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(expression, params);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }
    
    /**
     * 批量异步执行
     */
    public CompletableFuture<Object[]> executeBatchAsync(String[] expressions) {
        return executeBatchAsync(expressions, null);
    }
    
    /**
     * 批量异步执行（带参数）
     */
    public CompletableFuture<Object[]> executeBatchAsync(String[] expressions, Map<String, Object> params) {
        if (expressions == null || expressions.length == 0) {
            return CompletableFuture.completedFuture(new Object[0]);
        }
        
        CompletableFuture<Object>[] futures = new CompletableFuture[expressions.length];
        
        for (int i = 0; i < expressions.length; i++) {
            futures[i] = executeAsync(expressions[i], params);
        }
        
        return CompletableFuture.allOf(futures)
                .thenApply(v -> {
                    Object[] results = new Object[expressions.length];
                    for (int i = 0; i < futures.length; i++) {
                        try {
                            results[i] = futures[i].get();
                        } catch (Exception e) {
                            logger.error("批量执行第{}个表达式失败: {}", i + 1, expressions[i], e);
                            results[i] = null;
                        }
                    }
                    return results;
                });
    }
    
    /**
     * 获取或编译表达式（带高级缓存）
     */
    private CachedExpression getOrCompileExpression(String expression) {
        CachedExpression cached = expressionCache.get(expression);
        
        if (cached != null && !cached.isExpired()) {
            cacheHits.incrementAndGet();
            cached.updateAccessTime();
            return cached;
        }
        
        cacheMisses.incrementAndGet();
        
        // 检查缓存大小限制
        if (expressionCache.size() >= maxCacheSize) {
            evictExpiredExpressions();
        }
        
        try {
            Expression compiled = AviatorEvaluator.compile(expression);
            CachedExpression newCached = new CachedExpression(compiled, cacheExpireTimeMs);
            expressionCache.put(expression, newCached);
            
            logger.debug("编译并缓存表达式: {}", expression);
            return newCached;
            
        } catch (Exception e) {
            logger.error("编译表达式失败: {}", expression, e);
            throw new RuntimeException("表达式编译失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 清理过期表达式
     */
    private void evictExpiredExpressions() {
        long currentTime = System.currentTimeMillis();
        expressionCache.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(currentTime);
            if (expired) {
                logger.debug("移除过期表达式: {}", entry.getKey());
            }
            return expired;
        });
    }
    
    /**
     * 启动缓存清理任务
     */
    private void startCacheCleanupTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AviatorCacheCleanup");
            t.setDaemon(true);
            return t;
        });
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                evictExpiredExpressions();
                logger.debug("缓存清理完成，当前缓存大小: {}", expressionCache.size());
            } catch (Exception e) {
                logger.error("缓存清理任务执行失败", e);
            }
        }, 5, 5, TimeUnit.MINUTES);
    }
    
    /**
     * 预热缓存
     */
    public void warmupCache(String[] expressions) {
        if (expressions == null || expressions.length == 0) {
            return;
        }
        
        logger.info("开始预热表达式缓存，共{}个表达式", expressions.length);
        
        for (String expression : expressions) {
            try {
                getOrCompileExpression(expression);
            } catch (Exception e) {
                logger.warn("预热表达式失败: {}", expression, e);
            }
        }
        
        logger.info("表达式缓存预热完成，当前缓存大小: {}", expressionCache.size());
    }
    
    /**
     * 获取性能统计
     */
    public Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("totalExecutions", totalExecutions.get());
        stats.put("cacheHits", cacheHits.get());
        stats.put("cacheMisses", cacheMisses.get());
        stats.put("cacheSize", expressionCache.size());
        stats.put("hitRate", calculateHitRate());
        return stats;
    }
    
    /**
     * 计算缓存命中率
     */
    private double calculateHitRate() {
        long total = cacheHits.get() + cacheMisses.get();
        return total > 0 ? (double) cacheHits.get() / total : 0.0;
    }
    
    /**
     * 清空缓存
     */
    public void clearCache() {
        int size = expressionCache.size();
        expressionCache.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        totalExecutions.set(0);
        logger.info("清空表达式缓存，清除了{}个表达式", size);
    }
    
    /**
     * 关闭优化器
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("高级AviatorScript优化器已关闭");
    }
    
    /**
     * 缓存的表达式包装类
     */
    private static class CachedExpression {
        private final Expression expression;
        private final long expireTime;
        private volatile long lastAccessTime;
        
        public CachedExpression(Expression expression, long expireTimeMs) {
            this.expression = expression;
            this.expireTime = System.currentTimeMillis() + expireTimeMs;
            this.lastAccessTime = System.currentTimeMillis();
        }
        
        public Object execute(Map<String, Object> params) {
            return params != null ? expression.execute(params) : expression.execute();
        }
        
        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }
        
        public boolean isExpired(long currentTime) {
            return currentTime > expireTime;
        }
        
        public void updateAccessTime() {
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
}