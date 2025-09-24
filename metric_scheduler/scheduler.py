"""Distributed task scheduler for metric execution."""
import asyncio
import json
from typing import Dict, List, Optional, Callable, Any
from datetime import datetime, timezone
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.jobstores.redis import RedisJobStore
from apscheduler.executors.asyncio import AsyncIOExecutor
from apscheduler.events import (
    EVENT_JOB_EXECUTED, EVENT_JOB_ERROR, EVENT_JOB_MISSED,
    EVENT_JOB_SUBMITTED, EVENT_JOB_REMOVED
)
import redis.asyncio as redis
from loguru import logger
from .config import MetricConfig, SchedulerConfig
from .executor import PriorityMetricExecutor, MetricResult
from .database import ConnectionPoolManager
from .rate_limiter import DistributedRateLimiter


class DistributedMetricScheduler:
    """Distributed scheduler for metric execution across multiple nodes."""
    
    def __init__(self,
                 scheduler_config: SchedulerConfig,
                 redis_client: redis.Redis,
                 pool_manager: ConnectionPoolManager,
                 rate_limiter: DistributedRateLimiter):
        self.config = scheduler_config
        self.redis_client = redis_client
        self.pool_manager = pool_manager
        self.rate_limiter = rate_limiter
        
        # Initialize APScheduler with Redis job store for distributed scheduling
        jobstores = {
            'default': RedisJobStore(
                db=0,
                jobs_key='metric_scheduler:jobs',
                run_times_key='metric_scheduler:run_times',
                redis=redis_client
            )
        }
        
        executors = {
            'default': AsyncIOExecutor()
        }
        
        job_defaults = {
            **self.config.job_defaults,
            'max_instances': self.config.max_instances
        }
        
        self.scheduler = AsyncIOScheduler(
            jobstores=jobstores,
            executors=executors,
            job_defaults=job_defaults,
            timezone=self.config.timezone
        )
        
        # Metric executors for different databases
        self.executors: Dict[str, PriorityMetricExecutor] = {}
        
        # Result handlers
        self.result_handlers: List[Callable] = []
        
        # Scheduler metrics
        self.metrics = {
            'jobs_scheduled': 0,
            'jobs_executed': 0,
            'jobs_failed': 0,
            'jobs_missed': 0,
            'active_jobs': 0
        }
        
        # Setup event listeners
        self._setup_event_listeners()
    
    def _setup_event_listeners(self):
        """Setup scheduler event listeners for monitoring."""
        self.scheduler.add_listener(
            self._on_job_executed,
            EVENT_JOB_EXECUTED
        )
        self.scheduler.add_listener(
            self._on_job_error,
            EVENT_JOB_ERROR
        )
        self.scheduler.add_listener(
            self._on_job_missed,
            EVENT_JOB_MISSED
        )
        self.scheduler.add_listener(
            self._on_job_submitted,
            EVENT_JOB_SUBMITTED
        )
        self.scheduler.add_listener(
            self._on_job_removed,
            EVENT_JOB_REMOVED
        )
    
    def _on_job_executed(self, event):
        """Handle job execution event."""
        self.metrics['jobs_executed'] += 1
        logger.info(f"Job {event.job_id} executed successfully")
    
    def _on_job_error(self, event):
        """Handle job error event."""
        self.metrics['jobs_failed'] += 1
        logger.error(f"Job {event.job_id} failed: {event.exception}")
    
    def _on_job_missed(self, event):
        """Handle job missed event."""
        self.metrics['jobs_missed'] += 1
        logger.warning(f"Job {event.job_id} missed scheduled time")
    
    def _on_job_submitted(self, event):
        """Handle job submission event."""
        self.metrics['jobs_scheduled'] += 1
        self.metrics['active_jobs'] += 1
        logger.info(f"Job {event.job_id} submitted")
    
    def _on_job_removed(self, event):
        """Handle job removal event."""
        self.metrics['active_jobs'] -= 1
        logger.info(f"Job {event.job_id} removed")
    
    async def initialize(self):
        """Initialize the scheduler and start executor workers."""
        # Start metric executors for each database pool
        for pool_name, db_pool in self.pool_manager.pools.items():
            executor = PriorityMetricExecutor(
                db_pool=db_pool,
                rate_limiter=self.rate_limiter,
                result_callback=self._handle_metric_result
            )
            await executor.start_workers(num_workers=self.config.max_workers)
            self.executors[pool_name] = executor
            logger.info(f"Started executor for database pool: {pool_name}")
        
        # Start the scheduler
        self.scheduler.start()
        logger.info("Distributed metric scheduler initialized")
    
    async def shutdown(self):
        """Shutdown the scheduler and all executors."""
        # Shutdown scheduler
        self.scheduler.shutdown(wait=True)
        
        # Stop all executor workers
        for executor in self.executors.values():
            await executor.stop_workers()
        
        logger.info("Distributed metric scheduler shutdown complete")
    
    def add_result_handler(self, handler: Callable):
        """Add a result handler for metric execution results."""
        self.result_handlers.append(handler)
    
    async def _handle_metric_result(self, result: MetricResult):
        """Handle metric execution results."""
        # Store result in Redis for persistence
        result_key = f"metric_result:{result.metric_name}:{result.timestamp.timestamp()}"
        await self.redis_client.setex(
            result_key,
            86400,  # 24 hour TTL
            json.dumps(result.to_dict())
        )
        
        # Update latest result
        latest_key = f"metric_result:latest:{result.metric_name}"
        await self.redis_client.set(latest_key, json.dumps(result.to_dict()))
        
        # Call registered handlers
        for handler in self.result_handlers:
            try:
                await handler(result)
            except Exception as e:
                logger.error(f"Result handler failed: {e}")
    
    async def schedule_metric(self, metric: MetricConfig, pool_name: str = "default"):
        """Schedule a metric for periodic execution."""
        if pool_name not in self.executors:
            raise ValueError(f"No executor found for pool: {pool_name}")
        
        job_id = f"metric:{pool_name}:{metric.name}"
        
        # Remove existing job if any
        if self.scheduler.get_job(job_id):
            self.scheduler.remove_job(job_id)
        
        # Schedule the metric execution
        self.scheduler.add_job(
            func=self._execute_metric,
            trigger='cron',
            id=job_id,
            name=f"Metric: {metric.name}",
            args=[metric, pool_name],
            **self._parse_cron_expression(metric.schedule),
            replace_existing=True,
            misfire_grace_time=self.config.misfire_grace_time
        )
        
        logger.info(f"Scheduled metric '{metric.name}' with schedule: {metric.schedule}")
    
    async def _execute_metric(self, metric: MetricConfig, pool_name: str):
        """Execute a scheduled metric."""
        executor = self.executors.get(pool_name)
        if not executor:
            logger.error(f"No executor found for pool: {pool_name}")
            return
        
        # Submit metric to executor
        future = await executor.submit_metric(metric)
        
        # Wait for result
        try:
            result = await future
            logger.info(f"Metric '{metric.name}' completed: success={result.success}")
        except Exception as e:
            logger.error(f"Metric '{metric.name}' execution failed: {e}")
    
    def _parse_cron_expression(self, cron_expr: str) -> Dict[str, Any]:
        """Parse cron expression to APScheduler format."""
        parts = cron_expr.split()
        
        if len(parts) == 5:
            # Standard cron format: minute hour day month day_of_week
            return {
                'minute': parts[0],
                'hour': parts[1],
                'day': parts[2],
                'month': parts[3],
                'day_of_week': parts[4]
            }
        elif len(parts) == 6:
            # Extended format with seconds: second minute hour day month day_of_week
            return {
                'second': parts[0],
                'minute': parts[1],
                'hour': parts[2],
                'day': parts[3],
                'month': parts[4],
                'day_of_week': parts[5]
            }
        else:
            raise ValueError(f"Invalid cron expression: {cron_expr}")
    
    async def schedule_metrics_batch(self, metrics: List[MetricConfig], pool_name: str = "default"):
        """Schedule multiple metrics at once."""
        for metric in metrics:
            if metric.enabled:
                await self.schedule_metric(metric, pool_name)
    
    def get_scheduled_jobs(self) -> List[Dict[str, Any]]:
        """Get list of all scheduled jobs."""
        jobs = []
        for job in self.scheduler.get_jobs():
            jobs.append({
                'id': job.id,
                'name': job.name,
                'next_run_time': job.next_run_time.isoformat() if job.next_run_time else None,
                'trigger': str(job.trigger),
                'misfire_grace_time': job.misfire_grace_time,
                'max_instances': job.max_instances
            })
        return jobs
    
    def pause_metric(self, metric_name: str, pool_name: str = "default"):
        """Pause a scheduled metric."""
        job_id = f"metric:{pool_name}:{metric_name}"
        job = self.scheduler.get_job(job_id)
        if job:
            job.pause()
            logger.info(f"Paused metric: {metric_name}")
        else:
            logger.warning(f"Metric not found: {metric_name}")
    
    def resume_metric(self, metric_name: str, pool_name: str = "default"):
        """Resume a paused metric."""
        job_id = f"metric:{pool_name}:{metric_name}"
        job = self.scheduler.get_job(job_id)
        if job:
            job.resume()
            logger.info(f"Resumed metric: {metric_name}")
        else:
            logger.warning(f"Metric not found: {metric_name}")
    
    def remove_metric(self, metric_name: str, pool_name: str = "default"):
        """Remove a scheduled metric."""
        job_id = f"metric:{pool_name}:{metric_name}"
        if self.scheduler.get_job(job_id):
            self.scheduler.remove_job(job_id)
            logger.info(f"Removed metric: {metric_name}")
        else:
            logger.warning(f"Metric not found: {metric_name}")
    
    async def execute_metric_now(self, metric: MetricConfig, pool_name: str = "default"):
        """Execute a metric immediately without waiting for schedule."""
        await self._execute_metric(metric, pool_name)
    
    def get_metrics(self) -> Dict[str, Any]:
        """Get scheduler metrics."""
        executor_metrics = {}
        for name, executor in self.executors.items():
            executor_metrics[name] = executor.get_metrics()
        
        return {
            'scheduler': self.metrics,
            'jobs': {
                'total': len(self.scheduler.get_jobs()),
                'active': self.metrics['active_jobs']
            },
            'executors': executor_metrics
        }