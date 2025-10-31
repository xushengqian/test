#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
使用示例
"""

import json
import logging
from robot_call_handler import RobotCallHandler

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)

def example_single_call():
    """示例：单个外呼"""
    
    # 加载配置
    with open('config.json', 'r', encoding='utf-8') as f:
        config = json.load(f)
    
    # 创建处理器
    handler = RobotCallHandler(config)
    
    # 执行外呼
    target_phone = '13800138000'
    handler.run(target_phone)


def example_batch_call():
    """示例：批量外呼"""
    
    # 电话列表
    phone_list = [
        '13800138001',
        '13800138002',
        '13800138003',
    ]
    
    # 加载配置
    with open('config.json', 'r', encoding='utf-8') as f:
        config = json.load(f)
    
    # 依次外呼
    for phone in phone_list:
        print(f"\n{'='*50}")
        print(f"正在外呼: {phone}")
        print('='*50)
        
        handler = RobotCallHandler(config)
        handler.run(phone)


def example_custom_config():
    """示例：自定义配置"""
    
    # 自定义配置
    config = {
        'freeswitch_host': '127.0.0.1',
        'freeswitch_port': '8021',
        'freeswitch_password': 'ClueCon',
        'gateway': 'my_gateway',
        'caller_id': '10086',
        'agent_list': ['1001', '1002'],
        'agent_queue': 'vip_queue',  # 使用 VIP 队列
        'welcome_audio': '/path/to/custom_welcome.wav',
    }
    
    handler = RobotCallHandler(config)
    handler.run('13800138000')


if __name__ == '__main__':
    # 运行单个外呼示例
    example_single_call()
    
    # 运行批量外呼示例
    # example_batch_call()
    
    # 运行自定义配置示例
    # example_custom_config()
