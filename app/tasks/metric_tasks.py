import asyncio
import uuid
from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional
from celery import current_task
from sqlalchemy import select, and_, or_
from sqlalchemy.orm import selectinload
import time
from loguru import logger

from ..celery_app import celery_app, redis_client
from ..database import db_manager
from ..models import Company, MetricDefinition, CompanyMetric, MetricExecution
from ..protection import db_protection, task_throttler
from ..config import settings


@celery_app.task(bind=True, max_retries=3)
def execute_company_metric(self, company_id: int, metric_id: int, execution_params: Optional[Dict] = None):
    """执行单个企业指标计算"""
    execution_id = str(uuid.uuid4())
    
    try:
        # 使用asyncio运行异步任务
        return asyncio.run(_execute_company_metric_async(
            company_id, metric_id, execution_id, execution_params or {}
        ))
    except Exception as exc:
        logger.error(f"Task failed: company_id={company_id}, metric_id={metric_id}, error={exc}")
        
        # 记录失败状态
        asyncio.run(_update_execution_status(
            execution_id, "failed", error_message=str(exc)
        ))
        
        # 重试逻辑
        if self.request.retries < self.max_retries:
            retry_delay = settings.retry_delay * (2 ** self.request.retries)  # 指数退避
            logger.info(f"Retrying task in {retry_delay} seconds, attempt {self.request.retries + 1}")
            raise self.retry(countdown=retry_delay, exc=exc)
        
        raise exc


async def _execute_company_metric_async(
    company_id: int, 
    metric_id: int, 
    execution_id: str, 
    execution_params: Dict
) -> Dict[str, Any]:
    """异步执行企业指标计算"""
    start_time = datetime.utcnow()
    
    async with task_throttler.throttle(f"metric_{company_id}_{metric_id}"):
        async with db_protection.protect("execute_metric"):
            try:
                # 创建执行记录
                await _create_execution_record(
                    company_id, metric_id, execution_id, start_time
                )
                
                # 获取企业和指标信息
                async with db_manager.get_session() as session:
                    # 查询企业信息
                    company_result = await session.execute(
                        select(Company).where(Company.id == company_id)
                    )
                    company = company_result.scalar_one_or_none()
                    
                    if not company:
                        raise ValueError(f"Company not found: {company_id}")
                    
                    # 查询指标定义
                    metric_result = await session.execute(
                        select(MetricDefinition).where(MetricDefinition.id == metric_id)
                    )
                    metric = metric_result.scalar_one_or_none()
                    
                    if not metric:
                        raise ValueError(f"Metric not found: {metric_id}")
                    
                    # 检查指标是否启用
                    company_metric_result = await session.execute(
                        select(CompanyMetric).where(
                            and_(
                                CompanyMetric.company_id == company_id,
                                CompanyMetric.metric_id == metric_id,
                                CompanyMetric.is_enabled == True
                            )
                        )
                    )
                    company_metric = company_metric_result.scalar_one_or_none()
                    
                    if not company_metric:
                        raise ValueError(f"Metric not enabled for company: {company_id}, {metric_id}")
                
                # 执行指标计算
                result_data = await _calculate_metric(
                    company, metric, company_metric, execution_params
                )
                
                # 更新执行状态为完成
                end_time = datetime.utcnow()
                duration_ms = int((end_time - start_time).total_seconds() * 1000)
                
                await _update_execution_status(
                    execution_id, "completed", end_time, duration_ms, result_data
                )
                
                logger.info(f"Metric executed successfully: {company.code}.{metric.code}, duration: {duration_ms}ms")
                
                return {
                    "execution_id": execution_id,
                    "company_id": company_id,
                    "metric_id": metric_id,
                    "status": "completed",
                    "duration_ms": duration_ms,
                    "result_data": result_data
                }
                
            except Exception as e:
                # 更新执行状态为失败
                end_time = datetime.utcnow()
                duration_ms = int((end_time - start_time).total_seconds() * 1000)
                
                await _update_execution_status(
                    execution_id, "failed", end_time, duration_ms, error_message=str(e)
                )
                
                logger.error(f"Metric execution failed: {company_id}.{metric_id}, error: {e}")
                raise


