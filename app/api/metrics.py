from fastapi import APIRouter, Depends, HTTPException, Query, BackgroundTasks
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, and_, or_, func, desc
from sqlalchemy.orm import selectinload
from typing import List, Optional
from datetime import datetime, timedelta
import uuid
from loguru import logger

from ..database import get_db_session
from ..models import Company, MetricDefinition, CompanyMetric, MetricExecution
from ..schemas import (
    Company as CompanySchema,
    MetricDefinition as MetricDefinitionSchema,
    CompanyMetric as CompanyMetricSchema,
    MetricExecution as MetricExecutionSchema,
    ExecuteMetricRequest,
    BatchExecuteRequest,
    ExecutionResponse,
    BatchExecutionResponse,
    MetricExecutionQuery,
    PaginatedResponse,
    ExecutionStatus
)
from ..tasks.metric_tasks import execute_company_metric, batch_execute_metrics
from ..protection import db_protection

router = APIRouter(prefix="/api/v1/metrics", tags=["metrics"])


@router.get("/companies", response_model=List[CompanySchema])
async def get_companies(
    status: Optional[str] = Query(None, description="企业状态过滤"),
    limit: int = Query(100, le=1000, description="返回数量限制"),
    offset: int = Query(0, ge=0, description="偏移量"),
    db: AsyncSession = Depends(get_db_session)
):
    """获取企业列表"""
    async with db_protection.protect("get_companies"):
        query = select(Company)
        
        if status:
            query = query.where(Company.status == status)
        
        query = query.offset(offset).limit(limit).order_by(Company.id)
        
        result = await db.execute(query)
        companies = result.scalars().all()
        
        return companies


@router.get("/definitions", response_model=List[MetricDefinitionSchema])
async def get_metric_definitions(
    frequency: Optional[str] = Query(None, description="频率过滤"),
    priority_min: Optional[int] = Query(None, ge=1, le=10, description="最小优先级"),
    priority_max: Optional[int] = Query(None, ge=1, le=10, description="最大优先级"),
    limit: int = Query(100, le=1000, description="返回数量限制"),
    offset: int = Query(0, ge=0, description="偏移量"),
    db: AsyncSession = Depends(get_db_session)
):
    """获取指标定义列表"""
    async with db_protection.protect("get_metric_definitions"):
        query = select(MetricDefinition)
        
        if frequency:
            query = query.where(MetricDefinition.frequency == frequency)
        
        if priority_min is not None:
            query = query.where(MetricDefinition.priority >= priority_min)
        
        if priority_max is not None:
            query = query.where(MetricDefinition.priority <= priority_max)
        
        query = query.offset(offset).limit(limit).order_by(MetricDefinition.priority, MetricDefinition.id)
        
        result = await db.execute(query)
        definitions = result.scalars().all()
        
        return definitions


@router.get("/company-metrics", response_model=List[CompanyMetricSchema])
async def get_company_metrics(
    company_id: Optional[int] = Query(None, description="企业ID"),
    metric_id: Optional[int] = Query(None, description="指标ID"),
    is_enabled: Optional[bool] = Query(None, description="是否启用"),
    limit: int = Query(100, le=1000, description="返回数量限制"),
    offset: int = Query(0, ge=0, description="偏移量"),
    db: AsyncSession = Depends(get_db_session)
):
    """获取企业指标关联列表"""
    async with db_protection.protect("get_company_metrics"):
        query = select(CompanyMetric).options(
            selectinload(CompanyMetric.company),
            selectinload(CompanyMetric.metric)
        )
        
        if company_id is not None:
            query = query.where(CompanyMetric.company_id == company_id)
        
        if metric_id is not None:
            query = query.where(CompanyMetric.metric_id == metric_id)
        
        if is_enabled is not None:
            query = query.where(CompanyMetric.is_enabled == is_enabled)
        
        query = query.offset(offset).limit(limit).order_by(CompanyMetric.id)
        
        result = await db.execute(query)
        company_metrics = result.scalars().all()
        
        return company_metrics


