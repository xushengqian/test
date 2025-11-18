# DELETE 语句性能优化方案

## 问题
`delete from ks_index_user_performance_details where index_id = ? and index_params = ?` 执行慢导致死锁。

## 解决方案文件

1. **优化方案.md** - 详细的问题分析和优化方案
2. **分批删除实现指南.md** - ⭐ 分批删除详细实现指南（推荐阅读）
3. **使用示例.md** - 各种语言的使用示例和最佳实践
4. **索引创建脚本.sql** - 创建索引的SQL脚本
5. **优化代码示例.java** - Java代码优化示例（分批删除、软删除等）
6. **分批删除完整示例.java** - 完整的Java实现，包含多种方式
7. **存储过程示例.sql** - MySQL存储过程优化方案
8. **监控查询.sql** - 性能监控和诊断查询

## 快速开始

### 1. 立即执行（紧急优化）
```sql
-- 创建联合索引
CREATE INDEX idx_index_id_params 
ON ks_index_user_performance_details(index_id, index_params);
```

### 2. 代码优化
参考 `优化代码示例.java` 实现分批删除，避免长时间持有锁。

### 3. 监控
使用 `监控查询.sql` 中的查询语句监控性能指标。

## 主要优化点

1. **添加联合索引** - 提升WHERE条件查询速度
2. **分批删除** - 避免长时间持有锁，减少死锁概率
3. **优化事务** - 缩短事务时间，及时提交
4. **软删除** - 如果业务允许，使用UPDATE代替DELETE
5. **定期维护** - 优化表结构，清理碎片
