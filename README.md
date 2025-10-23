# FieldStrategy 字段策略框架

一个强大的Java字段处理策略框架，用于控制对象序列化和反序列化过程中的字段行为。重点实现了 **FieldStrategy.IGNORED** 策略，确保敏感信息和内部字段不会被意外暴露。

## 🎯 核心特性

- **完整的字段策略支持**：包括 IGNORED、READ_ONLY、WRITE_ONLY 等多种策略
- **敏感信息保护**：通过 IGNORED 策略完全隐藏内部字段
- **灵活的分组控制**：支持按分组控制字段的序列化行为
- **注解驱动**：使用简单的注解配置字段策略
- **类型安全**：编译时类型检查，运行时自动类型转换

## 📦 项目结构

```
field-strategy-demo/
├── src/main/java/com/example/fieldstrategy/
│   ├── enums/
│   │   └── FieldStrategy.java          # 字段策略枚举
│   ├── annotation/
│   │   └── FieldConfig.java           # 字段配置注解
│   ├── processor/
│   │   └── FieldProcessor.java        # 字段处理器核心逻辑
│   ├── serializer/
│   │   └── JsonSerializer.java        # JSON序列化器
│   ├── model/
│   │   ├── User.java                  # 用户模型示例
│   │   └── Product.java              # 商品模型示例
│   └── demo/
│       └── FieldStrategyDemo.java    # 演示程序
└── src/test/java/
    └── FieldStrategyTest.java        # 单元测试
```

## 🚀 快速开始

### 1. 添加Maven依赖

项目使用Maven构建，主要依赖：
- Jackson (JSON处理)
- Lombok (减少样板代码)
- JUnit 5 (单元测试)
- SLF4J (日志)

### 2. 运行演示程序

```bash
# 编译项目
mvn clean compile

# 运行演示程序
mvn exec:java -Dexec.mainClass="com.example.fieldstrategy.demo.FieldStrategyDemo"

# 运行测试
mvn test
```

## 📝 字段策略说明

### FieldStrategy.IGNORED (重点)

**完全忽略字段**，不参与序列化和反序列化过程。

```java
@FieldConfig(strategy = FieldStrategy.IGNORED)
private String internalNote;  // 内部备注，永远不会暴露

@FieldConfig(strategy = FieldStrategy.IGNORED)
private String accessToken;   // 访问令牌，永远不会暴露
```

**使用场景：**
- 🔒 敏感信息（密码哈希、API密钥、令牌）
- 📝 内部备注和标记
- 🔧 临时计算字段
- 💾 缓存数据
- 🏢 仅供内部使用的业务字段

### 其他策略

| 策略 | 序列化 | 反序列化 | 使用场景 |
|------|--------|----------|----------|
| **DEFAULT** | ✅ | ✅ | 普通字段，正常处理 |
| **IGNORED** | ❌ | ❌ | 敏感信息，完全忽略 |
| **READ_ONLY** | ✅ | ❌ | 只读字段（如ID、创建时间） |
| **WRITE_ONLY** | ❌ | ✅ | 只写字段（如密码输入） |
| **NOT_NULL** | 条件 | ✅ | 非空时才序列化 |
| **NOT_EMPTY** | 条件 | ✅ | 非空非空白时才序列化 |

## 💡 使用示例

### 基本使用

```java
// 定义模型
public class User {
    @FieldConfig(strategy = FieldStrategy.DEFAULT)
    private String username;
    
    @FieldConfig(strategy = FieldStrategy.IGNORED)
    private String internalNote;  // 不会被序列化或反序列化
    
    @FieldConfig(strategy = FieldStrategy.WRITE_ONLY)
    private String password;      // 只能写入，不会返回
    
    @FieldConfig(strategy = FieldStrategy.READ_ONLY)
    private Long id;              // 只读，不能通过反序列化设置
}

// 序列化
User user = new User();
user.setUsername("john");
user.setInternalNote("VIP客户");  // 这个值不会出现在JSON中
user.setPassword("secret");       // 这个值不会出现在JSON中

String json = JsonSerializer.serialize(user);
// 输出: {"username": "john", "id": null}
// 注意：internalNote 和 password 都没有出现

// 反序列化
String inputJson = "{\"username\": \"jane\", \"internalNote\": \"尝试设置\"}";
User newUser = JsonSerializer.deserialize(inputJson, User.class);
// newUser.getInternalNote() 仍然是 null (IGNORED策略)
```

### 分组控制

```java
@FieldConfig(
    strategy = FieldStrategy.DEFAULT,
    groups = {"detail", "admin"}  // 只在特定分组中显示
)
private Integer points;

// 使用分组
String detailJson = JsonSerializer.serialize(user, "detail");  // 包含points
String listJson = JsonSerializer.serialize(user, "list");      // 不包含points
```

## 🔐 安全最佳实践

1. **敏感信息必须使用 IGNORED**
   - 密码哈希、API密钥、访问令牌
   - 内部系统标识、调试信息

2. **成本和利润信息保护**
   ```java
   @FieldConfig(strategy = FieldStrategy.IGNORED)
   private BigDecimal costPrice;  // 成本价不对外暴露
   ```

3. **用户隐私保护**
   - 使用分组控制不同场景下的信息暴露
   - 敏感操作日志使用 IGNORED

4. **防止信息泄露**
   - 定期审查所有模型类的字段策略
   - 单元测试验证 IGNORED 字段不被序列化

## 🧪 测试

项目包含完整的单元测试，重点测试：
- IGNORED 策略的完全忽略行为
- 各种策略的序列化/反序列化行为
- 分组功能
- 边界条件处理

运行测试：
```bash
mvn test
```

## 📊 性能考虑

- 使用反射缓存提高性能
- 字段处理器支持批量操作
- 轻量级注解处理，最小运行时开销

## 🤝 贡献指南

欢迎提交Issue和Pull Request来改进这个项目！

## 📄 许可证

MIT License

## 🎉 总结

FieldStrategy框架提供了强大而灵活的字段处理能力，特别是 **FieldStrategy.IGNORED** 策略，为保护敏感信息和内部数据提供了简单有效的解决方案。通过注解驱动的方式，开发者可以轻松控制每个字段的序列化行为，确保数据安全和API的清晰性。