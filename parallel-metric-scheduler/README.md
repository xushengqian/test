# 并行指标调度系统

一个高性能的分布式指标调度和执行系统，支持大规模并发执行、多种指标类型、智能重试和实时监控。

## 🚀 核心特性

### 1. 高性能并行处理
- **多级并行架构**：作业扫描、创建和执行分别使用独立线程池
- **批量处理优化**：支持批量扫描和创建作业，减少数据库压力
- **智能并发控制**：可配置的最大并发数，防止资源耗尽
- **异步结果保存**：执行结果异步保存，不阻塞作业完成

### 2. 分布式调度支持
- **基于Redis的分布式锁**：支持多实例部署，防止作业重复执行
- **自动故障转移**：某个实例失败时，其他实例可接管作业
- **单机模式兼容**：未配置Redis时自动降级为单机模式

### 3. 多种指标类型
- **SQL查询**：直接执行SQL语句
- **存储过程**：调用数据库存储过程
- **Python脚本**：执行自定义Python脚本
- **并行SQL**：一个指标内并行执行多个SQL查询

### 4. 灵活的调度配置
- **Cron表达式**：支持标准Cron表达式定时调度
- **固定频率**：按固定时间间隔执行
- **一次性执行**：指定时间执行一次
- **优先级调度**：支持作业优先级，高优先级作业优先执行

### 5. 企业级可靠性
- **自动重试机制**：失败作业自动重试，可配置重试次数
- **超时控制**：每个作业可独立配置超时时间
- **详细日志记录**：完整的作业执行日志，便于问题排查
- **性能监控**：实时收集和存储性能指标

## 📊 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                    并行指标调度器                              │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ 扫描线程池  │  │ 创建线程池  │  │ 执行线程池  │         │
│  │ (4 workers) │  │ (5 workers) │  │(20 workers) │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│         │               │                  │                 │
│         ▼               ▼                  ▼                 │
│  ┌──────────────────────────────────────────────┐           │
│  │         并行作业调度引擎                      │           │
│  │  - 批量扫描调度配置                           │           │
│  │  - 并行创建作业实例                           │           │
│  │  - 优先级队列调度                             │           │
│  │  - 智能并发控制                               │           │
│  └──────────────────────────────────────────────┘           │
│                                                               │
└─────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│    MySQL    │      │    Redis    │      │   监控系统   │
│  (作业数据)  │      │ (分布式锁)  │      │ (性能指标)   │
└─────────────┘      └─────────────┘      └─────────────┘
```

## 🔧 并行处理优势

### 传统串行调度 vs 并行调度对比

| 特性 | 串行调度 | 并行调度 |
|------|---------|---------|
| 作业扫描 | 逐个扫描，慢 | 批量并行扫描，快10倍+ |
| 作业创建 | 串行创建 | 批量并行创建 |
| 作业执行 | 有限并发 | 高并发执行（50+） |
| 资源利用 | 低（单线程） | 高（多线程池） |
| 吞吐量 | 低 | 高3-5倍 |
| 适用场景 | 小规模 | 大规模（1000+指标） |

### 性能指标示例

在配置 `MAX_CONCURRENT_JOBS=50` 的情况下：
- **扫描速度**：1000个调度配置扫描时间 < 5秒
- **创建速度**：批量创建100个作业 < 2秒
- **执行吞吐量**：每分钟可完成50-100个作业（取决于作业复杂度）
- **响应时间**：作业从PENDING到开始执行 < 30秒

## 🚦 快速开始

### 1. 环境要求

- Python 3.8+
- MySQL 5.7+
- Redis 6.0+ (可选，用于分布式部署)

### 2. 安装依赖

```bash
cd /workspace/parallel-metric-scheduler
pip install -r requirements.txt
```

### 3. 配置数据库

创建数据库和表结构：

```bash
mysql -u root -p < scripts/create_database.sql
```

插入测试数据（可选）：

```bash
mysql -u root -p < scripts/insert_test_data.sql
```

### 4. 配置环境变量

复制并修改配置文件：

```bash
cp .env.example .env
# 编辑 .env 文件，设置数据库连接等配置
```

主要配置项说明：

```bash
# 并发控制
MAX_CONCURRENT_JOBS=50        # 最大并发作业数
EXECUTOR_WORKERS=20            # 执行器工作线程数
JOB_SCAN_WORKERS=4            # 扫描工作线程数
BATCH_CREATE_WORKERS=5        # 批量创建工作线程数

