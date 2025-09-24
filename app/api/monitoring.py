from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy.ext.asyncio import AsyncSession
from typing import List, Dict, Any, Optional
from datetime import datetime, timedelta
import asyncio
from loguru import logger

from ..database import get_db_session, db_manager
from ..monitoring.prometheus import prometheus_metrics
from ..monitoring.alerts import alert_manager, AlertSeverity, AlertStatus
from ..protection import db_protection, task_throttler
from ..tasks.system_tasks import collect_system_metrics

router = APIRouter(prefix="/api/v1/monitoring", tags=["monitoring"])


@router.get("/metrics")
async def get_prometheus_metrics():
    """获取Prometheus格式的指标"""
    try:
        metrics_data = prometheus_metrics.get_metrics()
        return Response(content=metrics_data, media_type="text/plain")
    except Exception as e:
        logger.error(f"Failed to get Prometheus metrics: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to get metrics: {str(e)}")


@router.get("/alerts")
async def get_alerts(
    status: Optional[AlertStatus] = Query(None, description="告警状态过滤"),
    severity: Optional[AlertSeverity] = Query(None, description="严重程度过滤"),
    hours: int = Query(24, ge=1, le=168, description="时间范围(小时)")
):
    """获取告警列表"""
    try:
        if status == AlertStatus.ACTIVE:
            alerts = alert_manager.get_active_alerts()
        else:
            alerts = alert_manager.get_alert_history(hours)
        
        # 过滤条件
        if status and status != AlertStatus.ACTIVE:
            alerts = [alert for alert in alerts if alert.status == status]
        
        if severity:
            alerts = [alert for alert in alerts if alert.severity == severity]
        
        # 转换为字典格式
        alerts_data = [alert.to_dict() for alert in alerts]
        
        return {
            "alerts": alerts_data,
            "total": len(alerts_data),
            "filters": {
                "status": status.value if status else None,
                "severity": severity.value if severity else None,
                "hours": hours
            }
        }
    except Exception as e:
        logger.error(f"Failed to get alerts: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to get alerts: {str(e)}")


@router.get("/alerts/stats")
async def get_alert_stats():
    """获取告警统计"""
    try:
        stats = alert_manager.get_alert_stats()
        return stats
    except Exception as e:
        logger.error(f"Failed to get alert stats: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to get alert stats: {str(e)}")


@router.post("/alerts/check")
async def trigger_alert_check(
    db: AsyncSession = Depends(get_db_session)
):
    """手动触发告警检查"""
    try:
        # 收集当前系统指标
        await collect_system_metrics()
        
        # 获取系统指标用于告警检查
        metrics = await _collect_current_metrics(db)
        
        # 执行告警检查
        await alert_manager.check_alerts(metrics)
        
        return {
            "status": "completed",
            "message": "Alert check completed",
            "metrics_collected": len(metrics),
            "timestamp": datetime.utcnow().isoformat()
        }
    except Exception as e:
        logger.error(f"Failed to trigger alert check: {e}")
        raise HTTPException(status_code=500, detail=f"Failed to trigger alert check: {str(e)}")


