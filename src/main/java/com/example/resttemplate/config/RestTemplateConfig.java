package com.example.resttemplate.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import com.example.resttemplate.exception.RestTemplateErrorHandler;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * RestTemplate 配置类
 * 
 * 最佳实践：
 * 1. 使用连接池管理 HTTP 连接
 * 2. 配置合理的超时时间
 * 3. 添加请求/响应日志拦截器
 * 4. 配置消息转换器
 * 5. 使用 RestTemplateBuilder 构建 RestTemplate
 */
@Slf4j
@Configuration
public class RestTemplateConfig {

    /**
     * 连接池最大连接数
     */
    private static final int MAX_TOTAL_CONNECTIONS = 200;

    /**
     * 每个路由的最大连接数
     */
    private static final int MAX_CONNECTIONS_PER_ROUTE = 50;

    /**
     * 连接超时时间（毫秒）
     */
    private static final int CONNECT_TIMEOUT = 5000;

    /**
     * 读取超时时间（毫秒）
     */
    private static final int READ_TIMEOUT = 10000;

    /**
     * 从连接池获取连接的超时时间（毫秒）
     */
    private static final int CONNECTION_REQUEST_TIMEOUT = 5000;

    /**
     * 配置 RestTemplate Bean
     * 
     * @param builder RestTemplateBuilder
     * @param errorHandler 错误处理器
     * @return RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, RestTemplateErrorHandler errorHandler) {
        // 创建连接池管理器
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(MAX_TOTAL_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(MAX_CONNECTIONS_PER_ROUTE);

        // 配置请求参数
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECT_TIMEOUT)
                .setSocketTimeout(READ_TIMEOUT)
                .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT)
                .build();

        // 创建 HttpClient
        CloseableHttpClient httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictIdleConnections(Duration.ofSeconds(30))
                .evictExpiredConnections()
                .build();

        // 创建请求工厂
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        
        // 使用 BufferingClientHttpRequestFactory 以便多次读取响应体（用于日志记录）
        BufferingClientHttpRequestFactory bufferingFactory = new BufferingClientHttpRequestFactory(factory);

        // 配置消息转换器
        List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
        messageConverters.add(new MappingJackson2HttpMessageConverter());

        // 配置拦截器
        List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>();
        interceptors.add(new LoggingRequestInterceptor());

        // 构建 RestTemplate
        RestTemplate restTemplate = builder
                .requestFactory(() -> bufferingFactory)
                .setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT))
                .setReadTimeout(Duration.ofMillis(READ_TIMEOUT))
                .messageConverters(messageConverters)
                .interceptors(interceptors)
                .build();

        // 设置错误处理器
        restTemplate.setErrorHandler(errorHandler);

        return restTemplate;
    }
}
