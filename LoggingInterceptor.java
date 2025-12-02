import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * RestTemplate 请求/响应日志拦截器
 */
public class LoggingInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        
        // 记录请求信息
        logRequest(request, body);
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        
        // 执行请求
        ClientHttpResponse response = execution.execute(request, body);
        
        // 记录响应时间
        long duration = System.currentTimeMillis() - startTime;
        
        // 记录响应信息
        logResponse(response, duration);
        
        return response;
    }

    private void logRequest(HttpRequest request, byte[] body) {
        if (log.isDebugEnabled()) {
            log.debug("===========================请求开始===========================");
            log.debug("URI         : {}", request.getURI());
            log.debug("Method      : {}", request.getMethod());
            log.debug("Headers     : {}", request.getHeaders());
            
            if (body.length > 0) {
                String requestBody = new String(body, StandardCharsets.UTF_8);
                log.debug("Request Body: {}", requestBody);
            }
            log.debug("==============================================================");
        }
    }

    private void logResponse(ClientHttpResponse response, long duration) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("===========================响应开始===========================");
            log.debug("Status code  : {}", response.getStatusCode());
            log.debug("Status text  : {}", response.getStatusText());
            log.debug("Headers      : {}", response.getHeaders());
            log.debug("Duration     : {} ms", duration);
            log.debug("==============================================================");
        }
    }
}
