# 大批量指标调度执行系统

基于MySQL的高性能指标调度和执行系统，支持大规模指标的自动化计算、调度和监控。

## 🚀 特性

- **高性能调度**: 基于cron表达式的灵活调度，支持优先级队列
- **分布式执行**: 多节点分布式执行，支持水平扩展
- **多数据源支持**: 支持MySQL、PostgreSQL、ClickHouse等多种数据源
- **实时监控**: 集成Prometheus监控和告警系统
- **REST API**: 完整的REST API接口，支持所有管理功能
- **容错机制**: 自动重试、超时处理、故障恢复
- **可视化管理**: 提供完整的Web管理界面
- **日志审计**: 结构化日志记录，支持任务执行追踪

## 📋 系统架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Web UI/API    │    │   Scheduler     │    │   Executor      │
│                 │    │                 │    │                 │
│ - 指标管理      │    │ - 任务调度      │    │ - SQL执行       │
│ - 调度配置      │◄──►│ - 优先级队列    │◄──►│ - 结果处理      │
│ - 监控面板      │    │ - 状态管理      │    │ - 错误处理      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 │
                    ┌─────────────────┐
                    │     MySQL       │
                    │                 │
                    │ - 指标定义      │
                    │ - 调度配置      │
                    │ - 执行记录      │
                    │ - 系统配置      │
                    └─────────────────┘
```

## 🛠️ 安装部署

### 环境要求

- Python 3.8+
- MySQL 5.7+
- Redis 6.0+ (可选，用于缓存)

### 快速开始

1. **克隆项目**
```bash
git clone <repository-url>
cd metric-scheduler
```

2. **安装依赖**
```bash
pip install -r requirements.txt
```

3. **配置环境**
```bash
cp .env.example .env
# 编辑 .env 文件，配置数据库连接等信息
```

4. **初始化数据库**
```bash
mysql -u root -p < database/schema.sql
```

5. **启动系统**

启动API服务器:
```bash
python -m uvicorn src.api:app --host 0.0.0.0 --port 8000
```

启动调度器和执行器:
```bash
python src/main.py
```

## 📖 使用指南

### 1. 创建数据源

```python
# 通过API创建数据源
import httpx

async with httpx.AsyncClient() as client:
    response = await client.post("http://localhost:8000/datasources", json={
        "name": "my_database",
        "type": "mysql",
        "host": "localhost",
        "port": 3306,
        "database_name": "analytics",
        "username": "user",
        "password": "password",
        "max_connections": 10,
        "is_active": True
    })
```

### 2. 定义指标

```python
# 创建指标定义
metric_data = {
    "name": "daily_active_users",
    "description": "每日活跃用户数",
    "sql_template": """
        SELECT 
            DATE(login_time) as date,
            COUNT(DISTINCT user_id) as active_users
        FROM user_sessions 
        WHERE login_time >= '{{ start_date }}' 
          AND login_time < '{{ end_date }}'
        GROUP BY DATE(login_time)
    """,
    "data_source": "my_database",
    "category": "user_metrics",
    "timeout_seconds": 300,
    "retry_count": 3
}

response = await client.post("http://localhost:8000/metrics", json=metric_data)
```

### 3. 配置调度

```python
# 创建调度配置
schedule_data = {
    "metric_id": 1,
    "cron_expression": "0 2 * * *",  # 每天凌晨2点执行
    "priority": 1,
    "max_concurrent": 1,
    "is_enabled": True,
    "parameters": {
        "start_date": "{{ yesterday }}",
        "end_date": "{{ today }}"
    }
}

response = await client.post("http://localhost:8000/schedules", json=schedule_data)
```

### 4. 手动执行任务

```python
# 提交手动任务
response = await client.post("http://localhost:8000/tasks/submit", params={
    "metric_id": 1,
    "parameters": {
        "start_date": "2024-01-01",
        "end_date": "2024-01-02"
    }
})

task_id = response.json()["task_id"]
print(f"任务ID: {task_id}")
```

### 5. 监控任务状态

```python
# 查询任务状态
response = await client.get(f"http://localhost:8000/tasks/{task_id}")
task = response.json()

