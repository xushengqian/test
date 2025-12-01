import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

/**
 * 使用Apache HttpClient提交byte[]格式的文件
 * 需要添加依赖：
 * <dependency>
 *     <groupId>org.apache.httpcomponents</groupId>
 *     <artifactId>httpclient</artifactId>
 *     <version>4.5.14</version>
 * </dependency>
 * <dependency>
 *     <groupId>org.apache.httpcomponents</groupId>
 *     <artifactId>httpmime</artifactId>
 *     <version>4.5.14</version>
 * </dependency>
 */
public class ApacheHttpClientExample {
    
    /**
     * 使用Apache HttpClient提交表单文件
     * @param serverUrl 服务器URL
     * @param fileBytes 文件的byte数组
     * @param fileName 文件名
     * @param fieldName 表单字段名
     * @return 服务器响应
     * @throws IOException IO异常
     */
    public static String uploadFile(String serverUrl, byte[] fileBytes, String fileName, String fieldName) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(serverUrl);
        
        // 构建multipart表单数据
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addPart(fieldName, new ByteArrayBody(fileBytes, "application/octet-stream", fileName));
        
        HttpEntity multipart = builder.build();
        httpPost.setEntity(multipart);
        
        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            HttpEntity responseEntity = response.getEntity();
            String responseString = EntityUtils.toString(responseEntity, "UTF-8");
            EntityUtils.consume(responseEntity);
            return responseString;
        } finally {
            httpClient.close();
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
