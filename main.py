#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FreeSwitch 机器人呼出系统主程序
整合机器人呼出、意图识别和人工转接功能
"""

import os
import sys
import time
import json
import signal
import logging
import argparse
import threading
from datetime import datetime
from typing import Dict, List, Optional

# 添加项目路径
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from src.robot.outbound_caller import OutboundCaller
from src.intent_recognition.intent_service import IntentService
from src.agent_transfer.transfer_service import TransferService

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('logs/main.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


class FreeSwitchRobotSystem:
    """FreeSwitch 机器人系统主类"""
    
    def __init__(self, config_dir: str = "config"):
        self.config_dir = config_dir
        self.running = False
        
        # 初始化各个服务
        logger.info("初始化 FreeSwitch 机器人系统...")
        
        try:
            # 初始化呼出服务
            self.outbound_caller = OutboundCaller(
                os.path.join(config_dir, "outbound_config.json")
            )
            
            # 初始化意图识别服务
            self.intent_service = IntentService()
            
            # 初始化转接服务
            self.transfer_service = TransferService(
                os.path.join(config_dir, "transfer_config.json")
            )
            
            logger.info("所有服务初始化完成")
            
        except Exception as e:
            logger.error(f"系统初始化失败: {str(e)}")
            raise
    
    def start_system(self):
        """启动系统"""
        try:
            logger.info("启动 FreeSwitch 机器人系统")
            self.running = True
            
            # 启动呼出服务
            self.outbound_caller.start_outbound_service()
            
            # 转接服务已在初始化时启动
            
            logger.info("系统启动完成")
            
            # 启动监控线程
            monitor_thread = threading.Thread(target=self.monitor_system)
            monitor_thread.daemon = True
            monitor_thread.start()
            
        except Exception as e:
            logger.error(f"系统启动失败: {str(e)}")
            raise
    
    def stop_system(self):
        """停止系统"""
        try:
            logger.info("停止 FreeSwitch 机器人系统")
            self.running = False
            
            # 停止呼出服务
            self.outbound_caller.stop_outbound_service()
            
            # 停止转接服务
            self.transfer_service.stop_service()
            
            logger.info("系统已停止")
            
        except Exception as e:
            logger.error(f"系统停止异常: {str(e)}")
    
    def monitor_system(self):
        """系统监控"""
        logger.info("启动系统监控")
        
        while self.running:
            try:
                # 获取各服务统计信息
                outbound_stats = self.outbound_caller.get_statistics()
                intent_stats = self.intent_service.get_statistics()
                transfer_stats = self.transfer_service.get_service_statistics()
                
                # 记录统计信息
                stats_summary = {
                    'timestamp': datetime.now().isoformat(),
                    'outbound': outbound_stats,
                    'intent': intent_stats,
                    'transfer': transfer_stats
                }
                
                logger.info(f"系统统计: {json.dumps(stats_summary, ensure_ascii=False)}")
                
                # 检查系统健康状态
                self.check_system_health(stats_summary)
                
                # 每5分钟监控一次
                time.sleep(300)
                
            except Exception as e:
                logger.error(f"系统监控异常: {str(e)}")
                time.sleep(60)
    
    def check_system_health(self, stats: Dict):
        """检查系统健康状态"""
        try:
            # 检查呼出服务
            outbound_stats = stats.get('outbound', {})
            if outbound_stats:
                failed_rate = outbound_stats.get('tasks', {}).get('failed', 0)
                total_tasks = outbound_stats.get('tasks', {}).get('total', 0)
                
                if total_tasks > 0 and failed_rate / total_tasks > 0.5:
                    logger.warning("呼出服务失败率过高")
            
            # 检查转接服务
            transfer_stats = stats.get('transfer', {})
            if transfer_stats:
                queue_length = transfer_stats.get('queue', {}).get('total_requests', 0)
                if queue_length > 10:
                    logger.warning(f"转接队列过长: {queue_length}")
                
                online_agents = transfer_stats.get('agents', {}).get('online_agents', 0)
                if online_agents == 0:
                    logger.warning("没有在线客服")
            
        except Exception as e:
            logger.error(f"健康检查异常: {str(e)}")
    
    def add_outbound_task(self, customer_phone: str, priority: int = 5,
                         campaign_id: str = None, customer_data: Dict = None,
                         scheduled_time: datetime = None) -> str:
        """添加呼出任务"""
        return self.outbound_caller.add_call_task(
            customer_phone, priority, campaign_id, customer_data, scheduled_time
        )
    
    def process_user_intent(self, session_id: str, user_input: str,
                           context: Dict = None) -> Dict:
        """处理用户意图"""
        return self.intent_service.process_user_input(session_id, user_input, context)
    
    def request_agent_transfer(self, session_id: str, customer_phone: str,
                             customer_priority: str = "NORMAL",
                             required_skills: List[str] = None) -> Dict:
        """请求转接人工"""
        return self.transfer_service.request_transfer(
            session_id, customer_phone, customer_priority, required_skills
        )
    
    def get_system_status(self) -> Dict:
        """获取系统状态"""
        return {
            'running': self.running,
            'outbound_stats': self.outbound_caller.get_statistics(),
            'intent_stats': self.intent_service.get_statistics(),
            'transfer_stats': self.transfer_service.get_service_statistics(),
            'timestamp': datetime.now().isoformat()
        }


def signal_handler(signum, frame):
    """信号处理器"""
    logger.info(f"收到信号 {signum}，准备停止系统")
    if 'robot_system' in globals():
        robot_system.stop_system()
    sys.exit(0)


def create_sample_tasks(system: FreeSwitchRobotSystem):
    """创建示例任务"""
    sample_phones = [
        "13800138001",
        "13900139001", 
        "13700137001",
        "13600136001",
        "13500135001"
    ]
    
    for i, phone in enumerate(sample_phones):
        priority = 1 if i == 0 else 5  # 第一个设为VIP
        campaign_id = "CAMPAIGN_001"
        customer_data = {
            "name": f"客户{i+1}",
            "level": "VIP" if i == 0 else "NORMAL",
            "last_contact": "2024-01-01"
        }
        
        task_id = system.add_outbound_task(
            phone, priority, campaign_id, customer_data
        )
        
        if task_id:
            logger.info(f"创建示例任务: {task_id} -> {phone}")


def main():
    """主函数"""
    parser = argparse.ArgumentParser(description='FreeSwitch 机器人呼出系统')
    parser.add_argument('--config-dir', default='config', help='配置文件目录')
    parser.add_argument('--create-samples', action='store_true', help='创建示例任务')
    parser.add_argument('--daemon', action='store_true', help='后台运行')
    
    args = parser.parse_args()
    
    # 注册信号处理器
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    try:
        # 创建系统实例
        global robot_system
        robot_system = FreeSwitchRobotSystem(args.config_dir)
        
        # 启动系统
        robot_system.start_system()
        
        # 创建示例任务
        if args.create_samples:
            create_sample_tasks(robot_system)
        
        logger.info("系统运行中... 按 Ctrl+C 停止")
        
        if args.daemon:
            # 后台运行
            while robot_system.running:
                time.sleep(60)
        else:
            # 交互模式
            while robot_system.running:
                try:
                    command = input("\n请输入命令 (status/add/quit): ").strip().lower()
                    
                    if command == 'quit' or command == 'q':
                        break
                    elif command == 'status' or command == 's':
                        status = robot_system.get_system_status()
                        print(json.dumps(status, indent=2, ensure_ascii=False))
                    elif command == 'add' or command == 'a':
                        phone = input("请输入电话号码: ").strip()
                        if phone:
                            task_id = robot_system.add_outbound_task(phone)
                            if task_id:
                                print(f"任务已添加: {task_id}")
                            else:
                                print("添加任务失败")
                    else:
                        print("未知命令，请输入 status/add/quit")
                        
                except (EOFError, KeyboardInterrupt):
                    break
                except Exception as e:
                    logger.error(f"命令处理异常: {str(e)}")
        
    except Exception as e:
        logger.error(f"系统运行异常: {str(e)}")
        return 1
    finally:
        if 'robot_system' in globals():
            robot_system.stop_system()
    
    return 0


if __name__ == "__main__":
    sys.exit(main())