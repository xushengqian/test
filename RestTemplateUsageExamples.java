import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RestTemplate 使用示例 - 实际场景应用
 */
@Component
public class RestTemplateUsageExamples {

    @Autowired
    private RestTemplate restTemplate;

    // ==================== 示例实体类 ====================
    
    static class User {
        private Long id;
        private String name;
        private String email;
        
        // Getters and Setters
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    static class ApiResponse<T> {
        private int code;
        private String message;
        private T data;
        
        // Getters and Setters
        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public T getData() { return data; }
        public void setData(T data) { this.data = data; }
    }

    // ==================== 实际使用示例 ====================

    /**
     * 示例1: 获取单个用户
     */
    public User getUserById(Long userId) {
        String url = "https://api.example.com/users/" + userId;
        return restTemplate.getForObject(url, User.class);
    }

    /**
     * 示例2: 获取用户列表
     */
    public List<User> getUserList() {
        String url = "https://api.example.com/users";
        
        ResponseEntity<List<User>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<User>>() {}
        );
        
        return response.getBody();
    }

    /**
     * 示例3: 带查询参数的请求
     */
    public List<User> searchUsers(String keyword, int page, int size) {
        String url = "https://api.example.com/users/search?keyword={keyword}&page={page}&size={size}";
        
        Map<String, Object> params = new HashMap<>();
        params.put("keyword", keyword);
        params.put("page", page);
        params.put("size", size);
        
        ResponseEntity<List<User>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<User>>() {},
                params
        );
        
        return response.getBody();
    }

    /**
     * 示例4: 创建用户 (POST JSON)
     */
    public User createUser(User user) {
        String url = "https://api.example.com/users";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<User> request = new HttpEntity<>(user, headers);
        
        ResponseEntity<User> response = restTemplate.postForEntity(url, request, User.class);
        return response.getBody();
    }

    /**
     * 示例5: 更新用户 (PUT)
     */
    public User updateUser(Long userId, User user) {
        String url = "https://api.example.com/users/" + userId;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<User> request = new HttpEntity<>(user, headers);
        
        ResponseEntity<User> response = restTemplate.exchange(
                url,
                HttpMethod.PUT,
                request,
                User.class
        );
        
        return response.getBody();
    }

    /**
     * 示例6: 删除用户
     */
    public void deleteUser(Long userId) {
        String url = "https://api.example.com/users/" + userId;
        restTemplate.delete(url);
    }

    /**
     * 示例7: 带认证的请求
     */
    public User getUserWithAuth(Long userId, String token) {
        String url = "https://api.example.com/users/" + userId;
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        
        HttpEntity<?> entity = new HttpEntity<>(headers);
        
        ResponseEntity<User> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                User.class
        );
        
        return response.getBody();
    }

    /**
     * 示例8: 处理包装的API响应
     */
    public User getUserFromWrappedResponse(Long userId) {
        String url = "https://api.example.com/users/" + userId;
        
        ResponseEntity<ApiResponse<User>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<User>>() {}
        );
        
        ApiResponse<User> apiResponse = response.getBody();
        if (apiResponse != null && apiResponse.getCode() == 200) {
            return apiResponse.getData();
        }
        
        throw new RuntimeException("API调用失败: " + apiResponse.getMessage());
    }

    /**
     * 示例9: 批量操作
     */
    public List<User> batchCreateUsers(List<User> users) {
        String url = "https://api.example.com/users/batch";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<List<User>> request = new HttpEntity<>(users, headers);
        
        ResponseEntity<List<User>> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                new ParameterizedTypeReference<List<User>>() {}
        );
        
        return response.getBody();
    }

    /**
     * 示例10: 处理响应头
     */
    public void processResponseHeaders(Long userId) {
        String url = "https://api.example.com/users/" + userId;
        
        ResponseEntity<User> response = restTemplate.getForEntity(url, User.class);
        
        // 获取响应头
        HttpHeaders headers = response.getHeaders();
        String contentType = headers.getFirst(HttpHeaders.CONTENT_TYPE);
        List<String> cookies = headers.get(HttpHeaders.SET_COOKIE);
        
        // 获取状态码
        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
        
        System.out.println("Content-Type: " + contentType);
        System.out.println("Status Code: " + statusCode);
        System.out.println("Cookies: " + cookies);
    }

    /**
     * 示例11: 下载文件
     */
    public byte[] downloadFile(String fileUrl) {
        ResponseEntity<byte[]> response = restTemplate.getForEntity(fileUrl, byte[].class);
        return response.getBody();
    }

    /**
     * 示例12: 重试机制 (简单实现)
     */
    public User getUserWithRetry(Long userId, int maxRetries) {
        String url = "https://api.example.com/users/" + userId;
        int retries = 0;
        
        while (retries < maxRetries) {
            try {
                return restTemplate.getForObject(url, User.class);
            } catch (Exception e) {
                retries++;
                if (retries >= maxRetries) {
                    throw new RuntimeException("重试" + maxRetries + "次后仍然失败", e);
                }
                
                try {
                    // 指数退避
                    Thread.sleep((long) Math.pow(2, retries) * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", ie);
                }
            }
        }
        
        return null;
    }
}
