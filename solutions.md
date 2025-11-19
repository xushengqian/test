# MySQL 锁等待超时问题解决方案

## 问题描述

执行以下SQL时出现锁等待超时错误：
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

`INSERT ... ON DUPLICATE KEY UPDATE` 语句在以下情况下容易出现锁等待超时：

1. **唯一索引冲突**：当多个事务同时尝试插入/更新同一行时，会触发唯一索引检查，导致行锁竞争
2. **长时间运行的事务**：有其他事务长时间持有该行的锁
3. **死锁**：多个事务相互等待对方释放锁
4. **索引问题**：唯一索引 `index_params` 可能导致锁范围过大
5. **事务隔离级别**：高隔离级别（如 REPEATABLE READ）会增加锁的持有时间

## 解决方案

### 方案1：增加锁等待超时时间（临时方案）

```sql
-- 设置会话级别的锁等待超时时间（单位：秒）
SET innodb_lock_wait_timeout = 120;

-- 或者设置全局级别（需要SUPER权限）
SET GLOBAL innodb_lock_wait_timeout = 120;
```

### 方案2：检查并终止阻塞的事务

1. 运行诊断脚本 `mysql_lock_diagnosis.sql` 找出阻塞的事务
2. 终止阻塞的线程：
```sql
-- 从诊断结果中获取 blocking_thread ID
KILL <thread_id>;
```

### 方案3：优化SQL语句（推荐）

#### 3.1 使用更精确的WHERE条件
如果可能，先查询再决定插入或更新：
```sql
-- 先查询
SELECT COUNT(*) FROM ks_index_user_performance 
WHERE index_params = '2025-12@202508118005';

-- 根据结果决定使用 INSERT 或 UPDATE
```

#### 3.2 使用事务控制
```sql
START TRANSACTION;
-- 设置较短的超时时间
SET innodb_lock_wait_timeout = 10;
INSERT INTO ks_index_user_performance (month, user_id, index_params, kfzj_deal_amount) 
VALUES ('2025-12', '202508118005', '2025-12@202508118005', '3884400.00') 
ON DUPLICATE KEY UPDATE kfzj_deal_amount = '3884400.00';
COMMIT;
```

#### 3.3 使用批量插入优化
如果有多条记录，使用批量插入：
```sql
INSERT INTO ks_index_user_performance (month, user_id, index_params, kfzj_deal_amount) 
VALUES 
    ('2025-12', '202508118005', '2025-12@202508118005', '3884400.00'),
    ('2025-12', '202508118006', '2025-12@202508118006', '3884401.00')
ON DUPLICATE KEY UPDATE kfzj_deal_amount = VALUES(kfzj_deal_amount);
```

### 方案4：检查表结构和索引

确保 `index_params` 字段有唯一索引，且索引设计合理：

```sql
-- 查看表结构
SHOW CREATE TABLE ks_index_user_performance;

-- 查看索引
SHOW INDEX FROM ks_index_user_performance;

-- 如果索引不合理，考虑重建索引
-- ALTER TABLE ks_index_user_performance DROP INDEX idx_index_params;
-- ALTER TABLE ks_index_user_performance ADD UNIQUE INDEX idx_index_params (index_params);
```

### 方案5：降低事务隔离级别（谨慎使用）

```sql
-- 查看当前隔离级别
SELECT @@transaction_isolation;

-- 设置为 READ COMMITTED（需要重启或新会话生效）
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;
```

### 方案6：使用应用层重试机制

在应用代码中添加重试逻辑：

```java
// Java示例
int maxRetries = 3;
int retryCount = 0;
boolean success = false;

while (retryCount < maxRetries && !success) {
    try {
        // 执行SQL
        executeInsertOrUpdate(sql);
        success = true;
    } catch (MySQLTransactionRollbackException e) {
        if (e.getMessage().contains("Lock wait timeout")) {
            retryCount++;
            Thread.sleep(100 * retryCount); // 指数退避
        } else {
            throw e;
        }
    }
}
```

### 方案7：检查是否有表锁

```sql
-- 检查是否有ALTER TABLE等操作在进行
SHOW PROCESSLIST;

-- 如果有ALTER TABLE操作，等待其完成或取消
```

## 预防措施

1. **监控长时间运行的事务**
   - 定期检查 `information_schema.INNODB_TRX` 表
   - 设置告警监控事务运行时间

2. **优化索引设计**
   - 确保唯一索引字段尽可能短
   - 避免在频繁更新的字段上创建唯一索引

3. **控制事务大小**
   - 避免在事务中执行大量操作
   - 尽快提交事务

4. **使用连接池**
   - 合理配置连接池大小
   - 避免连接泄漏

5. **应用层优化**
   - 实现重试机制
   - 使用消息队列异步处理批量更新

## 紧急处理步骤

1. **立即诊断**
   ```bash
   mysql -u用户名 -p数据库名 < mysql_lock_diagnosis.sql
   ```

2. **找出阻塞事务**
   - 查看 `blocking_thread` 列
   - 检查 `blocking_query` 了解阻塞原因

3. **终止阻塞事务**（如果确认安全）
   ```sql
   KILL <blocking_thread_id>;
   ```

4. **重试失败的SQL**

## 注意事项

- ⚠️ 终止事务前请确认该事务可以安全终止
- ⚠️ 修改全局配置前请评估对系统的影响
- ⚠️ 降低隔离级别可能影响数据一致性
- ✅ 优先使用应用层重试机制
- ✅ 定期监控和优化数据库性能
