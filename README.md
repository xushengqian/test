# Java HTTP表单提交byte[]格式文件示例

本项目提供了多种在Java中使用HTTP表单提交byte[]格式文件的方法。

## 实现方式

### 1. HttpURLConnection（Java标准库）
- **文件**: `HttpFormFileUpload.java`
- **优点**: 无需额外依赖，Java标准库自带
- **缺点**: 代码相对复杂，需要手动构建multipart请求体

### 2. Apache HttpClient
- **文件**: `ApacheHttpClientExample.java`
- **优点**: API简洁，功能强大
- **缺点**: 需要添加依赖

### 3. OkHttp
- **文件**: `OkHttpExample.java`
- **优点**: 现代API，性能优秀，广泛使用
- **缺点**: 需要添加依赖

### 4. Java 11+ HttpClient
- **文件**: `Java11HttpClientExample.java`
- **优点**: Java 11+标准库，异步支持
- **缺点**: 需要手动构建multipart请求体

## 使用方法

### 编译和运行

```bash
# 使用Maven编译
mvn compile

# 运行示例（需要修改URL为实际的服务器地址）
java -cp target/classes HttpFormFileUpload
```

### 代码示例

所有示例都提供了相同的方法签名：

```java
String uploadFile(String serverUrl, byte[] fileBytes, String fileName, String fieldName)
```

**参数说明：**
- `serverUrl`: 服务器上传URL
- `fileBytes`: 文件的byte数组
- `fileName`: 文件名
- `fieldName`: 表单字段名（通常为"file"）

## 依赖管理

如果使用Maven，依赖已在`pom.xml`中配置。如果使用Gradle，可以添加：

```gradle
dependencies {
    implementation 'org.apache.httpcomponents:httpclient:4.5.14'
    implementation 'org.apache.httpcomponents:httpmime:4.5.14'
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
}
```

## 注意事项

1. 所有示例都需要将`serverUrl`替换为实际的上传服务器地址
2. 根据实际需求选择合适的实现方式
3. 生产环境建议添加异常处理和重试机制
4. 大文件上传时考虑使用流式传输，避免一次性加载到内存
