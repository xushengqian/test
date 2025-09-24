"""
FastAPI REST API接口
"""
import asyncio
from datetime import datetime, timedelta
from typing import List, Optional, Dict, Any
from fastapi import FastAPI, Depends, HTTPException, Query, Path, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import PlainTextResponse
from sqlalchemy.orm import Session
from sqlalchemy import and_, or_, desc, func

from .config import get_config
from .database import get_db, DatabaseService
from .models import (
    MetricDefinition, ScheduleConfig, ExecutionTask, ExecutionHistory,
    WorkerNode, DataSource, SystemConfig,
    MetricDefinitionCreate, MetricDefinitionResponse,
    ScheduleConfigCreate, ScheduleConfigResponse,
    ExecutionTaskResponse, TaskStatistics, NodeStatistics,
    TaskStatus, NodeStatus
)
from .scheduler import scheduler
from .executor import executor_manager
from .monitoring import metrics_collector, alert_manager, performance_monitor
from .logging_config import get_logger, log_api_request, LogTemplates
from .utils import get_current_time, validate_cron_expression, Timer

logger = get_logger(__name__)

# 创建FastAPI应用
app = FastAPI(
    title="Metric Scheduler API",
    description="大批量指标调度执行系统API",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc"
)

# 添加CORS中间件
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# 中间件：API请求日志
@app.middleware("http")
async def log_requests(request, call_next):
    start_time = datetime.now()
    response = await call_next(request)
    duration_ms = int((datetime.now() - start_time).total_seconds() * 1000)
    
    log_api_request(
        method=request.method,
        path=str(request.url.path),
        status_code=response.status_code,
        duration_ms=duration_ms
    )
    
    return response


# 健康检查
@app.get("/health")
async def health_check():
    """健康检查接口"""
    health_status = metrics_collector.get_health_status()
    
    if health_status["healthy"]:
        return health_status
    else:
        raise HTTPException(status_code=503, detail=health_status)


# Prometheus指标接口
@app.get("/metrics", response_class=PlainTextResponse)
async def get_metrics():
    """获取Prometheus格式的监控指标"""
    return metrics_collector.get_metrics()


# 系统状态接口
@app.get("/status")
async def get_system_status():
    """获取系统状态"""
    scheduler_status = scheduler.get_scheduler_status()
    executor_status = executor_manager.get_executor_status()
    health_status = metrics_collector.get_health_status()
    
    return {
        "scheduler": scheduler_status,
        "executors": executor_status,
        "health": health_status,
        "timestamp": get_current_time().isoformat()
    }


# 指标定义管理
@app.post("/metrics", response_model=MetricDefinitionResponse)
async def create_metric(
    metric: MetricDefinitionCreate,
    db: Session = Depends(get_db)
):
    """创建指标定义"""
    # 检查指标名称是否已存在
    existing = db.query(MetricDefinition).filter(
        MetricDefinition.name == metric.name
    ).first()
    
    if existing:
        raise HTTPException(status_code=400, detail="Metric name already exists")
    
    # 验证数据源是否存在
    data_source = db.query(DataSource).filter(
        DataSource.name == metric.data_source
    ).first()
    
    if not data_source:
        raise HTTPException(status_code=400, detail="Data source not found")
    
    # 创建指标定义
    db_metric = MetricDefinition(**metric.dict())
    db.add(db_metric)
    db.commit()
    db.refresh(db_metric)
    
    LogTemplates.metric_created(db_metric.id, db_metric.name, "api_user")
    
    return db_metric


@app.get("/metrics", response_model=List[MetricDefinitionResponse])
async def list_metrics(
    skip: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=1000),
    category: Optional[str] = Query(None),
    is_active: Optional[bool] = Query(None),
    db: Session = Depends(get_db)
):
    """获取指标定义列表"""
    query = db.query(MetricDefinition)
    
    if category:
        query = query.filter(MetricDefinition.category == category)
    
    if is_active is not None:
        query = query.filter(MetricDefinition.is_active == is_active)
    
    metrics = query.offset(skip).limit(limit).all()
    return metrics


@app.get("/metrics/{metric_id}", response_model=MetricDefinitionResponse)
async def get_metric(
    metric_id: int = Path(..., gt=0),
    db: Session = Depends(get_db)
):
    """获取指标定义详情"""
    metric = db.query(MetricDefinition).filter(
        MetricDefinition.id == metric_id
    ).first()
    
    if not metric:
        raise HTTPException(status_code=404, detail="Metric not found")
    
    return metric


