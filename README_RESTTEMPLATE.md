# RestTemplate 最佳实践示例项目

## 项目简介

本项目提供了 Spring RestTemplate 的完整最佳实践示例，包括配置、使用、测试等各个方面。

## 📁 项目结构

```
.
├── RestTemplateConfig.java          # RestTemplate 配置类
├── LoggingInterceptor.java          # 日志拦截器
├── AuthInterceptor.java             # 认证拦截器
├── CustomErrorHandler.java          # 自定义错误处理器
├── RestTemplateService.java         # RestTemplate 服务封装
├── RestTemplateUsageExamples.java   # 实际使用示例
├── RestTemplateBestPractices.md     # 详细的最佳实践指南
├── pom.xml                          # Maven 依赖配置
├── application.yml                  # 应用配置
└── README_RESTTEMPLATE.md           # 本文件
```

## 🚀 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- 可选: 用于连接池 -->
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
</dependency>
```

### 2. 配置 RestTemplate

使用 `RestTemplateBuilder` (推荐):

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

### 3. 使用 RestTemplate

```java
@Service
public class UserService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    public User getUser(Long id) {
        String url = "https://api.example.com/users/" + id;
        return restTemplate.getForObject(url, User.class);
    }
    
    public User createUser(User user) {
        String url = "https://api.example.com/users";
        return restTemplate.postForObject(url, user, User.class);
    }
}
```

## 📋 主要功能

### 1. 基础配置
- ✅ 超时设置 (连接超时、读取超时)
- ✅ 连接池配置 (Apache HttpClient)
- ✅ 消息转换器配置
- ✅ 字符编码设置

### 2. 拦截器
- ✅ 日志拦截器 (记录请求/响应)
- ✅ 认证拦截器 (自动添加 token)
- ✅ 重试拦截器 (自动重试失败请求)

### 3. 错误处理
- ✅ 自定义错误处理器
- ✅ 异常分类 (4xx, 5xx)
- ✅ 全局异常处理

### 4. 常用操作
- ✅ GET 请求 (简单查询、带参数、路径变量)
- ✅ POST 请求 (JSON、表单、文件上传)
- ✅ PUT/PATCH/DELETE 请求
- ✅ 泛型集合处理
- ✅ 响应头处理

### 5. 高级特性
- ✅ 连接池优化
- ✅ 重试机制
- ✅ 性能监控
- ✅ 安全配置 (HTTPS)

## 💡 核心最佳实践

### 1. ✅ 使用 RestTemplateBuilder

```java
// 推荐
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder.build();
}

// 不推荐
RestTemplate restTemplate = new RestTemplate();
```

**原因**: RestTemplateBuilder 提供了更好的配置管理和可测试性。

### 2. ✅ 设置超时时间

```java
builder
    .setConnectTimeout(Duration.ofSeconds(5))
    .setReadTimeout(Duration.ofSeconds(10))
```

**原因**: 防止线程无限阻塞，及时释放资源。

### 3. ✅ 使用连接池 (生产环境)

```java
// 使用 Apache HttpClient 连接池
PoolingHttpClientConnectionManager connectionManager = 
    new PoolingHttpClientConnectionManager();
connectionManager.setMaxTotal(100);
connectionManager.setDefaultMaxPerRoute(20);
```

**原因**: 复用连接，提高性能，减少资源消耗。

### 4. ✅ 添加拦截器

```java
builder.interceptors(new LoggingInterceptor())
```

**原因**: 统一处理日志、认证、重试等横切关注点。

### 5. ✅ 实现错误处理器

```java
builder.errorHandler(new CustomErrorHandler())
```

**原因**: 统一处理错误响应，提供更好的错误信息。

### 6. ✅ 复用 RestTemplate 实例

```java
// 推荐: 单例
@Bean
public RestTemplate restTemplate() { ... }

// 不推荐: 每次创建新实例
RestTemplate restTemplate = new RestTemplate();
```

**原因**: 避免重复创建连接，节省资源。

## ⚠️ 常见陷阱

### 1. ❌ 不设置超时

```java
// 错误示例
RestTemplate restTemplate = new RestTemplate();
// 可能导致线程永久阻塞
```

### 2. ❌ 不处理异常

```java
// 错误示例
User user = restTemplate.getForObject(url, User.class);
// 网络异常时会直接抛出异常
```

### 3. ❌ 在日志中暴露敏感信息

```java
// 错误示例
log.info("Request body: {}", requestBody); // 可能包含密码、token
```

### 4. ❌ 不使用连接池

```java
// 错误示例
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
// 每次都创建新连接，性能差
```

## 🧪 测试

### 使用 MockRestServiceServer

```java
@Test
public void testGetUser() {
    mockServer.expect(requestTo("/users/1"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    
    User user = userService.getUser(1L);
    
    assertEquals("John", user.getName());
}
```

### 使用 WireMock

```java
@Test
public void testGetUser() {
    stubFor(get(urlEqualTo("/users/1"))
        .willReturn(aResponse()
            .withStatus(200)
            .withBody("{\"name\":\"John\"}")));
    
    User user = userService.getUser(1L);
}
```

## 📊 性能优化建议

| 优化项 | 说明 | 效果 |
|-------|------|------|
| 连接池 | 使用 Apache HttpClient | 🚀🚀🚀 显著提升 |
| 超时设置 | 合理设置超时时间 | 🚀🚀 明显提升 |
| 复用实例 | RestTemplate 单例 | 🚀🚀 明显提升 |
| 持久连接 | Keep-Alive | 🚀 小幅提升 |

## 🔄 迁移到 WebClient

RestTemplate 从 Spring 5.0 开始处于维护模式，建议新项目使用 WebClient。

### 对比

| 特性 | RestTemplate | WebClient |
|-----|-------------|-----------|
| 编程模型 | 同步/阻塞 | 异步/非阻塞 |
| 性能 | 较好 | 更好 |
| API 设计 | 传统 | 现代/流畅 |
| 维护状态 | 维护模式 | 活跃开发 |
| 学习曲线 | 低 | 中等 |

### 迁移示例

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
    .block();
```

## 📚 相关资源

- [详细最佳实践指南](./RestTemplateBestPractices.md)
- [Spring 官方文档](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#rest-client-access)
- [Apache HttpClient 文档](https://hc.apache.org/httpcomponents-client-5.1.x/)
- [Spring WebClient 文档](https://docs.spring.io/spring-framework/docs/current/reference/html/web-reactive.html#webflux-client)

## 🤝 贡献

欢迎提交 Issue 和 Pull Request!

## 📄 许可

MIT License

---

**最后更新**: 2025-12-02
