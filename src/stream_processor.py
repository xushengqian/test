#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
实时语音流处理器
从FreeSWITCH捕获音频流并进行处理
"""

import asyncio
import logging
import struct
import wave
from typing import AsyncIterator, Optional, Callable
from dataclasses import dataclass
from datetime import datetime
from configparser import ConfigParser
import socket

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@dataclass
class AudioConfig:
    """音频配置"""
    sample_rate: int = 16000
    channels: int = 1
    sample_width: int = 2  # 16-bit = 2 bytes
    chunk_size: int = 1024


class AudioStreamProcessor:
    """音频流处理器"""
    
    def __init__(self, config_file: str = "config/mrcp_config.ini"):
        """
        初始化音频流处理器
        
        Args:
            config_file: 配置文件路径
        """
        self.config = ConfigParser()
        self.config.read(config_file)
        
        # 音频配置
        self.audio_config = AudioConfig(
            sample_rate=self.config.getint('AUDIO', 'sample_rate', fallback=16000),
            channels=self.config.getint('AUDIO', 'channels', fallback=1),
            sample_width=self.config.getint('AUDIO', 'bit_depth', fallback=16) // 8,
            chunk_size=self.config.getint('AUDIO', 'chunk_size', fallback=1024)
        )
        
        # 流配置
        self.stream_host = self.config.get('STREAM', 'stream_host', fallback='0.0.0.0')
        self.stream_port = self.config.getint('STREAM', 'stream_port', fallback=8765)
        
        # 缓冲区
        self.audio_buffer = bytearray()
        self.is_recording = False
        self.callbacks = []
        
        logger.info(f"音频流处理器已初始化: {self.audio_config.sample_rate}Hz, "
                   f"{self.audio_config.channels}声道")
    
    def register_callback(self, callback: Callable[[bytes], None]):
        """
        注册音频数据回调函数
        
        Args:
            callback: 回调函数，接收音频数据块
        """
        self.callbacks.append(callback)
        logger.info(f"已注册回调函数: {callback.__name__}")
    
    async def connect_to_freeswitch(self, host: str = '127.0.0.1', 
                                   port: int = 8021) -> bool:
        """
        连接到FreeSWITCH ESL接口
        
        Args:
            host: FreeSWITCH主机地址
            port: ESL端口
            
        Returns:
            是否连接成功
        """
        try:
            self.reader, self.writer = await asyncio.open_connection(host, port)
            
            # 读取欢迎消息
            welcome = await self.reader.readline()
            logger.info(f"FreeSWITCH响应: {welcome.decode().strip()}")
            
            # 认证
            self.writer.write(b"auth ClueCon\n\n")
            await self.writer.drain()
            
            response = await self.reader.readline()
            if b"Reply-Text: +OK" in response:
                logger.info("✓ 已连接到FreeSWITCH ESL")
                return True
            else:
                logger.error("FreeSWITCH认证失败")
                return False
        except Exception as e:
            logger.error(f"连接FreeSWITCH失败: {e}")
            return False
    
    async def subscribe_to_events(self):
        """订阅FreeSWITCH事件"""
        if not hasattr(self, 'writer'):
            logger.error("未连接到FreeSWITCH")
            return
        
        # 订阅音频相关事件
        events = ["CHANNEL_AUDIO", "DTMF", "DETECTED_SPEECH"]
        for event in events:
            command = f"event plain {event}\n\n"
            self.writer.write(command.encode())
            await self.writer.drain()
        
        logger.info(f"已订阅事件: {', '.join(events)}")
    
    async def get_audio_stream(self) -> AsyncIterator[bytes]:
        """
        获取音频流
        
        Yields:
            音频数据块
        """
        self.is_recording = True
        logger.info("开始捕获音频流...")
        
        try:
            while self.is_recording:
                # 从缓冲区读取数据
                if len(self.audio_buffer) >= self.audio_config.chunk_size:
                    chunk = bytes(self.audio_buffer[:self.audio_config.chunk_size])
                    self.audio_buffer = self.audio_buffer[self.audio_config.chunk_size:]
                    
                    # 调用回调函数
                    for callback in self.callbacks:
                        try:
                            callback(chunk)
                        except Exception as e:
                            logger.error(f"回调函数执行失败: {e}")
                    
                    yield chunk
                else:
                    # 等待更多数据
                    await asyncio.sleep(0.01)
        finally:
            self.is_recording = False
            logger.info("音频流捕获已停止")
    
    async def process_freeswitch_events(self):
        """处理FreeSWITCH事件流"""
        if not hasattr(self, 'reader'):
            logger.error("未连接到FreeSWITCH")
            return
        
        logger.info("开始处理FreeSWITCH事件...")
        
        try:
            while True:
                line = await self.reader.readline()
                if not line:
                    break
                
                line_str = line.decode().strip()
                
                # 解析事件
                if line_str.startswith("Event-Name:"):
                    event_name = line_str.split(":", 1)[1].strip()
                    logger.debug(f"接收到事件: {event_name}")
                
                # 解析音频数据
                elif line_str.startswith("Content-Length:"):
                    content_length = int(line_str.split(":", 1)[1].strip())
                    
                    # 跳过空行
                    await self.reader.readline()
                    
                    # 读取音频数据
                    if content_length > 0:
                        audio_data = await self.reader.readexactly(content_length)
                        self.audio_buffer.extend(audio_data)
                        logger.debug(f"接收到音频数据: {content_length} 字节")
        except Exception as e:
            logger.error(f"处理FreeSWITCH事件失败: {e}")
    
    def add_audio_data(self, audio_data: bytes):
        """
        添加音频数据到缓冲区
        
        Args:
            audio_data: 音频数据
        """
        self.audio_buffer.extend(audio_data)
    
    async def save_to_file(self, filename: str, duration: float = 10.0):
        """
        将音频流保存到文件
        
        Args:
            filename: 输出文件名
            duration: 录制时长（秒）
        """
        logger.info(f"开始录制音频到文件: {filename}")
        
        # 打开WAV文件
        with wave.open(filename, 'wb') as wav_file:
            wav_file.setnchannels(self.audio_config.channels)
            wav_file.setsampwidth(self.audio_config.sample_width)
            wav_file.setframerate(self.audio_config.sample_rate)
            
            # 计算总帧数
            total_frames = int(duration * self.audio_config.sample_rate)
            frames_written = 0
            
            # 录制音频
            async for chunk in self.get_audio_stream():
                wav_file.writeframes(chunk)
                frames_written += len(chunk) // self.audio_config.sample_width
                
                if frames_written >= total_frames:
                    break
        
        logger.info(f"✓ 音频已保存到: {filename}")
    
    async def process_with_callback(self, callback: Callable[[bytes], None], 
                                   duration: Optional[float] = None):
        """
        使用回调函数处理音频流
        
        Args:
            callback: 回调函数
            duration: 处理时长（秒），None表示持续处理
        """
        start_time = datetime.now()
        
        async for chunk in self.get_audio_stream():
            callback(chunk)
            
            if duration:
                elapsed = (datetime.now() - start_time).total_seconds()
                if elapsed >= duration:
                    break
    
    def stop_recording(self):
        """停止录制"""
        self.is_recording = False
        logger.info("停止录制")
    
    def get_audio_info(self) -> dict:
        """
        获取音频信息
        
        Returns:
            音频配置信息字典
        """
        return {
            'sample_rate': self.audio_config.sample_rate,
            'channels': self.audio_config.channels,
            'sample_width': self.audio_config.sample_width,
            'chunk_size': self.audio_config.chunk_size,
            'buffer_size': len(self.audio_buffer)
        }


class RTPStreamProcessor:
    """RTP流处理器"""
    
    def __init__(self, port: int = 4000):
        """
        初始化RTP流处理器
        
        Args:
            port: RTP监听端口
        """
        self.port = port
        self.socket: Optional[socket.socket] = None
        self.is_running = False
        
    async def start(self) -> bool:
        """
        启动RTP接收
        
        Returns:
            是否启动成功
        """
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self.socket.bind(('0.0.0.0', self.port))
            self.socket.setblocking(False)
            self.is_running = True
            logger.info(f"RTP流处理器已启动，监听端口: {self.port}")
            return True
        except Exception as e:
            logger.error(f"启动RTP流处理器失败: {e}")
            return False
    
    async def receive_rtp_stream(self) -> AsyncIterator[bytes]:
        """
        接收RTP流
        
        Yields:
            RTP音频数据
        """
        if not self.is_running or not self.socket:
            logger.error("RTP流处理器未运行")
            return
        
        loop = asyncio.get_event_loop()
        
        while self.is_running:
            try:
                # 非阻塞接收
                data = await loop.sock_recv(self.socket, 2048)
                
                if len(data) > 12:  # RTP头部至少12字节
                    # 提取RTP payload（跳过RTP头部）
                    rtp_header_len = 12
                    payload = data[rtp_header_len:]
                    yield payload
            except Exception as e:
                if self.is_running:
                    logger.error(f"接收RTP数据失败: {e}")
                await asyncio.sleep(0.01)
    
    def stop(self):
        """停止RTP接收"""
        self.is_running = False
        if self.socket:
            self.socket.close()
        logger.info("RTP流处理器已停止")


async def main():
    """测试示例"""
    processor = AudioStreamProcessor()
    
    print("音频流处理器配置:")
    info = processor.get_audio_info()
    for key, value in info.items():
        print(f"  {key}: {value}")
    
    # 模拟添加音频数据
    print("\n模拟接收音频数据...")
    for i in range(10):
        dummy_audio = bytes([i] * 1024)
        processor.add_audio_data(dummy_audio)
        await asyncio.sleep(0.1)
    
    # 保存到文件
    print("\n保存音频到文件...")
    await processor.save_to_file("test_output.wav", duration=1.0)
    print("✓ 完成")


if __name__ == "__main__":
    asyncio.run(main())
