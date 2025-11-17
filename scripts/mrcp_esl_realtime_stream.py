#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
FreeSWITCH ESL (Event Socket Library) 实时语音流处理示例
使用 ESL 连接 FreeSWITCH 并获取 MRCP 实时语音流
"""

import socket
import struct
import threading
import time
import sys

class FreeSwitchESL:
    """FreeSWITCH ESL 客户端类"""
    
    def __init__(self, host='127.0.0.1', port=8021, password='ClueCon'):
        self.host = host
        self.port = port
        self.password = password
        self.socket = None
        self.connected = False
        self.event_thread = None
        self.running = False
        
    def connect(self):
        """连接到 FreeSWITCH"""
        try:
            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            self.socket.connect((self.host, self.port))
            
            # 读取欢迎消息
            welcome = self._read_response()
            print(f"连接成功: {welcome}")
            
            # 认证
            self.send_command(f'auth {self.password}')
            auth_response = self._read_response()
            
            if 'OK' in auth_response:
                self.connected = True
                print("认证成功")
                return True
            else:
                print(f"认证失败: {auth_response}")
                return False
                
        except Exception as e:
            print(f"连接错误: {e}")
            return False
    
    def send_command(self, command):
        """发送命令到 FreeSWITCH"""
        if not self.connected:
            return None
            
        try:
            self.socket.send(f'{command}\n\n'.encode('utf-8'))
        except Exception as e:
            print(f"发送命令错误: {e}")
    
    def _read_response(self):
        """读取响应"""
        try:
            response = b''
            while True:
                chunk = self.socket.recv(4096)
                if not chunk:
                    break
                response += chunk
                if b'\n\n' in response:
                    break
            return response.decode('utf-8', errors='ignore')
        except Exception as e:
            print(f"读取响应错误: {e}")
            return ""
    
    def originate_call(self, extension, context='default'):
        """发起呼叫"""
        command = f'bgapi originate {{origination_caller_id_number=1000,origination_caller_id_name=ESL}}user/{extension} {context} {extension} 1'
        self.send_command(command)
        response = self._read_response()
        print(f"发起呼叫: {response}")
        return response
    
    def start_event_listener(self):
        """启动事件监听线程"""
        if self.event_thread and self.event_thread.is_alive():
            return
            
        self.running = True
        self.event_thread = threading.Thread(target=self._event_loop)
        self.event_thread.daemon = True
        self.event_thread.start()
    
    def _event_loop(self):
        """事件循环"""
        # 订阅事件
        self.send_command('event plain ALL')
        response = self._read_response()
        print(f"订阅事件: {response}")
        
        while self.running:
            try:
                event_data = self._read_response()
                if event_data:
                    self._handle_event(event_data)
            except Exception as e:
                if self.running:
                    print(f"事件循环错误: {e}")
                break
    
    def _handle_event(self, event_data):
        """处理事件"""
        # 解析事件数据
        if 'CHANNEL_DATA' in event_data:
            print("收到通道数据事件")
        elif 'CUSTOM' in event_data:
            print("收到自定义事件")
        # 可以添加更多事件处理逻辑
    
    def get_realtime_stream(self, uuid, output_file=None):
        """获取实时语音流"""
        if not output_file:
            output_file = f'/tmp/esl_stream_{uuid}.raw'
        
        # 使用 uuid_record 命令录制音频流
        command = f'api uuid_record {uuid} start {output_file}'
        self.send_command(command)
        response = self._read_response()
        print(f"开始录制: {response}")
        
        return output_file
    
    def stop_record(self, uuid):
        """停止录制"""
        command = f'api uuid_record {uuid} stop'
        self.send_command(command)
        response = self._read_response()
        print(f"停止录制: {response}")
    
    def disconnect(self):
        """断开连接"""
        self.running = False
        if self.socket:
            self.socket.close()
        self.connected = False
        print("已断开连接")


def main():
    """主函数"""
    print("FreeSWITCH ESL 实时语音流示例")
    print("=" * 50)
    
    # 创建 ESL 客户端
    esl = FreeSwitchESL(host='127.0.0.1', port=8021, password='ClueCon')
    
    # 连接
    if not esl.connect():
        print("无法连接到 FreeSWITCH")
        return
    
    try:
        # 启动事件监听
        esl.start_event_listener()
        
        # 发起呼叫到 MRCP 扩展
        print("\n发起呼叫到 MRCP 实时流扩展 (9999)...")
        esl.originate_call('9999')
        
        # 等待一段时间以处理流
        print("等待 30 秒处理实时流...")
        time.sleep(30)
        
        # 这里可以添加更多处理逻辑
        # 例如：获取流数据、实时处理等
        
    except KeyboardInterrupt:
        print("\n用户中断")
    finally:
        esl.disconnect()


if __name__ == '__main__':
    main()
