#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FreeSWITCH ESL控制器 - 管理机器人外呼和坐席接入
"""

import ESL
import json
import threading
import time
import logging
from datetime import datetime
import asyncio
import websockets
from typing import Dict, List, Optional

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class CallManager:
    """通话管理器"""
    
    def __init__(self):
        self.active_calls: Dict[str, dict] = {}
        self.waiting_agents: List[dict] = []
        self.lock = threading.Lock()
    
    def add_call(self, call_uuid: str, customer_number: str):
        """添加新的外呼通话"""
        with self.lock:
            self.active_calls[call_uuid] = {
                'uuid': call_uuid,
                'customer_number': customer_number,
                'start_time': datetime.now(),
                'status': 'robot_talking',
                'agent_id': None,
                'conference_name': f'conf_{call_uuid}'
            }
            logger.info(f"新增通话: {call_uuid} -> {customer_number}")
    
    def update_call_status(self, call_uuid: str, status: str, agent_id: Optional[str] = None):
        """更新通话状态"""
        with self.lock:
            if call_uuid in self.active_calls:
                self.active_calls[call_uuid]['status'] = status
                if agent_id:
                    self.active_calls[call_uuid]['agent_id'] = agent_id
                logger.info(f"更新通话状态: {call_uuid} -> {status}")
    
    def remove_call(self, call_uuid: str):
        """移除通话"""
        with self.lock:
            if call_uuid in self.active_calls:
                del self.active_calls[call_uuid]
                logger.info(f"移除通话: {call_uuid}")
    
    def get_call_info(self, call_uuid: str) -> Optional[dict]:
        """获取通话信息"""
        with self.lock:
            return self.active_calls.get(call_uuid)
    
    def get_all_calls(self) -> List[dict]:
        """获取所有通话"""
        with self.lock:
            return list(self.active_calls.values())


class FreeSwitchESLController:
    """FreeSWITCH ESL控制器"""
    
    def __init__(self, host='localhost', port=8021, password='ClueCon'):
        self.host = host
        self.port = port
        self.password = password
        self.connection = None
        self.call_manager = CallManager()
        self.running = False
        
    def connect(self):
        """连接到FreeSWITCH"""
        try:
            self.connection = ESL.ESLconnection(self.host, str(self.port), self.password)
            if self.connection.connected():
                logger.info("成功连接到FreeSWITCH ESL")
                
                # 订阅事件
                events = [
                    'CHANNEL_CREATE',
                    'CHANNEL_ANSWER', 
                    'CHANNEL_HANGUP',
                    'CUSTOM robot::*',
                    'CHANNEL_BRIDGE',
                    'CHANNEL_UNBRIDGE'
                ]
                
                for event in events:
                    self.connection.events('plain', event)
                
                return True
            else:
                logger.error("无法连接到FreeSWITCH ESL")
                return False
        except Exception as e:
            logger.error(f"连接错误: {e}")
            return False
    
    def initiate_outbound_call(self, customer_number: str) -> Optional[str]:
        """发起外呼"""
        try:
            # 生成通话UUID
            call_uuid = self.connection.api("create_uuid").getBody().strip()
            
            # 构建呼叫命令
            originate_cmd = (
                f"originate {{origination_uuid={call_uuid},"
                f"origination_caller_id_number=4008888888,"
                f"origination_caller_id_name=ServiceBot}}"
                f"sofia/external/{customer_number}@your_sip_provider "
                f"&lua('robot_handler.lua {call_uuid}')"
            )
            
            # 执行呼叫
            result = self.connection.bgapi(originate_cmd)
            
            if result:
                # 添加到通话管理器
                self.call_manager.add_call(call_uuid, customer_number)
                logger.info(f"发起外呼: {customer_number} (UUID: {call_uuid})")
                return call_uuid
            
        except Exception as e:
            logger.error(f"发起外呼失败: {e}")
        
        return None
    
    def agent_join_call(self, call_uuid: str, agent_id: str, agent_number: str):
        """坐席接入通话"""
        try:
            call_info = self.call_manager.get_call_info(call_uuid)
            if not call_info:
                logger.error(f"通话不存在: {call_uuid}")
                return False
            
            conference_name = call_info['conference_name']
            
            # 1. 先设置变量通知机器人脚本
            self.connection.api(f"uuid_setvar {call_uuid} agent_join_request true")
            
            # 2. 等待客户进入会议室
            time.sleep(1)
            
            # 3. 呼叫坐席并加入会议
            agent_originate = (
                f"originate {{origination_uuid={agent_id},"
                f"conference_auto_outcall_flags=moderator}}"
                f"sofia/internal/{agent_number} "
                f"&conference('{conference_name}@robot_conference')"
            )
            
            result = self.connection.bgapi(agent_originate)
            
            if result:
                # 更新通话状态
                self.call_manager.update_call_status(call_uuid, 'agent_talking', agent_id)
                logger.info(f"坐席 {agent_id} 成功接入通话 {call_uuid}")
                return True
                
        except Exception as e:
            logger.error(f"坐席接入失败: {e}")
        
        return False
    
    def monitor_agent_intervention(self, call_uuid: str):
        """监控坐席介入请求"""
        call_info = self.call_manager.get_call_info(call_uuid)
        if call_info and call_info['status'] == 'robot_talking':
            # 这里可以实现坐席主动介入的逻辑
            # 例如：检查坐席请求队列，或者通过WebSocket接收坐席请求
            pass
    
    def process_events(self):
        """处理FreeSWITCH事件"""
        self.running = True
        
        while self.running:
            event = self.connection.recvEvent()
            
            if event:
                event_name = event.getHeader("Event-Name")
                
                if event_name == "CHANNEL_CREATE":
                    self.handle_channel_create(event)
                elif event_name == "CHANNEL_ANSWER":
                    self.handle_channel_answer(event)
                elif event_name == "CHANNEL_HANGUP":
                    self.handle_channel_hangup(event)
                elif event_name == "CUSTOM":
                    subclass = event.getHeader("Event-Subclass")
                    if subclass == "robot::agent_needed":
                        self.handle_agent_needed(event)
    
    def handle_channel_create(self, event):
        """处理通道创建事件"""
        call_uuid = event.getHeader("Unique-ID")
        caller_number = event.getHeader("Caller-Caller-ID-Number")
        logger.info(f"通道创建: {call_uuid} from {caller_number}")
    
    def handle_channel_answer(self, event):
        """处理应答事件"""
        call_uuid = event.getHeader("Unique-ID")
        logger.info(f"通话应答: {call_uuid}")
    
    def handle_channel_hangup(self, event):
        """处理挂断事件"""
        call_uuid = event.getHeader("Unique-ID")
        hangup_cause = event.getHeader("Hangup-Cause")
        logger.info(f"通话挂断: {call_uuid} - {hangup_cause}")
        self.call_manager.remove_call(call_uuid)
    
    def handle_agent_needed(self, event):
        """处理需要坐席介入的事件"""
        call_uuid = event.getHeader("Call-UUID")
        customer_number = event.getHeader("Customer-Number")
        conference_name = event.getHeader("Conference-Name")
        
        logger.info(f"客户 {customer_number} 请求人工服务 (通话: {call_uuid})")
        
        # 通知可用坐席
        self.notify_available_agents(call_uuid, customer_number)
    
    def notify_available_agents(self, call_uuid: str, customer_number: str):
        """通知可用坐席"""
        # 这里可以实现通知逻辑，例如：
        # - 发送WebSocket消息到坐席端
        # - 调用坐席分配API
        # - 发送事件到消息队列
        pass
    
    def stop(self):
        """停止控制器"""
        self.running = False
        if self.connection:
            self.connection.disconnect()
        logger.info("ESL控制器已停止")


class WebSocketServer:
    """WebSocket服务器 - 用于坐席实时监控和控制"""
    
    def __init__(self, esl_controller: FreeSwitchESLController, host='0.0.0.0', port=8765):
        self.esl_controller = esl_controller
        self.host = host
        self.port = port
        self.clients = set()
    
    async def register(self, websocket):
        """注册新的WebSocket客户端"""
        self.clients.add(websocket)
        await self.send_call_list(websocket)
    
    async def unregister(self, websocket):
        """注销WebSocket客户端"""
        self.clients.remove(websocket)
    
    async def send_call_list(self, websocket):
        """发送当前通话列表"""
        calls = self.esl_controller.call_manager.get_all_calls()
        message = {
            'type': 'call_list',
            'data': calls
        }
        await websocket.send(json.dumps(message, default=str))
    
    async def broadcast_update(self, message):
        """广播更新到所有客户端"""
        if self.clients:
            await asyncio.gather(
                *[client.send(json.dumps(message, default=str)) for client in self.clients]
            )
    
    async def handle_message(self, websocket, message):
        """处理客户端消息"""
        try:
            data = json.loads(message)
            msg_type = data.get('type')
            
            if msg_type == 'initiate_call':
                # 发起外呼
                customer_number = data.get('customer_number')
                call_uuid = self.esl_controller.initiate_outbound_call(customer_number)
                
                response = {
                    'type': 'call_initiated',
                    'success': call_uuid is not None,
                    'call_uuid': call_uuid
                }
                await websocket.send(json.dumps(response))
                
            elif msg_type == 'agent_join':
                # 坐席接入
                call_uuid = data.get('call_uuid')
                agent_id = data.get('agent_id')
                agent_number = data.get('agent_number')
                
                success = self.esl_controller.agent_join_call(call_uuid, agent_id, agent_number)
                
                response = {
                    'type': 'agent_joined',
                    'success': success,
                    'call_uuid': call_uuid
                }
                await websocket.send(json.dumps(response))
                
            elif msg_type == 'get_calls':
                # 获取通话列表
                await self.send_call_list(websocket)
                
        except json.JSONDecodeError:
            logger.error(f"无效的JSON消息: {message}")
        except Exception as e:
            logger.error(f"处理消息错误: {e}")
    
    async def handler(self, websocket, path):
        """WebSocket连接处理器"""
        await self.register(websocket)
        try:
            async for message in websocket:
                await self.handle_message(websocket, message)
        finally:
            await self.unregister(websocket)
    
    def start(self):
        """启动WebSocket服务器"""
        start_server = websockets.serve(self.handler, self.host, self.port)
        asyncio.get_event_loop().run_until_complete(start_server)
        logger.info(f"WebSocket服务器启动在 ws://{self.host}:{self.port}")
        asyncio.get_event_loop().run_forever()


def main():
    """主函数"""
    # 创建ESL控制器
    esl_controller = FreeSwitchESLController()
    
    # 连接到FreeSWITCH
    if not esl_controller.connect():
        logger.error("无法启动，FreeSWITCH连接失败")
        return
    
    # 启动事件处理线程
    event_thread = threading.Thread(target=esl_controller.process_events)
    event_thread.daemon = True
    event_thread.start()
    
    # 启动WebSocket服务器
    ws_server = WebSocketServer(esl_controller)
    
    try:
        ws_server.start()
    except KeyboardInterrupt:
        logger.info("收到停止信号")
        esl_controller.stop()


if __name__ == "__main__":
    main()