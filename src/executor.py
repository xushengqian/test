"""
指标执行引擎模块
支持多线程/进程执行、状态管理、结果处理
"""
import asyncio
import logging
import threading
import traceback
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from typing import Dict, Any, Optional, List
from contextlib import contextmanager
import pandas as pd
import pymysql
from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from .config import get_config
from .database import DatabaseService
from .models import (
    ExecutionTask, MetricDefinition, DataSource, ExecutionHistory,
    TaskQueue, WorkerNode, TaskStatus, QueueStatus
)
from .utils import (
    get_current_time, parse_sql_template, sanitize_sql, 
    Timer, calculate_retry_delay, format_duration
)

logger = logging.getLogger(__name__)


class DataSourceManager:
    """数据源管理器"""
    
    def __init__(self):
        self._connections = {}
        self._lock = threading.Lock()
    
    def get_connection(self, data_source_name: str):
        """获取数据源连接"""
        with self._lock:
            if data_source_name not in self._connections:
                self._connections[data_source_name] = self._create_connection(data_source_name)
            return self._connections[data_source_name]
    
    def _create_connection(self, data_source_name: str):
        """创建数据源连接"""
        with DatabaseService() as db:
            data_source = db.query(DataSource).filter(
                DataSource.name == data_source_name
            ).first()
            
            if not data_source:
                raise ValueError(f"Data source '{data_source_name}' not found")
            
            if not data_source.is_active:
                raise ValueError(f"Data source '{data_source_name}' is not active")
            
            # 根据数据源类型创建连接
            if data_source.type.lower() == 'mysql':
                return self._create_mysql_connection(data_source)
            elif data_source.type.lower() == 'postgresql':
                return self._create_postgresql_connection(data_source)
            elif data_source.type.lower() == 'clickhouse':
                return self._create_clickhouse_connection(data_source)
            else:
                raise ValueError(f"Unsupported data source type: {data_source.type}")
    
    def _create_mysql_connection(self, data_source: DataSource):
        """创建MySQL连接"""
        connection_params = data_source.connection_params or {}
        
        engine = create_engine(
            f"mysql+pymysql://{data_source.username}:{data_source.password}@"
            f"{data_source.host}:{data_source.port}/{data_source.database_name}",
            pool_size=min(data_source.max_connections, 5),
            max_overflow=10,
            pool_timeout=30,
            pool_recycle=3600,
            **connection_params
        )
        
        return engine
    
    def _create_postgresql_connection(self, data_source: DataSource):
        """创建PostgreSQL连接"""
        connection_params = data_source.connection_params or {}
        
        engine = create_engine(
            f"postgresql://{data_source.username}:{data_source.password}@"
            f"{data_source.host}:{data_source.port}/{data_source.database_name}",
            pool_size=min(data_source.max_connections, 5),
            max_overflow=10,
            pool_timeout=30,
            pool_recycle=3600,
            **connection_params
        )
        
        return engine
    
    def _create_clickhouse_connection(self, data_source: DataSource):
        """创建ClickHouse连接"""
        connection_params = data_source.connection_params or {}
        
        engine = create_engine(
            f"clickhouse://{data_source.username}:{data_source.password}@"
            f"{data_source.host}:{data_source.port}/{data_source.database_name}",
            pool_size=min(data_source.max_connections, 5),
            max_overflow=10,
            pool_timeout=30,
            **connection_params
        )
        
        return engine
    
    def close_all_connections(self):
        """关闭所有连接"""
        with self._lock:
            for engine in self._connections.values():
                try:
                    engine.dispose()
                except Exception as e:
                    logger.error(f"Error closing connection: {e}")
            self._connections.clear()


