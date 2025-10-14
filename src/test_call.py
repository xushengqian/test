#!/usr/bin/env python3
"""
测试外呼脚本
用于测试机器人呼出功能
"""
import argparse
import requests
import json
import time
import sys
from pathlib import Path

# API 基础地址
API_BASE_URL = "http://localhost:5000"

def make_call(phone_number: str):
    """
    发起测试呼叫
    
    Args:
        phone_number: 电话号码
    """
    print(f"正在呼叫: {phone_number}")
    
    # 调用 API
    url = f"{API_BASE_URL}/api/call/make"
    data = {
        "phone_number": phone_number
    }
    
    try:
        response = requests.post(url, json=data)
        result = response.json()
        
        if result['success']:
            call_id = result['call_id']
            print(f"✓ 呼叫成功发起")
            print(f"  通话ID: {call_id}")
            
            # 监控通话状态
            monitor_call(call_id)
            
        else:
            print(f"✗ 呼叫失败: {result['message']}")
            
    except Exception as e:
        print(f"✗ 请求失败: {str(e)}")

def monitor_call(call_id: str):
    """
    监控通话状态
    
    Args:
        call_id: 通话ID
    """
    print("\n监控通话状态...")
    print("-" * 40)
    
    last_status = None
    
    while True:
        try:
            # 获取状态
            url = f"{API_BASE_URL}/api/call/status/{call_id}"
            response = requests.get(url)
            
            if response.status_code == 200:
                result = response.json()
                
                if result['success']:
                    data = result['data']
                    status = data['status']
                    
                    # 状态变化时打印
                    if status != last_status:
                        timestamp = time.strftime("%H:%M:%S")
                        print(f"[{timestamp}] 状态: {status}")
                        
                        if status == 'answered':
                            print("  ✓ 用户已接听")
                        elif status == 'transferring':
                            print("  ➜ 正在转接人工...")
                        elif status == 'transferred':
                            agent = data.get('agent_number', '未知')
                            print(f"  ✓ 已转接到坐席: {agent}")
                        elif status == 'completed':
                            duration = data.get('duration', 0)
                            print(f"  ✓ 通话结束，时长: {duration}秒")
                            
                            # 打印统计信息
                            print_call_summary(data)
                            break
                            
                        last_status = status
                        
            time.sleep(2)  # 每2秒检查一次
            
        except KeyboardInterrupt:
            print("\n监控已停止")
            break
        except Exception as e:
            print(f"监控出错: {str(e)}")
            break

def print_call_summary(call_data: dict):
    """
    打印通话摘要
    
    Args:
        call_data: 通话数据
    """
    print("\n" + "=" * 40)
    print("通话摘要")
    print("=" * 40)
    
    print(f"通话ID: {call_data.get('call_id', 'N/A')}")
    print(f"电话号码: {call_data.get('phone_number', 'N/A')}")
    print(f"通话时长: {call_data.get('duration', 0)}秒")
    print(f"是否转人工: {'是' if call_data.get('transfer_to_agent') else '否'}")
    
    if call_data.get('transfer_to_agent'):
        print(f"坐席号码: {call_data.get('agent_number', 'N/A')}")
    
    print(f"录音文件: {call_data.get('recording_path', '无')}")

def test_intent_detection():
    """测试意图识别"""
    from intent_detector import intent_detector
    
    print("\n测试意图识别")
    print("-" * 40)
    
    test_cases = [
        "我要转人工",
        "请帮我转接客服",
        "给我找个真人",
        "不要机器人",
        "我有投诉",
        "你好",
        "查询订单",
        "谢谢",
        "再见"
    ]
    
    for text in test_cases:
        intent, confidence, _ = intent_detector.detect_intent(text)
        should_transfer = intent_detector.should_transfer(text)
        
        print(f"输入: {text}")
        print(f"  意图: {intent}")
        print(f"  置信度: {confidence:.2f}")
        print(f"  转人工: {'是' if should_transfer else '否'}")
        print()

def check_system_status():
    """检查系统状态"""
    print("\n系统状态检查")
    print("-" * 40)
    
    # 检查健康状态
    try:
        response = requests.get(f"{API_BASE_URL}/health")
        if response.status_code == 200:
            print("✓ Web 服务正常")
        else:
            print("✗ Web 服务异常")
    except:
        print("✗ 无法连接到 Web 服务")
    
    # 检查队列状态
    try:
        response = requests.get(f"{API_BASE_URL}/api/queue/status")
        if response.status_code == 200:
            data = response.json()['data']
            print(f"✓ 队列状态:")
            print(f"  - 队列长度: {data['queue_length']}")
            print(f"  - 在线坐席: {data['online_agents']}")
            print(f"  - 可用坐席: {data['available_agents']}")
    except:
        print("✗ 无法获取队列状态")

def update_agent_status(agent_number: str, status: str):
    """
    更新坐席状态
    
    Args:
        agent_number: 坐席号码
        status: 状态
    """
    url = f"{API_BASE_URL}/api/agent/status/{agent_number}"
    data = {"status": status}
    
    try:
        response = requests.post(url, json=data)
        result = response.json()
        
        if result['success']:
            print(f"✓ 坐席 {agent_number} 状态已更新为: {status}")
        else:
            print(f"✗ 更新失败: {result['message']}")
            
    except Exception as e:
        print(f"✗ 请求失败: {str(e)}")

def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='测试 FreeSWITCH 机器人呼出系统')
    
    subparsers = parser.add_subparsers(dest='command', help='命令')
    
    # 呼叫命令
    call_parser = subparsers.add_parser('call', help='发起测试呼叫')
    call_parser.add_argument('number', help='电话号码')
    
    # 意图测试命令
    intent_parser = subparsers.add_parser('intent', help='测试意图识别')
    
    # 状态检查命令
    status_parser = subparsers.add_parser('status', help='检查系统状态')
    
    # 坐席状态命令
    agent_parser = subparsers.add_parser('agent', help='更新坐席状态')
    agent_parser.add_argument('number', help='坐席号码')
    agent_parser.add_argument('status', choices=['online', 'offline', 'busy', 'break', 'available'], 
                             help='状态')
    
    args = parser.parse_args()
    
    if args.command == 'call':
        make_call(args.number)
    elif args.command == 'intent':
        test_intent_detection()
    elif args.command == 'status':
        check_system_status()
    elif args.command == 'agent':
        update_agent_status(args.number, args.status)
    else:
        parser.print_help()

if __name__ == "__main__":
    main()