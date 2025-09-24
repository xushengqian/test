from sqlalchemy import Column, Integer, String, Boolean, Text, TIMESTAMP, ForeignKey, DECIMAL
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func
from datetime import datetime
from typing import Optional, Dict, Any

from .database import Base


class Company(Base):
    """企业模型"""
    __tablename__ = "companies"
    
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(255), nullable=False)
    code = Column(String(100), unique=True, nullable=False, index=True)
    status = Column(String(50), default="active", index=True)
    created_at = Column(TIMESTAMP, default=func.now())
    updated_at = Column(TIMESTAMP, default=func.now(), onupdate=func.now())
    
    # 关系
    company_metrics = relationship("CompanyMetric", back_populates="company")
    metric_executions = relationship("MetricExecution", back_populates="company")


class MetricDefinition(Base):
    """指标定义模型"""
    __tablename__ = "metric_definitions"
    
    id = Column(Integer, primary_key=True, index=True)
    name = Column(String(255), nullable=False)
    code = Column(String(100), unique=True, nullable=False, index=True)
    description = Column(Text)
    calculation_sql = Column(Text)
    data_source = Column(String(100))
    frequency = Column(String(50), default="daily")  # daily, weekly, monthly
    priority = Column(Integer, default=5)  # 1-10, 1 is highest priority
    timeout_seconds = Column(Integer, default=300)
    created_at = Column(TIMESTAMP, default=func.now())
    updated_at = Column(TIMESTAMP, default=func.now(), onupdate=func.now())
    
    # 关系
    company_metrics = relationship("CompanyMetric", back_populates="metric")
    metric_executions = relationship("MetricExecution", back_populates="metric")


class CompanyMetric(Base):
    """企业指标关联模型"""
    __tablename__ = "company_metrics"
    
    id = Column(Integer, primary_key=True, index=True)
    company_id = Column(Integer, ForeignKey("companies.id"), nullable=False)
    metric_id = Column(Integer, ForeignKey("metric_definitions.id"), nullable=False)
    is_enabled = Column(Boolean, default=True)
    custom_params = Column(JSONB)
    created_at = Column(TIMESTAMP, default=func.now())
    
    # 关系
    company = relationship("Company", back_populates="company_metrics")
    metric = relationship("MetricDefinition", back_populates="company_metrics")


class MetricExecution(Base):
    """指标执行记录模型"""
    __tablename__ = "metric_executions"
    
    id = Column(Integer, primary_key=True, index=True)
    company_id = Column(Integer, ForeignKey("companies.id"), nullable=False)
    metric_id = Column(Integer, ForeignKey("metric_definitions.id"), nullable=False)
    execution_id = Column(String(100), nullable=False, index=True)
    status = Column(String(50), default="pending", index=True)  # pending, running, completed, failed, timeout
    start_time = Column(TIMESTAMP)
    end_time = Column(TIMESTAMP)
    duration_ms = Column(Integer)
    result_data = Column(JSONB)
    error_message = Column(Text)
    retry_count = Column(Integer, default=0)
    created_at = Column(TIMESTAMP, default=func.now(), index=True)
    
    # 关系
    company = relationship("Company", back_populates="metric_executions")
    metric = relationship("MetricDefinition", back_populates="metric_executions")


class SystemMetric(Base):
    """系统监控指标模型"""
    __tablename__ = "system_metrics"
    
    id = Column(Integer, primary_key=True, index=True)
    metric_name = Column(String(100), nullable=False, index=True)
    metric_value = Column(DECIMAL(15, 4))
    tags = Column(JSONB)
    timestamp = Column(TIMESTAMP, default=func.now(), index=True)