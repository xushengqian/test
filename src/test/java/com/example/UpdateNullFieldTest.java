package com.example;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.entity.User;
import com.example.mapper.UserMapper;
import com.example.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MyBatis-Plus updateById null字段更新测试
 * 
 * 问题描述：
 * MyBatis-Plus 的 updateById 方法默认不会更新值为 null 的字段。
 * 这是因为 MyBatis-Plus 的默认更新策略是 NOT_NULL。
 * 
 * 本测试类演示了多种解决方案。
 */
@SpringBootTest
public class UpdateNullFieldTest {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private UserMapper userMapper;
    
    private Long testUserId;
    
    @BeforeEach
    public void setUp() {
        // 创建测试用户
        User testUser = new User()
                .setUsername("测试用户")
                .setPassword("password")
                .setEmail("test@example.com")
                .setPhone("13800138888")
                .setAddress("测试地址")
                .setAge(20)
                .setStatus(1);
        
        userService.save(testUser);
        testUserId = testUser.getId();
        System.out.println("创建测试用户，ID: " + testUserId);
    }
    
    /**
     * 测试1：默认的 updateById 不会更新 null 字段
     */
    @Test
    public void testDefaultUpdateById() {
        System.out.println("\n===== 测试1：默认updateById（不更新null字段）=====");
        
        // 尝试将email和phone设置为null
        User updateUser = new User();
        updateUser.setId(testUserId);
        updateUser.setEmail(null);  // 设置为null
        updateUser.setPhone(null);  // 设置为null
        updateUser.setAddress("新地址");  // 正常更新
        
        // 执行更新
        boolean success = userService.updateById(updateUser);
        assertTrue(success);
        
        // 查询更新后的结果
        User result = userService.getById(testUserId);
        
        // 验证：null字段没有被更新
        System.out.println("更新后的email: " + result.getEmail());  // 仍然是原值
        System.out.println("更新后的phone: " + result.getPhone());  // 仍然是原值
        System.out.println("更新后的address: " + result.getAddress());  // 已更新
        
        assertNotNull(result.getEmail(), "email不应该被更新为null");
        assertNotNull(result.getPhone(), "phone不应该被更新为null");
        assertEquals("新地址", result.getAddress(), "address应该被更新");
    }
    
    /**
     * 测试2：使用 UpdateWrapper 可以更新 null 字段
     */
    @Test
    public void testUpdateWrapperWithNull() {
        System.out.println("\n===== 测试2：使用UpdateWrapper更新null字段 =====");
        
        // 使用UpdateWrapper显式设置null
        LambdaUpdateWrapper<User> wrapper = Wrappers.<User>lambdaUpdate()
                .eq(User::getId, testUserId)
                .set(User::getEmail, null)  // 显式设置为null
                .set(User::getPhone, null)  // 显式设置为null
                .set(User::getAddress, "UpdateWrapper更新");
        
        boolean success = userService.update(wrapper);
        assertTrue(success);
        
        // 查询更新后的结果
        User result = userService.getById(testUserId);
        
        // 验证：字段已被更新为null
        System.out.println("更新后的email: " + result.getEmail());  // null
        System.out.println("更新后的phone: " + result.getPhone());  // null
        System.out.println("更新后的address: " + result.getAddress());
        
        assertNull(result.getEmail(), "email应该被更新为null");
        assertNull(result.getPhone(), "phone应该被更新为null");
        assertEquals("UpdateWrapper更新", result.getAddress());
    }
    
    /**
     * 测试3：使用 @TableField 注解的 updateStrategy 属性
     */
    @Test
    public void testTableFieldStrategy() {
        System.out.println("\n===== 测试3：@TableField updateStrategy 策略 =====");
        
        User updateUser = new User();
        updateUser.setId(testUserId);
        updateUser.setEmail(null);  // 默认策略，不会更新
        updateUser.setPhone(null);  // 设置了IGNORED策略，会更新为null
        
        boolean success = userService.updateById(updateUser);
        assertTrue(success);
        
        User result = userService.getById(testUserId);
        
        System.out.println("email字段（默认策略）: " + result.getEmail());  // 不为null
        System.out.println("phone字段（IGNORED策略）: " + result.getPhone());  // null
        
        assertNotNull(result.getEmail(), "email使用默认策略，不应该被更新为null");
        assertNull(result.getPhone(), "phone使用IGNORED策略，应该被更新为null");
    }
    
