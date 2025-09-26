from sqlalchemy import create_engine, Column, Integer, String, DateTime, Boolean, Text, Float, ForeignKey
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, relationship
from sqlalchemy.dialects.postgresql import UUID
import uuid
from datetime import datetime
from app.core.config import settings

# 数据库引擎
engine = create_engine(settings.DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()

class Customer(Base):
    """客户信息表"""
    __tablename__ = "customers"
    
    id = Column(Integer, primary_key=True, index=True)
    phone = Column(String(20), unique=True, index=True, nullable=False)
    name = Column(String(100))
    email = Column(String(100))
    company = Column(String(200))
    status = Column(String(20), default="active")  # active, inactive, blacklist
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    # 关联关系
    calls = relationship("CallRecord", back_populates="customer")
    tasks = relationship("CallTask", back_populates="customer")

class Agent(Base):
    """坐席信息表"""
    __tablename__ = "agents"
    
    id = Column(Integer, primary_key=True, index=True)
    agent_id = Column(String(50), unique=True, index=True, nullable=False)
    name = Column(String(100), nullable=False)
    extension = Column(String(20))
    email = Column(String(100))
    department = Column(String(100))
    status = Column(String(20), default="offline")  # online, offline, busy, break
    max_concurrent_calls = Column(Integer, default=1)
    skills = Column(Text)  # JSON格式存储技能标签
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    # 关联关系
    calls = relationship("CallRecord", back_populates="agent")

class CallTask(Base):
    """外呼任务表"""
    __tablename__ = "call_tasks"
    
    id = Column(Integer, primary_key=True, index=True)
    task_name = Column(String(200), nullable=False)
    customer_id = Column(Integer, ForeignKey("customers.id"))
    phone = Column(String(20), nullable=False)
    priority = Column(Integer, default=1)  # 1-5, 5最高
    status = Column(String(20), default="pending")  # pending, calling, completed, failed
    scheduled_time = Column(DateTime)
    max_attempts = Column(Integer, default=3)
    current_attempts = Column(Integer, default=0)
    robot_script = Column(Text)  # 机器人话术脚本
    created_at = Column(DateTime, default=datetime.utcnow)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)
    
    # 关联关系
    customer = relationship("Customer", back_populates="tasks")
    calls = relationship("CallRecord", back_populates="task")

class CallRecord(Base):
    """通话记录表"""
    __tablename__ = "call_records"
    
    id = Column(Integer, primary_key=True, index=True)
    call_uuid = Column(String(100), unique=True, index=True, nullable=False)
    task_id = Column(Integer, ForeignKey("call_tasks.id"))
    customer_id = Column(Integer, ForeignKey("customers.id"))
    agent_id = Column(Integer, ForeignKey("agents.id"), nullable=True)
    
    # 通话基本信息
    caller_number = Column(String(20))
    callee_number = Column(String(20))
    direction = Column(String(10))  # inbound, outbound
    status = Column(String(20))  # ringing, answered, hangup, failed
    
    # 时间信息
    start_time = Column(DateTime, default=datetime.utcnow)
    answer_time = Column(DateTime)
    end_time = Column(DateTime)
    duration = Column(Integer, default=0)  # 通话时长（秒）
    
    # 机器人相关
    robot_duration = Column(Integer, default=0)  # 机器人对话时长
    human_transfer = Column(Boolean, default=False)  # 是否转人工
    transfer_reason = Column(String(100))  # 转人工原因
    transfer_time = Column(DateTime)  # 转人工时间
    
    # 结果信息
    call_result = Column(String(50))  # 通话结果
    customer_intent = Column(String(100))  # 客户意向
    follow_up_required = Column(Boolean, default=False)
    notes = Column(Text)  # 备注
    
    # 文件信息
    recording_file = Column(String(500))  # 录音文件路径
    transcript = Column(Text)  # 通话转写
    
    created_at = Column(DateTime, default=datetime.utcnow)
    
    # 关联关系
    customer = relationship("Customer", back_populates="calls")
    agent = relationship("Agent", back_populates="calls")
    task = relationship("CallTask", back_populates="calls")
    conversations = relationship("Conversation", back_populates="call")

class Conversation(Base):
    """对话记录表"""
    __tablename__ = "conversations"
    
    id = Column(Integer, primary_key=True, index=True)
    call_id = Column(Integer, ForeignKey("call_records.id"))
    sequence = Column(Integer, nullable=False)  # 对话序号
    speaker = Column(String(20), nullable=False)  # robot, customer, agent
    content = Column(Text, nullable=False)  # 对话内容
    confidence = Column(Float)  # 识别置信度
    intent = Column(String(100))  # 意图识别结果
    timestamp = Column(DateTime, default=datetime.utcnow)
    
    # 关联关系
    call = relationship("CallRecord", back_populates="conversations")

class SystemLog(Base):
    """系统日志表"""
    __tablename__ = "system_logs"
    
    id = Column(Integer, primary_key=True, index=True)
    level = Column(String(20), nullable=False)  # INFO, WARN, ERROR
    module = Column(String(100))  # 模块名称
    message = Column(Text, nullable=False)
    details = Column(Text)  # 详细信息（JSON格式）
    timestamp = Column(DateTime, default=datetime.utcnow)

# 数据库初始化
async def init_db():
    """初始化数据库"""
    Base.metadata.create_all(bind=engine)

# 数据库会话依赖
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()