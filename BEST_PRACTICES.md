# RestTemplate 最佳实践总结

## 📌 核心原则

### 1. 配置优先
- ✅ 使用 `@Bean` 统一配置 RestTemplate
- ✅ 配置连接池、超时时间、拦截器等
- ✅ 避免在代码中直接创建 RestTemplate 实例

### 2. 连接池管理
```java
// ✅ 推荐：使用连接池
PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
connectionManager.setMaxTotal(200);
connectionManager.setDefaultMaxPerRoute(50);

// ❌ 不推荐：每次创建新连接
RestTemplate restTemplate = new RestTemplate();
```

### 3. 超时配置
```java
// ✅ 推荐：明确设置超时时间
.setConnectTimeout(Duration.ofMillis(5000))
.setReadTimeout(Duration.ofMillis(10000))

// ❌ 不推荐：使用默认超时（可能无限等待）
```

### 4. URL 构建
```java
// ✅ 推荐：使用 UriComponentsBuilder
String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
    .path("/users/{id}")
    .queryParam("name", name)
    .buildAndExpand(userId)
    .toUriString();

// ❌ 不推荐：手动拼接
String url = baseUrl + "/users/" + userId + "?name=" + name;
```

### 5. 响应处理
```java
// ✅ 推荐：使用 ResponseEntity 检查状态码
ResponseEntity<UserDTO> response = restTemplate.getForEntity(url, UserDTO.class);
if (response.getStatusCode() == HttpStatus.OK) {
    return response.getBody();
}

// ❌ 不推荐：直接获取对象，不检查状态码
UserDTO user = restTemplate.getForObject(url, UserDTO.class);
```

### 6. 异常处理
```java
// ✅ 推荐：捕获异常并记录详细信息
try {
    ResponseEntity<UserDTO> response = restTemplate.getForEntity(url, UserDTO.class);
    // ...
} catch (RestClientException e) {
    log.error("请求失败: {}", url, e);
    throw new RestTemplateException("请求失败", e, null, null);
}

// ❌ 不推荐：忽略异常或只打印堆栈
```

### 7. 请求头设置
```java
// ✅ 推荐：使用 HttpEntity 封装请求头和请求体
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.set("Authorization", "Bearer " + token);
HttpEntity<UserDTO> requestEntity = new HttpEntity<>(user, headers);
```

### 8. 泛型类型处理
```java
// ✅ 推荐：使用 ParameterizedTypeReference
ResponseEntity<List<UserDTO>> response = restTemplate.exchange(
    url,
    HttpMethod.GET,
    null,
    new ParameterizedTypeReference<List<UserDTO>>() {}
);

// ❌ 不推荐：直接使用 List.class（会丢失泛型信息）
List<UserDTO> users = restTemplate.getForObject(url, List.class);
```

## 🎯 性能优化

### 1. 连接复用
- 使用连接池复用连接
- 避免频繁创建和销毁连接

### 2. 合理设置超时
- 连接超时：5秒
- 读取超时：10秒（根据业务调整）
- 连接请求超时：5秒

### 3. 异步处理（考虑 WebClient）
对于高并发场景，考虑使用 WebClient 替代 RestTemplate

## 🔒 安全最佳实践

### 1. 敏感信息处理
```java
// ✅ 推荐：在日志中脱敏
log.info("Request Body: {}", maskSensitiveInfo(body));

// ❌ 不推荐：直接记录敏感信息
log.info("Request Body: {}", body); // 可能包含密码、token 等
```

### 2. HTTPS 使用
- 生产环境必须使用 HTTPS
- 验证 SSL 证书（不要跳过验证）

### 3. 输入验证
- 验证和清理用户输入
- 防止注入攻击

## 📊 监控和日志

### 1. 请求日志
- 记录请求 URL、方法、请求头
- 记录响应状态码、响应时间
- 记录请求耗时

### 2. 错误日志
- 记录详细的错误信息
- 包含请求上下文信息
- 便于问题排查

### 3. 性能监控
- 监控请求耗时
- 监控连接池使用情况
- 设置告警阈值

## ⚠️ 常见陷阱

### 1. 忘记检查响应状态码
```java
// ❌ 错误：不检查状态码
UserDTO user = restTemplate.getForObject(url, UserDTO.class);

// ✅ 正确：检查状态码
ResponseEntity<UserDTO> response = restTemplate.getForEntity(url, UserDTO.class);
if (response.getStatusCode() == HttpStatus.OK) {
    return response.getBody();
}
```

### 2. 手动拼接 URL
```java
// ❌ 错误：手动拼接，可能出错
String url = baseUrl + "/users/" + userId;

// ✅ 正确：使用 UriComponentsBuilder
String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
    .path("/users/{id}")
    .buildAndExpand(userId)
    .toUriString();
```

### 3. 忽略异常
```java
// ❌ 错误：忽略异常
try {
    restTemplate.getForObject(url, UserDTO.class);
} catch (Exception e) {
    // 什么都不做
}

// ✅ 正确：处理异常
try {
    return restTemplate.getForObject(url, UserDTO.class);
} catch (RestClientException e) {
    log.error("请求失败", e);
    throw new RestTemplateException("请求失败", e, null, null);
}
```

### 4. 不使用连接池
```java
// ❌ 错误：每次都创建新连接
RestTemplate restTemplate = new RestTemplate();

// ✅ 正确：使用配置的连接池
@Autowired
private RestTemplate restTemplate;
```

## 🔄 迁移到 WebClient

如果考虑迁移到 WebClient，主要变化：

```java
// RestTemplate（同步）
ResponseEntity<UserDTO> response = restTemplate.getForEntity(url, UserDTO.class);

// WebClient（异步）
Mono<UserDTO> user = webClient.get()
    .uri(url)
    .retrieve()
    .bodyToMono(UserDTO.class);
```

## 📚 参考资源

- [Spring RestTemplate 文档](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/client/RestTemplate.html)
- [Apache HttpClient 文档](https://hc.apache.org/httpcomponents-client-5.1.x/)
- [Spring WebClient 文档](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux-client)
