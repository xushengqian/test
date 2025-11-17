#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FreeSWITCH ESL (Event Socket Library) 客户端
用于通过 ESL 连接 FreeSWITCH 并控制实时语音流
"""

import socket
import threading
import time
import logging
import re
from typing import Optional, Callable, Dict

logger = logging.getLogger(__name__)


class FreeSWITCHESLClient:
    """FreeSWITCH ESL 客户端"""
    
    def __init__(self, host: str = "127.0.0.1", port: int = 8021, password: str = "ClueCon"):
        """
        初始化 ESL 客户端
        
        Args:
            host: FreeSWITCH 主机地址
            port: ESL 端口（默认 8021）
            password: ESL 密码（默认 ClueCon）
        """
        self.host = host
        self.port = port
        self.password = password
        self.socket: Optional[socket.socket] = None
        self.connected = False
        self.event_callbacks: Dict[str, Callable] = {}
        self.event_thread: Optional[threading.Thread] = None
        
    def connect(self) -> bool:
        """连接到 FreeSWITCH"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((self.host, self.port))
            
            # 读取欢迎消息
            welcome = self._read_response()
            logger.info(f"ESL 连接成功: {welcome}")
            
            # 认证
            self.send_command(f"auth {self.password}")
            auth_response = self._read_response()
            
            if "OK" in auth_response:
                self.connected = True
                logger.info("ESL 认证成功")
                
                # 启动事件监听线程
                self.event_thread = threading.Thread(target=self._event_listener)
                self.event_thread.daemon = True
                self.event_thread.start()
                
                return True
            else:
                logger.error(f"ESL 认证失败: {auth_response}")
                return False
                
        except Exception as e:
            logger.error(f"连接 FreeSWITCH 失败: {e}")
            return False
    
    def disconnect(self):
        """断开连接"""
        self.connected = False
        if self.socket:
            self.socket.close()
            logger.info("ESL 连接已断开")
    
    def send_command(self, command: str) -> bool:
        """发送命令到 FreeSWITCH"""
        if not self.connected or not self.socket:
            logger.warning("ESL 未连接，无法发送命令")
            return False
        
        try:
            self.socket.sendall(f"{command}\n\n".encode('utf-8'))
            return True
        except Exception as e:
            logger.error(f"发送命令失败: {e}")
            return False
    
    def _read_response(self) -> str:
        """读取响应"""
        if not self.socket:
            return ""
        
        response = ""
        while True:
            data = self.socket.recv(4096).decode('utf-8', errors='ignore')
            if not data:
                break
            response += data
            if response.endswith('\n\n'):
                break
        
        return response.strip()
    
    def _event_listener(self):
        """事件监听线程"""
        while self.connected:
            try:
                event_data = self._read_response()
                if event_data:
                    self._parse_event(event_data)
            except Exception as e:
                if self.connected:
                    logger.error(f"事件监听错误: {e}")
                break
    
    def _parse_event(self, event_data: str):
        """解析事件"""
        lines = event_data.split('\n')
        event_name = None
        event_body = {}
        
        for line in lines:
            if line.startswith('Event-Name:'):
                event_name = line.split(':', 1)[1].strip()
            elif ':' in line:
                key, value = line.split(':', 1)
                event_body[key.strip()] = value.strip()
        
        if event_name and event_name in self.event_callbacks:
            self.event_callbacks[event_name](event_body)
    
    def register_event_callback(self, event_name: str, callback: Callable):
        """注册事件回调"""
        self.event_callbacks[event_name] = callback
    
    def originate_call(self, destination: str, dialplan: str = "mrcp_stream",
                      context: str = "mrcp_stream") -> Optional[str]:
        """
        发起呼叫
        
        Args:
            destination: 目标号码或 SIP URI
            dialplan: Dialplan 扩展
            context: Dialplan 上下文
            
        Returns:
            会话 UUID
        """
        # 订阅事件以获取会话 UUID
        uuid = None
        
        def channel_create_callback(event):
            nonlocal uuid
            if 'Channel-Call-UUID' in event:
                uuid = event['Channel-Call-UUID']
        
        self.register_event_callback('CHANNEL_CREATE', channel_create_callback)
        
        # 发起呼叫
        command = f"bgapi originate {{context={context},dialplan={dialplan}}}user/{destination} &park"
        self.send_command(command)
        
        # 等待会话创建
        time.sleep(1)
        
        return uuid
    
    def start_mrcp_stream(self, uuid: str) -> bool:
        """启动 MRCP 实时语音流"""
        command = f"uuid_execute {uuid} lua mrcp_realtime_stream.lua"
        return self.send_command(command)
    
    def get_rtp_info(self, uuid: str) -> Optional[Dict]:
        """获取 RTP 信息"""
        command = f"uuid_dump {uuid}"
        self.send_command(command)
        response = self._read_response()
        
        # 解析响应获取 RTP 信息
        rtp_info = {}
        for line in response.split('\n'):
            if 'rtp' in line.lower():
                parts = line.split('=', 1)
                if len(parts) == 2:
                    rtp_info[parts[0].strip()] = parts[1].strip()
        
        return rtp_info if rtp_info else None


