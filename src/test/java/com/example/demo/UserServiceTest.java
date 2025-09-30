package com.example.demo;

import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户服务测试类
 * 演示各种 null 值更新解决方案
 */
@SpringBootTest
@ActiveProfiles("test")
public class UserServiceTest {
    
    @Autowired
    private UserService userService;
    
    /**
     * 测试标准 updateById 方法（null 值不会更新）
     */
    @Test
    public void testStandardUpdateById() {
        // 创建用户
        User user = new User()
                .setUsername("testuser")
                .setEmail("test@example.com")
                .setPhone("13800138000")
                .setAge(25)
                .setStatus(1)
                .setDescription("原始描述");
        
        userService.save(user);
        Long userId = user.getId();
        
        // 尝试将某些字段更新为 null
        User updateUser = new User()
                .setId(userId)
                .setUsername("newusername")
                .setEmail(null)  // 尝试设置为 null
                .setPhone("13900139000")
                .setAge(null)    // 尝试设置为 null
                .setStatus(null) // 尝试设置为 null（但实体类中配置了 IGNORED 策略）
                .setDescription(null); // 尝试设置为 null
        
        // 执行更新
        boolean result = userService.updateById(updateUser);
        assertTrue(result);
        
        // 验证结果
        User updatedUser = userService.getById(userId);
        assertEquals("newusername", updatedUser.getUsername()); // 非 null 值被更新
        assertEquals("test@example.com", updatedUser.getEmail()); // null 值未更新，保持原值
        assertEquals("13900139000", updatedUser.getPhone()); // 非 null 值被更新
        assertEquals(25, updatedUser.getAge()); // null 值未更新，保持原值
        assertNull(updatedUser.getStatus()); // 配置了 IGNORED 策略，null 值被更新
        assertEquals("原始描述", updatedUser.getDescription()); // null 值未更新，保持原值
        
        System.out.println("标准 updateById 测试完成：");
        System.out.println("更新后的用户：" + updatedUser);
    }
    
    /**
     * 测试使用 UpdateWrapper 更新（推荐方案）
     */
    @Test
    public void testUpdateWithWrapper() {
        // 创建用户
        User user = new User()
                .setUsername("testuser2")
                .setEmail("test2@example.com")
                .setPhone("13800138001")
                .setAge(30)
                .setStatus(1)
                .setDescription("原始描述2");
        
        userService.save(user);
        Long userId = user.getId();
        
        // 使用 UpdateWrapper 更新
        User updateUser = new User()
                .setUsername("newusername2")
                .setEmail(null)
                .setPhone("13900139001")
                .setAge(null)
                .setStatus(null)
                .setDescription(null);
        
        boolean result = userService.updateByIdWithWrapper(userId, updateUser);
        assertTrue(result);
        
        // 验证结果
        User updatedUser = userService.getById(userId);
        assertEquals("newusername2", updatedUser.getUsername());
        assertNull(updatedUser.getEmail()); // 使用 UpdateWrapper，null 值被更新
        assertEquals("13900139001", updatedUser.getPhone());
        assertNull(updatedUser.getAge()); // 使用 UpdateWrapper，null 值被更新
        assertNull(updatedUser.getStatus());
        assertNull(updatedUser.getDescription()); // 使用 UpdateWrapper，null 值被更新
        
        System.out.println("UpdateWrapper 测试完成：");
        System.out.println("更新后的用户：" + updatedUser);
    }
    
    /**
     * 测试自定义 SQL 强制更新所有字段
     */
    @Test
    public void testUpdateByIdForceAll() {
        // 创建用户
        User user = new User()
                .setUsername("testuser3")
                .setEmail("test3@example.com")
                .setPhone("13800138002")
                .setAge(35)
                .setStatus(1)
                .setDescription("原始描述3");
        
        userService.save(user);
        Long userId = user.getId();
        
        // 使用自定义 SQL 强制更新所有字段
        User updateUser = new User()
                .setId(userId)
                .setUsername("newusername3")
                .setEmail(null)
                .setPhone(null)
                .setAge(null)
                .setStatus(null)
                .setDescription(null);
        
        int result = userService.updateByIdForceAll(updateUser);
        assertEquals(1, result);
        
        // 验证结果
        User updatedUser = userService.getById(userId);
        assertEquals("newusername3", updatedUser.getUsername());
        assertNull(updatedUser.getEmail()); // 自定义 SQL，null 值被更新
        assertNull(updatedUser.getPhone()); // 自定义 SQL，null 值被更新
        assertNull(updatedUser.getAge()); // 自定义 SQL，null 值被更新
        assertNull(updatedUser.getStatus()); // 自定义 SQL，null 值被更新
        assertNull(updatedUser.getDescription()); // 自定义 SQL，null 值被更新
        
        System.out.println("自定义 SQL 强制更新测试完成：");
        System.out.println("更新后的用户：" + updatedUser);
    }
    
    /**
     * 测试更新特定字段为 null
     */
    @Test
    public void testUpdateSpecificFieldToNull() {
        // 创建用户
        User user = new User()
                .setUsername("testuser4")
                .setEmail("test4@example.com")
                .setPhone("13800138003")
                .setAge(40)
                .setStatus(1)
                .setDescription("原始描述4");
        
        userService.save(user);
        Long userId = user.getId();
        
        // 更新描述字段为 null
        int result = userService.updateDescriptionById(userId, null);
        assertEquals(1, result);
        
        // 验证结果
        User updatedUser = userService.getById(userId);
        assertEquals("testuser4", updatedUser.getUsername()); // 其他字段不变
        assertEquals("test4@example.com", updatedUser.getEmail());
        assertEquals("13800138003", updatedUser.getPhone());
        assertEquals(40, updatedUser.getAge());
        assertEquals(1, updatedUser.getStatus());
        assertNull(updatedUser.getDescription()); // 描述字段被设置为 null
        
        System.out.println("更新特定字段为 null 测试完成：");
        System.out.println("更新后的用户：" + updatedUser);
    }
    
    /**
     * 测试先查询再更新方案
     */
    @Test
    public void testUpdateWithQuery() {
        // 创建用户
        User user = new User()
                .setUsername("testuser5")
                .setEmail("test5@example.com")
                .setPhone("13800138004")
                .setAge(45)
                .setStatus(1)
                .setDescription("原始描述5");
        
        userService.save(user);
        Long userId = user.getId();
        
        // 使用先查询再更新的方案
        User updateUser = new User()
                .setUsername("newusername5")
                .setEmail(null)
                .setPhone("13900139004")
                .setAge(null)
                .setStatus(null)
                .setDescription(null);
        
        boolean result = userService.updateByIdWithQuery(userId, updateUser);
        assertTrue(result);
        
        // 验证结果
        User updatedUser = userService.getById(userId);
        assertEquals("newusername5", updatedUser.getUsername());
        assertNull(updatedUser.getEmail()); // 先查询再更新，null 值被更新
        assertEquals("13900139004", updatedUser.getPhone());
        assertNull(updatedUser.getAge()); // 先查询再更新，null 值被更新
        assertNull(updatedUser.getStatus());
        assertNull(updatedUser.getDescription()); // 先查询再更新，null 值被更新
        
        System.out.println("先查询再更新测试完成：");
        System.out.println("更新后的用户：" + updatedUser);
    }
}