class TaskExecutor:
    """任务执行器"""
    
    def __init__(self, node_id: str):
        self.node_id = node_id
        self.config = get_config().executor
        self.data_source_manager = DataSourceManager()
        self.executor = ThreadPoolExecutor(max_workers=self.config.max_workers)
        self.running_tasks = {}
        self._lock = threading.Lock()
    
    async def start(self):
        """启动执行器"""
        logger.info(f"Starting task executor for node {self.node_id}")
        
        # 注册工作节点
        await self._register_worker_node()
        
        # 启动任务处理循环
        asyncio.create_task(self._process_tasks())
        asyncio.create_task(self._heartbeat_loop())
        
        logger.info(f"Task executor started for node {self.node_id}")
    
    async def stop(self):
        """停止执行器"""
        logger.info(f"Stopping task executor for node {self.node_id}")
        
        # 等待正在执行的任务完成
        with self._lock:
            running_task_ids = list(self.running_tasks.keys())
        
        if running_task_ids:
            logger.info(f"Waiting for {len(running_task_ids)} running tasks to complete...")
            # 这里可以实现优雅关闭逻辑
        
        # 关闭线程池
        self.executor.shutdown(wait=True)
        
        # 关闭数据源连接
        self.data_source_manager.close_all_connections()
        
        # 更新节点状态
        await self._update_node_status("OFFLINE")
        
        logger.info(f"Task executor stopped for node {self.node_id}")
    
    async def _register_worker_node(self):
        """注册工作节点"""
        import socket
        
        with DatabaseService() as db:
            # 检查节点是否已存在
            existing_node = db.query(WorkerNode).filter(
                WorkerNode.node_id == self.node_id
            ).first()
            
            if existing_node:
                # 更新现有节点
                existing_node.status = "ONLINE"
                existing_node.max_concurrent_tasks = self.config.max_workers
                existing_node.current_tasks = 0
                existing_node.last_heartbeat = get_current_time()
            else:
                # 创建新节点
                node = WorkerNode(
                    node_id=self.node_id,
                    node_name=f"Worker Node {self.node_id}",
                    host_ip=socket.gethostbyname(socket.gethostname()),
                    port=8000,  # 默认端口
                    max_concurrent_tasks=self.config.max_workers,
                    current_tasks=0,
                    status="ONLINE",
                    last_heartbeat=get_current_time(),
                    capabilities={"sql_execution": True, "data_processing": True}
                )
                db.add(node)
            
            db.commit()
    
    async def _update_node_status(self, status: str):
        """更新节点状态"""
        with DatabaseService() as db:
            node = db.query(WorkerNode).filter(
                WorkerNode.node_id == self.node_id
            ).first()
            
            if node:
                node.status = status
                node.last_heartbeat = get_current_time()
                db.commit()
    
    async def _heartbeat_loop(self):
        """心跳循环"""
        while True:
            try:
                await self._send_heartbeat()
                await asyncio.sleep(30)  # 每30秒发送一次心跳
            except Exception as e:
                logger.error(f"Error in heartbeat loop: {e}")
                await asyncio.sleep(5)
    
    async def _send_heartbeat(self):
        """发送心跳"""
        with DatabaseService() as db:
            node = db.query(WorkerNode).filter(
                WorkerNode.node_id == self.node_id
            ).first()
            
            if node:
                node.last_heartbeat = get_current_time()
                with self._lock:
                    node.current_tasks = len(self.running_tasks)
                db.commit()
    
    async def _process_tasks(self):
        """处理任务循环"""
        while True:
            try:
                await self._fetch_and_execute_tasks()
                await asyncio.sleep(1)  # 每秒检查一次新任务
            except Exception as e:
                logger.error(f"Error in task processing loop: {e}")
                await asyncio.sleep(5)
    
    async def _fetch_and_execute_tasks(self):
        """获取并执行任务"""
        with DatabaseService() as db:
            # 获取分配给当前节点的待处理任务
            tasks = db.query(TaskQueue).filter(
                TaskQueue.worker_id == self.node_id,
                TaskQueue.status == QueueStatus.PROCESSING
            ).limit(self.config.max_workers).all()
            
            for task in tasks:
                with self._lock:
                    if len(self.running_tasks) >= self.config.max_workers:
                        break
                    
                    if task.task_id in self.running_tasks:
                        continue
                    
                    # 提交任务到线程池
                    future = self.executor.submit(self._execute_task, task.task_id, task.payload)
                    self.running_tasks[task.task_id] = future
                
                logger.debug(f"Started executing task {task.task_id}")
            
            # 检查已完成的任务
            await self._check_completed_tasks()
    
    async def _check_completed_tasks(self):
        """检查已完成的任务"""
        with self._lock:
            completed_tasks = []
            for task_id, future in self.running_tasks.items():
                if future.done():
                    completed_tasks.append(task_id)
            
            for task_id in completed_tasks:
                future = self.running_tasks.pop(task_id)
                try:
                    result = future.result()
                    logger.debug(f"Task {task_id} completed successfully")
                except Exception as e:
                    logger.error(f"Task {task_id} failed: {e}")
    
    def _execute_task(self, task_id: str, payload: Dict[str, Any]) -> Dict[str, Any]:
        """执行单个任务"""
        start_time = get_current_time()
        
        try:
            with DatabaseService() as db:
                # 获取任务详情
                task = db.query(ExecutionTask).filter(
                    ExecutionTask.task_id == task_id
                ).first()
                
                if not task:
                    raise ValueError(f"Task {task_id} not found")
                
                # 获取指标定义
                metric = db.query(MetricDefinition).filter(
                    MetricDefinition.id == task.metric_id
                ).first()
                
                if not metric:
                    raise ValueError(f"Metric {task.metric_id} not found")
                
                # 执行指标计算
                with Timer() as timer:
                    result = self._execute_metric(metric, task.parameters or {})
                
                # 更新任务状态
                task.status = TaskStatus.SUCCESS
                task.end_time = get_current_time()
                task.duration_ms = timer.get_duration_ms()
                task.result_data = {
                    "rows": len(result) if isinstance(result, list) else 1,
                    "columns": len(result[0]) if result and isinstance(result, list) else 0,
                    "data": result[:100] if isinstance(result, list) else result  # 只保存前100行
                }
                
                # 更新队列状态
                queue_item = db.query(TaskQueue).filter(
                    TaskQueue.task_id == task_id
                ).first()
                if queue_item:
                    queue_item.status = QueueStatus.COMPLETED
                
                # 记录执行历史
                self._record_execution_history(task, metric, TaskStatus.SUCCESS)
                
                db.commit()
                
                logger.info(f"Task {task_id} executed successfully in {format_duration(timer.get_duration_ms())}")
                
                return {"status": "success", "duration_ms": timer.get_duration_ms()}
        
        except Exception as e:
            error_message = str(e)
            error_traceback = traceback.format_exc()
            
            logger.error(f"Task {task_id} execution failed: {error_message}")
            logger.debug(f"Task {task_id} error traceback: {error_traceback}")
            
            # 更新任务状态为失败
            try:
                with DatabaseService() as db:
                    task = db.query(ExecutionTask).filter(
                        ExecutionTask.task_id == task_id
                    ).first()
                    
                    if task:
                        task.status = TaskStatus.FAILED
                        task.end_time = get_current_time()
                        task.duration_ms = int((get_current_time() - start_time).total_seconds() * 1000)
                        task.error_message = error_message
                        
                        # 检查是否需要重试
                        metric = db.query(MetricDefinition).filter(
                            MetricDefinition.id == task.metric_id
                        ).first()
                        
                        if metric and task.retry_count < metric.retry_count:
                            # 安排重试
                            task.retry_count += 1
                            task.status = TaskStatus.PENDING
                            task.scheduled_time = get_current_time() + timedelta(
                                seconds=calculate_retry_delay(task.retry_count)
                            )
                            
                            # 重新加入队列
                            queue_item = TaskQueue(
                                task_id=f"{task_id}_retry_{task.retry_count}",
                                priority=task.priority,
                                scheduled_time=task.scheduled_time,
                                payload=payload,
                                status=QueueStatus.QUEUED
                            )
                            db.add(queue_item)
                            
                            logger.info(f"Task {task_id} scheduled for retry {task.retry_count}")
                        else:
                            # 更新队列状态
                            queue_item = db.query(TaskQueue).filter(
                                TaskQueue.task_id == task_id
                            ).first()
                            if queue_item:
                                queue_item.status = QueueStatus.COMPLETED
                            
                            # 记录执行历史
                            self._record_execution_history(task, metric, TaskStatus.FAILED, error_message)
                        
                        db.commit()
            
            except Exception as update_error:
                logger.error(f"Failed to update task status: {update_error}")
            
            return {"status": "failed", "error": error_message}
    
    def _execute_metric(self, metric: MetricDefinition, parameters: Dict[str, Any]) -> Any:
        """执行指标计算"""
        try:
            # 解析SQL模板
            sql = parse_sql_template(metric.sql_template, parameters)
            
            # SQL安全检查
            sanitize_sql(sql)
            
            # 获取数据源连接
            engine = self.data_source_manager.get_connection(metric.data_source)
            
            # 执行SQL查询
            with engine.connect() as conn:
                result = pd.read_sql(sql, conn)
                
                # 转换为字典列表格式
                return result.to_dict('records')
        
        except Exception as e:
            logger.error(f"Error executing metric {metric.name}: {e}")
            raise
    
    def _record_execution_history(
        self, 
        task: ExecutionTask, 
        metric: MetricDefinition, 
        status: TaskStatus, 
        error_message: str = None
    ):
        """记录执行历史"""
        try:
            with DatabaseService() as db:
                history = ExecutionHistory(
                    task_id=task.task_id,
                    metric_id=task.metric_id,
                    metric_name=metric.name,
                    status=status.value,
                    scheduled_time=task.scheduled_time,
                    start_time=task.start_time,
                    end_time=task.end_time,
                    duration_ms=task.duration_ms,
                    worker_id=task.worker_id,
                    result_rows=len(task.result_data.get('data', [])) if task.result_data else 0,
                    error_message=error_message,
                    execution_date=task.scheduled_time.date()
                )
                
                db.add(history)
                db.commit()
        
        except Exception as e:
            logger.error(f"Failed to record execution history: {e}")


