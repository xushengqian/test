package com.example.demo;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池优化演示程序
 * 展示如何根据系统资源配置最优的数据库查询线程池
 */
public class ThreadPoolDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Java 数据库并发查询线程优化演示 ===");
        
        // 显示系统信息
        showSystemInfo();
        
        // 演示不同的线程池配置
        demonstrateThreadPoolConfigurations();
        
        // 演示动态调整
        demonstrateDynamicAdjustment();
        
        System.out.println("=== 演示完成 ===");
    }

    /**
     * 显示系统信息
     */
    private static void showSystemInfo() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        long maxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        long totalMemory = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        
        System.out.println("\n=== 系统信息 ===");
        System.out.println("CPU核心数: " + cpuCores);
        System.out.println("最大内存: " + maxMemory + " MB");
        System.out.println("当前内存: " + totalMemory + " MB");
        System.out.println("================");
    }

    /**
     * 演示不同的线程池配置
     */
    private static void demonstrateThreadPoolConfigurations() throws InterruptedException {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        System.out.println("\n=== 线程池配置策略 ===");
        
        // 1. CPU密集型任务配置
        System.out.println("1. CPU密集型任务配置:");
        System.out.println("   核心线程数: " + cpuCores);
        System.out.println("   最大线程数: " + (cpuCores + 1));
        System.out.println("   适用场景: 计算密集型任务");
        
        // 2. I/O密集型任务配置（数据库查询）
        int ioThreads = cpuCores * 2;
        System.out.println("\n2. I/O密集型任务配置（数据库查询）:");
        System.out.println("   核心线程数: " + cpuCores);
        System.out.println("   最大线程数: " + ioThreads);
        System.out.println("   队列容量: " + (ioThreads * 8));
        System.out.println("   适用场景: 数据库查询、网络请求");
        
        // 3. 混合型任务配置
        int mixedThreads = (int)(cpuCores * 1.5);
        System.out.println("\n3. 混合型任务配置:");
        System.out.println("   核心线程数: " + cpuCores);
        System.out.println("   最大线程数: " + mixedThreads);
        System.out.println("   适用场景: 混合计算和I/O操作");
        
        // 演示实际的线程池执行
        demonstrateActualExecution(cpuCores, ioThreads);
    }

    /**
     * 演示实际的线程池执行
     */
    private static void demonstrateActualExecution(int coreThreads, int maxThreads) throws InterruptedException {
        System.out.println("\n=== 实际执行演示 ===");
        
        // 创建优化的线程池（模拟数据库查询线程池）
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            coreThreads,                    // 核心线程数
            maxThreads,                     // 最大线程数
            60L,                           // 线程空闲时间
            TimeUnit.SECONDS,              // 时间单位
            new LinkedBlockingQueue<>(maxThreads * 8), // 队列容量
            new ThreadFactory() {          // 线程工厂
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "db-query-" + counter.getAndIncrement());
                    t.setDaemon(false);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
        );

        System.out.println("创建线程池 - 核心线程数: " + coreThreads + ", 最大线程数: " + maxThreads);
        
        // 提交模拟的数据库查询任务
        int taskCount = 50;
        CountDownLatch latch = new CountDownLatch(taskCount);
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    // 模拟数据库查询（I/O等待）
                    Thread.sleep(100 + (int)(Math.random() * 200)); // 100-300ms的查询时间
                    
                    System.out.printf("任务 %d 完成 - 线程: %s, 活跃线程数: %d, 队列大小: %d%n", 
                                    taskId, 
                                    Thread.currentThread().getName(),
                                    executor.getActiveCount(),
                                    executor.getQueue().size());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 等待所有任务完成
        latch.await();
        long endTime = System.currentTimeMillis();
        
        System.out.println("\n执行统计:");
        System.out.println("总任务数: " + taskCount);
        System.out.println("总耗时: " + (endTime - startTime) + " ms");
        System.out.println("平均耗时: " + (endTime - startTime) / taskCount + " ms/任务");
        System.out.println("已完成任务数: " + executor.getCompletedTaskCount());
        System.out.println("最大活跃线程数: " + executor.getLargestPoolSize());
        
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * 演示动态调整策略
     */
    private static void demonstrateDynamicAdjustment() {
        System.out.println("\n=== 动态调整策略 ===");
        
        System.out.println("线程池动态调整的触发条件:");
        System.out.println("1. 增加线程数的条件:");
        System.out.println("   - 队列使用率 > 80%");
        System.out.println("   - 线程使用率 > 90% 且 CPU使用率 < 80%");
        System.out.println("   - 平均查询时间 > 1000ms 且 CPU使用率 < 80%");
        
        System.out.println("\n2. 减少线程数的条件:");
        System.out.println("   - 队列使用率 < 10% 且线程使用率 < 30% 且 CPU使用率 < 30%");
        System.out.println("   - 平均查询时间 < 100ms 且线程使用率 < 20%");
        
        System.out.println("\n3. 调整策略:");
        System.out.println("   - 每次调整幅度: ±1-2个线程");
        System.out.println("   - 调整间隔: 30秒");
        System.out.println("   - 最小线程数: CPU核心数");
        System.out.println("   - 最大线程数: CPU核心数 * 4");
        
        // 模拟性能指标
        simulatePerformanceMetrics();
    }

    /**
     * 模拟性能指标监控
     */
    private static void simulatePerformanceMetrics() {
        System.out.println("\n=== 性能指标监控示例 ===");
        
        // 模拟不同负载下的指标
        double[][] scenarios = {
            {0.3, 0.2, 150},  // 低负载: CPU 30%, 线程使用率 20%, 平均查询时间 150ms
            {0.6, 0.7, 800},  // 中负载: CPU 60%, 线程使用率 70%, 平均查询时间 800ms  
            {0.9, 0.95, 1200} // 高负载: CPU 90%, 线程使用率 95%, 平均查询时间 1200ms
        };
        
        String[] scenarioNames = {"低负载", "中负载", "高负载"};
        
        for (int i = 0; i < scenarios.length; i++) {
            double cpuUsage = scenarios[i][0];
            double threadUsage = scenarios[i][1];
            double avgQueryTime = scenarios[i][2];
            
            System.out.printf("\n%s场景:\n", scenarioNames[i]);
            System.out.printf("  CPU使用率: %.1f%%\n", cpuUsage * 100);
            System.out.printf("  线程使用率: %.1f%%\n", threadUsage * 100);
            System.out.printf("  平均查询时间: %.0f ms\n", avgQueryTime);
            
            // 根据指标给出调整建议
            String recommendation = getAdjustmentRecommendation(cpuUsage, threadUsage, avgQueryTime);
            System.out.printf("  调整建议: %s\n", recommendation);
        }
    }

    /**
     * 根据性能指标给出调整建议
     */
    private static String getAdjustmentRecommendation(double cpuUsage, double threadUsage, double avgQueryTime) {
        if (threadUsage > 0.9 && cpuUsage < 0.8) {
            return "增加线程数 (+2)";
        } else if (avgQueryTime > 1000 && cpuUsage < 0.8) {
            return "增加线程数 (+1)";
        } else if (threadUsage < 0.3 && cpuUsage < 0.3 && avgQueryTime < 200) {
            return "减少线程数 (-1)";
        } else if (cpuUsage > 0.8) {
            return "减少线程数 (-1) - CPU使用率过高";
        } else {
            return "保持当前配置";
        }
    }
}