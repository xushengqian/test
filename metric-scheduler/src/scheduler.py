import time
import uuid
import threading
from datetime import datetime, timedelta
from typing import List, Optional
from concurrent.futures import ThreadPoolExecutor, as_completed
from loguru import logger
from sqlalchemy import and_, or_
from sqlalchemy.orm import Session
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from apscheduler.triggers.interval import IntervalTrigger
from croniter import croniter
import redis
from models import Session as DBSession, MetricSchedule, JobInstance, Schedule, JobLog
from executor import MetricExecutor
from config.config import config


class DistributedLock:
    """基于Redis的分布式锁"""
    
    def __init__(self, redis_client: redis.Redis, key: str, timeout: int = 60):
        self.redis_client = redis_client
        self.key = f"lock:{key}"
        self.timeout = timeout
        self.identifier = str(uuid.uuid4())
    
    def acquire(self) -> bool:
        """获取锁"""
        return self.redis_client.set(self.key, self.identifier, nx=True, ex=self.timeout)
    
    def release(self):
        """释放锁"""
        pipe = self.redis_client.pipeline(True)
        while True:
            try:
                pipe.watch(self.key)
                if pipe.get(self.key) == self.identifier.encode():
                    pipe.multi()
                    pipe.delete(self.key)
                    pipe.execute()
                    return True
                pipe.unwatch()
                break
            except redis.WatchError:
                pass
        return False
    
    def __enter__(self):
        if not self.acquire():
            raise RuntimeError(f"无法获取锁: {self.key}")
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.release()


