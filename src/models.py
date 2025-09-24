"""
数据模型定义
"""
from datetime import datetime
from typing import Optional, Dict, Any, List
from enum import Enum
from sqlalchemy import (
    Column, Integer, BigInteger, String, Text, Boolean, DateTime, 
    JSON, ForeignKey, Index, UniqueConstraint
)
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import relationship
from pydantic import BaseModel, Field

Base = declarative_base()


class TaskStatus(str, Enum):
    """任务状态枚举"""
    PENDING = "PENDING"
    RUNNING = "RUNNING"
    SUCCESS = "SUCCESS"
    FAILED = "FAILED"
    TIMEOUT = "TIMEOUT"
    CANCELLED = "CANCELLED"


class NodeStatus(str, Enum):
    """节点状态枚举"""
    ONLINE = "ONLINE"
    OFFLINE = "OFFLINE"
    BUSY = "BUSY"
    MAINTENANCE = "MAINTENANCE"


class QueueStatus(str, Enum):
    """队列状态枚举"""
    QUEUED = "QUEUED"
    PROCESSING = "PROCESSING"
    COMPLETED = "COMPLETED"


# SQLAlchemy 模型
class MetricDefinition(Base):
    """指标定义表"""
    __tablename__ = "metric_definitions"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    name = Column(String(255), nullable=False, unique=True, comment="指标名称")
    description = Column(Text, comment="指标描述")
    sql_template = Column(Text, nullable=False, comment="SQL模板")
    data_source = Column(String(100), nullable=False, comment="数据源标识")
    category = Column(String(100), default="default", comment="指标分类")
    tags = Column(JSON, comment="标签信息")
    timeout_seconds = Column(Integer, default=300, comment="超时时间(秒)")
    retry_count = Column(Integer, default=3, comment="重试次数")
    is_active = Column(Boolean, default=True, comment="是否启用")
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    created_by = Column(String(100), comment="创建人")

    # 关系
    schedules = relationship("ScheduleConfig", back_populates="metric", cascade="all, delete-orphan")
    tasks = relationship("ExecutionTask", back_populates="metric")

    __table_args__ = (
        Index("idx_category", "category"),
        Index("idx_is_active", "is_active"),
    )


class ScheduleConfig(Base):
    """调度配置表"""
    __tablename__ = "schedule_configs"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    metric_id = Column(BigInteger, ForeignKey("metric_definitions.id", ondelete="CASCADE"), nullable=False)
    cron_expression = Column(String(100), nullable=False, comment="Cron表达式")
    priority = Column(Integer, default=5, comment="优先级(1-10, 数字越小优先级越高)")
    max_concurrent = Column(Integer, default=1, comment="最大并发数")
    start_time = Column(DateTime, comment="开始时间")
    end_time = Column(DateTime, comment="结束时间")
    is_enabled = Column(Boolean, default=True, comment="是否启用")
    parameters = Column(JSON, comment="执行参数")
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # 关系
    metric = relationship("MetricDefinition", back_populates="schedules")
    tasks = relationship("ExecutionTask", back_populates="schedule")

    __table_args__ = (
        Index("idx_metric_id", "metric_id"),
        Index("idx_priority", "priority"),
        Index("idx_is_enabled", "is_enabled"),
    )


