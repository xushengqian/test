import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * 认证拦截器 - 自动添加认证头
 */
public class AuthInterceptor implements ClientHttpRequestInterceptor {

    private String token;

    public AuthInterceptor() {
        // 可以从配置或其他地方获取token
    }

    public AuthInterceptor(String token) {
        this.token = token;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        
        // 添加认证头
        if (token != null && !token.isEmpty()) {
            request.getHeaders().add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        
        // 添加其他通用头
        request.getHeaders().add("X-Request-Source", "Spring-RestTemplate");
        
        return execution.execute(request, body);
    }

    public void setToken(String token) {
        this.token = token;
    }
}
