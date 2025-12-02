package com.example.resttemplate.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 请求/响应日志拦截器
 * 
 * 最佳实践：
 * 1. 记录请求和响应的详细信息
 * 2. 记录请求耗时
 * 3. 注意敏感信息的脱敏处理
 */
@Slf4j
public class LoggingRequestInterceptor implements ClientHttpRequestInterceptor {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        long startTime = System.currentTimeMillis();
        String requestTime = LocalDateTime.now().format(FORMATTER);

        // 记录请求信息
        logRequest(request, body, requestTime);

        // 执行请求
        ClientHttpResponse response = execution.execute(request, body);

        // 记录响应信息
        long duration = System.currentTimeMillis() - startTime;
        logResponse(response, requestTime, duration);

        return response;
    }

    private void logRequest(HttpRequest request, byte[] body, String requestTime) {
        log.info("========== RestTemplate Request ==========");
        log.info("Request Time: {}", requestTime);
        log.info("Method: {}", request.getMethod());
        log.info("URI: {}", request.getURI());
        log.info("Headers: {}", request.getHeaders());

        if (body.length > 0) {
            String bodyStr = new String(body, StandardCharsets.UTF_8);
            // 注意：生产环境中应对敏感信息进行脱敏处理
            log.info("Request Body: {}", bodyStr);
        }
        log.info("==========================================");
    }

    private void logResponse(ClientHttpResponse response, String requestTime, long duration) throws IOException {
        log.info("========== RestTemplate Response ==========");
        log.info("Request Time: {}", requestTime);
        log.info("Status Code: {}", response.getStatusCode());
        log.info("Status Text: {}", response.getStatusText());
        log.info("Headers: {}", response.getHeaders());

        // 读取响应体（注意：这会消耗响应流，需要确保使用 BufferingClientHttpRequestFactory）
        byte[] bodyBytes = StreamUtils.copyToByteArray(response.getBody());
        if (bodyBytes.length > 0) {
            String bodyStr = new String(bodyBytes, StandardCharsets.UTF_8);
            log.info("Response Body: {}", bodyStr);
        }

        log.info("Duration: {} ms", duration);
        log.info("===========================================");
    }
}