async def _calculate_metric(
    company: Company, 
    metric: MetricDefinition, 
    company_metric: CompanyMetric,
    execution_params: Dict
) -> Dict[str, Any]:
    """计算指标值"""
    
    # 模拟指标计算逻辑
    # 在实际应用中，这里会执行SQL查询或调用外部API
    
    calculation_start = time.time()
    
    # 根据指标类型执行不同的计算逻辑
    if metric.code == "REVENUE":
        # 模拟营收计算
        await asyncio.sleep(0.1)  # 模拟计算时间
        result_value = 1000000 + (company.id * 50000)  # 模拟结果
        
    elif metric.code == "USER_GROWTH":
        # 模拟用户增长率计算
        await asyncio.sleep(0.05)
        result_value = 0.15 + (company.id * 0.01)  # 模拟增长率
        
    elif metric.code == "COST_ANALYSIS":
        # 模拟成本分析
        await asyncio.sleep(0.08)
        result_value = 800000 + (company.id * 30000)  # 模拟成本
        
    elif metric.code == "MARKET_SHARE":
        # 模拟市场份额计算
        await asyncio.sleep(0.12)
        result_value = 0.05 + (company.id * 0.02)  # 模拟市场份额
        
    elif metric.code == "CUSTOMER_SATISFACTION":
        # 模拟客户满意度计算
        await asyncio.sleep(0.06)
        result_value = 4.2 + (company.id * 0.1)  # 模拟满意度评分
        
    else:
        # 默认计算逻辑
        await asyncio.sleep(0.1)
        result_value = company.id * 1000
    
    calculation_duration = time.time() - calculation_start
    
    return {
        "metric_code": metric.code,
        "metric_name": metric.name,
        "company_code": company.code,
        "company_name": company.name,
        "value": result_value,
        "calculation_duration_ms": int(calculation_duration * 1000),
        "timestamp": datetime.utcnow().isoformat(),
        "custom_params": company_metric.custom_params,
        "execution_params": execution_params
    }


async def _create_execution_record(
    company_id: int, 
    metric_id: int, 
    execution_id: str, 
    start_time: datetime
):
    """创建执行记录"""
    async with db_manager.get_session() as session:
        execution = MetricExecution(
            company_id=company_id,
            metric_id=metric_id,
            execution_id=execution_id,
            status="running",
            start_time=start_time
        )
        session.add(execution)
        await session.commit()


async def _update_execution_status(
    execution_id: str,
    status: str,
    end_time: Optional[datetime] = None,
    duration_ms: Optional[int] = None,
    result_data: Optional[Dict] = None,
    error_message: Optional[str] = None
):
    """更新执行状态"""
    async with db_manager.get_session() as session:
        result = await session.execute(
            select(MetricExecution).where(MetricExecution.execution_id == execution_id)
        )
        execution = result.scalar_one_or_none()
        
        if execution:
            execution.status = status
            if end_time:
                execution.end_time = end_time
            if duration_ms is not None:
                execution.duration_ms = duration_ms
            if result_data:
                execution.result_data = result_data
            if error_message:
                execution.error_message = error_message
            
            await session.commit()


@celery_app.task
def batch_execute_metrics(company_ids: List[int], metric_ids: List[int], batch_params: Optional[Dict] = None):
    """批量执行指标计算"""
    return asyncio.run(_batch_execute_metrics_async(company_ids, metric_ids, batch_params or {}))


async def _batch_execute_metrics_async(
    company_ids: List[int], 
    metric_ids: List[int], 
    batch_params: Dict
):
    """异步批量执行指标计算"""
    batch_id = str(uuid.uuid4())
    logger.info(f"Starting batch execution: {batch_id}, companies: {len(company_ids)}, metrics: {len(metric_ids)}")
    
    tasks = []
    for company_id in company_ids:
        for metric_id in metric_ids:
            # 创建异步任务
            task = execute_company_metric.delay(company_id, metric_id, batch_params)
            tasks.append(task)
    
    # 等待所有任务完成（设置超时）
    results = []
    timeout = batch_params.get("timeout", settings.task_timeout)
    
    for task in tasks:
        try:
            result = task.get(timeout=timeout)
            results.append(result)
        except Exception as e:
            logger.error(f"Batch task failed: {e}")
            results.append({"error": str(e)})
    
    logger.info(f"Batch execution completed: {batch_id}, total tasks: {len(tasks)}, successful: {len([r for r in results if 'error' not in r])}")
    
    return {
        "batch_id": batch_id,
        "total_tasks": len(tasks),
        "results": results,
        "successful_count": len([r for r in results if 'error' not in r]),
        "failed_count": len([r for r in results if 'error' in r])
    }