class ExecutorManager:
    """执行器管理器"""
    
    def __init__(self):
        self.executors = {}
        self.config = get_config()
    
    async def start_executor(self, node_id: str) -> TaskExecutor:
        """启动执行器"""
        if node_id in self.executors:
            logger.warning(f"Executor {node_id} is already running")
            return self.executors[node_id]
        
        executor = TaskExecutor(node_id)
        await executor.start()
        self.executors[node_id] = executor
        
        logger.info(f"Executor {node_id} started")
        return executor
    
    async def stop_executor(self, node_id: str):
        """停止执行器"""
        if node_id not in self.executors:
            logger.warning(f"Executor {node_id} is not running")
            return
        
        executor = self.executors.pop(node_id)
        await executor.stop()
        
        logger.info(f"Executor {node_id} stopped")
    
    async def stop_all_executors(self):
        """停止所有执行器"""
        for node_id in list(self.executors.keys()):
            await self.stop_executor(node_id)
    
    def get_executor(self, node_id: str) -> Optional[TaskExecutor]:
        """获取执行器"""
        return self.executors.get(node_id)
    
    def get_executor_status(self) -> Dict[str, Any]:
        """获取执行器状态"""
        status = {}
        for node_id, executor in self.executors.items():
            with executor._lock:
                status[node_id] = {
                    "running_tasks": len(executor.running_tasks),
                    "max_workers": executor.config.max_workers,
                    "data_sources": len(executor.data_source_manager._connections)
                }
        
        return status


# 全局执行器管理器实例
executor_manager = ExecutorManager()