package com.example.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import com.googlecode.aviator.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 优化的 AviatorScript 执行器
 * 针对不需要参数的表达式执行进行优化
 */
public class OptimizedAviatorExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(OptimizedAviatorExecutor.class);
    
    // 表达式缓存，避免重复编译
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();
    
    // 预编译的表达式结果缓存（针对无参数的常量表达式）
    private final Map<String, Object> resultCache = new ConcurrentHashMap<>();
    
    // 空参数映射，避免重复创建
    private static final Map<String, Object> EMPTY_ENV = Collections.emptyMap();
    
    // 单例模式
    private static volatile OptimizedAviatorExecutor instance;
    
    private OptimizedAviatorExecutor() {
        // 配置 AviatorEvaluator 优化选项
        configureAviator();
    }
    
    public static OptimizedAviatorExecutor getInstance() {
        if (instance == null) {
            synchronized (OptimizedAviatorExecutor.class) {
                if (instance == null) {
                    instance = new OptimizedAviatorExecutor();
                }
            }
        }
        return instance;
    }
    
    /**
     * 配置 AviatorEvaluator 以获得最佳性能
     */
    private void configureAviator() {
        // AviatorScript 5.4.1 的基本优化配置
        // 注意：某些高级选项可能在不同版本中有所不同
        
        logger.info("AviatorEvaluator 配置完成，使用默认优化设置");
    }
    
    /**
     * 执行无参数表达式（最优化版本）
     * 
     * @param expression 表达式字符串
     * @return 执行结果
     */
    public Object executeWithoutParams(String expression) {
        // 1. 首先检查结果缓存（针对常量表达式）
        Object cachedResult = resultCache.get(expression);
        if (cachedResult != null) {
            logger.debug("从结果缓存中获取表达式结果: {}", expression);
            return cachedResult;
        }
        
        // 2. 获取编译后的表达式
        Expression compiledExpr = getCompiledExpression(expression);
        
        // 3. 执行表达式
        Object result = compiledExpr.execute(EMPTY_ENV);
        
        // 4. 如果是常量表达式，缓存结果
        if (isConstantExpression(expression)) {
            resultCache.put(expression, result);
            logger.debug("缓存常量表达式结果: {} = {}", expression, result);
        }
        
        return result;
    }
    
    /**
     * 获取编译后的表达式（带缓存）
     */
    private Expression getCompiledExpression(String expressionStr) {
        return expressionCache.computeIfAbsent(expressionStr, expr -> {
            logger.debug("编译新表达式: {}", expr);
            return AviatorEvaluator.compile(expr);
        });
    }
    
    /**
     * 判断是否为常量表达式
     * 简单的启发式判断：不包含变量名的表达式
     */
    private boolean isConstantExpression(String expression) {
        // 简单判断：如果表达式只包含数字、运算符、括号和常见函数，认为是常量表达式
        return expression.matches("[\\d+\\-*/()\\s.]+") || 
               expression.matches(".*\\b(sqrt|pow|abs|sin|cos|tan)\\([^a-zA-Z_]*\\).*");
    }
    
    /**
     * 预编译常用表达式
     * 
     * @param expressions 表达式数组
     */
    public void precompileExpressions(String... expressions) {
        logger.info("开始预编译 {} 个表达式", expressions.length);
        
        for (String expr : expressions) {
            try {
                // 预编译表达式
                Expression compiled = AviatorEvaluator.compile(expr);
                expressionCache.put(expr, compiled);
                
                // 如果是常量表达式，也预计算结果
                if (isConstantExpression(expr)) {
                    Object result = compiled.execute(EMPTY_ENV);
                    resultCache.put(expr, result);
                }
                
                logger.debug("预编译完成: {}", expr);
            } catch (Exception e) {
                logger.warn("预编译表达式失败: {}, 错误: {}", expr, e.getMessage());
            }
        }
        
        logger.info("预编译完成，缓存了 {} 个表达式", expressionCache.size());
    }
    
    /**
     * 批量执行无参数表达式
     * 
     * @param expressions 表达式数组
     * @return 结果数组
     */
    public Object[] batchExecute(String... expressions) {
        Object[] results = new Object[expressions.length];
        
        for (int i = 0; i < expressions.length; i++) {
            results[i] = executeWithoutParams(expressions[i]);
        }
        
        return results;
    }
    
    /**
     * 清除缓存
     */
    public void clearCache() {
        expressionCache.clear();
        resultCache.clear();
        logger.info("缓存已清除");
    }
    
    /**
     * 获取缓存统计信息
     */
    public String getCacheStats() {
        return String.format("表达式缓存: %d, 结果缓存: %d", 
                           expressionCache.size(), resultCache.size());
    }
}