@app.put("/metrics/{metric_id}", response_model=MetricDefinitionResponse)
async def update_metric(
    metric_id: int = Path(..., gt=0),
    metric_update: MetricDefinitionCreate = None,
    db: Session = Depends(get_db)
):
    """更新指标定义"""
    metric = db.query(MetricDefinition).filter(
        MetricDefinition.id == metric_id
    ).first()
    
    if not metric:
        raise HTTPException(status_code=404, detail="Metric not found")
    
    # 更新字段
    for field, value in metric_update.dict(exclude_unset=True).items():
        setattr(metric, field, value)
    
    metric.updated_at = get_current_time()
    db.commit()
    db.refresh(metric)
    
    return metric


@app.delete("/metrics/{metric_id}")
async def delete_metric(
    metric_id: int = Path(..., gt=0),
    db: Session = Depends(get_db)
):
    """删除指标定义"""
    metric = db.query(MetricDefinition).filter(
        MetricDefinition.id == metric_id
    ).first()
    
    if not metric:
        raise HTTPException(status_code=404, detail="Metric not found")
    
    # 检查是否有关联的调度配置
    schedules = db.query(ScheduleConfig).filter(
        ScheduleConfig.metric_id == metric_id
    ).count()
    
    if schedules > 0:
        raise HTTPException(
            status_code=400, 
            detail="Cannot delete metric with existing schedules"
        )
    
    db.delete(metric)
    db.commit()
    
    return {"message": "Metric deleted successfully"}


# 调度配置管理
@app.post("/schedules", response_model=ScheduleConfigResponse)
async def create_schedule(
    schedule: ScheduleConfigCreate,
    db: Session = Depends(get_db)
):
    """创建调度配置"""
    # 验证指标是否存在
    metric = db.query(MetricDefinition).filter(
        MetricDefinition.id == schedule.metric_id
    ).first()
    
    if not metric:
        raise HTTPException(status_code=400, detail="Metric not found")
    
    # 验证cron表达式
    if not validate_cron_expression(schedule.cron_expression):
        raise HTTPException(status_code=400, detail="Invalid cron expression")
    
    # 创建调度配置
    db_schedule = ScheduleConfig(**schedule.dict())
    db.add(db_schedule)
    db.commit()
    db.refresh(db_schedule)
    
    LogTemplates.schedule_created(db_schedule.id, metric.name, schedule.cron_expression)
    
    return db_schedule


@app.get("/schedules", response_model=List[ScheduleConfigResponse])
async def list_schedules(
    skip: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=1000),
    metric_id: Optional[int] = Query(None),
    is_enabled: Optional[bool] = Query(None),
    db: Session = Depends(get_db)
):
    """获取调度配置列表"""
    query = db.query(ScheduleConfig)
    
    if metric_id:
        query = query.filter(ScheduleConfig.metric_id == metric_id)
    
    if is_enabled is not None:
        query = query.filter(ScheduleConfig.is_enabled == is_enabled)
    
    schedules = query.offset(skip).limit(limit).all()
    return schedules


@app.get("/schedules/{schedule_id}", response_model=ScheduleConfigResponse)
async def get_schedule(
    schedule_id: int = Path(..., gt=0),
    db: Session = Depends(get_db)
):
    """获取调度配置详情"""
    schedule = db.query(ScheduleConfig).filter(
        ScheduleConfig.id == schedule_id
    ).first()
    
    if not schedule:
        raise HTTPException(status_code=404, detail="Schedule not found")
    
    return schedule


@app.put("/schedules/{schedule_id}", response_model=ScheduleConfigResponse)
async def update_schedule(
    schedule_id: int = Path(..., gt=0),
    schedule_update: ScheduleConfigCreate = None,
    db: Session = Depends(get_db)
):
    """更新调度配置"""
    schedule = db.query(ScheduleConfig).filter(
        ScheduleConfig.id == schedule_id
    ).first()
    
    if not schedule:
        raise HTTPException(status_code=404, detail="Schedule not found")
    
    # 验证cron表达式
    if schedule_update.cron_expression and not validate_cron_expression(schedule_update.cron_expression):
        raise HTTPException(status_code=400, detail="Invalid cron expression")
    
    # 更新字段
    for field, value in schedule_update.dict(exclude_unset=True).items():
        setattr(schedule, field, value)
    
    schedule.updated_at = get_current_time()
    db.commit()
    db.refresh(schedule)
    
    return schedule


@app.delete("/schedules/{schedule_id}")
async def delete_schedule(
    schedule_id: int = Path(..., gt=0),
    db: Session = Depends(get_db)
):
    """删除调度配置"""
    schedule = db.query(ScheduleConfig).filter(
        ScheduleConfig.id == schedule_id
    ).first()
    
    if not schedule:
        raise HTTPException(status_code=404, detail="Schedule not found")
    
    db.delete(schedule)
    db.commit()
    
    return {"message": "Schedule deleted successfully"}


