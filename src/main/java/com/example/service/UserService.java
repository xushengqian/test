package com.example.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.entity.User;
import com.example.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 用户服务类
 * 提供多种解决 null 字段更新的方案
 */
@Service
public class UserService extends ServiceImpl<UserMapper, User> {
    
    /**
     * 方案1：使用 updateById（默认不更新null字段）
     * 问题：字段为null时不会更新到数据库
     */
    public boolean updateByIdDefault(User user) {
        // 默认情况下，null字段不会被更新
        return updateById(user);
    }
    
    /**
     * 方案2：使用 UpdateWrapper 显式设置null值
     * 优点：可以精确控制哪些字段设置为null
     */
    public boolean updateByWrapperWithNull(Long id, User user) {
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .set("username", user.getUsername())
                .set("email", user.getEmail())  // 即使为null也会更新
                .set("phone", user.getPhone())  // 即使为null也会更新
                .set("address", user.getAddress())
                .set("age", user.getAge())
                .set("status", user.getStatus());
        
        return update(wrapper);
    }
    
    /**
     * 方案3：使用 LambdaUpdateWrapper（推荐）
     * 优点：类型安全，可以精确控制每个字段
     */
    public boolean updateByLambdaWrapper(Long id, User user) {
        LambdaUpdateWrapper<User> wrapper = Wrappers.<User>lambdaUpdate()
                .eq(User::getId, id)
                .set(User::getUsername, user.getUsername())
                .set(User::getEmail, user.getEmail())  // 即使为null也会更新
                .set(User::getPhone, user.getPhone())  // 即使为null也会更新
                .set(User::getAddress, user.getAddress())
                .set(User::getAge, user.getAge())
                .set(User::getStatus, user.getStatus());
        
        return update(wrapper);
    }
    
    /**
     * 方案4：使用自定义SQL
     * 优点：完全控制SQL语句
     */
    public boolean updateByCustomSql(User user) {
        return baseMapper.updateByIdWithNull(user) > 0;
    }
    
    /**
     * 方案5：先查询再更新
     * 缺点：需要两次数据库操作，性能较差
     */
    public boolean updateBySelectFirst(User updateUser) {
        User dbUser = getById(updateUser.getId());
        if (dbUser == null) {
            return false;
        }
        
        // 只更新传入的非null字段
        if (updateUser.getUsername() != null) {
            dbUser.setUsername(updateUser.getUsername());
        }
        // 如果需要将某个字段设置为null，需要特殊处理
        // 例如：通过一个标志位或特殊值来表示需要设置为null
        if ("NULL".equals(updateUser.getEmail())) {
            dbUser.setEmail(null);
        } else if (updateUser.getEmail() != null) {
            dbUser.setEmail(updateUser.getEmail());
        }
        
        if (updateUser.getPhone() != null) {
            dbUser.setPhone(updateUser.getPhone());
        }
        if (updateUser.getAddress() != null) {
            dbUser.setAddress(updateUser.getAddress());
        }
        if (updateUser.getAge() != null) {
            dbUser.setAge(updateUser.getAge());
        }
        if (updateUser.getStatus() != null) {
            dbUser.setStatus(updateUser.getStatus());
        }
        
        return updateById(dbUser);
    }
    
    /**
     * 方案6：使用 allEq 方法
     * 可以控制null值的处理方式
     */
    public boolean updateByAllEq(Long id, User user) {
        UpdateWrapper<User> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id);
        
        // 构建更新字段的Map
        java.util.Map<String, Object> updateMap = new java.util.HashMap<>();
        updateMap.put("username", user.getUsername());
        updateMap.put("email", user.getEmail());
        updateMap.put("phone", user.getPhone());
        updateMap.put("address", user.getAddress());
        updateMap.put("age", user.getAge());
        updateMap.put("status", user.getStatus());
        
        // allEq的第二个参数为true表示null值也会更新
        wrapper.allEq(updateMap, true);
        
        return update(wrapper);
    }
    
    /**
     * 方案7：动态SQL更新
     * 通过forceNull参数控制是否更新null值
     */
    public boolean updateDynamic(User user, boolean forceUpdateNull) {
        return baseMapper.updateDynamic(user, forceUpdateNull) > 0;
    }
    
    /**
     * 清空某个字段（设置为null）
     */
    public boolean clearField(Long id, String fieldName) {
        LambdaUpdateWrapper<User> wrapper = Wrappers.<User>lambdaUpdate()
                .eq(User::getId, id);
        
        switch (fieldName) {
            case "email":
                wrapper.set(User::getEmail, null);
                break;
            case "phone":
                wrapper.set(User::getPhone, null);
                break;
            case "address":
                wrapper.set(User::getAddress, null);
                break;
            default:
                return false;
        }
        
        return update(wrapper);
    }
}