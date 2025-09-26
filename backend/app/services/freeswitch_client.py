import asyncio
import logging
import socket
from typing import Dict, Optional
import json
import uuid

from app.core.config import settings

logger = logging.getLogger(__name__)

class FreeSwitchClient:
    """FreeSwitch ESL客户端"""
    
    def __init__(self):
        self.host = settings.FREESWITCH_HOST
        self.port = settings.FREESWITCH_PORT
        self.password = settings.FREESWITCH_PASSWORD
        self.socket = None
        self.connected = False
        
    async def connect(self) -> bool:
        """连接到FreeSwitch"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((self.host, self.port))
            
            # 认证
            auth_response = await self._send_command(f"auth {self.password}")
            if "OK" in auth_response:
                self.connected = True
                logger.info("Connected to FreeSwitch ESL")
                return True
            else:
                logger.error("FreeSwitch authentication failed")
                return False
                
        except Exception as e:
            logger.error(f"Failed to connect to FreeSwitch: {e}")
            return False
    
    async def disconnect(self):
        """断开连接"""
        if self.socket:
            try:
                self.socket.close()
            except:
                pass
            self.socket = None
            self.connected = False
            logger.info("Disconnected from FreeSwitch")
    
    async def originate_call(self, destination: str, caller_id: str, variables: Dict = None) -> dict:
        """发起外呼"""
        try:
            if not self.connected:
                await self.connect()
            
            call_uuid = str(uuid.uuid4())
            
            # 构建originate命令
            var_string = ""
            if variables:
                var_list = [f"{k}={v}" for k, v in variables.items()]
                var_string = "{" + ",".join(var_list) + "}"
            
            originate_cmd = (
                f"originate {var_string}sofia/gateway/{settings.OUTBOUND_GATEWAY}/{destination} "
                f"&park() XML default"
            )
            
            response = await self._send_command(originate_cmd)
            
            if "+OK" in response:
                return {"success": True, "uuid": call_uuid, "response": response}
            else:
                return {"success": False, "error": response}
                
        except Exception as e:
            logger.error(f"Failed to originate call: {e}")
            return {"success": False, "error": str(e)}
    
    async def transfer_call(self, call_uuid: str, destination: str) -> dict:
        """转接通话"""
        try:
            if not self.connected:
                await self.connect()
            
            transfer_cmd = f"uuid_transfer {call_uuid} {destination} XML default"
            response = await self._send_command(transfer_cmd)
            
            if "+OK" in response:
                return {"success": True, "response": response}
            else:
                return {"success": False, "error": response}
                
        except Exception as e:
            logger.error(f"Failed to transfer call: {e}")
            return {"success": False, "error": str(e)}
    
    async def hangup_call(self, call_uuid: str, cause: str = "NORMAL_CLEARING") -> dict:
        """挂断通话"""
        try:
            if not self.connected:
                await self.connect()
            
            hangup_cmd = f"uuid_kill {call_uuid} {cause}"
            response = await self._send_command(hangup_cmd)
            
            if "+OK" in response:
                return {"success": True, "response": response}
            else:
                return {"success": False, "error": response}
                
        except Exception as e:
            logger.error(f"Failed to hangup call: {e}")
            return {"success": False, "error": str(e)}
    
    async def get_call_info(self, call_uuid: str) -> dict:
        """获取通话信息"""
        try:
            if not self.connected:
                await self.connect()
            
            info_cmd = f"uuid_dump {call_uuid}"
            response = await self._send_command(info_cmd)
            
            if "+OK" in response:
                return {"success": True, "data": response}
            else:
                return {"success": False, "error": response}
                
        except Exception as e:
            logger.error(f"Failed to get call info: {e}")
            return {"success": False, "error": str(e)}
    
    async def play_file(self, call_uuid: str, file_path: str) -> dict:
        """播放文件"""
        try:
            if not self.connected:
                await self.connect()
            
            play_cmd = f"uuid_broadcast {call_uuid} {file_path}"
            response = await self._send_command(play_cmd)
            
            if "+OK" in response:
                return {"success": True, "response": response}
            else:
                return {"success": False, "error": response}
                
        except Exception as e:
            logger.error(f"Failed to play file: {e}")
            return {"success": False, "error": str(e)}
    
    async def start_recording(self, call_uuid: str, file_path: str) -> dict:
        """开始录音"""
        try:
            if not self.connected:
                await self.connect()
            
            record_cmd = f"uuid_record {call_uuid} start {file_path}"
            response = await self._send_command(record_cmd)
            
            if "+OK" in response:
                return {"success": True, "response": response}
            else:
                return {"success": False, "error": response}
                
        except Exception as e:
            logger.error(f"Failed to start recording: {e}")
            return {"success": False, "error": str(e)}
    
    async def stop_recording(self, call_uuid: str, file_path: str) -> dict:
        """停止录音"""
        try:
            if not self.connected:
                await self.connect()
            
            record_cmd = f"uuid_record {call_uuid} stop {file_path}"
            response = await self._send_command(record_cmd)
            
            if "+OK" in response:
                return {"success": True, "response": response}
            else:
                return {"success": False, "error": response}
                
        except Exception as e:
            logger.error(f"Failed to stop recording: {e}")
            return {"success": False, "error": str(e)}
    
    async def bridge_calls(self, call_uuid1: str, call_uuid2: str) -> dict:
        """桥接两个通话"""
        try:
            if not self.connected:
                await self.connect()
            
            bridge_cmd = f"uuid_bridge {call_uuid1} {call_uuid2}"
            response = await self._send_command(bridge_cmd)
            
            if "+OK" in response:
                return {"success": True, "response": response}
            else:
                return {"success": False, "error": response}
                
        except Exception as e:
            logger.error(f"Failed to bridge calls: {e}")
            return {"success": False, "error": str(e)}
    
    async def get_active_calls(self) -> dict:
        """获取活跃通话列表"""
        try:
            if not self.connected:
                await self.connect()
            
            show_cmd = "show calls as json"
            response = await self._send_command(show_cmd)
            
            if "+OK" in response:
                # 解析JSON响应
                try:
                    json_start = response.find('{')
                    if json_start != -1:
                        json_data = response[json_start:]
                        calls_data = json.loads(json_data)
                        return {"success": True, "calls": calls_data}
                except json.JSONDecodeError:
                    pass
                
                return {"success": True, "calls": []}
            else:
                return {"success": False, "error": response}
                
        except Exception as e:
            logger.error(f"Failed to get active calls: {e}")
            return {"success": False, "error": str(e)}
    
    async def _send_command(self, command: str) -> str:
        """发送命令到FreeSwitch"""
        try:
            if not self.socket:
                raise Exception("Not connected to FreeSwitch")
            
            # 发送命令
            full_command = f"api {command}\n\n"
            self.socket.send(full_command.encode())
            
            # 接收响应
            response = ""
            while True:
                data = self.socket.recv(4096).decode()
                response += data
                if "\n\n" in response:
                    break
            
            return response.strip()
            
        except Exception as e:
            logger.error(f"Failed to send command: {e}")
            raise e