# Spring 事务失效场景详解

本项目演示了Spring事务在各种场景下失效的情况，帮助开发者理解和避免这些常见问题。

## 🚀 快速开始

### 环境要求
- JDK 11+
- Maven 3.6+

### 启动项目
```bash
# 克隆项目
git clone <repository-url>

# 进入项目目录
cd spring-transaction-demo

# 编译运行
mvn spring-boot:run
```

### 访问地址
- 应用地址：http://localhost:8080
- H2控制台：http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:testdb`
  - 用户名：sa
  - 密码：（空）

## 📋 Spring事务失效的常见场景

### 1. 🔒 方法不是public

**问题描述**：Spring AOP代理只能拦截public方法，非public方法上的`@Transactional`注解不会生效。

**示例代码**：
```java
// ❌ 错误：private方法，事务不生效
@Transactional
private void privateMethod() {
    // 事务不会生效
}

// ✅ 正确：public方法
@Transactional
public void publicMethod() {
    // 事务会生效
}
```

**测试接口**：
```bash
# 测试public方法（事务生效）
curl -X POST "http://localhost:8080/api/transaction/test/non-public?username=test&type=public"

# 测试private方法（事务不生效）
curl -X POST "http://localhost:8080/api/transaction/test/non-public?username=error&type=private"
```

### 2. 🔄 类内部方法调用

**问题描述**：在同一个类中，一个方法调用另一个带`@Transactional`的方法，不会经过代理，导致事务失效。

**示例代码**：
```java
@Service
public class UserService {
    
    // ❌ 错误：直接内部调用
    public void methodA() {
        methodB(); // 直接调用，不经过代理，事务不生效
    }
    
    @Transactional
    public void methodB() {
        // 事务配置被忽略
    }
}
```

**解决方案**：
1. 通过注入自身代理
2. 通过ApplicationContext获取代理
3. 使用AopContext.currentProxy()
4. 重构代码，避免内部调用

**测试接口**：
```bash
# 错误的内部调用（事务不生效）
curl -X POST "http://localhost:8080/api/transaction/test/self-invocation?username=error&type=incorrect"

# 通过代理调用（事务生效）
curl -X POST "http://localhost:8080/api/transaction/test/self-invocation?username=error&type=proxy"
```

### 3. 🚫 异常被捕获

**问题描述**：如果异常在方法内部被try-catch捕获且没有重新抛出，事务管理器感知不到异常，不会触发回滚。

**示例代码**：
```java
// ❌ 错误：异常被捕获，事务不回滚
@Transactional
public void incorrectHandling() {
    try {
        // 业务逻辑
        throw new RuntimeException("业务异常");
    } catch (Exception e) {
        // 异常被捕获，事务不回滚
        log.error("错误：", e);
    }
}

// ✅ 正确：重新抛出异常
@Transactional
public void correctHandling() {
    try {
        // 业务逻辑
        throw new RuntimeException("业务异常");
    } catch (Exception e) {
        log.error("错误：", e);
        throw e; // 重新抛出
    }
}
```

**测试接口**：
```bash
# 异常被捕获（事务不回滚）
curl -X POST "http://localhost:8080/api/transaction/test/exception-handling?username=error&type=incorrect"

# 异常重新抛出（事务回滚）
curl -X POST "http://localhost:8080/api/transaction/test/exception-handling?username=error&type=rethrow"
```

### 4. ⚠️ 抛出受检异常

**问题描述**：Spring默认只对RuntimeException和Error进行回滚，对受检异常（checked exception）不会触发回滚。

**示例代码**：
```java
// ❌ 错误：受检异常不触发回滚
@Transactional
public void throwCheckedException() throws Exception {
    // 保存数据
    userRepository.save(user);
    // 抛出受检异常，事务不回滚
    throw new Exception("受检异常");
}

// ✅ 正确：配置rollbackFor
@Transactional(rollbackFor = Exception.class)
public void throwCheckedExceptionWithRollback() throws Exception {
    // 保存数据
    userRepository.save(user);
    // 配置了rollbackFor，事务会回滚
    throw new Exception("受检异常");
}
```

**测试接口**：
```bash
# 受检异常（事务不回滚）
curl -X POST "http://localhost:8080/api/transaction/test/exception-type?username=error&type=checked"

