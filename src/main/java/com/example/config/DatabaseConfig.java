package com.example.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * 数据库配置类
 * 优化数据库连接池配置以支持高并发查询
 */
@Configuration
public class DatabaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    /**
     * 记录数据库连接池配置信息
     */
    @PostConstruct
    public void logDatabaseConfig() {
        int cpuCores = Runtime.getRuntime().availableProcessors();
        int recommendedMaxPoolSize = cpuCores * 2 + 2; // 假设有2个有效磁盘
        
        logger.info("=== 数据库连接池配置建议 ===");
        logger.info("CPU核心数: {}", cpuCores);
        logger.info("建议的最大连接池大小: {}", Math.max(recommendedMaxPoolSize, 10));
        logger.info("配置公式: CPU核心数 * 2 + 有效磁盘数");
        logger.info("==============================");
    }
}