"""
数据库操作模块
"""
import json
from datetime import datetime
from typing import Optional, Dict, Any, List
from pathlib import Path

import yaml
import redis
from sqlalchemy import create_engine, Column, String, Integer, DateTime, Text, Boolean
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session
from sqlalchemy.pool import QueuePool

from .logger import get_logger

logger = get_logger(__name__)

# 加载配置
config_path = Path(__file__).parent.parent.parent / "config" / "config.yaml"
with open(config_path, 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

db_config = config.get('database', {})

# 创建基类
Base = declarative_base()

# 定义模型
class CallRecord(Base):
    """通话记录表"""
    __tablename__ = 'call_records'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    call_id = Column(String(64), unique=True, index=True, comment='通话ID')
    phone_number = Column(String(20), index=True, comment='电话号码')
    call_time = Column(DateTime, default=datetime.now, comment='呼叫时间')
    answer_time = Column(DateTime, comment='应答时间')
    end_time = Column(DateTime, comment='结束时间')
    duration = Column(Integer, default=0, comment='通话时长(秒)')
    status = Column(String(20), comment='通话状态')
    transfer_to_agent = Column(Boolean, default=False, comment='是否转人工')
    agent_number = Column(String(20), comment='坐席号码')
    recording_path = Column(String(255), comment='录音文件路径')
    transcription = Column(Text, comment='通话转写文本')
    created_at = Column(DateTime, default=datetime.now)
    updated_at = Column(DateTime, default=datetime.now, onupdate=datetime.now)

class IntentLog(Base):
    """意图识别日志表"""
    __tablename__ = 'intent_logs'
    
    id = Column(Integer, primary_key=True, autoincrement=True)
    call_id = Column(String(64), index=True, comment='通话ID')
    user_input = Column(Text, comment='用户输入')
    intent = Column(String(50), comment='识别的意图')
    confidence = Column(String(10), comment='置信度')
    timestamp = Column(DateTime, default=datetime.now)

class DatabaseManager:
    """数据库管理器"""
    
    def __init__(self):
        # MySQL 配置
        mysql_config = db_config.get('mysql', {})
        self.mysql_url = (
            f"mysql+pymysql://{mysql_config.get('user', 'root')}:"
            f"{mysql_config.get('password', 'password')}@"
            f"{mysql_config.get('host', '127.0.0.1')}:"
            f"{mysql_config.get('port', 3306)}/"
            f"{mysql_config.get('database', 'freeswitch_bot')}"
            f"?charset=utf8mb4"
        )
        
        # 创建 MySQL 引擎
        self.engine = create_engine(
            self.mysql_url,
            poolclass=QueuePool,
            pool_size=10,
            max_overflow=20,
            pool_recycle=3600,
            echo=False
        )
        
        # 创建表
        Base.metadata.create_all(self.engine)
        
        # 创建会话工厂
        self.SessionLocal = sessionmaker(
            autocommit=False,
            autoflush=False,
            bind=self.engine
        )
        
        # Redis 配置
        redis_config = db_config.get('redis', {})
        self.redis_client = redis.StrictRedis(
            host=redis_config.get('host', '127.0.0.1'),
            port=redis_config.get('port', 6379),
            password=redis_config.get('password', ''),
            db=redis_config.get('db', 0),
            decode_responses=True
        )
        
        logger.info("数据库连接初始化成功")
    
    def get_session(self) -> Session:
        """获取数据库会话"""
        return self.SessionLocal()
    
    def create_call_record(self, call_id: str, phone_number: str) -> CallRecord:
        """
        创建通话记录
        
        Args:
            call_id: 通话ID
            phone_number: 电话号码
            
        Returns:
            CallRecord 对象
        """
        with self.get_session() as session:
            record = CallRecord(
                call_id=call_id,
                phone_number=phone_number,
                status='initiated'
            )
            session.add(record)
            session.commit()
            logger.info(f"创建通话记录: {call_id}")
            return record
    
    def update_call_status(self, call_id: str, status: str, **kwargs):
        """
        更新通话状态
        
        Args:
            call_id: 通话ID
            status: 状态
            **kwargs: 其他要更新的字段
        """
        with self.get_session() as session:
            record = session.query(CallRecord).filter_by(call_id=call_id).first()
            if record:
                record.status = status
                for key, value in kwargs.items():
                    if hasattr(record, key):
                        setattr(record, key, value)
                session.commit()
                logger.info(f"更新通话状态: {call_id} -> {status}")
    
    def log_intent(self, call_id: str, user_input: str, intent: str, confidence: float):
        """
        记录意图识别结果
        
        Args:
            call_id: 通话ID
            user_input: 用户输入
            intent: 识别的意图
            confidence: 置信度
        """
        with self.get_session() as session:
            log = IntentLog(
                call_id=call_id,
                user_input=user_input,
                intent=intent,
                confidence=str(confidence)
            )
            session.add(log)
            session.commit()
            logger.info(f"记录意图: {call_id} - {intent} ({confidence})")
    
    def get_call_record(self, call_id: str) -> Optional[Dict[str, Any]]:
        """
        获取通话记录
        
        Args:
            call_id: 通话ID
            
        Returns:
            通话记录字典
        """
        with self.get_session() as session:
            record = session.query(CallRecord).filter_by(call_id=call_id).first()
            if record:
                return {
                    'call_id': record.call_id,
                    'phone_number': record.phone_number,
                    'call_time': record.call_time.isoformat() if record.call_time else None,
                    'answer_time': record.answer_time.isoformat() if record.answer_time else None,
                    'end_time': record.end_time.isoformat() if record.end_time else None,
                    'duration': record.duration,
                    'status': record.status,
                    'transfer_to_agent': record.transfer_to_agent,
                    'agent_number': record.agent_number,
                    'recording_path': record.recording_path
                }
            return None
    
    # Redis 操作方法
    def cache_set(self, key: str, value: Any, expire: int = None):
        """设置缓存"""
        if isinstance(value, dict) or isinstance(value, list):
            value = json.dumps(value, ensure_ascii=False)
        self.redis_client.set(key, value)
        if expire:
            self.redis_client.expire(key, expire)
    
    def cache_get(self, key: str) -> Optional[Any]:
        """获取缓存"""
        value = self.redis_client.get(key)
        if value:
            try:
                return json.loads(value)
            except:
                return value
        return None
    
    def cache_delete(self, key: str):
        """删除缓存"""
        self.redis_client.delete(key)
    
    def get_agent_status(self, agent_number: str) -> str:
        """获取坐席状态"""
        status = self.redis_client.get(f"agent:{agent_number}:status")
        return status or 'offline'
    
    def set_agent_status(self, agent_number: str, status: str):
        """设置坐席状态"""
        self.redis_client.set(f"agent:{agent_number}:status", status)
        logger.info(f"坐席 {agent_number} 状态更新为: {status}")

# 创建全局实例
db_manager = DatabaseManager()

# 导出
__all__ = ['db_manager', 'CallRecord', 'IntentLog']