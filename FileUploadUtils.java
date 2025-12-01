import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 文件上传工具类 - 提供各种便捷的文件上传方法
 */
public class FileUploadUtils {

    /**
     * 从本地文件路径上传文件
     * 
     * @param uploadUrl  上传URL
     * @param filePath   本地文件路径
     * @param fieldName  表单字段名
     * @return 上传结果
     */
    public static UploadResult uploadFromFilePath(String uploadUrl, String filePath, String fieldName) 
            throws IOException {
        Path path = Paths.get(filePath);
        byte[] fileBytes = Files.readAllBytes(path);
        String fileName = path.getFileName().toString();
        
        return uploadBytes(uploadUrl, fileBytes, fileName, fieldName, null);
    }

    /**
     * 从 Base64 字符串上传文件
     * 
     * @param uploadUrl    上传URL
     * @param base64String Base64编码的文件数据
     * @param fileName     文件名
     * @param fieldName    表单字段名
     * @return 上传结果
     */
    public static UploadResult uploadFromBase64(String uploadUrl, String base64String, 
                                               String fileName, String fieldName) throws IOException {
        // 移除可能存在的 data:image/png;base64, 前缀
        String base64Data = base64String;
        if (base64String.contains(",")) {
            base64Data = base64String.split(",")[1];
        }
        
        byte[] fileBytes = Base64.getDecoder().decode(base64Data);
        return uploadBytes(uploadUrl, fileBytes, fileName, fieldName, null);
    }

    /**
     * 从 InputStream 上传文件
     * 
     * @param uploadUrl   上传URL
     * @param inputStream 输入流
     * @param fileName    文件名
     * @param fieldName   表单字段名
     * @return 上传结果
     */
    public static UploadResult uploadFromInputStream(String uploadUrl, InputStream inputStream,
                                                    String fileName, String fieldName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        
        byte[] fileBytes = baos.toByteArray();
        return uploadBytes(uploadUrl, fileBytes, fileName, fieldName, null);
    }

    /**
     * 批量上传多个文件（同一个请求）
     * 
     * @param uploadUrl 上传URL
     * @param files     文件列表
     * @param fieldName 表单字段名（所有文件使用相同字段名）
     * @return 上传结果
     */
    public static UploadResult uploadMultipleFiles(String uploadUrl, List<FileData> files, 
                                                  String fieldName) throws IOException {
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
            
            // 遍历所有文件
            for (FileData file : files) {
                writer.append("--").append(boundary).append(CRLF);
                writer.append("Content-Disposition: form-data; name=\"").append(fieldName)
                      .append("\"; filename=\"").append(file.fileName).append("\"").append(CRLF);
                writer.append("Content-Type: ").append(file.contentType).append(CRLF);
                writer.append("Content-Transfer-Encoding: binary").append(CRLF);
                writer.append(CRLF);
                writer.flush();
                
                output.write(file.data);
                output.flush();
                
                writer.append(CRLF);
                writer.flush();
            }
            
            // 结束标记
            writer.append("--").append(boundary).append("--").append(CRLF);
            writer.flush();
        }
        
