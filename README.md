# MyBatis-Plus updateById null字段更新问题解决方案

## 问题描述

使用 MyBatis-Plus 的 `baseMapper.updateById(entity)` 方法时，如果实体对象的某些字段值为 `null`，这些字段不会被更新到数据库。

### 问题原因

MyBatis-Plus 的默认字段更新策略是 `FieldStrategy.NOT_NULL`，即只更新非 null 的字段。这是为了防止误操作将数据库中的有效数据更新为 null。

## 项目结构

```
mybatis-plus-null-update/
├── pom.xml                          # Maven配置文件
├── src/main/java/com/example/
│   ├── Application.java             # 主启动类
│   ├── entity/
│   │   └── User.java               # 用户实体类（演示不同更新策略）
│   ├── mapper/
│   │   └── UserMapper.java         # Mapper接口（包含自定义SQL）
│   ├── service/
│   │   └── UserService.java        # 服务层（多种解决方案实现）
│   ├── controller/
│   │   └── UserController.java     # 控制器（REST API）
│   ├── config/
│   │   └── MyBatisPlusConfig.java  # MyBatis-Plus配置
│   └── solutions/
│       └── UpdateNullSolutionDemo.java  # 解决方案详细说明
├── src/main/resources/
│   ├── application.yml              # 应用配置
│   └── schema.sql                   # 数据库初始化脚本
└── src/test/java/com/example/
    └── UpdateNullFieldTest.java    # 测试用例

```

## 快速开始

### 1. 运行项目

```bash
# 克隆或下载项目后，进入项目目录
cd /workspace

# 使用Maven运行项目
mvn spring-boot:run

# 或者先编译再运行
mvn clean package
java -jar target/mybatis-plus-null-update-1.0.0.jar
```

### 2. 访问H2控制台

项目启动后，访问 H2 数据库控制台：
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: (留空)

### 3. 运行测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=UpdateNullFieldTest
```

## 解决方案汇总

### 方案1：实体字段注解配置

```java
// 在实体类字段上使用 @TableField 注解
@TableField(updateStrategy = FieldStrategy.IGNORED)
private String phone;  // 该字段会更新null值
```

**适用场景**：某个字段总是需要更新null值

### 方案2：使用 UpdateWrapper

```java
// 使用 LambdaUpdateWrapper（推荐）
LambdaUpdateWrapper<User> wrapper = Wrappers.<User>lambdaUpdate()
    .eq(User::getId, userId)
    .set(User::getEmail, null)      // 明确设置为null
    .set(User::getPhone, null);     // 明确设置为null
    
userService.update(wrapper);
```

**适用场景**：偶尔需要更新null值，灵活控制

### 方案3：全局配置

```yaml
# application.yml
mybatis-plus:
  global-config:
    db-config:
      update-strategy: ignored  # 全局更新null值（慎用）
```

**适用场景**：整个项目都需要更新null值（不推荐）

### 方案4：自定义SQL

```java
@Update("UPDATE user SET email = #{email}, phone = #{phone} WHERE id = #{id}")
int updateWithNull(@Param("id") Long id, 
                  @Param("email") String email, 
                  @Param("phone") String phone);
```

**适用场景**：复杂业务逻辑，需要精确控制SQL

### 方案5：使用 allEq 方法

```java
UpdateWrapper<User> wrapper = new UpdateWrapper<>();
Map<String, Object> updateMap = new HashMap<>();
updateMap.put("email", null);
updateMap.put("phone", null);

wrapper.eq("id", userId)
       .allEq(updateMap, true);  // true表示包含null值
```

**适用场景**：批量字段更新

## API 接口测试

### 1. 创建用户

```bash
curl -X POST http://localhost:8080/user/create \
  -H "Content-Type: application/json" \
  -d '{
    "username": "测试用户",
    "email": "test@example.com",
    "phone": "13800138000"
  }'
```

### 2. 默认更新（null不更新）

```bash
curl -X PUT http://localhost:8080/user/update/default \
  -H "Content-Type: application/json" \
  -d '{
    "id": 1,
    "email": null,
    "phone": null,
    "address": "新地址"
  }'
```

### 3. 使用UpdateWrapper更新（null会更新）

```bash
curl -X PUT http://localhost:8080/user/update/wrapper/1 \
  -H "Content-Type: application/json" \
  -d '{
    "email": null,
    "phone": null,
    "address": "新地址"
  }'
```

### 4. 清空特定字段

```bash
curl -X PUT http://localhost:8080/user/clear/1/email
```

### 5. 动态更新

```bash
# forceUpdateNull=true 时更新null值
curl -X PUT "http://localhost:8080/user/update/dynamic?forceUpdateNull=true" \
  -H "Content-Type: application/json" \
  -d '{
    "id": 1,
    "email": null,
    "phone": null
  }'
```

## 最佳实践建议

### 1. 选择合适的方案

| 场景 | 推荐方案 | 原因 |
|-----|---------|------|
| 偶尔更新null | UpdateWrapper | 灵活控制，不影响其他操作 |
| 特定字段常更新null | @TableField注解 | 配置简单，针对性强 |
| 复杂业务逻辑 | 自定义SQL | 完全控制，性能最优 |
| 批量更新 | allEq方法 | 代码简洁，易于维护 |

### 2. 注意事项

1. **避免全局IGNORED策略**：可能导致意外的null更新
2. **使用LambdaWrapper**：类型安全，避免字段名拼写错误
3. **明确业务需求**：区分"不更新"和"更新为null"
4. **添加单元测试**：验证null更新行为
5. **代码注释**：说明null值处理策略

### 3. 性能优化

- 优先使用 UpdateWrapper（单次数据库操作）
- 避免先查后更（两次数据库操作）
- 批量更新考虑使用批处理

## 常见问题

### Q1: 为什么MyBatis-Plus默认不更新null字段？

**A**: 这是一种保护机制，防止误操作将数据库中的有效数据更新为null。在实际业务中，大部分情况下我们不希望将字段更新为null。

### Q2: 使用IGNORED策略有什么风险？

**A**: 如果全局或字段级别设置IGNORED策略，所有的更新操作都会包含null值，可能会意外地将数据库中的有效数据清空。

### Q3: UpdateWrapper和updateById性能有差异吗？

**A**: 性能差异很小。UpdateWrapper提供了更灵活的控制，而updateById在简单场景下代码更简洁。

### Q4: 如何只更新部分字段为null？

**A**: 使用UpdateWrapper的set方法，只对需要更新为null的字段调用set方法。

## 相关资源

- [MyBatis-Plus 官方文档](https://baomidou.com/)
- [字段策略说明](https://baomidou.com/pages/223848/)
- [UpdateWrapper 使用指南](https://baomidou.com/pages/10c804/)

## License

MIT