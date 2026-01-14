# Spring @Transactional(propagation = Propagation.NOT_SUPPORTED) 详解

## 1. 概述

`Propagation.NOT_SUPPORTED` 是 Spring 框架中事务传播行为的一种类型。当方法被标记为此传播类型时，**该方法将始终以非事务方式执行**。

## 2. 工作原理

```
┌─────────────────────────────────────────────────────────┐
│                    调用方法                              │
│  ┌─────────────────────────────────────────────────┐   │
│  │           存在事务?                               │   │
│  │              │                                   │   │
│  │      ┌───────┴───────┐                          │   │
│  │      ↓               ↓                          │   │
│  │    是               否                           │   │
│  │      │               │                          │   │
│  │      ↓               ↓                          │   │
│  │  挂起当前事务     直接执行                        │   │
│  │      │          (无事务)                         │   │
│  │      ↓               │                          │   │
│  │  以非事务方式执行     │                          │   │
│  │      │               │                          │   │
│  │      ↓               │                          │   │
│  │  方法执行完成         │                          │   │
│  │      │               │                          │   │
│  │      ↓               │                          │   │
│  │  恢复挂起的事务       │                          │   │
│  │      │               │                          │   │
│  │      └───────┬───────┘                          │   │
│  │              ↓                                   │   │
│  │         返回结果                                  │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
```

### 2.1 核心行为

| 场景 | 行为 |
|------|------|
| 调用方存在事务 | **挂起**当前事务，以非事务方式执行，执行完成后**恢复**挂起的事务 |
| 调用方不存在事务 | 直接以非事务方式执行 |

## 3. 代码示例

### 3.1 基础用法

```java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    /**
     * 此方法永远不会在事务中执行
     * 如果调用方存在事务，该事务会被挂起
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void processWithoutTransaction() {
        // 业务逻辑 - 始终在非事务环境中执行
        System.out.println("执行非事务操作...");
    }
}
```

