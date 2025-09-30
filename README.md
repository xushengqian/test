# MyBatis-Plus baseMapper.updateById 字段为 null 未更新问题解决方案

## 问题描述

在使用 MyBatis-Plus 的 `baseMapper.updateById()` 方法时，如果实体对象的某个字段值为 `null`，该字段不会被更新到数据库中。这是 MyBatis-Plus 的默认行为。

## 原因分析

MyBatis-Plus 默认使用 `FieldStrategy.NOT_NULL` 策略，即只有非 null 的字段才会被包含在 UPDATE 语句中。这样设计是为了避免意外覆盖数据库中的有效数据。

## 解决方案

### 1. 【推荐】使用 @TableField 注解的 updateStrategy 属性

在实体类字段上使用 `@TableField(updateStrategy = FieldStrategy.IGNORED)`：

```java
@TableField(value = "email", updateStrategy = FieldStrategy.IGNORED)
private String email;
```

**优点**：简单直接，对特定字段生效  
**缺点**：需要在实体类上修改，影响所有使用该实体的更新操作  
**适用场景**：确定某个字段经常需要设置为 null 的情况

### 2. 【推荐】使用 UpdateWrapper

```java
UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
updateWrapper.eq("id", id);
updateWrapper.set("email", null); // 强制设置为 null
userService.update(updateWrapper);
```

**优点**：灵活性最高，可以精确控制每次更新的字段  
**缺点**：代码稍微复杂一些  
**适用场景**：需要动态控制更新字段的情况（推荐）

### 3. 全局配置字段策略

在 `application.yml` 中配置：

```yaml
mybatis-plus:
  global-config:
    db-config:
      update-strategy: ignored
```

**优点**：一次配置，全局生效  
**缺点**：影响范围太大，可能导致意外的数据覆盖  
**适用场景**：项目中大部分更新操作都需要支持 null 值更新

### 4. 使用自定义 SQL

在 Mapper 中编写自定义的 UPDATE SQL 语句：

```java
@Update("UPDATE user SET email = #{email}, update_time = NOW() WHERE id = #{id}")
int updateEmailById(@Param("id") Long id, @Param("email") String email);
```

**优点**：完全控制 SQL 语句，性能最优  
**缺点**：需要编写和维护额外的 SQL 代码  
**适用场景**：复杂的更新逻辑或性能要求很高的场景

### 5. 先查询再更新

```java
User existingUser = userService.getById(id);
existingUser.setEmail(null); // 设置需要更新的字段
userService.updateById(existingUser);
```

**优点**：逻辑清晰，易于理解  
**缺点**：需要额外的数据库查询，性能较差，存在并发问题  
**适用场景**：更新频率不高，对性能要求不严格的场景

## 项目结构

```
src/
├── main/
│   ├── java/com/example/demo/
│   │   ├── entity/User.java              # 用户实体类（演示各种字段策略）
│   │   ├── mapper/UserMapper.java        # 用户 Mapper 接口（自定义 SQL）
│   │   ├── service/UserService.java      # 用户服务类（各种解决方案）
│   │   ├── controller/UserController.java # 用户控制器（API 接口）
│   │   ├── config/MybatisPlusConfig.java  # MyBatis-Plus 配置
│   │   ├── NullUpdateSolutions.java      # 解决方案详细说明
│   │   └── DemoApplication.java          # 主应用类
│   └── resources/
│       ├── application.yml               # 主配置文件
│       └── schema.sql                    # 数据库表结构
└── test/
    ├── java/com/example/demo/
    │   └── UserServiceTest.java          # 测试类（演示各种方案）
    └── resources/
        ├── application-test.yml          # 测试配置
        └── schema.sql                    # 测试数据库表结构
```

## 运行项目

### 1. 环境要求

- JDK 8+
- Maven 3.6+
- MySQL 8.0+（生产环境）
- H2 Database（测试环境，自动配置）

### 2. 配置数据库

修改 `src/main/resources/application.yml` 中的数据库连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/demo
    username: your_username
    password: your_password
```

### 3. 运行应用

```bash
# 编译项目
mvn clean compile

# 运行测试
mvn test

# 启动应用
mvn spring-boot:run
```

### 4. 测试 API

应用启动后，可以通过以下 API 测试各种更新方案：

- `POST /api/users` - 创建用户
- `GET /api/users/{id}` - 获取用户
- `PUT /api/users/{id}/standard` - 标准更新（null 值不更新）
- `PUT /api/users/{id}/wrapper` - 使用 UpdateWrapper 更新
- `PUT /api/users/{id}/force-all` - 强制更新所有字段
- `PUT /api/users/{id}/description?description=` - 更新描述为 null
- `PUT /api/users/{id}/query-update` - 先查询再更新

## 推荐使用顺序

1. **UpdateWrapper（方案2）** - 最灵活，适用于大多数场景
2. **@TableField 注解（方案1）** - 适用于特定字段总是需要支持 null 更新
3. **自定义 SQL（方案4）** - 适用于复杂更新逻辑
4. **全局配置（方案3）** - 谨慎使用，适用于项目整体需求
5. **先查询再更新（方案5）** - 不推荐，除非特殊情况

## 注意事项

1. 使用 `FieldStrategy.IGNORED` 时要谨慎，确保不会意外覆盖重要数据
2. 全局配置 `update-strategy: ignored` 影响范围很大，建议在新项目开始时就确定策略
3. 使用 UpdateWrapper 时，记得处理并发更新的问题
4. 自定义 SQL 要注意 SQL 注入安全问题
5. 在生产环境中，建议对关键字段的 null 值更新进行额外的业务逻辑验证

## 总结

对于 MyBatis-Plus 中 `baseMapper.updateById` 字段为 null 未更新的问题，推荐优先使用 **UpdateWrapper** 方案，它提供了最好的灵活性和控制力。对于经常需要设置为 null 的特定字段，可以在实体类上使用 `@TableField(updateStrategy = FieldStrategy.IGNORED)` 注解。