package com.example.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 专门针对无参数表达式的优化器
 * 提供更高效的执行策略
 */
public class NoParamExpressionOptimizer {
    
    // 表达式缓存
    private final ConcurrentHashMap<String, CachedExpression> expressionCache = new ConcurrentHashMap<>();
    
    // 读写锁
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    // 统计信息
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong totalExecutions = new AtomicLong(0);
    
    // 配置参数
    private static final int MAX_CACHE_SIZE = 2000;
    private static final long CACHE_EXPIRE_TIME = 30 * 60 * 1000; // 30分钟
    
    static {
        // 针对无参数表达式进行优化配置
        AviatorEvaluator.setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EVAL);
        AviatorEvaluator.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, false);
        AviatorEvaluator.setOption(Options.ALWAYS_PARSE_INTEGRAL_NUMBER_INTO_DECIMAL, false);
        
        // 注册常用函数
        registerCommonFunctions();
    }
    
    /**
     * 注册常用函数
     */
    private static void registerCommonFunctions() {
        // 注册数学函数
        AviatorEvaluator.addFunction(new MathFunction("sqrt", Math::sqrt));
        AviatorEvaluator.addFunction(new MathFunction("sin", Math::sin));
        AviatorEvaluator.addFunction(new MathFunction("cos", Math::cos));
        AviatorEvaluator.addFunction(new MathFunction("tan", Math::tan));
        AviatorEvaluator.addFunction(new MathFunction("log", Math::log));
        AviatorEvaluator.addFunction(new MathFunction("abs", Math::abs));
        
        // 注册字符串函数
        AviatorEvaluator.addFunction(new StringFunction("upper", String::toUpperCase));
        AviatorEvaluator.addFunction(new StringFunction("lower", String::toLowerCase));
        AviatorEvaluator.addFunction(new StringFunction("trim", String::trim));
    }
    
    /**
     * 数学函数包装器
     */
    private static class MathFunction extends com.googlecode.aviator.runtime.function.AbstractFunction {
        private final String name;
        private final java.util.function.Function<Double, Double> function;
        
        public MathFunction(String name, java.util.function.Function<Double, Double> function) {
            this.name = name;
            this.function = function;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public com.googlecode.aviator.runtime.type.AviatorObject call(java.util.Map<String, Object> env, com.googlecode.aviator.runtime.type.AviatorObject arg1) {
            Object valueObj = arg1.getValue(env);
            double value;
            if (valueObj instanceof Number) {
                value = ((Number) valueObj).doubleValue();
            } else {
                value = Double.parseDouble(valueObj.toString());
            }
            return new com.googlecode.aviator.runtime.type.AviatorString(String.valueOf(function.apply(value)));
        }
    }
    
    /**
     * 字符串函数包装器
     */
    private static class StringFunction extends com.googlecode.aviator.runtime.function.AbstractFunction {
        private final String name;
        private final java.util.function.Function<String, String> function;
        
        public StringFunction(String name, java.util.function.Function<String, String> function) {
            this.name = name;
            this.function = function;
        }
        
        @Override
        public String getName() {
            return name;
        }
        
        @Override
        public com.googlecode.aviator.runtime.type.AviatorObject call(java.util.Map<String, Object> env, com.googlecode.aviator.runtime.type.AviatorObject arg1) {
            String value = arg1.getValue(env).toString();
            return new com.googlecode.aviator.runtime.type.AviatorString(function.apply(value));
        }
    }
    
    /**
     * 执行无参数表达式（主要优化方法）
     */
    public Object execute(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Expression cannot be null or empty");
        }
        
        totalExecutions.incrementAndGet();
        
        // 尝试从缓存获取
        CachedExpression cached = getCachedExpression(expression);
        
        if (cached != null && !cached.isExpired()) {
            cacheHits.incrementAndGet();
            return cached.execute();
        }
        
        // 缓存未命中或已过期
        cacheMisses.incrementAndGet();
        cached = compileAndCache(expression);
        return cached.execute();
    }
    
    /**
     * 批量执行表达式
     */
    public Object[] executeBatch(String[] expressions) {
        Object[] results = new Object[expressions.length];
        
        for (int i = 0; i < expressions.length; i++) {
            results[i] = execute(expressions[i]);
        }
        
        return results;
    }
    
    /**
     * 预编译表达式（预热缓存）
     */
    public void precompile(String[] expressions) {
        for (String expression : expressions) {
            compileAndCache(expression);
        }
    }
    
    /**
     * 获取缓存的表达式
     */
    private CachedExpression getCachedExpression(String expression) {
        cacheLock.readLock().lock();
        try {
            return expressionCache.get(expression);
        } finally {
            cacheLock.readLock().unlock();
        }
    }
    
    /**
     * 编译并缓存表达式
     */
    private CachedExpression compileAndCache(String expression) {
        cacheLock.writeLock().lock();
        try {
            // 双重检查
            CachedExpression existing = expressionCache.get(expression);
            if (existing != null && !existing.isExpired()) {
                return existing;
            }
            
            // 检查缓存大小
            if (expressionCache.size() >= MAX_CACHE_SIZE) {
                evictExpiredEntries();
                if (expressionCache.size() >= MAX_CACHE_SIZE) {
                    evictOldestEntry();
                }
            }
            
            // 编译表达式
            Expression compiled = AviatorEvaluator.compile(expression, true);
            CachedExpression cached = new CachedExpression(compiled, System.currentTimeMillis());
            expressionCache.put(expression, cached);
            
            return cached;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * 清理过期条目
     */
    private void evictExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        expressionCache.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTime));
    }
    
    /**
     * 清理最旧的条目
     */
    private void evictOldestEntry() {
        if (!expressionCache.isEmpty()) {
            String oldestKey = expressionCache.keySet().iterator().next();
            expressionCache.remove(oldestKey);
        }
    }
    
    /**
     * 清空所有缓存
     */
    public void clearCache() {
        cacheLock.writeLock().lock();
        try {
            expressionCache.clear();
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public CacheStatistics getStatistics() {
        return new CacheStatistics(
            expressionCache.size(),
            cacheHits.get(),
            cacheMisses.get(),
            totalExecutions.get()
        );
    }
    
    /**
     * 缓存的表达式包装类
     */
    private static class CachedExpression {
        private final Expression expression;
        private final long createTime;
        
        public CachedExpression(Expression expression, long createTime) {
            this.expression = expression;
            this.createTime = createTime;
        }
        
        public Object execute() {
            return expression.execute();
        }
        
        public boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }
        
        public boolean isExpired(long currentTime) {
            return (currentTime - createTime) > CACHE_EXPIRE_TIME;
        }
    }
    
    /**
     * 缓存统计信息
     */
    public static class CacheStatistics {
        private final int cacheSize;
        private final long cacheHits;
        private final long cacheMisses;
        private final long totalExecutions;
        
        public CacheStatistics(int cacheSize, long cacheHits, long cacheMisses, long totalExecutions) {
            this.cacheSize = cacheSize;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.totalExecutions = totalExecutions;
        }
        
        public int getCacheSize() { return cacheSize; }
        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }
        public long getTotalExecutions() { return totalExecutions; }
        
        public double getHitRate() {
            long total = cacheHits + cacheMisses;
            return total > 0 ? (double) cacheHits / total : 0.0;
        }
        
        @Override
        public String toString() {
            return String.format("CacheStatistics{cacheSize=%d, hits=%d, misses=%d, total=%d, hitRate=%.2f%%}",
                cacheSize, cacheHits, cacheMisses, totalExecutions, getHitRate() * 100);
        }
    }
}