@celery_app.task
def schedule_priority_metrics():
    """调度高优先级指标"""
    return asyncio.run(_schedule_metrics_by_priority(priority_threshold=3))


@celery_app.task
def schedule_daily_metrics():
    """调度日常指标"""
    return asyncio.run(_schedule_metrics_by_frequency("daily"))


@celery_app.task
def schedule_weekly_metrics():
    """调度周度指标"""
    return asyncio.run(_schedule_metrics_by_frequency("weekly"))


async def _schedule_metrics_by_priority(priority_threshold: int):
    """按优先级调度指标"""
    async with db_manager.get_session() as session:
        # 查询高优先级指标
        result = await session.execute(
            select(CompanyMetric)
            .options(
                selectinload(CompanyMetric.company),
                selectinload(CompanyMetric.metric)
            )
            .join(MetricDefinition)
            .where(
                and_(
                    CompanyMetric.is_enabled == True,
                    MetricDefinition.priority <= priority_threshold,
                    Company.status == "active"
                )
            )
            .join(Company)
        )
        
        company_metrics = result.scalars().all()
        
        # 检查是否需要执行（避免重复执行）
        scheduled_tasks = []
        for cm in company_metrics:
            if await _should_execute_metric(cm.company_id, cm.metric_id):
                task = execute_company_metric.delay(
                    cm.company_id, 
                    cm.metric_id, 
                    {"scheduled_by": "priority", "priority": cm.metric.priority}
                )
                scheduled_tasks.append(task.id)
        
        logger.info(f"Scheduled {len(scheduled_tasks)} priority metrics")
        return {"scheduled_tasks": len(scheduled_tasks), "task_ids": scheduled_tasks}


async def _schedule_metrics_by_frequency(frequency: str):
    """按频率调度指标"""
    async with db_manager.get_session() as session:
        result = await session.execute(
            select(CompanyMetric)
            .options(
                selectinload(CompanyMetric.company),
                selectinload(CompanyMetric.metric)
            )
            .join(MetricDefinition)
            .where(
                and_(
                    CompanyMetric.is_enabled == True,
                    MetricDefinition.frequency == frequency,
                    Company.status == "active"
                )
            )
            .join(Company)
        )
        
        company_metrics = result.scalars().all()
        
        scheduled_tasks = []
        for cm in company_metrics:
            if await _should_execute_metric(cm.company_id, cm.metric_id, frequency):
                task = execute_company_metric.delay(
                    cm.company_id, 
                    cm.metric_id, 
                    {"scheduled_by": "frequency", "frequency": frequency}
                )
                scheduled_tasks.append(task.id)
        
        logger.info(f"Scheduled {len(scheduled_tasks)} {frequency} metrics")
        return {"scheduled_tasks": len(scheduled_tasks), "task_ids": scheduled_tasks}


async def _should_execute_metric(company_id: int, metric_id: int, frequency: str = "daily") -> bool:
    """判断是否应该执行指标"""
    # 检查最近的执行记录
    async with db_manager.get_session() as session:
        # 根据频率确定时间窗口
        if frequency == "daily":
            time_window = datetime.utcnow() - timedelta(hours=1)  # 1小时内不重复执行
        elif frequency == "weekly":
            time_window = datetime.utcnow() - timedelta(hours=6)  # 6小时内不重复执行
        else:
            time_window = datetime.utcnow() - timedelta(minutes=30)  # 30分钟内不重复执行
        
        result = await session.execute(
            select(MetricExecution)
            .where(
                and_(
                    MetricExecution.company_id == company_id,
                    MetricExecution.metric_id == metric_id,
                    MetricExecution.created_at > time_window,
                    or_(
                        MetricExecution.status == "completed",
                        MetricExecution.status == "running"
                    )
                )
            )
            .limit(1)
        )
        
        recent_execution = result.scalar_one_or_none()
        return recent_execution is None