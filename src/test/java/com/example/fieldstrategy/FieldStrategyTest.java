package com.example.fieldstrategy;

import com.example.fieldstrategy.enums.FieldStrategy;
import com.example.fieldstrategy.model.User;
import com.example.fieldstrategy.model.Product;
import com.example.fieldstrategy.processor.FieldProcessor;
import com.example.fieldstrategy.serializer.JsonSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FieldStrategy 单元测试
 * 重点测试 IGNORED 策略的行为
 */
public class FieldStrategyTest {
    
    @Test
    @DisplayName("测试 IGNORED 策略 - 字段完全不参与序列化")
    public void testIgnoredStrategyInSerialization() {
        User user = createTestUser();
        
        // 序列化
        Map<String, Object> serialized = JsonSerializer.toMap(user);
        
        // 验证 IGNORED 字段不存在
        assertFalse(serialized.containsKey("internalNote"), 
            "internalNote字段应该被IGNORED策略忽略");
        assertFalse(serialized.containsKey("accessToken"), 
            "accessToken字段应该被IGNORED策略忽略");
        assertFalse(serialized.containsKey("verificationCode"), 
            "verificationCode字段应该被IGNORED策略忽略");
        
        // 验证其他字段存在
        assertTrue(serialized.containsKey("username"), 
            "username字段应该存在");
        assertTrue(serialized.containsKey("email_address"), 
            "email字段应该使用别名email_address");
    }
    
    @Test
    @DisplayName("测试 IGNORED 策略 - 字段完全不参与反序列化")
    public void testIgnoredStrategyInDeserialization() {
        String json = "{\n" +
                "  \"username\": \"test_user\",\n" +
                "  \"internalNote\": \"尝试设置内部备注\",\n" +
                "  \"accessToken\": \"尝试设置token\",\n" +
                "  \"verificationCode\": \"999999\"\n" +
                "}";
        
        User user = JsonSerializer.deserialize(json, User.class);
        
        // 验证 IGNORED 字段没有被设置
        assertNull(user.getInternalNote(), 
            "internalNote不应该被反序列化设置");
        assertNull(user.getAccessToken(), 
            "accessToken不应该被反序列化设置");
        assertNull(user.getVerificationCode(), 
            "verificationCode不应该被反序列化设置");
        
        // 验证普通字段被正确设置
        assertEquals("test_user", user.getUsername(), 
            "username应该被正确设置");
    }
    
    @Test
    @DisplayName("测试 WRITE_ONLY 策略 - 密码字段不序列化")
    public void testWriteOnlyStrategy() {
        User user = createTestUser();
        user.setPassword("secret123");
        
        Map<String, Object> serialized = JsonSerializer.toMap(user);
        
        // 密码不应该出现在序列化结果中
        assertFalse(serialized.containsKey("password"), 
            "password字段不应该被序列化（WRITE_ONLY）");
        
        // 但可以反序列化
        String json = "{\"password\": \"newPassword\"}";
        User newUser = JsonSerializer.deserialize(json, User.class);
        assertEquals("newPassword", newUser.getPassword(), 
            "password应该可以被反序列化");
    }
    
    @Test
    @DisplayName("测试 READ_ONLY 策略 - ID字段只读")
    public void testReadOnlyStrategy() {
        User user = createTestUser();
        user.setId(1000L);
        
        // 可以序列化
        Map<String, Object> serialized = JsonSerializer.toMap(user);
        assertTrue(serialized.containsKey("id"), 
            "id字段应该被序列化（READ_ONLY）");
        assertEquals(1000L, serialized.get("id"));
        
        // 但不能反序列化设置
        String json = "{\"id\": 9999}";
        User newUser = JsonSerializer.deserialize(json, User.class);
        assertNull(newUser.getId(), 
            "id不应该被反序列化设置（READ_ONLY）");
    }
    
    @Test
    @DisplayName("测试 NOT_NULL 策略")
    public void testNotNullStrategy() {
        User user = new User();
        user.setUsername("test");
        user.setPhoneNumber(null);  // null值
        
        Map<String, Object> serialized = JsonSerializer.toMap(user);
        
        // phoneNumber为null时不应该被序列化
        assertFalse(serialized.containsKey("phoneNumber"), 
            "null的phoneNumber不应该被序列化（NOT_NULL）");
        
        // 设置值后应该被序列化
        user.setPhoneNumber("1234567890");
        serialized = JsonSerializer.toMap(user);
        assertTrue(serialized.containsKey("phoneNumber"), 
            "非null的phoneNumber应该被序列化");
    }
    
