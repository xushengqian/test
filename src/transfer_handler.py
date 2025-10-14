"""
转接处理模块
处理机器人到人工坐席的转接逻辑
"""
import time
import random
from typing import Optional, List, Dict, Any
from pathlib import Path
from enum import Enum

import yaml

from utils.logger import get_logger
from utils.database import db_manager

logger = get_logger(__name__)

# 加载配置
config_path = Path(__file__).parent.parent / "config" / "config.yaml"
with open(config_path, 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

agent_config = config.get('agent', {})

class TransferStrategy(Enum):
    """转接策略枚举"""
    ROUND_ROBIN = "round_robin"  # 轮询
    LEAST_BUSY = "least_busy"    # 最空闲
    SKILL_BASED = "skill_based"  # 基于技能
    RANDOM = "random"            # 随机

class AgentStatus(Enum):
    """坐席状态枚举"""
    ONLINE = "online"          # 在线
    BUSY = "busy"             # 忙碌
    OFFLINE = "offline"        # 离线
    BREAK = "break"           # 小休
    AVAILABLE = "available"    # 可用

class Agent:
    """坐席对象"""
    
    def __init__(self, number: str, name: str, skills: List[str] = None):
        self.number = number
        self.name = name
        self.skills = skills or []
        self.status = AgentStatus.OFFLINE
        self.current_calls = 0
        self.total_calls = 0
        self.last_call_time = None
    
    def is_available(self) -> bool:
        """检查坐席是否可用"""
        return self.status in [AgentStatus.ONLINE, AgentStatus.AVAILABLE]
    
    def to_dict(self) -> Dict[str, Any]:
        """转换为字典"""
        return {
            'number': self.number,
            'name': self.name,
            'skills': self.skills,
            'status': self.status.value,
            'current_calls': self.current_calls,
            'total_calls': self.total_calls
        }

class TransferHandler:
    """转接处理器"""
    
    def __init__(self):
        # 加载坐席配置
        self.agents = self._load_agents()
        
        # 转接策略
        strategy_name = agent_config.get('transfer_strategy', 'round_robin')
        self.strategy = TransferStrategy(strategy_name)
        
        # 队列名称
        self.queue_name = agent_config.get('queue', 'support_queue')
        
        # 等待音乐路径
        self.hold_music = agent_config.get('hold_music', '')
        
        # 轮询索引
        self.round_robin_index = 0
        
        logger.info(f"转接处理器初始化成功，策略: {self.strategy.value}")
    
    def _load_agents(self) -> List[Agent]:
        """加载坐席配置"""
        agents_config = agent_config.get('agents', [])
        agents = []
        
        for agent_data in agents_config:
            agent = Agent(
                number=agent_data['number'],
                name=agent_data['name'],
                skills=agent_data.get('skills', [])
            )
            agents.append(agent)
            logger.info(f"加载坐席: {agent.name} ({agent.number})")
        
        return agents
    
    def find_available_agent(self, required_skills: List[str] = None) -> Optional[Agent]:
        """
        查找可用的坐席
        
        Args:
            required_skills: 所需技能列表
            
        Returns:
            可用的坐席对象，如果没有则返回 None
        """
        # 获取所有可用的坐席
        available_agents = []
        
        for agent in self.agents:
            # 从缓存获取坐席状态
            status_str = db_manager.get_agent_status(agent.number)
            
            if status_str in ['online', 'available']:
                agent.status = AgentStatus(status_str)
                
                # 如果需要特定技能，检查技能匹配
                if required_skills:
                    if any(skill in agent.skills for skill in required_skills):
                        available_agents.append(agent)
                else:
                    available_agents.append(agent)
        
        if not available_agents:
            logger.warning("没有可用的坐席")
            return None
        
        # 根据策略选择坐席
        selected_agent = self._select_agent_by_strategy(available_agents)
        
        if selected_agent:
            logger.info(f"选择坐席: {selected_agent.name} ({selected_agent.number})")
        
        return selected_agent
    
    def _select_agent_by_strategy(self, agents: List[Agent]) -> Optional[Agent]:
        """
        根据策略选择坐席
        
        Args:
            agents: 可用坐席列表
            
        Returns:
            选中的坐席
        """
        if not agents:
            return None
        
        if self.strategy == TransferStrategy.ROUND_ROBIN:
            # 轮询策略
            agent = agents[self.round_robin_index % len(agents)]
            self.round_robin_index += 1
            return agent
            
        elif self.strategy == TransferStrategy.LEAST_BUSY:
            # 最空闲策略 - 选择当前通话数最少的
            return min(agents, key=lambda a: a.current_calls)
            
        elif self.strategy == TransferStrategy.RANDOM:
            # 随机策略
            return random.choice(agents)
            
        else:
            # 默认使用第一个
            return agents[0]
    
    def transfer_to_agent(self, call_id: str, agent: Agent, reason: str = "") -> bool:
        """
        转接到指定坐席
        
        Args:
            call_id: 通话ID
            agent: 目标坐席
            reason: 转接原因
            
        Returns:
            是否成功
        """
        try:
            # 更新坐席状态为忙碌
            db_manager.set_agent_status(agent.number, AgentStatus.BUSY.value)
            
            # 更新通话记录
            db_manager.update_call_status(
                call_id,
                status='transferring',
                transfer_to_agent=True,
                agent_number=agent.number
            )
            
            # 记录转接信息
            transfer_info = {
                'call_id': call_id,
                'agent_number': agent.number,
                'agent_name': agent.name,
                'reason': reason,
                'timestamp': time.time()
            }
            
            # 存储转接信息到缓存
            db_manager.cache_set(
                f"transfer:{call_id}",
                transfer_info,
                expire=3600  # 1小时过期
            )
            
            logger.info(f"转接成功 - 通话: {call_id} -> 坐席: {agent.name}")
            
            return True
            
        except Exception as e:
            logger.error(f"转接失败: {str(e)}")
            return False
    
    def handle_transfer_request(self, call_id: str, user_input: str = "") -> Dict[str, Any]:
        """
        处理转接请求
        
        Args:
            call_id: 通话ID
            user_input: 用户输入（用于分析原因）
            
        Returns:
            处理结果
        """
        result = {
            'success': False,
            'agent': None,
            'message': '',
            'wait_time': 0
        }
        
        # 分析转接原因
        reason = self._analyze_transfer_reason(user_input)
        
        # 确定所需技能
        required_skills = self._determine_required_skills(reason)
        
        # 查找可用坐席
        agent = self.find_available_agent(required_skills)
        
        if agent:
            # 执行转接
            success = self.transfer_to_agent(call_id, agent, reason)
            
            if success:
                result['success'] = True
                result['agent'] = agent.to_dict()
                result['message'] = f"正在为您转接到{agent.name}，请稍候..."
                result['wait_time'] = 3  # 预计等待时间
            else:
                result['message'] = "转接失败，请稍后再试"
        else:
            # 没有可用坐席，加入队列
            self.add_to_queue(call_id, reason)
            result['message'] = "当前坐席繁忙，已将您加入等待队列，请耐心等待..."
            result['wait_time'] = self.get_estimated_wait_time()
        
        return result
    
    def _analyze_transfer_reason(self, user_input: str) -> str:
        """
        分析转接原因
        
        Args:
            user_input: 用户输入
            
        Returns:
            原因描述
        """
        reasons = []
        
        # 关键词分析
        if "投诉" in user_input:
            reasons.append("投诉")
        if "退" in user_input or "换" in user_input:
            reasons.append("售后")
        if "买" in user_input or "购" in user_input:
            reasons.append("销售")
        if "技术" in user_input or "故障" in user_input:
            reasons.append("技术支持")
        
        if not reasons:
            reasons.append("一般咨询")
        
        return ",".join(reasons)
    
    def _determine_required_skills(self, reason: str) -> List[str]:
        """
        根据原因确定所需技能
        
        Args:
            reason: 转接原因
            
        Returns:
            技能列表
        """
        skills = []
        
        if "投诉" in reason:
            skills.append("投诉")
        if "售后" in reason:
            skills.append("售后")
        if "销售" in reason:
            skills.append("销售")
        if "技术" in reason:
            skills.append("技术支持")
        
        return skills
    
    def add_to_queue(self, call_id: str, reason: str = ""):
        """
        将通话加入队列
        
        Args:
            call_id: 通话ID
            reason: 原因
        """
        queue_item = {
            'call_id': call_id,
            'reason': reason,
            'timestamp': time.time()
        }
        
        # 使用 Redis 列表作为队列
        queue_key = f"queue:{self.queue_name}"
        db_manager.redis_client.rpush(queue_key, str(queue_item))
        
        logger.info(f"通话 {call_id} 已加入队列")
    
    def get_estimated_wait_time(self) -> int:
        """
        获取预计等待时间
        
        Returns:
            预计等待时间（秒）
        """
        # 获取队列长度
        queue_key = f"queue:{self.queue_name}"
        queue_length = db_manager.redis_client.llen(queue_key)
        
        # 估算每个通话的处理时间为3分钟
        estimated_time = queue_length * 180
        
        return estimated_time
    
    def get_queue_status(self) -> Dict[str, Any]:
        """
        获取队列状态
        
        Returns:
            队列状态信息
        """
        queue_key = f"queue:{self.queue_name}"
        queue_length = db_manager.redis_client.llen(queue_key)
        
        # 统计在线坐席数
        online_agents = sum(
            1 for agent in self.agents 
            if db_manager.get_agent_status(agent.number) in ['online', 'available']
        )
        
        # 统计忙碌坐席数
        busy_agents = sum(
            1 for agent in self.agents 
            if db_manager.get_agent_status(agent.number) == 'busy'
        )
        
        return {
            'queue_name': self.queue_name,
            'queue_length': queue_length,
            'online_agents': online_agents,
            'busy_agents': busy_agents,
            'available_agents': online_agents - busy_agents,
            'estimated_wait_time': self.get_estimated_wait_time()
        }

# 创建全局实例
transfer_handler = TransferHandler()

# 导出
__all__ = ['transfer_handler', 'TransferHandler', 'Agent', 'AgentStatus', 'TransferStrategy']