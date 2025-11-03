"""
??????????????????
"""
import logging
import uuid as uuid_lib
from typing import Dict, Optional
from freeswitch_client import FreeswitchClient
from asr_service import ASRServiceFactory
from websocket_server import WebSocketServer

logger = logging.getLogger(__name__)


class CallSession:
    """????"""
    
    def __init__(self, call_uuid: str, direction: str = "outbound"):
        self.call_uuid = call_uuid
        self.direction = direction  # outbound: ??, inbound: ??
        self.agent_uuid: Optional[str] = None  # ??UUID
        self.customer_uuid: Optional[str] = None  # ??UUID
        self.agent_asr: Optional[object] = None  # ??ASR??
        self.customer_asr: Optional[object] = None  # ??ASR??
        self.status = "init"  # init, ringing, answered, bridged, hangup
        self.transcription: list = []  # ??????
        
    def add_transcription(self, speaker: str, text: str, is_final: bool = True):
        """??????"""
        self.transcription.append({
            "speaker": speaker,  # "agent" ? "customer"
            "text": text,
            "is_final": is_final,
            "timestamp": None  # TODO: ?????
        })


class CallManager:
    """?????"""
    
    def __init__(self, fs_client: FreeswitchClient, ws_server: WebSocketServer):
        self.fs_client = fs_client
        self.ws_server = ws_server
        self.sessions: Dict[str, CallSession] = {}
        self._setup_event_handlers()
    
    def _setup_event_handlers(self):
        """???????"""
        self.fs_client.register_event_handler("CHANNEL_CREATE", self._on_channel_create)
        self.fs_client.register_event_handler("CHANNEL_ANSWER", self._on_channel_answer)
        self.fs_client.register_event_handler("CHANNEL_BRIDGE", self._on_channel_bridge)
        self.fs_client.register_event_handler("CHANNEL_HANGUP", self._on_channel_hangup)
    
    def _on_channel_create(self, event: dict):
        """??????"""
        call_uuid = event.get("Unique-ID", "")
        if call_uuid:
            session = CallSession(call_uuid, direction="outbound")
            self.sessions[call_uuid] = session
            logger.info(f"??????: {call_uuid}")
    
    def _on_channel_answer(self, event: dict):
        """??????"""
        call_uuid = event.get("Unique-ID", "")
        session = self.sessions.get(call_uuid)
        if session:
            session.status = "answered"
            logger.info(f"?????: {call_uuid}")
    
    def _on_channel_bridge(self, event: dict):
        """???????????"""
        other_uuid = event.get("Other-Leg-Unique-ID", "")
        call_uuid = event.get("Unique-ID", "")
        
        session = self.sessions.get(call_uuid)
        if not session:
            session = self.sessions.get(other_uuid)
            call_uuid = other_uuid
        
        if session:
            session.status = "bridged"
            # ???????UUID
            if not session.agent_uuid:
                # ?????????????????
                session.customer_uuid = call_uuid
                session.agent_uuid = other_uuid
            else:
                session.customer_uuid = other_uuid
            
            logger.info(f"??????????: ??={session.customer_uuid}, ??={session.agent_uuid}")
            
            # ??????
            self._start_transcription(session)
    
    def _on_channel_hangup(self, event: dict):
        """??????"""
        call_uuid = event.get("Unique-ID", "")
        session = self.sessions.get(call_uuid)
        if session:
            session.status = "hangup"
            # ????
            self._stop_transcription(session)
            logger.info(f"?????: {call_uuid}")
    
    def _start_transcription(self, session: CallSession):
        """??????"""
        try:
            # ?????ASR??
            session.agent_asr = ASRServiceFactory.create()
            session.agent_asr.on_result = lambda text, is_final=True: self._on_transcription_result(
                session, "agent", text, is_final
            )
            session.agent_asr.start_recognition(None)
            
            # ?????ASR??
            session.customer_asr = ASRServiceFactory.create()
            session.customer_asr.on_result = lambda text, is_final=True: self._on_transcription_result(
                session, "customer", text, is_final
            )
            session.customer_asr.start_recognition(None)
            
            logger.info(f"???????: {session.call_uuid}")
            
            # TODO: ?????Freeswitch??????????????ASR??
            # ???????
            # 1. ??Freeswitch?????????
            # 2. ???????????
            # 3. ?????ASR??
            
        except Exception as e:
            logger.error(f"??????: {e}")
    
    def _stop_transcription(self, session: CallSession):
        """??????"""
        try:
            if session.agent_asr:
                session.agent_asr.stop_recognition()
            if session.customer_asr:
                session.customer_asr.stop_recognition()
            logger.info(f"???????: {session.call_uuid}")
        except Exception as e:
            logger.error(f"??????: {e}")
    
    def _on_transcription_result(self, session: CallSession, speaker: str, text: str, is_final: bool = True):
        """??????"""
        session.add_transcription(speaker, text, is_final)
        
        # ??WebSocket??????
        self.ws_server.broadcast_transcription({
            "call_uuid": session.call_uuid,
            "speaker": speaker,
            "text": text,
            "is_final": is_final
        })
        
        logger.debug(f"???? [{speaker}]: {text}")
    
    def make_outbound_call(self, number: str, params: dict = None) -> str:
        """????"""
        call_uuid = str(uuid_lib.uuid4())
        params = params or {}
        
        # ??originate??
        # ????????????????
        command = f"originate {{{{origination_uuid={call_uuid}}}}}user/{number} &echo"
        
        result = self.fs_client.execute_command("originate", command)
        logger.info(f"????: {number}, UUID: {call_uuid}, ??: {result}")
        
        return call_uuid
    
    def transfer_to_agent(self, call_uuid: str, agent_number: str) -> bool:
        """?????"""
        session = self.sessions.get(call_uuid)
        if not session:
            logger.error(f"???????: {call_uuid}")
            return False
        
        # ??bridge???????
        # ??????????????
        command = f"{call_uuid} user/{agent_number}"
        result = self.fs_client.execute_command("uuid_bridge", command)
        
        logger.info(f"?????: {agent_number}, ??: {result}")
        return "OK" in result
    
    def get_session(self, call_uuid: str) -> Optional[CallSession]:
        """??????"""
        return self.sessions.get(call_uuid)