# 配置rollbackFor（事务回滚）
curl -X POST "http://localhost:8080/api/transaction/test/exception-type?username=error&type=checked-rollback"
```

### 5. 📡 事务传播行为配置错误

**问题描述**：错误的事务传播行为配置可能导致事务不按预期工作。

**7种传播行为**：
- **REQUIRED**（默认）：加入当前事务，没有则创建新事务
- **SUPPORTS**：有事务则加入，没有则非事务执行
- **MANDATORY**：必须在事务中执行，否则抛异常
- **REQUIRES_NEW**：创建新事务，挂起当前事务
- **NOT_SUPPORTED**：非事务执行，挂起当前事务
- **NEVER**：非事务执行，有事务则抛异常
- **NESTED**：嵌套事务

**示例代码**：
```java
// ❌ 错误：SUPPORTS在没有事务时不会创建事务
@Transactional(propagation = Propagation.SUPPORTS)
public void supportMethod() {
    // 单独调用时没有事务
}

// ❌ 错误：NOT_SUPPORTED总是非事务执行
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void notSupportedMethod() {
    // 总是非事务执行，即使有异常也不回滚
}
```

**测试接口**：
```bash
# SUPPORTS（单独调用无事务）
curl -X POST "http://localhost:8080/api/transaction/test/propagation?username=error&type=supports"

# NOT_SUPPORTED（总是无事务）
curl -X POST "http://localhost:8080/api/transaction/test/propagation?username=error&type=not-supported"

# REQUIRES_NEW演示
curl -X POST "http://localhost:8080/api/transaction/test/propagation?username=test&type=requires-new-demo"
```

### 6. 🔧 其他配置问题

#### 6.1 多线程环境
Spring事务基于ThreadLocal，新线程中没有事务上下文。

```java
@Transactional
public void multiThread() {
    // 主线程有事务
    userRepository.save(user1);
    
    CompletableFuture.runAsync(() -> {
        // 子线程没有事务
        userRepository.save(user2);
    });
}
```

#### 6.2 数据库引擎不支持事务
如MySQL的MyISAM引擎不支持事务。

#### 6.3 只读事务中进行写操作
```java
@Transactional(readOnly = true)
public void readOnlyMethod() {
    userRepository.save(user); // 可能失败或被忽略
}
```

**测试接口**：
```bash
# 多线程（子线程无事务）
curl -X POST "http://localhost:8080/api/transaction/test/misconfiguration?username=error&type=multithread"

# 只读事务
curl -X POST "http://localhost:8080/api/transaction/test/misconfiguration?username=test&type=readonly"
```

## 🔍 调试技巧

### 1. 开启事务日志
在`application.yml`中配置：
```yaml
logging:
  level:
    org.springframework.transaction: DEBUG
    org.springframework.orm.jpa: DEBUG
```

### 2. 检查事务状态
```java
TransactionSynchronizationManager.isActualTransactionActive()
```

### 3. 使用断点调试
在关键位置设置断点，观察事务的创建和回滚过程。

## 📊 测试所有场景

运行单元测试：
```bash
mvn test
```

或使用提供的REST API进行手动测试：
- 清空数据库：`DELETE /api/transaction/clear`
- 查询所有用户：`GET /api/transaction/users`
- 测试各种场景：见上述各个场景的测试接口

## ✅ 最佳实践

1. **始终使用public方法**：将需要事务的方法声明为public
2. **避免内部调用**：重构代码结构，避免在同一类中调用事务方法
3. **正确处理异常**：
   - 不要吞掉异常
   - 需要回滚时确保抛出RuntimeException
   - 对受检异常配置rollbackFor
4. **选择合适的传播行为**：理解各种传播行为的含义
5. **注意多线程**：事务不会跨线程传播
6. **配置事务管理器**：确保正确配置了事务管理器
7. **使用合适的数据库引擎**：确保数据库支持事务

## 🎯 总结

Spring事务失效的核心原因：
1. **AOP代理的限制**（非public方法、内部调用）
2. **异常处理不当**（异常被捕获、错误的异常类型）
3. **配置错误**（传播行为、事务管理器）
4. **环境限制**（多线程、数据库引擎）

理解这些场景，可以帮助我们在开发中避免事务失效的问题，确保数据的一致性。

## 📚 参考资料

- [Spring官方文档 - 事务管理](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction)
- [Spring Boot官方文档](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
- [Spring AOP原理](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop)