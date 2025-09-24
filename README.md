# Enterprise Metric Scheduling System

一个高性能的企业级指标调度系统，专为防止数据库压力过载而设计。支持多数据库、分布式调度、智能限流和全面监控。

## 核心特性

### 🛡️ 数据库保护
- **智能限流**: 多层限流机制（令牌桶、滑动窗口、分布式限流）
- **连接池管理**: 高效的数据库连接池，支持 MySQL 和 PostgreSQL
- **并发控制**: 精确控制并发查询数量，防止数据库过载
- **自动重试**: 带指数退避的失败重试机制

### ⚡ 高性能调度
- **分布式调度**: 基于 Redis 的分布式任务调度，支持多节点部署
- **优先级队列**: 根据任务优先级智能调度执行顺序
- **批量执行**: 支持批量任务执行，提高效率
- **Cron 表达式**: 灵活的定时任务配置

### 📊 监控与告警
- **Prometheus 集成**: 完整的 Prometheus 指标导出
- **实时监控**: CPU、内存、连接池、队列等关键指标监控
- **智能告警**: 基于阈值的告警系统，支持多种告警渠道
- **执行追踪**: 详细的任务执行历史和性能分析

### 🔧 易于使用
- **配置灵活**: 支持环境变量和配置文件
- **动态管理**: 运行时动态添加/删除任务和数据库
- **异步架构**: 基于 asyncio 的全异步实现
- **扩展性强**: 插件式架构，易于扩展

## 快速开始

### 安装

```bash
# 克隆仓库
git clone https://github.com/your-org/metric-scheduler.git
cd metric-scheduler

# 安装依赖
pip install -r requirements.txt

# 或者使用 setup.py
python setup.py install
```

### 基本使用

```python
import asyncio
from metric_scheduler.config import Config, MetricConfig
from metric_scheduler.app import MetricSchedulerApp

async def main():
    # 创建配置
    config = Config()
    
    # 定义指标
    config.metrics = [
        MetricConfig(
            name="daily_active_users",
            query="SELECT COUNT(DISTINCT user_id) FROM users WHERE last_login > NOW() - INTERVAL 1 DAY",
            schedule="0 2 * * *",  # 每天凌晨2点执行
            priority=8,
            timeout=60
        )
    ]
    
    # 创建并启动应用
    app = MetricSchedulerApp(config)
    await app.initialize()
    await app.start()

asyncio.run(main())
```

## 架构设计

### 系统架构

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Metric        │     │   Metric        │     │   Metric        │
│   Scheduler     │     │   Scheduler     │     │   Scheduler     │
│   Node 1        │     │   Node 2        │     │   Node 3        │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         └───────────────────────┴───────────────────────┘
                                 │
                         ┌───────▼────────┐
                         │     Redis      │
                         │  (Job Store)   │
                         └───────┬────────┘
                                 │
         ┌───────────────────────┼───────────────────────┐
         │                       │                       │
    ┌────▼─────┐          ┌─────▼─────┐          ┌─────▼─────┐
    │ Database │          │ Database  │          │ Database  │
    │ Pool 1   │          │ Pool 2    │          │ Pool 3    │
    └──────────┘          └───────────┘          └───────────┘
```

### 核心组件

1. **Rate Limiter (限流器)**
   - 令牌桶算法：控制每秒查询数
   - 滑动窗口：控制每分钟查询数
   - 分布式限流：基于 Redis 的跨节点限流
   - 并发控制：使用信号量限制并发数

2. **Database Pool Manager (连接池管理器)**
   - 连接池化：减少连接开销
   - 健康检查：自动检测和恢复死连接
   - 多数据库支持：同时管理多个数据库连接池
   - 监控指标：实时连接池状态

3. **Metric Executor (指标执行器)**
   - 优先级队列：高优先级任务优先执行
   - 超时控制：防止长时间查询
   - 自动重试：失败任务自动重试
   - 结果缓存：避免重复查询

4. **Distributed Scheduler (分布式调度器)**
   - Redis JobStore：分布式任务存储
   - Cron 调度：灵活的定时任务
   - 任务去重：防止重复执行
   - 错过补偿：处理错过的任务

5. **Monitoring System (监控系统)**
   - Prometheus 指标：标准化监控指标
   - 实时告警：基于阈值的告警
   - 性能分析：执行时间统计
   - 资源监控：CPU、内存使用率

## 配置说明

### 环境变量

```bash
# 数据库配置
DB_HOST=localhost
DB_PORT=3306
DB_USERNAME=root
DB_PASSWORD=secret
DB_DATABASE=metrics
DB_POOL_SIZE=20
DB_MAX_OVERFLOW=10

# Redis 配置
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DB=0

