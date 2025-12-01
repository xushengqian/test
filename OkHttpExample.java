import okhttp3.*;
import java.io.IOException;

/**
 * 使用OkHttp提交byte[]格式的文件
 * 需要添加依赖：
 * <dependency>
 *     <groupId>com.squareup.okhttp3</groupId>
 *     <artifactId>okhttp</artifactId>
 *     <version>4.12.0</version>
 * </dependency>
 */
public class OkHttpExample {
    
    /**
     * 使用OkHttp提交表单文件
     * @param serverUrl 服务器URL
     * @param fileBytes 文件的byte数组
     * @param fileName 文件名
     * @param fieldName 表单字段名
     * @return 服务器响应
     * @throws IOException IO异常
     */
    public static String uploadFile(String serverUrl, byte[] fileBytes, String fileName, String fieldName) throws IOException {
        OkHttpClient client = new OkHttpClient();
        
        // 构建multipart请求体
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    fieldName,
                    fileName,
                    RequestBody.create(fileBytes, MediaType.parse("application/octet-stream"))
                )
                .build();
        
        // 构建请求
        Request request = new Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build();
        
        // 执行请求
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
            return response.body().string();
        }
    }
    
    /**
     * 示例：上传文件
     */
    public static void main(String[] args) {
        try {
            byte[] fileBytes = "这是文件内容".getBytes("UTF-8");
            String fileName = "test.txt";
            
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
