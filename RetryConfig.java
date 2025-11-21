package org.springblade.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * 重试配置类
 * 如果使用 @Retryable 注解，需要启用此配置
 * 
 * 注意：如果使用手动重试机制，则不需要此配置类
 */
@Configuration
@EnableRetry
public class RetryConfig {
    // Spring Retry 配置
    // 详细配置可以在 application.yml 中设置
}
