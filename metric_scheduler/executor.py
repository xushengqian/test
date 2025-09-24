"""Metric execution engine with rate limiting and error handling."""
import asyncio
import time
from typing import Dict, Any, List, Optional, Callable
from datetime import datetime
from loguru import logger
from .config import MetricConfig
from .database import DatabaseConnectionPool
from .rate_limiter import DistributedRateLimiter, RateLimiterDecorator


class MetricResult:
    """Container for metric execution results."""
    
    def __init__(self, metric_name: str, success: bool, data: Any = None, 
                 error: Optional[str] = None, execution_time: float = 0.0):
        self.metric_name = metric_name
        self.success = success
        self.data = data
        self.error = error
        self.execution_time = execution_time
        self.timestamp = datetime.utcnow()
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            'metric_name': self.metric_name,
            'success': self.success,
            'data': self.data,
            'error': self.error,
            'execution_time': self.execution_time,
            'timestamp': self.timestamp.isoformat()
        }


class MetricExecutor:
    """Executes metric queries with rate limiting and connection pooling."""
    
    def __init__(self, 
                 db_pool: DatabaseConnectionPool,
                 rate_limiter: DistributedRateLimiter,
                 result_callback: Optional[Callable] = None):
        self.db_pool = db_pool
        self.rate_limiter = rate_limiter
        self.result_callback = result_callback
        self.rate_limit_decorator = RateLimiterDecorator(rate_limiter)
        
        # Execution metrics
        self.metrics = {
            'total_executions': 0,
            'successful_executions': 0,
            'failed_executions': 0,
            'total_execution_time': 0.0,
            'last_execution_time': {}
        }
    
    async def execute_metric(self, metric: MetricConfig) -> MetricResult:
        """Execute a single metric query."""
        start_time = time.time()
        self.metrics['total_executions'] += 1
        
        try:
            # Apply rate limiting based on metric priority
            weight = 11 - metric.priority  # Higher priority = lower weight
            await self.rate_limiter.wait_and_acquire(
                resource_id=f"metric:{metric.name}",
                weight=weight
            )
            
            # Execute query with timeout
            async with self.rate_limiter.limit_concurrent():
                result_data = await asyncio.wait_for(
                    self.db_pool.execute_query(metric.query),
                    timeout=metric.timeout
                )
            
            execution_time = time.time() - start_time
            self.metrics['successful_executions'] += 1
            self.metrics['total_execution_time'] += execution_time
            self.metrics['last_execution_time'][metric.name] = execution_time
            
            result = MetricResult(
                metric_name=metric.name,
                success=True,
                data=result_data,
                execution_time=execution_time
            )
            
            logger.info(f"Successfully executed metric '{metric.name}' in {execution_time:.2f}s")
            
        except asyncio.TimeoutError:
            execution_time = time.time() - start_time
            self.metrics['failed_executions'] += 1
            
            result = MetricResult(
                metric_name=metric.name,
                success=False,
                error=f"Query timeout after {metric.timeout}s",
                execution_time=execution_time
            )
            
            logger.error(f"Metric '{metric.name}' timed out after {metric.timeout}s")
            
        except Exception as e:
            execution_time = time.time() - start_time
            self.metrics['failed_executions'] += 1
            
            result = MetricResult(
                metric_name=metric.name,
                success=False,
                error=str(e),
                execution_time=execution_time
            )
            
            logger.error(f"Failed to execute metric '{metric.name}': {e}")
        
        # Call result callback if provided
        if self.result_callback:
            try:
                await self.result_callback(result)
            except Exception as e:
                logger.error(f"Result callback failed for metric '{metric.name}': {e}")
        
        return result
    
    async def execute_with_retry(self, metric: MetricConfig) -> MetricResult:
        """Execute metric with retry logic."""
        last_error = None
        
        for attempt in range(metric.retry_count + 1):
            if attempt > 0:
                logger.info(f"Retrying metric '{metric.name}' (attempt {attempt + 1}/{metric.retry_count + 1})")
                await asyncio.sleep(metric.retry_delay)
            
            result = await self.execute_metric(metric)
            
            if result.success:
                return result
            
            last_error = result.error
        
        # All retries failed
        return MetricResult(
            metric_name=metric.name,
            success=False,
            error=f"Failed after {metric.retry_count + 1} attempts. Last error: {last_error}"
        )
    
    async def execute_batch(self, metrics: List[MetricConfig], 
                          max_concurrent: int = 5) -> List[MetricResult]:
        """Execute multiple metrics concurrently with controlled parallelism."""
        semaphore = asyncio.Semaphore(max_concurrent)
        
        async def execute_with_semaphore(metric: MetricConfig) -> MetricResult:
            async with semaphore:
                return await self.execute_with_retry(metric)
        
        tasks = [execute_with_semaphore(metric) for metric in metrics if metric.enabled]
        results = await asyncio.gather(*tasks, return_exceptions=True)
        
        # Convert exceptions to MetricResult
        final_results = []
        for i, result in enumerate(results):
            if isinstance(result, Exception):
                final_results.append(MetricResult(
                    metric_name=metrics[i].name,
                    success=False,
                    error=str(result)
                ))
            else:
                final_results.append(result)
        
        return final_results
    
    def get_metrics(self) -> Dict[str, Any]:
        """Get executor metrics."""
        avg_execution_time = (
            self.metrics['total_execution_time'] / self.metrics['total_executions']
            if self.metrics['total_executions'] > 0 else 0
        )
        
        success_rate = (
            self.metrics['successful_executions'] / self.metrics['total_executions']
            if self.metrics['total_executions'] > 0 else 0
        )
        
        return {
            **self.metrics,
            'average_execution_time': avg_execution_time,
            'success_rate': success_rate,
            'rate_limiter_metrics': self.rate_limiter.get_metrics(),
            'db_pool_status': self.db_pool.get_pool_status()
        }


