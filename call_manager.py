"""
?????
?????????????????
"""
import asyncio
import logging
from typing import Dict, Optional
from freeswitch_client import FreeswitchClient
from asr_service import get_asr_service
import uuid

logger = logging.getLogger(__name__)


class CallSession:
    """????"""
    
    def __init__(self, call_uuid: str, caller: str, callee: str):
        self.call_uuid = call_uuid
        self.caller = caller  # ????????
        self.callee = callee  # ????????
        self.robot_active = True  # ???????
        self.agent_active = False  # ??????
        self.agent_stream_task: Optional[asyncio.Task] = None
        self.customer_stream_task: Optional[asyncio.Task] = None
        self.transcription_handlers = []  # ????????


class CallManager:
    """?????"""
    
    def __init__(self, fs_client: FreeswitchClient):
        self.fs_client = fs_client
        self.sessions: Dict[str, CallSession] = {}
        self.asr_service = get_asr_service()
        self._setup_event_handlers()
    
    def _setup_event_handlers(self):
        """???????"""
        
        @self.fs_client.on_event("CHANNEL_ANSWER")
        async def on_channel_answer(event: dict):
            """??????"""
            call_uuid = event.get("Unique-ID", "")
            logger.info(f"????: {call_uuid}")
            # ????????????
        
        @self.fs_client.on_event("CHANNEL_HANGUP")
        async def on_channel_hangup(event: dict):
            """??????"""
            call_uuid = event.get("Unique-ID", "")
            logger.info(f"????: {call_uuid}")
            if call_uuid in self.sessions:
                await self._cleanup_session(call_uuid)
        
        @self.fs_client.on_event("CHANNEL_BRIDGE")
        async def on_channel_bridge(event: dict):
            """???????????"""
            call_uuid = event.get("Unique-ID", "")
            other_uuid = event.get("Other-Leg-Unique-ID", "")
            logger.info(f"????: {call_uuid} <-> {other_uuid}")
            
            # ?????
            if call_uuid in self.sessions:
                session = self.sessions[call_uuid]
                if session.robot_active:
                    await self._transfer_to_agent(session, other_uuid)
    
    async def make_outbound_call(self, caller: str, callee: str) -> str:
        """????"""
        call_uuid = str(uuid.uuid4())
        
        # Originate????
        # originate {origination_uuid=<call_uuid>}user/<callee> <caller> XML default
        originate_cmd = (
            f"originate {{origination_uuid={call_uuid}}}"
            f"user/{callee} {caller} XML default"
        )
        
        try:
            result = await self.fs_client.execute_command("bgapi", originate_cmd)
            logger.info(f"??????: {result}")
            
            # ????
            session = CallSession(call_uuid, caller, callee)
            self.sessions[call_uuid] = session
            
            return call_uuid
        except Exception as e:
            logger.error(f"????: {e}")
            raise
    
    async def _transfer_to_agent(self, session: CallSession, agent_uuid: str):
        """???????"""
        logger.info(f"???: {session.call_uuid} -> {agent_uuid}")
        
        session.robot_active = False
        session.agent_active = True
        
        # ????Fork??????????????
        from audio_stream_handler import setup_audio_fork
        from config import Config
        http_url = f"http://{Config.SERVER_HOST}:{Config.SERVER_PORT}"
        await setup_audio_fork(self.fs_client, session.call_uuid, http_url)
        
        # ???????????
        await self._start_transcription(session)
    
    async def _start_transcription(self, session: CallSession):
        """???????"""
        # ??????Freeswitch?mod_audio_fork??????????
        # ??????RTP?
        
        # ???????
        session.agent_stream_task = asyncio.create_task(
            self._process_audio_stream(session.call_uuid, "agent")
        )
        session.customer_stream_task = asyncio.create_task(
            self._process_audio_stream(session.call_uuid, "customer")
        )
    
    async def _process_audio_stream(self, call_uuid: str, stream_type: str):
        """?????????"""
        logger.info(f"???????: {call_uuid} - {stream_type}")
        
        # ?????Freeswitch?????
        # ????????fork???
        # ????????Freeswitch????
        
        async def audio_generator():
            """????????"""
            # ?????Freeswitch??????
            while call_uuid in self.sessions:
                # ?????
                await asyncio.sleep(0.1)
                yield b'\x00' * 1600  # ??????
        
        try:
            async for text in self.asr_service.transcribe_stream(audio_generator()):
                if text:
                    await self._on_transcription_result(call_uuid, stream_type, text)
        except Exception as e:
            logger.error(f"???????: {e}")
    
    async def _on_transcription_result(self, call_uuid: str, stream_type: str, text: str):
        """???????"""
        session = self.sessions.get(call_uuid)
        if not session:
            return
        
        logger.info(f"???? [{stream_type}]: {text}")
        
        # ???????
        for handler in session.transcription_handlers:
            try:
                await handler(stream_type, text)
            except Exception as e:
                logger.error(f"????????: {e}")
    
    def add_transcription_handler(self, call_uuid: str, handler):
        """??????????"""
        session = self.sessions.get(call_uuid)
        if session:
            session.transcription_handlers.append(handler)
    
    async def _cleanup_session(self, call_uuid: str):
        """????"""
        session = self.sessions.get(call_uuid)
        if session:
            if session.agent_stream_task:
                session.agent_stream_task.cancel()
            if session.customer_stream_task:
                session.customer_stream_task.cancel()
            del self.sessions[call_uuid]
            logger.info(f"?????: {call_uuid}")
