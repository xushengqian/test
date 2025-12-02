package com.example.resttemplate.service;

import com.example.resttemplate.dto.UserDTO;
import com.example.resttemplate.exception.RestTemplateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户服务示例
 * 
 * 展示 RestTemplate 的最佳实践用法
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final RestTemplate restTemplate;

    // 基础 URL（实际项目中应该从配置文件读取）
    private static final String BASE_URL = "https://jsonplaceholder.typicode.com";

    /**
     * GET 请求示例 - 获取单个用户
     * 
     * 最佳实践：
     * 1. 使用 UriComponentsBuilder 构建 URL
     * 2. 使用 ResponseEntity 接收响应
     * 3. 检查响应状态码
     * 4. 使用 try-catch 处理异常
     */
    public UserDTO getUserById(Long id) {
        try {
            // 使用 UriComponentsBuilder 构建 URL（推荐方式）
            String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .path("/users/{id}")
                    .buildAndExpand(id)
                    .toUriString();

            log.info("请求用户信息，ID: {}", id);

            // 方式1：使用 ResponseEntity 接收响应（推荐）
            ResponseEntity<UserDTO> response = restTemplate.getForEntity(url, UserDTO.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

            throw new RestTemplateException(
                    "获取用户失败",
                    response.getStatusCode(),
                    null
            );

        } catch (RestClientException e) {
            log.error("获取用户失败，ID: {}", id, e);
            throw new RestTemplateException("获取用户失败: " + e.getMessage(), e, null, null);
        }
    }

    /**
     * GET 请求示例 - 获取用户列表
     * 
     * 最佳实践：
     * 1. 使用 ParameterizedTypeReference 处理泛型类型
     * 2. 使用 exchange 方法获得更多控制
     */
    public List<UserDTO> getAllUsers() {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .path("/users")
                    .toUriString();

            // 使用 exchange 方法，可以设置请求头等
            ResponseEntity<List<UserDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<UserDTO>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

            return List.of();

        } catch (RestClientException e) {
            log.error("获取用户列表失败", e);
            throw new RestTemplateException("获取用户列表失败: " + e.getMessage(), e, null, null);
        }
    }

    /**
     * POST 请求示例 - 创建用户
     * 
     * 最佳实践：
     * 1. 设置请求头（Content-Type 等）
     * 2. 使用 HttpEntity 封装请求体和请求头
     * 3. 使用 exchange 方法发送 POST 请求
     */
    public UserDTO createUser(UserDTO user) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .path("/users")
                    .toUriString();

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Request-Id", generateRequestId());

            // 封装请求体和请求头
            HttpEntity<UserDTO> requestEntity = new HttpEntity<>(user, headers);

            // 发送 POST 请求
            ResponseEntity<UserDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    UserDTO.class
            );

            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                return response.getBody();
            }

            throw new RestTemplateException(
                    "创建用户失败",
                    response.getStatusCode(),
                    null
            );

        } catch (RestClientException e) {
            log.error("创建用户失败", e);
            throw new RestTemplateException("创建用户失败: " + e.getMessage(), e, null, null);
        }
    }

    /**
     * PUT 请求示例 - 更新用户
     * 
     * 最佳实践：
     * 1. 使用 PUT 方法更新资源
     * 2. 使用 UriComponentsBuilder 构建带路径参数的 URL
     */
    public UserDTO updateUser(Long id, UserDTO user) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .path("/users/{id}")
                    .buildAndExpand(id)
                    .toUriString();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<UserDTO> requestEntity = new HttpEntity<>(user, headers);

            ResponseEntity<UserDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    requestEntity,
                    UserDTO.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

            throw new RestTemplateException(
                    "更新用户失败",
                    response.getStatusCode(),
                    null
            );

        } catch (RestClientException e) {
            log.error("更新用户失败，ID: {}", id, e);
            throw new RestTemplateException("更新用户失败: " + e.getMessage(), e, null, null);
        }
    }

    /**
     * DELETE 请求示例 - 删除用户
     * 
     * 最佳实践：
     * 1. DELETE 请求通常不需要响应体
     * 2. 检查响应状态码确认删除成功
     */
    public void deleteUser(Long id) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .path("/users/{id}")
                    .buildAndExpand(id)
                    .toUriString();

            restTemplate.delete(url);

            log.info("删除用户成功，ID: {}", id);

        } catch (RestClientException e) {
            log.error("删除用户失败，ID: {}", id, e);
            throw new RestTemplateException("删除用户失败: " + e.getMessage(), e, null, null);
        }
    }

    /**
     * GET 请求示例 - 带查询参数
     * 
     * 最佳实践：
     * 1. 使用 UriComponentsBuilder 添加查询参数
     * 2. 避免手动拼接 URL
     */
    public List<UserDTO> searchUsers(String name, String email) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .path("/users");

            // 添加查询参数
            if (name != null && !name.isEmpty()) {
                builder.queryParam("name", name);
            }
            if (email != null && !email.isEmpty()) {
                builder.queryParam("email", email);
            }

            String url = builder.toUriString();

            ResponseEntity<List<UserDTO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<UserDTO>>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

            return List.of();

        } catch (RestClientException e) {
            log.error("搜索用户失败", e);
            throw new RestTemplateException("搜索用户失败: " + e.getMessage(), e, null, null);
        }
    }

    /**
     * GET 请求示例 - 使用 Map 传递路径参数
     * 
     * 最佳实践：
     * 1. 当有多个路径参数时，使用 Map 更清晰
     */
    public UserDTO getUserWithMap(Long userId, String resource) {
        try {
            Map<String, Object> uriVariables = new HashMap<>();
            uriVariables.put("userId", userId);
            uriVariables.put("resource", resource);

            String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                    .path("/users/{userId}/{resource}")
                    .buildAndExpand(uriVariables)
                    .toUriString();

            ResponseEntity<UserDTO> response = restTemplate.getForEntity(url, UserDTO.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }

            throw new RestTemplateException(
                    "获取用户资源失败",
                    response.getStatusCode(),
                    null
            );

        } catch (RestClientException e) {
            log.error("获取用户资源失败", e);
            throw new RestTemplateException("获取用户资源失败: " + e.getMessage(), e, null, null);
        }
    }

    /**
     * 生成请求 ID（示例方法）
     */
    private String generateRequestId() {
        return "REQ-" + System.currentTimeMillis();
    }
}
