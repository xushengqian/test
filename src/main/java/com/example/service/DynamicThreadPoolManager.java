package com.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 动态线程池管理器
 * 根据系统负载和性能指标动态调整线程池大小
 */
@Service
public class DynamicThreadPoolManager {

    private static final Logger logger = LoggerFactory.getLogger(DynamicThreadPoolManager.class);

    private final ThreadPoolTaskExecutor databaseQueryExecutor;
    private final PerformanceMonitor performanceMonitor;

    // 性能阈值配置
    private static final double HIGH_CPU_THRESHOLD = 0.8;  // CPU使用率高阈值
    private static final double LOW_CPU_THRESHOLD = 0.3;   // CPU使用率低阈值
    private static final double HIGH_QUEUE_RATIO = 0.8;    // 队列使用率高阈值
    private static final long SLOW_QUERY_THRESHOLD = 1000; // 慢查询阈值(毫秒)

    public DynamicThreadPoolManager(@Qualifier("databaseQueryExecutor") Executor databaseQueryExecutor,
                                  PerformanceMonitor performanceMonitor) {
        this.databaseQueryExecutor = (ThreadPoolTaskExecutor) databaseQueryExecutor;
        this.performanceMonitor = performanceMonitor;
    }

    @PostConstruct
    public void init() {
        logger.info("动态线程池管理器初始化完成");
    }

    /**
     * 定期检查并调整线程池大小
     * 每30秒执行一次
     */
    @Scheduled(fixedRate = 30000)
    public void adjustThreadPoolSize() {
        try {
            ThreadPoolExecutor threadPool = databaseQueryExecutor.getThreadPoolExecutor();
            if (threadPool == null) {
                return;
            }

            // 获取当前性能指标
            PerformanceMetrics metrics = performanceMonitor.getCurrentMetrics();
            
            // 计算调整建议
            ThreadPoolAdjustment adjustment = calculateAdjustment(threadPool, metrics);
            
            // 应用调整
            if (adjustment.shouldAdjust()) {
                applyAdjustment(threadPool, adjustment);
            }

        } catch (Exception e) {
            logger.error("调整线程池大小时发生错误", e);
        }
    }

    /**
     * 计算线程池调整建议
     */
    private ThreadPoolAdjustment calculateAdjustment(ThreadPoolExecutor threadPool, PerformanceMetrics metrics) {
        int currentCoreSize = threadPool.getCorePoolSize();
        int currentMaxSize = threadPool.getMaximumPoolSize();
        int activeThreads = threadPool.getActiveCount();
        int queueSize = threadPool.getQueue().size();
        int queueCapacity = threadPool.getQueue().remainingCapacity() + threadPool.getQueue().size();

        // 计算使用率
        double queueUsageRatio = (double) queueSize / queueCapacity;
        double threadUsageRatio = (double) activeThreads / currentMaxSize;

        ThreadPoolAdjustment adjustment = new ThreadPoolAdjustment();

        // 决策逻辑
        if (shouldIncreaseThreads(metrics, queueUsageRatio, threadUsageRatio)) {
            // 增加线程数
            int newCoreSize = Math.min(currentCoreSize + 2, currentMaxSize);
            int newMaxSize = Math.min(currentMaxSize + 4, Runtime.getRuntime().availableProcessors() * 4);
            
            adjustment.setNewCoreSize(newCoreSize);
            adjustment.setNewMaxSize(newMaxSize);
            adjustment.setReason("高负载，增加线程数");
            
        } else if (shouldDecreaseThreads(metrics, queueUsageRatio, threadUsageRatio)) {
            // 减少线程数
            int minCoreSize = Runtime.getRuntime().availableProcessors();
            int newCoreSize = Math.max(currentCoreSize - 1, minCoreSize);
            int newMaxSize = Math.max(currentMaxSize - 2, minCoreSize * 2);
            
            adjustment.setNewCoreSize(newCoreSize);
            adjustment.setNewMaxSize(newMaxSize);
            adjustment.setReason("低负载，减少线程数");
        }

        return adjustment;
    }

    /**
     * 判断是否应该增加线程数
     */
    private boolean shouldIncreaseThreads(PerformanceMetrics metrics, double queueUsageRatio, double threadUsageRatio) {
        return (queueUsageRatio > HIGH_QUEUE_RATIO) ||
               (threadUsageRatio > 0.9 && metrics.getCpuUsage() < HIGH_CPU_THRESHOLD) ||
               (metrics.getAverageQueryTime() > SLOW_QUERY_THRESHOLD && metrics.getCpuUsage() < HIGH_CPU_THRESHOLD);
    }

