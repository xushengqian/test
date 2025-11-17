"""
并行指标调度器 - 支持高并发、批量处理和分布式调度
"""
import time
import uuid
import threading
from datetime import datetime, timedelta
from typing import List, Optional, Set
from concurrent.futures import ThreadPoolExecutor, as_completed, Future
from collections import defaultdict
from loguru import logger
from sqlalchemy import and_, or_
from sqlalchemy.orm import Session
from croniter import croniter
import redis

from models import Session as DBSession, MetricSchedule, JobInstance, Schedule, SchedulerMetrics
from parallel_executor import ParallelMetricExecutor
from config.config import config


class DistributedLock:
    """基于Redis的分布式锁 - 支持重试"""
    
    def __init__(self, redis_client: redis.Redis, key: str, timeout: int = 60):
        self.redis_client = redis_client
        self.key = f"lock:{key}"
        self.timeout = timeout
        self.identifier = str(uuid.uuid4())
        self.acquired = False
    
    def acquire(self, retry_times: int = 3) -> bool:
        """获取锁，支持重试"""
        for i in range(retry_times):
            if self.redis_client.set(self.key, self.identifier, nx=True, ex=self.timeout):
                self.acquired = True
                return True
            if i < retry_times - 1:
                time.sleep(0.1 * (i + 1))  # 递增等待时间
        return False
    
    def release(self):
        """释放锁"""
        if not self.acquired:
            return False
        
        pipe = self.redis_client.pipeline(True)
        while True:
            try:
                pipe.watch(self.key)
                if pipe.get(self.key) == self.identifier.encode():
                    pipe.multi()
                    pipe.delete(self.key)
                    pipe.execute()
                    self.acquired = False
                    return True
                pipe.unwatch()
                break
            except redis.WatchError:
                pass
        return False
    
    def __enter__(self):
        if not self.acquire(config.scheduler.lock_retry_times):
            raise RuntimeError(f"无法获取锁: {self.key}")
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.release()


