# DELETE 性能优化方案

## 问题描述
```sql
delete from ks_index_user_performance_details 
where index_id = ? and index_params = ?
```

**现象**: 执行缓慢，导致数据库死锁

## 根本原因分析

### 1. 缺少有效索引
当 WHERE 条件的字段没有索引时，DELETE 需要全表扫描，导致：
- 扫描时间长
- 持有行锁时间长
- 容易与其他事务产生锁冲突

### 2. 可能的死锁场景
- **场景A**: 多个事务同时删除不同的 index_id，但扫描顺序不一致
- **场景B**: DELETE 与 SELECT/UPDATE 操作交叉，锁获取顺序不同
- **场景C**: 删除大量数据时，持锁时间过长

## 优化方案

### ✅ 方案一：创建复合索引（推荐，优先级最高）

```sql
-- 创建复合索引（最优方案）
CREATE INDEX idx_index_id_params ON ks_index_user_performance_details(index_id, index_params);

-- 或者如果 index_params 是大字段，创建前缀索引
CREATE INDEX idx_index_id_params ON ks_index_user_performance_details(index_id, index_params(100));
```

**优点**:
- DELETE 可以直接定位到目标行，避免全表扫描
- 大幅减少持锁时间
- 从根本上解决性能问题

**效果预估**: 
- 从秒级降低到毫秒级
- 基本消除死锁问题

### ✅ 方案二：索引优化策略

如果已有索引但仍然慢，检查以下情况：

```sql
-- 1. 检查现有索引
SHOW INDEX FROM ks_index_user_performance_details;

-- 2. 查看执行计划
EXPLAIN DELETE FROM ks_index_user_performance_details 
WHERE index_id = 1 AND index_params = 'test';

-- 3. 如果 index_params 字段过大，考虑单独建 index_id 索引
CREATE INDEX idx_index_id ON ks_index_user_performance_details(index_id);
```

### ✅ 方案三：批量删除优化（针对大量数据）

如果一次删除的数据量很大（超过1000行），采用分批删除：

```sql
-- 原始方式（慢）
DELETE FROM ks_index_user_performance_details 
WHERE index_id = ? AND index_params = ?;

-- 优化方式：限制每次删除数量
DELETE FROM ks_index_user_performance_details 
WHERE index_id = ? AND index_params = ?
LIMIT 1000;
-- 循环执行直到删除完成
```

**Java 代码示例**:
```java
public int batchDelete(Long indexId, String indexParams) {
    int totalDeleted = 0;
    int batchSize = 1000;
    int deleted;
    
    do {
        deleted = jdbcTemplate.update(
            "DELETE FROM ks_index_user_performance_details " +
            "WHERE index_id = ? AND index_params = ? LIMIT ?",
            indexId, indexParams, batchSize
        );
        totalDeleted += deleted;
        
        // 短暂休眠，释放锁资源
        if (deleted == batchSize) {
            Thread.sleep(10);
        }
    } while (deleted == batchSize);
    
    return totalDeleted;
}
```

### ✅ 方案四：使用更精确的条件

如果表有主键或唯一索引，可以先查询再删除：

```java
// 先查询出主键 ID
List<Long> ids = jdbcTemplate.queryForList(
    "SELECT id FROM ks_index_user_performance_details " +
    "WHERE index_id = ? AND index_params = ? LIMIT 1000",
    Long.class, indexId, indexParams
);

// 使用主键批量删除（主键删除最快）
if (!ids.isEmpty()) {
    jdbcTemplate.update(
        "DELETE FROM ks_index_user_performance_details WHERE id IN (?)",
        StringUtils.join(ids, ",")
    );
}
```

### ✅ 方案五：调整事务隔离级别（谨慎使用）

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void deleteWithLowerIsolation(Long indexId, String indexParams) {
    // READ_COMMITTED 比 REPEATABLE_READ 锁更少
    // 但要确保业务逻辑允许这种隔离级别
    jdbcTemplate.update(
        "DELETE FROM ks_index_user_performance_details " +
        "WHERE index_id = ? AND index_params = ?",
        indexId, indexParams
    );
}
```

## 死锁预防措施

### 1. 保证访问顺序一致
```java
// 统一按 index_id 升序访问
// 避免事务1先锁A再锁B，事务2先锁B再锁A的情况
```

### 2. 缩短事务时间
```java
@Transactional
public void optimizedDelete(Long indexId, String indexParams) {
    // 不要在事务中执行耗时操作
    // 尽快完成数据库操作
    deletePerformanceDetails(indexId, indexParams);
    // 不要在这里做复杂计算或调用外部服务
}
```

### 3. 设置锁等待超时
```sql
-- 在会话级别设置
SET innodb_lock_wait_timeout = 5;  -- 5秒超时

