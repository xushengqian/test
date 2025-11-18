# DELETE 性能优化 - 快速开始指南

## 🚨 问题现象

```sql
DELETE FROM ks_index_user_performance_details 
WHERE index_id = ? AND index_params = ?
```

**症状**: 执行慢（数秒），频繁死锁

---

## ✅ 快速解决方案（5分钟）

### 步骤1：创建索引（核心解决方案）

```sql
-- 在生产库执行
CREATE INDEX idx_index_id_params 
ON ks_index_user_performance_details(index_id, index_params)
ALGORITHM=INPLACE, LOCK=NONE;
```

### 步骤2：验证索引

```sql
-- 确认索引创建成功
SHOW INDEX FROM ks_index_user_performance_details;

-- 查看执行计划（替换实际值）
EXPLAIN DELETE FROM ks_index_user_performance_details 
WHERE index_id = 1 AND index_params = 'test';
```

**期望结果**: 
- `type` 显示为 `ref` 或 `range`
- `key` 显示为 `idx_index_id_params`

### 步骤3：测试性能

```sql
-- 测试删除性能
DELETE FROM ks_index_user_performance_details 
WHERE index_id = ? AND index_params = ?;
-- 观察执行时间应该从秒级降到毫秒级
```

---

## 📊 预期效果

| 指标 | 优化前 | 优化后 |
|------|--------|--------|
| **执行时间** | 1-10秒 | **10-100毫秒** |
| **死锁频率** | 经常发生 | **极少/消除** |
| **数据库负载** | 高 | **正常** |

---

## 🔧 进阶优化（如果索引后仍有问题）

### 场景1：需要删除大量数据（>1000行）

**使用批量删除**，避免长事务：

```java
// 使用提供的 OptimizedDeleteService
optimizedDeleteService.batchDelete(indexId, indexParams);
```

### 场景2：偶尔还是遇到死锁

**使用带重试的删除**：

```java
// 自动重试机制
optimizedDeleteService.deleteWithRetry(indexId, indexParams);
```

### 场景3：不确定数据量

**使用智能删除**（自动选择最优策略）：

```java
// 根据数据量自动选择策略
optimizedDeleteService.smartDelete(indexId, indexParams);
```

---

## 📁 相关文件说明

| 文件 | 说明 |
|------|------|
| `optimize_delete_performance.sql` | 完整的 SQL 诊断和优化脚本 |
| `OptimizedDeleteService.java` | Java 代码优化实现（5种方案） |
| `DELETE_OPTIMIZATION_SOLUTION.md` | 详细的技术方案文档 |

---

## ⚠️ 注意事项

1. **索引创建时间**: 
   - 小表（<100万行）：几秒到几分钟
   - 大表（>100万行）：可能需要10-30分钟
   - 使用 `ALGORITHM=INPLACE, LOCK=NONE` 不会阻塞其他操作

2. **字段类型考虑**:
   - 如果 `index_params` 是 TEXT/BLOB 类型，可能需要调整索引策略
   - 如果 `index_params` 是很长的 VARCHAR，考虑使用前缀索引

3. **测试优先**:
   - 建议先在测试环境验证
   - 确认性能提升后再在生产环境执行

---

## 🔍 监控和诊断

### 查看死锁信息

```sql
-- 查看最近的死锁
SHOW ENGINE INNODB STATUS;
```

### 查看慢查询

```sql
-- 启用慢查询日志
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;
```

---

## 🆘 常见问题

### Q1: 创建索引需要多久？
**A**: 取决于表大小，通常几分钟到半小时。可以查询进度：
```sql
SHOW PROCESSLIST;
```

### Q2: 创建索引会影响业务吗？
**A**: 使用 `ALGORITHM=INPLACE, LOCK=NONE` 方式创建索引，不会锁表，其他操作可以正常进行。

### Q3: 如果 index_params 字段很大怎么办？
**A**: 使用前缀索引：
```sql
CREATE INDEX idx_index_id_params 
ON ks_index_user_performance_details(index_id, index_params(100));
```

### Q4: 创建索引后还是慢怎么办？
**A**: 
1. 检查执行计划是否使用了索引（EXPLAIN）
2. 更新表统计信息：`ANALYZE TABLE ks_index_user_performance_details;`
3. 如果删除大量数据，使用批量删除策略

### Q5: 如何回滚？
**A**: 删除索引即可：
```sql
DROP INDEX idx_index_id_params ON ks_index_user_performance_details;
```

---

## 📞 需要帮助？

如果按照本指南操作后问题仍未解决，请检查：

1. ✅ 索引是否创建成功
2. ✅ 执行计划是否使用了索引
3. ✅ 是否还有其他慢查询导致锁冲突
4. ✅ 数据库配置是否合理（innodb_lock_wait_timeout 等）

详细的诊断步骤请参考 `DELETE_OPTIMIZATION_SOLUTION.md` 文档。

---

## ✨ 最佳实践总结

1. **立即创建索引** - 这是解决问题的根本方法
2. **大量删除时使用批量策略** - 避免长事务
3. **添加重试机制** - 处理偶发的锁冲突
4. **定期维护** - 执行 ANALYZE TABLE 更新统计信息
5. **监控性能** - 启用慢查询日志，持续关注

---

**执行时间**: 预计 5-10 分钟完成索引创建和验证  
**预期收益**: DELETE 性能提升 10-100 倍，死锁问题基本解决
