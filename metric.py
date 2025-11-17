"""
指标定义模块
定义指标的基本结构和接口
"""
from dataclasses import dataclass
from typing import Callable, Any, Optional
from enum import Enum
from datetime import datetime


class MetricStatus(Enum):
    """指标状态枚举"""
    PENDING = "pending"  # 等待执行
    RUNNING = "running"  # 执行中
    COMPLETED = "completed"  # 已完成
    FAILED = "failed"  # 执行失败
    CANCELLED = "cancelled"  # 已取消


@dataclass
class Metric:
    """指标定义"""
    id: str  # 指标唯一标识
    name: str  # 指标名称
    func: Callable[[], Any]  # 指标计算函数
    priority: int = 0  # 优先级（数字越大优先级越高）
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
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    duration: Optional[float] = None  # 执行耗时（秒）
    
    def __post_init__(self):
        if self.start_time and self.end_time:
            self.duration = (self.end_time - self.start_time).total_seconds()
