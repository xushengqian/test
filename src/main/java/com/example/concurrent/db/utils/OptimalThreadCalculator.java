package com.example.concurrent.db.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 最优线程数计算工具类
 * 
 * 根据不同的场景和系统资源计算最优线程数：
 * 1. CPU密集型任务: 线程数 = CPU核心数 + 1
 * 2. IO密集型任务: 线程数 = CPU核心数 * (1 + 等待时间/计算时间)
 * 3. 数据库查询: 需要考虑数据库连接池大小和系统资源
 */
public class OptimalThreadCalculator {
    
    private static final Logger logger = LoggerFactory.getLogger(OptimalThreadCalculator.class);
    
    // 系统CPU核心数
    private static final int CPU_CORES = Runtime.getRuntime().availableProcessors();
    
    // 默认配置
    private static final double DEFAULT_BLOCKING_COEFFICIENT = 0.9; // 默认阻塞系数
    private static final int MIN_THREADS = 2; // 最小线程数
    private static final int MAX_THREADS_MULTIPLIER = 4; // 最大线程数倍数
    
    /**
     * 获取CPU核心数
     */
    public static int getCpuCores() {
        return CPU_CORES;
    }
    
    /**
     * 计算CPU密集型任务的最优线程数
     * 公式: CPU核心数 + 1
     */
    public static int calculateForCpuIntensive() {
        int threads = CPU_CORES + 1;
        logger.info("CPU密集型任务最优线程数: {}, CPU核心数: {}", threads, CPU_CORES);
        return threads;
    }
    
    /**
     * 计算IO密集型任务的最优线程数
     * 公式: CPU核心数 * 2
     */
    public static int calculateForIoIntensive() {
        int threads = CPU_CORES * 2;
        logger.info("IO密集型任务最优线程数: {}, CPU核心数: {}", threads, CPU_CORES);
        return threads;
    }
    
    /**
     * 根据阻塞系数计算最优线程数
     * 公式: CPU核心数 / (1 - 阻塞系数)
     * 
     * @param blockingCoefficient 阻塞系数 (0-1之间，越接近1表示IO等待时间越长)
     */
    public static int calculateWithBlockingCoefficient(double blockingCoefficient) {
        if (blockingCoefficient < 0 || blockingCoefficient >= 1) {
            throw new IllegalArgumentException("阻塞系数必须在0-1之间");
        }
        
        int threads = (int) Math.ceil(CPU_CORES / (1 - blockingCoefficient));
        threads = Math.max(threads, MIN_THREADS);
        threads = Math.min(threads, CPU_CORES * MAX_THREADS_MULTIPLIER);
        
        logger.info("根据阻塞系数{}计算的最优线程数: {}", blockingCoefficient, threads);
        return threads;
    }
    
    /**
     * 根据等待时间和计算时间计算最优线程数
     * 公式: CPU核心数 * (1 + 等待时间/计算时间)
     * 
     * @param waitTime 等待时间（毫秒）
     * @param computeTime 计算时间（毫秒）
     */
    public static int calculateWithWaitTime(long waitTime, long computeTime) {
        if (computeTime <= 0) {
            throw new IllegalArgumentException("计算时间必须大于0");
        }
        
        double ratio = (double) waitTime / computeTime;
        int threads = (int) Math.ceil(CPU_CORES * (1 + ratio));
        threads = Math.max(threads, MIN_THREADS);
        threads = Math.min(threads, CPU_CORES * MAX_THREADS_MULTIPLIER);
        
        logger.info("等待时间: {}ms, 计算时间: {}ms, 最优线程数: {}", 
                    waitTime, computeTime, threads);
        return threads;
    }
    
