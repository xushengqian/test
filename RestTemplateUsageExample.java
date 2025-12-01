package com.example.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

/**
 * RestTemplate 使用示例
 * 演示如何使用配置好的 RestTemplate 进行 HTTP 请求
 */
@Service
public class RestTemplateUsageExample {

    // 注入配置好的 RestTemplate
    @Autowired
    @Qualifier("restTemplateWithBuilder")
    private RestTemplate restTemplate;

    /**
     * GET 请求示例
     */
    public String getExample(String url) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getBody();
        } catch (ResourceAccessException e) {
            // 超时异常会抛出 ResourceAccessException
            System.err.println("请求超时: " + e.getMessage());
            throw new RuntimeException("请求超时", e);
        }
    }

    /**
     * POST 请求示例
     */
    public <T, R> R postExample(String url, T requestBody, Class<R> responseType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<T> requestEntity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<R> response = restTemplate.postForEntity(url, requestEntity, responseType);
            return response.getBody();
        } catch (ResourceAccessException e) {
            System.err.println("请求超时: " + e.getMessage());
            throw new RuntimeException("请求超时", e);
        }
    }

    /**
     * 带重试机制的请求示例
     */
    public String getWithRetry(String url, int maxRetries) {
        int retries = 0;
        while (retries < maxRetries) {
            try {
                return restTemplate.getForObject(url, String.class);
            } catch (ResourceAccessException e) {
                retries++;
                if (retries >= maxRetries) {
                    throw new RuntimeException("请求失败，已重试 " + maxRetries + " 次", e);
                }
                
                // 等待后重试
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", ie);
                }
            }
        }
        return null;
    }

    /**
     * 自定义超时的请求示例
     * 为特定请求设置不同的超时时间
     */
    public String getWithCustomTimeout(String url, int connectTimeout, int readTimeout) {
        // 创建临时的 RestTemplate 实例，使用自定义超时
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = 
            new org.springframework.http.client.SimpleClientHttpRequestFactory();
        
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        
        RestTemplate customRestTemplate = new RestTemplate(factory);
        
        return customRestTemplate.getForObject(url, String.class);
    }
}