-- 或在配置文件中设置
-- my.cnf
[mysqld]
innodb_lock_wait_timeout = 5
```

### 4. 使用乐观锁代替悲观锁（适用场景）
```java
// 如果业务允许，先查询后删除，减少锁持有时间
```

## 监控和诊断

### 查看死锁信息
```sql
-- 查看最近的死锁日志
SHOW ENGINE INNODB STATUS;

-- 查看当前锁等待
SELECT * FROM information_schema.innodb_locks;
SELECT * FROM information_schema.innodb_lock_waits;

-- MySQL 8.0+
SELECT * FROM performance_schema.data_locks;
SELECT * FROM performance_schema.data_lock_waits;
```

### 性能监控
```sql
-- 查看慢查询日志
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;  -- 记录超过1秒的查询

-- 分析表统计信息
ANALYZE TABLE ks_index_user_performance_details;
```

## 实施步骤（推荐执行顺序）

### 第一步：立即实施（5分钟）
```sql
-- 1. 创建索引（非阻塞方式）
CREATE INDEX idx_index_id_params 
ON ks_index_user_performance_details(index_id, index_params) 
ALGORITHM=INPLACE, LOCK=NONE;

-- 2. 验证索引创建成功
SHOW INDEX FROM ks_index_user_performance_details;
```

### 第二步：验证效果（10分钟）
```sql
-- 执行前查看执行计划
EXPLAIN DELETE FROM ks_index_user_performance_details 
WHERE index_id = 1 AND index_params = 'test';
-- 检查 type 是否为 ref 或 range，key 是否使用了新索引

-- 测试删除性能
DELETE FROM ks_index_user_performance_details 
WHERE index_id = ? AND index_params = ?;
-- 观察执行时间
```

### 第三步：代码优化（如果仍有问题）
- 实施批量删除逻辑
- 调整事务范围
- 添加重试机制

### 第四步：持续监控
- 启用慢查询日志
- 监控死锁发生频率
- 定期分析表

## 注意事项

1. **索引字段选择**: 
   - `index_id` 通常是数字类型，适合做索引
   - `index_params` 如果是 TEXT/BLOB 类型，考虑前缀索引或改用 VARCHAR

2. **索引创建时机**:
   - 大表（百万级以上）创建索引可能需要较长时间
   - 使用 `ALGORITHM=INPLACE, LOCK=NONE` 减少影响
   - 建议在业务低峰期执行

3. **索引维护成本**:
   - 索引会增加 INSERT/UPDATE 的开销
   - 但对于这个场景，DELETE 性能提升远大于维护成本

4. **数据库版本考虑**:
   - MySQL 5.6+ 支持在线 DDL
   - 更老版本需要更谨慎地创建索引

## 预期效果

| 指标 | 优化前 | 优化后 |
|------|--------|--------|
| DELETE 执行时间 | 1-10秒 | 10-100毫秒 |
| 锁等待时间 | 频繁超时 | 基本无等待 |
| 死锁发生频率 | 经常 | 极少/无 |
| 数据库 CPU | 高 | 正常 |

## 快速参考命令

```sql
-- 创建索引
CREATE INDEX idx_index_id_params ON ks_index_user_performance_details(index_id, index_params);

-- 检查索引
SHOW INDEX FROM ks_index_user_performance_details;

-- 查看执行计划
EXPLAIN DELETE FROM ks_index_user_performance_details WHERE index_id = 1 AND index_params = 'test';

-- 查看死锁
SHOW ENGINE INNODB STATUS;

-- 删除索引（如果需要）
DROP INDEX idx_index_id_params ON ks_index_user_performance_details;
```

---

**建议**: 优先创建索引（方案一），这是解决问题的根本方法，效果最显著。
