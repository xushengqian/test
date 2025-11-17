"""
并行指标调度器
支持并行执行多个指标计算任务
"""
import asyncio
import threading
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List, Dict, Optional, Callable
from datetime import datetime
from queue import Queue, PriorityQueue
import time

from metric import Metric, MetricResult, MetricStatus


class ParallelMetricScheduler:
    """并行指标调度器"""
    
    def __init__(self, max_workers: int = 4, timeout: Optional[float] = None):
        """
        初始化调度器
        
        Args:
            max_workers: 最大并行工作线程数
            timeout: 全局超时时间（秒）
        """
        self.max_workers = max_workers
        self.global_timeout = timeout
        self.executor = ThreadPoolExecutor(max_workers=max_workers)
        self.results: Dict[str, MetricResult] = {}
        self.lock = threading.Lock()
        
    def _execute_metric(self, metric: Metric) -> MetricResult:
        """
        执行单个指标
        
        Args:
            metric: 要执行的指标
            
        Returns:
            指标执行结果
        """
        result = MetricResult(
            metric_id=metric.id,
            status=MetricStatus.RUNNING,
            start_time=datetime.now()
        )
        
        try:
            # 检查是否有超时设置
            timeout = metric.timeout or self.global_timeout
            
            if timeout:
                # 使用线程池执行带超时的任务
                future = self.executor.submit(metric.func)
                try:
                    result.result = future.result(timeout=timeout)
                    result.status = MetricStatus.COMPLETED
                except Exception as e:
                    result.status = MetricStatus.FAILED
                    result.error = str(e)
            else:
                # 直接执行
                result.result = metric.func()
                result.status = MetricStatus.COMPLETED
                
        except Exception as e:
            result.status = MetricStatus.FAILED
            result.error = str(e)
        finally:
            result.end_time = datetime.now()
            if result.start_time and result.end_time:
                result.duration = (result.end_time - result.start_time).total_seconds()
        
        return result
    
    def _check_dependencies(self, metric: Metric, completed_metrics: set) -> bool:
        """
        检查指标的依赖是否都已完成
        
        Args:
            metric: 要检查的指标
            completed_metrics: 已完成的指标ID集合
            
        Returns:
            如果所有依赖都已完成返回True，否则返回False
        """
        if not metric.dependencies:
            return True
        return all(dep_id in completed_metrics for dep_id in metric.dependencies)
    
    def schedule(self, metrics: List[Metric]) -> Dict[str, MetricResult]:
        """
        并行调度执行多个指标
        
        Args:
            metrics: 指标列表
            
        Returns:
            指标ID到执行结果的映射
        """
        # 初始化结果字典
        self.results = {}
        pending_metrics = {m.id: m for m in metrics}
        completed_metrics = set()
        running_metrics = {}  # metric_id -> future
        
        # 创建优先级队列（优先级高的先执行）
        ready_queue = PriorityQueue()
        for metric in metrics:
            if not metric.dependencies:
                # 没有依赖的指标可以立即加入队列
                ready_queue.put((-metric.priority, metric.id, metric))
        
        # 主调度循环
        while pending_metrics or running_metrics:
            # 提交可以执行的指标
            while not ready_queue.empty() and len(running_metrics) < self.max_workers:
                _, metric_id, metric = ready_queue.get()
                if metric_id in pending_metrics:
                    # 提交任务
                    future = self.executor.submit(self._execute_metric, metric)
                    running_metrics[metric_id] = (future, metric)
                    del pending_metrics[metric_id]
            
            # 检查完成的任务
            completed_futures = []
            for metric_id, (future, metric) in running_metrics.items():
                if future.done():
                    completed_futures.append(metric_id)
                    result = future.result()
                    self.results[metric_id] = result
                    
                    if result.status == MetricStatus.COMPLETED:
                        completed_metrics.add(metric_id)
            
            # 移除已完成的任务
            for metric_id in completed_futures:
                del running_metrics[metric_id]
            
            # 检查是否有新的指标可以执行（依赖已完成）
            newly_ready = []
            for metric_id, metric in list(pending_metrics.items()):
                if self._check_dependencies(metric, completed_metrics):
                    newly_ready.append(metric_id)
                    ready_queue.put((-metric.priority, metric_id, metric))
            
            # 如果没有任务在运行且没有可执行的任务，但有未完成的指标，说明有循环依赖或错误
            if not running_metrics and ready_queue.empty() and pending_metrics:
                # 检查是否有无法解决的依赖
                remaining = list(pending_metrics.values())
                for metric in remaining:
                    missing_deps = [dep for dep in metric.dependencies if dep not in completed_metrics and dep not in pending_metrics]
                    if missing_deps:
                        # 依赖缺失，标记为失败
                        result = MetricResult(
                            metric_id=metric.id,
                            status=MetricStatus.FAILED,
                            error=f"Missing dependencies: {missing_deps}",
                            start_time=datetime.now(),
                            end_time=datetime.now()
                        )
                        self.results[metric.id] = result
                        del pending_metrics[metric.id]
            
            # 避免CPU空转
            if not running_metrics and ready_queue.empty():
                time.sleep(0.01)
        
        return self.results
    
    def schedule_async(self, metrics: List[Metric]) -> Dict[str, MetricResult]:
        """
        异步并行调度执行多个指标（使用asyncio）
        
        Args:
            metrics: 指标列表
            
        Returns:
            指标ID到执行结果的映射
        """
        async def async_schedule():
            loop = asyncio.get_event_loop()
            results = {}
            completed_metrics = set()
            pending_metrics = {m.id: m for m in metrics}
            running_tasks = {}
            
            # 创建就绪队列
            ready_queue = []
            for metric in metrics:
                if not metric.dependencies:
                    ready_queue.append(metric)
            ready_queue.sort(key=lambda m: -m.priority)
            
            while pending_metrics or running_tasks:
                # 提交可以执行的指标
                while ready_queue and len(running_tasks) < self.max_workers:
                    metric = ready_queue.pop(0)
                    if metric.id in pending_metrics:
                        task = asyncio.create_task(
                            self._execute_metric_async(metric, loop)
                        )
                        running_tasks[metric.id] = (task, metric)
                        del pending_metrics[metric.id]
                
                # 等待至少一个任务完成
                if running_tasks:
                    done, pending = await asyncio.wait(
                        [task for task, _ in running_tasks.values()],
                        return_when=asyncio.FIRST_COMPLETED
                    )
                    
                    # 处理完成的任务
                    for task in done:
                        metric_id = None
                        for mid, (t, m) in running_tasks.items():
                            if t == task:
                                metric_id = mid
                                break
                        
                        if metric_id:
                            result = await task
                            results[metric_id] = result
                            if result.status == MetricStatus.COMPLETED:
                                completed_metrics.add(metric_id)
                            del running_tasks[metric_id]
                    
                    # 检查新就绪的指标
                    newly_ready = []
                    for metric_id, metric in list(pending_metrics.items()):
                        if self._check_dependencies(metric, completed_metrics):
                            newly_ready.append(metric_id)
                            ready_queue.append(metric)
                            ready_queue.sort(key=lambda m: -m.priority)
                else:
                    await asyncio.sleep(0.01)
            
            return results
        
        # 运行异步调度
        return asyncio.run(async_schedule())
    
    async def _execute_metric_async(self, metric: Metric, loop) -> MetricResult:
        """异步执行指标"""
        result = MetricResult(
            metric_id=metric.id,
            status=MetricStatus.RUNNING,
            start_time=datetime.now()
        )
        
        try:
            timeout = metric.timeout or self.global_timeout
            if timeout:
                func_result = await asyncio.wait_for(
                    loop.run_in_executor(None, metric.func),
                    timeout=timeout
                )
            else:
                func_result = await loop.run_in_executor(None, metric.func)
            
            result.result = func_result
            result.status = MetricStatus.COMPLETED
        except asyncio.TimeoutError:
            result.status = MetricStatus.FAILED
            result.error = f"Timeout after {timeout} seconds"
        except Exception as e:
            result.status = MetricStatus.FAILED
            result.error = str(e)
        finally:
            result.end_time = datetime.now()
            if result.start_time and result.end_time:
                result.duration = (result.end_time - result.start_time).total_seconds()
        
        return result
    
    def shutdown(self, wait: bool = True):
        """关闭调度器"""
        self.executor.shutdown(wait=wait)