class ExecutionTask(Base):
    """执行任务表"""
    __tablename__ = "execution_tasks"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    metric_id = Column(BigInteger, ForeignKey("metric_definitions.id"), nullable=False)
    schedule_id = Column(BigInteger, ForeignKey("schedule_configs.id"), nullable=False)
    task_id = Column(String(64), nullable=False, unique=True, comment="任务唯一标识")
    status = Column(String(20), default=TaskStatus.PENDING, comment="执行状态")
    priority = Column(Integer, default=5, comment="优先级")
    scheduled_time = Column(DateTime, nullable=False, comment="计划执行时间")
    start_time = Column(DateTime, comment="开始执行时间")
    end_time = Column(DateTime, comment="结束执行时间")
    duration_ms = Column(BigInteger, comment="执行耗时(毫秒)")
    worker_id = Column(String(100), comment="执行节点ID")
    result_data = Column(JSON, comment="执行结果数据")
    error_message = Column(Text, comment="错误信息")
    retry_count = Column(Integer, default=0, comment="已重试次数")
    parameters = Column(JSON, comment="执行参数")
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    # 关系
    metric = relationship("MetricDefinition", back_populates="tasks")
    schedule = relationship("ScheduleConfig", back_populates="tasks")

    __table_args__ = (
        Index("idx_status", "status"),
        Index("idx_scheduled_time", "scheduled_time"),
        Index("idx_metric_schedule", "metric_id", "schedule_id"),
        Index("idx_priority_status", "priority", "status"),
    )


class ExecutionHistory(Base):
    """执行历史表"""
    __tablename__ = "execution_history"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    task_id = Column(String(64), nullable=False, comment="任务ID")
    metric_id = Column(BigInteger, nullable=False, comment="指标ID")
    metric_name = Column(String(255), nullable=False, comment="指标名称")
    status = Column(String(20), nullable=False, comment="执行状态")
    scheduled_time = Column(DateTime, nullable=False, comment="计划执行时间")
    start_time = Column(DateTime, comment="开始执行时间")
    end_time = Column(DateTime, comment="结束执行时间")
    duration_ms = Column(BigInteger, comment="执行耗时(毫秒)")
    worker_id = Column(String(100), comment="执行节点ID")
    result_rows = Column(Integer, comment="结果行数")
    error_message = Column(Text, comment="错误信息")
    execution_date = Column(DateTime, nullable=False, comment="执行日期")
    created_at = Column(DateTime, default=datetime.utcnow)

    __table_args__ = (
        Index("idx_execution_date", "execution_date"),
        Index("idx_metric_id_date", "metric_id", "execution_date"),
        Index("idx_status_date", "status", "execution_date"),
    )


class DataSource(Base):
    """数据源配置表"""
    __tablename__ = "data_sources"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    name = Column(String(100), nullable=False, unique=True, comment="数据源名称")
    type = Column(String(50), nullable=False, comment="数据源类型")
    host = Column(String(255), nullable=False, comment="主机地址")
    port = Column(Integer, nullable=False, comment="端口")
    database_name = Column(String(100), nullable=False, comment="数据库名")
    username = Column(String(100), nullable=False, comment="用户名")
    password = Column(String(255), nullable=False, comment="密码(加密存储)")
    connection_params = Column(JSON, comment="连接参数")
    max_connections = Column(Integer, default=10, comment="最大连接数")
    is_active = Column(Boolean, default=True, comment="是否启用")
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    __table_args__ = (
        Index("idx_type", "type"),
        Index("idx_is_active", "is_active"),
    )


class SystemConfig(Base):
    """系统配置表"""
    __tablename__ = "system_configs"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    config_key = Column(String(100), nullable=False, unique=True, comment="配置键")
    config_value = Column(Text, nullable=False, comment="配置值")
    description = Column(Text, comment="配置描述")
    config_type = Column(String(50), default="string", comment="配置类型")
    is_encrypted = Column(Boolean, default=False, comment="是否加密")
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)


class WorkerNode(Base):
    """工作节点表"""
    __tablename__ = "worker_nodes"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    node_id = Column(String(100), nullable=False, unique=True, comment="节点ID")
    node_name = Column(String(255), nullable=False, comment="节点名称")
    host_ip = Column(String(45), nullable=False, comment="主机IP")
    port = Column(Integer, nullable=False, comment="端口")
    max_concurrent_tasks = Column(Integer, default=10, comment="最大并发任务数")
    current_tasks = Column(Integer, default=0, comment="当前任务数")
    status = Column(String(20), default=NodeStatus.OFFLINE, comment="节点状态")
    last_heartbeat = Column(DateTime, comment="最后心跳时间")
    capabilities = Column(JSON, comment="节点能力")
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    __table_args__ = (
        Index("idx_status", "status"),
        Index("idx_last_heartbeat", "last_heartbeat"),
    )


