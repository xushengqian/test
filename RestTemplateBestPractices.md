# RestTemplate 最佳实践指南

## 目录
1. [基本配置](#基本配置)
2. [超时设置](#超时设置)
3. [连接池配置](#连接池配置)
4. [拦截器](#拦截器)
5. [错误处理](#错误处理)
6. [常用操作](#常用操作)
7. [性能优化](#性能优化)
8. [安全考虑](#安全考虑)
9. [测试建议](#测试建议)
10. [迁移到 WebClient](#迁移到-webclient)

---

## 基本配置

### 1. 使用 RestTemplateBuilder (推荐)

```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .interceptors(new LoggingInterceptor())
            .errorHandler(new CustomErrorHandler())
            .build();
}
```

**优点:**
- 自动集成 Spring Boot 的配置
- 支持链式调用
- 更好的可测试性
- 自动配置消息转换器

### 2. 手动配置 (适用于特殊需求)

```java
@Bean
public RestTemplate restTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);
    factory.setReadTimeout(10000);
    
    RestTemplate restTemplate = new RestTemplate(factory);
    restTemplate.getInterceptors().add(new LoggingInterceptor());
    restTemplate.setErrorHandler(new CustomErrorHandler());
    
    return restTemplate;
}
```

---

## 超时设置

### ⚠️ 为什么需要超时设置

- **防止线程阻塞**: 避免无限等待
- **资源管理**: 及时释放连接
- **用户体验**: 快速失败，避免长时间等待

### 推荐的超时配置

```java
// 连接超时: 客户端与服务器建立连接的最长时间
factory.setConnectTimeout(5000); // 5秒

// 读取超时: 服务器返回数据的最长等待时间
factory.setReadTimeout(10000); // 10秒
```

### 超时时间建议

| 场景 | 连接超时 | 读取超时 |
|------|---------|---------|
| 内网API | 2-3秒 | 5-10秒 |
| 外网API | 5-10秒 | 10-30秒 |
| 长时间处理 | 5-10秒 | 60-300秒 |
| 文件上传/下载 | 10秒 | 根据文件大小 |

---

## 连接池配置

### 为什么使用连接池

1. **复用连接**: 避免频繁创建和销毁连接
2. **提高性能**: 减少建立连接的开销
3. **控制并发**: 限制最大连接数

### 使用 Apache HttpClient 连接池 (推荐生产环境)

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpclient</artifactId>
</dependency>
```

```java
@Bean
public RestTemplate pooledRestTemplate() {
    PoolingHttpClientConnectionManager connectionManager = 
        new PoolingHttpClientConnectionManager();
    
    // 最大连接数
    connectionManager.setMaxTotal(100);
    
    // 每个路由的最大连接数
    connectionManager.setDefaultMaxPerRoute(20);
    
    // 连接存活时间
    connectionManager.setDefaultMaxPerRoute(60, TimeUnit.SECONDS);
    
    RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(5000)
            .setSocketTimeout(10000)
            .setConnectionRequestTimeout(3000) // 从连接池获取连接的超时时间
            .build();
    
    CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .setRetryHandler(new DefaultHttpRequestRetryHandler(3, true))
            .build();
    
    HttpComponentsClientHttpRequestFactory factory = 
        new HttpComponentsClientHttpRequestFactory(httpClient);
    
    return new RestTemplate(factory);
}
```

### 连接池参数说明

- **MaxTotal**: 最大连接数 (建议: 100-200)
- **DefaultMaxPerRoute**: 每个路由的最大连接数 (建议: MaxTotal的20%-30%)
- **ConnectionRequestTimeout**: 从连接池获取连接的超时时间

---

## 拦截器

### 1. 日志拦截器

用途: 记录请求和响应详情，便于调试和监控

```java
public class LoggingInterceptor implements ClientHttpRequestInterceptor {
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) {
        // 记录请求
        log.info("Request: {} {}", request.getMethod(), request.getURI());
        
        long start = System.currentTimeMillis();
        ClientHttpResponse response = execution.execute(request, body);
        long duration = System.currentTimeMillis() - start;
        
        // 记录响应
        log.info("Response: {} in {} ms", response.getStatusCode(), duration);
        
        return response;
    }
}
```

### 2. 认证拦截器

用途: 自动添加认证信息

```java
public class AuthInterceptor implements ClientHttpRequestInterceptor {
    private String token;
    
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) {
        request.getHeaders().add("Authorization", "Bearer " + token);
        return execution.execute(request, body);
    }
}
```

### 3. 重试拦截器

用途: 自动重试失败的请求

```java
public class RetryInterceptor implements ClientHttpRequestInterceptor {
    private static final int MAX_RETRIES = 3;
    
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) {
        int retries = 0;
        while (true) {
            try {
                return execution.execute(request, body);
            } catch (IOException e) {
                if (++retries >= MAX_RETRIES) {
                    throw e;
                }
                // 指数退避
                Thread.sleep((long) Math.pow(2, retries) * 1000);
            }
        }
    }
}
```

---

## 错误处理

### 1. 自定义错误处理器

```java
public class CustomErrorHandler implements ResponseErrorHandler {
    
    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        HttpStatus.Series series = response.getStatusCode().series();
        return (series == HttpStatus.Series.CLIENT_ERROR || 
                series == HttpStatus.Series.SERVER_ERROR);
    }
    
    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        HttpStatus statusCode = response.getStatusCode();
        String responseBody = readResponseBody(response);
        
        switch (statusCode) {
            case NOT_FOUND:
                throw new ResourceNotFoundException("资源未找到");
            case UNAUTHORIZED:
                throw new UnauthorizedException("未授权");
            case BAD_REQUEST:
                throw new BadRequestException("请求参数错误: " + responseBody);
            default:
                throw new RestApiException("API调用失败: " + responseBody);
        }
    }
}
```

### 2. 全局异常处理

```java
@ControllerAdvice
public class RestTemplateExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(404, e.getMessage()));
    }
    
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> handleRestClientException(RestClientException e) {
        log.error("REST API调用失败", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(500, "API调用失败"));
    }
}
```

---

## 常用操作

### 1. GET 请求

```java
// 简单 GET
User user = restTemplate.getForObject(url, User.class);

// 带路径变量
User user = restTemplate.getForObject(
    "/users/{id}", User.class, userId);

// 带查询参数
String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
    .queryParam("page", page)
    .queryParam("size", size)
    .toUriString();
User user = restTemplate.getForObject(url, User.class);

// 获取泛型集合
ResponseEntity<List<User>> response = restTemplate.exchange(
    url, HttpMethod.GET, null, 
    new ParameterizedTypeReference<List<User>>() {});
```

### 2. POST 请求

```java
// 简单 POST
User user = restTemplate.postForObject(url, requestBody, User.class);

// POST JSON
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
HttpEntity<User> request = new HttpEntity<>(user, headers);
ResponseEntity<User> response = restTemplate.postForEntity(url, request, User.class);

// POST 表单
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
map.add("key", "value");
HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);
```

### 3. PUT/PATCH/DELETE 请求

```java
// PUT
restTemplate.exchange(url, HttpMethod.PUT, request, User.class);

// PATCH
restTemplate.exchange(url, HttpMethod.PATCH, request, User.class);

// DELETE
restTemplate.delete(url);
```

---

## 性能优化

### 1. 使用连接池

✅ **推荐**: 使用 Apache HttpClient 连接池
❌ **避免**: 使用默认的 SimpleClientHttpRequestFactory

### 2. 合理设置超时时间

- 根据实际业务场景设置超时时间
- 避免设置过长的超时时间

### 3. 复用 RestTemplate 实例

✅ **推荐**: 将 RestTemplate 配置为单例 Bean
❌ **避免**: 每次请求都创建新的 RestTemplate 实例

### 4. 启用 HTTP 持久连接

```java
// 默认已启用，确保不要禁用
connectionManager.setDefaultKeepAliveStrategy(
    (response, context) -> 60 * 1000); // 60秒
```

### 5. 使用异步请求 (考虑迁移到 WebClient)

对于高并发场景，考虑使用 Spring WebClient (响应式)

---

## 安全考虑

### 1. HTTPS 配置

```java
// 信任所有证书 (仅开发环境使用)
TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
SSLContext sslContext = SSLContexts.custom()
        .loadTrustMaterial(null, acceptingTrustStrategy)
        .build();
SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);
```

⚠️ **警告**: 生产环境必须使用正确的证书验证

### 2. 敏感信息处理

- 不要在日志中打印敏感信息 (token、密码等)
- 使用环境变量或配置中心存储敏感配置

```java
// 在日志拦截器中过滤敏感信息
private void logRequest(HttpRequest request, byte[] body) {
    String bodyString = new String(body, StandardCharsets.UTF_8);
    // 过滤敏感字段
    bodyString = bodyString.replaceAll("\"password\":\"[^\"]*\"", 
                                       "\"password\":\"***\"");
    log.info("Request Body: {}", bodyString);
}
```

### 3. 防止 SSRF 攻击

```java
// 验证目标 URL
public void validateUrl(String url) {
    URI uri = URI.create(url);
    String host = uri.getHost();
    
    // 禁止访问内网地址
    if (isInternalIp(host)) {
        throw new SecurityException("禁止访问内网地址");
    }
}
```

---

## 测试建议

### 1. 使用 MockRestServiceServer

```java
@SpringBootTest
public class UserServiceTest {
    
