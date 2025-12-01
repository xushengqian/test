import java.nio.charset.StandardCharsets;

/**
 * 使用 Spring WebClient 上传 byte[] 文件
 * 
 * Maven 依赖:
 * <dependency>
 *     <groupId>org.springframework.boot</groupId>
 *     <artifactId>spring-boot-starter-webflux</artifactId>
 * </dependency>
 */
public class SpringWebClientExample {

    /**
     * 使用 Spring WebClient 上传文件（响应式方式）
     * 注意: 需要 Spring WebFlux 依赖
     */
    public static String uploadWithWebClient(String uploadUrl, byte[] fileBytes,
                                            String fileName, String fieldName) {
        /*
        import org.springframework.core.io.ByteArrayResource;
        import org.springframework.http.HttpHeaders;
        import org.springframework.http.MediaType;
        import org.springframework.http.client.MultipartBodyBuilder;
        import org.springframework.web.reactive.function.BodyInserters;
        import org.springframework.web.reactive.function.client.WebClient;
        
        try {
            // 创建 WebClient
            WebClient webClient = WebClient.builder()
                    .baseUrl(uploadUrl)
                    .build();
            
            // 构建 multipart 请求体
            MultipartBodyBuilder builder = new MultipartBodyBuilder();
            
            // 从 byte[] 创建文件部分
            builder.part(fieldName, new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            }).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);
            
            // 添加其他参数
            // builder.part("userId", "12345");
            // builder.part("description", "文件描述");
            
            // 发送请求
            String response = webClient.post()
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // 阻塞等待响应（同步方式）
            
            return "上传成功，响应: " + response;
        } catch (Exception e) {
            e.printStackTrace();
            return "上传失败: " + e.getMessage();
        }
        */
        
        return "请添加 Spring WebFlux 依赖后使用此方法";
    }

    /**
     * 使用 Spring RestTemplate 上传文件（传统方式）
     * 注意: 需要 Spring Web 依赖
     */
    public static String uploadWithRestTemplate(String uploadUrl, byte[] fileBytes,
                                               String fileName, String fieldName) {
        /*
        import org.springframework.core.io.ByteArrayResource;
        import org.springframework.http.HttpEntity;
        import org.springframework.http.HttpHeaders;
        import org.springframework.http.MediaType;
        import org.springframework.http.ResponseEntity;
        import org.springframework.util.LinkedMultiValueMap;
        import org.springframework.util.MultiValueMap;
        import org.springframework.web.client.RestTemplate;
        
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            // 构建 multipart 请求体
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // 从 byte[] 创建文件资源
            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            
            body.add(fieldName, fileResource);
            
            // 添加其他参数
            // body.add("userId", "12345");
            // body.add("description", "文件描述");
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            // 发送请求
            ResponseEntity<String> response = restTemplate.postForEntity(
                uploadUrl,
                requestEntity,
                String.class
            );
            
            return "响应码: " + response.getStatusCode() + "\n响应内容: " + response.getBody();
        } catch (Exception e) {
            e.printStackTrace();
            return "上传失败: " + e.getMessage();
        }
        */
        
        return "请添加 Spring Web 依赖后使用此方法";
    }

    public static void main(String[] args) {
        System.out.println("Spring 文件上传示例");
        System.out.println("=============================================");
        System.out.println("WebClient 需要依赖:");
        System.out.println("<dependency>");
        System.out.println("    <groupId>org.springframework.boot</groupId>");
        System.out.println("    <artifactId>spring-boot-starter-webflux</artifactId>");
        System.out.println("</dependency>");
        System.out.println();
        System.out.println("RestTemplate 需要依赖:");
        System.out.println("<dependency>");
        System.out.println("    <groupId>org.springframework.boot</groupId>");
        System.out.println("    <artifactId>spring-boot-starter-web</artifactId>");
        System.out.println("</dependency>");
        System.out.println("=============================================");
        
        String testContent = "Spring 测试文件内容";
        byte[] fileBytes = testContent.getBytes(StandardCharsets.UTF_8);
        
        // WebClient 示例
        String result1 = uploadWithWebClient(
            "http://localhost:8080/upload",
            fileBytes,
            "test-spring-webclient.txt",
            "file"
        );
        System.out.println("\nWebClient: " + result1);
        
        // RestTemplate 示例
        String result2 = uploadWithRestTemplate(
            "http://localhost:8080/upload",
            fileBytes,
            "test-spring-resttemplate.txt",
            "file"
        );
        System.out.println("\nRestTemplate: " + result2);
    }
}
