# FieldStrategy.IGNORED 完全指南

## 🎯 什么是 FieldStrategy.IGNORED？

`FieldStrategy.IGNORED` 是一个字段处理策略，用于**完全忽略**某个字段，使其不参与任何序列化和反序列化过程。这是保护敏感信息和内部数据的最强策略。

## 🔒 核心特性

### 完全隔离
- **序列化时**：字段值永远不会出现在输出中
- **反序列化时**：输入数据中的对应字段会被完全忽略
- **不可绕过**：即使客户端尝试提交该字段的值，也会被忽略

## 📋 典型使用场景

### 1. 敏感信息保护
```java
public class User {
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private String passwordHash;        // 密码哈希值
    
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private String apiKey;              // API密钥
    
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private String refreshToken;        // 刷新令牌
}
```

### 2. 内部业务数据
```java
public class Product {
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private BigDecimal costPrice;       // 成本价（商业机密）
    
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private String supplierInfo;        // 供应商信息
    
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private Double profitMargin;        // 利润率
}
```

### 3. 系统内部字段
```java
public class Order {
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private String internalOrderCode;   // 内部订单号
    
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private String debugInfo;           // 调试信息
    
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private Long processingNodeId;      // 处理节点ID
}
```

### 4. 临时/缓存字段
```java
public class Report {
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private byte[] cachedPdf;           // 缓存的PDF数据
    
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private String tempCalculation;     // 临时计算结果
    
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private Map<String, Object> runtimeCache;  // 运行时缓存
}
```

## ⚖️ 与其他策略的对比

| 策略 | 序列化 | 反序列化 | 使用场景 | 安全级别 |
|------|--------|----------|----------|----------|
| **IGNORED** | ❌ 完全不输出 | ❌ 完全不接收 | 敏感信息、内部数据 | 🔴 最高 |
| **WRITE_ONLY** | ❌ 不输出 | ✅ 可接收 | 密码输入 | 🟡 高 |
| **READ_ONLY** | ✅ 可输出 | ❌ 不接收 | ID、创建时间 | 🟢 中 |
| **DEFAULT** | ✅ 可输出 | ✅ 可接收 | 普通字段 | ⚪ 无 |

## 🛠️ 实现原理

```java
public enum FieldStrategy {
    IGNORED("忽略策略", "字段在序列化和反序列化过程中被完全忽略"),
    // ... 其他策略
    
    public boolean shouldSerialize(Object value) {
        switch (this) {
            case IGNORED:
                return false;  // 永远返回false，不序列化
            // ...
        }
    }
    
    public boolean shouldDeserialize() {
        switch (this) {
            case IGNORED:
                return false;  // 永远返回false，不反序列化
            // ...
        }
    }
}
```

## 💻 完整示例

### 定义模型
```java
@Data
public class BankAccount {
    // 公开信息
    @FieldConfig(strategy = FieldStrategy.DEFAULT)
    private String accountNumber;
    
    @FieldConfig(strategy = FieldStrategy.DEFAULT)
    private String accountHolder;
    
    // 敏感信息 - 使用IGNORED策略
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private String pin;                 // PIN码
    
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private String securityAnswer;      // 安全问题答案
    
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private BigDecimal actualBalance;   // 实际余额（内部使用）
    
    // 只读信息
    @FieldConfig(strategy = FieldStrategy.READ_ONLY)
    private BigDecimal displayBalance;  // 显示余额（对外展示）
}
```

### 序列化示例
```java
BankAccount account = new BankAccount();
account.setAccountNumber("1234567890");
account.setAccountHolder("John Doe");
account.setPin("1234");                    // IGNORED - 不会出现在JSON中
account.setSecurityAnswer("MyPet");        // IGNORED - 不会出现在JSON中
account.setActualBalance(new BigDecimal("10000"));  // IGNORED - 不会出现在JSON中
account.setDisplayBalance(new BigDecimal("9500"));

String json = JsonSerializer.serialize(account);
System.out.println(json);
```

