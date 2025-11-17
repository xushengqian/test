# 并行指标调度系统

一个支持并行执行、优先级调度和依赖管理的指标调度系统。

## 功能特性

- ✅ **并行执行**：支持多线程并行执行多个指标任务
- ✅ **优先级调度**：支持基于优先级的任务调度
- ✅ **依赖管理**：支持指标之间的依赖关系，自动处理依赖顺序
- ✅ **结果追踪**：完整的执行结果和状态追踪
- ✅ **超时控制**：支持任务超时设置
- ✅ **线程安全**：使用线程锁保证并发安全

## 项目结构

```
.
├── metric.py          # 指标定义和任务类
├── scheduler.py       # 并行指标调度器核心实现
├── example.py         # 使用示例
├── requirements.txt   # 依赖包（本项目主要使用标准库）
└── README.md          # 项目文档
```

## 快速开始

### 1. 创建指标任务

```python
from metric import MetricTask

def my_metric():
    # 执行指标计算逻辑
    return {"value": 100}

task = MetricTask(
    metric_id="metric_1",
    name="我的指标",
    execute_func=my_metric,
    priority=5,  # 优先级（可选）
    dependencies=["metric_0"]  # 依赖的指标ID列表（可选）
)
```

### 2. 使用调度器

```python
from scheduler import ParallelMetricScheduler

# 创建调度器，设置最大并行数为4
scheduler = ParallelMetricScheduler(max_workers=4, enable_priority=True)

# 提交任务
scheduler.submit(task)

# 或者批量提交
scheduler.submit_batch([task1, task2, task3])

# 等待所有任务完成
results = scheduler.wait_all()

# 获取结果
result = scheduler.get_result("metric_1")
print(result.status)  # MetricStatus.COMPLETED
print(result.result)   # 指标计算结果
print(result.execution_time)  # 执行耗时

# 关闭调度器
scheduler.shutdown()
```

### 3. 运行示例

```bash
python example.py
```

## 核心类说明

### MetricTask

指标任务定义类，包含以下属性：

- `metric_id`: 指标唯一标识符
- `name`: 指标名称
- `execute_func`: 执行函数（无参数，返回计算结果）
- `priority`: 优先级（数字越大优先级越高，默认0）
- `timeout`: 超时时间（秒，可选）
- `dependencies`: 依赖的其他指标ID列表（可选）

### ParallelMetricScheduler

并行指标调度器，主要方法：

- `submit(task)`: 提交单个任务
- `submit_batch(tasks)`: 批量提交任务
- `wait_all(timeout)`: 等待所有任务完成
- `get_result(metric_id)`: 获取指定指标的结果
- `get_all_results()`: 获取所有指标的结果
- `shutdown(wait)`: 关闭调度器

### MetricResult

指标执行结果，包含：

- `metric_id`: 指标ID
- `status`: 执行状态（PENDING/RUNNING/COMPLETED/FAILED）
- `result`: 计算结果
- `error`: 错误信息（如果失败）
- `execution_time`: 执行耗时
- `start_time`: 开始时间
- `end_time`: 结束时间

## 使用场景

- 数据指标计算和聚合
- 批量数据处理任务调度
- 需要依赖关系的任务编排
- 高并发指标计算场景

## 注意事项

1. 调度器使用线程池实现并行，适合I/O密集型任务
2. 对于CPU密集型任务，建议使用进程池（可扩展实现）
3. 依赖关系会确保任务按正确顺序执行
4. 优先级仅在依赖满足时生效

## 扩展建议

- 添加任务重试机制
- 支持任务取消功能
- 添加任务进度回调
- 支持分布式调度（使用消息队列）
- 添加指标执行历史记录
- 支持动态调整并行度