"""
监控和指标收集模块
"""
import time
import logging
import asyncio
from datetime import datetime, timedelta
from typing import Dict, Any, List, Optional
from prometheus_client import Counter, Histogram, Gauge, CollectorRegistry, generate_latest
from sqlalchemy import func, and_

from .config import get_config
from .database import DatabaseService
from .models import (
    ExecutionTask, ExecutionHistory, WorkerNode, TaskQueue,
    TaskStatus, NodeStatus, QueueStatus
)
from .utils import get_current_time

logger = logging.getLogger(__name__)


class MetricsCollector:
    """指标收集器"""
    
    def __init__(self):
        self.registry = CollectorRegistry()
        self._init_metrics()
        self.config = get_config().monitor
        self.running = False
        self._collection_task = None
    
    def _init_metrics(self):
        """初始化Prometheus指标"""
        # 任务相关指标
        self.task_total = Counter(
            'scheduler_tasks_total',
            'Total number of tasks',
            ['status', 'metric_name'],
            registry=self.registry
        )
        
        self.task_duration = Histogram(
            'scheduler_task_duration_seconds',
            'Task execution duration in seconds',
            ['metric_name'],
            registry=self.registry
        )
        
        self.task_queue_size = Gauge(
            'scheduler_task_queue_size',
            'Number of tasks in queue',
            ['status'],
            registry=self.registry
        )
        
        self.active_tasks = Gauge(
            'scheduler_active_tasks',
            'Number of currently active tasks',
            registry=self.registry
        )
        
        # 节点相关指标
        self.worker_nodes = Gauge(
            'scheduler_worker_nodes',
            'Number of worker nodes',
            ['status'],
            registry=self.registry
        )
        
        self.node_capacity = Gauge(
            'scheduler_node_capacity',
            'Total node capacity',
            registry=self.registry
        )
        
        self.node_utilization = Gauge(
            'scheduler_node_utilization',
            'Node utilization percentage',
            registry=self.registry
        )
        
        # 系统相关指标
        self.scheduler_uptime = Gauge(
            'scheduler_uptime_seconds',
            'Scheduler uptime in seconds',
            registry=self.registry
        )
        
        self.database_connections = Gauge(
            'scheduler_database_connections',
            'Number of database connections',
            registry=self.registry
        )
        
        self.error_rate = Gauge(
            'scheduler_error_rate',
            'Error rate percentage',
            registry=self.registry
        )
        
        # 性能指标
        self.throughput = Gauge(
            'scheduler_throughput_tasks_per_minute',
            'Tasks processed per minute',
            registry=self.registry
        )
        
        self.avg_response_time = Gauge(
            'scheduler_avg_response_time_seconds',
            'Average task response time',
            registry=self.registry
        )
    
    async def start(self):
        """启动指标收集"""
        if self.running:
            return
        
        self.running = True
        self.start_time = time.time()
        
        # 启动指标收集任务
        self._collection_task = asyncio.create_task(self._collect_metrics_loop())
        
        logger.info("Metrics collector started")
    
    async def stop(self):
        """停止指标收集"""
        if not self.running:
            return
        
        self.running = False
        
        if self._collection_task and not self._collection_task.done():
            self._collection_task.cancel()
            try:
                await self._collection_task
            except asyncio.CancelledError:
                pass
        
        logger.info("Metrics collector stopped")
    
    async def _collect_metrics_loop(self):
        """指标收集循环"""
        while self.running:
            try:
                await self._collect_metrics()
                await asyncio.sleep(self.config.health_check_interval)
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error collecting metrics: {e}")
                await asyncio.sleep(30)
    
    async def _collect_metrics(self):
        """收集指标数据"""
        try:
            with DatabaseService() as db:
                # 收集任务指标
                await self._collect_task_metrics(db)
                
                # 收集节点指标
                await self._collect_node_metrics(db)
                
                # 收集系统指标
                await self._collect_system_metrics(db)
                
                # 收集性能指标
                await self._collect_performance_metrics(db)
        
        except Exception as e:
            logger.error(f"Error in metrics collection: {e}")
    
    async def _collect_task_metrics(self, db: DatabaseService):
        """收集任务相关指标"""
        # 任务状态统计
        task_stats = db.query(
            ExecutionTask.status,
            func.count(ExecutionTask.id).label('count')
        ).group_by(ExecutionTask.status).all()
        
        # 更新任务队列大小指标
        for status in QueueStatus:
            count = db.query(TaskQueue).filter(
                TaskQueue.status == status.value
            ).count()
            self.task_queue_size.labels(status=status.value.lower()).set(count)
        
        # 活跃任务数
        active_count = db.query(ExecutionTask).filter(
            ExecutionTask.status == TaskStatus.RUNNING
        ).count()
        self.active_tasks.set(active_count)
        
        # 任务执行时长统计
        recent_tasks = db.query(ExecutionTask).filter(
            and_(
                ExecutionTask.end_time.isnot(None),
                ExecutionTask.duration_ms.isnot(None),
                ExecutionTask.end_time >= get_current_time() - timedelta(hours=1)
            )
        ).all()
        
        for task in recent_tasks:
            if task.duration_ms:
                duration_seconds = task.duration_ms / 1000.0
                metric_name = task.metric.name if task.metric else 'unknown'
                self.task_duration.labels(metric_name=metric_name).observe(duration_seconds)
    
    async def _collect_node_metrics(self, db: DatabaseService):
        """收集节点相关指标"""
        # 节点状态统计
        for status in NodeStatus:
            count = db.query(WorkerNode).filter(
                WorkerNode.status == status.value
            ).count()
            self.worker_nodes.labels(status=status.value.lower()).set(count)
        
        # 节点容量和利用率
        nodes = db.query(WorkerNode).filter(
            WorkerNode.status == NodeStatus.ONLINE
        ).all()
        
        total_capacity = sum(node.max_concurrent_tasks for node in nodes)
        current_load = sum(node.current_tasks for node in nodes)
        
        self.node_capacity.set(total_capacity)
        
        if total_capacity > 0:
            utilization = (current_load / total_capacity) * 100
            self.node_utilization.set(utilization)
        else:
            self.node_utilization.set(0)
    
    async def _collect_system_metrics(self, db: DatabaseService):
        """收集系统相关指标"""
        # 系统运行时间
        uptime = time.time() - self.start_time
        self.scheduler_uptime.set(uptime)
        
        # 错误率计算
        one_hour_ago = get_current_time() - timedelta(hours=1)
        
        total_tasks = db.query(ExecutionTask).filter(
            ExecutionTask.created_at >= one_hour_ago
        ).count()
        
        failed_tasks = db.query(ExecutionTask).filter(
            and_(
                ExecutionTask.created_at >= one_hour_ago,
                ExecutionTask.status.in_([TaskStatus.FAILED, TaskStatus.TIMEOUT])
            )
        ).count()
        
        if total_tasks > 0:
            error_rate = (failed_tasks / total_tasks) * 100
            self.error_rate.set(error_rate)
        else:
            self.error_rate.set(0)
    
    async def _collect_performance_metrics(self, db: DatabaseService):
        """收集性能相关指标"""
        # 吞吐量计算（每分钟处理的任务数）
        one_minute_ago = get_current_time() - timedelta(minutes=1)
        
        completed_tasks = db.query(ExecutionTask).filter(
            and_(
                ExecutionTask.end_time >= one_minute_ago,
                ExecutionTask.status.in_([TaskStatus.SUCCESS, TaskStatus.FAILED])
            )
        ).count()
        
        self.throughput.set(completed_tasks)
        
        # 平均响应时间
        recent_tasks = db.query(ExecutionTask).filter(
            and_(
                ExecutionTask.end_time >= get_current_time() - timedelta(minutes=5),
                ExecutionTask.duration_ms.isnot(None)
            )
        ).all()
        
        if recent_tasks:
            avg_duration = sum(task.duration_ms for task in recent_tasks) / len(recent_tasks)
            self.avg_response_time.set(avg_duration / 1000.0)  # 转换为秒
        else:
            self.avg_response_time.set(0)
    
    def get_metrics(self) -> str:
        """获取Prometheus格式的指标数据"""
        return generate_latest(self.registry).decode('utf-8')
    
    def get_health_status(self) -> Dict[str, Any]:
        """获取健康状态"""
        with DatabaseService() as db:
            try:
                # 数据库连接检查
                db.execute("SELECT 1")
                db_healthy = True
            except Exception:
                db_healthy = False
            
            # 节点状态检查
            online_nodes = db.query(WorkerNode).filter(
                WorkerNode.status == NodeStatus.ONLINE
            ).count()
            
            # 队列状态检查
            queued_tasks = db.query(TaskQueue).filter(
                TaskQueue.status == QueueStatus.QUEUED
            ).count()
            
            # 错误率检查
            one_hour_ago = get_current_time() - timedelta(hours=1)
            total_recent = db.query(ExecutionTask).filter(
                ExecutionTask.created_at >= one_hour_ago
            ).count()
            
            failed_recent = db.query(ExecutionTask).filter(
                and_(
                    ExecutionTask.created_at >= one_hour_ago,
                    ExecutionTask.status.in_([TaskStatus.FAILED, TaskStatus.TIMEOUT])
                )
            ).count()
            
            error_rate = (failed_recent / total_recent * 100) if total_recent > 0 else 0
            
            # 综合健康状态
            is_healthy = (
                db_healthy and
                online_nodes > 0 and
                error_rate < 50  # 错误率低于50%
            )
            
            return {
                "healthy": is_healthy,
                "timestamp": get_current_time().isoformat(),
                "components": {
                    "database": {"healthy": db_healthy},
                    "workers": {
                        "healthy": online_nodes > 0,
                        "online_count": online_nodes
                    },
                    "queue": {
                        "healthy": True,
                        "queued_tasks": queued_tasks
                    },
                    "error_rate": {
                        "healthy": error_rate < 50,
                        "rate": error_rate
                    }
                },
                "metrics": {
                    "uptime_seconds": time.time() - self.start_time,
                    "total_tasks_last_hour": total_recent,
                    "failed_tasks_last_hour": failed_recent,
                    "error_rate_percent": error_rate
                }
            }


