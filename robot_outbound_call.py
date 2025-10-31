#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FreeSWITCH 机器人外呼系统
支持意图识别和转人工功能
"""

import sys
import time
import logging
from enum import Enum
from typing import Optional, Dict, Any
import socket

try:
    import ESL
except ImportError:
    print("请安装 python-esl 库: pip install python-esl")
    sys.exit(1)

try:
    from intent_recognizer import IntentRecognizer, IntentType
except ImportError:
    # 如果无法导入，使用本地定义的类
    from enum import Enum
    
    class IntentType(Enum):
        """意图类型枚举"""
        GREETING = "greeting"
        QUESTION = "question"
        TRANSFER_TO_HUMAN = "transfer_to_human"
        GOODBYE = "goodbye"
        UNKNOWN = "unknown"
    
    class IntentRecognizer:
        """简化的意图识别器"""
        TRANSFER_KEYWORDS = [
            "转人工", "人工服务", "人工客服", "转接人工",
            "我要人工", "找人工", "人工坐席", "人工台",
            "转接客服", "找客服", "人工", "客服"
        ]
        
        @staticmethod
        def recognize(text: str) -> IntentType:
            if not text:
                return IntentType.UNKNOWN
            text_lower = text.lower().strip()
            for keyword in IntentRecognizer.TRANSFER_KEYWORDS:
                if keyword in text_lower:
                    return IntentType.TRANSFER_TO_HUMAN
            return IntentType.UNKNOWN

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


class CallState(Enum):
    """呼叫状态枚举"""
    INIT = "init"
    RINGING = "ringing"
    ANSWERED = "answered"
    TALKING = "talking"
    TRANSFERRING = "transferring"
    TRANSFERRED = "transferred"
    HANGUP = "hangup"
    FAILED = "failed"




class RobotOutboundCall:
    """机器人外呼类"""
    
    def __init__(self, host: str = "127.0.0.1", port: int = 8021, password: str = "ClueCon"):
        """
        初始化机器人外呼系统
        
        Args:
            host: FreeSWITCH ESL 主机地址
            port: FreeSWITCH ESL 端口
            password: FreeSWITCH ESL 密码
        """
        self.host = host
        self.port = port
        self.password = password
        self.con = None
        self.current_call_state = CallState.INIT
        self.intent_recognizer = IntentRecognizer()
        self.human_agent_number = None  # 人工坐席号码
        
    def connect(self) -> bool:
        """
        连接到FreeSWITCH
        
        Returns:
            bool: 连接是否成功
        """
        try:
            self.con = ESL.ESLconnection(self.host, str(self.port), self.password)
            if self.con.connected():
                logger.info(f"成功连接到FreeSWITCH: {self.host}:{self.port}")
                return True
            else:
                logger.error("无法连接到FreeSWITCH")
                return False
        except Exception as e:
            logger.error(f"连接FreeSWITCH时出错: {e}")
            return False
    
    def make_call(self, caller_id: str, callee_number: str, 
                  human_agent_number: Optional[str] = None) -> bool:
        """
        发起外呼
        
        Args:
            caller_id: 主叫号码
            callee_number: 被叫号码
            human_agent_number: 人工坐席号码（用于转接）
            
        Returns:
            bool: 呼叫是否成功发起
        """
        self.human_agent_number = human_agent_number
        
        # 构建originate命令
        # 使用Lua脚本处理呼叫流程和语音识别
        originate_string = f"originate {{origination_caller_id_number={caller_id},origination_caller_id_name=机器人客服}}sofia/external/{callee_number}@your_gateway &lua(/path/to/robot_call_handler.lua {caller_id})"
        
        # 简化版本：使用bridge到Lua脚本
        # 实际使用时需要根据你的FreeSWITCH配置调整
        try:
            logger.info(f"发起外呼: {caller_id} -> {callee_number}")
            cmd = f"originate {{origination_caller_id_number={caller_id}}}user/{callee_number} &lua(robot_call_handler.lua)"
            
            # 发送originate命令
            response = self.con.api(cmd)
            result = response.getBody()
            
            logger.info(f"呼叫结果: {result}")
            
            if "+OK" in result or "UUID" in result:
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
    
    def transfer_to_human(self, uuid: str, human_number: str) -> bool:
        """
        转接到人工坐席
        
        Args:
            uuid: 当前呼叫的UUID
            human_number: 人工坐席号码
            
        Returns:
            bool: 转接是否成功
        """
        try:
            logger.info(f"转接到人工坐席: {human_number}")
            self.current_call_state = CallState.TRANSFERRING
            
            # 方法1: 使用bridge命令转接
            transfer_cmd = f"uuid_transfer {uuid} {human_number} inline"
            response = self.con.api(transfer_cmd)
            result = response.getBody()
            
            # 方法2: 使用Lua脚本中的transfer应用
            # transfer_cmd = f"uuid_bridge {uuid} {human_number}"
            
            logger.info(f"转接命令结果: {result}")
            
            if "+OK" in result or "SUCCESS" in result:
                self.current_call_state = CallState.TRANSFERRED
                logger.info(f"成功转接到人工坐席: {human_number}")
                return True
            else:
                logger.error(f"转接失败: {result}")
                return False
                
        except Exception as e:
            logger.error(f"转接时出错: {e}")
            return False
    
    def process_call_with_intent_recognition(self, uuid: str, audio_data: str = None) -> Optional[IntentType]:
        """
        处理呼叫过程中的意图识别
        
        Args:
            uuid: 呼叫UUID
            audio_data: 音频数据（文本或音频文件路径）
            
        Returns:
            Optional[IntentType]: 识别的意图，如果没有识别到则返回None
        """
        # 这里应该集成语音识别（ASR）服务
        # 示例：使用文本输入进行意图识别
        # 实际应用中需要将音频转换为文本
        
        # 模拟：如果提供了文本，直接识别
        if audio_data and isinstance(audio_data, str) and not audio_data.startswith('/'):
            intent = self.intent_recognizer.recognize(audio_data)
            
            if intent == IntentType.TRANSFER_TO_HUMAN:
                # 执行转人工
                if self.human_agent_number:
                    self.transfer_to_human(uuid, self.human_agent_number)
                else:
                    logger.warning("未配置人工坐席号码，无法转接")
            
            return intent
        
        return None
    
    def disconnect(self):
        """断开FreeSWITCH连接"""
        if self.con and self.con.connected():
            self.con.disconnect()
            logger.info("已断开FreeSWITCH连接")


def main():
    """主函数 - 示例用法"""
    # 创建机器人外呼实例
    robot = RobotOutboundCall(
        host="127.0.0.1",
        port=8021,
        password="ClueCon"  # 修改为你的FreeSWITCH密码
    )
    
    # 连接到FreeSWITCH
    if not robot.connect():
        logger.error("无法连接到FreeSWITCH，退出")
        return
    
    try:
        # 发起外呼
        caller_id = "1000"  # 主叫号码
        callee_number = "1001"  # 被叫号码
        human_agent_number = "1002"  # 人工坐席号码
        
        if robot.make_call(caller_id, callee_number, human_agent_number):
            logger.info("呼叫已发起，等待接听...")
            
            # 这里应该监听呼叫事件，处理语音识别和意图识别
            # 实际应用中需要集成事件监听
            
        else:
            logger.error("呼叫发起失败")
            
    finally:
        robot.disconnect()


if __name__ == "__main__":
    main()
