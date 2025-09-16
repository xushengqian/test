import asyncio
import logging
import socket
from typing import Optional, Dict, Any

logger = logging.getLogger(__name__)

class FreeSwitchService:
    def __init__(self):
        self.host = "freeswitch"
        self.port = 8021
        self.password = "ClueCon"  # FreeSWITCH默认密码
        self.socket = None
        self.connected = False
    
    async def connect(self):
        """连接到FreeSWITCH Event Socket Interface"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((self.host, self.port))
            
            # 发送认证
            await self._send_command("auth", self.password)
            response = await self._read_response()
            
            if "Reply-Text: +OK accepted" in response:
                self.connected = True
                logger.info("Connected to FreeSWITCH")
            else:
                logger.error("Failed to authenticate with FreeSWITCH")
                self.connected = False
                
        except Exception as e:
            logger.error(f"Failed to connect to FreeSWITCH: {e}")
            self.connected = False
    
    async def disconnect(self):
        """断开FreeSWITCH连接"""
        if self.socket:
            try:
                await self._send_command("exit")
                self.socket.close()
                self.connected = False
                logger.info("Disconnected from FreeSWITCH")
            except Exception as e:
                logger.error(f"Error disconnecting from FreeSWITCH: {e}")
    
    async def make_call(self, call_id: str, phone_number: str, agent_id: str = "default") -> bool:
        """发起外呼"""
        if not self.connected:
            logger.error("Not connected to FreeSWITCH")
            return False
        
        try:
            # 构建originate命令
            originate_cmd = f"originate {{origination_uuid={call_id},agent_id={agent_id}}}sofia/gateway/trunk/{phone_number} &park()"
            
            # 发送命令
            await self._send_command("api", originate_cmd)
            response = await self._read_response()
            
            if "+OK" in response:
                logger.info(f"Call initiated: {call_id} -> {phone_number}")
                return True
            else:
                logger.error(f"Failed to initiate call: {response}")
                return False
                
        except Exception as e:
            logger.error(f"Error making call: {e}")
            return False
    
    async def hangup_call(self, call_id: str) -> bool:
        """挂断通话"""
        if not self.connected:
            logger.error("Not connected to FreeSWITCH")
            return False
        
        try:
            # 发送挂断命令
            hangup_cmd = f"uuid_kill {call_id}"
            await self._send_command("api", hangup_cmd)
            response = await self._read_response()
            
            if "+OK" in response:
                logger.info(f"Call hung up: {call_id}")
                return True
            else:
                logger.error(f"Failed to hangup call: {response}")
                return False
                
        except Exception as e:
            logger.error(f"Error hanging up call: {e}")
            return False
    
    async def get_call_status(self, call_id: str) -> Optional[Dict[str, Any]]:
        """获取通话状态"""
        if not self.connected:
            return None
        
        try:
            # 获取通话信息
            status_cmd = f"uuid_dump {call_id}"
            await self._send_command("api", status_cmd)
            response = await self._read_response()
            
            # 解析响应
            status = {}
            for line in response.split('\n'):
                if ':' in line:
                    key, value = line.split(':', 1)
                    status[key.strip()] = value.strip()
            
            return status
            
        except Exception as e:
            logger.error(f"Error getting call status: {e}")
            return None
    
    async def _send_command(self, command: str, args: str = ""):
        """发送命令到FreeSWITCH"""
        if not self.socket:
            return
        
        message = f"{command} {args}\n\n"
        self.socket.send(message.encode())
    
    async def _read_response(self) -> str:
        """读取FreeSWITCH响应"""
        if not self.socket:
            return ""
        
        response = ""
        while True:
            data = self.socket.recv(1024).decode()
            response += data
            if data.endswith('\n\n'):
                break
        
        return response