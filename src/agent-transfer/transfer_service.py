#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
人工客服转接服务
负责管理人工客服状态和转接逻辑
"""

import time
import json
import logging
import threading
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any
from enum import Enum
import sqlite3
import os
import requests
from dataclasses import dataclass, asdict

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('logs/transfer_service.log', encoding='utf-8'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)


class AgentStatus(Enum):
    """客服状态枚举"""
    ONLINE = "online"
    BUSY = "busy"
    OFFLINE = "offline"
    BREAK = "break"
    AWAY = "away"


class TransferStatus(Enum):
    """转接状态枚举"""
    PENDING = "pending"
    CONNECTING = "connecting"
    CONNECTED = "connected"
    FAILED = "failed"
    TIMEOUT = "timeout"
    CANCELLED = "cancelled"


@dataclass
class Agent:
    """客服信息"""
    id: str
    name: str
    extension: str
    skills: List[str]
    max_concurrent_calls: int
    current_calls: int
    status: AgentStatus
    last_activity: datetime
    priority_level: int = 5  # 1-10, 数字越小优先级越高
    
    def to_dict(self) -> Dict:
        data = asdict(self)
        data['status'] = self.status.value
        data['last_activity'] = self.last_activity.isoformat()
        return data


@dataclass
class TransferRequest:
    """转接请求"""
    id: str
    session_id: str
    customer_phone: str
    customer_priority: str
    request_time: datetime
    assigned_agent_id: Optional[str] = None
    status: TransferStatus = TransferStatus.PENDING
    queue_position: int = 0
    estimated_wait_time: int = 0
    max_wait_time: int = 300  # 最大等待时间（秒）
    
    def to_dict(self) -> Dict:
        data = asdict(self)
        data['status'] = self.status.value
        data['request_time'] = self.request_time.isoformat()
        return data


class AgentManager:
    """客服管理器"""
    
    def __init__(self, db_path: str = "logs/agents.db"):
        self.db_path = db_path
        self.agents: Dict[str, Agent] = {}
        self.agent_lock = threading.Lock()
        self.init_database()
        self.load_agents()
        
        # 启动状态监控线程
        self.monitoring = True
        self.monitor_thread = threading.Thread(target=self.monitor_agents)
        self.monitor_thread.daemon = True
        self.monitor_thread.start()
    
    def init_database(self):
        """初始化数据库"""
        try:
            os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
            
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            # 创建客服表
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS agents (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    extension TEXT UNIQUE NOT NULL,
                    skills TEXT,
                    max_concurrent_calls INTEGER DEFAULT 3,
                    current_calls INTEGER DEFAULT 0,
                    status TEXT DEFAULT 'offline',
                    last_activity DATETIME DEFAULT CURRENT_TIMESTAMP,
                    priority_level INTEGER DEFAULT 5,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
            # 创建客服活动日志表
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS agent_activities (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    agent_id TEXT,
                    activity_type TEXT,
                    description TEXT,
                    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (agent_id) REFERENCES agents (id)
                )
            ''')
            
            conn.commit()
            conn.close()
            
            logger.info("客服数据库初始化完成")
            
        except Exception as e:
            logger.error(f"客服数据库初始化失败: {str(e)}")
    
    def load_agents(self):
        """从数据库加载客服信息"""
        try:
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('SELECT * FROM agents')
            rows = cursor.fetchall()
            columns = [description[0] for description in cursor.description]
            
            with self.agent_lock:
                self.agents.clear()
                
                for row in rows:
                    data = dict(zip(columns, row))
                    
                    agent = Agent(
                        id=data['id'],
                        name=data['name'],
                        extension=data['extension'],
                        skills=json.loads(data['skills']) if data['skills'] else [],
                        max_concurrent_calls=data['max_concurrent_calls'],
                        current_calls=data['current_calls'],
                        status=AgentStatus(data['status']),
                        last_activity=datetime.fromisoformat(data['last_activity']),
                        priority_level=data['priority_level']
                    )
                    
                    self.agents[agent.id] = agent
            
            conn.close()
            logger.info(f"已加载 {len(self.agents)} 个客服")
            
            # 如果没有客服，创建默认客服
            if not self.agents:
                self.create_default_agents()
                
        except Exception as e:
            logger.error(f"加载客服信息失败: {str(e)}")
            self.create_default_agents()
    
    def create_default_agents(self):
        """创建默认客服"""
        default_agents = [
            {
                'id': 'agent_001',
                'name': '客服小王',
                'extension': '1001',
                'skills': ['general', 'technical'],
                'max_concurrent_calls': 3,
                'priority_level': 3
            },
            {
                'id': 'agent_002',
                'name': '客服小李',
                'extension': '1002',
                'skills': ['general', 'complaint'],
                'max_concurrent_calls': 2,
                'priority_level': 4
            },
            {
                'id': 'agent_003',
                'name': '客服小张',
                'extension': '1003',
                'skills': ['technical', 'vip'],
                'max_concurrent_calls': 4,
                'priority_level': 2
            },
            {
                'id': 'agent_004',
                'name': '客服小陈',
                'extension': '1004',
                'skills': ['general'],
                'max_concurrent_calls': 2,
                'priority_level': 5
            },
            {
                'id': 'agent_005',
                'name': '客服小刘',
                'extension': '1005',
                'skills': ['vip', 'complaint'],
                'max_concurrent_calls': 3,
                'priority_level': 1
            }
        ]
        
        for agent_data in default_agents:
            self.add_agent(
                agent_id=agent_data['id'],
                name=agent_data['name'],
                extension=agent_data['extension'],
                skills=agent_data['skills'],
                max_concurrent_calls=agent_data['max_concurrent_calls'],
                priority_level=agent_data['priority_level']
            )
    
    def add_agent(self, agent_id: str, name: str, extension: str,
                  skills: List[str] = None, max_concurrent_calls: int = 3,
                  priority_level: int = 5) -> bool:
        """添加客服"""
        try:
            agent = Agent(
                id=agent_id,
                name=name,
                extension=extension,
                skills=skills or ['general'],
                max_concurrent_calls=max_concurrent_calls,
                current_calls=0,
                status=AgentStatus.OFFLINE,
                last_activity=datetime.now(),
                priority_level=priority_level
            )
            
            # 保存到数据库
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('''
                INSERT OR REPLACE INTO agents 
                (id, name, extension, skills, max_concurrent_calls, 
                 current_calls, status, last_activity, priority_level)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                agent.id, agent.name, agent.extension,
                json.dumps(agent.skills), agent.max_concurrent_calls,
                agent.current_calls, agent.status.value,
                agent.last_activity.isoformat(), agent.priority_level
            ))
            
            conn.commit()
            conn.close()
            
            # 添加到内存
            with self.agent_lock:
                self.agents[agent_id] = agent
            
            logger.info(f"客服已添加: {name} ({agent_id})")
            return True
            
        except Exception as e:
            logger.error(f"添加客服失败: {str(e)}")
            return False
    
    def update_agent_status(self, agent_id: str, status: AgentStatus) -> bool:
        """更新客服状态"""
        try:
            with self.agent_lock:
                if agent_id not in self.agents:
                    logger.warning(f"客服不存在: {agent_id}")
                    return False
                
                old_status = self.agents[agent_id].status
                self.agents[agent_id].status = status
                self.agents[agent_id].last_activity = datetime.now()
            
            # 更新数据库
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('''
                UPDATE agents 
                SET status = ?, last_activity = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            ''', (status.value, datetime.now().isoformat(), agent_id))
            
            # 记录活动日志
            cursor.execute('''
                INSERT INTO agent_activities (agent_id, activity_type, description)
                VALUES (?, ?, ?)
            ''', (agent_id, 'status_change', f'{old_status.value} -> {status.value}'))
            
            conn.commit()
            conn.close()
            
            logger.info(f"客服状态已更新: {agent_id} -> {status.value}")
            return True
            
        except Exception as e:
            logger.error(f"更新客服状态失败: {str(e)}")
            return False
    
    def get_available_agents(self, required_skills: List[str] = None,
                           customer_priority: str = "NORMAL") -> List[Agent]:
        """获取可用客服列表"""
        available = []
        
        with self.agent_lock:
            for agent in self.agents.values():
                # 检查状态
                if agent.status != AgentStatus.ONLINE:
                    continue
                
                # 检查负载
                if agent.current_calls >= agent.max_concurrent_calls:
                    continue
                
                # 检查技能匹配
                if required_skills:
                    if not any(skill in agent.skills for skill in required_skills):
                        continue
                
                # VIP客户优先匹配VIP技能的客服
                if customer_priority == "VIP":
                    if "vip" in agent.skills:
                        available.insert(0, agent)  # 插入到前面
                    else:
                        available.append(agent)
                else:
                    available.append(agent)
        
        # 按优先级和负载排序
        available.sort(key=lambda a: (a.priority_level, a.current_calls))
        
        return available
    
    def assign_call_to_agent(self, agent_id: str) -> bool:
        """为客服分配通话"""
        try:
            with self.agent_lock:
                if agent_id not in self.agents:
                    return False
                
                agent = self.agents[agent_id]
                if agent.current_calls >= agent.max_concurrent_calls:
                    return False
                
                agent.current_calls += 1
                agent.last_activity = datetime.now()
            
            # 更新数据库
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('''
                UPDATE agents 
                SET current_calls = current_calls + 1, 
                    last_activity = ?, 
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            ''', (datetime.now().isoformat(), agent_id))
            
            conn.commit()
            conn.close()
            
            return True
            
        except Exception as e:
            logger.error(f"分配通话失败: {str(e)}")
            return False
    
    def release_call_from_agent(self, agent_id: str) -> bool:
        """释放客服的通话"""
        try:
            with self.agent_lock:
                if agent_id not in self.agents:
                    return False
                
                agent = self.agents[agent_id]
                if agent.current_calls > 0:
                    agent.current_calls -= 1
                agent.last_activity = datetime.now()
            
            # 更新数据库
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('''
                UPDATE agents 
                SET current_calls = CASE 
                    WHEN current_calls > 0 THEN current_calls - 1 
                    ELSE 0 
                END,
                last_activity = ?,
                updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            ''', (datetime.now().isoformat(), agent_id))
            
            conn.commit()
            conn.close()
            
            return True
            
        except Exception as e:
            logger.error(f"释放通话失败: {str(e)}")
            return False
    
    def monitor_agents(self):
        """监控客服状态"""
        while self.monitoring:
            try:
                current_time = datetime.now()
                
                with self.agent_lock:
                    for agent in self.agents.values():
                        # 检查长时间无活动的客服
                        inactive_time = current_time - agent.last_activity
                        
                        if inactive_time > timedelta(minutes=30):
                            if agent.status == AgentStatus.ONLINE:
                                logger.warning(f"客服长时间无活动: {agent.name} ({inactive_time})")
                                # 可以发送提醒或自动设置为离开状态
                
                time.sleep(60)  # 每分钟检查一次
                
            except Exception as e:
                logger.error(f"监控客服状态异常: {str(e)}")
                time.sleep(60)
    
    def get_agent_statistics(self) -> Dict:
        """获取客服统计信息"""
        with self.agent_lock:
            total_agents = len(self.agents)
            online_agents = sum(1 for a in self.agents.values() if a.status == AgentStatus.ONLINE)
            busy_agents = sum(1 for a in self.agents.values() if a.status == AgentStatus.BUSY)
            total_calls = sum(a.current_calls for a in self.agents.values())
            
            return {
                'total_agents': total_agents,
                'online_agents': online_agents,
                'busy_agents': busy_agents,
                'offline_agents': total_agents - online_agents - busy_agents,
                'total_active_calls': total_calls,
                'timestamp': datetime.now().isoformat()
            }
    
    def stop_monitoring(self):
        """停止监控"""
        self.monitoring = False


class TransferQueue:
    """转接队列管理"""
    
    def __init__(self, db_path: str = "logs/transfer_queue.db"):
        self.db_path = db_path
        self.queue: List[TransferRequest] = []
        self.queue_lock = threading.Lock()
        self.init_database()
    
    def init_database(self):
        """初始化数据库"""
        try:
            os.makedirs(os.path.dirname(self.db_path), exist_ok=True)
            
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('''
                CREATE TABLE IF NOT EXISTS transfer_requests (
                    id TEXT PRIMARY KEY,
                    session_id TEXT,
                    customer_phone TEXT,
                    customer_priority TEXT,
                    request_time DATETIME,
                    assigned_agent_id TEXT,
                    status TEXT,
                    queue_position INTEGER,
                    estimated_wait_time INTEGER,
                    max_wait_time INTEGER,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            ''')
            
            conn.commit()
            conn.close()
            
        except Exception as e:
            logger.error(f"转接队列数据库初始化失败: {str(e)}")
    
    def add_transfer_request(self, session_id: str, customer_phone: str,
                           customer_priority: str = "NORMAL") -> str:
        """添加转接请求"""
        try:
            request_id = f"TRANSFER_{int(time.time())}_{session_id}"
            
            request = TransferRequest(
                id=request_id,
                session_id=session_id,
                customer_phone=customer_phone,
                customer_priority=customer_priority,
                request_time=datetime.now()
            )
            
            with self.queue_lock:
                # 根据优先级插入队列
                if customer_priority == "VIP":
                    # VIP客户插入到队列前面
                    vip_position = 0
                    for i, req in enumerate(self.queue):
                        if req.customer_priority != "VIP":
                            vip_position = i
                            break
                    else:
                        vip_position = len(self.queue)
                    
                    self.queue.insert(vip_position, request)
                else:
                    self.queue.append(request)
                
                # 更新队列位置
                self.update_queue_positions()
            
            # 保存到数据库
            self.save_transfer_request(request)
            
            logger.info(f"转接请求已添加: {request_id} (优先级: {customer_priority})")
            return request_id
            
        except Exception as e:
            logger.error(f"添加转接请求失败: {str(e)}")
            return None
    
    def update_queue_positions(self):
        """更新队列位置"""
        for i, request in enumerate(self.queue):
            request.queue_position = i + 1
            # 估算等待时间（简化计算）
            request.estimated_wait_time = i * 60  # 假设每个客户平均等待1分钟
    
    def get_next_request(self) -> Optional[TransferRequest]:
        """获取下一个转接请求"""
        with self.queue_lock:
            if self.queue:
                return self.queue[0]
            return None
    
    def remove_request(self, request_id: str) -> bool:
        """移除转接请求"""
        try:
            with self.queue_lock:
                for i, request in enumerate(self.queue):
                    if request.id == request_id:
                        self.queue.pop(i)
                        self.update_queue_positions()
                        break
                else:
                    return False
            
            # 更新数据库状态
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('''
                UPDATE transfer_requests 
                SET status = 'removed', updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
            ''', (request_id,))
            
            conn.commit()
            conn.close()
            
            return True
            
        except Exception as e:
            logger.error(f"移除转接请求失败: {str(e)}")
            return False
    
    def update_request_status(self, request_id: str, status: TransferStatus,
                            assigned_agent_id: str = None) -> bool:
        """更新请求状态"""
        try:
            with self.queue_lock:
                for request in self.queue:
                    if request.id == request_id:
                        request.status = status
                        if assigned_agent_id:
                            request.assigned_agent_id = assigned_agent_id
                        break
            
            # 更新数据库
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            if assigned_agent_id:
                cursor.execute('''
                    UPDATE transfer_requests 
                    SET status = ?, assigned_agent_id = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                ''', (status.value, assigned_agent_id, request_id))
            else:
                cursor.execute('''
                    UPDATE transfer_requests 
                    SET status = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                ''', (status.value, request_id))
            
            conn.commit()
            conn.close()
            
            return True
            
        except Exception as e:
            logger.error(f"更新请求状态失败: {str(e)}")
            return False
    
    def save_transfer_request(self, request: TransferRequest):
        """保存转接请求到数据库"""
        try:
            conn = sqlite3.connect(self.db_path)
            cursor = conn.cursor()
            
            cursor.execute('''
                INSERT OR REPLACE INTO transfer_requests 
                (id, session_id, customer_phone, customer_priority, request_time,
                 assigned_agent_id, status, queue_position, estimated_wait_time, max_wait_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ''', (
                request.id, request.session_id, request.customer_phone,
                request.customer_priority, request.request_time.isoformat(),
                request.assigned_agent_id, request.status.value,
                request.queue_position, request.estimated_wait_time, request.max_wait_time
            ))
            
            conn.commit()
            conn.close()
            
        except Exception as e:
            logger.error(f"保存转接请求失败: {str(e)}")
    
    def get_queue_status(self) -> Dict:
        """获取队列状态"""
        with self.queue_lock:
            return {
                'total_requests': len(self.queue),
                'vip_requests': sum(1 for r in self.queue if r.customer_priority == "VIP"),
                'normal_requests': sum(1 for r in self.queue if r.customer_priority == "NORMAL"),
                'avg_wait_time': sum(r.estimated_wait_time for r in self.queue) / max(len(self.queue), 1),
                'timestamp': datetime.now().isoformat()
            }


class TransferService:
    """转接服务主类"""
    
    def __init__(self, config_file: str = "config/transfer_config.json"):
        self.config = self.load_config(config_file)
        self.agent_manager = AgentManager()
        self.transfer_queue = TransferQueue()
        self.freeswitch_api = self.init_freeswitch_api()
        
        # 启动转接处理线程
        self.processing = True
        self.process_thread = threading.Thread(target=self.process_transfer_queue)
        self.process_thread.daemon = True
        self.process_thread.start()
    
    def load_config(self, config_file: str) -> Dict:
        """加载配置"""
        try:
            if os.path.exists(config_file):
                with open(config_file, 'r', encoding='utf-8') as f:
                    return json.load(f)
            else:
                default_config = {
                    "freeswitch": {
                        "host": "localhost",
                        "port": 8080,
                        "username": "freeswitch",
                        "password": "works"
                    },
                    "transfer": {
                        "max_wait_time": 300,
                        "retry_interval": 30,
                        "max_retries": 3
                    },
                    "queue": {
                        "vip_priority": True,
                        "max_queue_size": 50
                    }
                }
                
                os.makedirs(os.path.dirname(config_file), exist_ok=True)
                with open(config_file, 'w', encoding='utf-8') as f:
                    json.dump(default_config, f, indent=2, ensure_ascii=False)
                
                return default_config
        except Exception as e:
            logger.error(f"加载配置失败: {str(e)}")
            return {}
    
    def init_freeswitch_api(self):
        """初始化 FreeSwitch API"""
        # 这里应该初始化 FreeSwitch API 连接
        # 简化实现，返回模拟对象
        class MockFreeSwitchAPI:
            def transfer_call(self, session_id: str, agent_extension: str) -> bool:
                logger.info(f"模拟转接通话: {session_id} -> {agent_extension}")
                return True
        
        return MockFreeSwitchAPI()
    
    def request_transfer(self, session_id: str, customer_phone: str,
                        customer_priority: str = "NORMAL",
                        required_skills: List[str] = None) -> Dict:
        """请求转接人工客服"""
        try:
            logger.info(f"收到转接请求: {session_id} ({customer_priority})")
            
            # 检查是否有可用客服
            available_agents = self.agent_manager.get_available_agents(
                required_skills, customer_priority
            )
            
            if available_agents:
                # 立即分配客服
                selected_agent = available_agents[0]
                
                # 分配通话
                if self.agent_manager.assign_call_to_agent(selected_agent.id):
                    # 执行转接
                    success = self.freeswitch_api.transfer_call(session_id, selected_agent.extension)
                    
                    if success:
                        logger.info(f"转接成功: {session_id} -> {selected_agent.name}")
                        return {
                            'status': 'success',
                            'transfer_type': 'immediate',
                            'agent_id': selected_agent.id,
                            'agent_name': selected_agent.name,
                            'agent_extension': selected_agent.extension,
                            'wait_time': 0
                        }
                    else:
                        # 转接失败，释放分配
                        self.agent_manager.release_call_from_agent(selected_agent.id)
            
            # 没有可用客服，加入队列
            request_id = self.transfer_queue.add_transfer_request(
                session_id, customer_phone, customer_priority
            )
            
            if request_id:
                queue_status = self.transfer_queue.get_queue_status()
                
                return {
                    'status': 'queued',
                    'transfer_type': 'queued',
                    'request_id': request_id,
                    'queue_position': len(self.transfer_queue.queue),
                    'estimated_wait_time': queue_status['avg_wait_time'],
                    'message': '正在为您安排客服，请稍候...'
                }
            else:
                return {
                    'status': 'failed',
                    'error': '无法加入转接队列'
                }
                
        except Exception as e:
            logger.error(f"转接请求处理失败: {str(e)}")
            return {
                'status': 'error',
                'error': str(e)
            }
    
    def process_transfer_queue(self):
        """处理转接队列"""
        while self.processing:
            try:
                # 获取下一个请求
                request = self.transfer_queue.get_next_request()
                
                if not request:
                    time.sleep(5)  # 没有请求，等待5秒
                    continue
                
                # 检查请求是否超时
                wait_time = (datetime.now() - request.request_time).total_seconds()
                if wait_time > request.max_wait_time:
                    logger.warning(f"转接请求超时: {request.id}")
                    self.transfer_queue.update_request_status(request.id, TransferStatus.TIMEOUT)
                    self.transfer_queue.remove_request(request.id)
                    continue
                
                # 查找可用客服
                available_agents = self.agent_manager.get_available_agents(
                    customer_priority=request.customer_priority
                )
                
                if available_agents:
                    selected_agent = available_agents[0]
                    
                    # 更新请求状态
                    self.transfer_queue.update_request_status(
                        request.id, TransferStatus.CONNECTING, selected_agent.id
                    )
                    
                    # 分配通话
                    if self.agent_manager.assign_call_to_agent(selected_agent.id):
                        # 执行转接
                        success = self.freeswitch_api.transfer_call(
                            request.session_id, selected_agent.extension
                        )
                        
                        if success:
                            logger.info(f"队列转接成功: {request.id} -> {selected_agent.name}")
                            self.transfer_queue.update_request_status(
                                request.id, TransferStatus.CONNECTED
                            )
                            self.transfer_queue.remove_request(request.id)
                        else:
                            logger.error(f"队列转接失败: {request.id}")
                            self.agent_manager.release_call_from_agent(selected_agent.id)
                            self.transfer_queue.update_request_status(
                                request.id, TransferStatus.FAILED
                            )
                    else:
                        logger.error(f"客服分配失败: {selected_agent.id}")
                        self.transfer_queue.update_request_status(
                            request.id, TransferStatus.FAILED
                        )
                else:
                    # 没有可用客服，继续等待
                    time.sleep(10)
                
            except Exception as e:
                logger.error(f"处理转接队列异常: {str(e)}")
                time.sleep(5)
    
    def cancel_transfer_request(self, request_id: str) -> bool:
        """取消转接请求"""
        return self.transfer_queue.remove_request(request_id)
    
    def get_transfer_status(self, request_id: str) -> Optional[Dict]:
        """获取转接状态"""
        with self.transfer_queue.queue_lock:
            for request in self.transfer_queue.queue:
                if request.id == request_id:
                    return request.to_dict()
        return None
    
    def get_service_statistics(self) -> Dict:
        """获取服务统计"""
        agent_stats = self.agent_manager.get_agent_statistics()
        queue_stats = self.transfer_queue.get_queue_status()
        
        return {
            'agents': agent_stats,
            'queue': queue_stats,
            'timestamp': datetime.now().isoformat()
        }
    
    def stop_service(self):
        """停止服务"""
        logger.info("停止转接服务")
        self.processing = False
        self.agent_manager.stop_monitoring()
        
        if self.process_thread.is_alive():
            self.process_thread.join(timeout=10)


def main():
    """测试主函数"""
    service = TransferService()
    
    try:
        # 模拟客服上线
        service.agent_manager.update_agent_status("agent_001", AgentStatus.ONLINE)
        service.agent_manager.update_agent_status("agent_002", AgentStatus.ONLINE)
        service.agent_manager.update_agent_status("agent_003", AgentStatus.ONLINE)
        
        # 模拟转接请求
        result1 = service.request_transfer("session_001", "13800138000", "VIP")
        print(f"转接结果1: {json.dumps(result1, indent=2, ensure_ascii=False)}")
        
        result2 = service.request_transfer("session_002", "13900139000", "NORMAL")
        print(f"转接结果2: {json.dumps(result2, indent=2, ensure_ascii=False)}")
        
        # 获取统计信息
        stats = service.get_service_statistics()
        print(f"服务统计: {json.dumps(stats, indent=2, ensure_ascii=False)}")
        
        # 保持运行一段时间
        time.sleep(30)
        
    except KeyboardInterrupt:
        logger.info("收到停止信号")
    finally:
        service.stop_service()


if __name__ == "__main__":
    main()