    /**
     * 判断是否应该减少线程数
     */
    private boolean shouldDecreaseThreads(PerformanceMetrics metrics, double queueUsageRatio, double threadUsageRatio) {
        return (queueUsageRatio < 0.1 && threadUsageRatio < 0.3 && metrics.getCpuUsage() < LOW_CPU_THRESHOLD) ||
               (metrics.getAverageQueryTime() < 100 && threadUsageRatio < 0.2);
    }

    /**
     * 应用线程池调整
     */
    private void applyAdjustment(ThreadPoolExecutor threadPool, ThreadPoolAdjustment adjustment) {
        int oldCoreSize = threadPool.getCorePoolSize();
        int oldMaxSize = threadPool.getMaximumPoolSize();

        // 设置新的线程池大小
        if (adjustment.getNewMaxSize() > oldMaxSize) {
            // 先增加最大大小，再增加核心大小
            threadPool.setMaximumPoolSize(adjustment.getNewMaxSize());
            threadPool.setCorePoolSize(adjustment.getNewCoreSize());
        } else {
            // 先减少核心大小，再减少最大大小
            threadPool.setCorePoolSize(adjustment.getNewCoreSize());
            threadPool.setMaximumPoolSize(adjustment.getNewMaxSize());
        }

        logger.info("线程池大小已调整 - 原配置[核心:{}, 最大:{}] -> 新配置[核心:{}, 最大:{}], 原因: {}",
                   oldCoreSize, oldMaxSize, 
                   adjustment.getNewCoreSize(), adjustment.getNewMaxSize(),
                   adjustment.getReason());
    }

    /**
     * 获取当前线程池状态
     */
    public ThreadPoolStatus getCurrentStatus() {
        ThreadPoolExecutor threadPool = databaseQueryExecutor.getThreadPoolExecutor();
        if (threadPool == null) {
            return new ThreadPoolStatus();
        }

        ThreadPoolStatus status = new ThreadPoolStatus();
        status.setCorePoolSize(threadPool.getCorePoolSize());
        status.setMaximumPoolSize(threadPool.getMaximumPoolSize());
        status.setActiveCount(threadPool.getActiveCount());
        status.setQueueSize(threadPool.getQueue().size());
        status.setCompletedTaskCount(threadPool.getCompletedTaskCount());
        status.setTaskCount(threadPool.getTaskCount());

        return status;
    }

    /**
     * 线程池调整建议
     */
    private static class ThreadPoolAdjustment {
        private int newCoreSize;
        private int newMaxSize;
        private String reason;
        private boolean adjust = false;

        public boolean shouldAdjust() {
            return adjust;
        }

        public int getNewCoreSize() {
            return newCoreSize;
        }

        public void setNewCoreSize(int newCoreSize) {
            this.newCoreSize = newCoreSize;
            this.adjust = true;
        }

        public int getNewMaxSize() {
            return newMaxSize;
        }

        public void setNewMaxSize(int newMaxSize) {
            this.newMaxSize = newMaxSize;
            this.adjust = true;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    /**
     * 线程池状态信息
     */
    public static class ThreadPoolStatus {
        private int corePoolSize;
        private int maximumPoolSize;
        private int activeCount;
        private int queueSize;
        private long completedTaskCount;
        private long taskCount;

        // Getters and Setters
        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
        
        public int getMaximumPoolSize() { return maximumPoolSize; }
        public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
        
        public int getActiveCount() { return activeCount; }
        public void setActiveCount(int activeCount) { this.activeCount = activeCount; }
        
        public int getQueueSize() { return queueSize; }
        public void setQueueSize(int queueSize) { this.queueSize = queueSize; }
        
        public long getCompletedTaskCount() { return completedTaskCount; }
        public void setCompletedTaskCount(long completedTaskCount) { this.completedTaskCount = completedTaskCount; }
        
        public long getTaskCount() { return taskCount; }
        public void setTaskCount(long taskCount) { this.taskCount = taskCount; }

        @Override
        public String toString() {
            return String.format("ThreadPoolStatus{核心线程数=%d, 最大线程数=%d, 活跃线程数=%d, 队列大小=%d, 已完成任务=%d, 总任务数=%d}",
                               corePoolSize, maximumPoolSize, activeCount, queueSize, completedTaskCount, taskCount);
        }
    }
}