@router.post("/execute", response_model=ExecutionResponse)
async def execute_metric(
    request: ExecuteMetricRequest,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db_session)
):
    """执行单个企业指标"""
    async with db_protection.protect("execute_metric"):
        # 验证企业和指标是否存在且启用
        company_metric_result = await db.execute(
            select(CompanyMetric)
            .options(
                selectinload(CompanyMetric.company),
                selectinload(CompanyMetric.metric)
            )
            .where(
                and_(
                    CompanyMetric.company_id == request.company_id,
                    CompanyMetric.metric_id == request.metric_id,
                    CompanyMetric.is_enabled == True
                )
            )
        )
        
        company_metric = company_metric_result.scalar_one_or_none()
        
        if not company_metric:
            raise HTTPException(
                status_code=404,
                detail=f"Enabled metric not found for company {request.company_id} and metric {request.metric_id}"
            )
        
        if company_metric.company.status != "active":
            raise HTTPException(
                status_code=400,
                detail=f"Company {request.company_id} is not active"
            )
        
        # 检查是否有正在运行的任务
        running_execution_result = await db.execute(
            select(MetricExecution)
            .where(
                and_(
                    MetricExecution.company_id == request.company_id,
                    MetricExecution.metric_id == request.metric_id,
                    MetricExecution.status == ExecutionStatus.RUNNING,
                    MetricExecution.created_at > datetime.utcnow() - timedelta(hours=1)
                )
            )
        )
        
        running_execution = running_execution_result.scalar_one_or_none()
        
        if running_execution:
            raise HTTPException(
                status_code=409,
                detail=f"Metric execution already running: {running_execution.execution_id}"
            )
        
        # 提交异步任务
        task = execute_company_metric.delay(
            request.company_id,
            request.metric_id,
            request.execution_params
        )
        
        logger.info(f"Metric execution scheduled: company={request.company_id}, metric={request.metric_id}, task_id={task.id}")
        
        return ExecutionResponse(
            execution_id=task.id,
            company_id=request.company_id,
            metric_id=request.metric_id,
            status=ExecutionStatus.PENDING,
            message=f"Metric execution scheduled for {company_metric.company.name}.{company_metric.metric.name}"
        )


@router.post("/batch-execute", response_model=BatchExecutionResponse)
async def batch_execute_metrics_endpoint(
    request: BatchExecuteRequest,
    background_tasks: BackgroundTasks,
    db: AsyncSession = Depends(get_db_session)
):
    """批量执行企业指标"""
    async with db_protection.protect("batch_execute_metrics"):
        # 验证企业和指标
        if len(request.company_ids) > 50:
            raise HTTPException(
                status_code=400,
                detail="Too many companies, maximum 50 allowed per batch"
            )
        
        if len(request.metric_ids) > 20:
            raise HTTPException(
                status_code=400,
                detail="Too many metrics, maximum 20 allowed per batch"
            )
        
        # 验证企业是否存在且活跃
        companies_result = await db.execute(
            select(Company).where(
                and_(
                    Company.id.in_(request.company_ids),
                    Company.status == "active"
                )
            )
        )
        active_companies = companies_result.scalars().all()
        active_company_ids = [c.id for c in active_companies]
        
        if len(active_company_ids) != len(request.company_ids):
            inactive_ids = set(request.company_ids) - set(active_company_ids)
            raise HTTPException(
                status_code=400,
                detail=f"Inactive or non-existent companies: {list(inactive_ids)}"
            )
        
        # 验证指标是否存在
        metrics_result = await db.execute(
            select(MetricDefinition).where(MetricDefinition.id.in_(request.metric_ids))
        )
        metrics = metrics_result.scalars().all()
        metric_ids = [m.id for m in metrics]
        
        if len(metric_ids) != len(request.metric_ids):
            missing_ids = set(request.metric_ids) - set(metric_ids)
            raise HTTPException(
                status_code=400,
                detail=f"Non-existent metrics: {list(missing_ids)}"
            )
        
        # 提交批量任务
        batch_id = str(uuid.uuid4())
        task = batch_execute_metrics.delay(
            request.company_ids,
            request.metric_ids,
            {**request.batch_params or {}, "batch_id": batch_id}
        )
        
        total_tasks = len(request.company_ids) * len(request.metric_ids)
        
        logger.info(f"Batch execution scheduled: batch_id={batch_id}, companies={len(request.company_ids)}, metrics={len(request.metric_ids)}, total_tasks={total_tasks}")
        
        return BatchExecutionResponse(
            batch_id=batch_id,
            total_tasks=total_tasks,
            scheduled_tasks=[task.id],
            message=f"Batch execution scheduled: {len(request.company_ids)} companies × {len(request.metric_ids)} metrics = {total_tasks} tasks"
        )


