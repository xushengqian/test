package com.example.fieldstrategy.demo;

import com.example.fieldstrategy.model.User;
import com.example.fieldstrategy.model.Product;
import com.example.fieldstrategy.processor.FieldProcessor;
import com.example.fieldstrategy.serializer.JsonSerializer;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * FieldStrategy.IGNORED 及其他策略的演示类
 * 
 * @author Example
 * @since 1.0.0
 */
@Slf4j
public class FieldStrategyDemo {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   FieldStrategy 字段策略演示程序");
        System.out.println("========================================\n");
        
        // 演示1: User模型中的 IGNORED 策略
        demonstrateUserFieldStrategy();
        
        System.out.println("\n========================================\n");
        
        // 演示2: Product模型中的字段策略
        demonstrateProductFieldStrategy();
        
        System.out.println("\n========================================\n");
        
        // 演示3: 分组功能
        demonstrateGroupFeature();
        
        System.out.println("\n========================================\n");
        
        // 演示4: 字段信息查询
        demonstrateFieldInfo();
    }
    
    /**
     * 演示User模型中的IGNORED策略和其他策略
     */
    private static void demonstrateUserFieldStrategy() {
        System.out.println("【演示1: User模型字段策略】\n");
        
        // 创建用户对象
        User user = new User();
        user.setId(1001L);
        user.setUsername("john_doe");
        user.setPassword("secretPassword123");  // WRITE_ONLY - 不会被序列化
        user.setEmail("john@example.com");
        user.setPhoneNumber("1234567890");
        user.setNickname("John");
        user.setInternalNote("内部备注：VIP客户");  // IGNORED - 完全忽略
        user.setAccessToken("token_abc123xyz");    // IGNORED - 完全忽略
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(Arrays.asList("USER", "ADMIN"));
        user.setActive(true);
        user.setPoints(1500);
        user.setVerificationCode("123456");        // IGNORED - 完全忽略
        
        // 序列化
        String json = JsonSerializer.serialize(user);
        System.out.println("序列化结果（注意IGNORED字段不会出现）:");
        System.out.println(json);
        
        // 重点说明
        System.out.println("\n重点观察:");
        System.out.println("✗ password (WRITE_ONLY) - 不在序列化结果中");
        System.out.println("✗ internalNote (IGNORED) - 完全被忽略");
        System.out.println("✗ accessToken (IGNORED) - 完全被忽略");
        System.out.println("✗ verificationCode (IGNORED) - 完全被忽略");
        System.out.println("✓ id, createdAt, updatedAt (READ_ONLY) - 出现在序列化中");
        System.out.println("✓ 其他字段 (DEFAULT/NOT_NULL/NOT_EMPTY) - 正常序列化");
        
        // 反序列化测试
        System.out.println("\n反序列化测试:");
        String inputJson = "{\n" +
                "  \"username\": \"jane_doe\",\n" +
                "  \"password\": \"newPassword456\",\n" +
                "  \"email_address\": \"jane@example.com\",\n" +
                "  \"id\": 9999,\n" +  // READ_ONLY - 会被忽略
                "  \"internalNote\": \"尝试设置内部备注\",\n" +  // IGNORED - 会被忽略
                "  \"accessToken\": \"尝试设置token\"\n" +  // IGNORED - 会被忽略
                "}";
        
        User newUser = JsonSerializer.deserialize(inputJson, User.class);
        System.out.println("反序列化后的对象:");
        System.out.println("- username: " + newUser.getUsername());
        System.out.println("- password: " + newUser.getPassword() + " (WRITE_ONLY可以接收)");
        System.out.println("- email: " + newUser.getEmail());
        System.out.println("- id: " + newUser.getId() + " (READ_ONLY不能设置，保持null)");
        System.out.println("- internalNote: " + newUser.getInternalNote() + " (IGNORED完全忽略)");
        System.out.println("- accessToken: " + newUser.getAccessToken() + " (IGNORED完全忽略)");
    }
    
    /**
     * 演示Product模型中的字段策略
     */
    private static void demonstrateProductFieldStrategy() {
        System.out.println("【演示2: Product模型字段策略】\n");
        
        Product product = new Product();
        product.setId(2001L);
        product.setName("高端笔记本电脑");
        product.setDescription("16GB内存，512GB SSD");
        product.setPrice(new BigDecimal("8999.99"));
        product.setCostPrice(new BigDecimal("5000.00"));  // IGNORED - 成本价不会暴露
        product.setStock(50);
        product.setSupplierInfo("供应商：XX科技有限公司");  // IGNORED - 供应商信息不暴露
        product.setSku("LAPTOP-001");
        product.setDiscountPrice(new BigDecimal("7999.99"));
        product.setCategory("电子产品");
        product.setOnSale(true);
        product.setCreatedAt(LocalDateTime.now());
        product.setSalesCount(120);
        product.setInternalNotes("注意：此产品利润率较高");  // IGNORED - 内部备注不暴露
        
        String json = JsonSerializer.serialize(product);
        System.out.println("产品序列化结果:");
        System.out.println(json);
        
        System.out.println("\n敏感信息保护:");
        System.out.println("✗ costPrice (成本价) - IGNORED策略，不会暴露");
        System.out.println("✗ supplierInfo (供应商信息) - IGNORED策略，不会暴露");
        System.out.println("✗ internalNotes (内部备注) - IGNORED策略，不会暴露");
        System.out.println("✓ price (销售价) - 正常显示");
        System.out.println("✓ discountPrice (折扣价) - NOT_NULL策略，有值时显示");
    }
    
    /**
     * 演示分组功能
     */
    private static void demonstrateGroupFeature() {
        System.out.println("【演示3: 分组功能】\n");
        
        User user = new User();
        user.setId(3001L);
        user.setUsername("test_user");
        user.setPassword("password");
        user.setEmail("test@example.com");
        user.setPhoneNumber("9876543210");
        user.setNickname("Tester");
        user.setActive(true);
        user.setPoints(500);
        user.setRoles(Arrays.asList("USER"));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        // 列表分组 - 只显示基本信息
        System.out.println("列表视图 (list分组):");
        String listJson = JsonSerializer.serialize(user, "list");
        System.out.println(listJson);
        
        System.out.println("\n详情视图 (detail分组):");
        String detailJson = JsonSerializer.serialize(user, "detail");
        System.out.println(detailJson);
        
        System.out.println("\n创建视图 (create分组):");
        String createJson = JsonSerializer.serialize(user, "create");
        System.out.println(createJson);
    }
    
    /**
     * 演示字段信息查询功能
     */
    private static void demonstrateFieldInfo() {
        System.out.println("【演示4: 字段信息查询】\n");
        
        User user = new User();
        user.setUsername("info_test");
        user.setInternalNote("这是被忽略的字段");
        user.setAccessToken("secret_token");
        
        System.out.println("User类的字段策略信息:");
        System.out.println("----------------------------------------");
        
        FieldProcessor.getFieldsInfo(user).forEach(info -> {
            System.out.printf("字段名: %-20s 策略: %-15s 别名: %-15s 加密: %-5s\n",
                    info.getFieldName(),
                    info.getStrategy().getName(),
                    info.getAlias() != null ? info.getAlias() : "-",
                    info.isEncrypted() ? "是" : "否"
            );
            
            if (info.getDescription() != null && !info.getDescription().isEmpty()) {
                System.out.printf("      描述: %s\n", info.getDescription());
            }
            
            // 特别标注IGNORED字段
            if (info.getStrategy() == com.example.fieldstrategy.enums.FieldStrategy.IGNORED) {
                System.out.println("      ⚠️ 此字段被完全忽略，不参与序列化/反序列化");
            }
            System.out.println();
        });
    }
}