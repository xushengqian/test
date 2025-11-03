"""
WebSocket??? - ??Freeswitch???????????
"""
import asyncio
import websockets
import json
import struct
import logging
from typing import Dict, Set
from datetime import datetime

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class AudioStreamHandler:
    """??????"""
    
    def __init__(self, asr_service):
        self.asr_service = asr_service
        self.sessions: Dict[str, dict] = {}
        self.web_clients: Set[websockets.WebSocketServerProtocol] = set()
        
    async def handle_freeswitch_audio(self, websocket, path):
        """????Freeswitch????"""
        session_id = None
        
        try:
            logger.info(f"??Freeswitch??: {websocket.remote_address}")
            
            async for message in websocket:
                if isinstance(message, str):
                    # ??????
                    data = json.loads(message)
                    
                    if data.get('type') == 'session_start':
                        session_id = data.get('session_id')
                        call_info = data.get('call_info', {})
                        
                        self.sessions[session_id] = {
                            'session_id': session_id,
                            'call_info': call_info,
                            'start_time': datetime.now().isoformat(),
                            'agent_channel': None,
                            'customer_channel': None,
                            'transcription': []
                        }
                        
                        logger.info(f"????: {session_id}, ????: {call_info}")
                        
                        # ??Web???
                        await self.broadcast_to_web({
                            'type': 'session_start',
                            'session_id': session_id,
                            'call_info': call_info
                        })
                        
                    elif data.get('type') == 'session_end':
                        session_id = data.get('session_id')
                        if session_id in self.sessions:
                            logger.info(f"????: {session_id}")
                            await self.broadcast_to_web({
                                'type': 'session_end',
                                'session_id': session_id
                            })
                            # ??????
                            await self.save_session(session_id)
                            del self.sessions[session_id]
                            
                elif isinstance(message, bytes):
                    # ??????
                    # ??: [session_id_length(4??)][session_id][channel(1??)][audio_data]
                    if len(message) < 5:
                        continue
                        
                    # ????
                    session_id_len = struct.unpack('!I', message[0:4])[0]
                    session_id = message[4:4+session_id_len].decode('utf-8')
                    channel = message[4+session_id_len]  # 0=??, 1=??
                    audio_data = message[5+session_id_len:]
                    
                    if session_id not in self.sessions:
                        logger.warning(f"?????????: {session_id}")
                        continue
                    
                    # ???ASR??????
                    await self.process_audio(session_id, channel, audio_data)
                    
        except websockets.exceptions.ConnectionClosed:
            logger.info(f"Freeswitch????: {websocket.remote_address}")
        except Exception as e:
            logger.error(f"????????: {e}", exc_info=True)
        finally:
            if session_id and session_id in self.sessions:
                await self.save_session(session_id)
                del self.sessions[session_id]
    
    async def process_audio(self, session_id: str, channel: int, audio_data: bytes):
        """?????????????"""
        try:
            # ??ASR??
            channel_name = 'customer' if channel == 0 else 'agent'
            
            text = await self.asr_service.recognize(
                audio_data, 
                session_id=session_id,
                channel=channel_name
            )
            
            if text:
                # ??????
                transcription = {
                    'session_id': session_id,
                    'channel': channel_name,
                    'text': text,
                    'timestamp': datetime.now().isoformat()
                }
                
                # ???????
                self.sessions[session_id]['transcription'].append(transcription)
                
                logger.info(f"[{session_id}] {channel_name}: {text}")
                
                # ?????Web???
                await self.broadcast_to_web({
                    'type': 'transcription',
                    **transcription
                })
                
        except Exception as e:
            logger.error(f"?????????: {e}", exc_info=True)
    
    async def handle_web_client(self, websocket, path):
        """??Web?????"""
        logger.info(f"??Web?????: {websocket.remote_address}")
        self.web_clients.add(websocket)
        
        try:
            # ??????????
            await websocket.send(json.dumps({
                'type': 'active_sessions',
                'sessions': list(self.sessions.keys())
            }))
            
            async for message in websocket:
                # ????Web??????
                data = json.loads(message)
                
                if data.get('type') == 'get_session_history':
                    session_id = data.get('session_id')
                    if session_id in self.sessions:
                        await websocket.send(json.dumps({
                            'type': 'session_history',
                            'session_id': session_id,
                            'history': self.sessions[session_id]['transcription']
                        }))
                        
        except websockets.exceptions.ConnectionClosed:
            logger.info(f"Web?????: {websocket.remote_address}")
        finally:
            self.web_clients.discard(websocket)
    
    async def broadcast_to_web(self, message: dict):
        """???Web???????"""
        if self.web_clients:
            message_str = json.dumps(message, ensure_ascii=False)
            await asyncio.gather(
                *[client.send(message_str) for client in self.web_clients],
                return_exceptions=True
            )
    
    async def save_session(self, session_id: str):
        """??????????"""
        try:
            if session_id in self.sessions:
                session_data = self.sessions[session_id]
                # ???????MongoDB??????
                logger.info(f"??????: {session_id}, ?{len(session_data['transcription'])}???")
        except Exception as e:
            logger.error(f"?????????: {e}", exc_info=True)


async def start_websocket_servers(asr_service, fs_host='0.0.0.0', fs_port=8765, web_port=8766):
    """??WebSocket???"""
    handler = AudioStreamHandler(asr_service)
    
    # ??Freeswitch??????
    fs_server = await websockets.serve(
        handler.handle_freeswitch_audio,
        fs_host,
        fs_port,
        max_size=10 * 1024 * 1024  # 10MB
    )
    logger.info(f"Freeswitch????????: ws://{fs_host}:{fs_port}")
    
    # ??Web??????
    web_server = await websockets.serve(
        handler.handle_web_client,
        fs_host,
        web_port
    )
    logger.info(f"Web????????: ws://{fs_host}:{web_port}")
    
    return fs_server, web_server, handler


if __name__ == '__main__':
    # ???
    from asr_service import ASRService
    
    async def main():
        asr = ASRService()
        await start_websocket_servers(asr)
        
        # ????
        await asyncio.Future()
    
    asyncio.run(main())
