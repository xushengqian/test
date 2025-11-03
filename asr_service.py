"""
ASR???????
????ASR?????
"""
import logging
import json
import requests
import websocket
import threading
import base64
from typing import Callable, Optional
from config import Config

logger = logging.getLogger(__name__)


class ASRService:
    """ASR????"""
    
    def __init__(self):
        self.on_result: Optional[Callable] = None
        self.on_error: Optional[Callable] = None
    
    def start_recognition(self, audio_stream):
        """????"""
        raise NotImplementedError
    
    def stop_recognition(self):
        """????"""
        raise NotImplementedError
    
    def process_audio(self, audio_data: bytes):
        """??????"""
        raise NotImplementedError


class BaiduASRService(ASRService):
    """??ASR??"""
    
    def __init__(self, app_id: str = None, api_key: str = None, secret_key: str = None):
        super().__init__()
        self.app_id = app_id or Config.ASR_APP_ID
        self.api_key = api_key or Config.ASR_API_KEY
        self.secret_key = secret_key or Config.ASR_SECRET_KEY
        self.access_token: Optional[str] = None
        self.ws_url: Optional[str] = None
        self.ws: Optional[websocket.WebSocketApp] = None
        self.running = False
        
    def _get_access_token(self) -> bool:
        """??access_token"""
        url = "https://aip.baidubce.com/oauth/2.0/token"
        params = {
            "grant_type": "client_credentials",
            "client_id": self.api_key,
            "client_secret": self.secret_key
        }
        try:
            response = requests.post(url, params=params)
            data = response.json()
            if "access_token" in data:
                self.access_token = data["access_token"]
                logger.info("????ASR access_token??")
                return True
            else:
                logger.error(f"??access_token??: {data}")
                return False
        except Exception as e:
            logger.error(f"??access_token??: {e}")
            return False
    
    def start_recognition(self, audio_stream):
        """????"""
        if not self.access_token:
            if not self._get_access_token():
                return False
        
        # ????????WebSocket??
        self.ws_url = f"wss://vop.baidu.com/realtime_asr?access_token={self.access_token}"
        
        self.running = True
        self.ws = websocket.WebSocketApp(
            self.ws_url,
            on_message=self._on_message,
            on_error=self._on_error,
            on_close=self._on_close
        )
        
        thread = threading.Thread(target=self.ws.run_forever, daemon=True)
        thread.start()
        
        # ??????
        start_params = {
            "type": "START",
            "data": {
                "appid": self.app_id,
                "appkey": self.api_key,
                "dev_pid": 1537,  # ???(?????????)
                "format": "pcm",
                "rate": 16000,
                "channel": 1,
                "cuid": "test_client"
            }
        }
        self.ws.send(json.dumps(start_params))
        logger.info("??ASR?????")
        return True
    
    def _on_message(self, ws, message):
        """??????"""
        try:
            data = json.loads(message)
            if data.get("type") == "FIN_TEXT":
                result = data.get("result", "")
                if result and self.on_result:
                    self.on_result(result)
            elif data.get("type") == "PARTIAL":
                result = data.get("result", "")
                if result and self.on_result:
                    self.on_result(result, is_final=False)
        except Exception as e:
            logger.error(f"??ASR????: {e}")
    
    def _on_error(self, ws, error):
        """????"""
        logger.error(f"ASR WebSocket??: {error}")
        if self.on_error:
            self.on_error(error)
    
    def _on_close(self, ws, close_status_code, close_msg):
        """????"""
        logger.info("ASR WebSocket?????")
        self.running = False
    
    def process_audio(self, audio_data: bytes):
        """??????"""
        if not self.ws or not self.running:
            return
        
        try:
            # ??????
            audio_data_b64 = base64.b64encode(audio_data).decode('utf-8')
            data = {
                "type": "AUDIO",
                "data": audio_data_b64
            }
            self.ws.send(json.dumps(data))
        except Exception as e:
            logger.error(f"????????: {e}")
    
    def stop_recognition(self):
        """????"""
        if self.ws:
            try:
                stop_data = {"type": "STOP"}
                self.ws.send(json.dumps(stop_data))
                self.ws.close()
            except:
                pass
        self.running = False
        logger.info("??ASR?????")


class ASRServiceFactory:
    """ASR????"""
    
    @staticmethod
    def create(asr_type: str = None, **kwargs) -> ASRService:
        """??ASR????"""
        asr_type = asr_type or Config.ASR_TYPE
        
        if asr_type == "baidu":
            return BaiduASRService(**kwargs)
        elif asr_type == "aliyun":
            # TODO: ?????ASR
            raise NotImplementedError("???ASR????")
        elif asr_type == "xunfei":
            # TODO: ????ASR
            raise NotImplementedError("??ASR????")
        else:
            raise ValueError(f"????ASR??: {asr_type}")
