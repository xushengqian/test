# 快速开始指南

## 🚀 5分钟快速体验

### 方式1: Docker Compose（推荐）

最简单的方式，一键启动所有服务：

```bash
cd /workspace/parallel-metric-scheduler

# 启动所有服务（MySQL + Redis + Scheduler）
docker-compose up -d

# 查看日志
docker-compose logs -f scheduler

# 停止服务
docker-compose down
```

**优点**：
- ✅ 无需手动安装MySQL和Redis
- ✅ 一键启动，开箱即用
- ✅ 环境隔离，不影响系统

### 方式2: 本地安装

如果你已有MySQL和Redis服务：

```bash
cd /workspace/parallel-metric-scheduler

# 1. 运行安装脚本
bash setup.sh

# 2. 编辑配置文件
vi .env
# 配置你的MySQL和Redis连接信息

# 3. 启动调度器
python main.py
```

### 方式3: 手动安装（详细步骤）

```bash
cd /workspace/parallel-metric-scheduler

# 1. 安装Python依赖
pip install -r requirements.txt

# 2. 初始化数据库
mysql -u root -p < scripts/create_database.sql
mysql -u root -p < scripts/insert_test_data.sql

# 3. 创建配置文件
cp .env.example .env
# 编辑 .env 文件，配置数据库连接

# 4. 启动调度器
python main.py
```

## 📊 验证系统运行

### 1. 查看日志

```bash
# 实时查看日志
tail -f logs/parallel_scheduler.log

# 或者直接查看控制台输出
```

你应该看到类似的输出：

```
🚀 启动并行指标调度器
配置:
  - 作业扫描工作线程: 4
  - 批量创建工作线程: 5
  - 执行器工作线程: 20
  - 最大并发作业数: 50
  - 批处理大小: 100
✓ Redis连接成功，启用分布式调度
📊 扫描 3 个激活的调度配置
✓ 创建了 5 个新作业
🔄 执行 3 个待处理作业
▶ 开始执行作业: DAILY_USER_COUNT_20241117000000_abc123
✓ 作业执行成功: DAILY_USER_COUNT_20241117000000_abc123 (耗时: 2.15秒)
```

### 2. 查询数据库

连接到MySQL，查看作业执行情况：

```sql
-- 查看所有作业状态
SELECT 
    job_code,
    status,
    scheduled_time,
    actual_start_time,
    actual_end_time
FROM job_instances
ORDER BY created_at DESC
LIMIT 10;

-- 查看指标结果
SELECT 
    m.metric_name,
    mr.metric_value,
    mr.calc_time,
    mr.dimension_key
FROM metric_results mr
JOIN metrics m ON mr.metric_id = m.id
ORDER BY mr.created_at DESC
LIMIT 10;

-- 查看性能统计
SELECT 
    DATE_FORMAT(metric_time, '%Y-%m-%d %H:%i') as time,
    pending_jobs,
    running_jobs,
    success_jobs,
    failed_jobs,
    ROUND(avg_execution_time, 2) as avg_time
FROM scheduler_metrics
ORDER BY metric_time DESC
LIMIT 10;
```

## 🎯 创建你的第一个指标

### 1. 创建一个简单的SQL指标

```sql
USE metric_scheduler;

-- 插入指标定义
INSERT INTO metrics (
    metric_code, 
    metric_name, 
    metric_type, 
    metric_sql, 
    description
) VALUES (
    'MY_FIRST_METRIC',
    '我的第一个指标',
    'SQL',
    'SELECT COUNT(*) as value, NOW() as calc_time FROM metrics',
    '统计指标表的记录数'
);

-- 创建每分钟执行的调度
INSERT INTO schedules (
    schedule_code,
    schedule_name,
    schedule_type,
    cron_expression
) VALUES (
    'EVERY_MINUTE',
    '每分钟执行',
    'CRON',
    '0 * * * * ?'
);

-- 绑定指标和调度
INSERT INTO metric_schedules (
    metric_id,
    schedule_id,
    max_retry_times,
    timeout_seconds,
    priority
)
SELECT 
    m.id,
    s.id,
    3,
    300,
    5
FROM metrics m, schedules s
WHERE m.metric_code = 'MY_FIRST_METRIC'
AND s.schedule_code = 'EVERY_MINUTE';
```

等待1分钟后，你的指标就会被自动执行！

### 2. 查看执行结果

```sql
-- 查看作业执行情况
SELECT 
    ji.job_code,
    ji.status,
    ji.scheduled_time,
    ji.actual_start_time,
    ji.actual_end_time,
    TIMESTAMPDIFF(SECOND, ji.actual_start_time, ji.actual_end_time) as duration
FROM job_instances ji
JOIN metric_schedules ms ON ji.metric_schedule_id = ms.id
JOIN metrics m ON ms.metric_id = m.id
WHERE m.metric_code = 'MY_FIRST_METRIC'
ORDER BY ji.created_at DESC
LIMIT 5;

-- 查看指标结果
SELECT 
    metric_value,
    calc_time,
    dimension_key,
    metric_json
FROM metric_results mr
JOIN metrics m ON mr.metric_id = m.id
WHERE m.metric_code = 'MY_FIRST_METRIC'
ORDER BY mr.created_at DESC
LIMIT 5;
```

## 🎓 下一步学习

1. **了解更多指标类型**
   - 阅读 `README.md` 中的"指标类型说明"
   - 查看 `scripts/example_metric.py` 脚本示例

2. **优化性能配置**
   - 阅读 `PERFORMANCE.md` 了解性能优化建议
   - 根据你的服务器资源调整并发参数

3. **分布式部署**
   - 了解如何部署多个调度器实例
   - 查看 `README.md` 中的"分布式部署"章节

4. **监控和运维**
   - 学习如何监控系统性能
   - 掌握常见问题的排查方法

## 📖 完整文档

- **[README.md](parallel-metric-scheduler/README.md)** - 完整的使用文档
- **[PERFORMANCE.md](parallel-metric-scheduler/PERFORMANCE.md)** - 性能测试报告
- **[IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)** - 实现总结

## ❓ 常见问题

### Q1: 为什么作业一直是PENDING状态？

**A**: 检查以下几点：
1. 确认调度时间是否已到：`scheduled_time <= NOW()`
2. 检查是否达到最大并发限制
3. 查看日志文件是否有错误信息

### Q2: 如何提高系统吞吐量？

**A**: 调整以下配置：
```bash
MAX_CONCURRENT_JOBS=100      # 增加最大并发数
EXECUTOR_WORKERS=40          # 增加执行线程数
MYSQL_POOL_SIZE=50          # 增加数据库连接池
```

### Q3: Redis连接失败怎么办？

**A**: 系统会自动降级为单机模式，不影响核心功能。如果需要分布式部署，请检查Redis配置。

### Q4: 如何停止调度器？

**A**: 
- Docker: `docker-compose down`
- 本地运行: 按 `Ctrl+C`

系统会等待运行中的作业完成后优雅退出。

## 🎉 开始体验吧！

选择一种方式启动系统，创建你的第一个指标，体验并行调度的强大性能！

有问题？查看完整文档或提交Issue。
