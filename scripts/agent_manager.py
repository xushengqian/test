#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
座席管理服务
管理座席状态，分配座席
"""

import json
import logging
from typing import Optional, List, Dict
from dataclasses import dataclass, asdict
from enum import Enum

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class AgentStatus(Enum):
    """座席状态"""
    IDLE = "idle"      # 空闲
    BUSY = "busy"      # 忙碌
    OFFLINE = "offline"  # 离线


@dataclass
class Agent:
    """座席信息"""
    agent_id: str
    extension: str
    status: AgentStatus
    skill_tags: List[str] = None
    current_calls: int = 0
    max_calls: int = 1


class AgentManager:
    """座席管理器"""
    
    def __init__(self):
        # 座席列表（实际项目中应该从数据库加载）
        self.agents = {
            "1001": Agent("1001", "1001", AgentStatus.IDLE, ["general"], 0, 1),
            "1002": Agent("1002", "1002", AgentStatus.IDLE, ["general", "technical"], 0, 1),
            "1003": Agent("1003", "1003", AgentStatus.IDLE, ["general"], 0, 1),
            "1004": Agent("1004", "1004", AgentStatus.IDLE, ["sales"], 0, 1),
            "1005": Agent("1005", "1005", AgentStatus.IDLE, ["support"], 0, 1),
        }
    
    def get_available_agent(self, skill_tags: List[str] = None) -> Optional[str]:
        """
        获取可用座席
        
        Args:
            skill_tags: 技能标签（可选）
            
        Returns:
            座席编号，如果没有可用座席返回 None
        """
        # 查找空闲且未达到最大并发数的座席
        available_agents = []
        
        for agent_id, agent in self.agents.items():
            if (agent.status == AgentStatus.IDLE and 
                agent.current_calls < agent.max_calls):
                
                # 如果指定了技能标签，检查是否匹配
                if skill_tags:
                    if any(tag in (agent.skill_tags or []) for tag in skill_tags):
                        available_agents.append(agent)
                else:
                    available_agents.append(agent)
        
        if not available_agents:
            logger.warning("没有可用座席")
            return None
        
        # 选择负载最轻的座席
        best_agent = min(available_agents, key=lambda a: a.current_calls)
        
        # 更新座席状态
        best_agent.status = AgentStatus.BUSY
        best_agent.current_calls += 1
        
        logger.info(f"分配座席: {best_agent.agent_id}")
        return best_agent.agent_id
    
    def release_agent(self, agent_id: str):
        """释放座席"""
        if agent_id in self.agents:
            agent = self.agents[agent_id]
            agent.current_calls = max(0, agent.current_calls - 1)
            
            if agent.current_calls == 0:
                agent.status = AgentStatus.IDLE
            
            logger.info(f"释放座席: {agent_id}, 当前通话数: {agent.current_calls}")
    
    def get_agent_info(self, agent_id: str) -> Optional[Dict]:
        """获取座席信息"""
        if agent_id in self.agents:
            agent = self.agents[agent_id]
            result = asdict(agent)
            result['status'] = agent.status.value
            return result
        return None


# 全局座席管理器实例
_manager = AgentManager()


def get_available_agent(skill_tags: List[str] = None) -> Optional[str]:
    """获取可用座席（供外部调用）"""
    return _manager.get_available_agent(skill_tags)


def release_agent(agent_id: str):
    """释放座席（供外部调用）"""
    _manager.release_agent(agent_id)


def main():
    """命令行测试"""
    import sys
    
    if len(sys.argv) < 2:
        print("Usage: python agent_manager.py <command> [args]")
        print("Commands: get_agent, release_agent <agent_id>")
        sys.exit(1)
    
    command = sys.argv[1]
    
    if command == "get_agent":
        agent_id = get_available_agent()
        if agent_id:
            print(json.dumps({"agent_id": agent_id}))
        else:
            print(json.dumps({"agent_id": None}))
    
    elif command == "release_agent":
        if len(sys.argv) < 3:
            print("Error: 需要提供座席ID")
            sys.exit(1)
        agent_id = sys.argv[2]
        release_agent(agent_id)
        print(json.dumps({"status": "success"}))
    
    else:
        print(f"Unknown command: {command}")
        sys.exit(1)


if __name__ == "__main__":
    main()
