import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 使用 Apache HttpClient 上传 byte[] 文件
 * 
 * Maven 依赖:
 * <dependency>
 *     <groupId>org.apache.httpcomponents.client5</groupId>
 *     <artifactId>httpclient5</artifactId>
 *     <version>5.2.1</version>
 * </dependency>
 * 
 * 或使用旧版本:
 * <dependency>
 *     <groupId>org.apache.httpcomponents</groupId>
 *     <artifactId>httpclient</artifactId>
 *     <version>4.5.14</version>
 * </dependency>
 */
public class ApacheHttpClientExample {

    /**
     * 使用 Apache HttpClient 5.x 上传文件
     * 注意: 此代码需要导入 Apache HttpClient 依赖才能运行
     */
    public static String uploadWithApacheHttpClient5(String uploadUrl, byte[] fileBytes,
                                                    String fileName, String fieldName) {
        /*
        try {
            // 创建 HttpClient
            CloseableHttpClient httpClient = HttpClients.createDefault();
            
            // 创建 POST 请求
            HttpPost httpPost = new HttpPost(uploadUrl);
            
            // 构建 multipart 实体
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            builder.setCharset(StandardCharsets.UTF_8);
            
            // 添加文件部分（从 byte[] 创建）
            builder.addBinaryBody(
                fieldName,
                fileBytes,
                ContentType.APPLICATION_OCTET_STREAM,
                fileName
            );
            
            // 可选: 添加其他表单参数
            // builder.addTextBody("userId", "12345", ContentType.TEXT_PLAIN);
            // builder.addTextBody("description", "文件描述", ContentType.TEXT_PLAIN);
            
            HttpEntity multipart = builder.build();
            httpPost.setEntity(multipart);
            
            // 执行请求
            CloseableHttpResponse response = httpClient.execute(httpPost);
            
            try {
                // 读取响应
                int statusCode = response.getCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                return "响应码: " + statusCode + "\n响应内容: " + responseBody;
            } finally {
                response.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "上传失败: " + e.getMessage();
        }
        */
        
        return "请添加 Apache HttpClient 依赖后使用此方法";
    }

    /**
     * 使用 Apache HttpClient 4.x 上传文件
     * 注意: 此代码需要导入 Apache HttpClient 4.x 依赖才能运行
     */
    public static String uploadWithApacheHttpClient4(String uploadUrl, byte[] fileBytes,
                                                    String fileName, String fieldName) {
        /*
        try {
            CloseableHttpClient httpClient = HttpClients.createDefault();
            HttpPost httpPost = new HttpPost(uploadUrl);
            
            // 构建 multipart 实体
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            
            // 从 byte[] 创建文件部分
            builder.addBinaryBody(
                fieldName,
                fileBytes,
                ContentType.APPLICATION_OCTET_STREAM,
                fileName
            );
            
            // 添加其他参数
            // builder.addTextBody("userId", "12345", ContentType.TEXT_PLAIN);
            
            HttpEntity multipart = builder.build();
            httpPost.setEntity(multipart);
            
            // 执行请求
            CloseableHttpResponse response = httpClient.execute(httpPost);
            
            try {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                return "响应码: " + statusCode + "\n响应内容: " + responseBody;
            } finally {
                response.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "上传失败: " + e.getMessage();
        }
        */
        
        return "请添加 Apache HttpClient 4.x 依赖后使用此方法";
    }

    public static void main(String[] args) {
        System.out.println("Apache HttpClient 示例");
        System.out.println("=============================================");
        System.out.println("此示例需要添加 Maven 依赖:");
        System.out.println();
        System.out.println("Apache HttpClient 5.x:");
        System.out.println("<dependency>");
        System.out.println("    <groupId>org.apache.httpcomponents.client5</groupId>");
        System.out.println("    <artifactId>httpclient5</artifactId>");
        System.out.println("    <version>5.2.1</version>");
        System.out.println("</dependency>");
        System.out.println();
        System.out.println("或 Apache HttpClient 4.x:");
        System.out.println("<dependency>");
        System.out.println("    <groupId>org.apache.httpcomponents</groupId>");
        System.out.println("    <artifactId>httpclient</artifactId>");
        System.out.println("    <version>4.5.14</version>");
        System.out.println("</dependency>");
        System.out.println("=============================================");
        
        // 取消注释上面的方法代码，添加依赖后即可使用
        String testContent = "Apache HttpClient 测试文件";
        byte[] fileBytes = testContent.getBytes(StandardCharsets.UTF_8);
        
        String result = uploadWithApacheHttpClient5(
            "http://localhost:8080/upload",
            fileBytes,
            "test-apache.txt",
            "file"
        );
        System.out.println(result);
    }
}
