#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FreeSWITCH 机器人外呼处理脚本
实现智能识别转人工意图并执行转接
"""

import ESL
import time
import re
import json
import logging
from typing import Optional, Dict, List

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class IntentRecognizer:
    """意图识别器 - 识别用户是否想转人工"""
    
    # 转人工关键词列表
    TRANSFER_KEYWORDS = [
        "转人工", "人工", "客服", "转接", "人工客服",
        "找人工", "转", "接人工", "人工服务",
        "manual", "agent", "human", "operator"
    ]
    
    # 否定词列表（用户明确不需要转人工）
    NEGATIVE_KEYWORDS = [
        "不用", "不要", "不需要", "算了", "没事"
    ]
    
    def __init__(self):
        """初始化意图识别器"""
        self.transfer_count = 0  # 用户提出转人工的次数
        
    def recognize(self, user_input: str) -> Dict:
        """
        识别用户意图
        
        Args:
            user_input: 用户输入的文本
            
        Returns:
            dict: {
                'intent': 'transfer_to_human' | 'continue_robot' | 'unknown',
                'confidence': float,
                'keywords_matched': list
            }
        """
        if not user_input:
            return {
                'intent': 'unknown',
                'confidence': 0.0,
                'keywords_matched': []
            }
        
        user_input_lower = user_input.lower().strip()
        
        # 检查是否包含否定词
        for negative_word in self.NEGATIVE_KEYWORDS:
            if negative_word in user_input_lower:
                return {
                    'intent': 'continue_robot',
                    'confidence': 0.9,
                    'keywords_matched': [negative_word]
                }
        
        # 检查是否包含转人工关键词
        matched_keywords = []
        for keyword in self.TRANSFER_KEYWORDS:
            if keyword in user_input_lower:
                matched_keywords.append(keyword)
        
        if matched_keywords:
            self.transfer_count += 1
            confidence = min(0.9 + (self.transfer_count * 0.05), 1.0)
            return {
                'intent': 'transfer_to_human',
                'confidence': confidence,
                'keywords_matched': matched_keywords
            }
        
        return {
            'intent': 'unknown',
            'confidence': 0.0,
            'keywords_matched': []
        }


class RobotCallHandler:
    """机器人外呼处理器"""
    
    def __init__(self, config: Dict):
        """
        初始化机器人外呼处理器
        
        Args:
            config: 配置字典，包含 FreeSWITCH 连接信息和转接配置
        """
        self.config = config
        self.conn = None
        self.intent_recognizer = IntentRecognizer()
        self.call_uuid = None
        self.customer_number = None
        
    def connect_freeswitch(self) -> bool:
        """
        连接到 FreeSWITCH ESL
        
        Returns:
            bool: 连接是否成功
        """
        try:
            self.conn = ESL.ESLconnection(
                self.config['freeswitch_host'],
                self.config['freeswitch_port'],
                self.config['freeswitch_password']
            )
            
            if self.conn.connected():
                logger.info("成功连接到 FreeSWITCH ESL")
                return True
            else:
                logger.error("无法连接到 FreeSWITCH ESL")
                return False
                
        except Exception as e:
            logger.error(f"连接 FreeSWITCH 失败: {e}")
            return False
    
    def make_outbound_call(self, phone_number: str) -> Optional[str]:
        """
        发起外呼
        
        Args:
            phone_number: 目标手机号码
            
        Returns:
            str: 呼叫的 UUID，失败返回 None
        """
        try:
            self.customer_number = phone_number
            
            # 构建拨号字符串
            dial_string = (
                f"{{origination_caller_id_number={self.config['caller_id']},"
                f"ignore_early_media=true}}"
                f"sofia/gateway/{self.config['gateway']}/{phone_number}"
            )
            
            # 执行拨号
            command = f"originate {dial_string} &park()"
            logger.info(f"发起外呼: {command}")
            
            event = self.conn.api(command)
            response = event.getBody()
            
            # 提取 UUID
            if response and response.startswith('+OK'):
                uuid = response.split()[1].strip()
                self.call_uuid = uuid
                logger.info(f"外呼成功，UUID: {uuid}")
                return uuid
            else:
                logger.error(f"外呼失败: {response}")
                return None
                
        except Exception as e:
            logger.error(f"发起外呼异常: {e}")
            return None
    
    def play_welcome_message(self):
        """播放欢迎语"""
        if not self.call_uuid:
            return
        
        welcome_file = self.config.get('welcome_audio', '/usr/local/freeswitch/sounds/welcome.wav')
        command = f"uuid_broadcast {self.call_uuid} {welcome_file}"
        
        logger.info("播放欢迎语")
        self.conn.api(command)
        time.sleep(3)  # 等待播放完成
    
    def start_speech_recognition(self) -> str:
        """
        启动语音识别（ASR）
        
        Returns:
            str: 识别到的文本
        """
        if not self.call_uuid:
            return ""
        
        # 这里应该接入实际的 ASR 服务（如阿里云、腾讯云、讯飞等）
        # 示例代码，实际需要根据使用的 ASR 服务调整
        
        logger.info("开始语音识别...")
        
        # 使用 FreeSWITCH 的 detect_speech 模块
        command = f"uuid_detect_speech {self.call_uuid} start"
        self.conn.api(command)
        
        # 等待识别结果（实际应该通过事件监听获取）
        # 这里简化处理，返回模拟数据
        time.sleep(3)
        
        # 实际应该从事件中获取识别结果
        recognized_text = self._get_asr_result()
        logger.info(f"识别结果: {recognized_text}")
        
        return recognized_text
    
    def _get_asr_result(self) -> str:
        """
        获取 ASR 识别结果
        实际应该通过事件监听获取
        
        Returns:
            str: 识别的文本
        """
        # TODO: 实现实际的 ASR 结果获取逻辑
        # 这里返回空字符串，实际使用时需要对接 ASR 服务
        return ""
    
    def robot_conversation(self, max_rounds: int = 5) -> bool:
        """
        机器人对话流程
        
        Args:
            max_rounds: 最大对话轮数
            
        Returns:
            bool: 是否需要转人工
        """
        logger.info("开始机器人对话流程")
        
        for round_num in range(1, max_rounds + 1):
            logger.info(f"对话轮次: {round_num}/{max_rounds}")
            
            # 机器人播放话术
            robot_message = self._get_robot_response(round_num)
            self.speak(robot_message)
            
            # 等待用户回复
            time.sleep(1)
            
            # 识别用户语音
            user_input = self.start_speech_recognition()
            
            if not user_input:
                logger.warning("未识别到用户输入")
                continue
            
            # 意图识别
            intent_result = self.intent_recognizer.recognize(user_input)
            logger.info(f"意图识别结果: {intent_result}")
            
            # 判断是否需要转人工
            if intent_result['intent'] == 'transfer_to_human':
                if intent_result['confidence'] >= 0.8:
                    logger.info("检测到转人工意图，准备转接")
                    return True
            elif intent_result['intent'] == 'continue_robot':
                logger.info("用户选择继续机器人服务")
                continue
            
            # 继续对话流程
            # TODO: 实现具体的业务逻辑
        
        return False  # 对话结束，未请求转人工
    
    def _get_robot_response(self, round_num: int) -> str:
        """
        获取机器人回复话术
        
        Args:
            round_num: 当前对话轮次
            
        Returns:
            str: 机器人话术文本
        """
        # 根据业务需求自定义话术
        responses = {
            1: "您好，我是智能客服机器人，请问有什么可以帮您？如需人工服务，请说转人工。",
            2: "我可以为您查询订单、办理业务等，请问您需要什么帮助？",
            3: "如果您需要人工客服，请随时告诉我。",
        }
        return responses.get(round_num, "请问还有其他需要帮助的吗？")
    
    def speak(self, text: str):
        """
        播放语音（TTS）
        
        Args:
            text: 要播放的文本
        """
        if not self.call_uuid:
            return
        
        logger.info(f"TTS 播放: {text}")
        
        # 这里应该接入实际的 TTS 服务
        # 方式1: 使用 FreeSWITCH 的 mod_tts_commandline 或 mod_unimrcp
        # 方式2: 预先生成音频文件
        # 方式3: 实时调用云端 TTS API 生成音频后播放
        
        # 示例：使用预先生成的音频文件
        # audio_file = self._text_to_speech(text)
        # command = f"uuid_broadcast {self.call_uuid} {audio_file}"
        # self.conn.api(command)
        
        # 简化处理：使用 say 模块
        command = f"uuid_say {self.call_uuid} zh number iterated {text}"
        self.conn.api(command)
        
        time.sleep(2)  # 等待播放完成
    
    def transfer_to_human(self) -> bool:
        """
        转接到人工客服
        
        Returns:
            bool: 转接是否成功
        """
        if not self.call_uuid:
            logger.error("无法转接：没有有效的呼叫 UUID")
            return False
        
        try:
            logger.info("开始转接人工客服...")
            
            # 播放转接提示音
            transfer_prompt = "正在为您转接人工客服，请稍候..."
            self.speak(transfer_prompt)
            
            # 获取人工坐席
            agent_number = self._get_available_agent()
            
            if not agent_number:
                logger.error("没有可用的人工坐席")
                self.speak("抱歉，当前人工客服繁忙，请稍后再试")
                return False
            
            logger.info(f"转接到坐席: {agent_number}")
            
            # 执行转接
            # 方式1: 使用 uuid_transfer
            transfer_destination = f"user/{agent_number}"
            command = f"uuid_transfer {self.call_uuid} {transfer_destination}"
            
            # 方式2: 使用桥接
            # command = f"uuid_bridge {self.call_uuid} user/{agent_number}"
            
            event = self.conn.api(command)
            response = event.getBody()
            
            if '+OK' in response:
                logger.info("转接人工成功")
                
                # 记录转接日志
                self._log_transfer(agent_number)
                
                return True
            else:
                logger.error(f"转接失败: {response}")
                return False
                
        except Exception as e:
            logger.error(f"转接人工异常: {e}")
            return False
    
    def _get_available_agent(self) -> Optional[str]:
        """
        获取可用的人工坐席
        
        Returns:
            str: 坐席号码/分机号，无可用坐席返回 None
        """
        # 这里应该实现实际的坐席路由逻辑
        # 可以对接呼叫中心系统、查询数据库等
        
        # 方式1: 固定坐席列表
        agents = self.config.get('agent_list', ['1001', '1002', '1003'])
        
        # 方式2: 查询 FreeSWITCH 注册用户状态
        for agent in agents:
            if self._check_agent_available(agent):
                return agent
        
        # 方式3: 转接到队列
        queue = self.config.get('agent_queue')
        if queue:
            return f"fifo {queue}"
        
        return None
    
    def _check_agent_available(self, agent_number: str) -> bool:
        """
        检查坐席是否可用
        
        Args:
            agent_number: 坐席号码
            
        Returns:
            bool: 是否可用
        """
        try:
            # 查询用户状态
            command = f"sofia_contact user/{agent_number}"
            event = self.conn.api(command)
            response = event.getBody()
            
            # 如果返回包含错误，说明用户不在线
            if 'error' in response.lower():
                return False
            
            # 检查是否在通话中
            command = f"show channels like {agent_number}"
            event = self.conn.api(command)
            response = event.getBody()
            
            # 如果没有通道，说明空闲
            if '0 total' in response:
                return True
                
            return False
            
        except Exception as e:
            logger.error(f"检查坐席状态异常: {e}")
            return False
    
    def _log_transfer(self, agent_number: str):
        """
        记录转接日志
        
        Args:
            agent_number: 转接到的坐席号码
        """
        transfer_log = {
            'timestamp': time.strftime('%Y-%m-%d %H:%M:%S'),
            'call_uuid': self.call_uuid,
            'customer_number': self.customer_number,
            'agent_number': agent_number,
            'transfer_count': self.intent_recognizer.transfer_count
        }
        
        logger.info(f"转接记录: {json.dumps(transfer_log, ensure_ascii=False)}")
        
        # TODO: 将日志写入数据库或文件
    
    def hangup(self):
        """挂断电话"""
        if self.call_uuid:
            logger.info(f"挂断通话: {self.call_uuid}")
            command = f"uuid_kill {self.call_uuid}"
            self.conn.api(command)
    
    def run(self, phone_number: str):
        """
        执行完整的外呼流程
        
        Args:
            phone_number: 目标电话号码
        """
        try:
            # 1. 连接 FreeSWITCH
            if not self.connect_freeswitch():
                logger.error("无法连接 FreeSWITCH，退出")
                return
            
            # 2. 发起外呼
            call_uuid = self.make_outbound_call(phone_number)
            if not call_uuid:
                logger.error("外呼失败")
                return
            
            # 等待接通
            time.sleep(2)
            
            # 3. 播放欢迎语
            self.play_welcome_message()
            
            # 4. 机器人对话
            need_transfer = self.robot_conversation(max_rounds=5)
            
            # 5. 判断是否需要转人工
            if need_transfer:
                transfer_success = self.transfer_to_human()
                if transfer_success:
                    logger.info("已成功转接人工，保持通话")
                    # 转接后不挂断，让人工处理
                else:
                    logger.error("转接人工失败，挂断电话")
                    self.hangup()
            else:
                # 对话结束，正常挂断
                logger.info("机器人对话结束，挂断电话")
                self.speak("感谢您的来电，再见")
                time.sleep(2)
                self.hangup()
                
        except Exception as e:
            logger.error(f"外呼流程异常: {e}")
            self.hangup()


def main():
    """主函数"""
    # 配置信息
    config = {
        # FreeSWITCH ESL 连接配置
        'freeswitch_host': '127.0.0.1',
        'freeswitch_port': '8021',
        'freeswitch_password': 'ClueCon',
        
        # 外呼配置
        'gateway': 'my_gateway',  # SIP 网关名称
        'caller_id': '10086',      # 主叫号码
        
        # 人工坐席配置
        'agent_list': ['1001', '1002', '1003'],  # 坐席分机号列表
        'agent_queue': 'support_queue',           # 坐席队列
        
        # 音频文件路径
        'welcome_audio': '/usr/local/freeswitch/sounds/welcome.wav',
    }
    
    # 创建机器人处理器
    handler = RobotCallHandler(config)
    
    # 执行外呼（示例电话号码）
    target_phone = '13800138000'
    handler.run(target_phone)


if __name__ == '__main__':
    main()
