#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FreeSWITCH 机器人外呼系统 - 事件监听版本
支持实时事件监听、意图识别和转人工功能
"""

import sys
import time
import threading
import logging
from typing import Optional, Dict, Any
from robot_outbound_call import RobotOutboundCall, IntentType, CallState
from intent_recognizer import IntentRecognizer

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('robot_call.log', encoding='utf-8'),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)


class EventListener(threading.Thread):
    """FreeSWITCH事件监听器"""
    
    def __init__(self, con, robot_instance):
        super().__init__(daemon=True)
        self.con = con
        self.robot = robot_instance
        self.running = True
        self.active_calls = {}  # {uuid: call_info}
        
    def run(self):
        """监听事件"""
        logger.info("开始监听FreeSWITCH事件")
        
        # 订阅事件
        self.con.events("plain", "CHANNEL_CREATE CHANNEL_ANSWER CHANNEL_BRIDGE CHANNEL_HANGUP CUSTOM")
        
        while self.running and self.con.connected():
            e = self.con.recvEvent()
            if e:
                self.handle_event(e)
    
    def handle_event(self, event):
        """处理事件"""
        event_name = event.getHeader("Event-Name")
        uuid = event.getHeader("Unique-ID")
        
        logger.debug(f"收到事件: {event_name}, UUID: {uuid}")
        
        if event_name == "CHANNEL_CREATE":
            logger.info(f"呼叫创建: {uuid}")
            self.active_calls[uuid] = {
                "state": CallState.RINGING,
                "caller_id": event.getHeader("Caller-Caller-ID-Number"),
                "callee_id": event.getHeader("Caller-Destination-Number"),
            }
        
        elif event_name == "CHANNEL_ANSWER":
            logger.info(f"呼叫接听: {uuid}")
            if uuid in self.active_calls:
                self.active_calls[uuid]["state"] = CallState.ANSWERED
        
        elif event_name == "CHANNEL_BRIDGE":
            logger.info(f"呼叫桥接: {uuid}")
            if uuid in self.active_calls:
                self.active_calls[uuid]["state"] = CallState.TRANSFERRED
        
        elif event_name == "CHANNEL_HANGUP":
            logger.info(f"呼叫挂断: {uuid}")
            if uuid in self.active_calls:
                self.active_calls[uuid]["state"] = CallState.HANGUP
                # 清理
                del self.active_calls[uuid]
        
        elif event_name == "CUSTOM":
            # 处理自定义事件（如意图识别结果）
            subclass = event.getHeader("Event-Subclass")
            if subclass == "intent_detected":
                intent = event.getHeader("Intent-Type")
                if intent == "TRANSFER_TO_HUMAN":
                    logger.info(f"检测到转人工意图，呼叫UUID: {uuid}")
                    if self.robot.human_agent_number:
                        self.robot.transfer_to_human(uuid, self.robot.human_agent_number)
    
    def stop(self):
        """停止监听"""
        self.running = False


class EnhancedRobotOutboundCall(RobotOutboundCall):
    """增强版机器人外呼类（支持事件监听）"""
    
    def __init__(self, host: str = "127.0.0.1", port: int = 8021, password: str = "ClueCon"):
        super().__init__(host, port, password)
        self.event_listener = None
        
    def start_event_listener(self):
        """启动事件监听"""
        if self.con and self.con.connected():
            self.event_listener = EventListener(self.con, self)
            self.event_listener.start()
            logger.info("事件监听器已启动")
        else:
            logger.error("FreeSWITCH未连接，无法启动事件监听")
    
    def stop_event_listener(self):
        """停止事件监听"""
        if self.event_listener:
            self.event_listener.stop()
            self.event_listener.join(timeout=2)
            logger.info("事件监听器已停止")
    
    def make_call_with_callback(self, caller_id: str, callee_number: str,
                                human_agent_number: Optional[str] = None,
                                callback_url: Optional[str] = None) -> bool:
        """
        发起外呼（带回调URL，用于接收语音识别结果）
        
        Args:
            caller_id: 主叫号码
            callee_number: 被叫号码
            human_agent_number: 人工坐席号码
            callback_url: 回调URL（接收语音识别结果）
        """
        self.human_agent_number = human_agent_number
        
        try:
            logger.info(f"发起外呼（带回调）: {caller_id} -> {callee_number}")
            
            # 构建originate命令，包含回调URL
            if callback_url:
                cmd = f"originate {{origination_caller_id_number={caller_id},origination_caller_id_name=机器人客服,callback_url={callback_url}}}user/{callee_number} &lua(robot_call_handler.lua)"
            else:
                cmd = f"originate {{origination_caller_id_number={caller_id},origination_caller_id_name=机器人客服}}user/{callee_number} &lua(robot_call_handler.lua)"
            
            response = self.con.api(cmd)
            result = response.getBody()
            
            logger.info(f"呼叫结果: {result}")
            
            if "+OK" in result or "UUID" in result:
                # 提取UUID
                if "UUID" in result:
                    uuid = result.split("UUID: ")[1].split()[0] if "UUID:" in result else None
                    if uuid:
                        logger.info(f"呼叫UUID: {uuid}")
                        return True
                
                self.current_call_state = CallState.RINGING
                return True
            else:
                logger.error(f"呼叫失败: {result}")
                self.current_call_state = CallState.FAILED
                return False
                
        except Exception as e:
            logger.error(f"发起呼叫时出错: {e}")
            self.current_call_state = CallState.FAILED
            return False
    
    def disconnect(self):
        """断开连接（包含停止事件监听）"""
        self.stop_event_listener()
        super().disconnect()


def main():
    """主函数 - 示例用法"""
    # 创建增强版机器人外呼实例
    robot = EnhancedRobotOutboundCall(
        host="127.0.0.1",
        port=8021,
        password="ClueCon"  # 修改为你的FreeSWITCH密码
    )
    
    # 连接到FreeSWITCH
    if not robot.connect():
        logger.error("无法连接到FreeSWITCH，退出")
        return
    
    try:
        # 启动事件监听
        robot.start_event_listener()
        
        # 发起外呼
        caller_id = "1000"
        callee_number = "1001"
        human_agent_number = "1002"
        
        if robot.make_call_with_callback(caller_id, callee_number, human_agent_number):
            logger.info("呼叫已发起，等待接听...")
            
            # 保持运行，监听事件
            try:
                while True:
                    time.sleep(1)
            except KeyboardInterrupt:
                logger.info("收到中断信号，停止...")
        else:
            logger.error("呼叫发起失败")
            
    finally:
        robot.disconnect()


if __name__ == "__main__":
    main()
