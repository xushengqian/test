package com.example.aviator;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 单元测试类
 */
public class OptimizedAviatorExecutorTest {
    
    private OptimizedAviatorExecutor executor;
    
    @Before
    public void setUp() {
        executor = new OptimizedAviatorExecutor(true, true, 100, 100);
    }
    
    @Test
    public void testSimpleExpression() {
        Object result = executor.execute("1 + 2");
        assertEquals(3L, result);
    }
    
    @Test
    public void testComplexExpression() {
        Object result = executor.execute("let a = 10; let b = 20; a * b");
        assertEquals(200L, result);
    }
    
    @Test
    public void testStringConcatenation() {
        Object result = executor.execute("'Hello' + ' ' + 'World'");
        assertEquals("Hello World", result);
    }
    
    @Test
    public void testBooleanExpression() {
        Object result = executor.execute("true && true");
        assertEquals(true, result);
        
        result = executor.execute("true && false");
        assertEquals(false, result);
    }
    
    @Test
    public void testMathExpression() {
        Object result = executor.execute("math.sqrt(16)");
        assertEquals(4.0, (Double) result, 0.001);
    }
    
    @Test
    public void testBatchExecution() {
        String[] expressions = {
            "1 + 1",
            "2 * 3",
            "'test'",
            "true"
        };
        
        Object[] results = executor.executeBatch(expressions);
        
        assertEquals(4, results.length);
        assertEquals(2L, results[0]);
        assertEquals(6L, results[1]);
        assertEquals("test", results[2]);
        assertEquals(true, results[3]);
    }
    
    @Test
    public void testAsyncExecution() throws ExecutionException, InterruptedException {
        CompletableFuture<Object> future = executor.executeAsync("1 + 2 + 3");
        Object result = future.get();
        assertEquals(6L, result);
    }
    
    @Test
    public void testCacheEffectiveness() {
        // 第一次执行
        long start = System.currentTimeMillis();
        executor.execute("math.pow(2, 10)");
        long firstTime = System.currentTimeMillis() - start;
        
        // 第二次执行（应该从缓存获取）
        start = System.currentTimeMillis();
        executor.execute("math.pow(2, 10)");
        long secondTime = System.currentTimeMillis() - start;
        
        // 缓存应该让第二次执行更快
        assertTrue("Cache should make second execution faster", secondTime <= firstTime);
    }
    
    @Test
    public void testCacheStats() {
        // 执行一些表达式
        executor.execute("1 + 1");
        executor.execute("1 + 1"); // 重复，应该命中缓存
        executor.execute("2 + 2");
        
        OptimizedAviatorExecutor.CacheStats stats = executor.getCacheStats();
        assertNotNull(stats);
        assertTrue(stats.expressionCacheSize > 0);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testNullExpression() {
        executor.execute(null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testEmptyExpression() {
        executor.execute("");
    }
    
    @Test
    public void testWarmUp() {
        String[] expressions = {
            "1 + 1",
            "2 * 2",
            "'test'"
        };
        
        executor.warmUp(expressions);
        
        // 验证预热后的表达式执行更快
        long start = System.nanoTime();
        executor.execute("1 + 1");
        long elapsed = System.nanoTime() - start;
        
        // 应该很快，因为已经在缓存中
        assertTrue("Warmed up expression should execute quickly", elapsed < 1_000_000); // < 1ms
    }
    
    @Test
    public void testClearCache() {
        executor.execute("1 + 1");
        OptimizedAviatorExecutor.CacheStats statsBefore = executor.getCacheStats();
        assertTrue(statsBefore.expressionCacheSize > 0);
        
        executor.clearCache();
        
        OptimizedAviatorExecutor.CacheStats statsAfter = executor.getCacheStats();
        assertEquals(0, statsAfter.expressionCacheSize);
    }
}