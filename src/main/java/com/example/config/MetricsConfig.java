package com.example.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 指标监控配置
 */
@Configuration
public class MetricsConfig {

    /**
     * 配置简单的指标注册表
     * 在生产环境中可以替换为Prometheus、InfluxDB等
     */
    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }
}