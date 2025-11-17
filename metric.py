"""
指标定义和任务类
"""
from dataclasses import dataclass
from typing import Callable, Any, Optional
from enum import Enum
import time


class MetricStatus(Enum):
    """指标状态枚举"""
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"


@dataclass
class MetricTask:
    """指标任务定义"""
    metric_id: str
    name: str
    execute_func: Callable[[], Any]
    priority: int = 0  # 优先级，数字越大优先级越高
    timeout: Optional[float] = None  # 超时时间（秒）
    dependencies: list[str] = None  # 依赖的其他指标ID列表
    
    def __post_init__(self):
        if self.dependencies is None:
            self.dependencies = []


@dataclass
class MetricResult:
    """指标执行结果"""
    metric_id: str
    status: MetricStatus
    result: Any = None
    error: Optional[str] = None
    execution_time: float = 0.0
    start_time: Optional[float] = None
    end_time: Optional[float] = None
