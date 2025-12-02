import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.BufferingClientHttpRequestInterceptor;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.time.Duration;
import java.nio.charset.StandardCharsets;

/**
 * RestTemplate 配置类 - 最佳实践
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 方法1: 使用 RestTemplateBuilder (推荐)
     * 优点: 自动配置、支持链式调用、更好的可测试性
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                // 设置连接超时时间
                .setConnectTimeout(Duration.ofSeconds(5))
                // 设置读取超时时间
                .setReadTimeout(Duration.ofSeconds(10))
                // 添加拦截器
                .interceptors(new LoggingInterceptor())
                // 添加错误处理器
                .errorHandler(new CustomErrorHandler())
                // 设置根URL (如果有固定的API前缀)
                // .rootUri("https://api.example.com")
                .build();
    }

    /**
     * 方法2: 手动配置 RestTemplate
     * 适用于需要更细粒度控制的场景
     */
    @Bean("customRestTemplate")
    public RestTemplate customRestTemplate() {
        RestTemplate restTemplate = new RestTemplate(clientHttpRequestFactory());
        
        // 设置字符编码
        restTemplate.getMessageConverters()
                .stream()
                .filter(converter -> converter instanceof StringHttpMessageConverter)
                .forEach(converter -> ((StringHttpMessageConverter) converter)
                        .setDefaultCharset(StandardCharsets.UTF_8));
        
        // 添加拦截器
        restTemplate.getInterceptors().add(new LoggingInterceptor());
        restTemplate.getInterceptors().add(new AuthInterceptor());
        
        // 设置错误处理器
        restTemplate.setErrorHandler(new CustomErrorHandler());
        
        return restTemplate;
    }

    /**
     * 配置连接池和超时
     */
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        
        // 连接超时: 5秒
        factory.setConnectTimeout(5000);
        
        // 读取超时: 10秒
        factory.setReadTimeout(10000);
        
        // 如果需要使用 Apache HttpClient 连接池，可以使用以下配置:
        // HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        // factory.setConnectTimeout(5000);
        // factory.setReadTimeout(10000);
        
        return factory;
    }

    /**
     * 方法3: 使用 Apache HttpClient 连接池 (推荐用于生产环境)
     * 需要添加依赖: org.apache.httpcomponents:httpclient
     */
    /*
    @Bean
    public RestTemplate pooledRestTemplate() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        // 最大连接数
        connectionManager.setMaxTotal(100);
        // 每个路由的最大连接数
        connectionManager.setDefaultMaxPerRoute(20);
        
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setSocketTimeout(10000)
                .setConnectionRequestTimeout(3000)
                .build();
        
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(3, true))
                .build();
        
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        
        RestTemplate restTemplate = new RestTemplate(factory);
        restTemplate.getInterceptors().add(new LoggingInterceptor());
        restTemplate.setErrorHandler(new CustomErrorHandler());
        
        return restTemplate;
    }
    */
}