    @Autowired
    private RestTemplate restTemplate;
    
    private MockRestServiceServer mockServer;
    
    @BeforeEach
    public void setup() {
        mockServer = MockRestServiceServer.createServer(restTemplate);
    }
    
    @Test
    public void testGetUser() {
        User expectedUser = new User(1L, "John", "john@example.com");
        
        mockServer.expect(requestTo("/users/1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                    objectMapper.writeValueAsString(expectedUser),
                    MediaType.APPLICATION_JSON));
        
        User user = userService.getUser(1L);
        
        assertEquals(expectedUser.getName(), user.getName());
        mockServer.verify();
    }
}
```

### 2. 使用 WireMock

```java
@SpringBootTest
@AutoConfigureWireMock(port = 8089)
public class UserServiceIntegrationTest {
    
    @Test
    public void testGetUser() {
        stubFor(get(urlEqualTo("/users/1"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":1,\"name\":\"John\"}")));
        
        User user = userService.getUser(1L);
        assertEquals("John", user.getName());
    }
}
```

---

## 迁移到 WebClient

### 为什么迁移?

1. **RestTemplate 已维护模式**: Spring 5.0+ 推荐使用 WebClient
2. **响应式编程**: 支持非阻塞 I/O
3. **更好的性能**: 适合高并发场景
4. **更现代的 API**: 更流畅的 API 设计

### 对比示例

**RestTemplate:**
```java
User user = restTemplate.getForObject("/users/1", User.class);
```

**WebClient:**
```java
User user = webClient.get()
    .uri("/users/1")
    .retrieve()
    .bodyToMono(User.class)
    .block(); // 阻塞调用

