import asyncio
import psutil
from datetime import datetime, timedelta
from typing import Dict, Any
from sqlalchemy import select, delete
from loguru import logger

from ..celery_app import celery_app
from ..database import db_manager, check_database_health
from ..models import MetricExecution, SystemMetric
from ..protection import task_throttler
from ..config import settings


@celery_app.task
def cleanup_old_executions():
    """清理旧的执行记录"""
    return asyncio.run(_cleanup_old_executions_async())


async def _cleanup_old_executions_async():
    """异步清理旧的执行记录"""
    cutoff_date = datetime.utcnow() - timedelta(days=settings.metric_execution_history_days)
    
    async with db_manager.get_session() as session:
        try:
            # 删除旧的执行记录
            result = await session.execute(
                delete(MetricExecution).where(MetricExecution.created_at < cutoff_date)
            )
            deleted_count = result.rowcount
            await session.commit()
            
            logger.info(f"Cleaned up {deleted_count} old execution records")
            
            # 清理旧的系统指标
            system_cutoff_date = datetime.utcnow() - timedelta(days=7)  # 保留7天的系统指标
            system_result = await session.execute(
                delete(SystemMetric).where(SystemMetric.timestamp < system_cutoff_date)
            )
            system_deleted_count = system_result.rowcount
            await session.commit()
            
            logger.info(f"Cleaned up {system_deleted_count} old system metrics")
            
            return {
                "execution_records_deleted": deleted_count,
                "system_metrics_deleted": system_deleted_count,
                "cutoff_date": cutoff_date.isoformat()
            }
            
        except Exception as e:
            await session.rollback()
            logger.error(f"Failed to cleanup old records: {e}")
            raise


@celery_app.task
def collect_system_metrics():
    """收集系统指标"""
    return asyncio.run(_collect_system_metrics_async())


async def _collect_system_metrics_async():
    """异步收集系统指标"""
    timestamp = datetime.utcnow()
    metrics_collected = []
    
    try:
        # 收集CPU指标
        cpu_percent = psutil.cpu_percent(interval=1)
        cpu_count = psutil.cpu_count()
        load_avg = psutil.getloadavg() if hasattr(psutil, 'getloadavg') else [0, 0, 0]
        
        await _save_system_metric("cpu_usage_percent", cpu_percent, {"cores": cpu_count}, timestamp)
        await _save_system_metric("load_average_1m", load_avg[0], {}, timestamp)
        await _save_system_metric("load_average_5m", load_avg[1], {}, timestamp)
        await _save_system_metric("load_average_15m", load_avg[2], {}, timestamp)
        
        metrics_collected.extend(["cpu_usage_percent", "load_average_1m", "load_average_5m", "load_average_15m"])
        
        # 收集内存指标
        memory = psutil.virtual_memory()
        await _save_system_metric("memory_usage_percent", memory.percent, {"total_gb": round(memory.total / (1024**3), 2)}, timestamp)
        await _save_system_metric("memory_available_gb", round(memory.available / (1024**3), 2), {}, timestamp)
        await _save_system_metric("memory_used_gb", round(memory.used / (1024**3), 2), {}, timestamp)
        
        metrics_collected.extend(["memory_usage_percent", "memory_available_gb", "memory_used_gb"])
        
        # 收集磁盘指标
        disk = psutil.disk_usage('/')
        await _save_system_metric("disk_usage_percent", round((disk.used / disk.total) * 100, 2), {"total_gb": round(disk.total / (1024**3), 2)}, timestamp)
        await _save_system_metric("disk_free_gb", round(disk.free / (1024**3), 2), {}, timestamp)
        
        metrics_collected.extend(["disk_usage_percent", "disk_free_gb"])
        
        # 收集网络指标
        network = psutil.net_io_counters()
        await _save_system_metric("network_bytes_sent", network.bytes_sent, {}, timestamp)
        await _save_system_metric("network_bytes_recv", network.bytes_recv, {}, timestamp)
        await _save_system_metric("network_packets_sent", network.packets_sent, {}, timestamp)
        await _save_system_metric("network_packets_recv", network.packets_recv, {}, timestamp)
        
        metrics_collected.extend(["network_bytes_sent", "network_bytes_recv", "network_packets_sent", "network_packets_recv"])
        
        # 收集数据库连接池指标
        from ..database import db_manager
        db_info = await db_manager.get_connection_info()
        
        await _save_system_metric("db_pool_size", db_info["pool_size"], {}, timestamp)
        await _save_system_metric("db_checked_out", db_info["checked_out"], {}, timestamp)
        await _save_system_metric("db_checked_in", db_info["checked_in"], {}, timestamp)
        await _save_system_metric("db_overflow", db_info["overflow"], {}, timestamp)
        await _save_system_metric("db_active_connections", db_info["active_connections"], {}, timestamp)
        
        metrics_collected.extend(["db_pool_size", "db_checked_out", "db_checked_in", "db_overflow", "db_active_connections"])
        
        # 收集任务节流器指标
        throttler_stats = task_throttler.get_stats()
        await _save_system_metric("active_tasks", throttler_stats["active_tasks"], {}, timestamp)
        await _save_system_metric("available_task_slots", throttler_stats["available_slots"], {}, timestamp)
        await _save_system_metric("max_concurrent_tasks", throttler_stats["max_concurrent"], {}, timestamp)
        
        metrics_collected.extend(["active_tasks", "available_task_slots", "max_concurrent_tasks"])
        
        # 检查数据库健康状态
        db_healthy = await check_database_health()
        await _save_system_metric("database_healthy", 1 if db_healthy else 0, {}, timestamp)
        
        metrics_collected.append("database_healthy")
        
        logger.info(f"Collected {len(metrics_collected)} system metrics")
        
        return {
            "metrics_collected": len(metrics_collected),
            "metric_names": metrics_collected,
            "timestamp": timestamp.isoformat()
        }
        
    except Exception as e:
        logger.error(f"Failed to collect system metrics: {e}")
        raise