        return getResponse(connection);
    }

    /**
     * 上传字节数组（核心方法）
     * 
     * @param uploadUrl 上传URL
     * @param fileBytes 文件字节数组
     * @param fileName  文件名
     * @param fieldName 表单字段名
     * @param params    额外参数
     * @return 上传结果
     */
    public static UploadResult uploadBytes(String uploadUrl, byte[] fileBytes, String fileName,
                                          String fieldName, Map<String, String> params) throws IOException {
        String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");
        String CRLF = "\r\n";
        
        URL url = new URL(uploadUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(60000);
        
        try (OutputStream output = connection.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true)) {
            
            // 添加额外参数
            if (params != null && !params.isEmpty()) {
                for (Map.Entry<String, String> entry : params.entrySet()) {
                    writer.append("--").append(boundary).append(CRLF);
                    writer.append("Content-Disposition: form-data; name=\"")
                          .append(entry.getKey()).append("\"").append(CRLF);
                    writer.append(CRLF);
                    writer.append(entry.getValue()).append(CRLF);
                    writer.flush();
                }
            }
            
            // 添加文件
            writer.append("--").append(boundary).append(CRLF);
            writer.append("Content-Disposition: form-data; name=\"").append(fieldName)
                  .append("\"; filename=\"").append(fileName).append("\"").append(CRLF);
            writer.append("Content-Type: ").append(detectContentType(fileName)).append(CRLF);
            writer.append("Content-Transfer-Encoding: binary").append(CRLF);
            writer.append(CRLF);
            writer.flush();
            
            output.write(fileBytes);
            output.flush();
            
            writer.append(CRLF);
            writer.append("--").append(boundary).append("--").append(CRLF);
            writer.flush();
        }
        
        return getResponse(connection);
    }

    /**
     * 根据文件名检测 Content-Type
     */
    private static String detectContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }

    /**
     * 获取响应
     */
    private static UploadResult getResponse(HttpURLConnection connection) throws IOException {
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
        } catch (Exception e) {
            // 忽略读取响应时的异常
        }
        
        connection.disconnect();
        
        return new UploadResult(
            responseCode,
            response.toString().trim(),
            responseCode >= 200 && responseCode < 300
        );
    }

    /**
     * 文件数据封装类
     */
    public static class FileData {
        public byte[] data;
        public String fileName;
        public String contentType;
        
        public FileData(byte[] data, String fileName) {
            this.data = data;
            this.fileName = fileName;
            this.contentType = detectContentType(fileName);
        }
        
        public FileData(byte[] data, String fileName, String contentType) {
            this.data = data;
            this.fileName = fileName;
            this.contentType = contentType;
        }
    }

    /**
     * 上传结果封装类
     */
    public static class UploadResult {
        public int statusCode;
        public String responseBody;
        public boolean success;
        
        public UploadResult(int statusCode, String responseBody, boolean success) {
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.success = success;
        }
        
        @Override
        public String toString() {
            return String.format("上传%s\n状态码: %d\n响应内容: %s",
                success ? "成功" : "失败", statusCode, responseBody);
        }
    }

    // ============ 使用示例 ============
    public static void main(String[] args) {
        String uploadUrl = "http://localhost:8080/upload";
        
        try {
            System.out.println("=== 示例1: 从本地文件上传 ===");
            // UploadResult result1 = uploadFromFilePath(uploadUrl, "/path/to/file.pdf", "file");
            // System.out.println(result1);
            
            System.out.println("\n=== 示例2: 从 Base64 上传 ===");
            String base64 = Base64.getEncoder().encodeToString("测试内容".getBytes(StandardCharsets.UTF_8));
            UploadResult result2 = uploadFromBase64(uploadUrl, base64, "test.txt", "file");
            System.out.println(result2);
            
            System.out.println("\n=== 示例3: 从字节数组上传（带参数） ===");
            byte[] fileBytes = "文件内容示例".getBytes(StandardCharsets.UTF_8);
            Map<String, String> params = new HashMap<>();
            params.put("userId", "user123");
            params.put("category", "documents");
            
            UploadResult result3 = uploadBytes(uploadUrl, fileBytes, "document.txt", "file", params);
            System.out.println(result3);
            
            System.out.println("\n=== 示例4: 批量上传多个文件 ===");
            List<FileData> files = new ArrayList<>();
            files.add(new FileData("文件1内容".getBytes(StandardCharsets.UTF_8), "file1.txt"));
            files.add(new FileData("文件2内容".getBytes(StandardCharsets.UTF_8), "file2.txt"));
            files.add(new FileData("文件3内容".getBytes(StandardCharsets.UTF_8), "file3.txt"));
            
            UploadResult result4 = uploadMultipleFiles(uploadUrl, files, "files");
            System.out.println(result4);
            
            System.out.println("\n=== 示例5: 从 ByteArrayInputStream 上传 ===");
            byte[] data = "Stream测试内容".getBytes(StandardCharsets.UTF_8);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            UploadResult result5 = uploadFromInputStream(uploadUrl, bais, "stream-file.txt", "file");
            System.out.println(result5);
            
        } catch (Exception e) {
            System.err.println("上传失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