    /**
     * 为数据库查询计算最优线程数
     * 需要考虑数据库连接池大小和系统资源
     * 
     * @param connectionPoolSize 数据库连接池大小
     * @param queryResponseTime 平均查询响应时间（毫秒）
     * @param processingTime 结果处理时间（毫秒）
     */
    public static int calculateForDatabaseQuery(int connectionPoolSize, 
                                                 long queryResponseTime, 
                                                 long processingTime) {
        // 基于等待时间计算
        int optimalByWaitTime = calculateWithWaitTime(queryResponseTime, processingTime);
        
        // 不能超过连接池大小
        int threads = Math.min(optimalByWaitTime, connectionPoolSize);
        
        // 确保在合理范围内
        threads = Math.max(threads, MIN_THREADS);
        threads = Math.min(threads, connectionPoolSize);
        
        logger.info("数据库查询最优线程数: {}, 连接池大小: {}, 查询响应时间: {}ms, 处理时间: {}ms",
                    threads, connectionPoolSize, queryResponseTime, processingTime);
        return threads;
    }
    
    /**
     * 动态计算最优线程数（考虑系统负载）
     * 
     * @param baseThreads 基础线程数
     * @param systemLoad 系统负载 (0-1之间)
     */
    public static int calculateWithSystemLoad(int baseThreads, double systemLoad) {
        if (systemLoad < 0 || systemLoad > 1) {
            throw new IllegalArgumentException("系统负载必须在0-1之间");
        }
        
        // 系统负载高时减少线程数
        double adjustmentFactor = 1 - (systemLoad * 0.5);
        int threads = (int) Math.ceil(baseThreads * adjustmentFactor);
        threads = Math.max(threads, MIN_THREADS);
        
        logger.info("考虑系统负载{}后的线程数: {} (基础线程数: {})", 
                    systemLoad, threads, baseThreads);
        return threads;
    }
    
    /**
     * 获取推荐的线程池配置
     */
    public static ThreadPoolConfig getRecommendedConfig(TaskType taskType) {
        ThreadPoolConfig config = new ThreadPoolConfig();
        
        switch (taskType) {
            case CPU_INTENSIVE:
                config.setCorePoolSize(calculateForCpuIntensive());
                config.setMaximumPoolSize(config.getCorePoolSize());
                break;
            case IO_INTENSIVE:
                config.setCorePoolSize(calculateForIoIntensive());
                config.setMaximumPoolSize(config.getCorePoolSize() * 2);
                break;
            case DATABASE_QUERY:
                config.setCorePoolSize(calculateWithBlockingCoefficient(DEFAULT_BLOCKING_COEFFICIENT));
                config.setMaximumPoolSize(config.getCorePoolSize() * 2);
                break;
            case MIXED:
                int baseThreads = (int) (CPU_CORES * 1.5);
                config.setCorePoolSize(baseThreads);
                config.setMaximumPoolSize(baseThreads * 2);
                break;
        }
        
        config.setQueueCapacity(config.getCorePoolSize() * 10);
        config.setKeepAliveSeconds(60);
        
        logger.info("任务类型: {}, 推荐配置: {}", taskType, config);
        return config;
    }
    
    /**
     * 任务类型枚举
     */
    public enum TaskType {
        CPU_INTENSIVE("CPU密集型"),
        IO_INTENSIVE("IO密集型"),
        DATABASE_QUERY("数据库查询"),
        MIXED("混合型");
        
        private final String description;
        
        TaskType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 线程池配置类
     */
    public static class ThreadPoolConfig {
        private int corePoolSize;
        private int maximumPoolSize;
        private int queueCapacity;
        private long keepAliveSeconds;
        
        // Getters and Setters
        public int getCorePoolSize() {
            return corePoolSize;
        }
        
        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }
        
        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }
        
        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }
        
        public int getQueueCapacity() {
            return queueCapacity;
        }
        
        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
        
        public long getKeepAliveSeconds() {
            return keepAliveSeconds;
        }
        
        public void setKeepAliveSeconds(long keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }
        
        @Override
        public String toString() {
            return String.format("ThreadPoolConfig{corePoolSize=%d, maximumPoolSize=%d, " +
                    "queueCapacity=%d, keepAliveSeconds=%d}",
                    corePoolSize, maximumPoolSize, queueCapacity, keepAliveSeconds);
        }
    }
}