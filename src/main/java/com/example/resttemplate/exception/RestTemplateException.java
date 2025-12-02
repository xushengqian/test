package com.example.resttemplate.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * RestTemplate 自定义异常
 * 
 * 最佳实践：
 * 1. 定义统一的异常类
 * 2. 包含 HTTP 状态码和错误信息
 * 3. 便于统一异常处理
 */
@Getter
public class RestTemplateException extends RuntimeException {

    private final HttpStatus statusCode;
    private final String responseBody;

    public RestTemplateException(String message, HttpStatus statusCode, String responseBody) {
        super(message);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public RestTemplateException(String message, Throwable cause, HttpStatus statusCode, String responseBody) {
        super(message, cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }
}
