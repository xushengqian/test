package com.example.demo.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.entity.User;
import com.example.demo.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 用户服务类
 * 演示多种解决 null 值更新的方案
 */
@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    
    /**
     * 方案1：使用 UpdateWrapper 更新（推荐）
     * 可以精确控制哪些字段需要更新，包括 null 值
     * 
     * @param id 用户ID
     * @param user 要更新的用户信息
     * @return 是否更新成功
     */
    public boolean updateByIdWithWrapper(Long id, User user) {
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id);
        
        // 使用 set 方法可以强制更新字段，即使值为 null
        if (user.getUsername() != null) {
            updateWrapper.set("username", user.getUsername());
        }
        
        // 强制设置 email 为 null（如果需要）
        updateWrapper.set("email", user.getEmail());
        
        if (user.getPhone() != null) {
            updateWrapper.set("phone", user.getPhone());
        }
        
        if (user.getAge() != null) {
            updateWrapper.set("age", user.getAge());
        }
        
        // 强制设置 status，即使为 null
        updateWrapper.set("status", user.getStatus());
        
        // 强制设置 description，即使为 null
        updateWrapper.set("description", user.getDescription());
        
        return this.update(updateWrapper);
    }
    
    /**
     * 方案2：使用 UpdateWrapper 的 setSql 方法
     * 直接写 SQL 片段，完全控制更新逻辑
     * 
     * @param id 用户ID
     * @param description 描述（可以为null）
     * @return 是否更新成功
     */
    public boolean updateDescriptionToNull(Long id, String description) {
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", id);
        updateWrapper.setSql("description = '" + (description == null ? "NULL" : description) + "'");
        
        return this.update(updateWrapper);
    }
    
    /**
     * 方案3：使用自定义 Mapper 方法
     * 
     * @param user 用户对象
     * @return 影响行数
     */
    public int updateByIdForceAll(User user) {
        return baseMapper.updateByIdForceAll(user);
    }
    
    /**
     * 方案4：使用自定义 Mapper 方法更新特定字段
     * 
     * @param id 用户ID
     * @param description 描述
     * @return 影响行数
     */
    public int updateDescriptionById(Long id, String description) {
        return baseMapper.updateDescriptionById(id, description);
    }
    
    /**
     * 方案5：先查询再更新（不推荐，但有时必要）
     * 
     * @param id 用户ID
     * @param user 要更新的用户信息
     * @return 是否更新成功
     */
    public boolean updateByIdWithQuery(Long id, User user) {
        User existingUser = this.getById(id);
        if (existingUser == null) {
            return false;
        }
        
        // 手动设置需要更新的字段
        if (user.getUsername() != null) {
            existingUser.setUsername(user.getUsername());
        }
        
        // 强制更新为 null
        existingUser.setEmail(user.getEmail());
        existingUser.setStatus(user.getStatus());
        existingUser.setDescription(user.getDescription());
        
        if (user.getPhone() != null) {
            existingUser.setPhone(user.getPhone());
        }
        
        if (user.getAge() != null) {
            existingUser.setAge(user.getAge());
        }
        
        return this.updateById(existingUser);
    }
}