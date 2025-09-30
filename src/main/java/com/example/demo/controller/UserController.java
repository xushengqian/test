package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户控制器
 * 演示各种更新方案的使用
 */
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    /**
     * 创建用户
     */
    @PostMapping
    public User createUser(@RequestBody User user) {
        userService.save(user);
        return user;
    }
    
    /**
     * 获取用户
     */
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        return userService.getById(id);
    }
    
    /**
     * 标准更新（null 值不会更新）
     */
    @PutMapping("/{id}/standard")
    public boolean updateUserStandard(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        return userService.updateById(user);
    }
    
    /**
     * 使用 UpdateWrapper 更新（推荐方案）
     */
    @PutMapping("/{id}/wrapper")
    public boolean updateUserWithWrapper(@PathVariable Long id, @RequestBody User user) {
        return userService.updateByIdWithWrapper(id, user);
    }
    
    /**
     * 强制更新所有字段（包括 null 值）
     */
    @PutMapping("/{id}/force-all")
    public boolean updateUserForceAll(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        return userService.updateByIdForceAll(user) > 0;
    }
    
    /**
     * 更新描述字段为 null
     */
    @PutMapping("/{id}/description")
    public boolean updateDescription(@PathVariable Long id, @RequestParam(required = false) String description) {
        return userService.updateDescriptionById(id, description) > 0;
    }
    
    /**
     * 先查询再更新
     */
    @PutMapping("/{id}/query-update")
    public boolean updateUserWithQuery(@PathVariable Long id, @RequestBody User user) {
        return userService.updateByIdWithQuery(id, user);
    }
}