# MySQL 锁等待超时问题解决方案

## 问题描述

在执行以下 SQL 语句时出现锁等待超时错误：

```sql
INSERT INTO ks_index_user_performance (month, user_id, index_params, kfzj_deal_amount) 
VALUES ('2025-12', '202508118005', '2025-12@202508118005', '3884400.00') 
ON DUPLICATE KEY UPDATE kfzj_deal_amount = '3884400.00'
```

错误信息：
```
Lock wait timeout exceeded; try restarting transaction
```

## 问题原因分析

1. **长时间运行的事务**：有其他事务持有该表的锁且未提交
2. **并发冲突**：多个事务同时尝试更新同一行数据
3. **死锁或锁竞争**：事务之间存在循环等待
4. **索引问题**：`index_params` 或 `(month, user_id)` 组合可能缺少合适的索引

## 解决方案

### 方案 1：增加锁等待超时时间（临时方案）

```sql
-- 查看当前锁等待超时时间（默认 50 秒）
SHOW VARIABLES LIKE 'innodb_lock_wait_timeout';

-- 临时增加超时时间（会话级别）
SET innodb_lock_wait_timeout = 120;

-- 或者在连接字符串中设置
-- jdbc:mysql://host:port/db?innodb_lock_wait_timeout=120
```

### 方案 2：优化 SQL 语句（推荐）

#### 2.1 使用更精确的 WHERE 条件

确保 `ON DUPLICATE KEY UPDATE` 基于唯一索引，避免全表扫描：

```sql
-- 检查表结构，确保有唯一索引
SHOW CREATE TABLE ks_index_user_performance;

-- 如果 index_params 是唯一键，使用它
INSERT INTO ks_index_user_performance (month, user_id, index_params, kfzj_deal_amount) 
VALUES ('2025-12', '202508118005', '2025-12@202508118005', '3884400.00') 
ON DUPLICATE KEY UPDATE kfzj_deal_amount = VALUES(kfzj_deal_amount);
```

#### 2.2 使用事务隔离级别优化

```java
// Java 代码示例
@Transactional(isolation = Isolation.READ_COMMITTED)
public void updateUserPerformance(String month, String userId, BigDecimal amount) {
    // 使用 READ_COMMITTED 隔离级别，减少锁持有时间
}
```

### 方案 3：检查并终止阻塞的事务

```sql
-- 1. 查看当前正在运行的事务
SELECT * FROM information_schema.innodb_trx;

-- 2. 查看锁等待情况
SELECT 
    r.trx_id waiting_trx_id,
    r.trx_mysql_thread_id waiting_thread,
    r.trx_query waiting_query,
    b.trx_id blocking_trx_id,
    b.trx_mysql_thread_id blocking_thread,
    b.trx_query blocking_query
FROM information_schema.innodb_lock_waits w
INNER JOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_trx_id
INNER JOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_trx_id;

-- 3. 终止阻塞的事务（谨慎使用）
KILL <thread_id>;
```

### 方案 4：代码层面优化

#### 4.1 使用重试机制

```java
@Retryable(value = {MySQLTransactionRollbackException.class}, 
           maxAttempts = 3, 
           backoff = @Backoff(delay = 100))
public void insertOrUpdateUserPerformance(String month, String userId, BigDecimal amount) {
    // 执行 INSERT ... ON DUPLICATE KEY UPDATE
}
```

#### 4.2 批量处理优化

```java
// 使用批量插入，减少事务次数
@Transactional
public void batchUpdateUserPerformance(List<UserPerformance> records) {
    // 批量执行，减少锁竞争
    for (UserPerformance record : records) {
        // 批量插入逻辑
    }
}
```

#### 4.3 使用悲观锁或乐观锁

```java
// 方案 A：使用 SELECT ... FOR UPDATE（悲观锁）
@Transactional
public void updateWithLock(String month, String userId, BigDecimal amount) {
    UserPerformance existing = repository.findByMonthAndUserId(month, userId)
        .orElse(null);
    
    if (existing != null) {
        existing.setKfzjDealAmount(amount);
        repository.save(existing);
    } else {
        repository.save(new UserPerformance(month, userId, amount));
    }
}

// 方案 B：使用版本号（乐观锁）
@Entity
public class UserPerformance {
    @Version
    private Long version; // 添加版本字段
}
```

### 方案 5：数据库层面优化

#### 5.1 检查并创建合适的索引

```sql
-- 确保有唯一索引
ALTER TABLE ks_index_user_performance 
ADD UNIQUE INDEX idx_month_user (month, user_id);

-- 或者如果 index_params 是唯一键
ALTER TABLE ks_index_user_performance 
ADD UNIQUE INDEX idx_index_params (index_params);
```

#### 5.2 优化表结构

```sql
-- 检查表状态
SHOW TABLE STATUS LIKE 'ks_index_user_performance';

-- 如果表很大，考虑分区
ALTER TABLE ks_index_user_performance 
PARTITION BY RANGE (YEAR(month)) (
    PARTITION p2024 VALUES LESS THAN (2025),
    PARTITION p2025 VALUES LESS THAN (2026),
    PARTITION p2026 VALUES LESS THAN (2027)
);
```

### 方案 6：使用消息队列异步处理

对于非实时性要求高的场景，可以使用消息队列：

```java
// 发送消息到队列
@Async
public void updateUserPerformanceAsync(String month, String userId, BigDecimal amount) {
    messageQueue.send(new UserPerformanceUpdateMessage(month, userId, amount));
}

// 消费者处理
@RabbitListener(queues = "user.performance.update")
public void handleUpdate(UserPerformanceUpdateMessage message) {
    // 异步处理，避免阻塞主流程
    repository.insertOrUpdate(message);
}
```

## 最佳实践建议

1. **事务尽量短小**：尽快提交事务，减少锁持有时间
2. **避免长事务**：不要在事务中执行耗时操作（如外部 API 调用）
3. **使用合适的隔离级别**：根据业务需求选择最低的隔离级别
4. **添加重试机制**：对于可能冲突的操作，实现指数退避重试
5. **监控锁等待**：定期检查 `information_schema.innodb_lock_waits`
6. **索引优化**：确保有合适的唯一索引支持 `ON DUPLICATE KEY UPDATE`

## 紧急处理步骤

如果生产环境出现此问题：

1. **立即检查阻塞事务**：
   ```sql
   SELECT * FROM information_schema.innodb_trx 
   ORDER BY trx_started;
   ```

2. **识别长时间运行的事务**：
   ```sql
   SELECT trx_id, trx_started, TIMESTAMPDIFF(SECOND, trx_started, NOW()) as duration_seconds
   FROM information_schema.innodb_trx
   WHERE TIMESTAMPDIFF(SECOND, trx_started, NOW()) > 60;
   ```

3. **谨慎终止阻塞事务**（需要确认业务影响）：
   ```sql
   KILL <blocking_thread_id>;
   ```

4. **临时增加超时时间**（给业务恢复时间）：
   ```sql
   SET GLOBAL innodb_lock_wait_timeout = 120;
   ```

## 预防措施

1. **代码审查**：确保所有事务都尽快提交
2. **监控告警**：设置锁等待时间监控
3. **压力测试**：在高并发场景下测试
4. **数据库优化**：定期分析慢查询和锁等待情况
