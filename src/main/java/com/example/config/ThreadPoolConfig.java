package com.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程池配置类
 * 针对数据库并发查询进行优化的线程池配置
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolConfig.class);

    /**
     * 数据库查询专用线程池
     * 根据数据库连接池大小和系统性能进行优化配置
     */
    @Bean("databaseQueryExecutor")
    public Executor databaseQueryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 获取系统CPU核心数
        int cpuCores = Runtime.getRuntime().availableProcessors();
        logger.info("系统CPU核心数: {}", cpuCores);
        
        // 核心线程数设置为CPU核心数，确保基本并发能力
        int corePoolSize = cpuCores;
        
        // 最大线程数设置为CPU核心数的2倍，适合I/O密集型任务（数据库查询）
        // 对于数据库查询，由于涉及网络I/O和磁盘I/O，可以设置更多线程
        int maxPoolSize = cpuCores * 2;
        
        // 如果数据库连接池较小，需要限制最大线程数避免连接池耗尽
        // 通常设置为连接池大小的80%左右
        int dbConnectionPoolSize = 20; // 从配置文件读取
        maxPoolSize = Math.min(maxPoolSize, (int)(dbConnectionPoolSize * 0.8));
        
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        
        // 队列容量设置：平衡内存使用和响应时间
        // 设置为最大线程数的5-10倍
        executor.setQueueCapacity(maxPoolSize * 8);
        
        // 线程空闲时间：60秒，避免线程频繁创建销毁
        executor.setKeepAliveSeconds(60);
        
        // 线程名前缀，便于监控和调试
        executor.setThreadNamePrefix("db-query-");
        
        // 拒绝策略：使用调用者线程执行，避免任务丢失
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 等待所有任务完成后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        
        logger.info("数据库查询线程池配置 - 核心线程数: {}, 最大线程数: {}, 队列容量: {}", 
                   corePoolSize, maxPoolSize, maxPoolSize * 8);
        
        return executor;
    }

    /**
     * 通用异步任务线程池
     * 用于非数据库相关的异步任务
     */
    @Bean("asyncTaskExecutor")
    public Executor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        int cpuCores = Runtime.getRuntime().availableProcessors();
        
        // 对于CPU密集型任务，线程数接近CPU核心数
        executor.setCorePoolSize(cpuCores / 2);
        executor.setMaxPoolSize(cpuCores);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("async-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        
        logger.info("异步任务线程池配置 - 核心线程数: {}, 最大线程数: {}, 队列容量: {}", 
                   executor.getCorePoolSize(), executor.getMaxPoolSize(), 50);
        
        return executor;
    }

    /**
     * 自定义拒绝策略：记录被拒绝的任务信息
     */
    public static class LoggingRejectedExecutionHandler implements RejectedExecutionHandler {
        private static final Logger logger = LoggerFactory.getLogger(LoggingRejectedExecutionHandler.class);
        
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            logger.warn("任务被拒绝执行: {}, 线程池状态 - 活跃线程: {}, 队列大小: {}, 已完成任务: {}", 
                       r.toString(), 
                       executor.getActiveCount(), 
                       executor.getQueue().size(), 
                       executor.getCompletedTaskCount());
            
            // 使用调用者线程执行任务
            if (!executor.isShutdown()) {
                r.run();
            }
        }
    }
}