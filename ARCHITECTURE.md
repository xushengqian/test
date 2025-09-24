# 企业指标调度系统架构文档

## 系统概述

企业指标调度系统是一个高性能、高可用的分布式调度系统，专门用于处理多家企业的指标计算任务，通过多层保护机制防止数据库被压力打垮。

## 核心架构

### 1. 分层架构

```
┌─────────────────────────────────────────────────────────────┐
│                    API Gateway Layer                        │
│                   (FastAPI + CORS)                         │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  Business Logic Layer                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │   Metrics   │  │   System    │  │ Monitoring  │        │
│  │   Service   │  │   Service   │  │   Service   │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                  Protection Layer                           │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ Rate Limiter│  │Circuit      │  │ Task        │        │
│  │             │  │Breaker      │  │Throttler    │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                   Data Access Layer                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ Connection  │  │   ORM       │  │   Cache     │        │
│  │    Pool     │  │(SQLAlchemy) │  │  (Redis)    │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                   Storage Layer                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │ PostgreSQL  │  │    Redis    │  │   Logs      │        │
│  │  Database   │  │   Queue     │  │   Files     │        │
│  └─────────────┘  └─────────────┘  └─────────────┘        │
└─────────────────────────────────────────────────────────────┘
```

### 2. 核心组件

#### 2.1 API层 (FastAPI)
- **职责**: 提供RESTful API接口
- **特性**: 
  - 自动API文档生成
  - 请求验证和序列化
  - 异常处理和日志记录
  - CORS支持

#### 2.2 任务调度层 (Celery)
- **职责**: 异步任务调度和执行
- **特性**:
  - 分布式任务队列
  - 优先级调度
  - 定时任务支持
  - 任务重试机制

#### 2.3 数据库保护层
- **连接池管理**: 限制数据库连接数，防止连接泄漏
- **限流器**: 基于令牌桶算法的请求限流
- **熔断器**: 自动熔断异常服务
- **并发控制**: 精确控制同时执行的任务数量

#### 2.4 监控告警层
- **指标收集**: Prometheus格式的指标收集
- **告警规则**: 多维度告警规则引擎
- **通知系统**: 支持多种通知方式
- **仪表板**: 实时监控仪表板

## 数据库设计

### 核心表结构

```sql
-- 企业表
companies (id, name, code, status, created_at, updated_at)

-- 指标定义表
metric_definitions (id, name, code, description, calculation_sql, 
                   frequency, priority, timeout_seconds, created_at, updated_at)

-- 企业指标关联表
company_metrics (id, company_id, metric_id, is_enabled, 
                custom_params, created_at)

-- 指标执行记录表
metric_executions (id, company_id, metric_id, execution_id, status,
                  start_time, end_time, duration_ms, result_data,
                  error_message, retry_count, created_at)

-- 系统监控表
system_metrics (id, metric_name, metric_value, tags, timestamp)
```

### 索引策略

```sql
-- 性能优化索引
CREATE INDEX idx_companies_status ON companies(status);
CREATE INDEX idx_metric_executions_status ON metric_executions(status);
CREATE INDEX idx_metric_executions_company_metric ON metric_executions(company_id, metric_id);
CREATE INDEX idx_metric_executions_created_at ON metric_executions(created_at);
CREATE INDEX idx_system_metrics_timestamp ON system_metrics(timestamp);
CREATE INDEX idx_system_metrics_name ON system_metrics(metric_name);
```

## 保护机制详解

### 1. 数据库连接池保护

```python
# 连接池配置
engine = create_async_engine(
    database_url,
    pool_size=10,           # 基础连接数
    max_overflow=20,        # 最大溢出连接数
    pool_timeout=30,        # 获取连接超时时间
    pool_recycle=3600,      # 连接回收时间
    pool_pre_ping=True      # 连接前检查
)
```

### 2. 限流机制

```python
class RateLimiter:
    def __init__(self, rate: int, burst: int):
        self.rate = rate      # 每秒令牌数
        self.burst = burst    # 桶容量
    
    async def acquire(self, tokens: int = 1) -> bool:
        # 令牌桶算法实现
        pass
```

### 3. 熔断器机制

```python
class CircuitBreaker:
    def __init__(self, failure_threshold: int, timeout: int):
        self.failure_threshold = failure_threshold  # 失败阈值
        self.timeout = timeout                     # 熔断超时时间
        self.state = CircuitBreakerState.CLOSED   # 初始状态
```

### 4. 并发控制

```python
class TaskThrottler:
    def __init__(self, max_concurrent: int, rate_per_second: int):
        self.semaphore = asyncio.Semaphore(max_concurrent)
        self.rate_limiter = RateLimiter(rate_per_second, rate_per_second * 2)
```