# 批处理
BATCH_SIZE=100                # 批量处理大小

# 扫描间隔
JOB_SCAN_INTERVAL=30          # 作业扫描间隔（秒）
```

### 5. 启动调度器

```bash
python main.py
```

## 📝 使用示例

### 示例1：创建SQL类型指标

```sql
INSERT INTO metrics (metric_code, metric_name, metric_type, metric_sql, description)
VALUES (
    'DAILY_REVENUE',
    '每日营收统计',
    'SQL',
    'SELECT SUM(amount) as value, DATE(order_time) as calc_date 
     FROM orders 
     WHERE DATE(order_time) = CURDATE()
     GROUP BY DATE(order_time)',
    '统计每日订单总金额'
);
```

### 示例2：创建并行SQL指标

```sql
INSERT INTO metrics (metric_code, metric_name, metric_type, metric_sql, description)
VALUES (
    'MULTI_DIMENSION_STATS',
    '多维度统计',
    'PARALLEL_SQL',
    'SELECT COUNT(*) as user_count, "users" as dimension FROM users;
     SELECT COUNT(*) as order_count, "orders" as dimension FROM orders;
     SELECT SUM(amount) as total_amount, "revenue" as dimension FROM orders',
    '并行执行多个统计查询'
);
```

### 示例3：创建脚本类型指标

```python
# scripts/custom_metric.py
import os
import json

# 获取参数
metric_code = os.environ.get('METRIC_CODE')
date_param = os.environ.get('PARAM_DATE', '2024-01-01')

# 执行自定义逻辑
result = {
    'value': 12345,
    'dimension': 'custom',
    'calc_date': date_param
}

# 输出JSON结果
print(json.dumps([result]))
```

```sql
INSERT INTO metrics (metric_code, metric_name, metric_type, script_path, params)
VALUES (
    'CUSTOM_METRIC',
    '自定义指标',
    'SCRIPT',
    '/path/to/scripts/custom_metric.py',
    '{"date": "2024-01-01"}'
);
```

### 示例4：创建调度配置

```sql
-- 每小时执行
INSERT INTO schedules (schedule_code, schedule_name, schedule_type, cron_expression)
VALUES ('HOURLY', '每小时执行', 'CRON', '0 0 * * * ?');

-- 每5分钟执行
INSERT INTO schedules (schedule_code, schedule_name, schedule_type, cron_expression)
VALUES ('EVERY_5MIN', '每5分钟执行', 'CRON', '0 */5 * * * ?');

-- 每天凌晨2点执行
INSERT INTO schedules (schedule_code, schedule_name, schedule_type, cron_expression)
VALUES ('DAILY_2AM', '每日凌晨2点执行', 'CRON', '0 0 2 * * ?');
```

### 示例5：绑定指标和调度

```sql
INSERT INTO metric_schedules (metric_id, schedule_id, max_retry_times, timeout_seconds, priority)
SELECT m.id, s.id, 3, 1800, 10
FROM metrics m, schedules s
WHERE m.metric_code = 'DAILY_REVENUE' AND s.schedule_code = 'DAILY_2AM';
```

## 📈 性能优化建议

### 1. 并发配置优化

根据服务器资源调整并发参数：

```bash
# 4核8G服务器建议配置
MAX_CONCURRENT_JOBS=30
EXECUTOR_WORKERS=15
JOB_SCAN_WORKERS=3
BATCH_CREATE_WORKERS=3

# 8核16G服务器建议配置
MAX_CONCURRENT_JOBS=50
EXECUTOR_WORKERS=20
JOB_SCAN_WORKERS=4
BATCH_CREATE_WORKERS=5

