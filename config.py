"""
????
"""
import os
from typing import Optional

class Config:
    """????"""
    
    # Freeswitch ??
    FS_HOST: str = os.getenv("FS_HOST", "localhost")
    FS_PORT: int = int(os.getenv("FS_PORT", "8021"))
    FS_PASSWORD: str = os.getenv("FS_PASSWORD", "ClueCon")
    
    # ASR ?????????????
    ASR_TYPE: str = os.getenv("ASR_TYPE", "baidu")  # baidu, tencent, aliyun, etc.
    
    # ??ASR??
    BAIDU_ASR_API_KEY: str = os.getenv("BAIDU_ASR_API_KEY", "")
    BAIDU_ASR_SECRET_KEY: str = os.getenv("BAIDU_ASR_SECRET_KEY", "")
    BAIDU_ASR_APP_ID: str = os.getenv("BAIDU_ASR_APP_ID", "")
    
    # WebSocket ??
    WS_PORT: int = int(os.getenv("WS_PORT", "8765"))
    
    # ?????
    SERVER_HOST: str = os.getenv("SERVER_HOST", "0.0.0.0")
    SERVER_PORT: int = int(os.getenv("SERVER_PORT", "8000"))
    
    # ????
    AUDIO_CHUNK_SIZE: int = 3200  # ?????
    SAMPLE_RATE: int = 8000  # ???
    
    @classmethod
    def validate(cls) -> bool:
        """????"""
        if cls.ASR_TYPE == "baidu":
            if not cls.BAIDU_ASR_API_KEY or not cls.BAIDU_ASR_SECRET_KEY:
                return False
        return True
