"""
Freeswitch????? - ??ESL (Event Socket Library)
??Freeswitch?????????
"""
import asyncio
import logging
from typing import Optional, Callable
import ESL

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class FreeswitchEventHandler:
    """Freeswitch?????"""
    
    def __init__(self, host: str = '127.0.0.1', port: int = 8021, password: str = 'ClueCon'):
        self.host = host
        self.port = port
        self.password = password
        self.connection: Optional[ESL.ESLconnection] = None
        self.running = False
        self.event_callbacks = {}
        
    def connect(self) -> bool:
        """???Freeswitch ESL"""
        try:
            self.connection = ESL.ESLconnection(self.host, str(self.port), self.password)
            
            if self.connection.connected():
                logger.info(f"?????Freeswitch ESL: {self.host}:{self.port}")
                
                # ????
                self.connection.events("plain", "all")
                
                return True
            else:
                logger.error("?????Freeswitch ESL")
                return False
                
        except Exception as e:
            logger.error(f"??Freeswitch ESL???: {e}")
            return False
    
    def disconnect(self):
        """??ESL??"""
        if self.connection:
            self.connection.disconnect()
            logger.info("???Freeswitch ESL??")
    
    def register_event_callback(self, event_name: str, callback: Callable):
        """??????"""
        if event_name not in self.event_callbacks:
            self.event_callbacks[event_name] = []
        self.event_callbacks[event_name].append(callback)
        logger.info(f"??????: {event_name}")
    
    async def start_event_loop(self):
        """??????"""
        if not self.connection or not self.connection.connected():
            if not self.connect():
                logger.error("?????????????Freeswitch")
                return
        
        self.running = True
        logger.info("????Freeswitch??...")
        
        while self.running:
            try:
                # ????
                event = self.connection.recvEvent()
                
                if event:
                    await self.handle_event(event)
                
                # ??CPU????
                await asyncio.sleep(0.01)
                
            except Exception as e:
                logger.error(f"???????: {e}", exc_info=True)
                await asyncio.sleep(1)
    
    async def handle_event(self, event):
        """????"""
        try:
            event_name = event.getHeader("Event-Name")
            
            if not event_name:
                return
            
            # ??????
            if event_name in ['CHANNEL_CREATE', 'CHANNEL_ANSWER', 'CHANNEL_BRIDGE', 
                             'CHANNEL_HANGUP', 'CHANNEL_DESTROY']:
                uuid = event.getHeader("Unique-ID")
                caller_number = event.getHeader("Caller-Caller-ID-Number")
                callee_number = event.getHeader("Caller-Destination-Number")
                
                logger.info(f"??: {event_name}, UUID: {uuid}, "
                          f"{caller_number} -> {callee_number}")
            
            # ???????
            if event_name in self.event_callbacks:
                for callback in self.event_callbacks[event_name]:
                    try:
                        if asyncio.iscoroutinefunction(callback):
                            await callback(event)
                        else:
                            callback(event)
                    except Exception as e:
                        logger.error(f"???????: {e}", exc_info=True)
            
            # ??????
            await self.handle_special_events(event_name, event)
            
        except Exception as e:
            logger.error(f"???????: {e}", exc_info=True)
    
    async def handle_special_events(self, event_name: str, event):
        """??????"""
        
        # ?????
        if event_name == 'CHANNEL_CREATE':
            uuid = event.getHeader("Unique-ID")
            logger.info(f"?????: {uuid}")
        
        # ????
        elif event_name == 'CHANNEL_ANSWER':
            uuid = event.getHeader("Unique-ID")
            logger.info(f"????: {uuid}")
        
        # ?????????
        elif event_name == 'CHANNEL_BRIDGE':
            uuid = event.getHeader("Unique-ID")
            other_uuid = event.getHeader("Other-Leg-Unique-ID")
            logger.info(f"????: {uuid} <-> {other_uuid}")
        
        # ????
        elif event_name == 'CHANNEL_HANGUP':
            uuid = event.getHeader("Unique-ID")
            hangup_cause = event.getHeader("Hangup-Cause")
            logger.info(f"????: {uuid}, ??: {hangup_cause}")
    
    def stop(self):
        """??????"""
        self.running = False
        logger.info("??????")
    
    def originate_call(self, destination: str, caller_id: str = '1000', 
                      extension: str = None, context: str = 'default') -> Optional[str]:
        """
        ????
        
        Args:
            destination: ????
            caller_id: ????
            extension: ??????
            context: ???????
        
        Returns:
            ??UUID?????None
        """
        try:
            if not extension:
                extension = f"transcribe_{destination}"
            
            cmd = (f"originate {{origination_caller_id_number={caller_id},"
                  f"origination_caller_id_name=Robot}}"
                  f"sofia/gateway/your_gateway/{destination} "
                  f"&park")
            
            logger.info(f"????: {cmd}")
            
            event = self.connection.api("originate", cmd)
            
            if event:
                result = event.getBody()
                
                if result and not result.startswith("-ERR"):
                    uuid = result.strip()
                    logger.info(f"???????UUID: {uuid}")
                    return uuid
                else:
                    logger.error(f"??????: {result}")
                    return None
            
        except Exception as e:
            logger.error(f"???????: {e}")
            return None
    
    def transfer_to_agent(self, uuid: str, agent_extension: str = 'agent_queue') -> bool:
        """
        ?????
        
        Args:
            uuid: ??UUID
            agent_extension: ???????
        
        Returns:
            ????
        """
        try:
            cmd = f"{uuid} {agent_extension} XML default"
            
            logger.info(f"?????: {cmd}")
            
            event = self.connection.api("uuid_transfer", cmd)
            
            if event:
                result = event.getBody()
                
                if result and result.strip() == "+OK":
                    logger.info(f"????: {uuid} -> {agent_extension}")
                    return True
                else:
                    logger.error(f"????: {result}")
                    return False
            
        except Exception as e:
            logger.error(f"?????: {e}")
            return False
    
    def hangup_call(self, uuid: str, cause: str = 'NORMAL_CLEARING') -> bool:
        """
        ????
        
        Args:
            uuid: ??UUID
            cause: ????
        
        Returns:
            ????
        """
        try:
            event = self.connection.api("uuid_kill", f"{uuid} {cause}")
            
            if event:
                result = event.getBody()
                
                if result and result.strip() == "+OK":
                    logger.info(f"????: {uuid}")
                    return True
                else:
                    logger.error(f"????: {result}")
                    return False
            
        except Exception as e:
            logger.error(f"?????: {e}")
            return False


async def test_handler():
    """???????"""
    handler = FreeswitchEventHandler()
    
    # ??????
    async def on_channel_answer(event):
        logger.info("??????")
    
    handler.register_event_callback('CHANNEL_ANSWER', on_channel_answer)
    
    # ??????
    await handler.start_event_loop()


if __name__ == '__main__':
    asyncio.run(test_handler())