# 任务管理
@app.get("/tasks", response_model=List[ExecutionTaskResponse])
async def list_tasks(
    skip: int = Query(0, ge=0),
    limit: int = Query(100, ge=1, le=1000),
    status: Optional[TaskStatus] = Query(None),
    metric_id: Optional[int] = Query(None),
    worker_id: Optional[str] = Query(None),
    start_date: Optional[datetime] = Query(None),
    end_date: Optional[datetime] = Query(None),
    db: Session = Depends(get_db)
):
    """获取任务列表"""
    query = db.query(ExecutionTask)
    
    if status:
        query = query.filter(ExecutionTask.status == status.value)
    
    if metric_id:
        query = query.filter(ExecutionTask.metric_id == metric_id)
    
    if worker_id:
        query = query.filter(ExecutionTask.worker_id == worker_id)
    
    if start_date:
        query = query.filter(ExecutionTask.scheduled_time >= start_date)
    
    if end_date:
        query = query.filter(ExecutionTask.scheduled_time <= end_date)
    
    tasks = query.order_by(desc(ExecutionTask.created_at)).offset(skip).limit(limit).all()
    return tasks


@app.get("/tasks/{task_id}", response_model=ExecutionTaskResponse)
async def get_task(
    task_id: str = Path(...),
    db: Session = Depends(get_db)
):
    """获取任务详情"""
    task = db.query(ExecutionTask).filter(
        ExecutionTask.task_id == task_id
    ).first()
    
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    
    return task


@app.post("/tasks/submit")
async def submit_task(
    metric_id: int,
    parameters: Optional[Dict[str, Any]] = None,
    background_tasks: BackgroundTasks = None
):
    """手动提交任务"""
    try:
        task_id = await scheduler.submit_task(metric_id, parameters)
        return {"task_id": task_id, "message": "Task submitted successfully"}
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error(f"Error submitting task: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")


@app.post("/tasks/{task_id}/cancel")
async def cancel_task(
    task_id: str = Path(...),
):
    """取消任务"""
    try:
        success = await scheduler.cancel_task(task_id)
        if success:
            return {"message": "Task cancelled successfully"}
        else:
            raise HTTPException(status_code=400, detail="Task cannot be cancelled")
    except Exception as e:
        logger.error(f"Error cancelling task: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")


@app.post("/tasks/{task_id}/retry")
async def retry_task(
    task_id: str = Path(...),
    db: Session = Depends(get_db)
):
    """重试任务"""
    task = db.query(ExecutionTask).filter(
        ExecutionTask.task_id == task_id
    ).first()
    
    if not task:
        raise HTTPException(status_code=404, detail="Task not found")
    
    if task.status not in [TaskStatus.FAILED, TaskStatus.TIMEOUT]:
        raise HTTPException(status_code=400, detail="Only failed or timeout tasks can be retried")
    
    try:
        new_task_id = await scheduler.submit_task(task.metric_id, task.parameters)
        return {"new_task_id": new_task_id, "message": "Task retry submitted successfully"}
    except Exception as e:
        logger.error(f"Error retrying task: {e}")
        raise HTTPException(status_code=500, detail="Internal server error")


# 统计信息
@app.get("/statistics/tasks", response_model=TaskStatistics)
async def get_task_statistics(
    start_date: Optional[datetime] = Query(None),
    end_date: Optional[datetime] = Query(None),
    db: Session = Depends(get_db)
):
    """获取任务统计信息"""
    query = db.query(ExecutionTask)
    
    if start_date:
        query = query.filter(ExecutionTask.created_at >= start_date)
    
    if end_date:
        query = query.filter(ExecutionTask.created_at <= end_date)
    
    # 统计各状态任务数量
    stats = {}
    for status in TaskStatus:
        count = query.filter(ExecutionTask.status == status.value).count()
        stats[f"{status.value.lower()}_tasks"] = count
    
    # 总任务数
    total_tasks = query.count()
    stats["total_tasks"] = total_tasks
    
    # 平均执行时间
    avg_duration = query.filter(
        ExecutionTask.duration_ms.isnot(None)
    ).with_entities(func.avg(ExecutionTask.duration_ms)).scalar()
    
    stats["average_duration_ms"] = float(avg_duration) if avg_duration else None
    
    return TaskStatistics(**stats)