# 限流配置
RATE_LIMIT_QPS=100          # 每秒最大查询数
RATE_LIMIT_QPM=5000         # 每分钟最大查询数
RATE_LIMIT_CONCURRENT=50    # 最大并发查询数

# 调度器配置
SCHEDULER_TIMEZONE=Asia/Shanghai
SCHEDULER_MAX_WORKERS=10

# 监控配置
ENABLE_PROMETHEUS=true
PROMETHEUS_PORT=8000
LOG_LEVEL=INFO
```

### 配置文件示例

```python
from metric_scheduler.config import Config, MetricConfig, DatabaseConfig

config = Config(
    database=DatabaseConfig(
        host="localhost",
        port=3306,
        username="metrics_user",
        password="secure_password",
        database="metrics_db",
        pool_size=30,
        max_overflow=20,
        pool_timeout=30,
        pool_recycle=3600
    ),
    rate_limiter={
        "max_queries_per_second": 100,
        "max_queries_per_minute": 5000,
        "max_concurrent_queries": 50,
        "burst_multiplier": 1.5
    },
    scheduler={
        "timezone": "Asia/Shanghai",
        "max_instances": 3,
        "misfire_grace_time": 60,
        "max_workers": 10
    },
    monitoring={
        "enable_prometheus": True,
        "prometheus_port": 8000,
        "log_level": "INFO",
        "alert_thresholds": {
            "query_error_rate": 0.1,      # 10% 错误率
            "query_latency_p99": 5.0,     # 5秒 P99 延迟
            "db_connection_usage": 0.8,    # 80% 连接池使用率
            "queue_backlog": 1000         # 1000个待处理任务
        }
    }
)
```

## 高级用法

### 多数据库支持

```python
# 添加多个数据库连接池
await app.add_database_pool(
    "analytics_db",
    DatabaseConfig(host="analytics.example.com", ...),
    "mysql"
)

await app.add_database_pool(
    "reporting_db", 
    DatabaseConfig(host="reporting.example.com", ...),
    "postgresql"
)

# 为不同数据库调度指标
await app.schedule_metric(analytics_metric, "analytics_db")
await app.schedule_metric(reporting_metric, "reporting_db")
```

### 自定义告警处理

```python
from metric_scheduler.monitoring import AlertHandler, Alert

class EmailAlertHandler(AlertHandler):
    async def handle_alert(self, alert: Alert):
        if alert.severity in ['error', 'critical']:
            await send_email(
                to="ops@example.com",
                subject=f"[{alert.severity.upper()}] {alert.alert_type}",
                body=alert.message
            )

# 注册告警处理器
app.monitor.add_alert_handler(EmailAlertHandler().handle_alert)
```

### 动态任务调度

```python
# 根据系统负载动态调整任务
async def adaptive_scheduler(result: MetricResult):
    if result.metric_name == "system_load":
        load = result.data[0]['load_average']
        
        if load < 0.5:
            # 系统负载低，可以执行更多任务
            await app.execute_metric_now(heavy_metric)
        elif load > 0.8:
            # 系统负载高，暂停一些任务
            app.scheduler.pause_metric("low_priority_metric")

app.scheduler.add_result_handler(adaptive_scheduler)
```

## 监控指标

### Prometheus 指标

- `metric_executions_total`: 指标执行总数
- `metric_execution_duration_seconds`: 指标执行时间
- `database_connections`: 数据库连接数
- `rate_limit_rejections_total`: 限流拒绝次数
- `metric_queue_size`: 队列大小
- `system_cpu_percent`: 系统 CPU 使用率
- `system_memory_percent`: 系统内存使用率

### 告警类型

1. **query_error_rate**: 查询错误率过高
2. **query_latency_p99**: 查询延迟过高
3. **db_connection_usage**: 连接池使用率过高
4. **queue_backlog**: 队列积压过多

## 性能优化建议

1. **合理设置限流参数**
   - 根据数据库性能设置 QPS 限制
   - 预留 20% 的性能余量

2. **优化连接池配置**
   - pool_size: 通常设置为数据库最大连接数的 20-30%
   - max_overflow: 设置为 pool_size 的 50-100%

3. **使用优先级队列**
   - 关键业务指标设置高优先级
   - 分析类指标设置低优先级

4. **监控和调优**
   - 定期检查 Prometheus 指标
   - 根据告警调整配置参数

## 故障处理

### 常见问题

1. **数据库连接池耗尽**
   - 增加 pool_size 和 max_overflow
   - 优化查询，减少执行时间
   - 检查是否有连接泄露

2. **任务执行超时**
   - 增加 timeout 配置
   - 优化 SQL 查询
   - 考虑分批处理

3. **Redis 连接失败**
   - 检查 Redis 服务状态
   - 验证连接配置
   - 检查防火墙规则

## 贡献指南

欢迎贡献代码！请遵循以下步骤：

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'Add amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建 Pull Request

## 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件