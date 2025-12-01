package com.example.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate 超时配置类
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 方式一：使用 RestTemplateBuilder 配置超时（推荐）
     * 
     * @param builder RestTemplateBuilder
     * @return RestTemplate
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                // 连接超时时间：5秒
                .setConnectTimeout(Duration.ofSeconds(5))
                // 读取超时时间：10秒
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 方式二：通过 ClientHttpRequestFactory 配置超时
     * 
     * @return RestTemplate
     */
    @Bean(name = "customRestTemplate")
    public RestTemplate customRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 连接超时时间：5秒（5000毫秒）
        factory.setConnectTimeout(5000);
        
        // 读取超时时间：10秒（10000毫秒）
        factory.setReadTimeout(10000);
        
        return new RestTemplate(factory);
    }

    /**
     * 方式三：使用 HttpComponentsClientHttpRequestFactory（需要添加 Apache HttpComponents 依赖）
     * 这种方式支持更细粒度的超时控制
     * 
     * @return RestTemplate
     */
    /*
    @Bean(name = "advancedRestTemplate")
    public RestTemplate advancedRestTemplate() {
        // 需要添加依赖：
        // <dependency>
        //     <groupId>org.apache.httpcomponents</groupId>
        //     <artifactId>httpclient</artifactId>
        // </dependency>
        
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(5000)           // 连接超时：5秒
                .setSocketTimeout(10000)           // 读取超时：10秒
                .setConnectionRequestTimeout(3000)  // 从连接池获取连接的超时：3秒
                .build();
        
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
        
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        
        return new RestTemplate(factory);
    }
    */
}