// 或者响应式调用
Mono<User> userMono = webClient.get()
    .uri("/users/1")
    .retrieve()
    .bodyToMono(User.class);
```

### 迁移策略

1. **渐进式迁移**: 新功能使用 WebClient，旧功能逐步迁移
2. **共存**: RestTemplate 和 WebClient 可以同时使用
3. **学习成本**: 需要了解响应式编程概念 (Mono, Flux)

---

## 总结

### ✅ 最佳实践清单

- [ ] 使用 RestTemplateBuilder 配置
- [ ] 设置合理的超时时间
- [ ] 使用连接池 (生产环境)
- [ ] 添加日志拦截器
- [ ] 实现自定义错误处理器
- [ ] 复用 RestTemplate 实例
- [ ] 处理敏感信息安全
- [ ] 编写单元测试
- [ ] 监控和日志
- [ ] 考虑迁移到 WebClient

### ⚠️ 常见陷阱

- ❌ 不设置超时时间
- ❌ 每次请求创建新的 RestTemplate
- ❌ 不处理异常
- ❌ 在日志中暴露敏感信息
- ❌ 不使用连接池
- ❌ 忽略 HTTPS 证书验证 (生产环境)

### 📚 推荐阅读

- [Spring RestTemplate 官方文档](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#rest-client-access)
- [Apache HttpClient 文档](https://hc.apache.org/httpcomponents-client-4.5.x/index.html)
- [Spring WebClient 文档](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux-client)

---

**版本说明**: 本指南适用于 Spring Boot 2.x 和 3.x
