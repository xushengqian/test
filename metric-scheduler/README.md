# 大批量指标调度执行系统

基于MySQL实现的大规模指标调度和执行系统，支持SQL、存储过程和脚本等多种指标类型，具备分布式调度、错误重试、超时控制等企业级特性。

## 系统特性

- **多种指标类型支持**：SQL查询、存储过程、Python脚本
- **灵活的调度配置**：支持Cron表达式、固定频率、一次性执行
- **分布式调度**：基于Redis的分布式锁，支持多实例部署
- **高并发执行**：线程池并发执行，可配置最大并发数
- **错误处理机制**：自动重试、超时控制、详细日志记录
- **性能监控**：实时监控作业状态、执行性能统计
- **结果存储**：指标计算结果持久化存储，支持多维度查询

## 系统架构

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   调度器    │────▶│   执行器    │────▶│  结果存储   │
└─────────────┘     └─────────────┘     └─────────────┘
       │                    │                    │
       ▼                    ▼                    ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│    MySQL    │     │    Redis    │     │   监控器    │
└─────────────┘     └─────────────┘     └─────────────┘
```

## 快速开始

### 1. 环境要求

- Python 3.8+
- MySQL 5.7+
- Redis 6.0+ (可选，用于分布式部署)

### 2. 安装依赖

```bash
cd /workspace/metric-scheduler
pip install -r requirements.txt
```

### 3. 配置数据库

创建数据库和表结构：

```bash
mysql -u root -p < scripts/create_database.sql
```

### 4. 配置环境变量

复制并修改配置文件：

```bash
cp .env.example .env
# 编辑 .env 文件，设置数据库连接等配置
```

### 5. 使用命令行工具

查看帮助信息：

```bash
python main.py --help
```

## 使用指南

### 管理指标

列出所有指标：
```bash
python main.py metric list
```

添加新指标：
```bash
python main.py metric add
```

### 管理调度

列出所有调度：
```bash
python main.py schedule list
```

绑定指标和调度：
```bash
python main.py schedule bind --metric DAILY_USER_COUNT --schedule DAILY_0AM
```

### 启动调度器

```bash
python main.py start
```

### 监控作业

查看执行摘要：
```bash
python main.py monitor summary --hours 24
```

查看运行中的作业：
```bash
python main.py monitor running
```

查看失败的作业：
```bash
python main.py monitor failed --hours 24
```

查看指标性能统计：
```bash
python main.py monitor performance --days 7
```

查看作业日志：
```bash
python main.py monitor logs <job_code>
```

查看指标结果：
```bash
python main.py monitor results <metric_code> --hours 24
```

## 指标类型说明

### 1. SQL类型指标

直接执行SQL查询，支持参数替换：

```sql
SELECT COUNT(DISTINCT user_id) as value, 
       DATE(login_time) as calc_date 
FROM user_login_logs 
WHERE DATE(login_time) = '${date}'
GROUP BY DATE(login_time)
```

### 2. 存储过程类型

调用数据库存储过程：

```sql
CALL calculate_daily_metrics('${date}')
```

### 3. 脚本类型

执行Python脚本，通过环境变量传递参数，输出JSON格式结果：

```python
# 从环境变量获取参数
job_instance_id = os.environ.get('JOB_INSTANCE_ID')
metric_code = os.environ.get('METRIC_CODE')
date_param = os.environ.get('PARAM_DATE')

# 计算指标...

# 输出JSON结果
results = [{"value": 100, "dimension": "A"}]
print(json.dumps(results))
```

## 调度类型说明

### 1. CRON表达式

使用标准的Cron表达式定义执行时间：

- `0 0 0 * * ?` - 每天凌晨0点执行
- `0 0 * * * ?` - 每小时整点执行
- `0 */5 * * * ?` - 每5分钟执行

### 2. 固定频率

按固定时间间隔执行，单位为秒。

### 3. 一次性执行

在指定时间执行一次。

## 高级特性

### 分布式部署

配置Redis后，可以部署多个调度器实例，通过分布式锁确保作业不会重复执行。

### 并发控制

通过 `MAX_CONCURRENT_JOBS` 配置控制最大并发作业数。

### 错误重试

- 默认重试3次
- 可为每个指标调度单独配置重试次数
- 重试间隔可配置

### 超时控制

- 默认超时时间3600秒
- 可为每个指标调度单独配置超时时间
- 自动检测并标记超时作业

## 性能优化建议

1. **合理设置并发数**：根据系统资源调整 `MAX_CONCURRENT_JOBS`
2. **优化SQL查询**：为指标SQL添加合适的索引
3. **使用连接池**：系统已内置MySQL连接池
4. **定期清理历史数据**：定期清理 `job_instances` 和 `job_logs` 表
5. **监控系统资源**：关注CPU、内存、数据库连接数等指标

## 故障排查

1. **查看系统日志**：`logs/metric_scheduler.log`
2. **查看作业日志**：使用 `monitor logs` 命令
3. **检查数据库连接**：确认数据库配置正确
4. **检查Redis连接**：如使用分布式模式，确认Redis可用

## 扩展开发

### 添加新的指标类型

1. 在 `executor.py` 中添加新的执行方法
2. 更新 `execute_job` 方法的分发逻辑
3. 在数据库中添加新的指标类型

### 添加新的调度类型

1. 在 `scheduler.py` 中添加新的调度计算逻辑
2. 更新 `_calculate_next_run_time` 方法

## 注意事项

1. 确保数据库有足够的连接数配置
2. 定期备份 `metric_results` 表数据
3. 监控磁盘空间，避免日志文件过大
4. 在生产环境使用前进行充分测试