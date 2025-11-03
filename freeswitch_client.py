"""
Freeswitch ???
??ESL???????
"""
import asyncio
import logging
import socket
from typing import Optional, Callable, Dict
from enum import Enum

logger = logging.getLogger(__name__)


class FSCommandType(Enum):
    """Freeswitch????"""
    API = "api"
    BGAPI = "bgapi"


class FreeswitchClient:
    """Freeswitch ESL???"""
    
    def __init__(self, host: str, port: int, password: str):
        self.host = host
        self.port = port
        self.password = password
        self.socket: Optional[socket.socket] = None
        self.reader: Optional[asyncio.StreamReader] = None
        self.writer: Optional[asyncio.StreamWriter] = None
        self.connected = False
        self.event_handlers: Dict[str, list] = {}
        
    async def connect(self):
        """???Freeswitch"""
        try:
            self.reader, self.writer = await asyncio.open_connection(
                self.host, self.port
            )
            
            # ??????
            welcome = await self.reader.readline()
            logger.info(f"FS??: {welcome.decode().strip()}")
            
            # ??
            await self.send_command(f"auth {self.password}")
            auth_response = await self.reader.readline()
            logger.info(f"????: {auth_response.decode().strip()}")
            
            if "OK" in auth_response.decode():
                self.connected = True
                # ????
                await self.send_command("event plain ALL")
                # ????????
                asyncio.create_task(self._event_loop())
                logger.info("Freeswitch????")
            else:
                raise Exception("Freeswitch????")
                
        except Exception as e:
            logger.error(f"??Freeswitch??: {e}")
            raise
    
    async def send_command(self, command: str) -> str:
        """?????Freeswitch"""
        if not self.connected or not self.writer:
            raise Exception("????Freeswitch")
        
        full_command = f"{command}\n\n"
        self.writer.write(full_command.encode())
        await self.writer.drain()
        
        # ????
        response = b""
        while True:
            line = await self.reader.readline()
            response += line
            if line == b"\n":
                break
        
        return response.decode()
    
    async def execute_command(self, command: str, app: str, args: str = "") -> str:
        """??originate???"""
        cmd = f"{command} {app} {args}"
        return await self.send_command(cmd)
    
    def on_event(self, event_type: str):
        """??????????"""
        def decorator(handler: Callable):
            if event_type not in self.event_handlers:
                self.event_handlers[event_type] = []
            self.event_handlers[event_type].append(handler)
            return handler
        return decorator
    
    async def _event_loop(self):
        """??????"""
        current_event = {}
        
        while self.connected:
            try:
                line = await self.reader.readline()
                if not line:
                    break
                
                line = line.decode().strip()
                
                if line.startswith("Content-Type:"):
                    # ?????
                    content_length = 0
                    headers = {}
                    
                    while True:
                        header_line = await self.reader.readline()
                        header_line = header_line.decode().strip()
                        
                        if not header_line:
                            break
                            
                        if ":" in header_line:
                            key, value = header_line.split(":", 1)
                            headers[key.strip()] = value.strip()
                            if key.strip().lower() == "content-length":
                                content_length = int(value.strip())
                    
                    # ??????
                    if content_length > 0:
                        body = await self.reader.readexactly(content_length)
                        event_data = body.decode()
                        
                        # ????
                        event_dict = {}
                        for line in event_data.split("\n"):
                            if ":" in line:
                                key, value = line.split(":", 1)
                                event_dict[key.strip()] = value.strip()
                        
                        # ???????
                        event_name = event_dict.get("Event-Name", "")
                        if event_name in self.event_handlers:
                            for handler in self.event_handlers[event_name]:
                                try:
                                    await handler(event_dict)
                                except Exception as e:
                                    logger.error(f"??????: {e}")
                
            except Exception as e:
                logger.error(f"??????: {e}")
                if self.connected:
                    await asyncio.sleep(1)
                else:
                    break
    
    async def disconnect(self):
        """????"""
        self.connected = False
        if self.writer:
            self.writer.close()
            await self.writer.wait_closed()
