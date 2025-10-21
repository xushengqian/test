package com.example.concurrent.db;

import com.example.concurrent.db.utils.OptimalThreadCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 最优线程数计算器测试
 */
public class OptimalThreadCalculatorTest {
    
    @Test
    @DisplayName("测试CPU密集型任务线程数计算")
    public void testCalculateForCpuIntensive() {
        int threads = OptimalThreadCalculator.calculateForCpuIntensive();
        int expectedThreads = Runtime.getRuntime().availableProcessors() + 1;
        
        assertEquals(expectedThreads, threads);
        assertTrue(threads > 0);
        System.out.println("CPU密集型任务线程数: " + threads);
    }
    
    @Test
    @DisplayName("测试IO密集型任务线程数计算")
    public void testCalculateForIoIntensive() {
        int threads = OptimalThreadCalculator.calculateForIoIntensive();
        int expectedThreads = Runtime.getRuntime().availableProcessors() * 2;
        
        assertEquals(expectedThreads, threads);
        assertTrue(threads > 0);
        System.out.println("IO密集型任务线程数: " + threads);
    }
    
    @Test
    @DisplayName("测试阻塞系数计算")
    public void testCalculateWithBlockingCoefficient() {
        // 测试正常值
        double blockingCoefficient = 0.8;
        int threads = OptimalThreadCalculator.calculateWithBlockingCoefficient(blockingCoefficient);
        
        assertTrue(threads > 0);
        assertTrue(threads >= 2); // 最小线程数
        System.out.println("阻塞系数" + blockingCoefficient + "时线程数: " + threads);
        
        // 测试边界值
        int lowBlockingThreads = OptimalThreadCalculator.calculateWithBlockingCoefficient(0.1);
        int highBlockingThreads = OptimalThreadCalculator.calculateWithBlockingCoefficient(0.95);
        
        assertTrue(lowBlockingThreads < highBlockingThreads);
    }
    
    @Test
    @DisplayName("测试非法阻塞系数")
    public void testInvalidBlockingCoefficient() {
        assertThrows(IllegalArgumentException.class, () -> 
            OptimalThreadCalculator.calculateWithBlockingCoefficient(-0.1));
        
        assertThrows(IllegalArgumentException.class, () -> 
            OptimalThreadCalculator.calculateWithBlockingCoefficient(1.0));
        
        assertThrows(IllegalArgumentException.class, () -> 
            OptimalThreadCalculator.calculateWithBlockingCoefficient(1.5));
    }
    
    @Test
    @DisplayName("测试等待时间计算")
    public void testCalculateWithWaitTime() {
        long waitTime = 100;
        long computeTime = 20;
        
        int threads = OptimalThreadCalculator.calculateWithWaitTime(waitTime, computeTime);
        
        assertTrue(threads > 0);
        assertTrue(threads >= 2); // 最小线程数
        
        // 等待时间越长，线程数应该越多
        int moreWaitThreads = OptimalThreadCalculator.calculateWithWaitTime(200, computeTime);
        assertTrue(moreWaitThreads >= threads);
        
        System.out.println("等待时间" + waitTime + "ms, 计算时间" + computeTime + "ms时线程数: " + threads);
    }
    
    @Test
    @DisplayName("测试非法等待时间参数")
    public void testInvalidWaitTimeParams() {
        assertThrows(IllegalArgumentException.class, () -> 
            OptimalThreadCalculator.calculateWithWaitTime(100, 0));
        
        assertThrows(IllegalArgumentException.class, () -> 
            OptimalThreadCalculator.calculateWithWaitTime(100, -1));
    }
    
    @Test
    @DisplayName("测试数据库查询线程数计算")
    public void testCalculateForDatabaseQuery() {
        int connectionPoolSize = 20;
        long queryResponseTime = 100;
        long processingTime = 10;
        
        int threads = OptimalThreadCalculator.calculateForDatabaseQuery(
            connectionPoolSize, queryResponseTime, processingTime);
        
        assertTrue(threads > 0);
        assertTrue(threads <= connectionPoolSize); // 不应超过连接池大小
        assertTrue(threads >= 2); // 最小线程数
        
        System.out.println("数据库查询线程数: " + threads + 
            " (连接池: " + connectionPoolSize + ")");
    }
    
    @Test
    @DisplayName("测试系统负载调整")
    public void testCalculateWithSystemLoad() {
        int baseThreads = 10;
        
        // 低负载
        int lowLoadThreads = OptimalThreadCalculator.calculateWithSystemLoad(baseThreads, 0.2);
        assertTrue(lowLoadThreads <= baseThreads);
        assertTrue(lowLoadThreads >= 2);
        
        // 高负载
        int highLoadThreads = OptimalThreadCalculator.calculateWithSystemLoad(baseThreads, 0.9);
        assertTrue(highLoadThreads < lowLoadThreads);
        assertTrue(highLoadThreads >= 2);
        
        System.out.println("基础线程数: " + baseThreads);
        System.out.println("低负载(0.2)线程数: " + lowLoadThreads);
        System.out.println("高负载(0.9)线程数: " + highLoadThreads);
    }
    
    @Test
    @DisplayName("测试非法系统负载参数")
    public void testInvalidSystemLoad() {
        assertThrows(IllegalArgumentException.class, () -> 
            OptimalThreadCalculator.calculateWithSystemLoad(10, -0.1));
        
        assertThrows(IllegalArgumentException.class, () -> 
            OptimalThreadCalculator.calculateWithSystemLoad(10, 1.1));
    }
    
    @Test
    @DisplayName("测试推荐配置获取")
    public void testGetRecommendedConfig() {
        for (OptimalThreadCalculator.TaskType taskType : OptimalThreadCalculator.TaskType.values()) {
            OptimalThreadCalculator.ThreadPoolConfig config = 
                OptimalThreadCalculator.getRecommendedConfig(taskType);
            
            assertNotNull(config);
            assertTrue(config.getCorePoolSize() > 0);
            assertTrue(config.getMaximumPoolSize() >= config.getCorePoolSize());
            assertTrue(config.getQueueCapacity() > 0);
            assertTrue(config.getKeepAliveSeconds() > 0);
            
            System.out.println(taskType.getDescription() + " 配置: " + config);
        }
    }
    
    @Test
    @DisplayName("测试CPU核心数获取")
    public void testGetCpuCores() {
        int cores = OptimalThreadCalculator.getCpuCores();
        
        assertTrue(cores > 0);
        assertEquals(Runtime.getRuntime().availableProcessors(), cores);
        
        System.out.println("系统CPU核心数: " + cores);
    }
}