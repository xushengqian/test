from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select, func, desc
from typing import Dict, Any, List
from datetime import datetime, timedelta
import psutil
from loguru import logger

from ..database import get_db_session, db_manager, check_database_health
from ..models import SystemMetric, MetricExecution
from ..schemas import SystemHealthResponse, SystemStatsResponse
from ..protection import db_protection, task_throttler
from ..tasks.system_tasks import health_check, generate_system_report
from ..celery_app import redis_client

router = APIRouter(prefix="/api/v1/system", tags=["system"])


@router.get("/health", response_model=SystemHealthResponse)
async def get_system_health():
    """获取系统健康状态"""
    try:
        # 执行健康检查任务
        task = health_check.delay()
        health_data = task.get(timeout=30)  # 30秒超时
        
        return SystemHealthResponse(
            status=health_data["status"],
            timestamp=datetime.fromisoformat(health_data["timestamp"]),
            checks=health_data["checks"]
        )
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        return SystemHealthResponse(
            status="unhealthy",
            timestamp=datetime.utcnow(),
            checks={"error": str(e)}
        )


@router.get("/stats", response_model=SystemStatsResponse)
async def get_system_stats(
    db: AsyncSession = Depends(get_db_session)
):
    """获取系统统计信息"""
    async with db_protection.protect("get_system_stats"):
        try:
            # 数据库连接池信息
            db_info = await db_manager.get_connection_info()
            
            # 任务队列统计
            task_stats = task_throttler.get_stats()
            
            # 系统资源信息
            cpu_percent = psutil.cpu_percent(interval=1)
            memory = psutil.virtual_memory()
            disk = psutil.disk_usage('/')
            
            system_resources = {
                "cpu_usage_percent": cpu_percent,
                "memory_usage_percent": memory.percent,
                "memory_total_gb": round(memory.total / (1024**3), 2),
                "memory_available_gb": round(memory.available / (1024**3), 2),
                "disk_usage_percent": round((disk.used / disk.total) * 100, 2),
                "disk_total_gb": round(disk.total / (1024**3), 2),
                "disk_free_gb": round(disk.free / (1024**3), 2)
            }
            
            # 最近执行统计
            last_hour = datetime.utcnow() - timedelta(hours=1)
            
            # 最近1小时的执行统计
            recent_executions_result = await db.execute(
                select(
                    MetricExecution.status,
                    func.count(MetricExecution.id).label('count')
                )
                .where(MetricExecution.created_at >= last_hour)
                .group_by(MetricExecution.status)
            )
            
            recent_executions = {
                status: count for status, count in recent_executions_result.fetchall()
            }
            
            # 总执行数
            total_recent_result = await db.execute(
                select(func.count(MetricExecution.id))
                .where(MetricExecution.created_at >= last_hour)
            )
            total_recent = total_recent_result.scalar()
            
            recent_executions["total"] = total_recent
            recent_executions["period"] = "last_1_hour"
            
            return SystemStatsResponse(
                database_connections=db_info,
                task_queue_stats=task_stats,
                system_resources=system_resources,
                recent_executions=recent_executions
            )
            
        except Exception as e:
            logger.error(f"Failed to get system stats: {e}")
            raise HTTPException(status_code=500, detail=f"Failed to get system stats: {str(e)}")


@router.get("/metrics")
async def get_system_metrics(
    metric_names: List[str] = Query(None, description="指标名称列表"),
    hours: int = Query(1, ge=1, le=24, description="时间范围(小时)"),
    limit: int = Query(100, le=1000, description="返回数量限制"),
    db: AsyncSession = Depends(get_db_session)
):
    """获取系统监控指标"""
    async with db_protection.protect("get_system_metrics"):
        start_time = datetime.utcnow() - timedelta(hours=hours)
        
        query = select(SystemMetric).where(SystemMetric.timestamp >= start_time)
        
        if metric_names:
            query = query.where(SystemMetric.metric_name.in_(metric_names))
        
        query = query.order_by(desc(SystemMetric.timestamp)).limit(limit)
        
        result = await db.execute(query)
        metrics = result.scalars().all()
        
        # 按指标名称分组
        grouped_metrics = {}
        for metric in metrics:
            if metric.metric_name not in grouped_metrics:
                grouped_metrics[metric.metric_name] = []
            
            grouped_metrics[metric.metric_name].append({
                "value": float(metric.metric_value) if metric.metric_value else None,
                "tags": metric.tags,
                "timestamp": metric.timestamp.isoformat()
            })
        
        return {
            "period_hours": hours,
            "start_time": start_time.isoformat(),
            "metrics": grouped_metrics,
            "total_points": len(metrics)
        }


