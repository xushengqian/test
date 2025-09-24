"""
指标调度器模块
支持cron表达式、优先级队列、任务分发
"""
import asyncio
import logging
import uuid
from datetime import datetime, timedelta
from typing import List, Dict, Any, Optional
from croniter import croniter
from sqlalchemy import and_, or_
from sqlalchemy.orm import Session

from .config import get_config
from .database import DatabaseService
from .models import (
    MetricDefinition, ScheduleConfig, ExecutionTask, TaskQueue,
    WorkerNode, TaskStatus, QueueStatus, NodeStatus
)
from .utils import get_current_time, generate_task_id

logger = logging.getLogger(__name__)


class TaskScheduler:
    """任务调度器"""
    
    def __init__(self):
        self.config = get_config().scheduler
        self.running = False
        self._scan_task = None
        self._dispatch_task = None
        self._cleanup_task = None
    
    async def start(self):
        """启动调度器"""
        if self.running:
            logger.warning("Scheduler is already running")
            return
        
        self.running = True
        logger.info("Starting task scheduler...")
        
        # 启动各个后台任务
        self._scan_task = asyncio.create_task(self._scan_schedules())
        self._dispatch_task = asyncio.create_task(self._dispatch_tasks())
        self._cleanup_task = asyncio.create_task(self._cleanup_expired_tasks())
        
        logger.info("Task scheduler started successfully")
    
    async def stop(self):
        """停止调度器"""
        if not self.running:
            return
        
        logger.info("Stopping task scheduler...")
        self.running = False
        
        # 取消后台任务
        for task in [self._scan_task, self._dispatch_task, self._cleanup_task]:
            if task and not task.done():
                task.cancel()
                try:
                    await task
                except asyncio.CancelledError:
                    pass
        
        logger.info("Task scheduler stopped")
    
    async def _scan_schedules(self):
        """扫描调度配置，生成待执行任务"""
        while self.running:
            try:
                await self._generate_pending_tasks()
                await asyncio.sleep(self.config.scan_interval)
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in schedule scanning: {e}")
                await asyncio.sleep(5)  # 出错时短暂等待
    
    async def _dispatch_tasks(self):
        """分发任务到工作节点"""
        while self.running:
            try:
                await self._dispatch_queued_tasks()
                await asyncio.sleep(1)  # 更频繁的任务分发
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in task dispatching: {e}")
                await asyncio.sleep(5)
    
    async def _cleanup_expired_tasks(self):
        """清理过期和超时任务"""
        while self.running:
            try:
                await self._cleanup_timeout_tasks()
                await self._cleanup_old_tasks()
                await asyncio.sleep(60)  # 每分钟清理一次
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in task cleanup: {e}")
                await asyncio.sleep(30)
    
    async def _generate_pending_tasks(self):
        """生成待执行任务"""
        current_time = get_current_time()
        scan_window = current_time + timedelta(seconds=self.config.scan_interval * 2)
        
        with DatabaseService() as db:
            # 查询启用的调度配置
            schedules = db.query(ScheduleConfig).join(MetricDefinition).filter(
                and_(
                    ScheduleConfig.is_enabled == True,
                    MetricDefinition.is_active == True,
                    or_(
                        ScheduleConfig.start_time.is_(None),
                        ScheduleConfig.start_time <= current_time
                    ),
                    or_(
                        ScheduleConfig.end_time.is_(None),
                        ScheduleConfig.end_time >= current_time
                    )
                )
            ).all()
            
            tasks_created = 0
            for schedule in schedules:
                try:
                    # 计算下次执行时间
                    next_times = self._calculate_next_execution_times(
                        schedule.cron_expression, current_time, scan_window
                    )
                    
                    for next_time in next_times:
                        # 检查是否已存在相同的任务
                        existing_task = db.query(ExecutionTask).filter(
                            and_(
                                ExecutionTask.schedule_id == schedule.id,
                                ExecutionTask.scheduled_time == next_time,
                                ExecutionTask.status.in_([
                                    TaskStatus.PENDING, TaskStatus.RUNNING
                                ])
                            )
                        ).first()
                        
                        if existing_task:
                            continue
                        
                        # 检查并发限制
                        running_count = db.query(ExecutionTask).filter(
                            and_(
                                ExecutionTask.schedule_id == schedule.id,
                                ExecutionTask.status == TaskStatus.RUNNING
                            )
                        ).count()
                        
                        if running_count >= schedule.max_concurrent:
                            logger.debug(f"Schedule {schedule.id} reached max concurrent limit")
                            continue
                        
                        # 创建新任务
                        task = ExecutionTask(
                            metric_id=schedule.metric_id,
                            schedule_id=schedule.id,
                            task_id=generate_task_id(),
                            status=TaskStatus.PENDING,
                            priority=schedule.priority,
                            scheduled_time=next_time,
                            parameters=schedule.parameters or {}
                        )
                        
                        db.add(task)
                        
                        # 添加到任务队列
                        queue_item = TaskQueue(
                            task_id=task.task_id,
                            priority=schedule.priority,
                            scheduled_time=next_time,
                            payload={
                                "task_id": task.task_id,
                                "metric_id": schedule.metric_id,
                                "schedule_id": schedule.id,
                                "parameters": schedule.parameters or {}
                            },
                            status=QueueStatus.QUEUED
                        )
                        
                        db.add(queue_item)
                        tasks_created += 1
                
                except Exception as e:
                    logger.error(f"Error generating tasks for schedule {schedule.id}: {e}")
                    continue
            
            if tasks_created > 0:
                db.commit()
                logger.info(f"Generated {tasks_created} new tasks")
    
    def _calculate_next_execution_times(
        self, 
        cron_expression: str, 
        start_time: datetime, 
        end_time: datetime
    ) -> List[datetime]:
        """计算指定时间窗口内的下次执行时间"""
        try:
            cron = croniter(cron_expression, start_time)
            next_times = []
            
            while True:
                next_time = cron.get_next(datetime)
                if next_time > end_time:
                    break
                next_times.append(next_time)
                
                # 限制单次扫描生成的任务数量
                if len(next_times) >= 10:
                    break
            
            return next_times
        
        except Exception as e:
            logger.error(f"Error calculating next execution times for cron '{cron_expression}': {e}")
            return []
    
    async def _dispatch_queued_tasks(self):
        """分发队列中的任务"""
        with DatabaseService() as db:
            # 获取可用的工作节点
            available_nodes = db.query(WorkerNode).filter(
                and_(
                    WorkerNode.status == NodeStatus.ONLINE,
                    WorkerNode.current_tasks < WorkerNode.max_concurrent_tasks
                )
            ).order_by(WorkerNode.current_tasks.asc()).all()
            
            if not available_nodes:
                return
            
            # 获取待分发的任务（按优先级和计划时间排序）
            current_time = get_current_time()
            queued_tasks = db.query(TaskQueue).filter(
                and_(
                    TaskQueue.status == QueueStatus.QUEUED,
                    TaskQueue.scheduled_time <= current_time
                )
            ).order_by(
                TaskQueue.priority.asc(),
                TaskQueue.scheduled_time.asc()
            ).limit(self.config.queue_batch_size).all()
            
            dispatched_count = 0
            for task in queued_tasks:
                # 选择最空闲的节点
                best_node = min(available_nodes, key=lambda n: n.current_tasks)
                
                if best_node.current_tasks >= best_node.max_concurrent_tasks:
                    break  # 所有节点都满了
                
                try:
                    # 更新任务队列状态
                    task.status = QueueStatus.PROCESSING
                    task.worker_id = best_node.node_id
                    
                    # 更新执行任务状态
                    execution_task = db.query(ExecutionTask).filter(
                        ExecutionTask.task_id == task.task_id
                    ).first()
                    
                    if execution_task:
                        execution_task.status = TaskStatus.RUNNING
                        execution_task.start_time = current_time
                        execution_task.worker_id = best_node.node_id
                    
                    # 更新节点当前任务数
                    best_node.current_tasks += 1
                    
                    dispatched_count += 1
                    logger.debug(f"Dispatched task {task.task_id} to node {best_node.node_id}")
                
                except Exception as e:
                    logger.error(f"Error dispatching task {task.task_id}: {e}")
                    # 回滚任务状态
                    task.status = QueueStatus.QUEUED
                    task.worker_id = None
                    continue
            
            if dispatched_count > 0:
                db.commit()
                logger.info(f"Dispatched {dispatched_count} tasks")
    
    async def _cleanup_timeout_tasks(self):
        """清理超时任务"""
        current_time = get_current_time()
        
        with DatabaseService() as db:
            # 查找超时的运行中任务
            timeout_threshold = current_time - timedelta(seconds=self.config.task_timeout)
            
            timeout_tasks = db.query(ExecutionTask).filter(
                and_(
                    ExecutionTask.status == TaskStatus.RUNNING,
                    ExecutionTask.start_time < timeout_threshold
                )
            ).all()
            
            timeout_count = 0
            for task in timeout_tasks:
                try:
                    # 更新任务状态为超时
                    task.status = TaskStatus.TIMEOUT
                    task.end_time = current_time
                    task.error_message = f"Task timeout after {self.config.task_timeout} seconds"
                    
                    # 更新队列状态
                    queue_item = db.query(TaskQueue).filter(
                        TaskQueue.task_id == task.task_id
                    ).first()
                    if queue_item:
                        queue_item.status = QueueStatus.COMPLETED
                    
                    # 释放工作节点资源
                    if task.worker_id:
                        worker = db.query(WorkerNode).filter(
                            WorkerNode.node_id == task.worker_id
                        ).first()
                        if worker and worker.current_tasks > 0:
                            worker.current_tasks -= 1
                    
                    timeout_count += 1
                    logger.warning(f"Task {task.task_id} marked as timeout")
                
                except Exception as e:
                    logger.error(f"Error handling timeout task {task.task_id}: {e}")
                    continue
            
            if timeout_count > 0:
                db.commit()
                logger.info(f"Cleaned up {timeout_count} timeout tasks")
    
    async def _cleanup_old_tasks(self):
        """清理旧任务记录"""
        current_time = get_current_time()
        retention_days = 7  # 保留7天的任务记录
        cutoff_time = current_time - timedelta(days=retention_days)
        
        with DatabaseService() as db:
            # 删除旧的已完成任务队列记录
            deleted_queue = db.query(TaskQueue).filter(
                and_(
                    TaskQueue.status == QueueStatus.COMPLETED,
                    TaskQueue.created_at < cutoff_time
                )
            ).delete()
            
            # 将旧的执行任务移动到历史表
            old_tasks = db.query(ExecutionTask).filter(
                and_(
                    ExecutionTask.status.in_([
                        TaskStatus.SUCCESS, TaskStatus.FAILED, 
                        TaskStatus.TIMEOUT, TaskStatus.CANCELLED
                    ]),
                    ExecutionTask.created_at < cutoff_time
                )
            ).all()
            
            # 这里可以实现将任务移动到历史表的逻辑
            # 为了简化，这里直接删除
            if old_tasks:
                for task in old_tasks:
                    db.delete(task)
            
            if deleted_queue > 0 or old_tasks:
                db.commit()
                logger.info(f"Cleaned up {deleted_queue} queue records and {len(old_tasks)} old tasks")
    
    async def submit_task(self, metric_id: int, parameters: Dict[str, Any] = None) -> str:
        """手动提交任务"""
        task_id = generate_task_id()
        current_time = get_current_time()
        
        with DatabaseService() as db:
            # 获取指标定义
            metric = db.query(MetricDefinition).filter(
                MetricDefinition.id == metric_id
            ).first()
            
            if not metric:
                raise ValueError(f"Metric {metric_id} not found")
            
            if not metric.is_active:
                raise ValueError(f"Metric {metric_id} is not active")
            
            # 创建执行任务
            task = ExecutionTask(
                metric_id=metric_id,
                schedule_id=0,  # 手动任务没有调度配置
                task_id=task_id,
                status=TaskStatus.PENDING,
                priority=1,  # 手动任务高优先级
                scheduled_time=current_time,
                parameters=parameters or {}
            )
            
            db.add(task)
            
            # 添加到任务队列
            queue_item = TaskQueue(
                task_id=task_id,
                priority=1,
                scheduled_time=current_time,
                payload={
                    "task_id": task_id,
                    "metric_id": metric_id,
                    "schedule_id": 0,
                    "parameters": parameters or {}
                },
                status=QueueStatus.QUEUED
            )
            
            db.add(queue_item)
            db.commit()
            
            logger.info(f"Manual task {task_id} submitted for metric {metric_id}")
            return task_id
    
    async def cancel_task(self, task_id: str) -> bool:
        """取消任务"""
        with DatabaseService() as db:
            task = db.query(ExecutionTask).filter(
                ExecutionTask.task_id == task_id
            ).first()
            
            if not task:
                return False
            
            if task.status not in [TaskStatus.PENDING, TaskStatus.RUNNING]:
                return False
            
            # 更新任务状态
            task.status = TaskStatus.CANCELLED
            task.end_time = get_current_time()
            task.error_message = "Task cancelled by user"
            
            # 更新队列状态
            queue_item = db.query(TaskQueue).filter(
                TaskQueue.task_id == task_id
            ).first()
            if queue_item:
                queue_item.status = QueueStatus.COMPLETED
            
            # 释放工作节点资源
            if task.worker_id:
                worker = db.query(WorkerNode).filter(
                    WorkerNode.node_id == task.worker_id
                ).first()
                if worker and worker.current_tasks > 0:
                    worker.current_tasks -= 1
            
            db.commit()
            logger.info(f"Task {task_id} cancelled")
            return True
    
    def get_scheduler_status(self) -> Dict[str, Any]:
        """获取调度器状态"""
        with DatabaseService() as db:
            # 统计各状态任务数量
            task_stats = {}
            for status in TaskStatus:
                count = db.query(ExecutionTask).filter(
                    ExecutionTask.status == status.value
                ).count()
                task_stats[status.value.lower()] = count
            
            # 统计节点状态
            node_stats = {}
            for status in NodeStatus:
                count = db.query(WorkerNode).filter(
                    WorkerNode.status == status.value
                ).count()
                node_stats[status.value.lower()] = count
            
            # 队列统计
            queue_stats = {}
            for status in QueueStatus:
                count = db.query(TaskQueue).filter(
                    TaskQueue.status == status.value
                ).count()
                queue_stats[status.value.lower()] = count
            
            return {
                "running": self.running,
                "config": {
                    "scan_interval": self.config.scan_interval,
                    "max_concurrent_tasks": self.config.max_concurrent_tasks,
                    "task_timeout": self.config.task_timeout,
                },
                "statistics": {
                    "tasks": task_stats,
                    "nodes": node_stats,
                    "queue": queue_stats
                }
            }


# 全局调度器实例
scheduler = TaskScheduler()