    @Test
    @DisplayName("测试 NOT_EMPTY 策略")
    public void testNotEmptyStrategy() {
        User user = new User();
        user.setUsername("test");
        
        // 测试各种空值情况
        user.setNickname(null);
        Map<String, Object> serialized = JsonSerializer.toMap(user);
        assertFalse(serialized.containsKey("nickname"), 
            "null的nickname不应该被序列化");
        
        user.setNickname("");
        serialized = JsonSerializer.toMap(user);
        assertFalse(serialized.containsKey("nickname"), 
            "空字符串的nickname不应该被序列化");
        
        user.setNickname("   ");
        serialized = JsonSerializer.toMap(user);
        assertFalse(serialized.containsKey("nickname"), 
            "只有空格的nickname不应该被序列化");
        
        user.setNickname("John");
        serialized = JsonSerializer.toMap(user);
        assertTrue(serialized.containsKey("nickname"), 
            "有值的nickname应该被序列化");
    }
    
    @Test
    @DisplayName("测试 Product 模型中的 IGNORED 字段")
    public void testProductIgnoredFields() {
        Product product = new Product();
        product.setName("测试产品");
        product.setPrice(new BigDecimal("100.00"));
        product.setCostPrice(new BigDecimal("50.00"));  // IGNORED
        product.setSupplierInfo("供应商信息");  // IGNORED
        product.setInternalNotes("内部备注");  // IGNORED
        
        Map<String, Object> serialized = JsonSerializer.toMap(product);
        
        // 验证敏感信息不被暴露
        assertFalse(serialized.containsKey("costPrice"), 
            "成本价不应该被序列化");
        assertFalse(serialized.containsKey("supplierInfo"), 
            "供应商信息不应该被序列化");
        assertFalse(serialized.containsKey("internalNotes"), 
            "内部备注不应该被序列化");
        
        // 验证普通字段正常
        assertTrue(serialized.containsKey("name"));
        assertTrue(serialized.containsKey("price"));
    }
    
    @Test
    @DisplayName("测试字段策略枚举的判断方法")
    public void testFieldStrategyMethods() {
        // 测试 shouldSerialize
        assertFalse(FieldStrategy.IGNORED.shouldSerialize("any value"), 
            "IGNORED不应该序列化任何值");
        assertFalse(FieldStrategy.WRITE_ONLY.shouldSerialize("any value"), 
            "WRITE_ONLY不应该序列化任何值");
        assertTrue(FieldStrategy.READ_ONLY.shouldSerialize("any value"), 
            "READ_ONLY应该序列化");
        assertTrue(FieldStrategy.DEFAULT.shouldSerialize("any value"), 
            "DEFAULT应该序列化");
        
        // 测试 shouldDeserialize
        assertFalse(FieldStrategy.IGNORED.shouldDeserialize(), 
            "IGNORED不应该反序列化");
        assertFalse(FieldStrategy.READ_ONLY.shouldDeserialize(), 
            "READ_ONLY不应该反序列化");
        assertTrue(FieldStrategy.WRITE_ONLY.shouldDeserialize(), 
            "WRITE_ONLY应该反序列化");
        assertTrue(FieldStrategy.DEFAULT.shouldDeserialize(), 
            "DEFAULT应该反序列化");
    }
    
    @Test
    @DisplayName("测试分组功能")
    public void testGroupFeature() {
        User user = createTestUser();
        user.setPoints(100);  // 只在detail分组中显示
        
        // detail分组
        Map<String, Object> detailView = JsonSerializer.toMap(user, "detail");
        assertTrue(detailView.containsKey("points"), 
            "points应该在detail分组中显示");
        
        // list分组
        Map<String, Object> listView = JsonSerializer.toMap(user, "list");
        assertFalse(listView.containsKey("points"), 
            "points不应该在list分组中显示");
    }
    
    // 辅助方法：创建测试用户
    private User createTestUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("test_user");
        user.setPassword("password123");
        user.setEmail("test@example.com");
        user.setPhoneNumber("1234567890");
        user.setNickname("Test");
        user.setInternalNote("内部备注");
        user.setAccessToken("token123");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(Arrays.asList("USER"));
        user.setActive(true);
        user.setPoints(100);
        user.setVerificationCode("123456");
        return user;
    }
}