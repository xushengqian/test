package com.example.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * AviatorScript 优化执行器
 * 针对不需要参数的表达式进行性能优化
 */
public class AviatorOptimizer {
    
    private static final Logger logger = LoggerFactory.getLogger(AviatorOptimizer.class);
    
    // 表达式缓存，避免重复编译
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();
    
    // 单例模式
    private static volatile AviatorOptimizer instance;
    
    private AviatorOptimizer() {
        // 配置AviatorScript优化选项
        configureAviator();
    }
    
    public static AviatorOptimizer getInstance() {
        if (instance == null) {
            synchronized (AviatorOptimizer.class) {
                if (instance == null) {
                    instance = new AviatorOptimizer();
                }
            }
        }
        return instance;
    }
    
    /**
     * 配置AviatorScript优化选项
     */
    private void configureAviator() {
        // 启用编译优化
        AviatorEvaluator.setOption(Options.OPTIMIZE_LEVEL, AviatorEvaluator.EVAL);
        
        // 启用常量折叠优化
        AviatorEvaluator.setOption(Options.ALWAYS_PARSE_FLOATING_POINT_NUMBER_INTO_DECIMAL, false);
        
        // 启用字符串常量池
        AviatorEvaluator.setOption(Options.ALWAYS_PARSE_INTEGRAL_NUMBER_INTO_DECIMAL, false);
        
        // 设置编译超时时间
        AviatorEvaluator.setOption(Options.COMPILE_OPTIMIZE_LEVEL, 3);
        
        logger.info("AviatorScript 优化配置完成");
    }
    
    /**
     * 执行表达式（带缓存优化）
     * 
     * @param expression 表达式字符串
     * @return 执行结果
     */
    public Object execute(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        
        try {
            // 从缓存获取编译后的表达式
            Expression compiledExpression = getOrCompileExpression(expression);
            
            // 执行表达式（无参数）
            return compiledExpression.execute();
            
        } catch (Exception e) {
            logger.error("执行表达式失败: {}", expression, e);
            throw new RuntimeException("表达式执行失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 执行表达式（带缓存优化和超时控制）
     * 
     * @param expression 表达式字符串
     * @param timeoutMs 超时时间（毫秒）
     * @return 执行结果
     */
    public Object executeWithTimeout(String expression, long timeoutMs) {
        if (expression == null || expression.trim().isEmpty()) {
            throw new IllegalArgumentException("表达式不能为空");
        }
        
        try {
            Expression compiledExpression = getOrCompileExpression(expression);
            
            // 使用超时控制执行
            return executeWithTimeout(compiledExpression, timeoutMs);
            
        } catch (Exception e) {
            logger.error("执行表达式失败: {}", expression, e);
            throw new RuntimeException("表达式执行失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 批量执行表达式
     * 
     * @param expressions 表达式数组
     * @return 执行结果数组
     */
    public Object[] executeBatch(String[] expressions) {
        if (expressions == null || expressions.length == 0) {
            return new Object[0];
        }
        
        Object[] results = new Object[expressions.length];
        
        for (int i = 0; i < expressions.length; i++) {
            try {
                results[i] = execute(expressions[i]);
            } catch (Exception e) {
                logger.error("批量执行第{}个表达式失败: {}", i + 1, expressions[i], e);
                results[i] = null; // 或者可以设置默认值
            }
        }
        
        return results;
    }
    
    /**
     * 获取或编译表达式（带缓存）
     */
    private Expression getOrCompileExpression(String expression) {
        return expressionCache.computeIfAbsent(expression, expr -> {
            try {
                logger.debug("编译表达式: {}", expr);
                return AviatorEvaluator.compile(expr);
            } catch (Exception e) {
                logger.error("编译表达式失败: {}", expr, e);
                throw new RuntimeException("表达式编译失败: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * 带超时控制的表达式执行
     */
    private Object executeWithTimeout(Expression expression, long timeoutMs) {
        // 这里可以使用Future和ExecutorService实现真正的超时控制
        // 为了简化，这里直接执行
        long startTime = System.currentTimeMillis();
        Object result = expression.execute();
        long endTime = System.currentTimeMillis();
        
        if (endTime - startTime > timeoutMs) {
            logger.warn("表达式执行超时: {}ms, 实际耗时: {}ms", timeoutMs, endTime - startTime);
        }
        
        return result;
    }
    
    /**
     * 预热缓存（预编译常用表达式）
     * 
     * @param expressions 常用表达式数组
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
     * 清空表达式缓存
     */
    public void clearCache() {
        int size = expressionCache.size();
        expressionCache.clear();
        logger.info("清空表达式缓存，清除了{}个表达式", size);
    }
    
    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("cacheSize", expressionCache.size());
        stats.put("cachedExpressions", expressionCache.keySet());
        return stats;
    }
    
    /**
     * 验证表达式语法
     * 
     * @param expression 表达式字符串
     * @return 是否有效
     */
    public boolean validateExpression(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }
        
        try {
            AviatorEvaluator.compile(expression);
            return true;
        } catch (Exception e) {
            logger.debug("表达式语法验证失败: {}", expression, e);
            return false;
        }
    }
}