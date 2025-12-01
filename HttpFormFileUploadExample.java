import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Java HTTP 表单提交 byte[] 格式文件示例
 * 使用原生 HttpURLConnection
 */
public class HttpFormFileUploadExample {

    /**
     * 使用 multipart/form-data 上传 byte[] 文件
     * 
     * @param uploadUrl 上传接口URL
     * @param fileBytes 文件字节数组
     * @param fileName  文件名
     * @param fieldName 表单字段名（如 "file"）
     * @return 响应内容
     */
    public static String uploadFileFromBytes(String uploadUrl, byte[] fileBytes, 
                                            String fileName, String fieldName) throws IOException {
        // 生成分隔符
        String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");
        String CRLF = "\r\n";
        
        URL url = new URL(uploadUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        // 配置连接
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setRequestProperty("User-Agent", "Java HttpURLConnection");
        
        try (OutputStream output = connection.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true)) {
            
            // 写入文件部分的头部
            writer.append("--").append(boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"").append(fieldName)
                  .append("\"; filename=\"").append(fileName).append("\"").append(CRLF);
            writer.append("Content-Type: application/octet-stream").append(CRLF);
            writer.append("Content-Transfer-Encoding: binary").append(CRLF);
            writer.append(CRLF);
            writer.flush();
            
            // 写入文件字节数据
            output.write(fileBytes);
            output.flush();
            
            // 写入结束标记
            writer.append(CRLF);
            writer.append("--").append(boundary).append("--").append(CRLF);
            writer.flush();
        }
        
        // 读取响应
        int responseCode = connection.getResponseCode();
        StringBuilder response = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                    StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
        }
        
        connection.disconnect();
        return "响应码: " + responseCode + "\n响应内容: " + response.toString();
    }

    /**
     * 上传 byte[] 文件，同时提交其他表单参数
     * 
     * @param uploadUrl 上传接口URL
     * @param fileBytes 文件字节数组
     * @param fileName  文件名
     * @param fieldName 文件字段名
     * @param params    其他表单参数（键值对）
     * @return 响应内容
     */
    public static String uploadFileWithParams(String uploadUrl, byte[] fileBytes, 
                                             String fileName, String fieldName,
                                             java.util.Map<String, String> params) throws IOException {
        String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");
        String CRLF = "\r\n";
        
        URL url = new URL(uploadUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        
        try (OutputStream output = connection.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true)) {
            
            // 先写入其他表单参数
            if (params != null && !params.isEmpty()) {
                for (java.util.Map.Entry<String, String> entry : params.entrySet()) {
                    writer.append("--").append(boundary).append(CRLF);
                    writer.append("Content-Disposition: form-data; name=\"")
                          .append(entry.getKey()).append("\"").append(CRLF);
                    writer.append(CRLF);
                    writer.append(entry.getValue()).append(CRLF);
                    writer.flush();
                }
            }
            
            // 写入文件部分
            writer.append("--").append(boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"").append(fieldName)
                  .append("\"; filename=\"").append(fileName).append("\"").append(CRLF);
            writer.append("Content-Type: application/octet-stream").append(CRLF);
            writer.append("Content-Transfer-Encoding: binary").append(CRLF);
            writer.append(CRLF);
            writer.flush();
            
            output.write(fileBytes);
            output.flush();
            
            writer.append(CRLF);
            writer.append("--").append(boundary).append("--").append(CRLF);
            writer.flush();
        }
        
        // 读取响应
        int responseCode = connection.getResponseCode();
        StringBuilder response = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                    StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
        }
        
        connection.disconnect();
        return "响应码: " + responseCode + "\n响应内容: " + response.toString();
    }

    // ============ 测试示例 ============
    public static void main(String[] args) {
        try {
            // 示例1: 创建一个测试文件的字节数组
            String testContent = "这是测试文件内容\nHello World!";
            byte[] fileBytes = testContent.getBytes(StandardCharsets.UTF_8);
            
            String uploadUrl = "http://localhost:8080/upload";
            String fileName = "test.txt";
            String fieldName = "file";
            
            // 方法1: 仅上传文件
            System.out.println("=== 方法1: 仅上传文件 ===");
            String result1 = uploadFileFromBytes(uploadUrl, fileBytes, fileName, fieldName);
            System.out.println(result1);
            
            // 方法2: 上传文件 + 其他参数
            System.out.println("\n=== 方法2: 上传文件 + 其他参数 ===");
            java.util.Map<String, String> params = new java.util.HashMap<>();
            params.put("userId", "12345");
            params.put("description", "文件描述信息");
            
            String result2 = uploadFileWithParams(uploadUrl, fileBytes, fileName, fieldName, params);
            System.out.println(result2);
            
            // 示例3: 从现有文件读取为byte[]后上传
            // byte[] existingFileBytes = Files.readAllBytes(Paths.get("/path/to/file.pdf"));
            // String result3 = uploadFileFromBytes(uploadUrl, existingFileBytes, "document.pdf", "file");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