class MRCPStreamController:
    """MRCP 流控制器 - 整合 ESL 和 MRCP 客户端"""
    
    def __init__(self, esl_host: str = "127.0.0.1", esl_port: int = 8021,
                 mrcp_ip: str = "127.0.0.1", mrcp_port: int = 1544):
        """
        初始化流控制器
        
        Args:
            esl_host: FreeSWITCH ESL 主机
            esl_port: FreeSWITCH ESL 端口
            mrcp_ip: MRCP 服务器 IP
            mrcp_port: MRCP 服务器端口
        """
        from mrcp_client import MRCPClient
        
        self.esl_client = FreeSWITCHESLClient(esl_host, esl_port)
        self.mrcp_client = MRCPClient(mrcp_ip, mrcp_port)
        self.current_uuid: Optional[str] = None
        
    def start_stream(self, destination: str) -> bool:
        """启动实时语音流"""
        # 连接 ESL
        if not self.esl_client.connect():
            return False
        
        # 连接 MRCP
        if not self.mrcp_client.connect():
            self.esl_client.disconnect()
            return False
        
        # 发起呼叫
        uuid = self.esl_client.originate_call(destination)
        if not uuid:
            logger.error("无法创建会话")
            return False
        
        self.current_uuid = uuid
        
        # 启动 MRCP 流处理
        self.esl_client.start_mrcp_stream(uuid)
        
        logger.info(f"实时语音流已启动，会话 UUID: {uuid}")
        return True
    
    def stop_stream(self):
        """停止实时语音流"""
        if self.current_uuid:
            self.esl_client.send_command(f"uuid_kill {self.current_uuid}")
            self.current_uuid = None
        
        self.mrcp_client.disconnect()
        self.esl_client.disconnect()
        logger.info("实时语音流已停止")
    
    def get_stream_status(self) -> Dict:
        """获取流状态"""
        status = {
            "esl_connected": self.esl_client.connected,
            "mrcp_connected": self.mrcp_client.connected,
            "session_uuid": self.current_uuid
        }
        
        if self.current_uuid:
            rtp_info = self.esl_client.get_rtp_info(self.current_uuid)
            if rtp_info:
                status["rtp_info"] = rtp_info
        
        return status


def main():
    """主函数 - 示例用法"""
    controller = MRCPStreamController(
        esl_host="127.0.0.1",
        esl_port=8021,
        mrcp_ip="127.0.0.1",
        mrcp_port=1544
    )
    
    try:
        # 启动流（替换为实际的目标号码）
        if controller.start_stream("1000"):
            logger.info("实时语音流运行中，按 Ctrl+C 停止...")
            
            # 定期检查状态
            while True:
                status = controller.get_stream_status()
                logger.info(f"流状态: {status}")
                time.sleep(5)
        else:
            logger.error("启动实时语音流失败")
    except KeyboardInterrupt:
        logger.info("收到停止信号...")
    finally:
        controller.stop_stream()


if __name__ == "__main__":
    main()
