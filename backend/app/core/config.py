from pydantic_settings import BaseSettings
from typing import Optional
import os

class Settings(BaseSettings):
    """应用配置"""
    
    # 基本配置
    APP_NAME: str = "FS机器人外呼系统"
    DEBUG: bool = True
    HOST: str = "0.0.0.0"
    PORT: int = 8000
    
    # 数据库配置
    DATABASE_URL: str = "postgresql://postgres:password@localhost:5432/robot_call_db"
    
    # Redis配置
    REDIS_URL: str = "redis://localhost:6379/0"
    
    # FreeSwitch配置
    FREESWITCH_HOST: str = "localhost"
    FREESWITCH_PORT: int = 8021
    FREESWITCH_PASSWORD: str = "ClueCon"
    
    # 外呼配置
    OUTBOUND_GATEWAY: str = "outbound"
    MAX_CONCURRENT_CALLS: int = 100
    CALL_TIMEOUT: int = 60
    
    # 语音服务配置
    TTS_SERVICE_URL: str = "http://localhost:8001"
    ASR_SERVICE_URL: str = "http://localhost:8002"
    
    # AI服务配置
    OPENAI_API_KEY: Optional[str] = None
    AZURE_SPEECH_KEY: Optional[str] = None
    AZURE_SPEECH_REGION: str = "eastasia"
    
    # 安全配置
    SECRET_KEY: str = "your-secret-key-here"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = 30
    
    # 文件存储
    RECORDINGS_DIR: str = "/var/lib/freeswitch/recordings"
    TEMP_DIR: str = "/tmp"
    
    # 队列配置
    HUMAN_QUEUE_NAME: str = "human_agents"
    MAX_QUEUE_SIZE: int = 50
    QUEUE_TIMEOUT: int = 300  # 5分钟
    
    # 监控配置
    METRICS_ENABLED: bool = True
    LOG_LEVEL: str = "INFO"
    
    class Config:
        env_file = ".env"
        case_sensitive = True

# 创建全局配置实例
settings = Settings()

# 环境变量覆盖
if os.getenv("DATABASE_URL"):
    settings.DATABASE_URL = os.getenv("DATABASE_URL")

if os.getenv("REDIS_URL"):
    settings.REDIS_URL = os.getenv("REDIS_URL")

if os.getenv("OPENAI_API_KEY"):
    settings.OPENAI_API_KEY = os.getenv("OPENAI_API_KEY")

if os.getenv("AZURE_SPEECH_KEY"):
    settings.AZURE_SPEECH_KEY = os.getenv("AZURE_SPEECH_KEY")