"""
数据模型定义
"""
from datetime import datetime
from typing import Dict, Any, Optional
from sqlalchemy import (
    create_engine, Column, Integer, String, Text, DateTime, 
    Boolean, JSON, Float, ForeignKey, Index
)
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import relationship, sessionmaker, Session as DBSession
from contextlib import contextmanager
from config.config import config


# 创建数据库引擎
engine = create_engine(
    f"mysql+pymysql://{config.mysql.user}:{config.mysql.password}@"
    f"{config.mysql.host}:{config.mysql.port}/{config.mysql.database}",
    pool_size=config.mysql.pool_size,
    max_overflow=config.mysql.max_overflow,
    pool_recycle=config.mysql.pool_recycle,
    pool_pre_ping=True,
    echo=False
)

# 创建Session工厂
SessionLocal = sessionmaker(bind=engine, expire_on_commit=False)

# 基类
Base = declarative_base()


@contextmanager
def Session():
    """数据库会话上下文管理器"""
    session = SessionLocal()
    try:
        yield session
        session.commit()
    except Exception:
        session.rollback()
        raise
    finally:
        session.close()


class Metric(Base):
    """指标定义表"""
    __tablename__ = 'metrics'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    metric_code = Column(String(100), unique=True, nullable=False, comment='指标编码')
    metric_name = Column(String(200), nullable=False, comment='指标名称')
    metric_type = Column(String(50), nullable=False, comment='指标类型: SQL/STORED_PROCEDURE/SCRIPT')
    metric_sql = Column(Text, comment='SQL语句或存储过程名称')
    script_path = Column(String(500), comment='脚本路径')
    params = Column(JSON, comment='参数配置')
    description = Column(Text, comment='指标描述')
    is_active = Column(Boolean, default=True, comment='是否激活')
    created_at = Column(DateTime, default=datetime.now)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now)
    
    # 关系
    metric_schedules = relationship('MetricSchedule', back_populates='metric')
    
    __table_args__ = (
        Index('idx_metric_code', 'metric_code'),
        Index('idx_is_active', 'is_active'),
    )


class Schedule(Base):
    """调度配置表"""
    __tablename__ = 'schedules'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    schedule_code = Column(String(100), unique=True, nullable=False, comment='调度编码')
    schedule_name = Column(String(200), nullable=False, comment='调度名称')
    schedule_type = Column(String(50), nullable=False, comment='调度类型: CRON/FIXED_RATE/ONCE')
    cron_expression = Column(String(100), comment='Cron表达式')
    fixed_rate_seconds = Column(Integer, comment='固定频率(秒)')
    start_time = Column(DateTime, comment='开始时间')
    end_time = Column(DateTime, comment='结束时间')
    is_active = Column(Boolean, default=True, comment='是否激活')
    created_at = Column(DateTime, default=datetime.now)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now)
    
    # 关系
    metric_schedules = relationship('MetricSchedule', back_populates='schedule')
    
    __table_args__ = (
        Index('idx_schedule_code', 'schedule_code'),
        Index('idx_is_active', 'is_active'),
    )


class MetricSchedule(Base):
    """指标调度关联表"""
    __tablename__ = 'metric_schedules'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    metric_id = Column(Integer, ForeignKey('metrics.id'), nullable=False)
    schedule_id = Column(Integer, ForeignKey('schedules.id'), nullable=False)
    max_retry_times = Column(Integer, default=3, comment='最大重试次数')
    timeout_seconds = Column(Integer, default=3600, comment='超时时间(秒)')
    priority = Column(Integer, default=0, comment='优先级，数值越大优先级越高')
    is_active = Column(Boolean, default=True, comment='是否激活')
    created_at = Column(DateTime, default=datetime.now)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now)
    
    # 关系
    metric = relationship('Metric', back_populates='metric_schedules')
    schedule = relationship('Schedule', back_populates='metric_schedules')
    job_instances = relationship('JobInstance', back_populates='metric_schedule')
    
    __table_args__ = (
        Index('idx_metric_id', 'metric_id'),
        Index('idx_schedule_id', 'schedule_id'),
        Index('idx_is_active', 'is_active'),
        Index('idx_priority', 'priority'),
    )


