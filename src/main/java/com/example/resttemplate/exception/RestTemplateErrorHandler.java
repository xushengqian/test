package com.example.resttemplate.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * RestTemplate 错误处理器
 * 
 * 最佳实践：
 * 1. 自定义错误处理逻辑
 * 2. 记录详细的错误信息
 * 3. 根据不同的 HTTP 状态码进行不同的处理
 */
@Slf4j
@Component
public class RestTemplateErrorHandler extends DefaultResponseErrorHandler {

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        HttpStatus statusCode = HttpStatus.resolve(response.getRawStatusCode());
        String responseBody = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);

        log.error("RestTemplate 请求失败 - Status: {}, Body: {}", statusCode, responseBody);

        // 根据不同的状态码进行不同的处理
        if (statusCode != null) {
            switch (statusCode.series()) {
                case CLIENT_ERROR:
                    throw new RestTemplateException(
                            "客户端错误: " + statusCode.getReasonPhrase(),
                            statusCode,
                            responseBody
                    );
                case SERVER_ERROR:
                    throw new RestTemplateException(
                            "服务器错误: " + statusCode.getReasonPhrase(),
                            statusCode,
                            responseBody
                    );
                default:
                    throw new RestTemplateException(
                            "未知错误: " + statusCode.getReasonPhrase(),
                            statusCode,
                            responseBody
                    );
            }
        }

        super.handleError(response);
    }

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        // 可以自定义哪些状态码被认为是错误
        return super.hasError(response);
    }
}
