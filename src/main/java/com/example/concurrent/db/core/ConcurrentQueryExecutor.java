package com.example.concurrent.db.core;

import com.example.concurrent.db.utils.OptimalThreadCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 并发数据库查询执行器
 * 支持批量查询、并发执行、自动优化线程池大小
 */
public class ConcurrentQueryExecutor implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(ConcurrentQueryExecutor.class);
    
    private final DataSource dataSource;
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService monitorExecutor;
    
    // 性能指标
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong successfulQueries = new AtomicLong(0);
    private final AtomicLong failedQueries = new AtomicLong(0);
    private final AtomicLong totalResponseTime = new AtomicLong(0);
    
    // 配置参数
    private int batchSize = 1000;
    private long queryTimeout = 30000; // 30秒
    private boolean autoOptimize = true;
    
    /**
     * 构造函数
     * 
     * @param dataSource 数据源
     * @param corePoolSize 核心线程数
     * @param maxPoolSize 最大线程数
     */
    public ConcurrentQueryExecutor(DataSource dataSource, int corePoolSize, int maxPoolSize) {
        this.dataSource = dataSource;
        
        // 创建线程池
        this.executor = new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(corePoolSize * 10),
            new ThreadFactory() {
                private final AtomicLong counter = new AtomicLong(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("db-query-executor-" + counter.incrementAndGet());
                    thread.setDaemon(false);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 预启动核心线程
        executor.prestartAllCoreThreads();
        
        // 创建监控线程池
        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("db-query-monitor");
            thread.setDaemon(true);
            return thread;
        });
        
        // 启动监控任务
        startMonitoring();
        
        logger.info("并发查询执行器初始化完成: 核心线程数={}, 最大线程数={}", 
                   corePoolSize, maxPoolSize);
    }
    
    /**
     * 使用自动计算的线程数构造
     */
    public ConcurrentQueryExecutor(DataSource dataSource) {
        this(dataSource, 
             OptimalThreadCalculator.calculateForIoIntensive(),
             OptimalThreadCalculator.calculateForIoIntensive() * 2);
    }
    
    /**
     * 执行单个查询
     */
    public <T> CompletableFuture<T> executeQueryAsync(String sql, 
                                                       Function<ResultSet, T> resultMapper,
                                                       Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            totalQueries.incrementAndGet();
            
            try {
                T result = executeQuery(sql, resultMapper, params);
                successfulQueries.incrementAndGet();
                totalResponseTime.addAndGet(System.currentTimeMillis() - startTime);
                return result;
            } catch (Exception e) {
                failedQueries.incrementAndGet();
                logger.error("查询执行失败: {}", sql, e);
                throw new CompletionException(e);
            }
        }, executor);
    }
    
    /**
     * 同步执行查询
     */
    private <T> T executeQuery(String sql, Function<ResultSet, T> resultMapper, Object... params) 
            throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            // 设置查询超时
            stmt.setQueryTimeout((int) (queryTimeout / 1000));
            
            // 设置参数
            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }
            
            // 执行查询
            try (ResultSet rs = stmt.executeQuery()) {
                return resultMapper.apply(rs);
            }
        }
    }
    
    /**
     * 批量并发执行查询
     */
    public <T> List<Future<T>> executeBatchQueries(List<QueryTask<T>> queryTasks) {
        logger.info("开始批量执行{}个查询任务", queryTasks.size());
        
        List<Future<T>> futures = new ArrayList<>(queryTasks.size());
        
        for (QueryTask<T> task : queryTasks) {
            CompletableFuture<T> future = executeQueryAsync(
                task.getSql(), 
                task.getResultMapper(), 
                task.getParams()
            );
            futures.add(future);
        }
        
        return futures;
    }
    
    /**
     * 分页并发查询
     */
    public <T> List<T> executePagedQuery(String countSql, String querySql, 
                                         Function<ResultSet, List<T>> resultMapper,
                                         int pageSize) throws Exception {
        // 先获取总记录数
        int totalCount = executeQuery(countSql, rs -> {
            try {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
        
        if (totalCount == 0) {
            return new ArrayList<>();
        }
        
        logger.info("总记录数: {}, 每页大小: {}", totalCount, pageSize);
        
        // 计算页数
        int pageCount = (totalCount + pageSize - 1) / pageSize;
        
        // 创建分页查询任务
        List<CompletableFuture<List<T>>> futures = new ArrayList<>(pageCount);
        
        for (int page = 0; page < pageCount; page++) {
            final int offset = page * pageSize;
            final String pagedSql = querySql + " LIMIT ? OFFSET ?";
            
            CompletableFuture<List<T>> future = executeQueryAsync(
                pagedSql,
                resultMapper,
                pageSize, offset
            );
            
            futures.add(future);
        }
        
        // 等待所有查询完成并合并结果
        List<T> allResults = new ArrayList<>(totalCount);
        for (CompletableFuture<List<T>> future : futures) {
            allResults.addAll(future.get(queryTimeout, TimeUnit.MILLISECONDS));
        }
        
        logger.info("分页查询完成，返回{}条记录", allResults.size());
        return allResults;
    }
    
    /**
     * 执行并发更新操作
     */
    public CompletableFuture<Integer> executeUpdateAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> {
            totalQueries.incrementAndGet();
            long startTime = System.currentTimeMillis();
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                // 设置参数
                for (int i = 0; i < params.length; i++) {
                    stmt.setObject(i + 1, params[i]);
                }
                
                int affectedRows = stmt.executeUpdate();
                successfulQueries.incrementAndGet();
                totalResponseTime.addAndGet(System.currentTimeMillis() - startTime);
                
                logger.debug("更新操作完成，影响{}行", affectedRows);
                return affectedRows;
                
            } catch (SQLException e) {
                failedQueries.incrementAndGet();
                logger.error("更新操作失败: {}", sql, e);
                throw new CompletionException(e);
            }
        }, executor);
    }
    
    /**
     * 批量插入操作
     */
    public int[] executeBatchInsert(String sql, List<Object[]> batchParams) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Object[] params : batchParams) {
                    for (int i = 0; i < params.length; i++) {
                        stmt.setObject(i + 1, params[i]);
                    }
                    stmt.addBatch();
                    
                    // 分批执行
                    if (stmt.getParameterMetaData().getParameterCount() % batchSize == 0) {
                        stmt.executeBatch();
                        stmt.clearBatch();
                    }
                }
                
                // 执行剩余的批次
                int[] results = stmt.executeBatch();
                conn.commit();
                
                logger.info("批量插入完成，插入{}条记录", batchParams.size());
                return results;
                
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }
    
    /**
     * 动态调整线程池大小
     */
    public void optimizeThreadPoolSize() {
        if (!autoOptimize) {
            return;
        }
        
        // 获取当前性能指标
        long avgResponseTime = getAverageResponseTime();
        int activeThreads = executor.getActiveCount();
        int queueSize = executor.getQueue().size();
        
        logger.info("当前性能指标: 平均响应时间={}ms, 活跃线程={}, 队列大小={}",
                   avgResponseTime, activeThreads, queueSize);
        
        // 根据队列大小和响应时间调整
        if (queueSize > executor.getCorePoolSize() && avgResponseTime > 1000) {
            // 队列积压且响应慢，增加线程
            int newSize = Math.min(
                executor.getCorePoolSize() + 2,
                executor.getMaximumPoolSize()
            );
            executor.setCorePoolSize(newSize);
            logger.info("增加核心线程数到: {}", newSize);
            
        } else if (queueSize == 0 && activeThreads < executor.getCorePoolSize() / 2) {
            // 负载较低，减少线程
            int newSize = Math.max(
                executor.getCorePoolSize() - 1,
                OptimalThreadCalculator.getCpuCores()
            );
            executor.setCorePoolSize(newSize);
            logger.info("减少核心线程数到: {}", newSize);
        }
    }
    
    /**
     * 启动监控
     */
    private void startMonitoring() {
        monitorExecutor.scheduleAtFixedRate(() -> {
            try {
                // 输出性能指标
                logger.info("执行器状态: 活跃线程={}, 完成任务={}, 队列大小={}, " +
                           "总查询={}, 成功={}, 失败={}, 平均响应时间={}ms",
                           executor.getActiveCount(),
                           executor.getCompletedTaskCount(),
                           executor.getQueue().size(),
                           totalQueries.get(),
                           successfulQueries.get(),
                           failedQueries.get(),
                           getAverageResponseTime());
                
                // 自动优化
                optimizeThreadPoolSize();
                
            } catch (Exception e) {
                logger.error("监控任务执行失败", e);
            }
        }, 10, 30, TimeUnit.SECONDS);
    }
    
    /**
     * 获取平均响应时间
     */
    public long getAverageResponseTime() {
        long successful = successfulQueries.get();
        if (successful == 0) {
            return 0;
        }
        return totalResponseTime.get() / successful;
    }
    
    /**
     * 获取执行器统计信息
     */
    public ExecutorStats getStats() {
        return new ExecutorStats(
            executor.getCorePoolSize(),
            executor.getMaximumPoolSize(),
            executor.getActiveCount(),
            executor.getCompletedTaskCount(),
            executor.getQueue().size(),
            totalQueries.get(),
            successfulQueries.get(),
            failedQueries.get(),
            getAverageResponseTime()
        );
    }
    
    /**
     * 设置批量大小
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
    
    /**
     * 设置查询超时时间
     */
    public void setQueryTimeout(long queryTimeout) {
        this.queryTimeout = queryTimeout;
    }
    
    /**
     * 设置是否自动优化
     */
    public void setAutoOptimize(boolean autoOptimize) {
        this.autoOptimize = autoOptimize;
    }
    
    /**
     * 关闭执行器
     */
    @Override
    public void close() {
        logger.info("关闭并发查询执行器...");
        
        // 关闭监控
        monitorExecutor.shutdown();
        
        // 关闭线程池
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("并发查询执行器已关闭");
    }
    
    /**
     * 查询任务
     */
    public static class QueryTask<T> {
        private final String sql;
        private final Function<ResultSet, T> resultMapper;
        private final Object[] params;
        
        public QueryTask(String sql, Function<ResultSet, T> resultMapper, Object... params) {
            this.sql = sql;
            this.resultMapper = resultMapper;
            this.params = params;
        }
        
        public String getSql() {
            return sql;
        }
        
        public Function<ResultSet, T> getResultMapper() {
            return resultMapper;
        }
        
        public Object[] getParams() {
            return params;
        }
    }
    
    /**
     * 执行器统计信息
     */
    public static class ExecutorStats {
        private final int corePoolSize;
        private final int maxPoolSize;
        private final int activeThreads;
        private final long completedTasks;
        private final int queueSize;
        private final long totalQueries;
        private final long successfulQueries;
        private final long failedQueries;
        private final long avgResponseTime;
        
        public ExecutorStats(int corePoolSize, int maxPoolSize, int activeThreads,
                            long completedTasks, int queueSize, long totalQueries,
                            long successfulQueries, long failedQueries, long avgResponseTime) {
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.activeThreads = activeThreads;
            this.completedTasks = completedTasks;
            this.queueSize = queueSize;
            this.totalQueries = totalQueries;
            this.successfulQueries = successfulQueries;
            this.failedQueries = failedQueries;
            this.avgResponseTime = avgResponseTime;
        }
        
        // Getters
        public int getCorePoolSize() { return corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public int getActiveThreads() { return activeThreads; }
        public long getCompletedTasks() { return completedTasks; }
        public int getQueueSize() { return queueSize; }
        public long getTotalQueries() { return totalQueries; }
        public long getSuccessfulQueries() { return successfulQueries; }
        public long getFailedQueries() { return failedQueries; }
        public long getAvgResponseTime() { return avgResponseTime; }
        
        @Override
        public String toString() {
            return String.format("ExecutorStats{threads=%d/%d, active=%d, completed=%d, " +
                    "queue=%d, queries=%d, success=%d, failed=%d, avgTime=%dms}",
                    corePoolSize, maxPoolSize, activeThreads, completedTasks, 
                    queueSize, totalQueries, successfulQueries, failedQueries, avgResponseTime);
        }
    }
}