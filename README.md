# ks_index_user_performance_details 删除操作优化

## 问题描述

SQL删除语句 `delete from ks_index_user_performance_details where index_id = ? and index_params = ?` 执行缓慢并导致死锁问题。

## 解决方案

详细优化方案请参考：
- [优化方案.md](./优化方案.md) - 完整的问题分析和优化方案
- [索引创建脚本.sql](./索引创建脚本.sql) - 数据库索引优化脚本
- [分批删除示例代码.java](./分批删除示例代码.java) - Java实现示例
- [分批删除示例代码.py](./分批删除示例代码.py) - Python实现示例

## 快速开始

1. **创建索引**（最重要）：
   ```sql
   CREATE INDEX idx_index_id_params 
   ON ks_index_user_performance_details(index_id, index_params);
   ```

2. **实现分批删除**：参考示例代码实现分批删除逻辑

3. **添加死锁重试**：在代码中添加死锁异常捕获和重试机制