class TaskQueue(Base):
    """任务队列表"""
    __tablename__ = "task_queue"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    task_id = Column(String(64), nullable=False, unique=True, comment="任务ID")
    priority = Column(Integer, nullable=False, comment="优先级")
    scheduled_time = Column(DateTime, nullable=False, comment="计划执行时间")
    payload = Column(JSON, nullable=False, comment="任务载荷")
    status = Column(String(20), default=QueueStatus.QUEUED, comment="队列状态")
    worker_id = Column(String(100), comment="分配的工作节点")
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    __table_args__ = (
        Index("idx_priority_scheduled", "priority", "scheduled_time"),
        Index("idx_status", "status"),
        Index("idx_worker_id", "worker_id"),
    )


# Pydantic 模型（用于API）
class MetricDefinitionCreate(BaseModel):
    """创建指标定义的请求模型"""
    name: str = Field(..., description="指标名称")
    description: Optional[str] = Field(None, description="指标描述")
    sql_template: str = Field(..., description="SQL模板")
    data_source: str = Field(..., description="数据源标识")
    category: str = Field(default="default", description="指标分类")
    tags: Optional[Dict[str, Any]] = Field(None, description="标签信息")
    timeout_seconds: int = Field(default=300, description="超时时间(秒)")
    retry_count: int = Field(default=3, description="重试次数")
    is_active: bool = Field(default=True, description="是否启用")


class MetricDefinitionResponse(BaseModel):
    """指标定义响应模型"""
    id: int
    name: str
    description: Optional[str]
    sql_template: str
    data_source: str
    category: str
    tags: Optional[Dict[str, Any]]
    timeout_seconds: int
    retry_count: int
    is_active: bool
    created_at: datetime
    updated_at: datetime
    created_by: Optional[str]

    class Config:
        from_attributes = True


class ScheduleConfigCreate(BaseModel):
    """创建调度配置的请求模型"""
    metric_id: int = Field(..., description="指标ID")
    cron_expression: str = Field(..., description="Cron表达式")
    priority: int = Field(default=5, description="优先级")
    max_concurrent: int = Field(default=1, description="最大并发数")
    start_time: Optional[datetime] = Field(None, description="开始时间")
    end_time: Optional[datetime] = Field(None, description="结束时间")
    is_enabled: bool = Field(default=True, description="是否启用")
    parameters: Optional[Dict[str, Any]] = Field(None, description="执行参数")


class ScheduleConfigResponse(BaseModel):
    """调度配置响应模型"""
    id: int
    metric_id: int
    cron_expression: str
    priority: int
    max_concurrent: int
    start_time: Optional[datetime]
    end_time: Optional[datetime]
    is_enabled: bool
    parameters: Optional[Dict[str, Any]]
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class ExecutionTaskResponse(BaseModel):
    """执行任务响应模型"""
    id: int
    metric_id: int
    schedule_id: int
    task_id: str
    status: TaskStatus
    priority: int
    scheduled_time: datetime
    start_time: Optional[datetime]
    end_time: Optional[datetime]
    duration_ms: Optional[int]
    worker_id: Optional[str]
    result_data: Optional[Dict[str, Any]]
    error_message: Optional[str]
    retry_count: int
    parameters: Optional[Dict[str, Any]]
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class TaskStatistics(BaseModel):
    """任务统计模型"""
    total_tasks: int
    pending_tasks: int
    running_tasks: int
    success_tasks: int
    failed_tasks: int
    timeout_tasks: int
    cancelled_tasks: int
    average_duration_ms: Optional[float]


class NodeStatistics(BaseModel):
    """节点统计模型"""
    total_nodes: int
    online_nodes: int
    offline_nodes: int
    busy_nodes: int
    maintenance_nodes: int
    total_capacity: int
    current_load: int