### 3.2 实际应用场景

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    @Autowired
    private ReportRepository reportRepository;
    
    @Autowired
    private ExternalApiClient externalApiClient;

    /**
     * 场景1：生成报表 - 只读操作，不需要事务
     * 
     * 使用 NOT_SUPPORTED 可以避免长时间占用数据库连接
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Report generateLargeReport(Long reportId) {
        // 耗时的报表生成逻辑
        // 不需要事务保护，避免长时间持有数据库连接
        return reportRepository.generateReport(reportId);
    }

    /**
     * 场景2：调用外部API
     * 
     * 外部API调用不应该影响当前事务
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ApiResponse callExternalService(String requestData) {
        // 调用外部服务，此操作与数据库事务无关
        // 使用 NOT_SUPPORTED 确保不会因为外部调用失败而回滚事务
        return externalApiClient.call(requestData);
    }

    /**
     * 场景3：记录审计日志
     * 
     * 即使主事务回滚，日志也应该被保存
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void saveAuditLog(AuditLog log) {
        // 审计日志应该独立于主事务
        // 使用 NOT_SUPPORTED 确保日志不受主事务影响
        auditLogRepository.save(log);
    }
}
```

### 3.3 与其他服务配合使用

```java
@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private NotificationService notificationService;

    @Transactional
    public void createOrder(Order order) {
        // 1. 保存订单（在事务中）
        orderRepository.save(order);
        
        // 2. 发送通知（NOT_SUPPORTED - 挂起当前事务）
        // 通知发送失败不应该导致订单创建失败
        notificationService.sendOrderNotification(order);
        
        // 3. 继续其他事务操作
        // 此时事务已恢复
        orderRepository.updateStatus(order.getId(), "CREATED");
    }
}

@Service
public class NotificationService {

    /**
     * 发送通知 - 不需要事务
     * 即使发送失败，也不应影响订单创建
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendOrderNotification(Order order) {
        // 发送邮件、短信等通知
        // 此方法执行期间，OrderService的事务被挂起
    }
}
```

## 4. 适用场景

### ✅ 推荐使用的场景

| 场景 | 说明 |
|------|------|
| **只读查询操作** | 大量数据查询不需要事务保护 |
| **外部API调用** | 与外部系统交互，避免长事务 |
| **日志记录** | 日志应该独立于业务事务 |
| **缓存操作** | 缓存读写不需要数据库事务 |
| **长时间运行的任务** | 避免长时间占用数据库连接 |
| **消息发送** | 发送消息到消息队列 |

### ❌ 不推荐使用的场景

| 场景 | 说明 |
|------|------|
| **需要数据一致性的操作** | 多表更新需要事务保证 |
| **银行转账等关键业务** | 必须保证原子性 |
| **依赖事务回滚的操作** | 操作失败需要回滚 |

## 5. 与其他传播行为对比

```java
// REQUIRED（默认）- 支持当前事务，没有则创建
@Transactional(propagation = Propagation.REQUIRED)

// SUPPORTS - 支持当前事务，没有则非事务执行
@Transactional(propagation = Propagation.SUPPORTS)

// MANDATORY - 必须在事务中执行，没有则抛异常
@Transactional(propagation = Propagation.MANDATORY)

// REQUIRES_NEW - 总是创建新事务，挂起当前事务
@Transactional(propagation = Propagation.REQUIRES_NEW)

// NOT_SUPPORTED - 总是非事务执行，挂起当前事务
@Transactional(propagation = Propagation.NOT_SUPPORTED)

// NEVER - 必须非事务执行，存在事务则抛异常
@Transactional(propagation = Propagation.NEVER)

// NESTED - 嵌套事务，可以独立回滚
@Transactional(propagation = Propagation.NESTED)
```

### 传播行为对比表

| 传播行为 | 当前有事务 | 当前无事务 |
|----------|-----------|-----------|
| REQUIRED | 加入事务 | 创建新事务 |
| SUPPORTS | 加入事务 | 非事务执行 |
| MANDATORY | 加入事务 | 抛出异常 |
| REQUIRES_NEW | 挂起，创建新事务 | 创建新事务 |
| **NOT_SUPPORTED** | **挂起，非事务执行** | **非事务执行** |
| NEVER | 抛出异常 | 非事务执行 |
| NESTED | 创建嵌套事务 | 创建新事务 |

## 6. NOT_SUPPORTED vs REQUIRES_NEW

这两个传播行为都会挂起当前事务，但有本质区别：

```java
@Service
public class ComparisonService {

    /**
     * NOT_SUPPORTED：挂起事务，非事务执行
     * - 不会创建新事务
     * - 操作直接提交到数据库
     * - 无法回滚
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void notSupportedMethod() {
        // 直接操作，无事务保护
    }

    /**
     * REQUIRES_NEW：挂起事务，创建新事务执行
     * - 创建独立的新事务
     * - 新事务可以独立提交或回滚
     * - 与外部事务完全隔离
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requiresNewMethod() {
        // 在独立事务中执行
    }
}
```

| 特性 | NOT_SUPPORTED | REQUIRES_NEW |
|------|---------------|--------------|
| 挂起当前事务 | ✅ | ✅ |
| 创建新事务 | ❌ | ✅ |
| 可以回滚 | ❌ | ✅ |
| 数据库连接 | 可能释放 | 需要新连接 |
| 性能影响 | 较小 | 较大 |

## 7. 注意事项

### 7.1 同类调用问题

```java
@Service
public class SameClassService {

    // ⚠️ 错误示例：同类内部调用不会触发代理
    public void methodA() {
        this.methodB(); // 直接调用，NOT_SUPPORTED 不生效！
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void methodB() {
        // 如果从 methodA 调用，事务配置不会生效
    }
}
```

### 7.2 正确的做法

```java
@Service
public class CallerService {

    @Autowired
    private CalleeService calleeService;

    @Transactional
    public void methodA() {
        // ✅ 通过注入的 Bean 调用，代理生效
        calleeService.methodB();
    }
}

@Service
public class CalleeService {

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void methodB() {
        // 事务配置正确生效
    }
}
```

### 7.3 异常处理

```java
@Service
public class ExceptionHandlingService {

    @Transactional
    public void outerMethod() {
        try {
            // 调用 NOT_SUPPORTED 方法
            notSupportedMethod();
        } catch (Exception e) {
            // NOT_SUPPORTED 方法的异常不会导致外部事务回滚
            // 因为它不在事务中执行
            log.error("非事务操作失败", e);
        }
        
        // 外部事务继续执行
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void notSupportedMethod() {
        // 此方法中的异常不会传播到外部事务
    }
}
```

## 8. 最佳实践

1. **明确使用意图**：只在确实不需要事务的场景使用
2. **避免数据不一致**：确保非事务操作不会导致数据不一致
3. **考虑补偿机制**：对于关键操作，考虑实现补偿逻辑
4. **日志记录**：记录非事务操作的执行情况，便于问题排查
5. **单元测试**：充分测试非事务场景下的行为

## 9. 总结

`Propagation.NOT_SUPPORTED` 提供了一种在事务环境中执行非事务操作的方式。它通过挂起当前事务来确保方法以非事务方式执行，适用于不需要事务保护的场景，如日志记录、外部API调用、只读查询等。

使用时需要注意：
- 操作一旦执行就会立即生效，无法回滚
- 同类内部调用不会触发代理
- 不适用于需要数据一致性保证的场景
