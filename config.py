"""
????
"""
import os
from typing import Optional

class Config:
    """????"""
    
    # Freeswitch??
    FS_HOST: str = os.getenv("FS_HOST", "127.0.0.1")
    FS_PORT: int = int(os.getenv("FS_PORT", "8021"))
    FS_PASSWORD: str = os.getenv("FS_PASSWORD", "ClueCon")
    
    # ASR???????????????????????ASR?????
    # ????????????
    ASR_TYPE: str = os.getenv("ASR_TYPE", "baidu")  # baidu, aliyun, xunfei
    ASR_APP_ID: Optional[str] = os.getenv("ASR_APP_ID")
    ASR_API_KEY: Optional[str] = os.getenv("ASR_API_KEY")
    ASR_SECRET_KEY: Optional[str] = os.getenv("ASR_SECRET_KEY")
    
    # WebSocket??
    WS_HOST: str = os.getenv("WS_HOST", "0.0.0.0")
    WS_PORT: int = int(os.getenv("WS_PORT", "8765"))
    
    # HTTP API??
    API_HOST: str = os.getenv("API_HOST", "0.0.0.0")
    API_PORT: int = int(os.getenv("API_PORT", "8000"))
    
    # ????
    AUDIO_SAMPLE_RATE: int = 16000
    AUDIO_CHANNELS: int = 1
    AUDIO_FORMAT: str = "PCM"
