package com.example.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控服务
 * 监控数据库连接池、线程池和系统性能指标
 */
@Service
public class PerformanceMonitor {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);

    private final ThreadPoolTaskExecutor databaseQueryExecutor;
    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final OperatingSystemMXBean osBean;

    // 性能指标
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong slowQueries = new AtomicLong(0);
    private final AtomicLong totalQueryTime = new AtomicLong(0);
    private final AtomicLong connectionErrors = new AtomicLong(0);

    // Micrometer指标
    private Counter queryCounter;
    private Counter slowQueryCounter;
    private Counter connectionErrorCounter;
    private Timer queryTimer;

    public PerformanceMonitor(@Qualifier("databaseQueryExecutor") Executor databaseQueryExecutor,
                            DataSource dataSource,
                            MeterRegistry meterRegistry) {
        this.databaseQueryExecutor = (ThreadPoolTaskExecutor) databaseQueryExecutor;
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
    }

    @PostConstruct
    public void init() {
        // 初始化Micrometer指标
        initializeMetrics();
        logger.info("性能监控服务初始化完成");
    }

    /**
     * 初始化监控指标
     */
    private void initializeMetrics() {
        // 计数器指标
        queryCounter = Counter.builder("database.queries.total")
                .description("数据库查询总数")
                .register(meterRegistry);

        slowQueryCounter = Counter.builder("database.queries.slow")
                .description("慢查询总数")
                .register(meterRegistry);

        connectionErrorCounter = Counter.builder("database.connections.errors")
                .description("数据库连接错误总数")
                .register(meterRegistry);

        // 计时器指标
        queryTimer = Timer.builder("database.query.duration")
                .description("数据库查询执行时间")
                .register(meterRegistry);

        // 仪表盘指标
        Gauge.builder("threadpool.active.threads", this, PerformanceMonitor::getActiveThreadCount)
                .description("线程池活跃线程数")
                .register(meterRegistry);

        Gauge.builder("threadpool.queue.size", this, PerformanceMonitor::getQueueSize)
                .description("线程池队列大小")
                .register(meterRegistry);

        Gauge.builder("system.cpu.usage", this, PerformanceMonitor::getCpuUsage)
                .description("系统CPU使用率")
                .register(meterRegistry);

        Gauge.builder("database.connections.active", this, PerformanceMonitor::getActiveConnections)
                .description("数据库活跃连接数")
                .register(meterRegistry);
    }

    /**
     * 记录查询执行
     */
    public void recordQuery(long executionTime) {
        totalQueries.incrementAndGet();
        totalQueryTime.addAndGet(executionTime);
        
        queryCounter.increment();
        queryTimer.record(executionTime, java.util.concurrent.TimeUnit.MILLISECONDS);

        // 记录慢查询
        if (executionTime > 1000) { // 1秒以上为慢查询
            slowQueries.incrementAndGet();
            slowQueryCounter.increment();
            logger.warn("检测到慢查询，执行时间: {}ms", executionTime);
        }
    }

    /**
     * 记录连接错误
     */
    public void recordConnectionError() {
        connectionErrors.incrementAndGet();
        connectionErrorCounter.increment();
    }

    /**
     * 获取当前性能指标
     */
    public PerformanceMetrics getCurrentMetrics() {
        PerformanceMetrics metrics = new PerformanceMetrics();
        
        // 查询性能指标
        long queries = totalQueries.get();
        metrics.setTotalQueries(queries);
        metrics.setSlowQueries(slowQueries.get());
        metrics.setConnectionErrors(connectionErrors.get());
        
        // 平均查询时间
        if (queries > 0) {
            metrics.setAverageQueryTime(totalQueryTime.get() / queries);
        }

        // 系统性能指标
        metrics.setCpuUsage(getCpuUsage());
        metrics.setActiveThreads((int)getActiveThreadCount());
        metrics.setQueueSize((int)getQueueSize());
        metrics.setActiveConnections((int)getActiveConnections());

        return metrics;
    }

    /**
     * 定期输出性能报告
     */
    @Scheduled(fixedRate = 60000) // 每分钟输出一次
    public void logPerformanceReport() {
        try {
            PerformanceMetrics metrics = getCurrentMetrics();
            
            logger.info("=== 性能监控报告 ===");
            logger.info("数据库查询 - 总数: {}, 慢查询: {}, 平均耗时: {}ms", 
                       metrics.getTotalQueries(), metrics.getSlowQueries(), metrics.getAverageQueryTime());
            logger.info("系统性能 - CPU使用率: {:.2f}%, 活跃连接数: {}", 
                       metrics.getCpuUsage() * 100, metrics.getActiveConnections());
            logger.info("线程池状态 - 活跃线程: {}, 队列大小: {}", 
                       metrics.getActiveThreads(), metrics.getQueueSize());
            logger.info("连接错误数: {}", metrics.getConnectionErrors());
            logger.info("==================");
            
        } catch (Exception e) {
            logger.error("输出性能报告时发生错误", e);
        }
    }

    /**
     * 获取CPU使用率
     */
    private double getCpuUsage() {
        try {
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                return ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuLoad();
            }
        } catch (Exception e) {
            logger.debug("获取CPU使用率失败", e);
        }
        return 0.0;
    }

    /**
     * 获取活跃线程数
     */
    private double getActiveThreadCount() {
        ThreadPoolExecutor executor = databaseQueryExecutor.getThreadPoolExecutor();
        return executor != null ? executor.getActiveCount() : 0;
    }

    /**
     * 获取队列大小
     */
    private double getQueueSize() {
        ThreadPoolExecutor executor = databaseQueryExecutor.getThreadPoolExecutor();
        return executor != null ? executor.getQueue().size() : 0;
    }

    /**
     * 获取活跃数据库连接数
     */
    private double getActiveConnections() {
        try {
            if (dataSource instanceof com.zaxxer.hikari.HikariDataSource) {
                com.zaxxer.hikari.HikariDataSource hikariDS = (com.zaxxer.hikari.HikariDataSource) dataSource;
                return hikariDS.getHikariPoolMXBean().getActiveConnections();
            }
        } catch (Exception e) {
            logger.debug("获取活跃连接数失败", e);
        }
        return 0;
    }

    /**
     * 测试数据库连接
     */
    public boolean testDatabaseConnection() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5); // 5秒超时
        } catch (SQLException e) {
            recordConnectionError();
            logger.error("数据库连接测试失败", e);
            return false;
        }
    }
}
