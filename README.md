# RestTemplate 最佳实践

本项目提供了 Spring RestTemplate 的完整最佳实践指南和示例代码。

## 📖 内容概览

### 核心文件

1. **配置类**
   - `RestTemplateConfig.java` - RestTemplate 配置（包括连接池、超时设置）
   - `application.yml` - 应用配置文件

2. **拦截器**
   - `LoggingInterceptor.java` - 日志拦截器（记录请求/响应）
   - `AuthInterceptor.java` - 认证拦截器（自动添加 token）

3. **错误处理**
   - `CustomErrorHandler.java` - 自定义错误处理器

4. **服务封装**
   - `RestTemplateService.java` - 封装常用操作
   - `RestTemplateUsageExamples.java` - 实际使用示例

5. **文档**
   - `RestTemplateBestPractices.md` - 详细的最佳实践指南（⭐推荐阅读）
   - `README_RESTTEMPLATE.md` - 项目说明文档

## 🚀 快速开始

### 基本配置示例

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

### 使用示例

```java
// GET 请求
User user = restTemplate.getForObject(url, User.class);

// POST 请求
User newUser = restTemplate.postForObject(url, user, User.class);

// 带请求头
HttpHeaders headers = new HttpHeaders();
headers.set("Authorization", "Bearer token");
HttpEntity<User> entity = new HttpEntity<>(user, headers);
ResponseEntity<User> response = restTemplate.exchange(url, HttpMethod.POST, entity, User.class);
```

## 💡 核心最佳实践

### ✅ 必须做的

1. **设置超时时间** - 防止线程无限阻塞
2. **使用连接池** - 提高性能（生产环境）
3. **添加拦截器** - 统一处理日志、认证等
4. **实现错误处理器** - 优雅处理异常
5. **复用 RestTemplate 实例** - 避免重复创建

### ⚠️ 避免的陷阱

1. ❌ 不设置超时（可能导致线程永久阻塞）
2. ❌ 每次创建新实例（浪费资源）
3. ❌ 不处理异常（缺乏容错性）
4. ❌ 在日志中暴露敏感信息
5. ❌ 生产环境不使用连接池

## 📋 配置项说明

### 超时配置

```yaml
rest:
  client:
    connect-timeout: 5000  # 连接超时（毫秒）
    read-timeout: 10000    # 读取超时（毫秒）
```

### 连接池配置

```yaml
rest:
  client:
    pool:
      max-total: 100           # 最大连接数
      max-per-route: 20        # 每个路由的最大连接数
      connection-request-timeout: 3000  # 从连接池获取连接的超时时间
```

## 📊 功能对比

| 功能 | SimpleClientHttpRequestFactory | Apache HttpClient |
|-----|-------------------------------|-------------------|
| 连接池 | ❌ | ✅ |
| 性能 | 一般 | 优秀 |
| 重试机制 | ❌ | ✅ |
| 推荐场景 | 开发/测试 | 生产环境 |

## 🔄 迁移建议

RestTemplate 从 Spring 5.0 开始处于维护模式，建议考虑迁移到 **WebClient**（支持响应式编程）。

## 📚 学习路径

1. 阅读 `RestTemplateBestPractices.md` 了解完整最佳实践
2. 查看 `RestTemplateConfig.java` 了解配置方法
3. 参考 `RestTemplateUsageExamples.java` 学习实际使用
4. 使用 `pom.xml` 中的依赖配置搭建项目

## 📖 详细文档

请查看 [RestTemplateBestPractices.md](./RestTemplateBestPractices.md) 获取完整的最佳实践指南。

---

**版本**: Spring Boot 3.x  
**最后更新**: 2025-12-02