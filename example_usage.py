#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FreeSwitch 机器人呼出系统使用示例
演示如何使用各个功能模块
"""

import os
import sys
import time
import json
import importlib.util
from datetime import datetime, timedelta

# 导入模块
def import_module_from_path(module_name, file_path):
    spec = importlib.util.spec_from_file_location(module_name, file_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

# 导入各个服务
outbound_module = import_module_from_path("outbound_caller", "src/robot/outbound_caller.py")
intent_module = import_module_from_path("intent_service", "src/intent-recognition/intent_service.py")
transfer_module = import_module_from_path("transfer_service", "src/agent-transfer/transfer_service.py")

OutboundCaller = outbound_module.OutboundCaller
IntentService = intent_module.IntentService
TransferService = transfer_module.TransferService
AgentStatus = transfer_module.AgentStatus

def example_outbound_calls():
    """示例：批量添加呼出任务"""
    print("=== 呼出任务示例 ===")
    
    caller = OutboundCaller()
    
    # 示例客户数据
    customers = [
        {
            "phone": "13800138001",
            "name": "张三",
            "level": "VIP",
            "product": "智能客服系统",
            "priority": 1
        },
        {
            "phone": "13900139001", 
            "name": "李四",
            "level": "NORMAL",
            "product": "语音识别服务",
            "priority": 5
        },
        {
            "phone": "13700137001",
            "name": "王五",
            "level": "HIGH",
            "product": "呼叫中心解决方案",
            "priority": 3
        }
    ]
    
    # 添加呼出任务
    task_ids = []
    for customer in customers:
        task_id = caller.add_call_task(
            customer_phone=customer["phone"],
            priority=customer["priority"],
            campaign_id="DEMO_CAMPAIGN_2024",
            customer_data={
                "name": customer["name"],
                "level": customer["level"],
                "product": customer["product"],
                "contact_time": datetime.now().isoformat()
            },
            scheduled_time=datetime.now() + timedelta(minutes=1)  # 1分钟后开始
        )
        
        if task_id:
            task_ids.append(task_id)
            print(f"✓ 已添加呼出任务: {customer['name']} ({customer['phone']}) - {task_id}")
        else:
            print(f"✗ 添加任务失败: {customer['name']} ({customer['phone']})")
    
    # 获取统计信息
    stats = caller.get_statistics()
    print(f"\n呼出统计: {json.dumps(stats, indent=2, ensure_ascii=False)}")
    
    return task_ids

def example_intent_recognition():
    """示例：意图识别和对话管理"""
    print("\n=== 意图识别示例 ===")
    
    service = IntentService()
    
    # 模拟客户对话
    conversation_scenarios = [
        {
            "session_id": "customer_001",
            "name": "友好咨询客户",
            "inputs": [
                "你好，我想了解一下你们的产品",
                "价格怎么样？",
                "好的，谢谢你的介绍",
                "再见"
            ]
        },
        {
            "session_id": "customer_002", 
            "name": "投诉客户",
            "inputs": [
                "你好",
                "我要投诉，你们的服务太差了",
                "我很生气，要求退款",
                "转人工客服！"
            ]
        },
        {
            "session_id": "customer_003",
            "name": "困惑客户",
            "inputs": [
                "嗯...",
                "我不太明白",
                "这个怎么用啊",
                "算了，我要找人工"
            ]
        }
    ]
    
    for scenario in conversation_scenarios:
        print(f"\n--- {scenario['name']} ---")
        
        for i, user_input in enumerate(scenario['inputs']):
            result = service.process_user_input(scenario['session_id'], user_input)
            
            intent_result = result['intent_result']
            should_transfer = result['should_transfer']
            
            print(f"轮次 {i+1}: \"{user_input}\"")
            print(f"  意图: {intent_result['intent']} (置信度: {intent_result['confidence']:.2f})")
            print(f"  情感: {intent_result['emotion']}")
            print(f"  转人工: {'是' if should_transfer else '否'}")
            
            if should_transfer:
                print(f"  >>> 检测到转人工需求，准备转接 <<<")
                break
        
        # 获取对话趋势分析
        trend = result.get('trend_analysis', {})
        print(f"对话趋势: {trend.get('trend', 'unknown')} (建议: {trend.get('recommendation', 'continue')})")

def example_agent_management():
    """示例：客服管理和转接"""
    print("\n=== 客服管理示例 ===")
    
    service = TransferService()
    agent_manager = service.agent_manager
    
    # 显示所有客服
    print("客服列表:")
    for agent_id, agent in agent_manager.agents.items():
        print(f"  {agent.name} ({agent.id}) - 分机: {agent.extension} - 状态: {agent.status.value}")
        print(f"    技能: {', '.join(agent.skills)} - 最大通话数: {agent.max_concurrent_calls}")
    
    # 设置客服状态
    print("\n设置客服状态:")
    online_agents = ["agent_001", "agent_002", "agent_003"]
    for agent_id in online_agents:
        success = agent_manager.update_agent_status(agent_id, AgentStatus.ONLINE)
        if success:
            agent = agent_manager.agents[agent_id]
            print(f"✓ {agent.name} 已上线")
    
    # 模拟转接请求
    print("\n转接请求示例:")
    transfer_scenarios = [
        {
            "session_id": "session_vip_001",
            "customer_phone": "13800138001",
            "customer_priority": "VIP",
            "required_skills": ["vip"],
            "description": "VIP客户技术咨询"
        },
        {
            "session_id": "session_complaint_001", 
            "customer_phone": "13900139001",
            "customer_priority": "HIGH",
            "required_skills": ["complaint"],
            "description": "投诉处理"
        },
        {
            "session_id": "session_normal_001",
            "customer_phone": "13700137001", 
            "customer_priority": "NORMAL",
            "required_skills": ["general"],
            "description": "一般咨询"
        }
    ]
    
    for scenario in transfer_scenarios:
        print(f"\n--- {scenario['description']} ---")
        
        result = service.request_transfer(
            session_id=scenario['session_id'],
            customer_phone=scenario['customer_phone'],
            customer_priority=scenario['customer_priority'],
            required_skills=scenario['required_skills']
        )
        
        print(f"转接结果: {result['status']}")
        if result['status'] == 'success':
            print(f"  分配客服: {result['agent_name']} (分机: {result['agent_extension']})")
            print(f"  等待时间: {result['wait_time']} 秒")
        elif result['status'] == 'queued':
            print(f"  队列位置: {result['queue_position']}")
            print(f"  预计等待: {result['estimated_wait_time']} 秒")
        else:
            print(f"  错误信息: {result.get('error', 'Unknown error')}")
    
    # 获取服务统计
    stats = service.get_service_statistics()
    print(f"\n转接服务统计:")
    print(f"  在线客服: {stats['agents']['online_agents']} 人")
    print(f"  忙碌客服: {stats['agents']['busy_agents']} 人")
    print(f"  活跃通话: {stats['agents']['total_active_calls']} 个")
    print(f"  队列长度: {stats['queue']['total_requests']} 个")

def example_complete_workflow():
    """示例：完整的呼出转接流程"""
    print("\n=== 完整流程示例 ===")
    
    # 初始化服务
    intent_service = IntentService()
    transfer_service = TransferService()
    
    # 设置客服在线
    agent_manager = transfer_service.agent_manager
    agent_manager.update_agent_status("agent_001", AgentStatus.ONLINE)
    agent_manager.update_agent_status("agent_002", AgentStatus.ONLINE)
    
    # 模拟完整的客户服务流程
    session_id = "complete_workflow_demo"
    customer_phone = "13888888888"
    
    print(f"客户 {customer_phone} 来电...")
    
    # 1. 机器人问候
    print("\n1. 机器人问候阶段")
    robot_greeting = "您好，我是智能客服机器人，很高兴为您服务。请问有什么可以帮助您的吗？"
    print(f"机器人: {robot_greeting}")
    
    # 2. 客户回应和意图识别
    print("\n2. 客户回应和意图识别")
    customer_responses = [
        "你好，我想咨询一下产品",
        "你们的产品质量怎么样？",
        "价格有点贵啊",
        "算了，我还是找个人工客服吧"
    ]
    
    for i, response in enumerate(customer_responses):
        print(f"\n轮次 {i+1}:")
        print(f"客户: {response}")
        
        # 意图识别
        result = intent_service.process_user_input(session_id, response)
        intent_result = result['intent_result']
        should_transfer = result['should_transfer']
        
        print(f"系统分析: 意图={intent_result['intent']}, 情感={intent_result['emotion']}, 转人工={should_transfer}")
        
        if should_transfer:
            print("机器人: 好的，我现在为您转接人工客服，请稍等片刻。")
            break
        else:
            # 机器人回复（简化）
            robot_responses = {
                'PRODUCT_INQUIRY': "我们的产品具有智能化、高效率的特点，可以大大提升您的工作效率。",
                'COMPLAINT': "非常抱歉给您带来不便，让我为您详细了解一下具体情况。"
            }
            robot_reply = robot_responses.get(intent_result['intent'], "我理解您的需求，让我为您详细介绍。")
            print(f"机器人: {robot_reply}")
    
    # 3. 转接人工客服
    if should_transfer:
        print("\n3. 转接人工客服")
        
        transfer_result = transfer_service.request_transfer(
            session_id=session_id,
            customer_phone=customer_phone,
            customer_priority="NORMAL",
            required_skills=["general"]
        )
        
        if transfer_result['status'] == 'success':
            print(f"✓ 转接成功!")
            print(f"  客服: {transfer_result['agent_name']}")
            print(f"  分机: {transfer_result['agent_extension']}")
            print(f"客服 {transfer_result['agent_name']}: 您好，我是人工客服，刚才机器人已经为我介绍了您的情况，请问还有什么可以帮助您的？")
        else:
            print(f"✗ 转接失败: {transfer_result.get('error', 'Unknown error')}")
    
    print("\n流程演示完成!")

def main():
    """主函数"""
    print("FreeSwitch 机器人呼出系统 - 使用示例")
    print("=" * 60)
    
    # 确保日志目录存在
    os.makedirs("logs", exist_ok=True)
    
    try:
        # 运行各个示例
        example_outbound_calls()
        example_intent_recognition()
        example_agent_management()
        example_complete_workflow()
        
        print("\n" + "=" * 60)
        print("所有示例运行完成!")
        print("查看 logs/ 目录下的日志文件获取详细信息。")
        
    except Exception as e:
        print(f"示例运行异常: {str(e)}")
        return 1
    
    return 0

if __name__ == "__main__":
    sys.exit(main())