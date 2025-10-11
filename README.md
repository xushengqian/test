# Spring 事务不生效的常见场景分析

本项目详细分析了 Spring 事务管理中常见的失效场景，提供了完整的示例代码和解决方案。

## 📋 目录

1. [类内部方法调用](#1-类内部方法调用)
2. [异常被捕获](#2-异常被捕获)
3. [方法不是 public](#3-方法不是-public)
4. [事务传播行为配置错误](#4-事务传播行为配置错误)
5. [数据库引擎不支持事务](#5-数据库引擎不支持事务)
6. [异步调用](#6-异步调用)
7. [类没有被 Spring 管理](#7-类没有被-spring-管理)

## 🚀 快速开始

### 环境要求
- Java 8+
- Maven 3.6+
- Spring Boot 2.7.0

### 运行项目
```bash
# 克隆项目
git clone <repository-url>
cd spring-transaction-failure-scenarios

# 编译运行
mvn spring-boot:run

# 访问 H2 控制台查看数据
# http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:testdb
# 用户名: sa
# 密码: password
```

## 📚 事务失效场景详解

### 1. 类内部方法调用

**问题描述**：在同一个类中，一个非事务方法调用另一个事务方法，事务不会生效。

**问题原因**：Spring AOP 基于代理模式，类内部调用不经过代理，因此事务注解失效。

**示例代码**：[InternalMethodCallScenario.java](src/main/java/com/example/transaction/scenario/InternalMethodCallScenario.java)

**错误示例**：
```java
@Service
public class InternalMethodCallScenario {
    
    // 非事务方法调用事务方法
    public void transfer(...) {
        transferMoney(...); // 事务失效！
    }
    
    @Transactional
    public void transferMoney(...) {
        // 事务逻辑
    }
}
```

**解决方案**：
1. **自注入方式**：通过注入自己获取代理对象
2. **方法合并**：在调用方法上添加 `@Transactional` 注解
3. **拆分服务**：将事务方法提取到另一个 Service 中

### 2. 异常被捕获

**问题描述**：事务方法中抛出的异常被 try-catch 捕获，事务不会回滚。

**问题原因**：Spring 默认只对运行时异常和 Error 进行回滚，且异常必须抛出到事务边界外。

**示例代码**：[ExceptionCatchScenario.java](src/main/java/com/example/transaction/scenario/ExceptionCatchScenario.java)

**错误示例**：
```java
@Transactional
public void transfer(...) {
    try {
        // 数据库操作
        if (error) {
            throw new RuntimeException("业务异常");
        }
    } catch (Exception e) {
        log.error("异常被捕获", e);
        // 事务不会回滚！
    }
}
```

**解决方案**：
1. **重新抛出异常**：在 catch 块中重新抛出异常
2. **手动回滚**：使用 `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`
3. **指定回滚异常**：使用 `@Transactional(rollbackFor = Exception.class)`

### 3. 方法不是 public

**问题描述**：`@Transactional` 注解只能作用在 public 方法上。

**问题原因**：Spring AOP 默认只能代理 public 方法。

**示例代码**：[NonPublicMethodScenario.java](src/main/java/com/example/transaction/scenario/NonPublicMethodScenario.java)

**错误示例**：
```java
@Transactional
private void transfer(...) {  // 事务失效！
    // 事务逻辑
}

@Transactional
protected void transfer(...) {  // 事务失效！
    // 事务逻辑
}
```

**解决方案**：
- **使用 public 方法**：确保事务方法是 public 的

### 4. 事务传播行为配置错误

**问题描述**：错误的传播行为配置导致事务不按预期工作。

**问题原因**：不同的传播行为有不同的事务处理逻辑。

**示例代码**：[PropagationScenario.java](src/main/java/com/example/transaction/scenario/PropagationScenario.java)

**错误示例**：
```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public void transfer(...) {  // 事务被挂起！
    // 数据库操作，不会回滚
}

@Transactional(propagation = Propagation.NEVER)
public void transfer(...) {  // 如果存在事务会抛异常！
    // 数据库操作
}
```

**解决方案**：
1. **使用默认传播行为**：`REQUIRED`（默认）
2. **理解各种传播行为**：根据业务需求选择合适的传播行为
3. **合理使用 REQUIRES_NEW**：谨慎使用，注意异常处理

### 5. 数据库引擎不支持事务

**问题描述**：使用不支持事务的存储引擎（如 MySQL 的 MyISAM）。

**问题原因**：事务是数据库层面的功能，数据库不支持则 Spring 事务管理无效。

**示例代码**：[DatabaseEngineScenario.java](src/main/java/com/example/transaction/scenario/DatabaseEngineScenario.java)

**解决方案**：
- **使用支持事务的存储引擎**：
  - MySQL: InnoDB（5.5+ 默认）
  - PostgreSQL: 默认支持
  - Oracle: 默认支持
  - SQL Server: 默认支持

### 6. 异步调用

**问题描述**：在事务方法中使用异步调用，异步任务不在同一个事务中。

**问题原因**：Spring 事务基于 ThreadLocal，异步执行在不同线程中，无法共享事务上下文。

**示例代码**：[AsyncCallScenario.java](src/main/java/com/example/transaction/scenario/AsyncCallScenario.java)

**错误示例**：
```java
@Transactional
public void transfer(...) {
    // 主线程操作
    updateAccount1();
    
    // 异步执行，不在同一事务中！
    CompletableFuture.runAsync(() -> {
        updateAccount2(); // 不会回滚
    });
    
    if (error) {
        throw new RuntimeException(); // 只回滚主线程操作
    }
}
```

**解决方案**：
1. **同步执行**：在同一个线程中完成所有事务操作
2. **事务后异步**：事务完成后再进行异步操作
3. **消息队列**：使用消息队列处理异步任务

### 7. 类没有被 Spring 管理

**问题描述**：直接 new 对象或类没有被 Spring 容器管理，事务注解不会生效。

**问题原因**：Spring AOP 只能作用在 Spring 容器管理的 Bean 上。

**示例代码**：[NonManagedBeanScenario.java](src/main/java/com/example/transaction/scenario/NonManagedBeanScenario.java)

**错误示例**：
```java
public void someMethod() {
    // 直接 new 对象，不是 Spring Bean
    TransactionService service = new TransactionService();
    service.transfer(); // 事务失效！
}

class TransactionService {  // 没有 @Component 等注解
    @Transactional
    public void transfer() {
        // 事务逻辑，不会生效
    }
}
```

**解决方案**：
1. **使用 Spring 注解**：添加 `@Component`、`@Service` 等注解
2. **依赖注入**：通过 `@Autowired` 注入 Bean，不要使用 new
3. **配置类注册**：在 `@Configuration` 类中用 `@Bean` 注册

## 🔧 最佳实践

### 1. 事务注解使用规范
```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // 类级别默认只读
public class UserService {
    
    @Transactional  // 写操作覆盖类级别配置
    public void updateUser(User user) {
        // 写操作
    }
    
    @Transactional(
        rollbackFor = Exception.class,  // 指定回滚异常
        timeout = 30,                   // 设置超时时间
        isolation = Isolation.READ_COMMITTED  // 设置隔离级别
    )
    public void complexOperation() {
        // 复杂操作
    }
}
```

### 2. 异常处理最佳实践
```java
@Transactional
public void businessOperation() {
    try {
        // 业务逻辑
        doSomething();
    } catch (BusinessException e) {
        // 记录日志
        log.error("业务异常", e);
        // 重新抛出，触发回滚
        throw e;
    } catch (Exception e) {
        // 包装为业务异常
        throw new BusinessException("操作失败", e);
    }
}
```

### 3. 服务层设计模式
```java
@Service
public class AccountService {
    
    @Autowired
    private AccountTransactionService transactionService;
    
    public void transfer(TransferRequest request) {
        // 参数验证
        validateTransferRequest(request);
        
        // 调用事务服务
        transactionService.executeTransfer(request);
        
        // 事务后处理（异步）
        afterTransferProcess(request);
    }
}

@Service
class AccountTransactionService {
    
    @Transactional
    public void executeTransfer(TransferRequest request) {
        // 事务逻辑
    }
}
```

## 🧪 测试验证

项目中每个场景都提供了对应的测试方法，可以通过以下方式验证：

1. **启动应用**：`mvn spring-boot:run`
2. **查看日志**：观察事务开启、提交、回滚的日志
3. **检查数据**：通过 H2 控制台查看数据变化
4. **断点调试**：设置断点观察事务状态

## 📖 参考资料

- [Spring Framework Reference Documentation - Transaction Management](https://docs.spring.io/spring-framework/docs/current/reference/html/data-access.html#transaction)
- [Spring Boot Reference Guide - Working with SQL Databases](https://docs.spring.io/spring-boot/docs/current/reference/html/data.html#data.sql)
- [Spring AOP Reference Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop)

## 🤝 贡献

欢迎提交 Issue 和 Pull Request 来完善这个项目！

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。