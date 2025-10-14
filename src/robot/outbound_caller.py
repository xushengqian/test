#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FreeSwitch 机器人呼出控制器
负责发起机器人呼出通话
"""

import time
import json
import logging
import threading
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any
import requests
import sqlite3
import os

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('logs/outbound_caller.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


class FreeSwitchAPI:
    """FreeSwitch API 接口封装"""
    
    def __init__(self, host: str = "localhost", port: int = 8080, 
                 username: str = "freeswitch", password: str = "works"):
        self.host = host
        self.port = port
        self.username = username
        self.password = password
        self.base_url = f"http://{host}:{port}/api"
        
    def execute_command(self, command: str, args: str = "") -> Optional[str]:
        """执行 FreeSwitch API 命令"""
        try:
            url = f"{self.base_url}/{command}"
            params = {"args": args} if args else {}
            
            response = requests.get(
                url, 
                params=params,
                auth=(self.username, self.password),
                timeout=30
            )
            
            if response.status_code == 200:
                return response.text
            else:
                logger.error(f"API 请求失败: {response.status_code} - {response.text}")
                return None
                
        except Exception as e:
            logger.error(f"API 请求异常: {str(e)}")
            return None
    
    def originate_call(self, destination: str, dialplan_context: str = "robot_outbound", 
                      variables: Dict[str, str] = None) -> Optional[str]:
        """发起呼出通话"""
        try:
            # 构建呼出命令
            call_vars = variables or {}
            var_string = ",".join([f"{k}={v}" for k, v in call_vars.items()])
            
            if var_string:
                originate_string = f"{{{var_string}}}user/{destination} &transfer(robot_call_{destination} XML {dialplan_context})"
            else:
                originate_string = f"user/{destination} &transfer(robot_call_{destination} XML {dialplan_context})"
            
            result = self.execute_command("originate", originate_string)
            
            if result and "SUCCESS" in result:
                logger.info(f"呼出成功: {destination}")
                return result
            else:
                logger.error(f"呼出失败: {destination} - {result}")
                return None
                
        except Exception as e:
            logger.error(f"呼出异常: {str(e)}")
            return None
    
    def get_channel_info(self, uuid: str) -> Optional[Dict]:
        """获取通道信息"""
        try:
            result = self.execute_command("uuid_dump", uuid)
            if result:
                # 解析通道信息
                info = {}
                for line in result.split('\n'):
                    if '=' in line:
                        key, value = line.split('=', 1)
                        info[key.strip()] = value.strip()
                return info
            return None
        except Exception as e:
            logger.error(f"获取通道信息异常: {str(e)}")
            return None


class CallDatabase:
    """通话数据库管理"""
    
    def __init__(self, db_path: str = "logs/calls.db"):
        self.db_path = db_path
        self.init_database()
    
    def init_database(self):
        """初始化数据库"""
        try:
            os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
            
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            # 创建通话记录表
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS call_records (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    session_id TEXT UNIQUE,
                    customer_phone TEXT,
                    call_direction TEXT,
                    call_status TEXT,
                    start_time DATETIME,
                    end_time DATETIME,
                    duration INTEGER,
                    transfer_to_agent BOOLEAN,
                    agent_id TEXT,
                    robot_turns INTEGER,
                    user_intents TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
            # 创建呼出任务表
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS outbound_tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    task_id TEXT UNIQUE,
                    customer_phone TEXT,
                    priority INTEGER DEFAULT 5,
                    max_attempts INTEGER DEFAULT 3,
                    current_attempts INTEGER DEFAULT 0,
                    status TEXT DEFAULT 'pending',
                    scheduled_time DATETIME,
                    last_attempt_time DATETIME,
                    campaign_id TEXT,
                    customer_data TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
            conn.commit()
            conn.close()
            
            logger.info("数据库初始化完成")
            
        except Exception as e:
            logger.error(f"数据库初始化失败: {str(e)}")
    
    def add_outbound_task(self, customer_phone: str, priority: int = 5, 
                         campaign_id: str = None, customer_data: Dict = None,
                         scheduled_time: datetime = None) -> str:
        """添加呼出任务"""
        try:
            task_id = f"TASK_{int(time.time())}_{customer_phone}"
            
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('''
                INSERT INTO outbound_tasks 
                (task_id, customer_phone, priority, campaign_id, customer_data, scheduled_time)
                VALUES (?, ?, ?, ?, ?, ?)
            ''', (
                task_id, customer_phone, priority, campaign_id,
                json.dumps(customer_data) if customer_data else None,
                scheduled_time or datetime.now()
            ))
            
            conn.commit()
            conn.close()
            
            logger.info(f"呼出任务已添加: {task_id}")
            return task_id
            
        except Exception as e:
            logger.error(f"添加呼出任务失败: {str(e)}")
            return None
    
    def get_pending_tasks(self, limit: int = 10) -> List[Dict]:
        """获取待处理的呼出任务"""
        try:
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('''
                SELECT * FROM outbound_tasks 
                WHERE status = 'pending' 
                AND current_attempts < max_attempts
                AND scheduled_time <= datetime('now')
                ORDER BY priority ASC, scheduled_time ASC
                LIMIT ?
            ''', (limit,))
            
            rows = cursor.fetchall()
            columns = [description[0] for description in cursor.description]
            
            tasks = []
            for row in rows:
                task = dict(zip(columns, row))
                if task['customer_data']:
                    task['customer_data'] = json.loads(task['customer_data'])
                tasks.append(task)
            
            conn.close()
            return tasks
            
        except Exception as e:
            logger.error(f"获取待处理任务失败: {str(e)}")
            return []
    
    def update_task_status(self, task_id: str, status: str, 
                          increment_attempts: bool = False):
        """更新任务状态"""
        try:
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            if increment_attempts:
                cursor.execute('''
                    UPDATE outbound_tasks 
                    SET status = ?, current_attempts = current_attempts + 1,
                        last_attempt_time = datetime('now'),
                        updated_at = datetime('now')
                    WHERE task_id = ?
                ''', (status, task_id))
            else:
                cursor.execute('''
                    UPDATE outbound_tasks 
                    SET status = ?, updated_at = datetime('now')
                    WHERE task_id = ?
                ''', (status, task_id))
            
            conn.commit()
            conn.close()
            
        except Exception as e:
            logger.error(f"更新任务状态失败: {str(e)}")
    
    def log_call_record(self, session_id: str, customer_phone: str, 
                       call_data: Dict):
        """记录通话信息"""
        try:
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('''
                INSERT OR REPLACE INTO call_records 
                (session_id, customer_phone, call_direction, call_status,
                 start_time, end_time, duration, transfer_to_agent, agent_id,
                 robot_turns, user_intents)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                session_id, customer_phone, call_data.get('call_direction', 'outbound'),
                call_data.get('call_status', 'completed'),
                call_data.get('start_time'), call_data.get('end_time'),
                call_data.get('duration', 0), call_data.get('transfer_to_agent', False),
                call_data.get('agent_id'), call_data.get('robot_turns', 0),
                json.dumps(call_data.get('user_intents', []))
            ))
            
            conn.commit()
            conn.close()
            
        except Exception as e:
            logger.error(f"记录通话信息失败: {str(e)}")


class OutboundCaller:
    """机器人呼出控制器"""
    
    def __init__(self, config_file: str = "config/outbound_config.json"):
        self.config = self.load_config(config_file)
        self.fs_api = FreeSwitchAPI(
            host=self.config.get('freeswitch_host', 'localhost'),
            port=self.config.get('freeswitch_port', 8080),
            username=self.config.get('freeswitch_user', 'freeswitch'),
            password=self.config.get('freeswitch_password', 'works')
        )
        self.db = CallDatabase()
        self.running = False
        self.worker_threads = []
        
    def load_config(self, config_file: str) -> Dict:
        """加载配置文件"""
        try:
            if os.path.exists(config_file):
                with open(config_file, 'r', encoding='utf-8') as f:
                    return json.load(f)
            else:
                # 默认配置
                default_config = {
                    "freeswitch_host": "localhost",
                    "freeswitch_port": 8080,
                    "freeswitch_user": "freeswitch",
                    "freeswitch_password": "works",
                    "max_concurrent_calls": 5,
                    "call_retry_interval": 300,
                    "max_retry_attempts": 3,
                    "working_hours": {
                        "start": "09:00",
                        "end": "18:00"
                    },
                    "blacklist_numbers": [],
                    "rate_limit": {
                        "calls_per_minute": 10,
                        "calls_per_hour": 100
                    }
                }
                
                # 保存默认配置
                os.makedirs(os.path.dirname(config_file), exist_ok=True)
                with open(config_file, 'w', encoding='utf-8') as f:
                    json.dump(default_config, f, indent=2, ensure_ascii=False)
                
                return default_config
                
        except Exception as e:
            logger.error(f"加载配置失败: {str(e)}")
            return {}
    
    def add_call_task(self, customer_phone: str, priority: int = 5,
                     campaign_id: str = None, customer_data: Dict = None,
                     scheduled_time: datetime = None) -> str:
        """添加呼出任务"""
        # 检查号码是否在黑名单中
        if customer_phone in self.config.get('blacklist_numbers', []):
            logger.warning(f"号码在黑名单中，跳过: {customer_phone}")
            return None
        
        # 验证号码格式
        if not self.validate_phone_number(customer_phone):
            logger.error(f"无效的电话号码: {customer_phone}")
            return None
        
        return self.db.add_outbound_task(
            customer_phone, priority, campaign_id, 
            customer_data, scheduled_time
        )
    
    def validate_phone_number(self, phone: str) -> bool:
        """验证电话号码格式"""
        # 简单的号码验证
        import re
        pattern = r'^1[3-9]\d{9}$'  # 中国手机号码格式
        return bool(re.match(pattern, phone))
    
    def is_working_hours(self) -> bool:
        """检查是否在工作时间内"""
        try:
            now = datetime.now()
            working_hours = self.config.get('working_hours', {})
            
            start_time = datetime.strptime(working_hours.get('start', '09:00'), '%H:%M').time()
            end_time = datetime.strptime(working_hours.get('end', '18:00'), '%H:%M').time()
            
            current_time = now.time()
            return start_time <= current_time <= end_time
            
        except Exception as e:
            logger.error(f"检查工作时间异常: {str(e)}")
            return True  # 默认允许呼出
    
    def make_outbound_call(self, task: Dict) -> bool:
        """执行呼出通话"""
        try:
            customer_phone = task['customer_phone']
            task_id = task['task_id']
            
            logger.info(f"开始呼出: {customer_phone} (任务: {task_id})")
            
            # 更新任务状态
            self.db.update_task_status(task_id, 'calling', increment_attempts=True)
            
            # 准备通话变量
            call_variables = {
                'customer_phone': customer_phone,
                'task_id': task_id,
                'campaign_id': task.get('campaign_id', ''),
                'customer_priority': self.get_customer_priority(customer_phone),
                'call_start_time': datetime.now().isoformat()
            }
            
            # 添加客户数据
            if task.get('customer_data'):
                for key, value in task['customer_data'].items():
                    call_variables[f'customer_{key}'] = str(value)
            
            # 发起呼出
            result = self.fs_api.originate_call(
                destination=customer_phone,
                dialplan_context="robot_outbound",
                variables=call_variables
            )
            
            if result:
                logger.info(f"呼出成功: {customer_phone}")
                self.db.update_task_status(task_id, 'in_progress')
                
                # 监控通话状态
                self.monitor_call_progress(task_id, customer_phone)
                
                return True
            else:
                logger.error(f"呼出失败: {customer_phone}")
                self.db.update_task_status(task_id, 'failed')
                return False
                
        except Exception as e:
            logger.error(f"呼出异常: {str(e)}")
            self.db.update_task_status(task['task_id'], 'failed')
            return False
    
    def get_customer_priority(self, customer_phone: str) -> str:
        """获取客户优先级"""
        # 这里可以根据客户数据库或规则确定优先级
        # 简化实现
        if customer_phone.startswith('138'):
            return 'VIP'
        elif customer_phone.startswith('139'):
            return 'HIGH'
        else:
            return 'NORMAL'
    
    def monitor_call_progress(self, task_id: str, customer_phone: str):
        """监控通话进度"""
        def monitor():
            try:
                # 等待通话结束的逻辑
                # 这里可以通过 FreeSwitch 事件系统或定期查询实现
                time.sleep(5)  # 简化实现
                
                # 模拟通话结束后的处理
                self.handle_call_completion(task_id, customer_phone)
                
            except Exception as e:
                logger.error(f"监控通话进度异常: {str(e)}")
        
        thread = threading.Thread(target=monitor)
        thread.daemon = True
        thread.start()
    
    def handle_call_completion(self, task_id: str, customer_phone: str):
        """处理通话完成"""
        try:
            # 这里应该从 FreeSwitch 获取通话结果
            # 简化实现，模拟通话结果
            call_result = {
                'call_status': 'completed',
                'duration': 120,
                'transfer_to_agent': False,
                'robot_turns': 5
            }
            
            # 记录通话信息
            session_id = f"SESSION_{task_id}"
            self.db.log_call_record(session_id, customer_phone, call_result)
            
            # 更新任务状态
            if call_result['call_status'] == 'completed':
                self.db.update_task_status(task_id, 'completed')
            else:
                self.db.update_task_status(task_id, 'failed')
            
            logger.info(f"通话完成: {customer_phone} (任务: {task_id})")
            
        except Exception as e:
            logger.error(f"处理通话完成异常: {str(e)}")
    
    def start_outbound_service(self):
        """启动呼出服务"""
        logger.info("启动机器人呼出服务")
        self.running = True
        
        # 启动工作线程
        max_workers = self.config.get('max_concurrent_calls', 5)
        for i in range(max_workers):
            worker = threading.Thread(target=self.worker_loop, args=(i,))
            worker.daemon = True
            worker.start()
            self.worker_threads.append(worker)
        
        logger.info(f"已启动 {max_workers} 个工作线程")
    
    def worker_loop(self, worker_id: int):
        """工作线程循环"""
        logger.info(f"工作线程 {worker_id} 已启动")
        
        while self.running:
            try:
                # 检查是否在工作时间内
                if not self.is_working_hours():
                    time.sleep(60)  # 非工作时间，等待1分钟
                    continue
                
                # 获取待处理任务
                tasks = self.db.get_pending_tasks(limit=1)
                
                if tasks:
                    task = tasks[0]
                    self.make_outbound_call(task)
                else:
                    time.sleep(10)  # 没有任务，等待10秒
                
            except Exception as e:
                logger.error(f"工作线程 {worker_id} 异常: {str(e)}")
                time.sleep(5)
        
        logger.info(f"工作线程 {worker_id} 已停止")
    
    def stop_outbound_service(self):
        """停止呼出服务"""
        logger.info("停止机器人呼出服务")
        self.running = False
        
        # 等待工作线程结束
        for worker in self.worker_threads:
            worker.join(timeout=10)
        
        logger.info("机器人呼出服务已停止")
    
    def get_statistics(self) -> Dict:
        """获取呼出统计信息"""
        try:
            conn = sqlite3.connect(self.db.db_path)
            cursor = conn.cursor()
            
            # 今日统计
            cursor.execute('''
                SELECT 
                    COUNT(*) as total_tasks,
                    SUM(CASE WHEN status = 'completed' THEN 1 ELSE 0 END) as completed,
                    SUM(CASE WHEN status = 'failed' THEN 1 ELSE 0 END) as failed,
                    SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) as pending
                FROM outbound_tasks 
                WHERE date(created_at) = date('now')
            ''')
            
            task_stats = cursor.fetchone()
            
            # 通话统计
            cursor.execute('''
                SELECT 
                    COUNT(*) as total_calls,
                    AVG(duration) as avg_duration,
                    SUM(CASE WHEN transfer_to_agent = 1 THEN 1 ELSE 0 END) as transfers
                FROM call_records 
                WHERE date(start_time) = date('now')
            ''')
            
            call_stats = cursor.fetchone()
            
            conn.close()
            
            return {
                'date': datetime.now().strftime('%Y-%m-%d'),
                'tasks': {
                    'total': task_stats[0] or 0,
                    'completed': task_stats[1] or 0,
                    'failed': task_stats[2] or 0,
                    'pending': task_stats[3] or 0
                },
                'calls': {
                    'total': call_stats[0] or 0,
                    'avg_duration': call_stats[1] or 0,
                    'transfers': call_stats[2] or 0
                }
            }
            
        except Exception as e:
            logger.error(f"获取统计信息失败: {str(e)}")
            return {}


def main():
    """主函数"""
    caller = OutboundCaller()
    
    try:
        # 启动呼出服务
        caller.start_outbound_service()
        
        # 保持运行
        while True:
            time.sleep(60)
            
            # 打印统计信息
            stats = caller.get_statistics()
            if stats:
                logger.info(f"今日统计: {stats}")
                
    except KeyboardInterrupt:
        logger.info("收到停止信号")
    finally:
        caller.stop_outbound_service()


if __name__ == "__main__":
    main()