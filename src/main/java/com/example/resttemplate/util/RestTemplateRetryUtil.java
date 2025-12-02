package com.example.resttemplate.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.function.Supplier;

/**
 * RestTemplate 重试工具类
 * 
 * 最佳实践：
 * 1. 实现重试机制处理临时性错误
 * 2. 可配置重试次数和重试间隔
 * 3. 只对特定异常进行重试（如网络超时、5xx 错误）
 */
@Slf4j
public class RestTemplateRetryUtil {

    /**
     * 默认最大重试次数
     */
    private static final int DEFAULT_MAX_RETRIES = 3;

    /**
     * 默认重试间隔（毫秒）
     */
    private static final long DEFAULT_RETRY_INTERVAL = 1000;

    /**
     * 执行带重试的 GET 请求
     * 
     * @param restTemplate RestTemplate 实例
     * @param url 请求 URL
     * @param responseType 响应类型
     * @param <T> 响应类型泛型
     * @return 响应实体
     */
    public static <T> ResponseEntity<T> getWithRetry(
            RestTemplate restTemplate,
            String url,
            Class<T> responseType) {
        return executeWithRetry(
                () -> restTemplate.getForEntity(url, responseType),
                DEFAULT_MAX_RETRIES,
                DEFAULT_RETRY_INTERVAL
        );
    }

    /**
     * 执行带重试的请求
     * 
     * @param supplier 请求执行器
     * @param maxRetries 最大重试次数
     * @param retryInterval 重试间隔（毫秒）
     * @param <T> 响应类型泛型
     * @return 响应实体
     */
    public static <T> ResponseEntity<T> executeWithRetry(
            Supplier<ResponseEntity<T>> supplier,
            int maxRetries,
            long retryInterval) {

        int attempt = 0;
        Exception lastException = null;

        while (attempt <= maxRetries) {
            try {
                ResponseEntity<T> response = supplier.get();

                // 如果是 5xx 错误，进行重试
                if (response.getStatusCode().is5xxServerError() && attempt < maxRetries) {
                    log.warn("服务器错误，准备重试。状态码: {}, 尝试次数: {}/{}",
                            response.getStatusCode(), attempt + 1, maxRetries + 1);
                    attempt++;
                    Thread.sleep(retryInterval);
                    continue;
                }

                return response;

            } catch (RestClientException e) {
                lastException = e;
                log.warn("请求失败，准备重试。异常: {}, 尝试次数: {}/{}",
                        e.getMessage(), attempt + 1, maxRetries + 1);

                if (attempt < maxRetries) {
                    attempt++;
                    try {
                        Thread.sleep(retryInterval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("重试被中断", ie);
                    }
                } else {
                    throw new RuntimeException("重试次数已达上限", e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("重试被中断", e);
            }
        }

        throw new RuntimeException("请求失败，重试次数已达上限", lastException);
    }

    /**
     * 判断是否应该重试
     * 
     * @param statusCode HTTP 状态码
     * @return 是否应该重试
     */
    private static boolean shouldRetry(HttpStatus statusCode) {
        // 只对 5xx 服务器错误进行重试
        // 4xx 客户端错误通常不应该重试
        return statusCode != null && statusCode.is5xxServerError();
    }
}
