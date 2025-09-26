import asyncio
import logging
from typing import Dict, List, Optional
import json
from fastapi import WebSocket

logger = logging.getLogger(__name__)

class WebSocketManager:
    """WebSocket连接管理器"""
    
    def __init__(self):
        # 按类型分组的连接
        self.connections: Dict[str, Dict[str, WebSocket]] = {
            "admin": {},    # 管理员连接
            "agent": {},    # 坐席连接
            "monitor": {}   # 监控连接
        }
        
    async def connect(self, websocket: WebSocket, client_type: str, client_id: str):
        """建立WebSocket连接"""
        await websocket.accept()
        
        if client_type not in self.connections:
            self.connections[client_type] = {}
        
        self.connections[client_type][client_id] = websocket
        
        logger.info(f"WebSocket connected: {client_type}:{client_id}")
        
        # 发送连接确认
        await self.send_to_client(client_type, client_id, {
            "type": "connection_established",
            "data": {
                "client_type": client_type,
                "client_id": client_id,
                "timestamp": asyncio.get_event_loop().time()
            }
        })
    
    def disconnect(self, client_type: str, client_id: str):
        """断开WebSocket连接"""
        if (client_type in self.connections and 
            client_id in self.connections[client_type]):
            del self.connections[client_type][client_id]
            logger.info(f"WebSocket disconnected: {client_type}:{client_id}")
    
    async def send_to_client(self, client_type: str, client_id: str, message: dict) -> bool:
        """发送消息给特定客户端"""
        try:
            if (client_type in self.connections and 
                client_id in self.connections[client_type]):
                websocket = self.connections[client_type][client_id]
                await websocket.send_text(json.dumps(message))
                return True
            else:
                logger.warning(f"Client not found: {client_type}:{client_id}")
                return False
        except Exception as e:
            logger.error(f"Failed to send message to {client_type}:{client_id}: {e}")
            # 移除失效连接
            self.disconnect(client_type, client_id)
            return False
    
    async def broadcast_to_type(self, client_type: str, message: dict):
        """广播消息给某类型的所有客户端"""
        if client_type not in self.connections:
            return
        
        disconnected_clients = []
        
        for client_id, websocket in self.connections[client_type].items():
            try:
                await websocket.send_text(json.dumps(message))
            except Exception as e:
                logger.error(f"Failed to broadcast to {client_type}:{client_id}: {e}")
                disconnected_clients.append(client_id)
        
        # 清理失效连接
        for client_id in disconnected_clients:
            self.disconnect(client_type, client_id)
    
    async def broadcast_to_all(self, message: dict):
        """广播消息给所有客户端"""
        for client_type in self.connections:
            await self.broadcast_to_type(client_type, message)
    
    async def handle_message(self, client_type: str, client_id: str, message: str):
        """处理收到的消息"""
        try:
            data = json.loads(message)
            message_type = data.get("type")
            
            logger.info(f"Received message from {client_type}:{client_id}, type: {message_type}")
            
            # 根据消息类型处理
            if message_type == "ping":
                await self.send_to_client(client_type, client_id, {
                    "type": "pong",
                    "data": {"timestamp": asyncio.get_event_loop().time()}
                })
            
            elif message_type == "agent_status_update":
                # 坐席状态更新
                if client_type == "agent":
                    await self._handle_agent_status_update(client_id, data.get("data", {}))
            
            elif message_type == "call_action":
                # 通话操作
                await self._handle_call_action(client_type, client_id, data.get("data", {}))
            
            elif message_type == "subscribe":
                # 订阅特定事件
                await self._handle_subscription(client_type, client_id, data.get("data", {}))
            
            else:
                logger.warning(f"Unknown message type: {message_type}")
                
        except json.JSONDecodeError:
            logger.error(f"Invalid JSON message from {client_type}:{client_id}")
        except Exception as e:
            logger.error(f"Error handling message from {client_type}:{client_id}: {e}")
    
    async def _handle_agent_status_update(self, agent_id: str, data: dict):
        """处理坐席状态更新"""
        status = data.get("status")
        if status:
            # 广播状态更新给管理员
            await self.broadcast_to_type("admin", {
                "type": "agent_status_changed",
                "data": {
                    "agent_id": agent_id,
                    "status": status,
                    "timestamp": asyncio.get_event_loop().time()
                }
            })
    
    async def _handle_call_action(self, client_type: str, client_id: str, data: dict):
        """处理通话操作"""
        action = data.get("action")
        call_uuid = data.get("call_uuid")
        
        if not action or not call_uuid:
            return
        
        # 根据操作类型处理
        if action == "accept_call" and client_type == "agent":
            # 坐席接受通话
            await self.broadcast_to_type("admin", {
                "type": "call_accepted",
                "data": {
                    "agent_id": client_id,
                    "call_uuid": call_uuid,
                    "timestamp": asyncio.get_event_loop().time()
                }
            })
        
        elif action == "reject_call" and client_type == "agent":
            # 坐席拒绝通话
            await self.broadcast_to_type("admin", {
                "type": "call_rejected",
                "data": {
                    "agent_id": client_id,
                    "call_uuid": call_uuid,
                    "reason": data.get("reason", ""),
                    "timestamp": asyncio.get_event_loop().time()
                }
            })
        
        elif action == "hangup_call":
            # 挂断通话
            await self.broadcast_to_type("admin", {
                "type": "call_hangup",
                "data": {
                    "client_id": client_id,
                    "call_uuid": call_uuid,
                    "timestamp": asyncio.get_event_loop().time()
                }
            })
    
    async def _handle_subscription(self, client_type: str, client_id: str, data: dict):
        """处理事件订阅"""
        events = data.get("events", [])
        
        # 这里可以实现更细粒度的事件订阅
        # 目前简单确认订阅
        await self.send_to_client(client_type, client_id, {
            "type": "subscription_confirmed",
            "data": {
                "events": events,
                "timestamp": asyncio.get_event_loop().time()
            }
        })
    
    def get_connection_stats(self) -> dict:
        """获取连接统计"""
        stats = {}
        for client_type, connections in self.connections.items():
            stats[client_type] = len(connections)
        
        return {
            "total_connections": sum(stats.values()),
            "by_type": stats
        }
    
    def get_connected_clients(self, client_type: str = None) -> dict:
        """获取已连接的客户端列表"""
        if client_type:
            if client_type in self.connections:
                return {client_type: list(self.connections[client_type].keys())}
            else:
                return {client_type: []}
        else:
            result = {}
            for ctype, connections in self.connections.items():
                result[ctype] = list(connections.keys())
            return result