@router.get("/dashboard")
async def get_monitoring_dashboard(
    db: AsyncSession = Depends(get_db_session)
):
    """获取监控仪表板数据"""
    async with db_protection.protect("get_monitoring_dashboard"):
        try:
            # 收集各种监控数据
            dashboard_data = {}
            
            # 系统资源指标
            import psutil
            cpu_percent = psutil.cpu_percent(interval=1)
            memory = psutil.virtual_memory()
            disk = psutil.disk_usage('/')
            
            dashboard_data["system_resources"] = {
                "cpu_usage_percent": cpu_percent,
                "memory_usage_percent": memory.percent,
                "memory_total_gb": round(memory.total / (1024**3), 2),
                "memory_used_gb": round(memory.used / (1024**3), 2),
                "disk_usage_percent": round((disk.used / disk.total) * 100, 2),
                "disk_total_gb": round(disk.total / (1024**3), 2),
                "disk_free_gb": round(disk.free / (1024**3), 2)
            }
            
            # 数据库连接池状态
            db_info = await db_manager.get_connection_info()
            dashboard_data["database"] = db_info
            
            # 任务队列状态
            task_stats = task_throttler.get_stats()
            dashboard_data["task_queue"] = task_stats
            
            # 告警统计
            alert_stats = alert_manager.get_alert_stats()
            dashboard_data["alerts"] = alert_stats
            
            # 最近执行统计
            from ..models import MetricExecution
            from sqlalchemy import select, func, and_
            
            last_hour = datetime.utcnow() - timedelta(hours=1)
            
            # 最近1小时的执行统计
            execution_result = await db.execute(
                select(
                    MetricExecution.status,
                    func.count(MetricExecution.id).label('count')
                )
                .where(MetricExecution.created_at >= last_hour)
                .group_by(MetricExecution.status)
            )
            
            execution_stats = {
                status: count for status, count in execution_result.fetchall()
            }
            
            total_executions = sum(execution_stats.values())
            success_rate = 0
            if total_executions > 0:
                success_count = execution_stats.get("completed", 0)
                success_rate = round((success_count / total_executions) * 100, 2)
            
            dashboard_data["executions"] = {
                "last_hour": execution_stats,
                "total_last_hour": total_executions,
                "success_rate_percent": success_rate
            }
            
            # 性能指标
            dashboard_data["performance"] = {
                "avg_cpu_usage": cpu_percent,
                "avg_memory_usage": memory.percent,
                "database_pool_usage": (db_info["checked_out"] / max(db_info["pool_size"], 1)) * 100,
                "task_queue_usage": (task_stats["active_tasks"] / max(task_stats["max_concurrent"], 1)) * 100
            }
            
            dashboard_data["timestamp"] = datetime.utcnow().isoformat()
            
            return dashboard_data
            
        except Exception as e:
            logger.error(f"Failed to get monitoring dashboard: {e}")
            raise HTTPException(status_code=500, detail=f"Failed to get dashboard data: {str(e)}")


@router.get("/health-summary")
async def get_health_summary(
    db: AsyncSession = Depends(get_db_session)
):
    """获取健康状态摘要"""
    try:
        # 检查各个组件的健康状态
        health_checks = {}
        overall_status = "healthy"
        
        # 数据库健康检查
        try:
            from ..database import check_database_health
            db_healthy = await check_database_health()
            health_checks["database"] = {
                "status": "healthy" if db_healthy else "unhealthy",
                "healthy": db_healthy
            }
            if not db_healthy:
                overall_status = "unhealthy"
        except Exception as e:
            health_checks["database"] = {
                "status": "unhealthy",
                "healthy": False,
                "error": str(e)
            }
            overall_status = "unhealthy"
        
        # Redis健康检查
        try:
            from ..celery_app import redis_client
            await redis_client.ping()
            health_checks["redis"] = {
                "status": "healthy",
                "healthy": True
            }
        except Exception as e:
            health_checks["redis"] = {
                "status": "unhealthy",
                "healthy": False,
                "error": str(e)
            }
            overall_status = "unhealthy"
        
        # 系统资源检查
        import psutil
        cpu_percent = psutil.cpu_percent(interval=1)
        memory = psutil.virtual_memory()
        disk = psutil.disk_usage('/')
        
        # CPU检查
        cpu_healthy = cpu_percent < 90
        health_checks["cpu"] = {
            "status": "healthy" if cpu_healthy else "warning",
            "healthy": cpu_healthy,
            "usage_percent": cpu_percent
        }
        
        # 内存检查
        memory_healthy = memory.percent < 90
        health_checks["memory"] = {
            "status": "healthy" if memory_healthy else "warning",
            "healthy": memory_healthy,
            "usage_percent": memory.percent
        }
        
        # 磁盘检查
        disk_usage_percent = (disk.used / disk.total) * 100
        disk_healthy = disk_usage_percent < 90
        health_checks["disk"] = {
            "status": "healthy" if disk_healthy else "warning",
            "healthy": disk_healthy,
            "usage_percent": round(disk_usage_percent, 2)
        }
        
        # 如果有资源警告，降级整体状态
        if not (cpu_healthy and memory_healthy and disk_healthy) and overall_status == "healthy":
            overall_status = "degraded"
        
        # 活跃告警检查
        active_alerts = alert_manager.get_active_alerts()
        critical_alerts = [a for a in active_alerts if a.severity == AlertSeverity.CRITICAL]
        high_alerts = [a for a in active_alerts if a.severity == AlertSeverity.HIGH]
        
        health_checks["alerts"] = {
            "status": "healthy" if not critical_alerts else "critical",
            "healthy": len(critical_alerts) == 0,
            "active_alerts": len(active_alerts),
            "critical_alerts": len(critical_alerts),
            "high_alerts": len(high_alerts)
        }
        
        if critical_alerts:
            overall_status = "critical"
        elif high_alerts and overall_status in ["healthy", "degraded"]:
            overall_status = "degraded"
        
        return {
            "overall_status": overall_status,
            "timestamp": datetime.utcnow().isoformat(),
            "checks": health_checks,
            "summary": {
                "total_checks": len(health_checks),
                "healthy_checks": len([c for c in health_checks.values() if c.get("healthy", False)]),
                "active_alerts": len(active_alerts),
                "critical_alerts": len(critical_alerts)
            }
        }
        
    except Exception as e:
        logger.error(f"Failed to get health summary: {e}")
        return {
            "overall_status": "unknown",
            "timestamp": datetime.utcnow().isoformat(),
            "error": str(e)
        }


