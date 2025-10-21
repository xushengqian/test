package com.example.concurrent.db.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * 数据库连接池配置类
 * 使用HikariCP作为高性能连接池
 */
public class DatabaseConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
    
    private HikariDataSource dataSource;
    private final HikariConfig config;
    
    /**
     * 默认配置构造函数
     */
    public DatabaseConfig() {
        this.config = new HikariConfig();
        setDefaultConfig();
    }
    
    /**
     * 使用属性文件构造
     */
    public DatabaseConfig(Properties properties) {
        this.config = new HikariConfig(properties);
    }
    
    /**
     * 设置默认配置
     */
    private void setDefaultConfig() {
        // 基础配置
        config.setPoolName("OptimizedDBPool");
        config.setMaximumPoolSize(20);  // 最大连接数
        config.setMinimumIdle(5);       // 最小空闲连接数
        config.setIdleTimeout(600000);  // 10分钟
        config.setConnectionTimeout(30000); // 30秒
        config.setMaxLifetime(1800000); // 30分钟
        
        // 性能优化配置
        config.setAutoCommit(true);
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);
        
        // 连接泄漏检测
        config.setLeakDetectionThreshold(60000); // 1分钟
        
        logger.info("数据库连接池默认配置已设置");
    }
    
    /**
     * 配置MySQL数据源
     */
    public DatabaseConfig configureMySql(String host, int port, String database, 
                                         String username, String password) {
        String jdbcUrl = String.format("jdbc:mysql://%s:%d/%s?" +
                "useSSL=false&serverTimezone=UTC&characterEncoding=utf8&" +
                "rewriteBatchedStatements=true&cachePrepStmts=true&" +
                "prepStmtCacheSize=250&prepStmtCacheSqlLimit=2048",
                host, port, database);
        
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        
        // MySQL特定优化
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        
        logger.info("MySQL数据源配置完成: {}:{}/{}", host, port, database);
        return this;
    }
    
    /**
     * 配置PostgreSQL数据源
     */
    public DatabaseConfig configurePostgreSql(String host, int port, String database, 
                                              String username, String password) {
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
        
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        
        // PostgreSQL特定优化
        config.addDataSourceProperty("preparedStatementCacheQueries", "256");
        config.addDataSourceProperty("preparedStatementCacheSizeMiB", "5");
        
        logger.info("PostgreSQL数据源配置完成: {}:{}/{}", host, port, database);
        return this;
    }
    
    /**
     * 根据并发需求优化连接池大小
     * 
     * @param expectedConcurrency 预期并发数
     * @param avgQueryTime 平均查询时间（毫秒）
     * @param avgConnectionUseTime 平均连接使用时间（毫秒）
     */
    public DatabaseConfig optimizePoolSize(int expectedConcurrency, 
                                           long avgQueryTime, 
                                           long avgConnectionUseTime) {
        // 计算所需连接数
        // 公式: pool size = (并发数 * 平均连接使用时间) / 平均查询时间
        int optimalPoolSize = (int) Math.ceil(
            (double) (expectedConcurrency * avgConnectionUseTime) / avgQueryTime
        );
        
        // 添加缓冲
        optimalPoolSize = (int) (optimalPoolSize * 1.2);
        
        // 设置合理的边界
        optimalPoolSize = Math.max(optimalPoolSize, 10);  // 最小10个连接
        optimalPoolSize = Math.min(optimalPoolSize, 100); // 最大100个连接
        
        config.setMaximumPoolSize(optimalPoolSize);
        config.setMinimumIdle(Math.max(5, optimalPoolSize / 4));
        
        logger.info("连接池优化: 最大连接数={}, 最小空闲连接数={}, " +
                   "预期并发={}, 平均查询时间={}ms, 平均连接使用时间={}ms",
                   config.getMaximumPoolSize(), config.getMinimumIdle(),
                   expectedConcurrency, avgQueryTime, avgConnectionUseTime);
        
        return this;
    }
    
    /**
     * 设置连接池大小
     */
    public DatabaseConfig setPoolSize(int minIdle, int maxPoolSize) {
        if (minIdle < 0 || maxPoolSize < minIdle) {
            throw new IllegalArgumentException("无效的连接池大小配置");
        }
        
        config.setMinimumIdle(minIdle);
        config.setMaximumPoolSize(maxPoolSize);
        
        logger.info("连接池大小设置: 最小空闲={}, 最大连接={}", minIdle, maxPoolSize);
        return this;
    }
    
    /**
     * 设置连接超时时间
     */
    public DatabaseConfig setTimeouts(long connectionTimeoutMs, long idleTimeoutMs, long maxLifetimeMs) {
        config.setConnectionTimeout(connectionTimeoutMs);
        config.setIdleTimeout(idleTimeoutMs);
        config.setMaxLifetime(maxLifetimeMs);
        
        logger.info("超时设置: 连接超时={}ms, 空闲超时={}ms, 最大生命周期={}ms",
                   connectionTimeoutMs, idleTimeoutMs, maxLifetimeMs);
        return this;
    }
    
    /**
     * 启用指标收集
     */
    public DatabaseConfig enableMetrics() {
        config.setMetricRegistry(null); // 可以设置Micrometer或其他指标收集器
        config.setHealthCheckRegistry(null); // 健康检查
        
        logger.info("指标收集已启用");
        return this;
    }
    
    /**
     * 构建数据源
     */
    public DataSource build() {
        if (dataSource != null) {
            logger.warn("数据源已存在，将关闭旧数据源并创建新的");
            close();
        }
        
        // 验证配置
        validateConfig();
        
        dataSource = new HikariDataSource(config);
        logger.info("数据源创建成功: {}", config.getPoolName());
        
        return dataSource;
    }
    
    /**
     * 验证配置
     */
    private void validateConfig() {
        if (config.getJdbcUrl() == null || config.getJdbcUrl().isEmpty()) {
            throw new IllegalStateException("JDBC URL未配置");
        }
        if (config.getUsername() == null || config.getUsername().isEmpty()) {
            throw new IllegalStateException("数据库用户名未配置");
        }
    }
    
    /**
     * 获取数据源
     */
    public DataSource getDataSource() {
        if (dataSource == null) {
            throw new IllegalStateException("数据源尚未构建，请先调用build()方法");
        }
        return dataSource;
    }
    
    /**
     * 获取连接池统计信息
     */
    public PoolStats getPoolStats() {
        if (dataSource == null) {
            throw new IllegalStateException("数据源尚未构建");
        }
        
        return new PoolStats(
            dataSource.getHikariPoolMXBean().getActiveConnections(),
            dataSource.getHikariPoolMXBean().getIdleConnections(),
            dataSource.getHikariPoolMXBean().getTotalConnections(),
            dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()
        );
    }
    
    /**
     * 关闭数据源
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("数据源已关闭");
        }
    }
    
    /**
     * 连接池统计信息
     */
    public static class PoolStats {
        private final int activeConnections;
        private final int idleConnections;
        private final int totalConnections;
        private final int threadsAwaitingConnection;
        
        public PoolStats(int activeConnections, int idleConnections, 
                         int totalConnections, int threadsAwaitingConnection) {
            this.activeConnections = activeConnections;
            this.idleConnections = idleConnections;
            this.totalConnections = totalConnections;
            this.threadsAwaitingConnection = threadsAwaitingConnection;
        }
        
        // Getters
        public int getActiveConnections() {
            return activeConnections;
        }
        
        public int getIdleConnections() {
            return idleConnections;
        }
        
        public int getTotalConnections() {
            return totalConnections;
        }
        
        public int getThreadsAwaitingConnection() {
            return threadsAwaitingConnection;
        }
        
        @Override
        public String toString() {
            return String.format("PoolStats{active=%d, idle=%d, total=%d, waiting=%d}",
                    activeConnections, idleConnections, totalConnections, threadsAwaitingConnection);
        }
    }
}