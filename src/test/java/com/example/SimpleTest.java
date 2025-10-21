package com.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 简单测试类，验证基本功能
 */
public class SimpleTest {

    @Test
    public void testBasicFunctionality() {
        // 测试CPU核心数获取
        int cpuCores = Runtime.getRuntime().availableProcessors();
        assertTrue(cpuCores > 0, "CPU核心数应该大于0");
        
        // 测试线程池大小计算
        int maxPoolSize = cpuCores * 2;
        assertTrue(maxPoolSize >= cpuCores, "最大线程池大小应该不小于CPU核心数");
        
        System.out.println("CPU核心数: " + cpuCores);
        System.out.println("建议的最大线程池大小: " + maxPoolSize);
        
        // 测试内存信息
        long maxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        assertTrue(maxMemory > 0, "最大内存应该大于0");
        
        System.out.println("最大可用内存: " + maxMemory + " MB");
    }

    @Test
    public void testThreadPoolCalculation() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        // 测试不同类型应用的线程池配置
        
        // CPU密集型
        int cpuIntensiveThreads = cpuCores;
        assertEquals(cpuCores, cpuIntensiveThreads);
        
        // I/O密集型（数据库查询）
        int ioIntensiveThreads = cpuCores * 2;
        assertEquals(cpuCores * 2, ioIntensiveThreads);
        
        // 混合型
        int mixedThreads = (int)(cpuCores * 1.5);
        assertTrue(mixedThreads >= cpuCores && mixedThreads <= cpuCores * 2);
        
        System.out.println("=== 线程池配置建议 ===");
        System.out.println("CPU密集型应用: " + cpuIntensiveThreads + " 个线程");
        System.out.println("I/O密集型应用: " + ioIntensiveThreads + " 个线程");
        System.out.println("混合型应用: " + mixedThreads + " 个线程");
    }
    
    @Test
    public void testDatabaseConnectionPoolSizing() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        // 数据库连接池大小计算
        int effectiveDiskCount = 2; // 假设有2个有效磁盘
        int dbPoolSize = cpuCores * 2 + effectiveDiskCount;
        
        assertTrue(dbPoolSize > cpuCores, "数据库连接池大小应该大于CPU核心数");
        
        // 线程池大小不应超过连接池大小的80%
        int threadPoolSize = (int)(dbPoolSize * 0.8);
        assertTrue(threadPoolSize <= dbPoolSize, "线程池大小不应超过连接池大小");
        
        System.out.println("=== 数据库配置建议 ===");
        System.out.println("数据库连接池大小: " + dbPoolSize);
        System.out.println("查询线程池大小: " + threadPoolSize);
    }
}