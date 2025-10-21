package com.example.concurrent.db.monitor;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控器
 * 监控数据库查询性能、系统资源使用情况，并提供自适应调整建议
 */
public class PerformanceMonitor {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);
    
    private final MeterRegistry meterRegistry;
    private final ScheduledExecutorService scheduler;
    private final OperatingSystemMXBean osBean;
    private final ThreadMXBean threadBean;
    
    // 性能指标
    private final Counter queryCounter;
    private final Counter errorCounter;
    private final Timer queryTimer;
    private final Gauge threadPoolSizeGauge;
    private final Gauge cpuUsageGauge;
    private final Gauge memoryUsageGauge;
    
    // 性能历史数据
    private final ConcurrentHashMap<String, PerformanceMetrics> metricsHistory;
    private final AtomicLong lastAdjustmentTime;
    
    // 配置参数
    private double cpuThreshold = 0.8;  // CPU使用率阈值
    private double memoryThreshold = 0.85;  // 内存使用率阈值
    private long adjustmentInterval = 60000;  // 调整间隔（毫秒）
    
    public PerformanceMonitor() {
        this.meterRegistry = new SimpleMeterRegistry();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("performance-monitor");
            thread.setDaemon(true);
            return thread;
        });
        
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        this.metricsHistory = new ConcurrentHashMap<>();
        this.lastAdjustmentTime = new AtomicLong(System.currentTimeMillis());
        
        // 初始化指标
        this.queryCounter = Counter.builder("db.queries.total")
            .description("总查询次数")
            .register(meterRegistry);
            
        this.errorCounter = Counter.builder("db.queries.errors")
            .description("查询错误次数")
            .register(meterRegistry);
            
        this.queryTimer = Timer.builder("db.query.duration")
            .description("查询耗时")
            .register(meterRegistry);
            
        this.threadPoolSizeGauge = Gauge.builder("thread.pool.size", this, 
            monitor -> getActiveThreadCount())
            .description("线程池大小")
            .register(meterRegistry);
            
        this.cpuUsageGauge = Gauge.builder("system.cpu.usage", this,
            monitor -> getCpuUsage())
            .description("CPU使用率")
            .register(meterRegistry);
            
        this.memoryUsageGauge = Gauge.builder("system.memory.usage", this,
            monitor -> getMemoryUsage())
            .description("内存使用率")
            .register(meterRegistry);
        
        // 启动监控任务
        startMonitoring();
        
        logger.info("性能监控器初始化完成");
    }
    
    /**
     * 记录查询执行
     */
    public void recordQuery(String queryType, long duration, boolean success) {
        queryCounter.increment();
        queryTimer.record(duration, TimeUnit.MILLISECONDS);
        
        if (!success) {
            errorCounter.increment();
        }
        
        // 更新历史数据
        String key = generateMetricsKey();
        metricsHistory.compute(key, (k, v) -> {
            if (v == null) {
                v = new PerformanceMetrics();
            }
            v.addQuery(duration, success);
            return v;
        });
        
        logger.debug("记录查询: 类型={}, 耗时={}ms, 成功={}", queryType, duration, success);
    }
    
    /**
     * 获取优化建议
     */
    public OptimizationAdvice getOptimizationAdvice(int currentThreads, int connectionPoolSize) {
        OptimizationAdvice advice = new OptimizationAdvice();
        
        // 检查是否需要调整
        long now = System.currentTimeMillis();
        if (now - lastAdjustmentTime.get() < adjustmentInterval) {
            advice.setNeedAdjustment(false);
            advice.setReason("距离上次调整时间太短");
            return advice;
        }
        
        // 获取系统指标
        double cpuUsage = getCpuUsage();
        double memoryUsage = getMemoryUsage();
        double avgQueryTime = getAverageQueryTime();
        double errorRate = getErrorRate();
        
        logger.info("系统指标: CPU={}%, 内存={}%, 平均查询时间={}ms, 错误率={}%",
                   String.format("%.2f", cpuUsage * 100),
                   String.format("%.2f", memoryUsage * 100),
                   String.format("%.2f", avgQueryTime),
                   String.format("%.2f", errorRate * 100));
        
        // 分析并给出建议
        advice.setCurrentThreads(currentThreads);
        
        if (cpuUsage > cpuThreshold && currentThreads > Runtime.getRuntime().availableProcessors()) {
            // CPU使用率过高，减少线程数
            advice.setNeedAdjustment(true);
            advice.setRecommendedThreads(Math.max(
                Runtime.getRuntime().availableProcessors(),
                currentThreads - 2
            ));
            advice.setReason("CPU使用率过高(" + String.format("%.1f%%", cpuUsage * 100) + ")，建议减少线程数");
            
        } else if (memoryUsage > memoryThreshold) {
            // 内存使用率过高
            advice.setNeedAdjustment(true);
            advice.setRecommendedThreads(Math.max(
                Runtime.getRuntime().availableProcessors(),
                currentThreads - 1
            ));
            advice.setReason("内存使用率过高(" + String.format("%.1f%%", memoryUsage * 100) + ")，建议减少线程数");
            
        } else if (avgQueryTime > 2000 && cpuUsage < 0.5 && currentThreads < connectionPoolSize) {
            // 查询慢但资源充足，增加线程数
            advice.setNeedAdjustment(true);
            advice.setRecommendedThreads(Math.min(
                connectionPoolSize,
                currentThreads + 2
            ));
            advice.setReason("查询响应慢但资源充足，建议增加线程数");
            
        } else if (errorRate > 0.05) {
            // 错误率过高
            advice.setNeedAdjustment(true);
            advice.setRecommendedThreads(Math.max(
                Runtime.getRuntime().availableProcessors(),
                currentThreads - 1
            ));
            advice.setReason("错误率过高(" + String.format("%.1f%%", errorRate * 100) + ")，建议减少并发");
            
        } else {
            advice.setNeedAdjustment(false);
            advice.setReason("当前配置良好，无需调整");
        }
        
        if (advice.isNeedAdjustment()) {
            lastAdjustmentTime.set(now);
        }
        
        return advice;
    }
    
    /**
     * 获取CPU使用率
     */
    private double getCpuUsage() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            return ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad();
        }
        return osBean.getSystemLoadAverage() / Runtime.getRuntime().availableProcessors();
    }
    
    /**
     * 获取内存使用率
     */
    private double getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        return 1.0 - ((double) freeMemory / totalMemory);
    }
    
    /**
     * 获取活跃线程数
     */
    private int getActiveThreadCount() {
        return threadBean.getThreadCount();
    }
    
    /**
     * 获取平均查询时间
     */
    private double getAverageQueryTime() {
        String key = generateMetricsKey();
        PerformanceMetrics metrics = metricsHistory.get(key);
        if (metrics != null) {
            return metrics.getAverageQueryTime();
        }
        return 0;
    }
    
    /**
     * 获取错误率
     */
    private double getErrorRate() {
        String key = generateMetricsKey();
        PerformanceMetrics metrics = metricsHistory.get(key);
        if (metrics != null) {
            return metrics.getErrorRate();
        }
        return 0;
    }
    
    /**
     * 生成指标键
     */
    private String generateMetricsKey() {
        // 使用分钟级别的时间窗口
        long minuteWindow = System.currentTimeMillis() / 60000;
        return String.valueOf(minuteWindow);
    }
    
    /**
     * 启动监控任务
     */
    private void startMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 清理旧数据（保留最近10分钟）
                long cutoff = (System.currentTimeMillis() / 60000) - 10;
                metricsHistory.entrySet().removeIf(entry -> {
                    try {
                        long key = Long.parseLong(entry.getKey());
                        return key < cutoff;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                });
                
                // 输出当前性能指标
                logger.info("性能监控 - CPU: {:.2f}%, 内存: {:.2f}%, 线程数: {}, " +
                           "平均查询时间: {:.2f}ms, 错误率: {:.2f}%",
                           getCpuUsage() * 100,
                           getMemoryUsage() * 100,
                           getActiveThreadCount(),
                           getAverageQueryTime(),
                           getErrorRate() * 100);
                
            } catch (Exception e) {
                logger.error("监控任务执行失败", e);
            }
        }, 10, 30, TimeUnit.SECONDS);
    }
    
    /**
     * 获取性能报告
     */
    public PerformanceReport getPerformanceReport() {
        PerformanceReport report = new PerformanceReport();
        
        report.setCpuUsage(getCpuUsage());
        report.setMemoryUsage(getMemoryUsage());
        report.setActiveThreads(getActiveThreadCount());
        report.setTotalQueries(queryCounter.count());
        report.setErrorCount(errorCounter.count());
        report.setAverageQueryTime(getAverageQueryTime());
        report.setErrorRate(getErrorRate());
        
        // 添加时间分布
        queryTimer.takeSnapshot().histogramCounts().forEach(bucket -> {
            report.addLatencyBucket(bucket.bucket(), bucket.count());
        });
        
        return report;
    }
    
    /**
     * 关闭监控器
     */
    public void close() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("性能监控器已关闭");
    }
    
    /**
     * 性能指标类
     */
    private static class PerformanceMetrics {
        private final AtomicLong totalQueries = new AtomicLong(0);
        private final AtomicLong totalTime = new AtomicLong(0);
        private final AtomicLong errorCount = new AtomicLong(0);
        
        void addQuery(long duration, boolean success) {
            totalQueries.incrementAndGet();
            totalTime.addAndGet(duration);
            if (!success) {
                errorCount.incrementAndGet();
            }
        }
        
        double getAverageQueryTime() {
            long queries = totalQueries.get();
            if (queries == 0) return 0;
            return (double) totalTime.get() / queries;
        }
        
        double getErrorRate() {
            long queries = totalQueries.get();
            if (queries == 0) return 0;
            return (double) errorCount.get() / queries;
        }
    }
    
    /**
     * 优化建议
     */
    public static class OptimizationAdvice {
        private boolean needAdjustment;
        private int currentThreads;
        private int recommendedThreads;
        private String reason;
        
        // Getters and Setters
        public boolean isNeedAdjustment() {
            return needAdjustment;
        }
        
        public void setNeedAdjustment(boolean needAdjustment) {
            this.needAdjustment = needAdjustment;
        }
        
        public int getCurrentThreads() {
            return currentThreads;
        }
        
        public void setCurrentThreads(int currentThreads) {
            this.currentThreads = currentThreads;
        }
        
        public int getRecommendedThreads() {
            return recommendedThreads;
        }
        
        public void setRecommendedThreads(int recommendedThreads) {
            this.recommendedThreads = recommendedThreads;
        }
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
        
        @Override
        public String toString() {
            return String.format("OptimizationAdvice{needAdjustment=%b, current=%d, " +
                    "recommended=%d, reason='%s'}",
                    needAdjustment, currentThreads, recommendedThreads, reason);
        }
    }
    
    /**
     * 性能报告
     */
    public static class PerformanceReport {
        private double cpuUsage;
        private double memoryUsage;
        private int activeThreads;
        private double totalQueries;
        private double errorCount;
        private double averageQueryTime;
        private double errorRate;
        private final ConcurrentHashMap<Long, Long> latencyDistribution = new ConcurrentHashMap<>();
        
        // Getters and Setters
        public double getCpuUsage() {
            return cpuUsage;
        }
        
        public void setCpuUsage(double cpuUsage) {
            this.cpuUsage = cpuUsage;
        }
        
        public double getMemoryUsage() {
            return memoryUsage;
        }
        
        public void setMemoryUsage(double memoryUsage) {
            this.memoryUsage = memoryUsage;
        }
        
        public int getActiveThreads() {
            return activeThreads;
        }
        
        public void setActiveThreads(int activeThreads) {
            this.activeThreads = activeThreads;
        }
        
        public double getTotalQueries() {
            return totalQueries;
        }
        
        public void setTotalQueries(double totalQueries) {
            this.totalQueries = totalQueries;
        }
        
        public double getErrorCount() {
            return errorCount;
        }
        
        public void setErrorCount(double errorCount) {
            this.errorCount = errorCount;
        }
        
        public double getAverageQueryTime() {
            return averageQueryTime;
        }
        
        public void setAverageQueryTime(double averageQueryTime) {
            this.averageQueryTime = averageQueryTime;
        }
        
        public double getErrorRate() {
            return errorRate;
        }
        
        public void setErrorRate(double errorRate) {
            this.errorRate = errorRate;
        }
        
        public void addLatencyBucket(long latency, long count) {
            latencyDistribution.put(latency, count);
        }
        
        @Override
        public String toString() {
            return String.format(
                "PerformanceReport{\n" +
                "  CPU使用率: %.2f%%\n" +
                "  内存使用率: %.2f%%\n" +
                "  活跃线程数: %d\n" +
                "  总查询数: %.0f\n" +
                "  错误数: %.0f\n" +
                "  平均查询时间: %.2fms\n" +
                "  错误率: %.2f%%\n" +
                "}",
                cpuUsage * 100, memoryUsage * 100, activeThreads,
                totalQueries, errorCount, averageQueryTime, errorRate * 100
            );
        }
    }
}