**输出结果：**
```json
{
  "accountNumber": "1234567890",
  "accountHolder": "John Doe",
  "displayBalance": 9500
}
```
注意：所有标记为 IGNORED 的字段都没有出现！

### 反序列化示例
```java
// 客户端尝试提交敏感信息
String maliciousJson = """
{
  "accountNumber": "9999999999",
  "pin": "5678",                    
  "securityAnswer": "HackedAnswer",  
  "actualBalance": 1000000         
}
""";

BankAccount account = JsonSerializer.deserialize(maliciousJson, BankAccount.class);

System.out.println(account.getAccountNumber());  // 输出: 9999999999 (正常字段)
System.out.println(account.getPin());            // 输出: null (IGNORED，被忽略)
System.out.println(account.getSecurityAnswer()); // 输出: null (IGNORED，被忽略)
System.out.println(account.getActualBalance());  // 输出: null (IGNORED，被忽略)
```

## 🔍 测试验证

```java
@Test
public void testIgnoredFieldsCompletely() {
    User user = new User();
    user.setUsername("testuser");
    user.setInternalNote("这是内部备注");  // IGNORED字段
    user.setAccessToken("secret-token");   // IGNORED字段
    
    // 测试序列化
    Map<String, Object> serialized = FieldProcessor.processForSerialization(user);
    
    // 断言：IGNORED字段不应该存在
    assertFalse(serialized.containsKey("internalNote"));
    assertFalse(serialized.containsKey("accessToken"));
    
    // 测试反序列化
    Map<String, Object> input = new HashMap<>();
    input.put("username", "newuser");
    input.put("internalNote", "尝试设置内部备注");  // 尝试设置IGNORED字段
    input.put("accessToken", "fake-token");        // 尝试设置IGNORED字段
    
    User newUser = new User();
    FieldProcessor.processForDeserialization(newUser, input);
    
    // 断言：IGNORED字段不应该被设置
    assertEquals("newuser", newUser.getUsername());  // 普通字段正常
    assertNull(newUser.getInternalNote());          // IGNORED字段保持null
    assertNull(newUser.getAccessToken());           // IGNORED字段保持null
}
```

## ⚠️ 注意事项

1. **不可恢复性**：一旦字段被标记为 IGNORED，它的值就永远不会通过序列化传输
2. **双向生效**：IGNORED 同时影响序列化和反序列化
3. **安全性最高**：这是最安全的策略，适用于绝对不能暴露的信息
4. **谨慎使用**：确保不会意外地将需要的字段标记为 IGNORED

## 🎯 最佳实践

### Do's ✅
- 所有密码、令牌、密钥类字段使用 IGNORED
- 内部计算字段、缓存字段使用 IGNORED
- 商业机密信息（成本、供应商）使用 IGNORED
- 定期审查所有 IGNORED 字段的必要性

### Don'ts ❌
- 不要将用户需要查看的字段设为 IGNORED
- 不要将需要更新的字段设为 IGNORED
- 不要依赖客户端来过滤 IGNORED 字段

## 📊 性能影响

IGNORED 策略实际上**提升性能**：
- 减少序列化数据量
- 减少网络传输
- 减少内存使用
- 简化处理逻辑

## 🔄 迁移指南

如果你的现有代码需要迁移到使用 IGNORED 策略：

```java
// 旧代码（手动处理）
public class OldUser {
    private String password;
    
    public Map<String, Object> toJson() {
        Map<String, Object> map = new HashMap<>();
        // 手动排除password
        map.put("username", username);
        // 不包含 password
        return map;
    }
}

// 新代码（使用IGNORED策略）
public class NewUser {
    @FieldConfig(strategy = FieldStrategy.DEFAULT)
    private String username;
    
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private String password;  // 自动处理，更安全
}
```

## 📝 总结

`FieldStrategy.IGNORED` 提供了一种简单、安全、高效的方式来保护敏感信息和内部数据。通过声明式的注解配置，开发者可以确保这些字段永远不会意外暴露，大大提高了应用的安全性。