#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FreeSWITCH MRCP 实时语音流客户端
功能：连接 FreeSWITCH，获取实时语音流并通过 MRCP 处理
"""

import socket
import struct
import threading
import time
import logging
from typing import Optional, Callable

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class MRCPClient:
    """MRCP 客户端类"""
    
    def __init__(self, server_ip: str = "127.0.0.1", server_port: int = 1544):
        """
        初始化 MRCP 客户端
        
        Args:
            server_ip: MRCP 服务器 IP 地址
            server_port: MRCP 服务器端口
        """
        self.server_ip = server_ip
        self.server_port = server_port
        self.socket: Optional[socket.socket] = None
        self.connected = False
        self.stream_active = False
        self.audio_callback: Optional[Callable] = None
        
    def connect(self) -> bool:
        """连接到 MRCP 服务器"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((self.server_ip, self.server_port))
            self.connected = True
            logger.info(f"已连接到 MRCP 服务器 {self.server_ip}:{self.server_port}")
            return True
        except Exception as e:
            logger.error(f"连接 MRCP 服务器失败: {e}")
            return False
    
    def disconnect(self):
        """断开 MRCP 服务器连接"""
        if self.socket:
            self.socket.close()
            self.connected = False
            logger.info("已断开 MRCP 服务器连接")
    
    def set_audio_callback(self, callback: Callable):
        """设置音频数据回调函数"""
        self.audio_callback = callback
    
    def send_audio_data(self, audio_data: bytes):
        """发送音频数据到 MRCP 服务器"""
        if not self.connected or not self.socket:
            logger.warning("MRCP 未连接，无法发送音频数据")
            return False
        
        try:
            # 发送音频数据长度和数据
            length = struct.pack('>I', len(audio_data))
            self.socket.sendall(length + audio_data)
            return True
        except Exception as e:
            logger.error(f"发送音频数据失败: {e}")
            return False
    
    def receive_audio_data(self, buffer_size: int = 4096) -> Optional[bytes]:
        """从 MRCP 服务器接收音频数据"""
        if not self.connected or not self.socket:
            return None
        
        try:
            # 接收数据长度
            length_data = self.socket.recv(4)
            if len(length_data) < 4:
                return None
            
            length = struct.unpack('>I', length_data)[0]
            
            # 接收实际数据
            audio_data = b''
            while len(audio_data) < length:
                chunk = self.socket.recv(min(length - len(audio_data), buffer_size))
                if not chunk:
                    return None
                audio_data += chunk
            
            return audio_data
        except Exception as e:
            logger.error(f"接收音频数据失败: {e}")
            return None


class FreeSWITCHRTPClient:
    """FreeSWITCH RTP 客户端 - 用于获取实时语音流"""
    
    def __init__(self, rtp_ip: str = "127.0.0.1", rtp_port: int = 16384):
        """
        初始化 RTP 客户端
        
        Args:
            rtp_ip: RTP 服务器 IP 地址
            rtp_port: RTP 服务器端口
        """
        self.rtp_ip = rtp_ip
        self.rtp_port = rtp_port
        self.socket: Optional[socket.socket] = None
        self.stream_active = False
        self.audio_callback: Optional[Callable] = None
        
    def start_stream(self) -> bool:
        """启动 RTP 流"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            self.socket.bind((self.rtp_ip, self.rtp_port))
            self.stream_active = True
            logger.info(f"RTP 流已启动，监听 {self.rtp_ip}:{self.rtp_port}")
            return True
        except Exception as e:
            logger.error(f"启动 RTP 流失败: {e}")
            return False
    
    def stop_stream(self):
        """停止 RTP 流"""
        self.stream_active = False
        if self.socket:
            self.socket.close()
            logger.info("RTP 流已停止")
    
    def set_audio_callback(self, callback: Callable):
        """设置音频数据回调函数"""
        self.audio_callback = callback
    
    def receive_rtp_packets(self):
        """接收 RTP 数据包"""
        if not self.socket:
            return
        
        while self.stream_active:
            try:
                # 接收 RTP 数据包（最大 1500 字节）
                data, addr = self.socket.recvfrom(1500)
                
                if data and len(data) > 12:  # RTP 头部至少 12 字节
                    # 解析 RTP 头部（简化版）
                    # 实际应用中需要完整解析 RTP 协议
                    audio_payload = data[12:]  # 跳过 RTP 头部
                    
                    if self.audio_callback:
                        self.audio_callback(audio_payload)
                        
            except socket.timeout:
                continue
            except Exception as e:
                if self.stream_active:
                    logger.error(f"接收 RTP 数据包失败: {e}")
                break


class RealtimeVoiceStreamProcessor:
    """实时语音流处理器 - 整合 FreeSWITCH RTP 和 MRCP"""
    
    def __init__(self, rtp_ip: str = "127.0.0.1", rtp_port: int = 16384,
                 mrcp_ip: str = "127.0.0.1", mrcp_port: int = 1544):
        """
        初始化实时语音流处理器
        
        Args:
            rtp_ip: RTP 服务器 IP
            rtp_port: RTP 服务器端口
            mrcp_ip: MRCP 服务器 IP
            mrcp_port: MRCP 服务器端口
        """
        self.rtp_client = FreeSWITCHRTPClient(rtp_ip, rtp_port)
        self.mrcp_client = MRCPClient(mrcp_ip, mrcp_port)
        self.audio_buffer = []
        self.buffer_size = 3200  # 400ms @ 8kHz
        self.chunk_size = 160  # 20ms @ 8kHz
        
    def start(self) -> bool:
        """启动实时语音流处理"""
        # 连接 MRCP
        if not self.mrcp_client.connect():
            return False
        
        # 启动 RTP 流
        if not self.rtp_client.start_stream():
            self.mrcp_client.disconnect()
            return False
        
        # 设置音频回调
        self.rtp_client.set_audio_callback(self._on_audio_data)
        
        # 启动 RTP 接收线程
        self.rtp_thread = threading.Thread(target=self.rtp_client.receive_rtp_packets)
        self.rtp_thread.daemon = True
        self.rtp_thread.start()
        
        logger.info("实时语音流处理已启动")
        return True
    
    def stop(self):
        """停止实时语音流处理"""
        self.rtp_client.stop_stream()
        self.mrcp_client.disconnect()
        logger.info("实时语音流处理已停止")
    
    def _on_audio_data(self, audio_data: bytes):
        """音频数据回调函数"""
        # 将音频数据添加到缓冲区
        self.audio_buffer.append(audio_data)
        
        # 当缓冲区达到一定大小时，发送到 MRCP
        total_size = sum(len(chunk) for chunk in self.audio_buffer)
        if total_size >= self.buffer_size:
            # 合并缓冲区数据
            combined_audio = b''.join(self.audio_buffer)
            self.audio_buffer = []
            
            # 发送到 MRCP
            self.mrcp_client.send_audio_data(combined_audio)
            logger.debug(f"发送音频数据到 MRCP，大小: {len(combined_audio)} 字节")


def main():
    """主函数 - 示例用法"""
    processor = RealtimeVoiceStreamProcessor(
        rtp_ip="127.0.0.1",
        rtp_port=16384,
        mrcp_ip="127.0.0.1",
        mrcp_port=1544
    )
    
    try:
        if processor.start():
            logger.info("实时语音流处理运行中，按 Ctrl+C 停止...")
            # 保持运行
            while True:
                time.sleep(1)
        else:
            logger.error("启动实时语音流处理失败")
    except KeyboardInterrupt:
        logger.info("收到停止信号...")
    finally:
        processor.stop()


if __name__ == "__main__":
    main()
