# 企业指标调度系统

多家企业指标同时执行调度，防止数据库被压力打垮的高性能调度系统。

## 🚀 核心特性

### 数据库保护机制
- **连接池管理**: 智能连接池配置，防止连接泄漏
- **限流控制**: 基于令牌桶算法的请求限流
- **熔断保护**: 自动熔断异常服务，保护系统稳定性
- **并发控制**: 精确控制同时执行的任务数量

### 任务调度系统
- **异步任务队列**: 基于Celery的分布式任务队列
- **优先级调度**: 支持按优先级和频率调度指标
- **批量执行**: 高效的批量指标计算
- **重试机制**: 智能重试策略，提高成功率

### 监控告警
- **实时监控**: Prometheus指标收集
- **智能告警**: 多维度告警规则和通知
- **性能监控**: 系统资源和任务执行监控
- **健康检查**: 全面的系统健康状态检查

### 高可用设计
- **分布式架构**: 支持多实例部署
- **故障恢复**: 自动故障检测和恢复
- **数据持久化**: 完整的执行历史记录
- **配置管理**: 灵活的环境配置

## 🏗️ 系统架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Web API       │    │   Task Queue    │    │   Database      │
│   (FastAPI)     │◄──►│   (Celery)      │◄──►│   (PostgreSQL)  │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Monitoring    │    │   Redis Cache   │    │   Prometheus    │
│   & Alerts      │    │   & Queue       │    │   Metrics       │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🛠️ 技术栈

- **Web框架**: FastAPI
- **数据库**: PostgreSQL + SQLAlchemy
- **缓存/队列**: Redis + Celery
- **监控**: Prometheus + 自定义告警
- **容器化**: Docker + Docker Compose

## 📦 快速开始

### 1. 环境准备

```bash
# 克隆项目
git clone <repository-url>
cd metrics-scheduler

# 复制环境配置
cp .env.example .env
```

### 2. 配置环境变量

编辑 `.env` 文件，配置数据库和Redis连接信息：

```env
DATABASE_URL=postgresql://postgres:password@localhost:5432/metrics_db
REDIS_URL=redis://localhost:6379/0
MAX_CONCURRENT_TASKS=5
RATE_LIMIT_PER_SECOND=10
```

### 3. 使用Docker Compose启动

```bash
# 启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f app
```

### 4. 访问服务

- **API文档**: http://localhost:8000/docs
- **系统监控**: http://localhost:8000/api/v1/monitoring/dashboard
- **Celery监控**: http://localhost:5555
- **健康检查**: http://localhost:8000/health

## 📊 API接口

### 指标管理 API

```bash
# 获取企业列表
GET /api/v1/metrics/companies

# 获取指标定义
GET /api/v1/metrics/definitions

# 执行单个指标
POST /api/v1/metrics/execute
{
  "company_id": 1,
  "metric_id": 1,
  "execution_params": {}
}

# 批量执行指标
POST /api/v1/metrics/batch-execute
{
  "company_ids": [1, 2, 3],
  "metric_ids": [1, 2],
  "batch_params": {}
}

# 查询执行记录
GET /api/v1/metrics/executions?company_id=1&status=completed
```

### 系统监控 API

```bash
# 系统健康状态
GET /api/v1/system/health

# 系统统计信息
GET /api/v1/system/stats

# 数据库状态
GET /api/v1/system/database/status

# Redis状态
GET /api/v1/system/redis/status
```

### 监控告警 API

```bash
# 获取告警列表
GET /api/v1/monitoring/alerts

# 告警统计
GET /api/v1/monitoring/alerts/stats

# 监控仪表板
GET /api/v1/monitoring/dashboard

# Prometheus指标
GET /api/v1/monitoring/metrics
```

## 🔧 配置说明

### 数据库保护配置

```python
# 连接池配置
DATABASE_POOL_SIZE=10          # 连接池大小
DATABASE_MAX_OVERFLOW=20       # 最大溢出连接数
DATABASE_POOL_TIMEOUT=30       # 连接超时时间

# 限流配置
RATE_LIMIT_PER_SECOND=10       # 每秒最大请求数
RATE_LIMIT_BURST=20            # 突发请求数

# 熔断器配置
CIRCUIT_BREAKER_FAILURE_THRESHOLD=5  # 失败阈值
CIRCUIT_BREAKER_TIMEOUT=60           # 熔断超时时间
```

### 任务调度配置

```python
# 并发控制
MAX_CONCURRENT_TASKS=5         # 最大并发任务数
TASK_TIMEOUT=300              # 任务超时时间

# 重试配置
MAX_RETRY_ATTEMPTS=3          # 最大重试次数
RETRY_DELAY=60               # 重试延迟时间
```

## 📈 监控指标

### 系统指标
- CPU使用率
- 内存使用率
- 磁盘使用率
- 网络IO

### 数据库指标
- 连接池状态
- 查询执行时间
- 连接数统计

### 任务指标
- 任务执行时间
- 任务成功率
- 队列长度
- 并发任务数

### 业务指标
- 指标执行统计
- 企业指标分布
- 执行失败率

## 🚨 告警规则

系统内置多种告警规则：

- **资源告警**: CPU/内存/磁盘使用率过高
- **数据库告警**: 连接池耗尽、健康检查失败
- **任务告警**: 执行失败率过高、队列积压
- **性能告警**: 响应时间过长、熔断器开启

## 🔍 故障排查

### 常见问题

1. **数据库连接失败**
   ```bash
   # 检查数据库状态
   curl http://localhost:8000/api/v1/system/database/status
   
   # 查看连接池状态
   curl http://localhost:8000/api/v1/system/stats
   ```

2. **任务执行失败**
   ```bash
   # 查看任务统计
   curl http://localhost:8000/api/v1/system/tasks/stats
   
   # 查看执行记录
   curl http://localhost:8000/api/v1/metrics/executions?status=failed
   ```

3. **系统性能问题**
   ```bash
   # 查看系统资源
   curl http://localhost:8000/api/v1/monitoring/dashboard
   
   # 查看告警信息
   curl http://localhost:8000/api/v1/monitoring/alerts
   ```

### 日志查看

```bash
# 应用日志
docker-compose logs -f app

# Worker日志
docker-compose logs -f worker

# 数据库日志
docker-compose logs -f db
```

## 🚀 部署指南

### 生产环境部署

1. **环境配置**
   ```bash
   # 生产环境变量
   export DATABASE_URL="postgresql://user:pass@prod-db:5432/metrics_db"
   export REDIS_URL="redis://prod-redis:6379/0"
   export LOG_LEVEL="WARNING"
   ```

2. **扩容配置**
   ```yaml
   # docker-compose.prod.yml
   worker:
     deploy:
       replicas: 4
     command: celery -A app.celery_app worker --concurrency=8
   ```

3. **监控配置**
   ```bash
   # 启用Prometheus监控
   export METRICS_PORT=9090
   
   # 配置告警通知
   export ALERT_WEBHOOK_URL="https://your-webhook-url"
   ```

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 📞 支持

如有问题或建议，请：

1. 查看 [文档](docs/)
2. 提交 [Issue](issues/)
3. 联系维护团队

---

**企业指标调度系统** - 让指标计算更安全、更高效！