print(f"状态: {task['status']}")
print(f"执行时间: {task['duration_ms']}ms")
```

## 🔧 配置说明

### 环境变量配置

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| `DB_HOST` | 数据库主机 | localhost |
| `DB_PORT` | 数据库端口 | 3306 |
| `DB_USERNAME` | 数据库用户名 | root |
| `DB_PASSWORD` | 数据库密码 | |
| `DB_DATABASE` | 数据库名 | metric_scheduler |
| `SCHEDULER_ENABLED` | 是否启用调度器 | true |
| `SCHEDULER_SCAN_INTERVAL` | 调度扫描间隔(秒) | 10 |
| `EXECUTOR_MAX_WORKERS` | 执行器最大工作线程数 | 10 |
| `LOG_LEVEL` | 日志级别 | INFO |

### Cron表达式格式

系统支持标准的5位cron表达式格式：

```
* * * * *
│ │ │ │ │
│ │ │ │ └─── 星期几 (0-7, 0和7都表示周日)
│ │ │ └───── 月份 (1-12)
│ │ └─────── 日期 (1-31)
│ └───────── 小时 (0-23)
└─────────── 分钟 (0-59)
```

常用示例：
- `0 2 * * *` - 每天凌晨2点
- `*/15 * * * *` - 每15分钟
- `0 9-17 * * 1-5` - 工作日9-17点整点
- `0 0 1 * *` - 每月1号零点

## 📊 监控告警

### Prometheus指标

系统暴露以下Prometheus指标：

- `scheduler_tasks_total` - 任务总数（按状态分类）
- `scheduler_task_duration_seconds` - 任务执行时长
- `scheduler_task_queue_size` - 队列中任务数量
- `scheduler_worker_nodes` - 工作节点数量（按状态分类）
- `scheduler_error_rate` - 错误率

### 健康检查

```bash
# 检查系统健康状态
curl http://localhost:8000/health

# 获取Prometheus指标
curl http://localhost:8000/metrics
```

### 告警规则

系统内置以下告警规则：

- 错误率超过20%
- 没有在线工作节点
- 任务队列积压超过1000个
- 数据库连接异常

## 🔍 故障排查

### 常见问题

1. **任务执行失败**
   - 检查SQL语法是否正确
   - 验证数据源连接配置
   - 查看任务执行日志

2. **调度器不工作**
   - 确认调度器已启用
   - 检查cron表达式格式
   - 验证调度配置是否启用

3. **性能问题**
   - 调整执行器工作线程数
   - 优化SQL查询性能
   - 检查数据库连接池配置

### 日志查看

```bash
# 查看主日志
tail -f logs/scheduler.log

# 查看错误日志
tail -f logs/error.log

# 查看任务执行日志
tail -f logs/tasks.log
```

## 🧪 开发测试

### 运行示例

```bash
# 基本使用示例
python examples/basic_usage.py

# API客户端示例
python examples/api_client_example.py
```

### 单元测试

```bash
# 运行所有测试
pytest

# 运行特定测试
pytest tests/test_scheduler.py

# 生成覆盖率报告
pytest --cov=src tests/
```

## 📚 API文档

启动服务后，访问以下地址查看API文档：

- Swagger UI: http://localhost:8000/docs
- ReDoc: http://localhost:8000/redoc

### 主要API端点

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| GET | `/status` | 系统状态 |
| POST | `/metrics` | 创建指标 |
| GET | `/metrics` | 获取指标列表 |
| POST | `/schedules` | 创建调度 |
| GET | `/tasks` | 获取任务列表 |
| POST | `/tasks/submit` | 提交任务 |
| GET | `/statistics/tasks` | 任务统计 |

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🆘 支持

如果您遇到问题或有疑问，请：

1. 查看[常见问题](#故障排查)
2. 搜索现有的[Issues](../../issues)
3. 创建新的[Issue](../../issues/new)

## 🗺️ 路线图

- [ ] Web管理界面
- [ ] 更多数据源支持（InfluxDB、Elasticsearch等）
- [ ] 任务依赖管理
- [ ] 数据质量检查
- [ ] 机器学习异常检测
- [ ] 多租户支持

---

**注意**: 这是一个生产就绪的系统，但在部署到生产环境前，请确保：
1. 修改默认密码和密钥
2. 配置适当的资源限制
3. 设置监控和告警
4. 定期备份数据库