async def _save_system_metric(name: str, value: float, tags: Dict[str, Any], timestamp: datetime):
    """保存系统指标"""
    async with db_manager.get_session() as session:
        try:
            metric = SystemMetric(
                metric_name=name,
                metric_value=value,
                tags=tags,
                timestamp=timestamp
            )
            session.add(metric)
            await session.commit()
        except Exception as e:
            await session.rollback()
            logger.error(f"Failed to save system metric {name}: {e}")


@celery_app.task
def health_check():
    """系统健康检查"""
    return asyncio.run(_health_check_async())


async def _health_check_async():
    """异步系统健康检查"""
    health_status = {
        "timestamp": datetime.utcnow().isoformat(),
        "status": "healthy",
        "checks": {}
    }
    
    try:
        # 检查数据库连接
        db_healthy = await check_database_health()
        health_status["checks"]["database"] = {
            "status": "healthy" if db_healthy else "unhealthy",
            "healthy": db_healthy
        }
        
        # 检查Redis连接
        try:
            from ..celery_app import redis_client
            await redis_client.ping()
            health_status["checks"]["redis"] = {
                "status": "healthy",
                "healthy": True
            }
        except Exception as e:
            health_status["checks"]["redis"] = {
                "status": "unhealthy",
                "healthy": False,
                "error": str(e)
            }
        
        # 检查系统资源
        cpu_percent = psutil.cpu_percent(interval=1)
        memory = psutil.virtual_memory()
        disk = psutil.disk_usage('/')
        
        # CPU检查
        cpu_healthy = cpu_percent < 90
        health_status["checks"]["cpu"] = {
            "status": "healthy" if cpu_healthy else "warning",
            "healthy": cpu_healthy,
            "usage_percent": cpu_percent
        }
        
        # 内存检查
        memory_healthy = memory.percent < 90
        health_status["checks"]["memory"] = {
            "status": "healthy" if memory_healthy else "warning",
            "healthy": memory_healthy,
            "usage_percent": memory.percent
        }
        
        # 磁盘检查
        disk_usage_percent = (disk.used / disk.total) * 100
        disk_healthy = disk_usage_percent < 90
        health_status["checks"]["disk"] = {
            "status": "healthy" if disk_healthy else "warning",
            "healthy": disk_healthy,
            "usage_percent": round(disk_usage_percent, 2)
        }
        
        # 检查任务队列
        throttler_stats = task_throttler.get_stats()
        queue_healthy = throttler_stats["active_tasks"] < throttler_stats["max_concurrent"]
        health_status["checks"]["task_queue"] = {
            "status": "healthy" if queue_healthy else "warning",
            "healthy": queue_healthy,
            "active_tasks": throttler_stats["active_tasks"],
            "max_concurrent": throttler_stats["max_concurrent"]
        }
        
        # 总体健康状态
        all_healthy = all(
            check.get("healthy", True) for check in health_status["checks"].values()
        )
        
        if not all_healthy:
            health_status["status"] = "degraded"
        
        # 如果数据库或Redis不健康，标记为不健康
        if not health_status["checks"]["database"]["healthy"] or not health_status["checks"]["redis"]["healthy"]:
            health_status["status"] = "unhealthy"
        
        logger.info(f"Health check completed: {health_status['status']}")
        
        return health_status
        
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        health_status["status"] = "unhealthy"
        health_status["error"] = str(e)
        return health_status


