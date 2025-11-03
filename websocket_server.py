"""
WebSocket ???
????????????
"""
import asyncio
import json
import logging
from typing import Dict, Set
from websockets.server import serve, WebSocketServerProtocol
from call_manager import CallManager

logger = logging.getLogger(__name__)


class WebSocketServer:
    """WebSocket???"""
    
    def __init__(self, call_manager: CallManager, port: int = 8765):
        self.call_manager = call_manager
        self.port = port
        self.connections: Set[WebSocketServerProtocol] = set()
        self.call_connections: Dict[str, Set[WebSocketServerProtocol]] = {}  # call_uuid -> connections
    
    async def register_connection(self, websocket: WebSocketServerProtocol):
        """??WebSocket??"""
        self.connections.add(websocket)
        logger.info(f"??WebSocket??: {websocket.remote_address}")
    
    async def unregister_connection(self, websocket: WebSocketServerProtocol):
        """??WebSocket??"""
        self.connections.discard(websocket)
        # ??????????
        for call_uuid, conns in self.call_connections.items():
            conns.discard(websocket)
        logger.info(f"WebSocket????: {websocket.remote_address}")
    
    async def subscribe_call(self, websocket: WebSocketServerProtocol, call_uuid: str):
        """??????????"""
        if call_uuid not in self.call_connections:
            self.call_connections[call_uuid] = set()
        self.call_connections[call_uuid].add(websocket)
        
        # ????????
        async def handler(stream_type: str, text: str):
            await self.broadcast_transcription(call_uuid, stream_type, text)
        
        self.call_manager.add_transcription_handler(call_uuid, handler)
        logger.info(f"???????: {call_uuid}")
    
    async def broadcast_transcription(self, call_uuid: str, stream_type: str, text: str):
        """???????"""
        message = {
            "type": "transcription",
            "call_uuid": call_uuid,
            "stream_type": stream_type,  # "agent" or "customer"
            "text": text,
            "timestamp": asyncio.get_event_loop().time()
        }
        
        # ???????????
        connections = self.call_connections.get(call_uuid, set())
        disconnected = set()
        
        for conn in connections:
            try:
                await conn.send(json.dumps(message, ensure_ascii=False))
            except Exception as e:
                logger.error(f"??????: {e}")
                disconnected.add(conn)
        
        # ???????
        for conn in disconnected:
            connections.discard(conn)
            self.connections.discard(conn)
    
    async def handle_client(self, websocket: WebSocketServerProtocol):
        """???????"""
        await self.register_connection(websocket)
        
        try:
            async for message in websocket:
                try:
                    data = json.loads(message)
                    msg_type = data.get("type")
                    
                    if msg_type == "subscribe":
                        # ???????
                        call_uuid = data.get("call_uuid")
                        if call_uuid:
                            await self.subscribe_call(websocket, call_uuid)
                            await websocket.send(json.dumps({
                                "type": "subscribed",
                                "call_uuid": call_uuid
                            }, ensure_ascii=False))
                    
                    elif msg_type == "ping":
                        # ??
                        await websocket.send(json.dumps({"type": "pong"}, ensure_ascii=False))
                    
                except json.JSONDecodeError:
                    logger.warning(f"???JSON??: {message}")
                except Exception as e:
                    logger.error(f"??????: {e}")
        
        except Exception as e:
            logger.error(f"WebSocket????: {e}")
        finally:
            await self.unregister_connection(websocket)
    
    async def start(self):
        """??WebSocket???"""
        logger.info(f"??WebSocket???: ws://0.0.0.0:{self.port}")
        async with serve(self.handle_client, "0.0.0.0", self.port):
            await asyncio.Future()  # ????
