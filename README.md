# 并行指标调度系统

一个支持并行执行的指标调度系统，可以高效地管理和执行多个指标计算任务。

## 功能特性

- ✅ **并行执行**：支持多个指标同时执行，提高效率
- ✅ **依赖管理**：支持指标之间的依赖关系，自动处理执行顺序
- ✅ **优先级调度**：支持为指标设置优先级，优先执行重要任务
- ✅ **超时控制**：支持为每个指标设置超时时间，防止任务卡死
- ✅ **错误处理**：完善的错误处理和结果收集机制
- ✅ **状态跟踪**：实时跟踪每个指标的执行状态

## 项目结构

```
.
├── metric.py          # 指标定义模块
├── scheduler.py       # 并行调度器实现
├── example.py         # 使用示例
├── test_scheduler.py  # 单元测试
└── README.md          # 项目文档
```

## 快速开始

### 基本使用

```python
from metric import Metric
from scheduler import ParallelMetricScheduler

# 定义指标函数
def calculate_metric():
    # 执行指标计算
    return {"value": 100}

# 创建指标
metric = Metric(
    id="metric1",
    name="指标1",
    func=calculate_metric,
    priority=1
)

# 创建调度器（最大4个并行线程）
scheduler = ParallelMetricScheduler(max_workers=4)

# 执行调度
results = scheduler.schedule([metric])

# 查看结果
for metric_id, result in results.items():
    print(f"{metric_id}: {result.status}, 结果: {result.result}")

# 关闭调度器
scheduler.shutdown()
```

### 带依赖的指标

```python
# 定义多个指标，其中metric3依赖metric1和metric2
metrics = [
    Metric(id="metric1", name="指标1", func=func1),
    Metric(id="metric2", name="指标2", func=func2),
    Metric(
        id="metric3",
        name="指标3",
        func=func3,
        dependencies=["metric1", "metric2"]  # 依赖前两个指标
    ),
]

scheduler = ParallelMetricScheduler(max_workers=4)
results = scheduler.schedule(metrics)
```

### 带超时的指标

```python
metric = Metric(
    id="metric1",
    name="指标1",
    func=slow_function,
    timeout=5.0  # 5秒超时
)
```

### 运行示例

```bash
# 运行示例程序
python example.py

# 运行测试
python test_scheduler.py
```

## API 文档

### Metric 类

指标定义类，用于描述一个需要执行的指标任务。

**参数：**
- `id` (str): 指标唯一标识
- `name` (str): 指标名称
- `func` (Callable): 指标计算函数
- `priority` (int, 可选): 优先级，数字越大优先级越高，默认0
- `timeout` (float, 可选): 超时时间（秒），默认None
- `dependencies` (list[str], 可选): 依赖的其他指标ID列表，默认[]

### ParallelMetricScheduler 类

并行指标调度器，负责管理和执行指标任务。

**初始化参数：**
- `max_workers` (int): 最大并行工作线程数，默认4
- `timeout` (float, 可选): 全局超时时间（秒），默认None

**主要方法：**
- `schedule(metrics: List[Metric]) -> Dict[str, MetricResult]`: 并行调度执行多个指标
- `schedule_async(metrics: List[Metric]) -> Dict[str, MetricResult]`: 异步并行调度（使用asyncio）
- `shutdown(wait: bool = True)`: 关闭调度器

### MetricResult 类

指标执行结果。

**属性：**
- `metric_id` (str): 指标ID
- `status` (MetricStatus): 执行状态（PENDING/RUNNING/COMPLETED/FAILED/CANCELLED）
- `result` (Any): 执行结果
- `error` (str, 可选): 错误信息
- `start_time` (datetime, 可选): 开始时间
- `end_time` (datetime, 可选): 结束时间
- `duration` (float, 可选): 执行耗时（秒）

## 使用场景

- 数据分析和报表生成
- 批量指标计算
- 监控系统指标收集
- ETL 数据处理流程
- 性能测试和基准测试

## 注意事项

1. 确保指标函数是线程安全的
2. 合理设置 `max_workers`，避免过多线程导致资源竞争
3. 对于I/O密集型任务，可以适当增加并行数
4. 对于CPU密集型任务，建议并行数不超过CPU核心数
5. 注意处理依赖关系，避免循环依赖

## 许可证

MIT License
