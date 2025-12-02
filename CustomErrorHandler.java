import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * 自定义错误处理器
 */
public class CustomErrorHandler implements ResponseErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomErrorHandler.class);

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        HttpStatus.Series series = response.getStatusCode().series();
        return (series == HttpStatus.Series.CLIENT_ERROR || 
                series == HttpStatus.Series.SERVER_ERROR);
    }

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
        String statusText = response.getStatusText();
        
        // 读取错误响应体
        String responseBody = new BufferedReader(
                new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
        
        log.error("HTTP请求失败 - Status: {}, Message: {}, Body: {}", 
                  statusCode, statusText, responseBody);
        
        // 根据不同的状态码抛出不同的异常
        switch (statusCode.series()) {
            case CLIENT_ERROR:
                if (statusCode == HttpStatus.NOT_FOUND) {
                    throw new ResourceNotFoundException("资源未找到: " + responseBody);
                } else if (statusCode == HttpStatus.UNAUTHORIZED) {
                    throw new UnauthorizedException("未授权: " + responseBody);
                } else if (statusCode == HttpStatus.BAD_REQUEST) {
                    throw new BadRequestException("错误的请求: " + responseBody);
                }
                throw new ClientException("客户端错误: " + responseBody);
                
            case SERVER_ERROR:
                throw new ServerException("服务器错误: " + responseBody);
                
            default:
                throw new RestApiException("API调用失败: " + responseBody);
        }
    }
}

// 自定义异常类
class RestApiException extends RuntimeException {
    public RestApiException(String message) {
        super(message);
    }
}

class ResourceNotFoundException extends RestApiException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

class UnauthorizedException extends RestApiException {
    public UnauthorizedException(String message) {
        super(message);
    }
}

class BadRequestException extends RestApiException {
    public BadRequestException(String message) {
        super(message);
    }
}

class ClientException extends RestApiException {
    public ClientException(String message) {
        super(message);
    }
}

class ServerException extends RestApiException {
    public ServerException(String message) {
        super(message);
    }
}