class AlertManager:
    """告警管理器"""
    
    def __init__(self, metrics_collector: MetricsCollector):
        self.metrics_collector = metrics_collector
        self.config = get_config()
        self.alert_rules = self._init_alert_rules()
        self.running = False
        self._alert_task = None
    
    def _init_alert_rules(self) -> List[Dict[str, Any]]:
        """初始化告警规则"""
        return [
            {
                "name": "high_error_rate",
                "condition": lambda metrics: metrics.get("error_rate_percent", 0) > 20,
                "message": "Error rate is above 20%",
                "severity": "warning"
            },
            {
                "name": "no_online_workers",
                "condition": lambda metrics: metrics["components"]["workers"]["online_count"] == 0,
                "message": "No online worker nodes available",
                "severity": "critical"
            },
            {
                "name": "high_queue_backlog",
                "condition": lambda metrics: metrics["components"]["queue"]["queued_tasks"] > 1000,
                "message": "Task queue backlog is high (>1000 tasks)",
                "severity": "warning"
            },
            {
                "name": "database_unhealthy",
                "condition": lambda metrics: not metrics["components"]["database"]["healthy"],
                "message": "Database connection is unhealthy",
                "severity": "critical"
            }
        ]
    
    async def start(self):
        """启动告警管理器"""
        if self.running:
            return
        
        self.running = True
        self._alert_task = asyncio.create_task(self._check_alerts_loop())
        
        logger.info("Alert manager started")
    
    async def stop(self):
        """停止告警管理器"""
        if not self.running:
            return
        
        self.running = False
        
        if self._alert_task and not self._alert_task.done():
            self._alert_task.cancel()
            try:
                await self._alert_task
            except asyncio.CancelledError:
                pass
        
        logger.info("Alert manager stopped")
    
    async def _check_alerts_loop(self):
        """告警检查循环"""
        while self.running:
            try:
                await self._check_alerts()
                await asyncio.sleep(60)  # 每分钟检查一次
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error checking alerts: {e}")
                await asyncio.sleep(30)
    
    async def _check_alerts(self):
        """检查告警条件"""
        try:
            health_status = self.metrics_collector.get_health_status()
            
            for rule in self.alert_rules:
                try:
                    if rule["condition"](health_status):
                        await self._trigger_alert(rule, health_status)
                except Exception as e:
                    logger.error(f"Error evaluating alert rule {rule['name']}: {e}")
        
        except Exception as e:
            logger.error(f"Error in alert checking: {e}")
    
    async def _trigger_alert(self, rule: Dict[str, Any], context: Dict[str, Any]):
        """触发告警"""
        alert_data = {
            "rule_name": rule["name"],
            "message": rule["message"],
            "severity": rule["severity"],
            "timestamp": get_current_time().isoformat(),
            "context": context
        }
        
        # 记录告警日志
        if rule["severity"] == "critical":
            logger.critical(f"ALERT: {rule['message']}")
        else:
            logger.warning(f"ALERT: {rule['message']}")
        
        # 这里可以扩展其他告警通知方式
        # 例如：发送邮件、Slack通知、webhook等
        await self._send_alert_notification(alert_data)
    
    async def _send_alert_notification(self, alert_data: Dict[str, Any]):
        """发送告警通知"""
        # 这里可以实现具体的通知逻辑
        # 例如：邮件、短信、webhook等
        logger.info(f"Alert notification: {alert_data}")


