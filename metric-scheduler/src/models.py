from datetime import datetime
from typing import Optional, Dict, Any
from sqlalchemy import create_engine, Column, BigInteger, String, Text, DateTime, Integer, JSON, ForeignKey, Boolean, DECIMAL, TIMESTAMP, func
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import relationship, sessionmaker
from config.config import config

# 创建数据库引擎
engine = create_engine(config.mysql.connection_string, pool_size=10, max_overflow=20, pool_pre_ping=True)
Session = sessionmaker(bind=engine)
Base = declarative_base()


class Metric(Base):
    """指标定义模型"""
    __tablename__ = 'metrics'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    metric_code = Column(String(100), nullable=False, unique=True)
    metric_name = Column(String(200), nullable=False)
    metric_type = Column(String(50), nullable=False)  # SQL, STORED_PROCEDURE, SCRIPT
    metric_sql = Column(Text)
    script_path = Column(String(500))
    description = Column(Text)
    params = Column(JSON)
    is_active = Column(Boolean, default=True)
    created_at = Column(TIMESTAMP, server_default=func.current_timestamp())
    updated_at = Column(TIMESTAMP, server_default=func.current_timestamp(), onupdate=func.current_timestamp())
    
    # 关系
    metric_schedules = relationship("MetricSchedule", back_populates="metric")
    metric_results = relationship("MetricResult", back_populates="metric")


class Schedule(Base):
    """调度配置模型"""
    __tablename__ = 'schedules'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    schedule_code = Column(String(100), nullable=False, unique=True)
    schedule_name = Column(String(200), nullable=False)
    schedule_type = Column(String(50), nullable=False)  # CRON, FIXED_RATE, ONCE
    cron_expression = Column(String(100))
    fixed_rate_seconds = Column(Integer)
    start_time = Column(DateTime)
    end_time = Column(DateTime)
    is_active = Column(Boolean, default=True)
    created_at = Column(TIMESTAMP, server_default=func.current_timestamp())
    updated_at = Column(TIMESTAMP, server_default=func.current_timestamp(), onupdate=func.current_timestamp())
    
    # 关系
    metric_schedules = relationship("MetricSchedule", back_populates="schedule")


class MetricSchedule(Base):
    """指标调度关联模型"""
    __tablename__ = 'metric_schedules'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    metric_id = Column(BigInteger, ForeignKey('metrics.id'), nullable=False)
    schedule_id = Column(BigInteger, ForeignKey('schedules.id'), nullable=False)
    priority = Column(Integer, default=0)
    max_retry_times = Column(Integer, default=3)
    timeout_seconds = Column(Integer, default=3600)
    is_active = Column(Boolean, default=True)
    created_at = Column(TIMESTAMP, server_default=func.current_timestamp())
    updated_at = Column(TIMESTAMP, server_default=func.current_timestamp(), onupdate=func.current_timestamp())
    
    # 关系
    metric = relationship("Metric", back_populates="metric_schedules")
    schedule = relationship("Schedule", back_populates="metric_schedules")
    job_instances = relationship("JobInstance", back_populates="metric_schedule")


class JobInstance(Base):
    """作业实例模型"""
    __tablename__ = 'job_instances'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    job_code = Column(String(100), nullable=False, unique=True)
    metric_schedule_id = Column(BigInteger, ForeignKey('metric_schedules.id'), nullable=False)
    scheduled_time = Column(DateTime, nullable=False)
    actual_start_time = Column(DateTime)
    actual_end_time = Column(DateTime)
    status = Column(String(50), nullable=False, default='PENDING')  # PENDING, RUNNING, SUCCESS, FAILED, TIMEOUT, CANCELLED
    retry_count = Column(Integer, default=0)
    error_message = Column(Text)
    result_data = Column(JSON)
    created_at = Column(TIMESTAMP, server_default=func.current_timestamp())
    updated_at = Column(TIMESTAMP, server_default=func.current_timestamp(), onupdate=func.current_timestamp())
    
    # 关系
    metric_schedule = relationship("MetricSchedule", back_populates="job_instances")
    job_logs = relationship("JobLog", back_populates="job_instance")
    metric_results = relationship("MetricResult", back_populates="job_instance")


class JobLog(Base):
    """作业执行日志模型"""
    __tablename__ = 'job_logs'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    job_instance_id = Column(BigInteger, ForeignKey('job_instances.id'), nullable=False)
    log_level = Column(String(20), nullable=False)  # DEBUG, INFO, WARN, ERROR
    log_message = Column(Text, nullable=False)
    log_time = Column(TIMESTAMP, server_default=func.current_timestamp())
    
    # 关系
    job_instance = relationship("JobInstance", back_populates="job_logs")


class MetricResult(Base):
    """指标结果模型"""
    __tablename__ = 'metric_results'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    job_instance_id = Column(BigInteger, ForeignKey('job_instances.id'), nullable=False)
    metric_id = Column(BigInteger, ForeignKey('metrics.id'), nullable=False)
    dimension_key = Column(String(500))
    dimension_values = Column(JSON)
    metric_value = Column(DECIMAL(20, 4))
    metric_json = Column(JSON)
    calc_time = Column(DateTime, nullable=False)
    created_at = Column(TIMESTAMP, server_default=func.current_timestamp())
    
    # 关系
    job_instance = relationship("JobInstance", back_populates="metric_results")
    metric = relationship("Metric", back_populates="metric_results")


class SystemConfig(Base):
    """系统配置模型"""
    __tablename__ = 'system_configs'
    
    id = Column(BigInteger, primary_key=True, autoincrement=True)
    config_key = Column(String(100), nullable=False, unique=True)
    config_value = Column(Text, nullable=False)
    config_desc = Column(String(500))
    created_at = Column(TIMESTAMP, server_default=func.current_timestamp())
    updated_at = Column(TIMESTAMP, server_default=func.current_timestamp(), onupdate=func.current_timestamp())