package com.example.aviator;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.Expression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 表达式池管理器
 * 预编译和池化常用表达式，减少编译开销
 */
public class ExpressionPool {
    
    private static final Logger logger = LoggerFactory.getLogger(ExpressionPool.class);
    
    // 表达式池，每个表达式字符串对应一个编译后的 Expression 队列
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Expression>> expressionPools = new ConcurrentHashMap<>();
    
    // 池统计信息
    private final ConcurrentHashMap<String, AtomicInteger> poolSizes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> hitCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> missCounts = new ConcurrentHashMap<>();
    
    // 默认池大小
    private static final int DEFAULT_POOL_SIZE = 10;
    
    // 单例
    private static volatile ExpressionPool instance;
    
    private ExpressionPool() {}
    
    public static ExpressionPool getInstance() {
        if (instance == null) {
            synchronized (ExpressionPool.class) {
                if (instance == null) {
                    instance = new ExpressionPool();
                }
            }
        }
        return instance;
    }
    
    /**
     * 初始化表达式池
     * 
     * @param expressionStr 表达式字符串
     * @param poolSize 池大小
     */
    public void initPool(String expressionStr, int poolSize) {
        logger.info("初始化表达式池: {} (大小: {})", expressionStr, poolSize);
        
        ConcurrentLinkedQueue<Expression> pool = new ConcurrentLinkedQueue<>();
        
        // 预编译指定数量的表达式实例
        for (int i = 0; i < poolSize; i++) {
            try {
                Expression expr = AviatorEvaluator.compile(expressionStr);
                pool.offer(expr);
            } catch (Exception e) {
                logger.error("编译表达式失败: {}", expressionStr, e);
                return;
            }
        }
        
        expressionPools.put(expressionStr, pool);
        poolSizes.put(expressionStr, new AtomicInteger(poolSize));
        hitCounts.put(expressionStr, new AtomicInteger(0));
        missCounts.put(expressionStr, new AtomicInteger(0));
        
        logger.info("表达式池初始化完成: {}", expressionStr);
    }
    
    /**
     * 使用默认大小初始化表达式池
     */
    public void initPool(String expressionStr) {
        initPool(expressionStr, DEFAULT_POOL_SIZE);
    }
    
    /**
     * 批量初始化表达式池
     */
    public void initPools(String... expressions) {
        for (String expr : expressions) {
            initPool(expr);
        }
    }
    
    /**
     * 从池中获取表达式
     * 
     * @param expressionStr 表达式字符串
     * @return 编译后的表达式，如果池中没有则现场编译
     */
    public Expression borrowExpression(String expressionStr) {
        ConcurrentLinkedQueue<Expression> pool = expressionPools.get(expressionStr);
        
        if (pool != null) {
            Expression expr = pool.poll();
            if (expr != null) {
                // 池命中
                hitCounts.get(expressionStr).incrementAndGet();
                logger.debug("从池中获取表达式: {}", expressionStr);
                return expr;
            }
        }
        
        // 池未命中，现场编译
        missCounts.computeIfAbsent(expressionStr, k -> new AtomicInteger(0)).incrementAndGet();
        logger.debug("池未命中，现场编译表达式: {}", expressionStr);
        
        try {
            return AviatorEvaluator.compile(expressionStr);
        } catch (Exception e) {
            logger.error("编译表达式失败: {}", expressionStr, e);
            throw new RuntimeException("表达式编译失败: " + expressionStr, e);
        }
    }
    
    /**
     * 将表达式归还到池中
     * 
     * @param expressionStr 表达式字符串
     * @param expression 表达式实例
     */
    public void returnExpression(String expressionStr, Expression expression) {
        ConcurrentLinkedQueue<Expression> pool = expressionPools.get(expressionStr);
        
        if (pool != null) {
            // 检查池大小，避免无限增长
            AtomicInteger currentSize = poolSizes.get(expressionStr);
            if (currentSize != null && pool.size() < currentSize.get() * 2) {
                pool.offer(expression);
                logger.debug("表达式归还到池: {}", expressionStr);
            } else {
                logger.debug("池已满，丢弃表达式: {}", expressionStr);
            }
        }
    }
    
    /**
     * 执行表达式（使用池优化）
     * 
     * @param expressionStr 表达式字符串
     * @return 执行结果
     */
    public Object execute(String expressionStr) {
        Expression expr = borrowExpression(expressionStr);
        
        try {
            return expr.execute();
        } finally {
            returnExpression(expressionStr, expr);
        }
    }
    
    /**
     * 获取池统计信息
     */
    public String getPoolStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("表达式池统计信息:\n");
        
        for (String expr : expressionPools.keySet()) {
            int poolSize = expressionPools.get(expr).size();
            int hits = hitCounts.get(expr).get();
            int misses = missCounts.get(expr).get();
            double hitRate = hits + misses > 0 ? (double) hits / (hits + misses) * 100 : 0;
            
            stats.append(String.format("表达式: %s\n", expr));
            stats.append(String.format("  池大小: %d, 命中: %d, 未命中: %d, 命中率: %.2f%%\n", 
                                     poolSize, hits, misses, hitRate));
        }
        
        return stats.toString();
    }
    
    /**
     * 清空所有池
     */
    public void clearAllPools() {
        expressionPools.clear();
        poolSizes.clear();
        hitCounts.clear();
        missCounts.clear();
        logger.info("所有表达式池已清空");
    }
    
    /**
     * 预热池（执行一次以触发 JIT 编译）
     */
    public void warmUp(String... expressions) {
        logger.info("开始预热表达式池...");
        
        for (String expr : expressions) {
            try {
                // 多次执行以触发 JIT 优化
                for (int i = 0; i < 1000; i++) {
                    execute(expr);
                }
                logger.debug("表达式预热完成: {}", expr);
            } catch (Exception e) {
                logger.warn("表达式预热失败: {}", expr, e);
            }
        }
        
        logger.info("表达式池预热完成");
    }
}