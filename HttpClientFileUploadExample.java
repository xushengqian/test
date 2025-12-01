import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 使用 Java 11+ HttpClient 上传 byte[] 文件
 * 这是更现代化的方式
 */
public class HttpClientFileUploadExample {

    /**
     * 使用 Java 11+ HttpClient 上传 byte[] 文件
     * 
     * @param uploadUrl 上传URL
     * @param fileBytes 文件字节数组
     * @param fileName  文件名
     * @param fieldName 表单字段名
     * @return 响应内容
     */
    public static String uploadFileUsingHttpClient(String uploadUrl, byte[] fileBytes,
                                                  String fileName, String fieldName) throws IOException, InterruptedException {
        String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");
        
        // 构建 multipart/form-data 请求体
        byte[] requestBody = buildMultipartBody(fileBytes, fileName, fieldName, boundary, null);
        
        // 创建 HttpClient
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        
        // 创建请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .timeout(Duration.ofMinutes(2))
                .build();
        
        // 发送请求
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        return "响应码: " + response.statusCode() + "\n响应内容: " + response.body();
    }

    /**
     * 上传文件并携带其他表单参数
     * 
     * @param uploadUrl 上传URL
     * @param fileBytes 文件字节数组
     * @param fileName  文件名
     * @param fieldName 文件字段名
     * @param params    其他表单参数
     * @return 响应内容
     */
    public static String uploadFileWithParamsHttpClient(String uploadUrl, byte[] fileBytes,
                                                       String fileName, String fieldName,
                                                       Map<String, String> params) throws IOException, InterruptedException {
        String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");
        
        byte[] requestBody = buildMultipartBody(fileBytes, fileName, fieldName, boundary, params);
        
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("User-Agent", "Java HttpClient")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .timeout(Duration.ofMinutes(2))
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        return "响应码: " + response.statusCode() + "\n响应内容: " + response.body();
    }

    /**
     * 构建 multipart/form-data 请求体
     */
    private static byte[] buildMultipartBody(byte[] fileBytes, String fileName, 
                                            String fieldName, String boundary,
                                            Map<String, String> params) throws IOException {
        String CRLF = "\r\n";
        List<byte[]> parts = new ArrayList<>();
        
        // 添加普通表单参数
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                StringBuilder sb = new StringBuilder();
                sb.append("--").append(boundary).append(CRLF);
                sb.append("Content-Disposition: form-data; name=\"").append(entry.getKey()).append("\"").append(CRLF);
                sb.append(CRLF);
                sb.append(entry.getValue()).append(CRLF);
                parts.add(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        
        // 添加文件部分的头部
        StringBuilder fileHeader = new StringBuilder();
        fileHeader.append("--").append(boundary).append(CRLF);
        fileHeader.append("Content-Disposition: form-data; name=\"").append(fieldName)
                  .append("\"; filename=\"").append(fileName).append("\"").append(CRLF);
        fileHeader.append("Content-Type: application/octet-stream").append(CRLF);
        fileHeader.append("Content-Transfer-Encoding: binary").append(CRLF);
        fileHeader.append(CRLF);
        parts.add(fileHeader.toString().getBytes(StandardCharsets.UTF_8));
        
        // 添加文件字节数据
        parts.add(fileBytes);
        
        // 添加结束标记
        String footer = CRLF + "--" + boundary + "--" + CRLF;
        parts.add(footer.getBytes(StandardCharsets.UTF_8));
        
        // 合并所有部分
        int totalLength = parts.stream().mapToInt(arr -> arr.length).sum();
        byte[] result = new byte[totalLength];
        int position = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, position, part.length);
            position += part.length;
        }
        
        return result;
    }

    /**
     * 异步上传文件
     */
    public static void uploadFileAsync(String uploadUrl, byte[] fileBytes,
                                      String fileName, String fieldName) throws IOException {
        String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] requestBody = buildMultipartBody(fileBytes, fileName, fieldName, boundary, null);
        
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .timeout(Duration.ofMinutes(2))
                .build();
        
        // 异步发送请求
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
              .thenApply(HttpResponse::body)
              .thenAccept(body -> {
                  System.out.println("异步上传完成，响应: " + body);
              })
              .exceptionally(e -> {
                  System.err.println("上传失败: " + e.getMessage());
                  return null;
              });
        
        System.out.println("异步请求已发送...");
    }

    // ============ 测试示例 ============
    public static void main(String[] args) {
        try {
            // 创建测试文件字节数组
            String testContent = "这是使用HttpClient上传的文件\n支持Java 11+";
            byte[] fileBytes = testContent.getBytes(StandardCharsets.UTF_8);
            
            String uploadUrl = "http://localhost:8080/upload";
            String fileName = "test-httpclient.txt";
            String fieldName = "file";
            
            // 方法1: 基本上传
            System.out.println("=== 方法1: HttpClient 基本上传 ===");
            String result1 = uploadFileUsingHttpClient(uploadUrl, fileBytes, fileName, fieldName);
            System.out.println(result1);
            
            // 方法2: 带参数上传
            System.out.println("\n=== 方法2: HttpClient 带参数上传 ===");
            Map<String, String> params = new java.util.HashMap<>();
            params.put("userId", "67890");
            params.put("category", "documents");
            
            String result2 = uploadFileWithParamsHttpClient(uploadUrl, fileBytes, fileName, fieldName, params);
            System.out.println(result2);
            
            // 方法3: 异步上传
            System.out.println("\n=== 方法3: HttpClient 异步上传 ===");
            uploadFileAsync(uploadUrl, fileBytes, fileName, fieldName);
            
            // 等待异步请求完成
            Thread.sleep(3000);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
