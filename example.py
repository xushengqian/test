#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
使用示例 - FreeSWITCH 机器人外呼
"""

from robot_outbound_call import RobotOutboundCall, CallState
from intent_recognizer import IntentRecognizer
import time

def example_basic_usage():
    """基础使用示例"""
    print("=== 基础使用示例 ===")
    
    # 创建机器人外呼实例
    robot = RobotOutboundCall(
        host="127.0.0.1",
        port=8021,
        password="ClueCon"  # 修改为你的FreeSWITCH密码
    )
    
    # 连接到FreeSWITCH
    if not robot.connect():
        print("❌ 无法连接到FreeSWITCH")
        return
    
    print("✅ 已连接到FreeSWITCH")
    
    try:
        # 配置参数
        caller_id = "1000"  # 主叫号码
        callee_number = "1001"  # 被叫号码
        human_agent_number = "1002"  # 人工坐席号码
        
        # 发起外呼
        print(f"📞 发起外呼: {caller_id} -> {callee_number}")
        if robot.make_call(caller_id, callee_number, human_agent_number):
            print("✅ 呼叫已发起")
            
            # 模拟处理意图识别（实际应该从语音识别获取）
            print("\n测试意图识别:")
            test_texts = [
                "你好",
                "我想咨询一个问题",
                "转人工",  # 这个应该触发转接
                "我要找人工客服",
                "再见"
            ]
            
            for text in test_texts:
                intent = robot.intent_recognizer.recognize(text)
                print(f"  文本: '{text}' -> 意图: {intent.value}")
                
                if intent.value == "transfer_to_human":
                    print(f"  ⚠️  检测到转人工意图！应该执行转接到: {human_agent_number}")
            
        else:
            print("❌ 呼叫发起失败")
            
    except Exception as e:
        print(f"❌ 发生错误: {e}")
    finally:
        robot.disconnect()
        print("✅ 已断开连接")


def example_intent_recognition():
    """意图识别示例"""
    print("\n=== 意图识别示例 ===")
    
    recognizer = IntentRecognizer()
    
    test_cases = [
        ("你好", "greeting"),
        ("转人工", "transfer_to_human"),
        ("我要找人工客服", "transfer_to_human"),
        ("帮我转接人工服务", "transfer_to_human"),
        ("我想问一个问题", "question"),
        ("再见", "goodbye"),
        ("", "unknown"),
    ]
    
    print("意图识别测试:")
    for text, expected in test_cases:
        intent = recognizer.recognize(text)
        status = "✅" if intent.value == expected else "❌"
        print(f"{status} '{text}' -> {intent.value} (期望: {expected})")


def example_transfer_simulation():
    """转人工模拟示例"""
    print("\n=== 转人工模拟示例 ===")
    
    robot = RobotOutboundCall(
        host="127.0.0.1",
        port=8021,
        password="ClueCon"
    )
    
    if not robot.connect():
        print("❌ 无法连接到FreeSWITCH")
        return
    
    try:
        # 模拟呼叫UUID（实际使用时从FreeSWITCH获取）
        test_uuid = "test-uuid-12345"
        human_agent_number = "1002"
        
        print(f"📞 模拟呼叫UUID: {test_uuid}")
        print(f"🎯 人工坐席号码: {human_agent_number}")
        
        # 模拟用户说"转人工"
        user_input = "转人工"
        print(f"👤 用户说: '{user_input}'")
        
        intent = robot.intent_recognizer.recognize(user_input)
        print(f"🔍 识别意图: {intent.value}")
        
        if intent.value == "transfer_to_human":
            print(f"🔄 执行转接到: {human_agent_number}")
            # 实际转接（需要真实的UUID）
            # result = robot.transfer_to_human(test_uuid, human_agent_number)
            print("   (注意: 这是模拟，需要真实的呼叫UUID才能执行)")
        
    finally:
        robot.disconnect()


if __name__ == "__main__":
    print("FreeSWITCH 机器人外呼系统 - 使用示例\n")
    
    # 运行示例
    example_intent_recognition()
    example_transfer_simulation()
    
    # 取消注释以运行实际外呼（需要FreeSWITCH运行）
    # example_basic_usage()