# 16核32G服务器建议配置
MAX_CONCURRENT_JOBS=100
EXECUTOR_WORKERS=40
JOB_SCAN_WORKERS=6
BATCH_CREATE_WORKERS=8
```

### 2. 数据库优化

- **增加连接池大小**：`MYSQL_POOL_SIZE` 应大于 `EXECUTOR_WORKERS`
- **优化查询SQL**：为指标SQL添加合适的索引
- **定期清理历史数据**：清理30天前的 `job_instances` 和 `job_logs`

```sql
-- 清理历史作业实例
DELETE FROM job_instances 
WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);

-- 清理历史日志
DELETE FROM job_logs 
WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
```

### 3. Redis优化

- 使用专用Redis实例，避免与其他服务共享
- 配置合适的最大内存和淘汰策略
- 监控Redis连接数和响应时间

### 4. 批处理优化

- 根据调度配置数量调整 `BATCH_SIZE`
- 调度配置 < 100：`BATCH_SIZE=50`
- 调度配置 100-1000：`BATCH_SIZE=100`
- 调度配置 > 1000：`BATCH_SIZE=200`

## 🔍 监控和运维

### 查看性能指标

```sql
-- 查看最近1小时的性能指标
SELECT 
    DATE_FORMAT(metric_time, '%Y-%m-%d %H:%i') as time,
    pending_jobs,
    running_jobs,
    success_jobs,
    failed_jobs,
    ROUND(avg_execution_time, 2) as avg_time,
    ROUND(throughput, 2) as throughput
FROM scheduler_metrics
WHERE metric_time >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
ORDER BY metric_time DESC;
```

### 查看作业状态

```sql
-- 统计各状态作业数
SELECT status, COUNT(*) as count
FROM job_instances
WHERE created_at >= CURDATE()
GROUP BY status;

-- 查看失败的作业
SELECT 
    job_code,
    scheduled_time,
    error_message,
    retry_count
FROM job_instances
WHERE status = 'FAILED'
AND created_at >= CURDATE()
ORDER BY scheduled_time DESC
LIMIT 20;
```

### 查看执行慢的作业

```sql
SELECT 
    job_code,
    TIMESTAMPDIFF(SECOND, actual_start_time, actual_end_time) as duration_seconds,
    actual_start_time,
    actual_end_time
FROM job_instances
WHERE status = 'SUCCESS'
AND actual_start_time IS NOT NULL
AND actual_end_time IS NOT NULL
ORDER BY duration_seconds DESC
LIMIT 20;
```

## 🔧 故障排查

### 1. 作业一直处于PENDING状态

**可能原因**：
- 达到最大并发数限制
- 分布式锁被其他实例占用
- 计划执行时间未到

**解决方法**：
```bash
# 检查运行中作业数
SELECT COUNT(*) FROM job_instances WHERE status = 'RUNNING';

# 增加最大并发数
# 修改 .env 文件中的 MAX_CONCURRENT_JOBS
```

### 2. 作业执行失败率高

**可能原因**：
- SQL语句有误
- 数据库连接不稳定
- 超时时间设置过短

**解决方法**：
```sql
-- 查看失败原因
SELECT error_message, COUNT(*) as count
FROM job_instances
WHERE status = 'FAILED'
GROUP BY error_message;

-- 调整超时时间
UPDATE metric_schedules 
SET timeout_seconds = 7200 
WHERE timeout_seconds < 3600;
```

### 3. 内存占用过高

**可能原因**：
- 并发数设置过大
- 指标结果数据量大
- 线程池未及时回收

**解决方法**：
- 降低 `MAX_CONCURRENT_JOBS` 和 `EXECUTOR_WORKERS`
- 优化指标SQL，减少返回数据量
- 增加服务器内存

## 🚀 分布式部署

### 多实例部署示例

1. 在多台服务器上部署相同的代码和配置
2. 确保所有实例连接到同一个Redis和MySQL
3. 启动多个实例

```bash
# 服务器1
python main.py

# 服务器2
python main.py

# 服务器3
python main.py
```

系统会自动通过Redis分布式锁协调作业执行，避免重复。

### 负载均衡策略

- 所有实例平等竞争作业锁
- 先到先得原则
- 某个实例失败时，其他实例会接管未完成的作业

## 📄 许可证

MIT License

## 🤝 贡献

欢迎提交Issue和Pull Request！

## 📧 联系方式

如有问题，请提交Issue。