@app.get("/statistics/nodes", response_model=NodeStatistics)
async def get_node_statistics(db: Session = Depends(get_db)):
    """获取节点统计信息"""
    stats = {}
    
    # 统计各状态节点数量
    for status in NodeStatus:
        count = db.query(WorkerNode).filter(
            WorkerNode.status == status.value
        ).count()
        stats[f"{status.value.lower()}_nodes"] = count
    
    # 总节点数
    total_nodes = db.query(WorkerNode).count()
    stats["total_nodes"] = total_nodes
    
    # 总容量和当前负载
    nodes = db.query(WorkerNode).filter(
        WorkerNode.status == NodeStatus.ONLINE
    ).all()
    
    total_capacity = sum(node.max_concurrent_tasks for node in nodes)
    current_load = sum(node.current_tasks for node in nodes)
    
    stats["total_capacity"] = total_capacity
    stats["current_load"] = current_load
    
    return NodeStatistics(**stats)


# 工作节点管理
@app.get("/nodes")
async def list_nodes(
    status: Optional[NodeStatus] = Query(None),
    db: Session = Depends(get_db)
):
    """获取工作节点列表"""
    query = db.query(WorkerNode)
    
    if status:
        query = query.filter(WorkerNode.status == status.value)
    
    nodes = query.all()
    return nodes


@app.get("/nodes/{node_id}")
async def get_node(
    node_id: str = Path(...),
    db: Session = Depends(get_db)
):
    """获取工作节点详情"""
    node = db.query(WorkerNode).filter(
        WorkerNode.node_id == node_id
    ).first()
    
    if not node:
        raise HTTPException(status_code=404, detail="Node not found")
    
    return node


# 数据源管理
@app.get("/datasources")
async def list_datasources(
    is_active: Optional[bool] = Query(None),
    db: Session = Depends(get_db)
):
    """获取数据源列表"""
    query = db.query(DataSource)
    
    if is_active is not None:
        query = query.filter(DataSource.is_active == is_active)
    
    datasources = query.all()
    
    # 隐藏密码字段
    for ds in datasources:
        ds.password = "***"
    
    return datasources


@app.post("/datasources")
async def create_datasource(
    datasource: dict,
    db: Session = Depends(get_db)
):
    """创建数据源"""
    # 检查名称是否已存在
    existing = db.query(DataSource).filter(
        DataSource.name == datasource["name"]
    ).first()
    
    if existing:
        raise HTTPException(status_code=400, detail="Datasource name already exists")
    
    # 创建数据源
    db_datasource = DataSource(**datasource)
    db.add(db_datasource)
    db.commit()
    db.refresh(db_datasource)
    
    # 隐藏密码
    db_datasource.password = "***"
    
    return db_datasource


# 系统配置管理
@app.get("/config")
async def get_system_config(
    config_key: Optional[str] = Query(None),
    db: Session = Depends(get_db)
):
    """获取系统配置"""
    query = db.query(SystemConfig)
    
    if config_key:
        config = query.filter(SystemConfig.config_key == config_key).first()
        if not config:
            raise HTTPException(status_code=404, detail="Config not found")
        return config
    
    configs = query.all()
    return configs


@app.put("/config/{config_key}")
async def update_system_config(
    config_key: str = Path(...),
    config_value: str = None,
    db: Session = Depends(get_db)
):
    """更新系统配置"""
    config = db.query(SystemConfig).filter(
        SystemConfig.config_key == config_key
    ).first()
    
    if not config:
        raise HTTPException(status_code=404, detail="Config not found")
    
    config.config_value = config_value
    config.updated_at = get_current_time()
    db.commit()
    db.refresh(config)
    
    return config


# 启动和关闭事件
@app.on_event("startup")
async def startup_event():
    """应用启动事件"""
    logger.info("Starting Metric Scheduler API...")
    
    # 启动监控系统
    await metrics_collector.start()
    await alert_manager.start()
    await performance_monitor.start()
    
    # 启动调度器
    if get_config().scheduler.enabled:
        await scheduler.start()
    
    # 启动执行器
    node_id = get_config().node_id
    await executor_manager.start_executor(node_id)
    
    logger.info("Metric Scheduler API started successfully")


@app.on_event("shutdown")
async def shutdown_event():
    """应用关闭事件"""
    logger.info("Shutting down Metric Scheduler API...")
    
    # 停止调度器
    await scheduler.stop()
    
    # 停止执行器
    await executor_manager.stop_all_executors()
    
    # 停止监控系统
    await metrics_collector.stop()
    await alert_manager.stop()
    await performance_monitor.stop()
    
    logger.info("Metric Scheduler API shutdown completed")


if __name__ == "__main__":
    import uvicorn
    
    config = get_config().api
    uvicorn.run(
        "api:app",
        host=config.host,
        port=config.port,
        debug=config.debug,
        reload=config.debug
    )