async def _collect_current_metrics(db: AsyncSession) -> Dict[str, Any]:
    """收集当前系统指标用于告警检查"""
    metrics = {}
    
    try:
        # 系统资源指标
        import psutil
        metrics["cpu_usage_percent"] = psutil.cpu_percent(interval=1)
        memory = psutil.virtual_memory()
        metrics["memory_usage_percent"] = memory.percent
        disk = psutil.disk_usage('/')
        metrics["disk_usage_percent"] = (disk.used / disk.total) * 100
        
        # 数据库指标
        db_info = await db_manager.get_connection_info()
        metrics.update(db_info)
        
        # 任务队列指标
        task_stats = task_throttler.get_stats()
        metrics.update(task_stats)
        
        # 数据库健康检查
        from ..database import check_database_health
        metrics["database_healthy"] = await check_database_health()
        
        # Redis健康检查
        try:
            from ..celery_app import redis_client
            await redis_client.ping()
            metrics["redis_healthy"] = True
        except:
            metrics["redis_healthy"] = False
        
        # 最近执行统计
        from ..models import MetricExecution
        from sqlalchemy import select, func
        
        last_hour = datetime.utcnow() - timedelta(hours=1)
        
        total_result = await db.execute(
            select(func.count(MetricExecution.id))
            .where(MetricExecution.created_at >= last_hour)
        )
        total_executions = total_result.scalar() or 0
        
        failed_result = await db.execute(
            select(func.count(MetricExecution.id))
            .where(
                and_(
                    MetricExecution.created_at >= last_hour,
                    MetricExecution.status == "failed"
                )
            )
        )
        failed_executions = failed_result.scalar() or 0
        
        metrics["total_executions"] = total_executions
        metrics["failed_executions"] = failed_executions
        
        # 最长执行时间
        max_duration_result = await db.execute(
            select(func.max(MetricExecution.duration_ms))
            .where(
                and_(
                    MetricExecution.created_at >= last_hour,
                    MetricExecution.duration_ms.isnot(None)
                )
            )
        )
        max_duration = max_duration_result.scalar() or 0
        metrics["max_execution_duration_ms"] = max_duration
        
    except Exception as e:
        logger.error(f"Error collecting metrics for alerts: {e}")
    
    return metrics


# 定期告警检查任务
async def periodic_alert_check():
    """定期告警检查"""
    while True:
        try:
            from ..database import AsyncSessionLocal
            async with AsyncSessionLocal() as db:
                metrics = await _collect_current_metrics(db)
                await alert_manager.check_alerts(metrics)
        except Exception as e:
            logger.error(f"Error in periodic alert check: {e}")
        
        # 每分钟检查一次
        await asyncio.sleep(60)


# 启动定期告警检查
def start_alert_monitoring():
    """启动告警监控"""
    asyncio.create_task(periodic_alert_check())