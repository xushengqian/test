# Java HTTP 表单提交 byte[] 格式文件示例

本项目提供了多种在Java中通过HTTP表单提交byte[]格式文件的完整示例。

## 📋 目录

1. **HttpFormFileUploadExample.java** - 使用原生 HttpURLConnection（无需额外依赖）
2. **HttpClientFileUploadExample.java** - 使用 Java 11+ HttpClient（推荐，现代化）
3. **ApacheHttpClientExample.java** - 使用 Apache HttpClient
4. **SpringWebClientExample.java** - 使用 Spring WebClient/RestTemplate
5. **FileUploadUtils.java** - 实用工具类（支持多种数据源）
6. **SimpleUploadServer.java** - 测试服务器（用于本地测试）

## 🎯 快速测试

### 方式一：一键测试（推荐）

**Linux/Mac:**
```bash
# 自动编译、启动服务器、运行测试
bash run-test.sh
```

**Windows:**
```cmd
REM 自动编译、启动服务器、运行测试
run-test.bat
```

### 方式二：手动测试

**1. 编译项目**

Linux/Mac:
```bash
bash build.sh
```

Windows:
```cmd
build.bat
```

**2. 启动测试服务器**

```bash
java SimpleUploadServer
```

服务器将在 http://localhost:8080 启动
- 上传接口: http://localhost:8080/upload
- 网页界面: http://localhost:8080/

**3. 运行快速测试**

新开一个终端窗口：
```bash
java QuickTest
```

或者在浏览器访问 http://localhost:8080 使用网页上传测试

## 🚀 快速开始

### 方法1: 使用原生 HttpURLConnection（推荐初学者）

**优点**: 无需任何外部依赖，JDK自带

```java
// 基本用法
byte[] fileBytes = "文件内容".getBytes(StandardCharsets.UTF_8);
String result = HttpFormFileUploadExample.uploadFileFromBytes(
    "http://localhost:8080/upload",  // 上传URL
    fileBytes,                        // 文件字节数组
    "test.txt",                       // 文件名
    "file"                            // 表单字段名
);

// 带参数上传
Map<String, String> params = new HashMap<>();
params.put("userId", "12345");
params.put("description", "文件描述");

String result = HttpFormFileUploadExample.uploadFileWithParams(
    uploadUrl, fileBytes, fileName, fieldName, params
);
```

### 方法2: 使用 Java 11+ HttpClient（推荐现代项目）

**优点**: JDK 11+内置，支持异步，API现代化

```java
// 同步上传
String result = HttpClientFileUploadExample.uploadFileUsingHttpClient(
    "http://localhost:8080/upload",
    fileBytes,
    "test.txt",
    "file"
);

// 异步上传
HttpClientFileUploadExample.uploadFileAsync(
    uploadUrl, fileBytes, fileName, fieldName
);
```

### 方法3: 使用 Apache HttpClient

**优点**: 功能强大，广泛使用

**Maven 依赖**:
```xml
<!-- Apache HttpClient 5.x -->
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
    <version>5.2.1</version>
</dependency>

<!-- 或 Apache HttpClient 4.x -->
<dependency>
    <groupId>org.apache.httpcomponents</groupId>
    <artifactId>httpclient</artifactId>
    <version>4.5.14</version>
</dependency>
```

### 方法4: 使用 Spring Framework

**优点**: 与Spring生态集成，代码简洁

**Maven 依赖**:
```xml
<!-- WebClient (推荐) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- RestTemplate -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

## 🛠️ 使用工具类（推荐）

`FileUploadUtils.java` 提供了更便捷的方法：

```java
// 1. 从本地文件上传
UploadResult result = FileUploadUtils.uploadFromFilePath(
    "http://localhost:8080/upload",
    "/path/to/document.pdf",
    "file"
);

// 2. 从 Base64 字符串上传
UploadResult result = FileUploadUtils.uploadFromBase64(
    uploadUrl,
    base64String,
    "image.png",
    "file"
);

// 3. 从 InputStream 上传
UploadResult result = FileUploadUtils.uploadFromInputStream(
    uploadUrl,
    inputStream,
    "data.bin",
    "file"
);

// 4. 批量上传多个文件
List<FileData> files = new ArrayList<>();
files.add(new FileData(bytes1, "file1.txt"));
files.add(new FileData(bytes2, "file2.pdf"));
UploadResult result = FileUploadUtils.uploadMultipleFiles(uploadUrl, files, "files");

// 5. 带参数上传
Map<String, String> params = new HashMap<>();
params.put("userId", "123");
UploadResult result = FileUploadUtils.uploadBytes(
    uploadUrl, fileBytes, fileName, "file", params
);
```

## 📝 常见使用场景

### 场景1: 从文件读取并上传

```java
// 读取现有文件为 byte[]
byte[] fileBytes = Files.readAllBytes(Paths.get("/path/to/file.pdf"));

// 上传
String result = HttpFormFileUploadExample.uploadFileFromBytes(
    "http://api.example.com/upload",
    fileBytes,
    "document.pdf",
    "file"
);
```

### 场景2: 从内存生成文件并上传

```java
// 动态生成文件内容
String content = "用户ID: 12345\n订单号: ORDER-001\n金额: 1000.00";
byte[] fileBytes = content.getBytes(StandardCharsets.UTF_8);

