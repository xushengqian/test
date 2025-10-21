package com.example.concurrent.db.core;

import com.example.concurrent.db.monitor.PerformanceMonitor;
import com.example.concurrent.db.utils.OptimalThreadCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自适应线程池管理器
 * 根据系统负载和查询性能动态调整线程池大小
 */
public class AdaptiveThreadPoolManager {
    
    private static final Logger logger = LoggerFactory.getLogger(AdaptiveThreadPoolManager.class);
    
    private final ThreadPoolExecutor threadPool;
    private final PerformanceMonitor performanceMonitor;
    private final ScheduledExecutorService adjustmentExecutor;
    
    // 配置参数
    private final int minThreads;
    private final int maxThreads;
    private final long adjustmentPeriod;
    
    // 性能统计
    private final AtomicLong totalTasks = new AtomicLong(0);
    private final AtomicLong completedTasks = new AtomicLong(0);
    private final AtomicLong rejectedTasks = new AtomicLong(0);
    private final AtomicInteger consecutiveAdjustments = new AtomicInteger(0);
    
    // 调整策略参数
    private static final int MAX_CONSECUTIVE_ADJUSTMENTS = 3;
    private static final double LOAD_FACTOR_THRESHOLD_HIGH = 0.9;
    private static final double LOAD_FACTOR_THRESHOLD_LOW = 0.3;
    
    /**
     * 构造函数
     */
    public AdaptiveThreadPoolManager(int initialThreads, int minThreads, int maxThreads,
                                     long adjustmentPeriodSeconds) {
        this.minThreads = minThreads;
        this.maxThreads = maxThreads;
        this.adjustmentPeriod = adjustmentPeriodSeconds;
        
        // 创建线程池
        this.threadPool = new ThreadPoolExecutor(
            initialThreads,
            maxThreads,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(initialThreads * 10),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("adaptive-pool-" + counter.incrementAndGet());
                    return thread;
                }
            },
            new RejectedExecutionHandler() {
                @Override
                public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                    rejectedTasks.incrementAndGet();
                    logger.warn("任务被拒绝，当前队列大小: {}", executor.getQueue().size());
                    // 尝试直接执行
                    if (!executor.isShutdown()) {
                        try {
                            executor.getQueue().put(r);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        );
        
        // 预启动核心线程
        threadPool.prestartAllCoreThreads();
        
        // 创建性能监控器
        this.performanceMonitor = new PerformanceMonitor();
        
        // 创建调整执行器
        this.adjustmentExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("pool-adjuster");
            thread.setDaemon(true);
            return thread;
        });
        
        // 启动自适应调整
        startAdaptiveAdjustment();
        
