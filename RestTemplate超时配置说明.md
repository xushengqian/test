# RestTemplate 超时配置完整指南

## 📋 目录
1. [超时类型说明](#超时类型说明)
2. [配置方式](#配置方式)
3. [最佳实践](#最佳实践)
4. [常见问题](#常见问题)

## 🔍 超时类型说明

### 1. 连接超时 (Connect Timeout)
- **定义**: 客户端与服务器建立连接的最长等待时间
- **何时触发**: 当无法在指定时间内建立 TCP 连接时
- **常见原因**: 
  - 服务器宕机或不可达
  - 网络问题
  - 防火墙阻止
- **推荐值**: 3-5 秒

### 2. 读取超时 (Read Timeout / Socket Timeout)
- **定义**: 建立连接后，等待服务器响应数据的最长时间
- **何时触发**: 连接已建立，但在指定时间内没有收到数据
- **常见原因**:
  - 服务器处理慢
  - 网络延迟高
  - 服务器无响应
- **推荐值**: 10-30 秒（根据业务需求）

### 3. 连接请求超时 (Connection Request Timeout)
- **定义**: 从连接池获取连接的最长等待时间
- **何时触发**: 连接池中没有可用连接，且等待时间超过限制
- **常见原因**:
  - 连接池已满
  - 其他请求占用连接时间过长
- **推荐值**: 3 秒
- **注意**: 仅在使用连接池时有效（如 Apache HttpClient）

## ⚙️ 配置方式

### 方式 1: 使用 RestTemplateBuilder (推荐) ⭐

```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
}
```

**优点**:
- ✅ 代码简洁
- ✅ 类型安全
- ✅ Spring Boot 官方推荐
- ✅ 支持 Duration 类型

### 方式 2: 使用 SimpleClientHttpRequestFactory

```java
@Bean
public RestTemplate restTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);  // 毫秒
    factory.setReadTimeout(10000);     // 毫秒
    return new RestTemplate(factory);
}
```

**适用场景**:
- 简单的 HTTP 请求
- 不需要连接池
- 请求量不大

### 方式 3: 使用 Apache HttpClient (高级配置)

```java
@Bean
public RestTemplate restTemplate() {
    RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(5000)
            .setConnectionRequestTimeout(3000)
            .setSocketTimeout(10000)
            .build();
    
    CloseableHttpClient httpClient = HttpClients.custom()
            .setDefaultRequestConfig(requestConfig)
            .build();
    
    HttpComponentsClientHttpRequestFactory factory = 
        new HttpComponentsClientHttpRequestFactory(httpClient);
    
    return new RestTemplate(factory);
}
```

**优点**:
- ✅ 支持连接池
- ✅ 性能更好
- ✅ 配置选项更多
- ✅ 适合高并发场景

**需要依赖**:
```xml
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpclient</artifactId>
</dependency>
```

### 方式 4: 从配置文件读取

**application.yml**:
```yaml
rest:
  template:
    connect-timeout: 5
    read-timeout: 10
```

**Java 配置**:
```java
@Bean
public RestTemplate restTemplate(
        RestTemplateBuilder builder,
        @Value("${rest.template.connect-timeout}") int connectTimeout,
        @Value("${rest.template.read-timeout}") int readTimeout) {
    return builder
            .setConnectTimeout(Duration.ofSeconds(connectTimeout))
            .setReadTimeout(Duration.ofSeconds(readTimeout))
            .build();
}
```

## 🎯 最佳实践

### 1. 根据业务场景设置合理的超时时间

| 场景 | 连接超时 | 读取超时 |
|------|---------|---------|
| 内网快速接口 | 2-3秒 | 5-10秒 |
| 外网一般接口 | 5秒 | 15-30秒 |
| 长时间处理任务 | 5秒 | 60-120秒 |
| 文件上传/下载 | 10秒 | 300秒+ |

### 2. 使用连接池（高并发场景）

```java
PoolingHttpClientConnectionManager connectionManager = 
    new PoolingHttpClientConnectionManager();

// 设置最大连接数
connectionManager.setMaxTotal(200);

// 设置每个路由的最大连接数
connectionManager.setDefaultMaxPerRoute(20);
```

### 3. 实现重试机制

```java
public String requestWithRetry(String url, int maxRetries) {
    int attempt = 0;
    while (attempt < maxRetries) {
        try {
            return restTemplate.getForObject(url, String.class);
        } catch (ResourceAccessException e) {
            attempt++;
            if (attempt >= maxRetries) {
                throw e;
            }
            // 等待后重试
            Thread.sleep(1000 * attempt);
        }
    }
    return null;
}
```

### 4. 异常处理

```java
try {
    String result = restTemplate.getForObject(url, String.class);
    return result;
} catch (ResourceAccessException e) {
    // 超时或连接异常
    log.error("请求超时: {}", url, e);
    throw new TimeoutException("请求超时");
} catch (HttpClientErrorException e) {
    // 4xx 客户端错误
    log.error("客户端错误: {}", e.getStatusCode());
    throw e;
} catch (HttpServerErrorException e) {
    // 5xx 服务器错误
    log.error("服务器错误: {}", e.getStatusCode());
    throw e;
}
```

### 5. 为不同的外部服务配置不同的 RestTemplate

```java
@Configuration
public class RestTemplateConfig {
    
    @Bean("fastServiceRestTemplate")
    public RestTemplate fastServiceRestTemplate() {
        return builder()
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
    
    @Bean("slowServiceRestTemplate")
    public RestTemplate slowServiceRestTemplate() {
        return builder()
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(60))
                .build();
    }
}
```

## ❓ 常见问题

### Q1: 超时后会自动重试吗？
**A**: 默认不会。需要手动实现重试逻辑，或使用 Spring Retry。

### Q2: 设置了超时为什么还是没生效？
**A**: 可能原因：
1. RestTemplate 配置被其他地方覆盖
2. 使用了默认的 RestTemplate 而不是配置好的 Bean
3. 拦截器或过滤器修改了超时设置

### Q3: 连接超时和读取超时应该设置为多少？
**A**: 
- 没有固定答案，需要根据实际情况测试
- 建议：连接超时 < 读取超时
- 监控实际请求时间，设置为 P99 或 P95 的 1.5-2 倍

### Q4: 超时异常如何区分？
**A**: 
```java
catch (ResourceAccessException e) {
    if (e.getCause() instanceof SocketTimeoutException) {
        // 读取超时
    } else if (e.getCause() instanceof ConnectException) {
        // 连接超时
    }
}
```

### Q5: 全局超时和单个请求超时如何选择？
**A**:
- 全局超时：适用于大部分请求的默认配置
- 单个请求超时：特殊接口需要不同的超时时间

```java
// 单个请求自定义超时
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(customTimeout);
RestTemplate customRestTemplate = new RestTemplate(factory);
```

## 📊 监控建议

1. **记录超时情况**
```java
@Aspect
@Component
public class RestTemplateTimeoutMonitor {
    
    @Around("execution(* org.springframework.web.client.RestTemplate.*(..))")
    public Object monitor(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } catch (ResourceAccessException e) {
            long duration = System.currentTimeMillis() - start;
            log.warn("请求超时，耗时: {}ms", duration);
            throw e;
        }
    }
}
```

2. **统计超时率**
3. **告警机制**
4. **定期分析和优化**

## 🔗 相关资源

- [Spring RestTemplate 官方文档](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#rest-client-access)
- [Apache HttpClient 文档](https://hc.apache.org/httpcomponents-client-ga/)
- [超时配置最佳实践](https://spring.io/guides/gs/rest-service/)

## 📝 总结

选择超时配置方式的决策树：

```
是否使用 Spring Boot？
├─ 是 → 使用 RestTemplateBuilder ✅
└─ 否
    ├─ 需要连接池？
    │   ├─ 是 → 使用 Apache HttpClient ✅
    │   └─ 否 → 使用 SimpleClientHttpRequestFactory ✅
    └─ 需要灵活配置？
        └─ 是 → 使用 Apache HttpClient ✅
```

**核心要点**:
1. ⏱️ 合理设置超时时间，避免过长或过短
2. 🔄 高并发场景使用连接池
3. 🛡️ 实现超时重试和熔断机制
4. 📊 监控和记录超时情况
5. 🎯 不同服务使用不同的超时配置
