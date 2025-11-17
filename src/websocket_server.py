#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
WebSocket服务器
用于实时传输音频流到客户端
"""

import asyncio
import websockets
import json
import logging
from typing import Set, Optional
from datetime import datetime
from configparser import ConfigParser

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class AudioWebSocketServer:
    """音频WebSocket服务器"""
    
    def __init__(self, config_file: str = "config/mrcp_config.ini"):
        """
        初始化WebSocket服务器
        
        Args:
            config_file: 配置文件路径
        """
        self.config = ConfigParser()
        self.config.read(config_file)
        
        self.host = self.config.get('STREAM', 'stream_host', fallback='0.0.0.0')
        self.port = self.config.getint('STREAM', 'stream_port', fallback=8765)
        
        # 连接的客户端集合
        self.clients: Set[websockets.WebSocketServerProtocol] = set()
        
        # 统计信息
        self.total_connections = 0
        self.bytes_sent = 0
        self.start_time = datetime.now()
        
        logger.info(f"WebSocket服务器初始化: {self.host}:{self.port}")
    
    async def register_client(self, websocket: websockets.WebSocketServerProtocol):
        """
        注册新客户端
        
        Args:
            websocket: WebSocket连接
        """
        self.clients.add(websocket)
        self.total_connections += 1
        
        client_info = f"{websocket.remote_address[0]}:{websocket.remote_address[1]}"
        logger.info(f"✓ 新客户端连接: {client_info} (当前: {len(self.clients)})")
        
        # 发送欢迎消息
        welcome_msg = {
            'type': 'welcome',
            'message': '欢迎连接到音频流服务器',
            'timestamp': datetime.now().isoformat(),
            'server_info': {
                'sample_rate': self.config.getint('AUDIO', 'sample_rate', fallback=16000),
                'channels': self.config.getint('AUDIO', 'channels', fallback=1),
                'encoding': 'pcm'
            }
        }
        await websocket.send(json.dumps(welcome_msg))
    
    async def unregister_client(self, websocket: websockets.WebSocketServerProtocol):
        """
        注销客户端
        
        Args:
            websocket: WebSocket连接
        """
        self.clients.discard(websocket)
        client_info = f"{websocket.remote_address[0]}:{websocket.remote_address[1]}"
        logger.info(f"✗ 客户端断开: {client_info} (当前: {len(self.clients)})")
    
    async def broadcast_audio(self, audio_data: bytes):
        """
        广播音频数据到所有客户端
        
        Args:
            audio_data: 音频数据
        """
        if not self.clients:
            return
        
        # 准备消息
        message = {
            'type': 'audio',
            'timestamp': datetime.now().isoformat(),
            'size': len(audio_data)
        }
        
        # 发送JSON元数据
        metadata = json.dumps(message)
        
        # 广播到所有客户端
        disconnected = set()
        for client in self.clients:
            try:
                # 发送元数据
                await client.send(metadata)
                # 发送音频数据
                await client.send(audio_data)
                self.bytes_sent += len(audio_data)
            except websockets.exceptions.ConnectionClosed:
                disconnected.add(client)
            except Exception as e:
                logger.error(f"发送数据失败: {e}")
                disconnected.add(client)
        
        # 移除断开的客户端
        for client in disconnected:
            await self.unregister_client(client)
    
    async def send_to_client(self, websocket: websockets.WebSocketServerProtocol, 
                            audio_data: bytes):
        """
        发送音频数据到指定客户端
        
        Args:
            websocket: WebSocket连接
            audio_data: 音频数据
        """
        try:
            message = {
                'type': 'audio',
                'timestamp': datetime.now().isoformat(),
                'size': len(audio_data)
            }
            
            await websocket.send(json.dumps(message))
            await websocket.send(audio_data)
            self.bytes_sent += len(audio_data)
        except Exception as e:
            logger.error(f"发送数据到客户端失败: {e}")
    
    async def handle_client_message(self, websocket: websockets.WebSocketServerProtocol, 
                                    message: str):
        """
        处理客户端消息
        
        Args:
            websocket: WebSocket连接
            message: 客户端消息
        """
        try:
            data = json.loads(message)
            msg_type = data.get('type')
            
            if msg_type == 'ping':
                # 响应ping
                await websocket.send(json.dumps({
                    'type': 'pong',
                    'timestamp': datetime.now().isoformat()
                }))
            
            elif msg_type == 'stats':
                # 发送统计信息
                stats = self.get_statistics()
                await websocket.send(json.dumps({
                    'type': 'stats',
                    'data': stats
                }))
            
            elif msg_type == 'subscribe':
                # 订阅特定流
                stream_id = data.get('stream_id')
                logger.info(f"客户端订阅流: {stream_id}")
                await websocket.send(json.dumps({
                    'type': 'subscribed',
                    'stream_id': stream_id
                }))
            
            else:
                logger.warning(f"未知消息类型: {msg_type}")
        
        except json.JSONDecodeError:
            logger.error("无效的JSON消息")
        except Exception as e:
            logger.error(f"处理客户端消息失败: {e}")
    
    async def handle_client(self, websocket: websockets.WebSocketServerProtocol, 
                           path: str):
        """
        处理客户端连接
        
        Args:
            websocket: WebSocket连接
            path: 请求路径
        """
        await self.register_client(websocket)
        
        try:
            async for message in websocket:
                if isinstance(message, str):
                    await self.handle_client_message(websocket, message)
                else:
                    logger.warning("接收到非文本消息")
        except websockets.exceptions.ConnectionClosed:
            pass
        finally:
            await self.unregister_client(websocket)
    
    def get_statistics(self) -> dict:
        """
        获取服务器统计信息
        
        Returns:
            统计信息字典
        """
        uptime = (datetime.now() - self.start_time).total_seconds()
        
        return {
            'current_connections': len(self.clients),
            'total_connections': self.total_connections,
            'bytes_sent': self.bytes_sent,
            'uptime_seconds': uptime,
            'start_time': self.start_time.isoformat()
        }
    
    async def start(self):
        """启动WebSocket服务器"""
        logger.info(f"启动WebSocket服务器: ws://{self.host}:{self.port}")
        
        async with websockets.serve(self.handle_client, self.host, self.port):
            logger.info("✓ WebSocket服务器已启动")
            await asyncio.Future()  # 永久运行
    
    async def broadcast_loop(self, audio_source):
        """
        音频广播循环
        
        Args:
            audio_source: 音频源（异步生成器）
        """
        logger.info("开始音频广播循环")
        
        async for audio_chunk in audio_source:
            if self.clients:
                await self.broadcast_audio(audio_chunk)
            await asyncio.sleep(0.01)  # 避免过度CPU使用


class AudioStreamClient:
    """WebSocket音频流客户端"""
    
    def __init__(self, server_url: str = "ws://localhost:8765"):
        """
        初始化WebSocket客户端
        
        Args:
            server_url: WebSocket服务器URL
        """
        self.server_url = server_url
        self.websocket: Optional[websockets.WebSocketClientProtocol] = None
        self.is_connected = False
        
        logger.info(f"WebSocket客户端初始化: {server_url}")
    
    async def connect(self) -> bool:
        """
        连接到WebSocket服务器
        
        Returns:
            是否连接成功
        """
        try:
            self.websocket = await websockets.connect(self.server_url)
            self.is_connected = True
            logger.info(f"✓ 已连接到服务器: {self.server_url}")
            
            # 接收欢迎消息
            welcome = await self.websocket.recv()
            logger.info(f"服务器消息: {welcome}")
            
            return True
        except Exception as e:
            logger.error(f"连接服务器失败: {e}")
            self.is_connected = False
            return False
    
    async def receive_audio_stream(self):
        """接收音频流"""
        if not self.is_connected or not self.websocket:
            logger.error("未连接到服务器")
            return
        
        try:
            while self.is_connected:
                # 接收元数据
                metadata = await self.websocket.recv()
                data = json.loads(metadata)
                
                if data['type'] == 'audio':
                    # 接收音频数据
                    audio_data = await self.websocket.recv()
                    logger.info(f"接收到音频数据: {len(audio_data)} 字节")
                    yield audio_data
        except websockets.exceptions.ConnectionClosed:
            logger.info("服务器连接已关闭")
            self.is_connected = False
        except Exception as e:
            logger.error(f"接收音频流失败: {e}")
    
    async def send_message(self, message_type: str, data: dict = None):
        """
        发送消息到服务器
        
        Args:
            message_type: 消息类型
            data: 消息数据
        """
        if not self.is_connected or not self.websocket:
            logger.error("未连接到服务器")
            return
        
        message = {'type': message_type}
        if data:
            message.update(data)
        
        try:
            await self.websocket.send(json.dumps(message))
        except Exception as e:
            logger.error(f"发送消息失败: {e}")
    
    async def disconnect(self):
        """断开连接"""
        if self.websocket:
            await self.websocket.close()
        self.is_connected = False
        logger.info("已断开服务器连接")


async def test_server():
    """测试WebSocket服务器"""
    server = AudioWebSocketServer()
    
    # 启动服务器
    await server.start()


async def test_client():
    """测试WebSocket客户端"""
    client = AudioStreamClient("ws://localhost:8765")
    
    if await client.connect():
        # 接收音频流
        async for audio_chunk in client.receive_audio_stream():
            print(f"收到音频: {len(audio_chunk)} 字节")
        
        await client.disconnect()


if __name__ == "__main__":
    # 运行服务器测试
    asyncio.run(test_server())
