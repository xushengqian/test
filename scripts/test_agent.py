#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
座席管理测试脚本
用于测试座席分配和释放功能
"""

import sys
import json
from agent_manager import AgentManager, AgentStatus

def test_agent_allocation():
    """测试座席分配"""
    manager = AgentManager()
    
    print("=" * 50)
    print("座席分配测试")
    print("=" * 50)
    
    # 测试分配多个座席
    for i in range(7):
        agent_id = manager.get_available_agent()
        if agent_id:
            print(f"✓ 分配座席 {i+1}: {agent_id}")
            agent_info = manager.get_agent_info(agent_id)
            print(f"  状态: {agent_info['status']}, 当前通话: {agent_info['current_calls']}")
        else:
            print(f"✗ 分配座席 {i+1}: 无可用座席")
        print()
    
    # 释放一些座席
    print("释放座席 1001 和 1002...")
    manager.release_agent("1001")
    manager.release_agent("1002")
    print()
    
    # 再次分配
    print("再次分配座席...")
    agent_id = manager.get_available_agent()
    if agent_id:
        print(f"✓ 分配座席: {agent_id}")
    else:
        print("✗ 无可用座席")
    
    print()
    print("=" * 50)
    print("所有座席状态")
    print("=" * 50)
    for agent_id, agent in manager.agents.items():
        print(f"座席 {agent_id}: {agent.status.value}, 通话数: {agent.current_calls}/{agent.max_calls}")

if __name__ == "__main__":
    test_agent_allocation()
