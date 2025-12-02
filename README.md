# RestTemplate 最佳实践

本项目展示了 Spring RestTemplate 的最佳实践和推荐用法。

## 📋 目录

- [项目概述](#项目概述)
- [核心最佳实践](#核心最佳实践)
- [配置说明](#配置说明)
- [使用示例](#使用示例)
- [注意事项](#注意事项)
- [常见问题](#常见问题)

## 🎯 项目概述

本项目是一个完整的 Spring Boot 示例项目，演示了 RestTemplate 的最佳实践，包括：

- ✅ 连接池配置
- ✅ 超时设置
- ✅ 请求/响应日志记录
- ✅ 异常处理
- ✅ URL 构建最佳实践
- ✅ 消息转换器配置
- ✅ 拦截器使用

## 🚀 核心最佳实践

### 1. 使用连接池管理 HTTP 连接

**为什么重要？**
- 避免频繁创建和销毁连接，提高性能
- 控制并发连接数，防止资源耗尽
- 复用连接，减少延迟

**实现方式：**
```java
PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
connectionManager.setMaxTotal(200);  // 最大连接数
connectionManager.setDefaultMaxPerRoute(50);  // 每个路由的最大连接数
```

### 2. 配置合理的超时时间

**超时类型：**
- **连接超时（Connect Timeout）**：建立连接的最大等待时间
- **读取超时（Read Timeout）**：等待服务器响应的最大时间
- **连接请求超时（Connection Request Timeout）**：从连接池获取连接的最大等待时间

**推荐配置：**
```java
连接超时：5秒
读取超时：10秒
连接请求超时：5秒
```

### 3. 使用 RestTemplateBuilder 构建 RestTemplate

**优势：**
- 链式调用，代码更清晰
- 支持配置超时、拦截器、消息转换器等
- Spring Boot 自动配置支持

**示例：**
```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .setConnectTimeout(Duration.ofMillis(5000))
        .setReadTimeout(Duration.ofMillis(10000))
        .interceptors(interceptors)
        .build();
}
```

### 4. 使用 UriComponentsBuilder 构建 URL

**❌ 不推荐：**
```java
String url = baseUrl + "/users/" + userId + "?name=" + name;
```

**✅ 推荐：**
```java
String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
    .path("/users/{id}")
    .queryParam("name", name)
    .buildAndExpand(userId)
    .toUriString();
```

**优势：**
- 自动处理 URL 编码
- 避免手动拼接错误
- 代码更清晰易读

### 5. 使用 ResponseEntity 接收响应

**优势：**
- 可以访问响应状态码
- 可以访问响应头
- 更好的错误处理

**示例：**
```java
ResponseEntity<UserDTO> response = restTemplate.getForEntity(url, UserDTO.class);
if (response.getStatusCode() == HttpStatus.OK) {
    return response.getBody();
}
```

### 6. 使用 exchange 方法获得更多控制

**适用场景：**
- 需要设置自定义请求头
- 需要处理复杂响应
- 需要更多控制权

**示例：**
```java
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
HttpEntity<UserDTO> requestEntity = new HttpEntity<>(user, headers);

ResponseEntity<UserDTO> response = restTemplate.exchange(
    url,
    HttpMethod.POST,
    requestEntity,
    UserDTO.class
);
```

### 7. 使用 ParameterizedTypeReference 处理泛型

**问题：**
```java
// 这样无法正确反序列化 List<UserDTO>
List<UserDTO> users = restTemplate.getForObject(url, List.class);
```

**解决方案：**
```java
ResponseEntity<List<UserDTO>> response = restTemplate.exchange(
    url,
    HttpMethod.GET,
    null,
    new ParameterizedTypeReference<List<UserDTO>>() {}
);
```

### 8. 添加请求/响应日志拦截器

**目的：**
- 调试和问题排查
- 性能监控
- 审计日志

**注意：**
- 使用 `BufferingClientHttpRequestFactory` 以便多次读取响应体
- 生产环境应对敏感信息进行脱敏处理

### 9. 自定义错误处理

**实现方式：**
```java
@Component
public class RestTemplateErrorHandler extends DefaultResponseErrorHandler {
    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        // 自定义错误处理逻辑
    }
}
```

### 10. 统一异常处理

**创建自定义异常：**
```java
public class RestTemplateException extends RuntimeException {
    private final HttpStatus statusCode;
    private final String responseBody;
}
```

## ⚙️ 配置说明

### RestTemplateConfig

主要配置项：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| MAX_TOTAL_CONNECTIONS | 200 | 连接池最大连接数 |
| MAX_CONNECTIONS_PER_ROUTE | 50 | 每个路由的最大连接数 |
| CONNECT_TIMEOUT | 5000ms | 连接超时时间 |
| READ_TIMEOUT | 10000ms | 读取超时时间 |
| CONNECTION_REQUEST_TIMEOUT | 5000ms | 连接请求超时时间 |

### 配置文件

在 `application.yml` 中可以配置：

```yaml
logging:
  level:
    com.example.resttemplate: INFO
    org.springframework.web.client: DEBUG
```

## 📝 使用示例

### GET 请求

```java
// 简单 GET 请求
UserDTO user = restTemplate.getForObject(url, UserDTO.class);

// 带状态码检查的 GET 请求（推荐）
ResponseEntity<UserDTO> response = restTemplate.getForEntity(url, UserDTO.class);
if (response.getStatusCode() == HttpStatus.OK) {
    return response.getBody();
}
```

### POST 请求

```java
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
HttpEntity<UserDTO> requestEntity = new HttpEntity<>(user, headers);

ResponseEntity<UserDTO> response = restTemplate.exchange(
    url,
    HttpMethod.POST,
    requestEntity,
    UserDTO.class
);
```

### PUT 请求

```java
HttpEntity<UserDTO> requestEntity = new HttpEntity<>(user, headers);
ResponseEntity<UserDTO> response = restTemplate.exchange(
    url,
    HttpMethod.PUT,
    requestEntity,
    UserDTO.class
);
```

### DELETE 请求

```java
restTemplate.delete(url);
```

## ⚠️ 注意事项

### 1. RestTemplate 已进入维护模式

**重要提示：** Spring 官方已宣布 RestTemplate 进入维护模式，不再添加新功能。推荐使用 **WebClient**（Spring WebFlux）作为替代方案。

**何时使用 RestTemplate：**
- 现有项目已使用 RestTemplate
- 需要同步阻塞式调用
- 不依赖响应式编程

**何时使用 WebClient：**
- 新项目
- 需要响应式编程
- 需要更好的性能（异步非阻塞）

### 2. 线程安全

RestTemplate 是**线程安全**的，可以在多线程环境中共享同一个实例。

### 3. 连接池配置

- 根据实际并发量调整连接池大小
- 监控连接池使用情况
- 设置合理的超时时间

### 4. 异常处理

- 始终使用 try-catch 处理 RestClientException
- 检查响应状态码
- 记录详细的错误信息

### 5. 性能优化

- 使用连接池复用连接
- 合理设置超时时间
- 避免在循环中创建新的 RestTemplate 实例
- 使用异步调用（考虑 WebClient）处理高并发场景

### 6. 安全性

- 不要在日志中记录敏感信息（密码、token 等）
- 使用 HTTPS 传输敏感数据
- 验证和清理用户输入
- 设置合理的超时时间防止资源耗尽

## ❓ 常见问题

### Q1: RestTemplate 和 WebClient 有什么区别？

**RestTemplate：**
- 同步阻塞式
- 基于 Servlet API
- 线程安全
- 已进入维护模式

**WebClient：**
- 异步非阻塞
- 基于 Reactive Streams
- 更好的性能
- Spring 官方推荐

### Q2: 如何设置请求头？

```java
HttpHeaders headers = new HttpHeaders();
headers.set("Authorization", "Bearer " + token);
headers.setContentType(MediaType.APPLICATION_JSON);
HttpEntity<?> requestEntity = new HttpEntity<>(body, headers);
```

### Q3: 如何处理文件上传？

```java
MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
body.add("file", new FileSystemResource(file));

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.MULTIPART_FORM_DATA);

HttpEntity<MultiValueMap<String, Object>> requestEntity = 
    new HttpEntity<>(body, headers);

restTemplate.postForEntity(url, requestEntity, String.class);
```

### Q4: 如何设置代理？

```java
Proxy proxy = new Proxy(Proxy.Type.HTTP, 
    new InetSocketAddress("proxy.example.com", 8080));
HttpClient httpClient = HttpClientBuilder.create()
    .setProxy(proxy)
    .build();
```

### Q5: 如何禁用 SSL 验证（仅用于开发环境）？

```java
// ⚠️ 警告：仅用于开发环境，生产环境不要使用
TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
SSLContext sslContext = SSLContexts.custom()
    .loadTrustMaterial(null, acceptingTrustStrategy)
    .build();
SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);
CloseableHttpClient httpClient = HttpClients.custom()
    .setSSLSocketFactory(csf)
    .build();
```

## 📚 相关资源

- [Spring RestTemplate 官方文档](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/client/RestTemplate.html)
- [Spring WebClient 文档](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux-client)
- [Apache HttpClient 文档](https://hc.apache.org/httpcomponents-client-5.1.x/)

## 🏃 运行项目

1. 确保已安装 Java 11+ 和 Maven
2. 编译项目：
   ```bash
   mvn clean compile
   ```
3. 运行项目：
   ```bash
   mvn spring-boot:run
   ```
4. 访问 API：
   - GET http://localhost:8080/api/users/1
   - GET http://localhost:8080/api/users
   - POST http://localhost:8080/api/users

## 📄 许可证

MIT License
