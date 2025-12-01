package com.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 从配置文件读取超时参数的方式
 */
@Configuration
public class RestTemplateConfigWithProperties {

    /**
     * 使用 @ConfigurationProperties 读取配置
     */
    @Bean
    @ConfigurationProperties(prefix = "rest.template")
    public RestTemplateProperties restTemplateProperties() {
        return new RestTemplateProperties();
    }

    /**
     * 使用配置文件中的参数创建 RestTemplate
     */
    @Bean("restTemplateFromConfig")
    public RestTemplate restTemplateFromConfig(
            RestTemplateBuilder builder,
            RestTemplateProperties properties) {
        
        return builder
                .setConnectTimeout(Duration.ofSeconds(properties.getConnectTimeout()))
                .setReadTimeout(Duration.ofSeconds(properties.getReadTimeout()))
                .build();
    }

    /**
     * RestTemplate 配置属性类
     */
    public static class RestTemplateProperties {
        private int connectTimeout = 5;
        private int readTimeout = 10;
        private int connectionRequestTimeout = 3;

        public int getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public int getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
        }

        public int getConnectionRequestTimeout() {
            return connectionRequestTimeout;
        }

        public void setConnectionRequestTimeout(int connectionRequestTimeout) {
            this.connectionRequestTimeout = connectionRequestTimeout;
        }
    }
}