    /**
     * 测试4：使用自定义SQL更新null字段
     */
    @Test
    public void testCustomSqlUpdate() {
        System.out.println("\n===== 测试4：使用自定义SQL更新null字段 =====");
        
        User updateUser = new User();
        updateUser.setId(testUserId);
        updateUser.setEmail(null);
        updateUser.setPhone(null);
        updateUser.setAddress("自定义SQL更新");
        
        int rows = userMapper.updateByIdWithNull(updateUser);
        assertEquals(1, rows);
        
        User result = userService.getById(testUserId);
        
        System.out.println("更新后的email: " + result.getEmail());  // null
        System.out.println("更新后的phone: " + result.getPhone());  // null
        System.out.println("更新后的address: " + result.getAddress());
        
        assertNull(result.getEmail(), "email应该被更新为null");
        assertNull(result.getPhone(), "phone应该被更新为null");
        assertEquals("自定义SQL更新", result.getAddress());
    }
    
    /**
     * 测试5：使用Service层的封装方法
     */
    @Test
    public void testServiceWrapperMethod() {
        System.out.println("\n===== 测试5：使用Service封装的方法 =====");
        
        User updateUser = new User();
        updateUser.setEmail(null);
        updateUser.setPhone(null);
        updateUser.setAddress("Service方法更新");
        
        boolean success = userService.updateByLambdaWrapper(testUserId, updateUser);
        assertTrue(success);
        
        User result = userService.getById(testUserId);
        
        assertNull(result.getEmail(), "email应该被更新为null");
        assertNull(result.getPhone(), "phone应该被更新为null");
        assertEquals("Service方法更新", result.getAddress());
    }
    
    /**
     * 测试6：清空特定字段
     */
    @Test
    public void testClearSpecificField() {
        System.out.println("\n===== 测试6：清空特定字段 =====");
        
        // 清空email字段
        boolean success = userService.clearField(testUserId, "email");
        assertTrue(success);
        
        User result = userService.getById(testUserId);
        assertNull(result.getEmail(), "email字段应该被清空");
        assertNotNull(result.getPhone(), "phone字段不应该被影响");
        
        // 清空phone字段
        success = userService.clearField(testUserId, "phone");
        assertTrue(success);
        
        result = userService.getById(testUserId);
        assertNull(result.getPhone(), "phone字段应该被清空");
    }
    
    /**
     * 打印解决方案总结
     */
    @Test
    public void printSolutions() {
        System.out.println("\n========== MyBatis-Plus updateById null字段更新解决方案总结 ==========\n");
        
        System.out.println("问题原因：");
        System.out.println("- MyBatis-Plus 默认更新策略是 NOT_NULL");
        System.out.println("- updateById 方法不会更新值为 null 的字段\n");
        
        System.out.println("解决方案：");
        System.out.println("1. 【实体字段级别】使用 @TableField(updateStrategy = FieldStrategy.IGNORED)");
        System.out.println("   - 优点：简单直接，针对特定字段");
        System.out.println("   - 缺点：影响所有使用该实体的更新操作\n");
        
        System.out.println("2. 【全局配置】在 application.yml 中配置全局更新策略");
        System.out.println("   mybatis-plus.global-config.db-config.update-strategy=ignored");
        System.out.println("   - 优点：全局生效");
        System.out.println("   - 缺点：可能导致意外的null更新\n");
        
        System.out.println("3. 【UpdateWrapper】使用 UpdateWrapper 或 LambdaUpdateWrapper");
        System.out.println("   - 优点：灵活控制每个字段，类型安全");
        System.out.println("   - 缺点：代码稍微复杂\n");
        
        System.out.println("4. 【自定义SQL】编写自定义的Mapper方法");
        System.out.println("   - 优点：完全控制SQL");
        System.out.println("   - 缺点：需要手写SQL\n");
        
        System.out.println("5. 【先查后更】先查询再更新");
        System.out.println("   - 优点：逻辑清晰");
        System.out.println("   - 缺点：性能较差，需要两次数据库操作\n");
        
        System.out.println("推荐方案：");
        System.out.println("- 偶尔需要更新null：使用 UpdateWrapper");
        System.out.println("- 特定字段总需要更新null：使用 @TableField 注解");
        System.out.println("- 复杂业务逻辑：自定义SQL");
        System.out.println("\n=====================================================");
    }
}