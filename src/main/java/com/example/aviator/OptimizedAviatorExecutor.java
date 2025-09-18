package com.example.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import com.googlecode.aviator.runtime.function.AbstractFunction;
import com.googlecode.aviator.runtime.type.AviatorObject;
import com.googlecode.aviator.runtime.type.AviatorString;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 优化的AviatorScript表达式执行器
 * 针对不需要参数的表达式进行特殊优化
 */
public class OptimizedAviatorExecutor {
    
    // 表达式缓存，避免重复编译
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();
    
    // 读写锁，支持并发读取，独占写入
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    
    // 缓存大小限制
    private static final int MAX_CACHE_SIZE = 1000;
    
    static {
        // 配置AviatorScript优化选项
        AviatorEvaluator.setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EVAL);
        AviatorEvaluator.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, false);
        AviatorEvaluator.setOption(Options.ALWAYS_PARSE_INTEGRAL_NUMBER_INTO_DECIMAL, false);
        AviatorEvaluator.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, false);
        
        // 注册常用函数
        registerCommonFunctions();
    }
    
    /**
     * 执行表达式（无参数版本）
     * 这是最常用的优化场景
     */
    public Object execute(String expression) {
        return execute(expression, null);
    }
    
    /**
     * 执行表达式（带参数版本）
     */
    public Object execute(String expression, Map<String, Object> env) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("Expression cannot be null or empty");
        }
        
        // 尝试从缓存获取已编译的表达式
        Expression compiledExpression = getCachedExpression(expression);
        
        if (compiledExpression == null) {
            // 缓存未命中，编译并缓存
            compiledExpression = compileAndCache(expression);
        }
        
        // 执行表达式
        try {
            return compiledExpression.execute(env);
        } catch (Exception e) {
            // 如果执行失败，可能是表达式有变化，重新编译
            compiledExpression = compileAndCache(expression);
            return compiledExpression.execute(env);
        }
    }
    
    /**
     * 获取缓存的表达式
     */
    private Expression getCachedExpression(String expression) {
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
    private Expression compileAndCache(String expression) {
        cacheLock.writeLock().lock();
        try {
            // 双重检查，避免重复编译
            Expression cached = expressionCache.get(expression);
            if (cached != null) {
                return cached;
            }
            
            // 检查缓存大小限制
            if (expressionCache.size() >= MAX_CACHE_SIZE) {
                clearOldestCache();
            }
            
            // 编译表达式
            Expression compiled = AviatorEvaluator.compile(expression, true);
            expressionCache.put(expression, compiled);
            
            return compiled;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }
    
    /**
     * 清理最旧的缓存项（简单的LRU策略）
     */
    private void clearOldestCache() {
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
     * 获取缓存大小
     */
    public int getCacheSize() {
        cacheLock.readLock().lock();
        try {
            return expressionCache.size();
        } finally {
            cacheLock.readLock().unlock();
        }
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
    private static class MathFunction extends AbstractFunction {
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
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
            Object valueObj = arg1.getValue(env);
            double value;
            if (valueObj instanceof Number) {
                value = ((Number) valueObj).doubleValue();
            } else {
                value = Double.parseDouble(valueObj.toString());
            }
            return new AviatorString(String.valueOf(function.apply(value)));
        }
    }
    
    /**
     * 字符串函数包装器
     */
    private static class StringFunction extends AbstractFunction {
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
        public AviatorObject call(Map<String, Object> env, AviatorObject arg1) {
            String value = arg1.getValue(env).toString();
            return new AviatorString(function.apply(value));
        }
    }
}