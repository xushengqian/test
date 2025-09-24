from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, List
from datetime import datetime
from enum import Enum


class ExecutionStatus(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    COMPLETED = "completed"
    FAILED = "failed"
    TIMEOUT = "timeout"


class MetricFrequency(str, Enum):
    DAILY = "daily"
    WEEKLY = "weekly"
    MONTHLY = "monthly"


class CompanyBase(BaseModel):
    name: str = Field(..., description="企业名称")
    code: str = Field(..., description="企业代码")
    status: str = Field(default="active", description="企业状态")


class CompanyCreate(CompanyBase):
    pass


class CompanyUpdate(BaseModel):
    name: Optional[str] = None
    status: Optional[str] = None


class Company(CompanyBase):
    id: int
    created_at: datetime
    updated_at: datetime
    
    class Config:
        from_attributes = True


class MetricDefinitionBase(BaseModel):
    name: str = Field(..., description="指标名称")
    code: str = Field(..., description="指标代码")
    description: Optional[str] = Field(None, description="指标描述")
    calculation_sql: Optional[str] = Field(None, description="计算SQL")
    data_source: Optional[str] = Field(None, description="数据源")
    frequency: MetricFrequency = Field(default=MetricFrequency.DAILY, description="执行频率")
    priority: int = Field(default=5, ge=1, le=10, description="优先级(1-10)")
    timeout_seconds: int = Field(default=300, description="超时时间(秒)")


class MetricDefinitionCreate(MetricDefinitionBase):
    pass


class MetricDefinitionUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    calculation_sql: Optional[str] = None
    data_source: Optional[str] = None
    frequency: Optional[MetricFrequency] = None
    priority: Optional[int] = Field(None, ge=1, le=10)
    timeout_seconds: Optional[int] = None


class MetricDefinition(MetricDefinitionBase):
    id: int
    created_at: datetime
    updated_at: datetime
    
    class Config:
        from_attributes = True


class CompanyMetricBase(BaseModel):
    company_id: int
    metric_id: int
    is_enabled: bool = Field(default=True, description="是否启用")
    custom_params: Optional[Dict[str, Any]] = Field(None, description="自定义参数")


class CompanyMetricCreate(CompanyMetricBase):
    pass


class CompanyMetricUpdate(BaseModel):
    is_enabled: Optional[bool] = None
    custom_params: Optional[Dict[str, Any]] = None


class CompanyMetric(CompanyMetricBase):
    id: int
    created_at: datetime
    
    class Config:
        from_attributes = True


class MetricExecutionBase(BaseModel):
    company_id: int
    metric_id: int
    execution_id: str
    status: ExecutionStatus = Field(default=ExecutionStatus.PENDING)
    start_time: Optional[datetime] = None
    end_time: Optional[datetime] = None
    duration_ms: Optional[int] = None
    result_data: Optional[Dict[str, Any]] = None
    error_message: Optional[str] = None
    retry_count: int = Field(default=0)


class MetricExecution(MetricExecutionBase):
    id: int
    created_at: datetime
    
    class Config:
        from_attributes = True


class ExecuteMetricRequest(BaseModel):
    company_id: int = Field(..., description="企业ID")
    metric_id: int = Field(..., description="指标ID")
    execution_params: Optional[Dict[str, Any]] = Field(None, description="执行参数")


class BatchExecuteRequest(BaseModel):
    company_ids: List[int] = Field(..., description="企业ID列表")
    metric_ids: List[int] = Field(..., description="指标ID列表")
    batch_params: Optional[Dict[str, Any]] = Field(None, description="批处理参数")


class ExecutionResponse(BaseModel):
    execution_id: str
    company_id: int
    metric_id: int
    status: ExecutionStatus
    message: str


class BatchExecutionResponse(BaseModel):
    batch_id: str
    total_tasks: int
    scheduled_tasks: List[str]
    message: str


class SystemHealthResponse(BaseModel):
    status: str
    timestamp: datetime
    checks: Dict[str, Any]


class SystemStatsResponse(BaseModel):
    database_connections: Dict[str, Any]
    task_queue_stats: Dict[str, Any]
    system_resources: Dict[str, Any]
    recent_executions: Dict[str, Any]


class MetricExecutionQuery(BaseModel):
    company_id: Optional[int] = None
    metric_id: Optional[int] = None
    status: Optional[ExecutionStatus] = None
    start_date: Optional[datetime] = None
    end_date: Optional[datetime] = None
    limit: int = Field(default=100, le=1000)
    offset: int = Field(default=0, ge=0)


class PaginatedResponse(BaseModel):
    items: List[Any]
    total: int
    limit: int
    offset: int
    has_next: bool
    has_prev: bool