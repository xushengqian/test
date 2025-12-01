package com.example.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 超时配置示例
 * 
 * 提供了三种常用的超时配置方式：
 * 1. 使用 RestTemplateBuilder (推荐)
 * 2. 使用 SimpleClientHttpRequestFactory
 * 3. 使用 Apache HttpClient (更灵活)
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 方式1: 使用 RestTemplateBuilder 配置超时 (推荐方式)
     * 
     * 优点：
     * - 代码简洁
     * - 类型安全
     * - Spring Boot 推荐方式
     */
    @Bean("restTemplateWithBuilder")
    public RestTemplate restTemplateWithBuilder(RestTemplateBuilder builder) {
        return builder
                // 连接超时：建立连接的最长时间
                .setConnectTimeout(Duration.ofSeconds(5))
                // 读取超时：从服务器读取数据的最长时间
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 方式2: 使用 SimpleClientHttpRequestFactory 配置超时
     * 
     * 适用场景：
     * - 简单的 HTTP 请求
     * - 不需要连接池管理
     */
    @Bean("restTemplateWithSimpleFactory")
    public RestTemplate restTemplateWithSimpleFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 连接超时时间（毫秒）
        factory.setConnectTimeout(5000);
        
        // 读取超时时间（毫秒）
        factory.setReadTimeout(10000);
        
        return new RestTemplate(factory);
    }

    /**
     * 方式3: 使用 Apache HttpClient 配置超时 (最灵活)
     * 
     * 优点：
     * - 支持连接池管理
     * - 更多的配置选项
     * - 更好的性能
     * 
     * 注意：需要添加依赖
     * <dependency>
     *     <groupId>org.apache.httpcomponents</groupId>
     *     <artifactId>httpclient</artifactId>
     * </dependency>
     */
    @Bean("restTemplateWithHttpClient")
    public RestTemplate restTemplateWithHttpClient() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        
        // 连接超时时间（毫秒）
        factory.setConnectTimeout(5000);
        
        // 读取超时时间（毫秒）
        factory.setReadTimeout(10000);
        
        // 连接请求超时时间（从连接池获取连接的超时时间）
        factory.setConnectionRequestTimeout(3000);
        
        return new RestTemplate(factory);
    }

    /**
     * 方式4: 使用自定义的 HttpClient 进行更高级配置
     * 
     * 包含连接池管理等高级特性
     */
    @Bean("restTemplateWithPooling")
    public RestTemplate restTemplateWithPooling() {
        // 创建连接管理器
        org.apache.http.impl.conn.PoolingHttpClientConnectionManager connectionManager = 
            new org.apache.http.impl.conn.PoolingHttpClientConnectionManager();
        
        // 设置最大连接数
        connectionManager.setMaxTotal(200);
        // 设置每个路由的最大连接数
        connectionManager.setDefaultMaxPerRoute(20);
        
        // 配置请求超时参数
        org.apache.http.client.config.RequestConfig requestConfig = org.apache.http.client.config.RequestConfig.custom()
                // 连接超时时间（毫秒）
                .setConnectTimeout(5000)
                // 从连接池获取连接的超时时间（毫秒）
                .setConnectionRequestTimeout(3000)
                // Socket 读取超时时间（毫秒）
                .setSocketTimeout(10000)
                .build();
        
        // 创建 HttpClient
        org.apache.http.client.HttpClient httpClient = org.apache.http.impl.client.HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
        
        // 创建 RestTemplate
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        
        return new RestTemplate(factory);
    }
}