class PriorityMetricExecutor(MetricExecutor):
    """Metric executor with priority queue support."""
    
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.priority_queue = asyncio.PriorityQueue()
        self.worker_tasks = []
        self.running = False
    
    async def start_workers(self, num_workers: int = 5):
        """Start worker tasks to process the priority queue."""
        self.running = True
        
        for i in range(num_workers):
            task = asyncio.create_task(self._worker(f"worker-{i}"))
            self.worker_tasks.append(task)
        
        logger.info(f"Started {num_workers} metric executor workers")
    
    async def stop_workers(self):
        """Stop all worker tasks."""
        self.running = False
        
        # Cancel all worker tasks
        for task in self.worker_tasks:
            task.cancel()
        
        await asyncio.gather(*self.worker_tasks, return_exceptions=True)
        self.worker_tasks.clear()
        
        logger.info("Stopped all metric executor workers")
    
    async def _worker(self, worker_id: str):
        """Worker task that processes metrics from the priority queue."""
        logger.info(f"Worker {worker_id} started")
        
        while self.running:
            try:
                # Get metric from priority queue (blocks until available)
                priority, metric, future = await asyncio.wait_for(
                    self.priority_queue.get(),
                    timeout=1.0
                )
                
                # Execute the metric
                try:
                    result = await self.execute_with_retry(metric)
                    future.set_result(result)
                except Exception as e:
                    future.set_exception(e)
                
            except asyncio.TimeoutError:
                # No metrics in queue, continue
                continue
            except asyncio.CancelledError:
                logger.info(f"Worker {worker_id} cancelled")
                break
            except Exception as e:
                logger.error(f"Worker {worker_id} error: {e}")
        
        logger.info(f"Worker {worker_id} stopped")
    
    async def submit_metric(self, metric: MetricConfig) -> asyncio.Future:
        """Submit a metric for execution and return a future."""
        future = asyncio.Future()
        
        # Priority is inverted (lower number = higher priority)
        await self.priority_queue.put((metric.priority, metric, future))
        
        return future
    
    async def submit_batch(self, metrics: List[MetricConfig]) -> List[MetricResult]:
        """Submit multiple metrics and wait for all results."""
        futures = []
        
        for metric in metrics:
            if metric.enabled:
                future = await self.submit_metric(metric)
                futures.append(future)
        
        # Wait for all metrics to complete
        results = await asyncio.gather(*futures, return_exceptions=True)
        
        # Convert exceptions to MetricResult
        final_results = []
        for i, result in enumerate(results):
            if isinstance(result, Exception):
                final_results.append(MetricResult(
                    metric_name=metrics[i].name,
                    success=False,
                    error=str(result)
                ))
            else:
                final_results.append(result)
        
        return final_results