@router.get("/executions", response_model=PaginatedResponse)
async def get_metric_executions(
    company_id: Optional[int] = Query(None, description="企业ID"),
    metric_id: Optional[int] = Query(None, description="指标ID"),
    status: Optional[ExecutionStatus] = Query(None, description="执行状态"),
    start_date: Optional[datetime] = Query(None, description="开始时间"),
    end_date: Optional[datetime] = Query(None, description="结束时间"),
    limit: int = Query(100, le=1000, description="返回数量限制"),
    offset: int = Query(0, ge=0, description="偏移量"),
    db: AsyncSession = Depends(get_db_session)
):
    """查询指标执行记录"""
    async with db_protection.protect("get_metric_executions"):
        # 构建查询条件
        query = select(MetricExecution).options(
            selectinload(MetricExecution.company),
            selectinload(MetricExecution.metric)
        )
        count_query = select(func.count(MetricExecution.id))
        
        conditions = []
        
        if company_id is not None:
            conditions.append(MetricExecution.company_id == company_id)
        
        if metric_id is not None:
            conditions.append(MetricExecution.metric_id == metric_id)
        
        if status is not None:
            conditions.append(MetricExecution.status == status)
        
        if start_date is not None:
            conditions.append(MetricExecution.created_at >= start_date)
        
        if end_date is not None:
            conditions.append(MetricExecution.created_at <= end_date)
        
        if conditions:
            query = query.where(and_(*conditions))
            count_query = count_query.where(and_(*conditions))
        
        # 获取总数
        count_result = await db.execute(count_query)
        total = count_result.scalar()
        
        # 获取分页数据
        query = query.order_by(desc(MetricExecution.created_at)).offset(offset).limit(limit)
        result = await db.execute(query)
        executions = result.scalars().all()
        
        # 转换为字典格式
        items = []
        for execution in executions:
            item = {
                "id": execution.id,
                "company_id": execution.company_id,
                "company_name": execution.company.name if execution.company else None,
                "company_code": execution.company.code if execution.company else None,
                "metric_id": execution.metric_id,
                "metric_name": execution.metric.name if execution.metric else None,
                "metric_code": execution.metric.code if execution.metric else None,
                "execution_id": execution.execution_id,
                "status": execution.status,
                "start_time": execution.start_time,
                "end_time": execution.end_time,
                "duration_ms": execution.duration_ms,
                "result_data": execution.result_data,
                "error_message": execution.error_message,
                "retry_count": execution.retry_count,
                "created_at": execution.created_at
            }
            items.append(item)
        
        return PaginatedResponse(
            items=items,
            total=total,
            limit=limit,
            offset=offset,
            has_next=offset + limit < total,
            has_prev=offset > 0
        )


@router.get("/executions/{execution_id}", response_model=MetricExecutionSchema)
async def get_metric_execution(
    execution_id: str,
    db: AsyncSession = Depends(get_db_session)
):
    """获取单个指标执行记录"""
    async with db_protection.protect("get_metric_execution"):
        result = await db.execute(
            select(MetricExecution)
            .options(
                selectinload(MetricExecution.company),
                selectinload(MetricExecution.metric)
            )
            .where(MetricExecution.execution_id == execution_id)
        )
        
        execution = result.scalar_one_or_none()
        
        if not execution:
            raise HTTPException(
                status_code=404,
                detail=f"Execution not found: {execution_id}"
            )
        
        return execution


@router.get("/stats/summary")
async def get_metrics_summary(
    hours: int = Query(24, ge=1, le=168, description="统计时间范围(小时)"),
    db: AsyncSession = Depends(get_db_session)
):
    """获取指标执行统计摘要"""
    async with db_protection.protect("get_metrics_summary"):
        start_time = datetime.utcnow() - timedelta(hours=hours)
        
        # 总执行数
        total_result = await db.execute(
            select(func.count(MetricExecution.id))
            .where(MetricExecution.created_at >= start_time)
        )
        total_executions = total_result.scalar()
        
        # 按状态统计
        status_result = await db.execute(
            select(MetricExecution.status, func.count(MetricExecution.id))
            .where(MetricExecution.created_at >= start_time)
            .group_by(MetricExecution.status)
        )
        status_stats = {status: count for status, count in status_result.fetchall()}
        
        # 按企业统计
        company_result = await db.execute(
            select(Company.name, Company.code, func.count(MetricExecution.id))
            .join(MetricExecution)
            .where(MetricExecution.created_at >= start_time)
            .group_by(Company.id, Company.name, Company.code)
            .order_by(func.count(MetricExecution.id).desc())
            .limit(10)
        )
        top_companies = [
            {"name": name, "code": code, "executions": count}
            for name, code, count in company_result.fetchall()
        ]
        
        # 按指标统计
        metric_result = await db.execute(
            select(MetricDefinition.name, MetricDefinition.code, func.count(MetricExecution.id))
            .join(MetricExecution)
            .where(MetricExecution.created_at >= start_time)
            .group_by(MetricDefinition.id, MetricDefinition.name, MetricDefinition.code)
            .order_by(func.count(MetricExecution.id).desc())
            .limit(10)
        )
        top_metrics = [
            {"name": name, "code": code, "executions": count}
            for name, code, count in metric_result.fetchall()
        ]
        
        # 平均执行时间
        avg_duration_result = await db.execute(
            select(func.avg(MetricExecution.duration_ms))
            .where(
                and_(
                    MetricExecution.created_at >= start_time,
                    MetricExecution.duration_ms.isnot(None)
                )
            )
        )
        avg_duration_ms = avg_duration_result.scalar() or 0
        
        # 成功率
        success_rate = 0
        if total_executions > 0:
            success_count = status_stats.get("completed", 0)
            success_rate = round((success_count / total_executions) * 100, 2)
        
        return {
            "period_hours": hours,
            "start_time": start_time.isoformat(),
            "total_executions": total_executions,
            "status_breakdown": status_stats,
            "success_rate_percent": success_rate,
            "average_duration_ms": round(avg_duration_ms, 2),
            "top_companies": top_companies,
            "top_metrics": top_metrics
        }