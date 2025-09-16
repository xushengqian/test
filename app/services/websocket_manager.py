import asyncio
import json
import logging
from typing import Dict, List, Any
from fastapi import WebSocket

logger = logging.getLogger(__name__)

class WebSocketManager:
    def __init__(self):
        # 存储每个通话的WebSocket连接
        self.active_connections: Dict[str, List[WebSocket]] = {}
    
    async def connect(self, websocket: WebSocket, call_id: str):
        """接受WebSocket连接"""
        await websocket.accept()
        
        if call_id not in self.active_connections:
            self.active_connections[call_id] = []
        
        self.active_connections[call_id].append(websocket)
        logger.info(f"WebSocket connected for call: {call_id}")
    
    def disconnect(self, websocket: WebSocket, call_id: str):
        """断开WebSocket连接"""
        if call_id in self.active_connections:
            if websocket in self.active_connections[call_id]:
                self.active_connections[call_id].remove(websocket)
                logger.info(f"WebSocket disconnected for call: {call_id}")
            
            # 如果没有连接了，删除通话记录
            if not self.active_connections[call_id]:
                del self.active_connections[call_id]
    
    async def broadcast_to_call(self, call_id: str, message: Dict[str, Any]):
        """向特定通话的所有连接广播消息"""
        if call_id not in self.active_connections:
            return
        
        # 创建要发送的消息
        message_str = json.dumps(message, ensure_ascii=False)
        
        # 向所有连接发送消息
        disconnected = []
        for websocket in self.active_connections[call_id]:
            try:
                await websocket.send_text(message_str)
            except Exception as e:
                logger.error(f"Error sending message to WebSocket: {e}")
                disconnected.append(websocket)
        
        # 清理断开的连接
        for websocket in disconnected:
            self.disconnect(websocket, call_id)
    
    async def broadcast_to_all(self, message: Dict[str, Any]):
        """向所有连接广播消息"""
        for call_id in list(self.active_connections.keys()):
            await self.broadcast_to_call(call_id, message)
    
    def get_connection_count(self, call_id: str) -> int:
        """获取特定通话的连接数"""
        return len(self.active_connections.get(call_id, []))
    
    def get_total_connections(self) -> int:
        """获取总连接数"""
        return sum(len(connections) for connections in self.active_connections.values())
    
    def get_active_calls(self) -> List[str]:
        """获取活跃的通话列表"""
        return list(self.active_connections.keys())