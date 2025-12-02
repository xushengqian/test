import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * RestTemplate 服务类 - 封装常用操作的最佳实践
 */
@Service
public class RestTemplateService {

    @Autowired
    private RestTemplate restTemplate;

    // ==================== GET 请求 ====================

    /**
     * 最佳实践1: 简单的 GET 请求
     */
    public <T> T getForObject(String url, Class<T> responseType) {
        try {
            return restTemplate.getForObject(url, responseType);
        } catch (Exception e) {
            // 记录日志并处理异常
            throw new RestApiException("GET请求失败: " + url, e);
        }
    }

    /**
     * 最佳实践2: 带URL参数的 GET 请求
     */
    public <T> T getWithParams(String url, Map<String, Object> params, Class<T> responseType) {
        // 推荐使用 UriComponentsBuilder 构建URL
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        params.forEach(builder::queryParam);
        
        String finalUrl = builder.toUriString();
        return restTemplate.getForObject(finalUrl, responseType);
    }

    /**
     * 最佳实践3: 带路径变量的 GET 请求
     */
    public <T> T getWithPathVariables(String url, Map<String, Object> pathVariables, Class<T> responseType) {
        // 例如: /users/{userId}/posts/{postId}
        return restTemplate.getForObject(url, responseType, pathVariables);
    }

    /**
     * 最佳实践4: 获取完整的响应信息 (包括状态码、响应头等)
     */
    public <T> ResponseEntity<T> getWithFullResponse(String url, Class<T> responseType) {
        return restTemplate.getForEntity(url, responseType);
    }

    /**
     * 最佳实践5: 带自定义请求头的 GET 请求
     */
    public <T> ResponseEntity<T> getWithHeaders(String url, HttpHeaders headers, Class<T> responseType) {
        HttpEntity<?> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(url, HttpMethod.GET, entity, responseType);
    }

    /**
     * 最佳实践6: 获取泛型集合 (如 List<User>)
     */
    public <T> List<T> getList(String url, ParameterizedTypeReference<List<T>> typeReference) {
        ResponseEntity<List<T>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                typeReference
        );
        return response.getBody();
    }

    // ==================== POST 请求 ====================

    /**
     * 最佳实践7: 简单的 POST 请求
     */
    public <T> T postForObject(String url, Object request, Class<T> responseType) {
        return restTemplate.postForObject(url, request, responseType);
    }

    /**
     * 最佳实践8: POST JSON 请求
     */
    public <T> ResponseEntity<T> postJson(String url, Object requestBody, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.postForEntity(url, entity, responseType);
    }

    /**
     * 最佳实践9: POST 表单数据
     */
    public <T> T postForm(String url, Map<String, String> formData, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        // 构建表单数据
        StringBuilder formBody = new StringBuilder();
        formData.forEach((key, value) -> {
            if (formBody.length() > 0) {
                formBody.append("&");
            }
            formBody.append(key).append("=").append(value);
        });
        
        HttpEntity<String> entity = new HttpEntity<>(formBody.toString(), headers);
        return restTemplate.postForObject(url, entity, responseType);
    }

    /**
     * 最佳实践10: 文件上传
     */
    /*
    public <T> T uploadFile(String url, MultipartFile file, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());
        
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        return restTemplate.postForObject(url, entity, responseType);
    }
    */

    // ==================== PUT 请求 ====================

    /**
     * 最佳实践11: PUT 更新请求
     */
    public <T> ResponseEntity<T> put(String url, Object requestBody, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(url, HttpMethod.PUT, entity, responseType);
    }

    // ==================== DELETE 请求 ====================

    /**
     * 最佳实践12: DELETE 删除请求
     */
    public void delete(String url) {
        restTemplate.delete(url);
    }

    /**
     * 最佳实践13: DELETE 请求并返回响应
     */
    public <T> ResponseEntity<T> deleteWithResponse(String url, Class<T> responseType) {
        return restTemplate.exchange(url, HttpMethod.DELETE, null, responseType);
    }

    // ==================== PATCH 请求 ====================

    /**
     * 最佳实践14: PATCH 部分更新请求
     */
    public <T> ResponseEntity<T> patch(String url, Object requestBody, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Object> entity = new HttpEntity<>(requestBody, headers);
        return restTemplate.exchange(url, HttpMethod.PATCH, entity, responseType);
    }

    // ==================== 通用 exchange 方法 ====================

    /**
     * 最佳实践15: 使用 exchange 进行完全自定义的请求
     * 这是最灵活的方法，适用于复杂场景
     */
    public <T> ResponseEntity<T> exchange(
            String url,
            HttpMethod method,
            Object requestBody,
            Map<String, String> headers,
            Class<T> responseType) {
        
        HttpHeaders httpHeaders = new HttpHeaders();
        if (headers != null) {
            headers.forEach(httpHeaders::add);
        }
        
        HttpEntity<Object> entity = new HttpEntity<>(requestBody, httpHeaders);
        return restTemplate.exchange(url, method, entity, responseType);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建通用的请求头
     */
    private HttpHeaders buildCommonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }
}
