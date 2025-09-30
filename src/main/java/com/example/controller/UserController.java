package com.example.controller;

import com.example.entity.User;
import com.example.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户控制器
 * 演示不同的更新方式
 */
@RestController
@RequestMapping("/user")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    /**
     * 创建用户
     */
    @PostMapping("/create")
    public Map<String, Object> createUser(@RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        boolean success = userService.save(user);
        result.put("success", success);
        result.put("message", success ? "创建成功" : "创建失败");
        if (success) {
            result.put("userId", user.getId());
        }
        return result;
    }
    
    /**
     * 获取用户
     */
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getById(id);
    }
    
    /**
     * 方案1：使用默认的updateById（null字段不更新）
     */
    @PutMapping("/update/default")
    public Map<String, Object> updateDefault(@RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        boolean success = userService.updateByIdDefault(user);
        result.put("success", success);
        result.put("method", "updateById (默认方式)");
        result.put("description", "null字段不会被更新到数据库");
        return result;
    }
    
    /**
     * 方案2：使用UpdateWrapper
     */
    @PutMapping("/update/wrapper/{id}")
    public Map<String, Object> updateByWrapper(@PathVariable Long id, @RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        boolean success = userService.updateByWrapperWithNull(id, user);
        result.put("success", success);
        result.put("method", "UpdateWrapper");
        result.put("description", "使用set方法可以将字段更新为null");
        return result;
    }
    
    /**
     * 方案3：使用LambdaUpdateWrapper
     */
    @PutMapping("/update/lambda/{id}")
    public Map<String, Object> updateByLambda(@PathVariable Long id, @RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        boolean success = userService.updateByLambdaWrapper(id, user);
        result.put("success", success);
        result.put("method", "LambdaUpdateWrapper");
        result.put("description", "类型安全的方式，可以将字段更新为null");
        return result;
    }
    
    /**
     * 方案4：使用自定义SQL
     */
    @PutMapping("/update/custom")
    public Map<String, Object> updateByCustom(@RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        boolean success = userService.updateByCustomSql(user);
        result.put("success", success);
        result.put("method", "自定义SQL");
        result.put("description", "完全控制SQL语句，可以更新null");
        return result;
    }
    
    /**
     * 方案5：先查询再更新
     */
    @PutMapping("/update/select-first")
    public Map<String, Object> updateBySelectFirst(@RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        boolean success = userService.updateBySelectFirst(user);
        result.put("success", success);
        result.put("method", "先查询再更新");
        result.put("description", "需要两次数据库操作，性能较差");
        return result;
    }
    
    /**
     * 方案6：清空某个字段
     */
    @PutMapping("/clear/{id}/{fieldName}")
    public Map<String, Object> clearField(@PathVariable Long id, @PathVariable String fieldName) {
        Map<String, Object> result = new HashMap<>();
        boolean success = userService.clearField(id, fieldName);
        result.put("success", success);
        result.put("method", "清空字段");
        result.put("description", "将指定字段设置为null");
        result.put("field", fieldName);
        return result;
    }
    
    /**
     * 方案7：动态更新
     */
    @PutMapping("/update/dynamic")
    public Map<String, Object> updateDynamic(
            @RequestBody User user,
            @RequestParam(defaultValue = "false") boolean forceUpdateNull) {
        Map<String, Object> result = new HashMap<>();
        boolean success = userService.updateDynamic(user, forceUpdateNull);
        result.put("success", success);
        result.put("method", "动态SQL");
        result.put("description", "通过参数控制是否更新null值");
        result.put("forceUpdateNull", forceUpdateNull);
        return result;
    }
}