// 上传
String result = HttpClientFileUploadExample.uploadFileUsingHttpClient(
    "http://api.example.com/upload-report",
    fileBytes,
    "report-" + System.currentTimeMillis() + ".txt",
    "report"
);
```

### 场景3: 从数据库BLOB读取并上传

```java
// 从数据库读取 BLOB
// ResultSet rs = ...
// byte[] fileBytes = rs.getBytes("file_data");
// String fileName = rs.getString("file_name");

// 上传到另一个服务
String result = HttpFormFileUploadExample.uploadFileFromBytes(
    "http://backup.example.com/store",
    fileBytes,
    fileName,
    "file"
);
```

### 场景4: Base64字符串转换后上传

```java
// Base64 编码的文件数据
String base64String = "SGVsbG8gV29ybGQh...";
byte[] fileBytes = java.util.Base64.getDecoder().decode(base64String);

// 上传
String result = HttpClientFileUploadExample.uploadFileUsingHttpClient(
    "http://api.example.com/upload",
    fileBytes,
    "decoded-file.bin",
    "file"
);
```

## 🔧 multipart/form-data 格式说明

HTTP 表单文件上传使用 `multipart/form-data` 格式，结构如下：

```
POST /upload HTTP/1.1
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW

------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="userId"

12345
------WebKitFormBoundary7MA4YWxkTrZu0gW
Content-Disposition: form-data; name="file"; filename="test.txt"
Content-Type: application/octet-stream
Content-Transfer-Encoding: binary

[文件二进制数据]
------WebKitFormBoundary7MA4YWxkTrZu0gW--
```

## 📚 关键知识点

### 1. boundary（边界标识符）
- 用于分隔不同的表单字段
- 必须唯一，不能出现在实际数据中
- 通常使用 UUID 或时间戳生成

### 2. Content-Disposition
- 描述字段类型（form-data）
- `name`: 字段名称（服务器接收的参数名）
- `filename`: 文件名（可选，仅文件字段需要）

### 3. Content-Type
- `application/octet-stream`: 通用二进制流（默认）
- `text/plain`: 文本文件
- `image/jpeg`, `image/png`: 图片文件
- `application/pdf`: PDF文件

### 4. byte[] 来源
```java
// 1. 从文件读取
byte[] bytes1 = Files.readAllBytes(Paths.get("file.txt"));

// 2. 从字符串转换
byte[] bytes2 = "内容".getBytes(StandardCharsets.UTF_8);

// 3. 从 InputStream 读取
ByteArrayOutputStream baos = new ByteArrayOutputStream();
inputStream.transferTo(baos);
byte[] bytes3 = baos.toByteArray();

// 4. Base64 解码
byte[] bytes4 = Base64.getDecoder().decode(base64String);

// 5. 从 MultipartFile (Spring)
byte[] bytes5 = multipartFile.getBytes();
```

## ⚠️ 注意事项

1. **字符编码**: 统一使用 UTF-8 编码
2. **超时设置**: 大文件上传需要设置合适的超时时间
3. **文件大小**: 注意服务器对上传文件大小的限制
4. **Content-Type**: 根据实际文件类型设置合适的 MIME 类型
5. **异常处理**: 做好网络异常、IO异常的处理
6. **连接管理**: 确保 HttpClient 连接正确关闭

## 🧪 测试服务器

你可以使用以下简单的 Spring Boot 控制器测试上传：

```java
@RestController
public class FileUploadController {
    
    @PostMapping("/upload")
    public ResponseEntity<String> handleFileUpload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String description) {
        
        try {
            String fileName = file.getOriginalFilename();
            long fileSize = file.getSize();
            
            // 保存文件或处理字节数组
            byte[] bytes = file.getBytes();
            
            String response = String.format(
                "文件上传成功!\n文件名: %s\n大小: %d bytes\nuserId: %s\n描述: %s",
                fileName, fileSize, userId, description
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("上传失败: " + e.getMessage());
        }
    }
}
```

## 🎯 方法选择建议

| 场景 | 推荐方法 | 理由 |
|------|---------|------|
| 学习/简单项目 | HttpURLConnection | 无依赖，代码清晰 |
| Java 11+ 项目 | Java HttpClient | 现代化，支持异步 |
| 已使用 Apache 库 | Apache HttpClient | 功能强大，配置灵活 |
| Spring Boot 项目 | WebClient/RestTemplate | 与框架集成，代码简洁 |
| 高并发场景 | Java HttpClient (异步) | 性能好，资源占用少 |

## 📖 相关资源

- [RFC 2388 - multipart/form-data](https://www.ietf.org/rfc/rfc2388.txt)
- [Java HttpClient 文档](https://docs.oracle.com/en/java/javase/11/docs/api/java.net.http/java/net/http/HttpClient.html)
- [Apache HttpClient 文档](https://hc.apache.org/httpcomponents-client-5.2.x/)
- [Spring WebClient 文档](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)

## 💡 常见问题

### Q1: 文件名包含中文乱码？
A: 确保使用 UTF-8 编码，并在 Content-Disposition 中正确编码文件名。

### Q2: 大文件上传超时？
A: 增加超时时间设置，或考虑分片上传。

### Q3: 服务器返回 413 错误？
A: 文件超过服务器限制，检查服务器配置。

### Q4: 上传后文件损坏？
A: 检查 Content-Type 和编码设置，确保二进制数据正确传输。

---

**作者**: AI Assistant  
**日期**: 2025-12-01  
**License**: MIT