        logger.info("自适应线程池管理器初始化: 初始线程数={}, 范围=[{}, {}]",
                   initialThreads, minThreads, maxThreads);
    }
    
    /**
     * 使用默认参数构造
     */
    public AdaptiveThreadPoolManager() {
        this(OptimalThreadCalculator.calculateForIoIntensive(),
             OptimalThreadCalculator.getCpuCores(),
             OptimalThreadCalculator.getCpuCores() * 4,
             30);
    }
    
    /**
     * 提交任务
     */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        totalTasks.incrementAndGet();
        
        CompletableFuture<T> future = new CompletableFuture<>();
        
        threadPool.execute(() -> {
            long startTime = System.currentTimeMillis();
            try {
                T result = task.call();
                future.complete(result);
                completedTasks.incrementAndGet();
                
                // 记录性能
                long duration = System.currentTimeMillis() - startTime;
                performanceMonitor.recordQuery("task", duration, true);
                
            } catch (Exception e) {
                future.completeExceptionally(e);
                performanceMonitor.recordQuery("task", 
                    System.currentTimeMillis() - startTime, false);
            }
        });
        
        return future;
    }
    
    /**
     * 批量提交任务
     */
    public <T> List<CompletableFuture<T>> submitBatch(List<Callable<T>> tasks) {
        List<CompletableFuture<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            futures.add(submit(task));
        }
        return futures;
    }
    
    /**
     * 启动自适应调整
     */
    private void startAdaptiveAdjustment() {
        adjustmentExecutor.scheduleWithFixedDelay(() -> {
            try {
                adjustThreadPool();
            } catch (Exception e) {
                logger.error("线程池调整失败", e);
            }
        }, adjustmentPeriod, adjustmentPeriod, TimeUnit.SECONDS);
    }
    
    /**
     * 调整线程池大小
     */
    private void adjustThreadPool() {
        // 获取当前状态
        int currentSize = threadPool.getCorePoolSize();
        int activeCount = threadPool.getActiveCount();
        int queueSize = threadPool.getQueue().size();
        long completed = threadPool.getCompletedTaskCount();
        
        // 计算负载因子
        double loadFactor = (double) activeCount / currentSize;
        double queueUtilization = (double) queueSize / threadPool.getQueue().remainingCapacity();
        
        logger.info("线程池状态: 核心线程={}, 活跃={}, 队列={}, 完成={}, 负载={:.2f}",
                   currentSize, activeCount, queueSize, completed, loadFactor);
        
        // 获取性能建议
        PerformanceMonitor.OptimizationAdvice advice = 
            performanceMonitor.getOptimizationAdvice(currentSize, maxThreads);
        
        int newSize = currentSize;
        String reason = "";
        
        if (advice.isNeedAdjustment()) {
            // 使用性能监控的建议
            newSize = advice.getRecommendedThreads();
            reason = advice.getReason();
            
        } else if (loadFactor > LOAD_FACTOR_THRESHOLD_HIGH && queueSize > 0) {
            // 高负载且有排队任务，增加线程
            newSize = Math.min(currentSize + 2, maxThreads);
            reason = "高负载(负载因子=" + String.format("%.2f", loadFactor) + ")";
            
        } else if (loadFactor < LOAD_FACTOR_THRESHOLD_LOW && queueSize == 0) {
            // 低负载且无排队任务，减少线程
            newSize = Math.max(currentSize - 1, minThreads);
            reason = "低负载(负载因子=" + String.format("%.2f", loadFactor) + ")";
            
        } else if (rejectedTasks.get() > 0) {
            // 有任务被拒绝，增加线程
            newSize = Math.min(currentSize + 3, maxThreads);
            reason = "存在被拒绝的任务(" + rejectedTasks.get() + "个)";
            rejectedTasks.set(0); // 重置计数器
        }
        
        // 应用调整
        if (newSize != currentSize) {
            // 检查连续调整次数
            if (consecutiveAdjustments.incrementAndGet() > MAX_CONSECUTIVE_ADJUSTMENTS) {
                logger.warn("连续调整次数过多，暂停调整");
                consecutiveAdjustments.set(0);
                return;
            }
            
            // 调整线程池
            threadPool.setCorePoolSize(newSize);
            threadPool.setMaximumPoolSize(Math.max(newSize, threadPool.getMaximumPoolSize()));
            
            logger.info("线程池调整: {} -> {}, 原因: {}", currentSize, newSize, reason);
            
        } else {
            // 无需调整，重置计数器
            consecutiveAdjustments.set(0);
        }
    }
    
    /**
     * 获取线程池统计信息
     */
    public ThreadPoolStats getStats() {
        return new ThreadPoolStats(
            threadPool.getCorePoolSize(),
            threadPool.getMaximumPoolSize(),
            threadPool.getActiveCount(),
            threadPool.getCompletedTaskCount(),
            threadPool.getQueue().size(),
            totalTasks.get(),
            completedTasks.get(),
            rejectedTasks.get(),
            getAverageWaitTime()
        );
    }
    
    /**
     * 计算平均等待时间
     */
    private long getAverageWaitTime() {
        if (completedTasks.get() == 0) {
            return 0;
        }
        // 简化计算：队列大小 * 平均处理时间 / 活跃线程数
        int queueSize = threadPool.getQueue().size();
        int activeThreads = Math.max(threadPool.getActiveCount(), 1);
        return (queueSize * 100) / activeThreads; // 假设平均处理时间100ms
    }
    
    /**
     * 优雅关闭
     */
    public void shutdown() {
        logger.info("关闭自适应线程池管理器...");
        
        // 停止调整
        adjustmentExecutor.shutdown();
        
        // 关闭线程池
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 关闭监控
        performanceMonitor.close();
        
        logger.info("自适应线程池管理器已关闭");
    }
    
    /**
     * 线程池统计信息
     */
    public static class ThreadPoolStats {
        private final int corePoolSize;
        private final int maxPoolSize;
        private final int activeThreads;
        private final long completedTasks;
        private final int queueSize;
        private final long totalSubmitted;
        private final long totalCompleted;
        private final long totalRejected;
        private final long avgWaitTime;
        
        public ThreadPoolStats(int corePoolSize, int maxPoolSize, int activeThreads,
                              long completedTasks, int queueSize, long totalSubmitted,
                              long totalCompleted, long totalRejected, long avgWaitTime) {
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.activeThreads = activeThreads;
            this.completedTasks = completedTasks;
            this.queueSize = queueSize;
            this.totalSubmitted = totalSubmitted;
            this.totalCompleted = totalCompleted;
            this.totalRejected = totalRejected;
            this.avgWaitTime = avgWaitTime;
        }
        
        // Getters
        public int getCorePoolSize() { return corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public int getActiveThreads() { return activeThreads; }
        public long getCompletedTasks() { return completedTasks; }
        public int getQueueSize() { return queueSize; }
        public long getTotalSubmitted() { return totalSubmitted; }
        public long getTotalCompleted() { return totalCompleted; }
        public long getTotalRejected() { return totalRejected; }
        public long getAvgWaitTime() { return avgWaitTime; }
        
        @Override
        public String toString() {
            return String.format(
                "ThreadPoolStats{\n" +
                "  线程池大小: %d/%d\n" +
                "  活跃线程: %d\n" +
                "  完成任务: %d\n" +
                "  队列大小: %d\n" +
                "  总提交: %d\n" +
                "  总完成: %d\n" +
                "  总拒绝: %d\n" +
                "  平均等待时间: %dms\n" +
                "}",
                corePoolSize, maxPoolSize, activeThreads, completedTasks,
                queueSize, totalSubmitted, totalCompleted, totalRejected, avgWaitTime
            );
        }
    }
}