class JobInstance(Base):
    """作业实例表"""
    __tablename__ = 'job_instances'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    job_code = Column(String(200), unique=True, nullable=False, comment='作业编码')
    metric_schedule_id = Column(Integer, ForeignKey('metric_schedules.id'), nullable=False)
    status = Column(String(50), default='PENDING', comment='状态: PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT')
    scheduled_time = Column(DateTime, nullable=False, comment='计划执行时间')
    actual_start_time = Column(DateTime, comment='实际开始时间')
    actual_end_time = Column(DateTime, comment='实际结束时间')
    retry_count = Column(Integer, default=0, comment='重试次数')
    error_message = Column(Text, comment='错误信息')
    result_data = Column(JSON, comment='结果数据')
    created_at = Column(DateTime, default=datetime.now)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now)
    
    # 关系
    metric_schedule = relationship('MetricSchedule', back_populates='job_instances')
    job_logs = relationship('JobLog', back_populates='job_instance')
    metric_results = relationship('MetricResult', back_populates='job_instance')
    
    __table_args__ = (
        Index('idx_job_code', 'job_code'),
        Index('idx_status', 'status'),
        Index('idx_scheduled_time', 'scheduled_time'),
        Index('idx_metric_schedule_status', 'metric_schedule_id', 'status'),
    )


class JobLog(Base):
    """作业日志表"""
    __tablename__ = 'job_logs'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    job_instance_id = Column(Integer, ForeignKey('job_instances.id'), nullable=False)
    log_level = Column(String(20), nullable=False, comment='日志级别')
    log_message = Column(Text, nullable=False, comment='日志内容')
    created_at = Column(DateTime, default=datetime.now)
    
    # 关系
    job_instance = relationship('JobInstance', back_populates='job_logs')
    
    __table_args__ = (
        Index('idx_job_instance_id', 'job_instance_id'),
        Index('idx_log_level', 'log_level'),
        Index('idx_created_at', 'created_at'),
    )


class MetricResult(Base):
    """指标结果表"""
    __tablename__ = 'metric_results'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    job_instance_id = Column(Integer, ForeignKey('job_instances.id'), nullable=False)
    metric_id = Column(Integer, ForeignKey('metrics.id'), nullable=False)
    dimension_key = Column(String(500), comment='维度键')
    dimension_values = Column(JSON, comment='维度值')
    metric_value = Column(Float, comment='指标值')
    metric_json = Column(JSON, comment='指标JSON数据')
    calc_time = Column(DateTime, nullable=False, comment='计算时间')
    created_at = Column(DateTime, default=datetime.now)
    
    # 关系
    job_instance = relationship('JobInstance', back_populates='metric_results')
    metric = relationship('Metric')
    
    __table_args__ = (
        Index('idx_job_instance_id', 'job_instance_id'),
        Index('idx_metric_id', 'metric_id'),
        Index('idx_calc_time', 'calc_time'),
        Index('idx_dimension_key', 'dimension_key'),
    )


class SchedulerMetrics(Base):
    """调度器性能指标表"""
    __tablename__ = 'scheduler_metrics'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    metric_time = Column(DateTime, nullable=False, comment='指标时间')
    pending_jobs = Column(Integer, default=0, comment='待执行作业数')
    running_jobs = Column(Integer, default=0, comment='运行中作业数')
    success_jobs = Column(Integer, default=0, comment='成功作业数')
    failed_jobs = Column(Integer, default=0, comment='失败作业数')
    avg_execution_time = Column(Float, comment='平均执行时间(秒)')
    max_execution_time = Column(Float, comment='最大执行时间(秒)')
    throughput = Column(Float, comment='吞吐量(作业/分钟)')
    created_at = Column(DateTime, default=datetime.now)
    
    __table_args__ = (
        Index('idx_metric_time', 'metric_time'),
    )


def init_db():
    """初始化数据库表"""
    Base.metadata.create_all(engine)