## 任务调度策略

### 1. 优先级调度

- **高优先级** (1-3): 每分钟执行
- **中优先级** (4-6): 每5分钟执行  
- **低优先级** (7-10): 每小时执行

### 2. 频率调度

- **daily**: 日常指标，高频执行
- **weekly**: 周度指标，定期执行
- **monthly**: 月度指标，低频执行

### 3. 批量处理

```python
# 批量执行策略
def batch_execute_metrics(company_ids: List[int], metric_ids: List[int]):
    # 创建任务矩阵
    for company_id in company_ids:
        for metric_id in metric_ids:
            execute_company_metric.delay(company_id, metric_id)
```

## 监控告警体系

### 1. 系统指标

- **资源指标**: CPU、内存、磁盘使用率
- **数据库指标**: 连接池状态、查询性能
- **任务指标**: 执行时间、成功率、队列长度
- **业务指标**: 指标执行统计、企业分布

### 2. 告警规则

```python
# 内置告警规则
alert_rules = [
    "database_pool_exhausted",      # 数据库连接池耗尽
    "high_cpu_usage",              # CPU使用率过高
    "high_memory_usage",           # 内存使用率过高
    "task_queue_backlog",          # 任务队列积压
    "high_metric_failure_rate",    # 指标执行失败率过高
    "circuit_breaker_open",        # 熔断器开启
]
```

### 3. 通知机制

- **日志通知**: 记录到系统日志
- **控制台通知**: 输出到控制台
- **Webhook通知**: 发送到外部系统
- **邮件通知**: 发送邮件告警

## 性能优化策略

### 1. 数据库优化

- **连接池管理**: 合理配置连接池大小
- **查询优化**: 使用索引和查询优化
- **批量操作**: 减少数据库往返次数
- **读写分离**: 支持读写分离架构

### 2. 缓存策略

- **Redis缓存**: 缓存热点数据
- **应用缓存**: 内存缓存常用数据
- **查询缓存**: 缓存查询结果
- **会话缓存**: 缓存用户会话

### 3. 异步处理

- **异步IO**: 使用asyncio提高并发性能
- **任务队列**: 异步处理耗时任务
- **批量处理**: 批量处理相似任务
- **并发控制**: 合理控制并发数量

## 扩展性设计

### 1. 水平扩展

- **多实例部署**: 支持多个API实例
- **Worker扩展**: 支持多个Worker节点
- **数据库分片**: 支持数据库水平分片
- **负载均衡**: 支持负载均衡器

### 2. 垂直扩展

- **资源配置**: 支持动态资源配置
- **性能调优**: 支持性能参数调优
- **缓存扩展**: 支持缓存容量扩展
- **存储扩展**: 支持存储容量扩展

## 安全性考虑

### 1. 访问控制

- **API认证**: 支持API密钥认证
- **权限控制**: 基于角色的权限控制
- **IP白名单**: 支持IP访问控制
- **请求签名**: 支持请求签名验证

### 2. 数据安全

- **数据加密**: 敏感数据加密存储
- **传输加密**: HTTPS传输加密
- **备份恢复**: 定期数据备份
- **审计日志**: 完整的操作审计

## 部署架构

### 1. 开发环境

```yaml
# docker-compose.yml
services:
  app:        # API服务
  worker:     # 任务处理器
  beat:       # 定时任务调度器
  db:         # PostgreSQL数据库
  redis:      # Redis缓存和队列
  flower:     # Celery监控
```

### 2. 生产环境

```yaml
# 生产环境建议
- 使用外部数据库服务 (RDS)
- 使用外部Redis服务 (ElastiCache)
- 使用容器编排 (Kubernetes)
- 使用负载均衡器 (ALB/NLB)
- 使用监控服务 (CloudWatch/Prometheus)
```

## 运维监控

### 1. 健康检查

- **应用健康检查**: `/health` 端点
- **就绪检查**: `/ready` 端点
- **深度健康检查**: `/api/v1/system/health`

### 2. 日志管理

- **结构化日志**: JSON格式日志
- **日志级别**: 支持多级别日志
- **日志轮转**: 自动日志轮转
- **日志聚合**: 支持日志聚合分析

### 3. 指标收集

- **Prometheus指标**: 标准Prometheus格式
- **自定义指标**: 业务相关指标
- **指标导出**: `/api/v1/monitoring/metrics`
- **仪表板**: Grafana可视化

这个架构设计确保了系统的高性能、高可用性和可扩展性，同时通过多层保护机制有效防止数据库被压力打垮。