@router.get("/metrics/latest")
async def get_latest_system_metrics(
    db: AsyncSession = Depends(get_db_session)
):
    """获取最新的系统指标"""
    async with db_protection.protect("get_latest_system_metrics"):
        # 获取每个指标的最新值
        subquery = (
            select(
                SystemMetric.metric_name,
                func.max(SystemMetric.timestamp).label('latest_timestamp')
            )
            .group_by(SystemMetric.metric_name)
        ).subquery()
        
        query = (
            select(SystemMetric)
            .join(
                subquery,
                (SystemMetric.metric_name == subquery.c.metric_name) &
                (SystemMetric.timestamp == subquery.c.latest_timestamp)
            )
            .order_by(SystemMetric.metric_name)
        )
        
        result = await db.execute(query)
        latest_metrics = result.scalars().all()
        
        metrics_dict = {}
        for metric in latest_metrics:
            metrics_dict[metric.metric_name] = {
                "value": float(metric.metric_value) if metric.metric_value else None,
                "tags": metric.tags,
                "timestamp": metric.timestamp.isoformat()
            }
        
        return {
            "timestamp": datetime.utcnow().isoformat(),
            "metrics": metrics_dict,
            "count": len(metrics_dict)
        }


@router.get("/report")
async def get_system_report():
    """获取系统报告"""
    try:
        # 执行系统报告生成任务
        task = generate_system_report.delay()
        report_data = task.get(timeout=60)  # 60秒超时
        
        return report_data
    except Exception as e:
        logger.error(f"Failed to generate system report: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to generate system report: {str(e)}")


@router.get("/database/status")
async def get_database_status():
    """获取数据库状态"""
    try:
        # 检查数据库健康状态
        is_healthy = await check_database_health()
        
        # 获取连接池信息
        db_info = await db_manager.get_connection_info()
        
        return {
            "healthy": is_healthy,
            "connection_pool": db_info,
            "timestamp": datetime.utcnow().isoformat()
        }
    except Exception as e:
        logger.error(f"Failed to get database status: {e}")
        return {
            "healthy": False,
            "error": str(e),
            "timestamp": datetime.utcnow().isoformat()
        }


@router.get("/redis/status")
async def get_redis_status():
    """获取Redis状态"""
    try:
        # 检查Redis连接
        await redis_client.ping()
        
        # 获取Redis信息
        info = await redis_client.info()
        
        return {
            "healthy": True,
            "info": {
                "redis_version": info.get("redis_version"),
                "used_memory": info.get("used_memory"),
                "used_memory_human": info.get("used_memory_human"),
                "connected_clients": info.get("connected_clients"),
                "total_commands_processed": info.get("total_commands_processed"),
                "keyspace_hits": info.get("keyspace_hits"),
                "keyspace_misses": info.get("keyspace_misses"),
                "uptime_in_seconds": info.get("uptime_in_seconds")
            },
            "timestamp": datetime.utcnow().isoformat()
        }
    except Exception as e:
        logger.error(f"Failed to get Redis status: {e}")
        return {
            "healthy": False,
            "error": str(e),
            "timestamp": datetime.utcnow().isoformat()
        }


@router.get("/tasks/stats")
async def get_task_stats():
    """获取任务统计信息"""
    try:
        # 获取Celery任务统计
        from ..celery_app import celery_app
        
        # 获取活跃任务
        active_tasks = celery_app.control.inspect().active()
        
        # 获取预定任务
        scheduled_tasks = celery_app.control.inspect().scheduled()
        
        # 获取保留任务
        reserved_tasks = celery_app.control.inspect().reserved()
        
        # 统计信息
        total_active = sum(len(tasks) for tasks in (active_tasks or {}).values())
        total_scheduled = sum(len(tasks) for tasks in (scheduled_tasks or {}).values())
        total_reserved = sum(len(tasks) for tasks in (reserved_tasks or {}).values())
        
        # 获取任务节流器统计
        throttler_stats = task_throttler.get_stats()
        
        return {
            "celery_tasks": {
                "active": total_active,
                "scheduled": total_scheduled,
                "reserved": total_reserved,
                "active_details": active_tasks,
                "scheduled_details": scheduled_tasks,
                "reserved_details": reserved_tasks
            },
            "throttler": throttler_stats,
            "timestamp": datetime.utcnow().isoformat()
        }
    except Exception as e:
        logger.error(f"Failed to get task stats: {e}")
        return {
            "error": str(e),
            "timestamp": datetime.utcnow().isoformat()
        }


@router.post("/cleanup")
async def trigger_cleanup():
    """触发系统清理"""
    try:
        from ..tasks.system_tasks import cleanup_old_executions
        
        # 执行清理任务
        task = cleanup_old_executions.delay()
        result = task.get(timeout=120)  # 2分钟超时
        
        return {
            "status": "completed",
            "result": result,
            "timestamp": datetime.utcnow().isoformat()
        }
    except Exception as e:
        logger.error(f"Cleanup failed: {e}")
        raise HTTPException(status_code=500, detail=f"Cleanup failed: {str(e)}")


@router.post("/collect-metrics")
async def trigger_metrics_collection():
    """触发系统指标收集"""
    try:
        from ..tasks.system_tasks import collect_system_metrics
        
        # 执行指标收集任务
        task = collect_system_metrics.delay()
        result = task.get(timeout=60)  # 1分钟超时
        
        return {
            "status": "completed",
            "result": result,
            "timestamp": datetime.utcnow().isoformat()
        }
    except Exception as e:
        logger.error(f"Metrics collection failed: {e}")
        raise HTTPException(status_code=500, detail=f"Metrics collection failed: {str(e)}")