@celery_app.task
def generate_system_report():
    """生成系统报告"""
    return asyncio.run(_generate_system_report_async())


async def _generate_system_report_async():
    """异步生成系统报告"""
    report_time = datetime.utcnow()
    
    async with db_manager.get_session() as session:
        try:
            # 查询最近24小时的执行统计
            last_24h = report_time - timedelta(hours=24)
            
            # 执行统计
            execution_result = await session.execute(
                select(MetricExecution.status, MetricExecution.company_id, MetricExecution.metric_id)
                .where(MetricExecution.created_at > last_24h)
            )
            executions = execution_result.fetchall()
            
            # 统计各种状态的执行数量
            status_counts = {}
            company_counts = {}
            metric_counts = {}
            
            for execution in executions:
                status = execution.status
                company_id = execution.company_id
                metric_id = execution.metric_id
                
                status_counts[status] = status_counts.get(status, 0) + 1
                company_counts[company_id] = company_counts.get(company_id, 0) + 1
                metric_counts[metric_id] = metric_counts.get(metric_id, 0) + 1
            
            # 查询系统指标统计
            system_metrics_result = await session.execute(
                select(SystemMetric.metric_name, SystemMetric.metric_value)
                .where(SystemMetric.timestamp > report_time - timedelta(hours=1))
                .order_by(SystemMetric.timestamp.desc())
            )
            recent_system_metrics = system_metrics_result.fetchall()
            
            # 生成报告
            report = {
                "report_time": report_time.isoformat(),
                "period": "last_24_hours",
                "execution_summary": {
                    "total_executions": len(executions),
                    "status_breakdown": status_counts,
                    "companies_active": len(company_counts),
                    "metrics_executed": len(metric_counts),
                    "success_rate": round(
                        status_counts.get("completed", 0) / max(len(executions), 1) * 100, 2
                    )
                },
                "system_metrics": {
                    metric.metric_name: metric.metric_value 
                    for metric in recent_system_metrics[:20]  # 最近20个指标
                },
                "top_companies": sorted(
                    company_counts.items(), 
                    key=lambda x: x[1], 
                    reverse=True
                )[:10],
                "top_metrics": sorted(
                    metric_counts.items(), 
                    key=lambda x: x[1], 
                    reverse=True
                )[:10]
            }
            
            logger.info(f"System report generated: {report['execution_summary']['total_executions']} executions")
            
            return report
            
        except Exception as e:
            logger.error(f"Failed to generate system report: {e}")
            raise