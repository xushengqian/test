"""
并行指标调度器
"""
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed, Future
from typing import Dict, List, Optional, Callable
from queue import PriorityQueue, Queue
from metric import MetricTask, MetricResult, MetricStatus
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class ParallelMetricScheduler:
    """并行指标调度器"""
    
    def __init__(self, max_workers: int = 4, enable_priority: bool = True):
        """
        初始化调度器
        
        Args:
            max_workers: 最大并行工作线程数
            enable_priority: 是否启用优先级调度
        """
        self.max_workers = max_workers
        self.enable_priority = enable_priority
        self.executor = ThreadPoolExecutor(max_workers=max_workers)
        self.results: Dict[str, MetricResult] = {}
        self.lock = threading.Lock()
        self.task_queue = PriorityQueue() if enable_priority else Queue()
        self.running_tasks: Dict[str, Future] = {}
        self.completed_dependencies: set = set()
        
    def _execute_metric(self, task: MetricTask) -> MetricResult:
        """
        执行单个指标任务
        
        Args:
            task: 指标任务
            
        Returns:
            指标执行结果
        """
        metric_id = task.metric_id
        start_time = time.time()
        result = MetricResult(
            metric_id=metric_id,
            status=MetricStatus.RUNNING,
            start_time=start_time
        )
        
        try:
            logger.info(f"开始执行指标: {task.name} (ID: {metric_id})")
            
            # 执行指标计算函数
            if task.timeout:
                # 如果设置了超时，使用超时机制
                import signal
                def timeout_handler(signum, frame):
                    raise TimeoutError(f"指标 {metric_id} 执行超时")
                
                # 注意：signal只能在主线程中使用，这里简化处理
                metric_result = task.execute_func()
            else:
                metric_result = task.execute_func()
            
            end_time = time.time()
            execution_time = end_time - start_time
            
            result.status = MetricStatus.COMPLETED
            result.result = metric_result
            result.execution_time = execution_time
            result.end_time = end_time
            
            logger.info(f"指标 {task.name} (ID: {metric_id}) 执行成功，耗时: {execution_time:.2f}秒")
            
        except Exception as e:
            end_time = time.time()
            execution_time = end_time - start_time
            
            result.status = MetricStatus.FAILED
            result.error = str(e)
            result.execution_time = execution_time
            result.end_time = end_time
            
            logger.error(f"指标 {task.name} (ID: {metric_id}) 执行失败: {str(e)}")
        
        return result
    
    def _can_execute(self, task: MetricTask) -> bool:
        """
        检查任务是否可以执行（依赖是否满足）
        
        Args:
            task: 指标任务
            
        Returns:
            是否可以执行
        """
        if not task.dependencies:
            return True
        
        return all(dep_id in self.completed_dependencies for dep_id in task.dependencies)
    
    def _schedule_task(self, task: MetricTask):
        """
        调度单个任务
        
        Args:
            task: 指标任务
        """
        if not self._can_execute(task):
            # 依赖未满足，重新加入队列
            if self.enable_priority:
                self.task_queue.put((-task.priority, time.time(), task))
            else:
                self.task_queue.put(task)
            return
        
        # 提交任务到线程池
        future = self.executor.submit(self._execute_metric, task)
        self.running_tasks[task.metric_id] = future
        
        # 处理完成回调
        def done_callback(f: Future):
            result = f.result()
            with self.lock:
                self.results[task.metric_id] = result
                self.completed_dependencies.add(task.metric_id)
                del self.running_tasks[task.metric_id]
            
            # 检查是否有等待此任务完成的其他任务
            self._process_pending_tasks()
        
        future.add_done_callback(done_callback)
    
    def _process_pending_tasks(self):
        """处理待执行的任务"""
        # 检查队列中是否有可以执行的任务
        temp_tasks = []
        
        while not self.task_queue.empty():
            if self.enable_priority:
                try:
                    _, _, task = self.task_queue.get_nowait()
                except:
                    break
            else:
                try:
                    task = self.task_queue.get_nowait()
                except:
                    break
            
            if self._can_execute(task):
                self._schedule_task(task)
            else:
                temp_tasks.append(task)
        
        # 将无法执行的任务重新放回队列
        for task in temp_tasks:
            if self.enable_priority:
                self.task_queue.put((-task.priority, time.time(), task))
            else:
                self.task_queue.put(task)
    
    def submit(self, task: MetricTask):
        """
        提交指标任务到调度器
        
        Args:
            task: 指标任务
        """
        logger.info(f"提交指标任务: {task.name} (ID: {task.metric_id})")
        
        if self.enable_priority:
            # 优先级队列：使用负数优先级，因为PriorityQueue是小顶堆
            self.task_queue.put((-task.priority, time.time(), task))
        else:
            self.task_queue.put(task)
        
        # 尝试立即调度
        self._process_pending_tasks()
    
    def submit_batch(self, tasks: List[MetricTask]):
        """
        批量提交指标任务
        
        Args:
            tasks: 指标任务列表
        """
        for task in tasks:
            self.submit(task)
    
    def wait_all(self, timeout: Optional[float] = None) -> Dict[str, MetricResult]:
        """
        等待所有任务完成
        
        Args:
            timeout: 超时时间（秒）
            
        Returns:
            所有任务的执行结果
        """
        start_time = time.time()
        
        while True:
            # 检查是否所有任务都完成
            with self.lock:
                all_completed = (
                    self.task_queue.empty() and 
                    len(self.running_tasks) == 0
                )
            
            if all_completed:
                break
            
            # 检查超时
            if timeout and (time.time() - start_time) > timeout:
                logger.warning(f"等待超时，仍有 {len(self.running_tasks)} 个任务在执行")
                break
            
            time.sleep(0.1)
        
        return self.results
    
    def get_result(self, metric_id: str) -> Optional[MetricResult]:
        """
        获取指定指标的执行结果
        
        Args:
            metric_id: 指标ID
            
        Returns:
            指标执行结果，如果不存在则返回None
        """
        with self.lock:
            return self.results.get(metric_id)
    
    def get_all_results(self) -> Dict[str, MetricResult]:
        """
        获取所有指标的执行结果
        
        Returns:
            所有指标的执行结果字典
        """
        with self.lock:
            return self.results.copy()
    
    def shutdown(self, wait: bool = True):
        """
        关闭调度器
        
        Args:
            wait: 是否等待正在执行的任务完成
        """
        self.executor.shutdown(wait=wait)
        logger.info("调度器已关闭")
