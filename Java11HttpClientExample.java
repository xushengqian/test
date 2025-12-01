import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * 使用Java 11+ HttpClient提交byte[]格式的文件
 * 注意：Java 11+的HttpClient不直接支持multipart/form-data，
 * 需要手动构建multipart请求体
 */
public class Java11HttpClientExample {
    
    /**
     * 使用Java 11 HttpClient提交表单文件
     * @param serverUrl 服务器URL
     * @param fileBytes 文件的byte数组
     * @param fileName 文件名
     * @param fieldName 表单字段名
     * @return 服务器响应
     * @throws IOException IO异常
     * @throws InterruptedException 中断异常
     */
    public static String uploadFile(String serverUrl, byte[] fileBytes, String fileName, String fieldName) 
            throws IOException, InterruptedException {
        
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        
        // 构建multipart请求体
        StringBuilder bodyBuilder = new StringBuilder();
        bodyBuilder.append(twoHyphens).append(boundary).append(lineEnd);
        bodyBuilder.append("Content-Disposition: form-data; name=\"").append(fieldName)
                   .append("\"; filename=\"").append(fileName).append("\"").append(lineEnd);
        bodyBuilder.append("Content-Type: application/octet-stream").append(lineEnd);
        bodyBuilder.append(lineEnd);
        
        // 将字符串部分转换为字节
        byte[] headerBytes = bodyBuilder.toString().getBytes(StandardCharsets.UTF_8);
        byte[] lineEndBytes = lineEnd.getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = (lineEnd + twoHyphens + boundary + twoHyphens + lineEnd)
                .getBytes(StandardCharsets.UTF_8);
        
        // 计算总长度
        int totalLength = headerBytes.length + fileBytes.length + lineEndBytes.length + footerBytes.length;
        
        // 构建完整的请求体
        byte[] requestBody = new byte[totalLength];
        int offset = 0;
        System.arraycopy(headerBytes, 0, requestBody, offset, headerBytes.length);
        offset += headerBytes.length;
        System.arraycopy(fileBytes, 0, requestBody, offset, fileBytes.length);
        offset += fileBytes.length;
        System.arraycopy(lineEndBytes, 0, requestBody, offset, lineEndBytes.length);
        offset += lineEndBytes.length;
        System.arraycopy(footerBytes, 0, requestBody, offset, footerBytes.length);
        
        // 创建HttpClient和HttpRequest
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(serverUrl))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();
        
        // 发送请求并获取响应
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        
        return response.body();
    }
    
    /**
     * 示例：上传文件
     */
    public static void main(String[] args) {
        try {
            byte[] fileBytes = "这是文件内容".getBytes(StandardCharsets.UTF_8);
            String fileName = "test.txt";
            
            String response = uploadFile(
                "http://example.com/upload",
                fileBytes,
                fileName,
                "file"
            );
            
            System.out.println("上传成功，服务器响应：" + response);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