class ParallelMetricScheduler:
    """并行指标调度器 - 优化的并行处理"""
    
    def __init__(self):
        # 工作线程池
        self.scan_pool = ThreadPoolExecutor(
            max_workers=config.scheduler.job_scan_workers,
            thread_name_prefix='scan'
        )
        self.create_pool = ThreadPoolExecutor(
            max_workers=config.scheduler.batch_create_workers,
            thread_name_prefix='create'
        )
        self.executor_pool = ThreadPoolExecutor(
            max_workers=config.scheduler.executor_workers,
            thread_name_prefix='executor'
        )
        
        # Redis连接
        self.redis_client = self._init_redis()
        
        # 运行状态
        self._is_running = False
        self._lock = threading.RLock()
        self.running_jobs: dict[int, Future] = {}
        
        # 性能统计
        self.stats = {
            'jobs_created': 0,
            'jobs_executed': 0,
            'jobs_succeeded': 0,
            'jobs_failed': 0,
            'execution_times': []
        }
        
    def _init_redis(self) -> Optional[redis.Redis]:
        """初始化Redis连接"""
        try:
            client = redis.Redis(
                host=config.redis.host,
                port=config.redis.port,
                password=config.redis.password or None,
                db=config.redis.db,
                decode_responses=False,
                socket_connect_timeout=5,
                socket_timeout=5,
                retry_on_timeout=True
            )
            client.ping()
            logger.info("✓ Redis连接成功，启用分布式调度")
            return client
        except Exception as e:
            logger.warning(f"⚠ Redis连接失败，使用单机模式: {e}")
            return None
    
    def start(self):
        """启动调度器"""
        logger.info("=" * 60)
        logger.info("🚀 启动并行指标调度器")
        logger.info("=" * 60)
        logger.info(f"配置:")
        logger.info(f"  - 作业扫描工作线程: {config.scheduler.job_scan_workers}")
        logger.info(f"  - 批量创建工作线程: {config.scheduler.batch_create_workers}")
        logger.info(f"  - 执行器工作线程: {config.scheduler.executor_workers}")
        logger.info(f"  - 最大并发作业数: {config.scheduler.max_concurrent_jobs}")
        logger.info(f"  - 批处理大小: {config.scheduler.batch_size}")
        logger.info("=" * 60)
        
        self._is_running = True
        
        # 启动后台监控线程
        threading.Thread(target=self._monitor_loop, daemon=True, name='monitor').start()
        threading.Thread(target=self._timeout_checker_loop, daemon=True, name='timeout').start()
        threading.Thread(target=self._performance_collector_loop, daemon=True, name='perf').start()
        
        # 主循环
        try:
            while self._is_running:
                try:
                    # 1. 并行扫描和创建作业
                    self._parallel_scan_and_create_jobs()
                    
                    # 2. 并行执行待处理作业
                    self._parallel_execute_jobs()
                    
                    # 3. 等待一段时间
                    time.sleep(config.scheduler.job_scan_interval)
                    
                except KeyboardInterrupt:
                    logger.info("⚠ 接收到停止信号")
                    break
                except Exception as e:
                    logger.error(f"❌ 调度循环出错: {e}", exc_info=True)
                    time.sleep(5)
        finally:
            self.stop()
    
    def stop(self):
        """停止调度器"""
        logger.info("🛑 停止并行指标调度器...")
        self._is_running = False
        
        # 等待运行中的作业完成
        logger.info(f"等待 {len(self.running_jobs)} 个运行中的作业完成...")
        with self._lock:
            for job_id, future in list(self.running_jobs.items()):
                try:
                    future.result(timeout=30)
                except Exception as e:
                    logger.error(f"作业 {job_id} 异常: {e}")
        
        # 关闭线程池
        self.scan_pool.shutdown(wait=True, cancel_futures=False)
        self.create_pool.shutdown(wait=True, cancel_futures=False)
        self.executor_pool.shutdown(wait=True, cancel_futures=False)
        
        if self.redis_client:
            self.redis_client.close()
        
        logger.info("✓ 并行指标调度器已停止")
        self._print_stats()
    
    def _parallel_scan_and_create_jobs(self):
        """并行扫描调度配置并创建作业"""
        try:
            with DBSession() as session:
                # 获取所有激活的调度配置
                active_schedules = session.query(MetricSchedule).join(
                    Schedule
                ).filter(
                    MetricSchedule.is_active == True,
                    Schedule.is_active == True
                ).all()
                
                if not active_schedules:
                    return
                
                logger.info(f"📊 扫描 {len(active_schedules)} 个激活的调度配置")
                
                # 按优先级分组
                grouped_schedules = defaultdict(list)
                for ms in active_schedules:
                    grouped_schedules[ms.priority].append(ms)
                
                # 并行处理每个优先级组
                futures = []
                for priority in sorted(grouped_schedules.keys(), reverse=True):
                    schedules = grouped_schedules[priority]
                    # 将调度配置分批
                    for i in range(0, len(schedules), config.scheduler.batch_size):
                        batch = schedules[i:i + config.scheduler.batch_size]
                        future = self.scan_pool.submit(
                            self._create_jobs_for_batch,
                            [ms.id for ms in batch]
                        )
                        futures.append(future)
                
                # 等待所有扫描任务完成
                created_count = 0
                for future in as_completed(futures):
                    try:
                        count = future.result()
                        created_count += count
                    except Exception as e:
                        logger.error(f"批量创建作业失败: {e}")
                
                if created_count > 0:
                    logger.info(f"✓ 创建了 {created_count} 个新作业")
                    self.stats['jobs_created'] += created_count
                    
        except Exception as e:
            logger.error(f"扫描和创建作业失败: {e}", exc_info=True)
    
    def _create_jobs_for_batch(self, metric_schedule_ids: List[int]) -> int:
        """为一批调度配置创建作业"""
        created_count = 0
        
        try:
            with DBSession() as session:
                for ms_id in metric_schedule_ids:
                    metric_schedule = session.query(MetricSchedule).get(ms_id)
                    if not metric_schedule:
                        continue
                    
                    try:
                        count = self._create_job_for_schedule(session, metric_schedule)
                        created_count += count
                    except Exception as e:
                        logger.error(f"为调度 {ms_id} 创建作业失败: {e}")
                        
        except Exception as e:
            logger.error(f"批量创建作业出错: {e}")
        
        return created_count
    
    def _create_job_for_schedule(self, session: Session, metric_schedule: MetricSchedule) -> int:
        """为调度配置创建作业，返回创建的作业数量"""
        try:
            schedule = metric_schedule.schedule
            now = datetime.now()
            created_count = 0
            
            # 计算未来需要创建的作业时间点（提前创建）
            next_times = self._calculate_next_run_times(schedule, now, count=5)
            
            for next_time in next_times:
                # 检查该时间点是否已有作业
                existing_job = session.query(JobInstance).filter(
                    JobInstance.metric_schedule_id == metric_schedule.id,
                    JobInstance.scheduled_time == next_time
                ).first()
                
                if existing_job:
                    continue
                
                # 创建新作业
                job_code = self._generate_job_code(
                    metric_schedule.metric.metric_code,
                    next_time
                )
                
                job_instance = JobInstance(
                    job_code=job_code,
                    metric_schedule_id=metric_schedule.id,
                    scheduled_time=next_time,
                    status='PENDING'
                )
                session.add(job_instance)
                created_count += 1
            
            if created_count > 0:
                session.commit()
                logger.debug(f"为 {metric_schedule.metric.metric_code} 创建了 {created_count} 个作业")
            
            return created_count
            
        except Exception as e:
            logger.error(f"创建作业失败: {e}")
            session.rollback()
            return 0
    
    def _calculate_next_run_times(self, schedule: Schedule, base_time: datetime, count: int = 5) -> List[datetime]:
        """计算接下来的N个运行时间"""
        times = []
        
        # 检查调度是否在有效期内
        if schedule.start_time and base_time < schedule.start_time:
            base_time = schedule.start_time
        if schedule.end_time and base_time > schedule.end_time:
            return times
        
        if schedule.schedule_type == 'CRON':
            # 使用croniter计算多个执行时间
            cron = croniter(schedule.cron_expression, base_time)
            for _ in range(count):
                next_time = cron.get_next(datetime)
                if schedule.end_time and next_time > schedule.end_time:
                    break
                times.append(next_time)
                
        elif schedule.schedule_type == 'FIXED_RATE':
            # 固定频率
            current_time = base_time
            for _ in range(count):
                next_time = current_time + timedelta(seconds=schedule.fixed_rate_seconds)
                if schedule.end_time and next_time > schedule.end_time:
                    break
                times.append(next_time)
                current_time = next_time
                
        elif schedule.schedule_type == 'ONCE':
            # 一次性任务
            if schedule.start_time and schedule.start_time > base_time:
                times.append(schedule.start_time)
                
        return times
    
    def _generate_job_code(self, metric_code: str, scheduled_time: datetime) -> str:
        """生成作业编码"""
        return f"{metric_code}_{scheduled_time.strftime('%Y%m%d%H%M%S')}_{uuid.uuid4().hex[:8]}"
    
    def _parallel_execute_jobs(self):
        """并行执行待处理的作业"""
        try:
            # 清理已完成的作业
            self._cleanup_completed_jobs()
            
            # 检查是否还能接受新作业
            with self._lock:
                available_slots = config.scheduler.max_concurrent_jobs - len(self.running_jobs)
            
            if available_slots <= 0:
                return
            
            # 获取待执行的作业
            with DBSession() as session:
                now = datetime.now()
                pending_jobs = session.query(JobInstance).filter(
                    JobInstance.status == 'PENDING',
                    JobInstance.scheduled_time <= now
                ).order_by(
                    JobInstance.scheduled_time
                ).limit(available_slots).all()
                
                if not pending_jobs:
                    return
                
                logger.info(f"🔄 执行 {len(pending_jobs)} 个待处理作业 (可用槽位: {available_slots})")
                
                # 并行提交作业
                for job in pending_jobs:
                    self._submit_job(job.id)
                    
        except Exception as e:
            logger.error(f"并行执行作业失败: {e}", exc_info=True)
    
    def _submit_job(self, job_id: int):
        """提交作业到执行池"""
        try:
            # 尝试获取分布式锁（如果有Redis）
            lock_key = None
            if self.redis_client:
                lock_key = f"job:{job_id}"
                lock = DistributedLock(self.redis_client, lock_key)
                if not lock.acquire(config.scheduler.lock_retry_times):
                    logger.debug(f"作业 {job_id} 已被其他实例锁定")
                    return
            else:
                lock = None
            
            # 提交到执行池
            future = self.executor_pool.submit(self._execute_job_wrapper, job_id, lock)
            
            with self._lock:
                self.running_jobs[job_id] = future
                
        except Exception as e:
            logger.error(f"提交作业失败: {job_id}, 错误: {e}")
    
    def _execute_job_wrapper(self, job_id: int, lock: Optional[DistributedLock]):
        """执行作业的包装器"""
        start_time = time.time()
        
        try:
            with DBSession() as session:
                job = session.query(JobInstance).get(job_id)
                if not job:
                    logger.error(f"作业不存在: {job_id}")
                    return
                
                # 检查作业状态（防止重复执行）
                if job.status != 'PENDING':
                    logger.warning(f"作业 {job.job_code} 状态不是PENDING: {job.status}")
                    return
                
                # 执行作业
                logger.info(f"▶ 开始执行作业: {job.job_code}")
                executor = ParallelMetricExecutor(session)
                success = executor.execute_job(job)
                
                # 更新统计
                execution_time = time.time() - start_time
                self.stats['execution_times'].append(execution_time)
                self.stats['jobs_executed'] += 1
                
                if success:
                    self.stats['jobs_succeeded'] += 1
                    logger.info(f"✓ 作业执行成功: {job.job_code} (耗时: {execution_time:.2f}秒)")
                else:
                    self.stats['jobs_failed'] += 1
                    logger.warning(f"✗ 作业执行失败: {job.job_code}")
                    
                    # 检查是否需要重试
                    if job.retry_count < job.metric_schedule.max_retry_times:
                        job.retry_count += 1
                        job.status = 'PENDING'
                        session.commit()
                        logger.info(f"⟳ 作业将重试: {job.job_code}, 第 {job.retry_count} 次")
                        
        except Exception as e:
            logger.error(f"执行作业异常: {job_id}, 错误: {e}", exc_info=True)
            self.stats['jobs_failed'] += 1
        finally:
            # 释放锁
            if lock:
                lock.release()
    
    def _cleanup_completed_jobs(self):
        """清理已完成的作业"""
        with self._lock:
            completed_ids = []
            for job_id, future in self.running_jobs.items():
                if future.done():
                    completed_ids.append(job_id)
            
            for job_id in completed_ids:
                self.running_jobs.pop(job_id, None)
    
    def _monitor_loop(self):
        """监控循环"""
        while self._is_running:
            try:
                with self._lock:
                    running_count = len(self.running_jobs)
                
                if running_count > 0:
                    logger.info(f"📈 运行状态: {running_count}/{config.scheduler.max_concurrent_jobs} 作业运行中")
                
                time.sleep(30)
            except Exception as e:
                logger.error(f"监控循环出错: {e}")
    
    def _timeout_checker_loop(self):
        """超时检查循环"""
        while self._is_running:
            try:
                self._check_timeout_jobs()
                time.sleep(config.scheduler.job_timeout_check_interval)
            except Exception as e:
                logger.error(f"超时检查出错: {e}")
    
    def _check_timeout_jobs(self):
        """检查超时的作业"""
        try:
            with DBSession() as session:
                running_jobs = session.query(JobInstance).filter(
                    JobInstance.status == 'RUNNING'
                ).all()
                
                now = datetime.now()
                timeout_count = 0
                
                for job in running_jobs:
                    if job.actual_start_time:
                        duration = (now - job.actual_start_time).total_seconds()
                        if duration > job.metric_schedule.timeout_seconds:
                            job.status = 'TIMEOUT'
                            job.actual_end_time = now
                            job.error_message = f"作业执行超时，已运行 {duration:.0f} 秒"
                            timeout_count += 1
                            logger.warning(f"⏱ 作业超时: {job.job_code}")
                
                if timeout_count > 0:
                    session.commit()
                    logger.info(f"标记了 {timeout_count} 个超时作业")
                    
        except Exception as e:
            logger.error(f"检查超时作业失败: {e}")
    
    def _performance_collector_loop(self):
        """性能数据收集循环"""
        if not config.scheduler.enable_metrics:
            return
        
        while self._is_running:
            try:
                self._collect_performance_metrics()
                time.sleep(config.scheduler.metrics_interval)
            except Exception as e:
                logger.error(f"性能数据收集出错: {e}")
    
    def _collect_performance_metrics(self):
        """收集性能指标"""
        try:
            with DBSession() as session:
                now = datetime.now()
                one_minute_ago = now - timedelta(minutes=1)
                
                # 统计各状态作业数
                pending = session.query(JobInstance).filter(
                    JobInstance.status == 'PENDING'
                ).count()
                
                running = session.query(JobInstance).filter(
                    JobInstance.status == 'RUNNING'
                ).count()
                
                recent_success = session.query(JobInstance).filter(
                    JobInstance.status == 'SUCCESS',
                    JobInstance.actual_end_time >= one_minute_ago
                ).count()
                
                recent_failed = session.query(JobInstance).filter(
                    JobInstance.status.in_(['FAILED', 'TIMEOUT']),
                    JobInstance.updated_at >= one_minute_ago
                ).count()
                
                # 计算平均执行时间
                avg_time = None
                max_time = None
                if self.stats['execution_times']:
                    recent_times = self.stats['execution_times'][-100:]  # 最近100个
                    avg_time = sum(recent_times) / len(recent_times)
                    max_time = max(recent_times)
                
                # 计算吞吐量
                throughput = recent_success
                
                # 保存指标
                metrics = SchedulerMetrics(
                    metric_time=now,
                    pending_jobs=pending,
                    running_jobs=running,
                    success_jobs=recent_success,
                    failed_jobs=recent_failed,
                    avg_execution_time=avg_time,
                    max_execution_time=max_time,
                    throughput=throughput
                )
                session.add(metrics)
                session.commit()
                
        except Exception as e:
            logger.error(f"收集性能指标失败: {e}")
    
    def _print_stats(self):
        """打印统计信息"""
        logger.info("=" * 60)
        logger.info("📊 执行统计")
        logger.info("=" * 60)
        logger.info(f"  创建作业数: {self.stats['jobs_created']}")
        logger.info(f"  执行作业数: {self.stats['jobs_executed']}")
        logger.info(f"  成功作业数: {self.stats['jobs_succeeded']}")
        logger.info(f"  失败作业数: {self.stats['jobs_failed']}")
        
        if self.stats['execution_times']:
            avg_time = sum(self.stats['execution_times']) / len(self.stats['execution_times'])
            max_time = max(self.stats['execution_times'])
            logger.info(f"  平均执行时间: {avg_time:.2f}秒")
            logger.info(f"  最大执行时间: {max_time:.2f}秒")
        
        logger.info("=" * 60)