class PerformanceMonitor:
    """性能监控器"""
    
    def __init__(self):
        self.config = get_config()
        self.running = False
        self._monitor_task = None
    
    async def start(self):
        """启动性能监控"""
        if self.running:
            return
        
        self.running = True
        self._monitor_task = asyncio.create_task(self._monitor_performance_loop())
        
        logger.info("Performance monitor started")
    
    async def stop(self):
        """停止性能监控"""
        if not self.running:
            return
        
        self.running = False
        
        if self._monitor_task and not self._monitor_task.done():
            self._monitor_task.cancel()
            try:
                await self._monitor_task
            except asyncio.CancelledError:
                pass
        
        logger.info("Performance monitor stopped")
    
    async def _monitor_performance_loop(self):
        """性能监控循环"""
        while self.running:
            try:
                await self._collect_performance_data()
                await asyncio.sleep(300)  # 每5分钟收集一次
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in performance monitoring: {e}")
                await asyncio.sleep(60)
    
    async def _collect_performance_data(self):
        """收集性能数据"""
        with DatabaseService() as db:
            # 收集最近一小时的性能数据
            one_hour_ago = get_current_time() - timedelta(hours=1)
            
            # 任务执行统计
            tasks = db.query(ExecutionTask).filter(
                and_(
                    ExecutionTask.end_time >= one_hour_ago,
                    ExecutionTask.duration_ms.isnot(None)
                )
            ).all()
            
            if tasks:
                durations = [task.duration_ms for task in tasks]
                
                performance_data = {
                    "timestamp": get_current_time().isoformat(),
                    "period": "1h",
                    "task_count": len(tasks),
                    "avg_duration_ms": sum(durations) / len(durations),
                    "min_duration_ms": min(durations),
                    "max_duration_ms": max(durations),
                    "success_rate": len([t for t in tasks if t.status == TaskStatus.SUCCESS]) / len(tasks) * 100
                }
                
                logger.info(f"Performance data: {performance_data}")
                
                # 这里可以将性能数据存储到时序数据库或发送到监控系统


# 全局监控实例
metrics_collector = MetricsCollector()
alert_manager = AlertManager(metrics_collector)
performance_monitor = PerformanceMonitor()