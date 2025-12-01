# RestTemplate 超时配置

本项目提供了 RestTemplate 超时配置的完整示例。

## 配置方式

### 方式一：使用 RestTemplateBuilder（推荐）

这是 Spring Boot 推荐的方式，简单且易读：

```java
@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
            .setConnectTimeout(Duration.ofSeconds(5))   // 连接超时：5秒
            .setReadTimeout(Duration.ofSeconds(10))      // 读取超时：10秒
            .build();
}
```

### 方式二：使用 SimpleClientHttpRequestFactory

通过设置 `SimpleClientHttpRequestFactory` 来配置超时：

```java
@Bean
public RestTemplate customRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);   // 连接超时：5秒
    factory.setReadTimeout(10000);     // 读取超时：10秒
    return new RestTemplate(factory);
}
```

### 方式三：从配置文件读取（推荐用于生产环境）

通过 `@Value` 注解从配置文件读取超时参数，便于在不同环境使用不同配置：

```java
@Value("${rest-template.connect-timeout:5000}")
private int connectTimeout;

@Value("${rest-template.read-timeout:10000}")
private int readTimeout;

@Bean
public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
            .setConnectTimeout(Duration.ofMillis(connectTimeout))
            .setReadTimeout(Duration.ofMillis(readTimeout))
            .build();
}
```

配置文件（application.yml）：
```yaml
rest-template:
  connect-timeout: 5000    # 连接超时：5秒（毫秒）
  read-timeout: 10000      # 读取超时：10秒（毫秒）
```

## 超时参数说明

- **连接超时（Connect Timeout）**：建立连接的最大等待时间
  - 如果在此时间内无法建立连接，将抛出 `ResourceAccessException`
  - 建议值：3-10秒

- **读取超时（Read Timeout）**：从服务器读取数据的最大等待时间
  - 如果在此时间内没有收到响应，将抛出 `ResourceAccessException`
  - 建议值：10-30秒（根据业务需求调整）

## 异常处理

当请求超时时，会抛出 `org.springframework.web.client.ResourceAccessException`，需要捕获并处理：

```java
try {
    ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
} catch (ResourceAccessException e) {
    // 处理超时异常
    System.err.println("请求超时: " + e.getMessage());
}
```

## 文件说明

- `RestTemplateConfig.java` - 基础超时配置类（包含三种配置方式）
- `RestTemplateConfigWithProperties.java` - 从配置文件读取超时参数的配置类
- `RestTemplateExample.java` - RestTemplate 使用示例
- `application.yml` / `application.properties` - 配置文件示例
- `pom.xml` - Maven 依赖配置

## 依赖要求

- Spring Boot 2.x+
- 如需使用高级配置（HttpComponents），需要添加：
  ```xml
  <dependency>
      <groupId>org.apache.httpcomponents</groupId>
      <artifactId>httpclient</artifactId>
  </dependency>
  ```