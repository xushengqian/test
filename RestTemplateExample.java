package com.example.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 使用示例
 */
@Service
public class RestTemplateExample {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    @Qualifier("customRestTemplate")
    private RestTemplate customRestTemplate;

    /**
     * 使用默认的 RestTemplate（已配置超时）
     */
    public void example1() {
        try {
            String url = "https://api.example.com/data";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            System.out.println("响应: " + response.getBody());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // 超时异常处理
            System.err.println("请求超时: " + e.getMessage());
        }
    }

    /**
     * 使用自定义的 RestTemplate
     */
    public void example2() {
        try {
            String url = "https://api.example.com/data";
            String result = customRestTemplate.getForObject(url, String.class);
            System.out.println("结果: " + result);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            System.err.println("请求超时: " + e.getMessage());
        }
    }

    /**
     * POST 请求示例
     */
    public void example3() {
        try {
            String url = "https://api.example.com/data";
            String requestBody = "{\"key\":\"value\"}";
            
            ResponseEntity<String> response = customRestTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new org.springframework.http.HttpEntity<>(requestBody, 
                        org.springframework.http.HttpHeaders.createHeaders()),
                    String.class
            );
            
            System.out.println("响应状态: " + response.getStatusCode());
            System.out.println("响应体: " + response.getBody());
        } catch (org.springframework.web.client.ResourceAccessException e) {
            System.err.println("请求超时: " + e.getMessage());
        }
    }
}
