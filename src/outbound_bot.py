"""
外呼机器人主模块
处理 FreeSWITCH ESL 连接和呼叫流程
"""
import asyncio
import time
import uuid
from typing import Optional, Dict, Any
from pathlib import Path
from datetime import datetime

import yaml
import ESL

from asr_service import asr_service
from intent_detector import intent_detector
from transfer_handler import transfer_handler
from utils.logger import get_logger
from utils.database import db_manager

logger = get_logger(__name__)

# 加载配置
config_path = Path(__file__).parent.parent / "config" / "config.yaml"
with open(config_path, 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

fs_config = config.get('freeswitch', {})
outbound_config = config.get('outbound', {})
robot_config = config.get('robot', {})

class CallSession:
    """通话会话对象"""
    
    def __init__(self, call_id: str, phone_number: str):
        self.call_id = call_id
        self.phone_number = phone_number
        self.start_time = datetime.now()
        self.answer_time = None
        self.end_time = None
        self.status = 'initiated'
        self.transferred = False
        self.agent_number = None
        self.messages = []  # 对话记录
        self.recording_path = None
    
    def add_message(self, role: str, content: str):
        """添加对话消息"""
        self.messages.append({
            'role': role,
            'content': content,
            'timestamp': time.time()
        })
    
    def to_dict(self) -> Dict[str, Any]:
        """转换为字典"""
        return {
            'call_id': self.call_id,
            'phone_number': self.phone_number,
            'start_time': self.start_time.isoformat() if self.start_time else None,
            'answer_time': self.answer_time.isoformat() if self.answer_time else None,
            'end_time': self.end_time.isoformat() if self.end_time else None,
            'status': self.status,
            'transferred': self.transferred,
            'agent_number': self.agent_number,
            'messages': self.messages
        }

class OutboundBot:
    """外呼机器人"""
    
    def __init__(self):
        self.fs_host = fs_config.get('host', '127.0.0.1')
        self.fs_port = fs_config.get('port', 8021)
        self.fs_password = fs_config.get('password', 'ClueCon')
        
        # ESL 连接
        self.inbound_conn = None  # Inbound 连接（用于发起呼叫）
        self.outbound_conn = None  # Outbound Socket 连接（用于处理呼叫）
        
        # 会话管理
        self.sessions = {}
        
        # 配置
        self.max_concurrent_calls = outbound_config.get('max_concurrent_calls', 10)
        self.ring_timeout = outbound_config.get('ring_timeout', 30)
        self.call_timeout = outbound_config.get('call_timeout', 300)
        
        logger.info("外呼机器人初始化")
    
    def connect_freeswitch(self) -> bool:
        """
        连接到 FreeSWITCH
        
        Returns:
            是否成功
        """
        try:
            self.inbound_conn = ESL.ESLconnection(
                self.fs_host, 
                str(self.fs_port), 
                self.fs_password
            )
            
            if self.inbound_conn.connected():
                logger.info(f"成功连接到 FreeSWITCH: {self.fs_host}:{self.fs_port}")
                
                # 订阅事件
                self.inbound_conn.events('plain', 'ALL')
                
                return True
            else:
                logger.error("无法连接到 FreeSWITCH")
                return False
                
        except Exception as e:
            logger.error(f"连接 FreeSWITCH 失败: {str(e)}")
            return False
    
    def make_call(self, phone_number: str) -> Optional[str]:
        """
        发起外呼
        
        Args:
            phone_number: 目标电话号码
            
        Returns:
            通话ID，失败返回 None
        """
        if not self.inbound_conn or not self.inbound_conn.connected():
            logger.error("FreeSWITCH 未连接")
            return None
        
        # 检查并发限制
        if len(self.sessions) >= self.max_concurrent_calls:
            logger.warning(f"达到并发限制: {self.max_concurrent_calls}")
            return None
        
        try:
            # 生成唯一的通话ID
            call_id = str(uuid.uuid4())
            
            # 创建会话
            session = CallSession(call_id, phone_number)
            self.sessions[call_id] = session
            
            # 创建数据库记录
            db_manager.create_call_record(call_id, phone_number)
            
            # 构建 originate 命令
            sip_profile = fs_config.get('sip', {}).get('profile', 'external')
            gateway = fs_config.get('sip', {}).get('gateway', 'default')
            caller_id = fs_config.get('sip', {}).get('caller_id', '10086')
            
            # originate 命令格式
            originate_cmd = (
                f"originate {{origination_uuid={call_id},"
                f"origination_caller_id_number={caller_id},"
                f"ignore_early_media=true,"
                f"call_timeout={self.ring_timeout}}} "
                f"sofia/{sip_profile}/{phone_number}@{gateway} "
                f"&socket('127.0.0.1:8040 async full')"
            )
            
            logger.info(f"发起呼叫: {phone_number} (ID: {call_id})")
            
            # 执行呼叫命令
            result = self.inbound_conn.api('bgapi', originate_cmd)
            
            if result:
                response = result.getBody()
                logger.info(f"呼叫命令已发送: {response}")
                return call_id
            else:
                logger.error("呼叫命令执行失败")
                del self.sessions[call_id]
                return None
                
        except Exception as e:
            logger.error(f"发起呼叫失败: {str(e)}")
            return None
    
    async def handle_call(self, conn: ESL.ESLconnection, call_id: str):
        """
        处理通话（Outbound Socket 模式）
        
        Args:
            conn: ESL 连接
            call_id: 通话ID
        """
        session = self.sessions.get(call_id)
        if not session:
            logger.error(f"会话不存在: {call_id}")
            return
        
        try:
            # 应答呼叫
            conn.execute('answer')
            session.answer_time = datetime.now()
            session.status = 'answered'
            
            # 更新数据库
            db_manager.update_call_status(
                call_id, 
                status='answered',
                answer_time=session.answer_time
            )
            
            # 播放欢迎语
            welcome_msg = robot_config.get('welcome_message', '您好，我是智能客服助手')
            await self.play_tts(conn, welcome_msg)
            session.add_message('robot', welcome_msg)
            
            # 进入对话循环
            await self.conversation_loop(conn, session)
            
        except Exception as e:
            logger.error(f"处理通话异常: {str(e)}")
        finally:
            # 结束通话
            self.end_call(session)
    
    async def conversation_loop(self, conn: ESL.ESLconnection, session: CallSession):
        """
        对话循环
        
        Args:
            conn: ESL 连接
            session: 会话对象
        """
        max_silence_count = 3
        silence_count = 0
        
        while session.status == 'answered':
            try:
                # 录音并识别
                user_input = await self.record_and_recognize(conn)
                
                if not user_input:
                    # 静音处理
                    silence_count += 1
                    if silence_count >= max_silence_count:
                        await self.play_tts(conn, "您还在吗？如果需要帮助请随时告诉我。")
                        silence_count = 0
                    continue
                
                # 重置静音计数
                silence_count = 0
                
                # 记录用户输入
                session.add_message('user', user_input)
                logger.info(f"用户说: {user_input}")
                
                # 意图识别
                should_transfer = intent_detector.should_transfer(user_input, session.call_id)
                
                if should_transfer:
                    # 用户要求转人工
                    logger.info("检测到转人工意图")
                    await self.handle_transfer(conn, session, user_input)
                    break
                else:
                    # 继续机器人对话
                    response = await self.generate_response(user_input, session)
                    await self.play_tts(conn, response)
                    session.add_message('robot', response)
                
                # 检查是否结束对话
                if self.should_end_conversation(user_input):
                    goodbye_msg = robot_config.get('goodbye_message', '感谢您的来电，再见！')
                    await self.play_tts(conn, goodbye_msg)
                    session.add_message('robot', goodbye_msg)
                    break
                    
            except asyncio.TimeoutError:
                logger.warning("对话超时")
                break
            except Exception as e:
                logger.error(f"对话循环异常: {str(e)}")
                break
    
    async def record_and_recognize(self, conn: ESL.ESLconnection) -> Optional[str]:
        """
        录音并识别
        
        Args:
            conn: ESL 连接
            
        Returns:
            识别的文本
        """
        try:
            # 开始录音
            record_file = f"/tmp/recording_{uuid.uuid4()}.wav"
            conn.execute('record', f'{record_file} 5 200 3')
            
            # 等待录音完成
            await asyncio.sleep(5)
            
            # 语音识别
            success, text = asr_service.recognize_file(record_file)
            
            if success:
                return text
            else:
                return None
                
        except Exception as e:
            logger.error(f"录音识别失败: {str(e)}")
            return None
    
    async def play_tts(self, conn: ESL.ESLconnection, text: str):
        """
        播放 TTS 语音
        
        Args:
            conn: ESL 连接
            text: 要播放的文本
        """
        try:
            # 这里简化处理，实际应该调用 TTS 服务生成语音文件
            # 然后播放生成的语音文件
            logger.info(f"播放 TTS: {text}")
            
            # 暂时使用预录制的语音文件代替
            # conn.execute('playback', 'ivr/ivr-welcome.wav')
            
            # 模拟播放时间
            await asyncio.sleep(len(text) * 0.1)
            
        except Exception as e:
            logger.error(f"播放 TTS 失败: {str(e)}")
    
    async def generate_response(self, user_input: str, session: CallSession) -> str:
        """
        生成机器人响应
        
        Args:
            user_input: 用户输入
            session: 会话对象
            
        Returns:
            响应文本
        """
        # 这里可以接入 NLU/对话管理系统
        # 简单的规则响应
        
        if "你好" in user_input or "您好" in user_input:
            return "您好！很高兴为您服务，请问有什么可以帮助您的吗？"
        elif "查询" in user_input:
            return "请告诉我您要查询的内容，我会尽力为您解答。"
        elif "投诉" in user_input:
            return "非常抱歉给您带来不便，您的问题很重要，我建议您转接人工客服详细说明。"
        elif "谢谢" in user_input:
            return "不客气，很高兴能帮到您！还有其他需要帮助的吗？"
        else:
            return "我理解您的需求，让我为您查询一下相关信息..."
    
    async def handle_transfer(self, conn: ESL.ESLconnection, session: CallSession, user_input: str):
        """
        处理转人工
        
        Args:
            conn: ESL 连接
            session: 会话对象
            user_input: 触发转接的用户输入
        """
        try:
            # 播放转接提示
            transfer_msg = robot_config.get('transfer_message', '好的，正在为您转接人工客服，请稍候...')
            await self.play_tts(conn, transfer_msg)
            session.add_message('robot', transfer_msg)
            
            # 调用转接处理器
            result = transfer_handler.handle_transfer_request(session.call_id, user_input)
            
            if result['success']:
                # 转接成功
                agent = result['agent']
                session.transferred = True
                session.agent_number = agent['number']
                session.status = 'transferred'
                
                # 执行 FreeSWITCH 转接命令
                transfer_dest = f"agent_{agent['number']}"
                conn.execute('transfer', transfer_dest)
                
                logger.info(f"成功转接到坐席: {agent['name']} ({agent['number']})")
                
            else:
                # 转接失败
                await self.play_tts(conn, result['message'])
                session.add_message('robot', result['message'])
                
                if result['wait_time'] > 0:
                    # 播放等待音乐
                    hold_music = transfer_handler.hold_music
                    if hold_music:
                        conn.execute('playback', hold_music)
                        
        except Exception as e:
            logger.error(f"转接处理失败: {str(e)}")
            await self.play_tts(conn, "抱歉，转接失败，请稍后再试。")
    
    def should_end_conversation(self, user_input: str) -> bool:
        """
        判断是否应该结束对话
        
        Args:
            user_input: 用户输入
            
        Returns:
            是否结束
        """
        end_words = ["再见", "拜拜", "结束", "挂断", "没事了", "不用了"]
        return any(word in user_input for word in end_words)
    
    def end_call(self, session: CallSession):
        """
        结束通话
        
        Args:
            session: 会话对象
        """
        try:
            session.end_time = datetime.now()
            
            if session.answer_time:
                duration = (session.end_time - session.answer_time).seconds
            else:
                duration = 0
            
            session.status = 'completed'
            
            # 更新数据库
            db_manager.update_call_status(
                session.call_id,
                status='completed',
                end_time=session.end_time,
                duration=duration,
                transfer_to_agent=session.transferred,
                agent_number=session.agent_number
            )
            
            # 保存对话记录
            db_manager.cache_set(
                f"conversation:{session.call_id}",
                session.to_dict(),
                expire=86400  # 保存24小时
            )
            
            # 从会话列表中移除
            if session.call_id in self.sessions:
                del self.sessions[session.call_id]
            
            logger.info(f"通话结束: {session.call_id}, 时长: {duration}秒")
            
        except Exception as e:
            logger.error(f"结束通话失败: {str(e)}")
    
    def get_active_calls(self) -> List[Dict[str, Any]]:
        """
        获取活动通话列表
        
        Returns:
            通话列表
        """
        calls = []
        for session in self.sessions.values():
            calls.append(session.to_dict())
        return calls

# 创建全局实例
outbound_bot = OutboundBot()

# 导出
__all__ = ['outbound_bot', 'OutboundBot', 'CallSession']