class MetricScheduler:
    """指标调度器"""
    
    def __init__(self):
        self.scheduler = BackgroundScheduler()
        self.executor_pool = ThreadPoolExecutor(max_workers=config.scheduler.max_concurrent_jobs)
        self.running_jobs = {}
        self.redis_client = self._init_redis()
        self._is_running = False
        
    def _init_redis(self) -> Optional[redis.Redis]:
        """初始化Redis连接"""
        try:
            client = redis.Redis(
                host=config.redis.host,
                port=config.redis.port,
                password=config.redis.password or None,
                db=config.redis.db,
                decode_responses=False
            )
            client.ping()
            logger.info("Redis连接成功")
            return client
        except Exception as e:
            logger.warning(f"Redis连接失败，将使用单机模式: {e}")
            return None
    
    def start(self):
        """启动调度器"""
        logger.info("启动指标调度器...")
        self._is_running = True
        
        # 启动APScheduler
        self.scheduler.start()
        
        # 添加定时任务
        self.scheduler.add_job(
            self._scan_and_create_jobs,
            IntervalTrigger(seconds=config.scheduler.job_scan_interval),
            id='scan_jobs',
            name='扫描并创建作业'
        )
        
        self.scheduler.add_job(
            self._check_timeout_jobs,
            IntervalTrigger(seconds=config.scheduler.job_timeout_check_interval),
            id='check_timeout',
            name='检查超时作业'
        )
        
        self.scheduler.add_job(
            self._retry_failed_jobs,
            IntervalTrigger(seconds=config.scheduler.retry_interval),
            id='retry_jobs',
            name='重试失败作业'
        )
        
        # 立即执行一次扫描
        self._scan_and_create_jobs()
        
        logger.info("指标调度器启动完成")
        
        # 主循环：处理待执行的作业
        while self._is_running:
            try:
                self._process_pending_jobs()
                time.sleep(1)
            except KeyboardInterrupt:
                logger.info("接收到停止信号")
                break
            except Exception as e:
                logger.error(f"处理作业时出错: {e}")
                time.sleep(5)
    
    def stop(self):
        """停止调度器"""
        logger.info("停止指标调度器...")
        self._is_running = False
        self.scheduler.shutdown(wait=True)
        self.executor_pool.shutdown(wait=True)
        if self.redis_client:
            self.redis_client.close()
        logger.info("指标调度器已停止")
    
    def _scan_and_create_jobs(self):
        """扫描调度配置并创建作业"""
        try:
            with DBSession() as session:
                # 获取所有激活的调度配置
                active_schedules = session.query(MetricSchedule).join(
                    Schedule
                ).filter(
                    MetricSchedule.is_active == True,
                    Schedule.is_active == True
                ).all()
                
                for metric_schedule in active_schedules:
                    self._create_job_for_schedule(session, metric_schedule)
                    
        except Exception as e:
            logger.error(f"扫描调度配置失败: {e}")
    
    def _create_job_for_schedule(self, session: Session, metric_schedule: MetricSchedule):
        """为调度配置创建作业"""
        try:
            schedule = metric_schedule.schedule
            now = datetime.now()
            
            # 检查是否需要创建新作业
            next_run_time = self._calculate_next_run_time(schedule, now)
            if not next_run_time:
                return
            
            # 检查该时间点是否已有作业
            existing_job = session.query(JobInstance).filter(
                JobInstance.metric_schedule_id == metric_schedule.id,
                JobInstance.scheduled_time == next_run_time
            ).first()
            
            if existing_job:
                return
            
            # 创建新作业
            job_code = f"{metric_schedule.metric.metric_code}_{next_run_time.strftime('%Y%m%d%H%M%S')}_{uuid.uuid4().hex[:8]}"
            job_instance = JobInstance(
                job_code=job_code,
                metric_schedule_id=metric_schedule.id,
                scheduled_time=next_run_time,
                status='PENDING'
            )
            session.add(job_instance)
            session.commit()
            
            logger.info(f"创建新作业: {job_code}, 计划执行时间: {next_run_time}")
            
        except Exception as e:
            logger.error(f"创建作业失败: {e}")
            session.rollback()
    
    def _calculate_next_run_time(self, schedule: Schedule, base_time: datetime) -> Optional[datetime]:
        """计算下次运行时间"""
        # 检查调度是否在有效期内
        if schedule.start_time and base_time < schedule.start_time:
            base_time = schedule.start_time
        if schedule.end_time and base_time > schedule.end_time:
            return None
        
        if schedule.schedule_type == 'CRON':
            # 使用croniter计算下次执行时间
            cron = croniter(schedule.cron_expression, base_time)
            next_time = cron.get_next(datetime)
            # 只创建未来5分钟内的作业
            if next_time <= base_time + timedelta(minutes=5):
                return next_time
                
        elif schedule.schedule_type == 'FIXED_RATE':
            # 固定频率，创建下一个执行时间
            return base_time + timedelta(seconds=schedule.fixed_rate_seconds)
            
        elif schedule.schedule_type == 'ONCE':
            # 一次性任务
            if schedule.start_time and schedule.start_time > base_time:
                return schedule.start_time
                
        return None
    
    def _process_pending_jobs(self):
        """处理待执行的作业"""
        try:
            with DBSession() as session:
                # 获取待执行的作业
                now = datetime.now()
                pending_jobs = session.query(JobInstance).filter(
                    JobInstance.status == 'PENDING',
                    JobInstance.scheduled_time <= now
                ).order_by(
                    JobInstance.scheduled_time
                ).limit(10).all()
                
                for job in pending_jobs:
                    # 检查是否可以执行（并发控制）
                    if len(self.running_jobs) >= config.scheduler.max_concurrent_jobs:
                        break
                    
                    # 尝试获取分布式锁（如果有Redis）
                    if self.redis_client:
                        lock = DistributedLock(self.redis_client, f"job:{job.job_code}")
                        if not lock.acquire():
                            continue
                    else:
                        lock = None
                    
                    # 提交作业到线程池
                    future = self.executor_pool.submit(self._execute_job, job.id, lock)
                    self.running_jobs[job.id] = future
                    
        except Exception as e:
            logger.error(f"处理待执行作业失败: {e}")
    
    def _execute_job(self, job_id: int, lock: Optional[DistributedLock]):
        """执行作业"""
        try:
            with DBSession() as session:
                job = session.query(JobInstance).get(job_id)
                if not job:
                    logger.error(f"作业不存在: {job_id}")
                    return
                
                # 执行作业
                executor = MetricExecutor(session)
                success = executor.execute_job(job)
                
                # 处理执行结果
                if not success and job.retry_count < job.metric_schedule.max_retry_times:
                    # 需要重试
                    job.retry_count += 1
                    job.status = 'PENDING'
                    session.commit()
                    logger.info(f"作业将重试: {job.job_code}, 重试次数: {job.retry_count}")
                    
        except Exception as e:
            logger.error(f"执行作业异常: {e}")
        finally:
            # 释放锁
            if lock:
                lock.release()
            # 从运行列表中移除
            self.running_jobs.pop(job_id, None)
    
    def _check_timeout_jobs(self):
        """检查超时的作业"""
        try:
            with DBSession() as session:
                # 获取运行中的作业
                running_jobs = session.query(JobInstance).filter(
                    JobInstance.status == 'RUNNING'
                ).all()
                
                now = datetime.now()
                for job in running_jobs:
                    # 计算运行时长
                    if job.actual_start_time:
                        duration = (now - job.actual_start_time).total_seconds()
                        if duration > job.metric_schedule.timeout_seconds:
                            # 标记为超时
                            job.status = 'TIMEOUT'
                            job.actual_end_time = now
                            job.error_message = f"作业执行超时，已运行 {duration:.0f} 秒"
                            
                            # 记录日志
                            job_log = JobLog(
                                job_instance_id=job.id,
                                log_level='ERROR',
                                log_message=job.error_message
                            )
                            session.add(job_log)
                            
                            logger.warning(f"作业超时: {job.job_code}")
                
                session.commit()
                
        except Exception as e:
            logger.error(f"检查超时作业失败: {e}")
    
    def _retry_failed_jobs(self):
        """重试失败的作业"""
        try:
            with DBSession() as session:
                # 获取需要重试的作业
                retry_time = datetime.now() - timedelta(seconds=config.scheduler.retry_interval)
                failed_jobs = session.query(JobInstance).filter(
                    JobInstance.status.in_(['FAILED', 'TIMEOUT']),
                    JobInstance.retry_count < JobInstance.metric_schedule.max_retry_times,
                    JobInstance.updated_at <= retry_time
                ).all()
                
                for job in failed_jobs:
                    job.status = 'PENDING'
                    job.retry_count += 1
                    logger.info(f"重试作业: {job.job_code}, 第 {job.retry_count} 次重试")
                
                session.commit()
                
        except Exception as e:
            logger.error(f"重试失败作业出错: {e}")