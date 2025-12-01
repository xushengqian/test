import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 使用Java标准库HttpURLConnection提交byte[]格式的文件
 */
public class HttpFormFileUpload {
    
    /**
     * 使用HttpURLConnection提交表单文件
     * @param serverUrl 服务器URL
     * @param fileBytes 文件的byte数组
     * @param fileName 文件名
     * @param fieldName 表单字段名（默认为"file"）
     * @return 服务器响应
     * @throws IOException IO异常
     */
    public static String uploadFile(String serverUrl, byte[] fileBytes, String fileName, String fieldName) throws IOException {
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        
        URL url = new URL(serverUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        // 设置请求方法和属性
        connection.setDoOutput(true);
        connection.setDoInput(true);
        connection.setUseCaches(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Connection", "Keep-Alive");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        
        try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
            // 写入文件字段开始
            outputStream.writeBytes(twoHyphens + boundary + lineEnd);
            outputStream.writeBytes("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + fileName + "\"" + lineEnd);
            outputStream.writeBytes("Content-Type: application/octet-stream" + lineEnd);
            outputStream.writeBytes(lineEnd);
            
            // 写入文件内容
            outputStream.write(fileBytes);
            outputStream.writeBytes(lineEnd);
            
            // 写入结束边界
            outputStream.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            outputStream.flush();
        }
        
        // 读取响应
        int responseCode = connection.getResponseCode();
        StringBuilder response = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    responseCode == HttpURLConnection.HTTP_OK 
                        ? connection.getInputStream() 
                        : connection.getErrorStream(),
                    StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
        }
        
        connection.disconnect();
        return response.toString();
    }
    
    /**
     * 示例：上传文件
     */
    public static void main(String[] args) {
        try {
            // 示例：读取文件为byte数组
            byte[] fileBytes = "这是文件内容".getBytes(StandardCharsets.UTF_8);
            String fileName = "test.txt";
            
            // 上传文件
            String response = uploadFile(
                "http://example.com/upload",
                fileBytes,
                fileName,
                "file"
            );
            
            System.out.println("上传成功，服务器响应：" + response);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
