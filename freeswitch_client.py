"""
Freeswitch ESL????????Freeswitch?????
"""
import socket
import threading
import logging
from typing import Callable, Optional, Dict
from config import Config

logger = logging.getLogger(__name__)


class FreeswitchClient:
    """Freeswitch ESL???"""
    
    def __init__(self, host: str = None, port: int = None, password: str = None):
        self.host = host or Config.FS_HOST
        self.port = port or Config.FS_PORT
        self.password = password or Config.FS_PASSWORD
        self.socket: Optional[socket.socket] = None
        self.connected = False
        self.event_handlers: Dict[str, Callable] = {}
        self.running = False
        self.thread: Optional[threading.Thread] = None
        
    def connect(self) -> bool:
        """???Freeswitch"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((self.host, self.port))
            
            # ??????
            welcome = self._read_response()
            logger.info(f"Freeswitch????: {welcome}")
            
            # ??
            auth_response = self._send_command(f"auth {self.password}")
            if "OK" in auth_response:
                self.connected = True
                logger.info("Freeswitch????")
                return True
            else:
                logger.error(f"Freeswitch????: {auth_response}")
                return False
                
        except Exception as e:
            logger.error(f"??Freeswitch??: {e}")
            return False
    
    def _send_command(self, command: str) -> str:
        """?????Freeswitch"""
        if not self.socket:
            return ""
        
        try:
            command = command + "\n\n"
            self.socket.send(command.encode('utf-8'))
            return self._read_response()
        except Exception as e:
            logger.error(f"??????: {e}")
            return ""
    
    def _read_response(self) -> str:
        """??Freeswitch??"""
        if not self.socket:
            return ""
        
        try:
            response = ""
            while True:
                data = self.socket.recv(4096).decode('utf-8', errors='ignore')
                if not data:
                    break
                response += data
                if "\n\n" in response or "\r\n\r\n" in response:
                    break
            return response
        except Exception as e:
            logger.error(f"??????: {e}")
            return ""
    
    def subscribe_events(self, events: list = None):
        """????"""
        if events is None:
            events = ["CHANNEL_CREATE", "CHANNEL_ANSWER", "CHANNEL_BRIDGE", 
                     "CHANNEL_HANGUP", "CUSTOM", "sofia::register", 
                     "sofia::unregister", "sofia::gateway-add", 
                     "sofia::gateway-state"]
        
        event_string = " ".join(events)
        response = self._send_command(f"events plain {event_string}")
        logger.info(f"????: {response}")
    
    def register_event_handler(self, event_type: str, handler: Callable):
        """???????"""
        self.event_handlers[event_type] = handler
    
    def start_listening(self):
        """??????"""
        if not self.connected:
            logger.error("????Freeswitch")
            return
        
        self.running = True
        self.thread = threading.Thread(target=self._event_loop, daemon=True)
        self.thread.start()
        logger.info("????Freeswitch??")
    
    def _event_loop(self):
        """????"""
        while self.running and self.connected:
            try:
                event_data = self._read_response()
                if event_data:
                    self._process_event(event_data)
            except Exception as e:
                logger.error(f"??????: {e}")
                if self.running:
                    # ????
                    self.connected = False
                    self.connect()
    
    def _process_event(self, event_data: str):
        """????"""
        event_dict = {}
        for line in event_data.split('\n'):
            if ':' in line:
                key, value = line.split(':', 1)
                event_dict[key.strip()] = value.strip()
        
        event_name = event_dict.get('Event-Name', '')
        logger.debug(f"????: {event_name}")
        
        # ????????
        if event_name in self.event_handlers:
            try:
                self.event_handlers[event_name](event_dict)
            except Exception as e:
                logger.error(f"???? {event_name} ??: {e}")
    
    def execute_command(self, command: str, args: str = "") -> str:
        """??Freeswitch??"""
        full_command = f"api {command} {args}"
        return self._send_command(full_command)
    
    def uuid_record(self, uuid: str, file_path: str, channels: str = "both"):
        """????"""
        command = f"uuid_record {uuid} start {file_path} {channels}"
        return self.execute_command("uuid_record", f"{uuid} start {file_path} {channels}")
    
    def uuid_bridge(self, uuid1: str, uuid2: str):
        """????UUID"""
        return self.execute_command("uuid_bridge", f"{uuid1} {uuid2}")
    
    def disconnect(self):
        """????"""
        self.running = False
        if self.socket:
            try:
                self.socket.close()
            except:
                pass
        self.connected = False
        logger.info("???Freeswitch??")
