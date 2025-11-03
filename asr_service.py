"""
ASR ???????
????ASR???
"""
import asyncio
import aiohttp
import json
import logging
from abc import ABC, abstractmethod
from typing import Optional, AsyncIterator
from config import Config

logger = logging.getLogger(__name__)


class ASRService(ABC):
    """ASR????"""
    
    @abstractmethod
    async def transcribe_stream(self, audio_stream: AsyncIterator[bytes]) -> AsyncIterator[str]:
        """???????"""
        pass


class BaiduASRService(ASRService):
    """??ASR??"""
    
    def __init__(self):
        self.api_key = Config.BAIDU_ASR_API_KEY
        self.secret_key = Config.BAIDU_ASR_SECRET_KEY
        self.app_id = Config.BAIDU_ASR_APP_ID
        self.access_token: Optional[str] = None
        
    async def _get_access_token(self) -> str:
        """??????"""
        if self.access_token:
            return self.access_token
            
        url = f"https://aip.baidubce.com/oauth/2.0/token"
        params = {
            "grant_type": "client_credentials",
            "client_id": self.api_key,
            "client_secret": self.secret_key
        }
        
        async with aiohttp.ClientSession() as session:
            async with session.post(url, params=params) as resp:
                data = await resp.json()
                self.access_token = data.get("access_token")
                return self.access_token
    
    async def transcribe_stream(self, audio_stream: AsyncIterator[bytes]) -> AsyncIterator[str]:
        """?????????????????"""
        token = await self._get_access_token()
        url = f"https://vop.baidu.com/server_api"
        
        async for audio_chunk in audio_stream:
            # ???????????????API
            # ??????????????
            # ?????? WebSocket ?????????????
            try:
                # ??????
                yield f"[???: {len(audio_chunk)} bytes]"
            except Exception as e:
                logger.error(f"ASR????: {e}")
                yield ""


class TencentASRService(ASRService):
    """???ASR??"""
    
    async def transcribe_stream(self, audio_stream: AsyncIterator[bytes]) -> AsyncIterator[str]:
        """???????"""
        # ?????ASR??
        async for audio_chunk in audio_stream:
            yield f"[????: {len(audio_chunk)} bytes]"


def get_asr_service() -> ASRService:
    """??????ASR????"""
    asr_type = Config.ASR_TYPE.lower()
    
    if asr_type == "baidu":
        return BaiduASRService()
    elif asr_type == "tencent":
        return TencentASRService()
    else:
        raise ValueError(f"????ASR??: {asr_type}")
