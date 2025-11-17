#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
MRCP客户端实现
支持ASR（语音识别）和TTS（文本转语音）功能
"""

import asyncio
import socket
import logging
import uuid
from typing import Optional, AsyncIterator, Dict, Any
from dataclasses import dataclass
from configparser import ConfigParser

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@dataclass
class MRCPMessage:
    """MRCP消息数据类"""
    request_id: str
    method: str
    status_code: Optional[int] = None
    headers: Dict[str, str] = None
    body: Optional[bytes] = None

    def __post_init__(self):
        if self.headers is None:
            self.headers = {}


class MRCPClient:
    """MRCP客户端类"""
    
    def __init__(self, config_file: str = "config/mrcp_config.ini"):
        """
        初始化MRCP客户端
        
        Args:
            config_file: 配置文件路径
        """
        self.config = ConfigParser()
        self.config.read(config_file)
        
        self.host = self.config.get('MRCP', 'server_ip', fallback='127.0.0.1')
        self.port = self.config.getint('MRCP', 'server_port', fallback=1544)
        self.version = self.config.getint('MRCP', 'protocol_version', fallback=2)
        
        self.socket: Optional[socket.socket] = None
        self.reader: Optional[asyncio.StreamReader] = None
        self.writer: Optional[asyncio.StreamWriter] = None
        
        self.session_id: Optional[str] = None
        self.is_connected = False
        
        logger.info(f"初始化MRCP客户端: {self.host}:{self.port}")
    
    async def connect(self) -> bool:
        """
        连接到MRCP服务器
        
        Returns:
            连接是否成功
        """
        try:
            self.reader, self.writer = await asyncio.open_connection(
                self.host, self.port
            )
            self.session_id = str(uuid.uuid4())
            self.is_connected = True
            logger.info(f"已连接到MRCP服务器: {self.host}:{self.port}")
            return True
        except Exception as e:
            logger.error(f"连接MRCP服务器失败: {e}")
            self.is_connected = False
            return False
    
    async def disconnect(self):
        """断开与MRCP服务器的连接"""
        if self.writer:
            self.writer.close()
            await self.writer.wait_closed()
        self.is_connected = False
        logger.info("已断开MRCP服务器连接")
    
    def _build_mrcp_message(self, method: str, headers: Dict[str, str], 
                           body: Optional[bytes] = None) -> bytes:
        """
        构建MRCP消息
        
        Args:
            method: MRCP方法（如RECOGNIZE, SPEAK等）
            headers: 消息头
            body: 消息体
            
        Returns:
            完整的MRCP消息字节流
        """
        request_id = str(uuid.uuid4())[:8]
        
        # 构建请求行
        if self.version == 2:
            request_line = f"MRCP/2.0 {len(body) if body else 0} {method} {request_id}\r\n"
        else:
            request_line = f"MRCP/1.0 {method} {request_id}\r\n"
        
        # 构建消息头
        header_lines = []
        for key, value in headers.items():
            header_lines.append(f"{key}: {value}\r\n")
        
        # 组合消息
        message = request_line.encode() + ''.join(header_lines).encode() + b"\r\n"
        if body:
            message += body
        
        return message
    
    async def _send_message(self, message: bytes) -> bool:
        """
        发送MRCP消息
        
        Args:
            message: MRCP消息
            
        Returns:
            是否发送成功
        """
        if not self.is_connected or not self.writer:
            logger.error("未连接到MRCP服务器")
            return False
        
        try:
            self.writer.write(message)
            await self.writer.drain()
            return True
        except Exception as e:
            logger.error(f"发送MRCP消息失败: {e}")
            return False
    
    async def _receive_message(self) -> Optional[MRCPMessage]:
        """
        接收MRCP消息
        
        Returns:
            MRCP消息对象
        """
        if not self.is_connected or not self.reader:
            logger.error("未连接到MRCP服务器")
            return None
        
        try:
            # 读取状态行
            status_line = await self.reader.readline()
            if not status_line:
                return None
            
            # 解析状态行
            parts = status_line.decode().strip().split()
            if len(parts) < 3:
                return None
            
            # 读取消息头
            headers = {}
            while True:
                line = await self.reader.readline()
                if line == b"\r\n":
                    break
                if line:
                    key, value = line.decode().strip().split(':', 1)
                    headers[key.strip()] = value.strip()
            
            # 读取消息体（如果有）
            body = None
            if 'Content-Length' in headers:
                content_length = int(headers['Content-Length'])
                body = await self.reader.readexactly(content_length)
            
            return MRCPMessage(
                request_id=parts[-1],
                method=parts[-2] if len(parts) > 3 else parts[1],
                status_code=int(parts[1]) if len(parts) > 3 else None,
                headers=headers,
                body=body
            )
        except Exception as e:
            logger.error(f"接收MRCP消息失败: {e}")
            return None
    
    async def recognize(self, audio_data: bytes, 
                       language: str = "zh-CN",
                       grammar: str = "builtin:grammar/boolean") -> Optional[str]:
        """
        语音识别（ASR）
        
        Args:
            audio_data: 音频数据
            language: 语言代码
            grammar: 语法规则
            
        Returns:
            识别结果文本
        """
        if not self.is_connected:
            await self.connect()
        
        headers = {
            "Channel-Identifier": self.session_id,
            "Content-Type": "application/octet-stream",
            "Content-Length": str(len(audio_data)),
            "Recognition-Language": language,
            "Grammar": grammar,
            "Start-Input-Timers": "false",
            "No-Input-Timeout": "5000",
            "Recognition-Timeout": "10000"
        }
        
        message = self._build_mrcp_message("RECOGNIZE", headers, audio_data)
        
        if await self._send_message(message):
            response = await self._receive_message()
            if response and response.status_code == 200:
                # 解析识别结果
                if 'Completion-Cause' in response.headers:
                    result_text = response.headers.get('Recognition-Result', '')
                    logger.info(f"识别结果: {result_text}")
                    return result_text
        
        return None
    
    async def synthesize(self, text: str, 
                        voice: str = "zh-CN-Standard-A",
                        language: str = "zh-CN") -> AsyncIterator[bytes]:
        """
        文本转语音（TTS）
        
        Args:
            text: 要合成的文本
            voice: 语音标识
            language: 语言代码
            
        Yields:
            音频数据块
        """
        if not self.is_connected:
            await self.connect()
        
        headers = {
            "Channel-Identifier": self.session_id,
            "Content-Type": "text/plain",
            "Content-Length": str(len(text.encode())),
            "Voice-Name": voice,
            "Speech-Language": language,
            "Audio-Encoding": "PCM"
        }
        
        message = self._build_mrcp_message("SPEAK", headers, text.encode())
        
        if await self._send_message(message):
            # 接收音频流
            while True:
                response = await self._receive_message()
                if not response:
                    break
                
                if response.body:
                    yield response.body
                
                # 检查是否完成
                if 'Speech-Marker' in response.headers:
                    if response.headers['Speech-Marker'] == 'end':
                        break
    
    async def start_recognition_stream(self) -> bool:
        """
        启动流式识别会话
        
        Returns:
            是否启动成功
        """
        if not self.is_connected:
            await self.connect()
        
        headers = {
            "Channel-Identifier": self.session_id,
            "Recognition-Language": self.config.get('ASR', 'language', fallback='zh-CN'),
            "Start-Input-Timers": "true",
            "No-Input-Timeout": "5000"
        }
        
        message = self._build_mrcp_message("RECOGNIZE", headers)
        return await self._send_message(message)
    
    async def send_audio_chunk(self, audio_chunk: bytes) -> bool:
        """
        发送音频数据块（用于流式识别）
        
        Args:
            audio_chunk: 音频数据块
            
        Returns:
            是否发送成功
        """
        if not self.is_connected:
            return False
        
        try:
            self.writer.write(audio_chunk)
            await self.writer.drain()
            return True
        except Exception as e:
            logger.error(f"发送音频块失败: {e}")
            return False
    
    async def stop_recognition(self) -> Optional[str]:
        """
        停止识别并获取结果
        
        Returns:
            识别结果
        """
        headers = {
            "Channel-Identifier": self.session_id
        }
        
        message = self._build_mrcp_message("STOP", headers)
        
        if await self._send_message(message):
            response = await self._receive_message()
            if response:
                return response.headers.get('Recognition-Result', '')
        
        return None


async def main():
    """测试示例"""
    client = MRCPClient()
    
    # 连接服务器
    if await client.connect():
        print("✓ 已连接到MRCP服务器")
        
        # 测试TTS
        print("\n测试TTS...")
        async for audio_chunk in client.synthesize("你好，这是语音合成测试"):
            print(f"接收到音频数据: {len(audio_chunk)} 字节")
        
        # 断开连接
        await client.disconnect()
        print("\n✓ 已断开连接")


if __name__ == "__main__":
    asyncio.run(main())
