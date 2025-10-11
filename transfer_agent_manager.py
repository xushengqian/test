#!/usr/bin/env python3
"""
FreeSWITCH 转人工功能管理器
用于监控、测试和管理转人工功能
"""

import json
import logging
import datetime
import sqlite3
from typing import Dict, List, Optional
import ESL  # FreeSWITCH ESL库

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class TransferAgentManager:
    """转人工功能管理器"""
    
    def __init__(self, host='127.0.0.1', port=8021, password='ClueCon'):
        """
        初始化管理器
        
        Args:
            host: FreeSWITCH ESL主机地址
            port: ESL端口
            password: ESL密码
        """
        self.host = host
        self.port = port
        self.password = password
        self.connection = None
        self.db_path = '/var/lib/freeswitch/transfer_agent.db'
        self._init_database()
    
    def _init_database(self):
        """初始化数据库"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        # 创建转人工日志表
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS transfer_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid TEXT,
                caller_id TEXT,
                transfer_reason TEXT,
                agent_id TEXT,
                status TEXT,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        ''')
        
        # 创建坐席状态表
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS agent_status (
                agent_id TEXT PRIMARY KEY,
                status TEXT,
                last_call_time DATETIME,
                calls_today INTEGER DEFAULT 0,
                avg_handle_time INTEGER DEFAULT 0,
                updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
        ''')
        
        # 创建回调请求表
        cursor.execute('''
            CREATE TABLE IF NOT EXISTS callback_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                phone_number TEXT,
                request_time DATETIME DEFAULT CURRENT_TIMESTAMP,
                priority INTEGER DEFAULT 3,
                status TEXT DEFAULT 'pending',
                callback_time DATETIME,
                agent_id TEXT
            )
        ''')
        
        conn.commit()
        conn.close()
    
    def connect(self) -> bool:
        """连接到FreeSWITCH ESL"""
        try:
            self.connection = ESL.ESLconnection(self.host, str(self.port), self.password)
            
            if self.connection.connected():
                logger.info(f"成功连接到FreeSWITCH ESL {self.host}:{self.port}")
                
                # 订阅事件
                self.connection.events('plain', 'CUSTOM agent::login agent::notification callback::request')
                return True
            else:
                logger.error("无法连接到FreeSWITCH ESL")
                return False
        except Exception as e:
            logger.error(f"ESL连接错误: {e}")
            return False
    
    def disconnect(self):
        """断开ESL连接"""
        if self.connection:
            self.connection.disconnect()
            logger.info("已断开ESL连接")
    
    def get_agent_status_list(self) -> List[Dict]:
        """获取所有坐席状态列表"""
        if not self.connection or not self.connection.connected():
            logger.error("未连接到FreeSWITCH")
            return []
        
        agents = []
        
        # 获取所有坐席
        for i in range(1, 6):  # 假设有5个坐席
            agent_id = f"agent_{i:03d}"
            
            # 获取坐席状态
            status_cmd = f"global_getvar agent_status_{agent_id}"
            result = self.connection.api(status_cmd)
            
            if result:
                status_code = result.getBody()
                status = self._parse_agent_status(status_code)
                
                # 获取坐席信息
                agent_info = {
                    'agent_id': agent_id,
                    'status': status,
                    'extension': f"100{i}",
                    'calls_today': self._get_agent_calls_today(agent_id),
                    'current_call': self._get_agent_current_call(agent_id)
                }
                
                agents.append(agent_info)
        
        return agents
    
    def _parse_agent_status(self, status_code: str) -> str:
        """解析坐席状态码"""
        status_map = {
            '0': '离线',
            '1': '登录',
            '2': '空闲',
            '3': '通话中',
            '4': '话后处理',
            '5': '休息中'
        }
        return status_map.get(status_code, '未知')
    
    def _get_agent_calls_today(self, agent_id: str) -> int:
        """获取坐席今日通话数量"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        today = datetime.date.today()
        cursor.execute('''
            SELECT COUNT(*) FROM transfer_logs 
            WHERE agent_id = ? AND DATE(timestamp) = ?
        ''', (agent_id, today))
        
        count = cursor.fetchone()[0]
        conn.close()
        
        return count
    
    def _get_agent_current_call(self, agent_id: str) -> Optional[Dict]:
        """获取坐席当前通话信息"""
        if not self.connection or not self.connection.connected():
            return None
        
        # 获取通道信息
        cmd = f"show channels like {agent_id}"
        result = self.connection.api(cmd)
        
        if result:
            body = result.getBody()
            if body and agent_id in body:
                # 解析通话信息
                return {
                    'active': True,
                    'duration': self._parse_call_duration(body)
                }
        
        return None
    
    def _parse_call_duration(self, channel_info: str) -> int:
        """解析通话时长"""
        # 简化处理，实际需要解析channel信息
        import random
        return random.randint(0, 300)
    
    def get_queue_status(self) -> Dict:
        """获取队列状态"""
        if not self.connection or not self.connection.connected():
            logger.error("未连接到FreeSWITCH")
            return {}
        
        queues = {}
        queue_names = ['agent_queue', 'sales_queue', 'support_queue', 'complaint_queue', 'vip_queue']
        
        for queue_name in queue_names:
            cmd = f"fifo list {queue_name}"
            result = self.connection.api(cmd)
            
            if result:
                body = result.getBody()
                queue_info = self._parse_queue_info(body)
                queues[queue_name] = queue_info
        
        return queues
    
    def _parse_queue_info(self, fifo_info: str) -> Dict:
        """解析FIFO队列信息"""
        info = {
            'waiting': 0,
            'agents': 0,
            'calls_served': 0
        }
        
        if not fifo_info:
            return info
        
        lines = fifo_info.split('\n')
        for line in lines:
            if 'waiting' in line.lower():
                info['waiting'] += 1
            elif 'agent' in line.lower():
                info['agents'] += 1
        
        return info
    
    def trigger_transfer_test(self, caller_id: str, reason: str = 'test') -> bool:
        """
        触发转人工测试
        
        Args:
            caller_id: 主叫号码
            reason: 转人工原因
        
        Returns:
            是否成功触发
        """
        if not self.connection or not self.connection.connected():
            logger.error("未连接到FreeSWITCH")
            return False
        
        try:
            # 创建测试呼叫
            cmd = f"originate {{transfer_reason={reason},caller_id_number={caller_id}}}loopback/detect_intent/transfer_to_agent &park()"
            result = self.connection.api(cmd)
            
            if result:
                logger.info(f"成功触发转人工测试: {caller_id} - {reason}")
                return True
            else:
                logger.error("触发转人工测试失败")
                return False
        except Exception as e:
            logger.error(f"触发测试错误: {e}")
            return False
    
    def process_callback_requests(self):
        """处理回调请求"""
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        # 获取待处理的回调请求
        cursor.execute('''
            SELECT id, phone_number, priority 
            FROM callback_requests 
            WHERE status = 'pending'
            ORDER BY priority DESC, request_time ASC
            LIMIT 10
        ''')
        
        requests = cursor.fetchall()
        
        for req_id, phone_number, priority in requests:
            # 检查是否有可用坐席
            available_agent = self._find_available_agent()
            
            if available_agent:
                # 发起回调
                if self._initiate_callback(phone_number, available_agent):
                    # 更新回调状态
                    cursor.execute('''
                        UPDATE callback_requests 
                        SET status = 'processing', 
                            agent_id = ?,
                            callback_time = CURRENT_TIMESTAMP
                        WHERE id = ?
                    ''', (available_agent, req_id))
                    
                    logger.info(f"开始处理回调请求: {phone_number} -> {available_agent}")
        
        conn.commit()
        conn.close()
    
    def _find_available_agent(self) -> Optional[str]:
        """查找可用坐席"""
        agents = self.get_agent_status_list()
        
        for agent in agents:
            if agent['status'] == '空闲':
                return agent['agent_id']
        
        return None
    
    def _initiate_callback(self, phone_number: str, agent_id: str) -> bool:
        """发起回调"""
        if not self.connection or not self.connection.connected():
            return False
        
        try:
            # 先呼叫坐席
            agent_ext = agent_id.replace('agent_', '1')  # agent_001 -> 1001
            
            cmd = f"originate user/{agent_ext} &bridge(sofia/external/{phone_number}@your_gateway)"
            result = self.connection.api(cmd)
            
            return result is not None
        except Exception as e:
            logger.error(f"发起回调错误: {e}")
            return False
    
    def generate_report(self, date: Optional[str] = None) -> Dict:
        """
        生成报告
        
        Args:
            date: 日期 (YYYY-MM-DD格式)，默认为今天
        
        Returns:
            报告数据
        """
        if not date:
            date = datetime.date.today().strftime('%Y-%m-%d')
        
        conn = sqlite3.connect(self.db_path)
        cursor = conn.cursor()
        
        # 转人工统计
        cursor.execute('''
            SELECT 
                COUNT(*) as total_transfers,
                COUNT(CASE WHEN status = 'success' THEN 1 END) as successful_transfers,
                COUNT(CASE WHEN status = 'failed' THEN 1 END) as failed_transfers,
                COUNT(DISTINCT agent_id) as active_agents
            FROM transfer_logs
            WHERE DATE(timestamp) = ?
        ''', (date,))
        
        transfer_stats = dict(zip(
            ['total_transfers', 'successful_transfers', 'failed_transfers', 'active_agents'],
            cursor.fetchone()
        ))
        
        # 转人工原因分布
        cursor.execute('''
            SELECT transfer_reason, COUNT(*) as count
            FROM transfer_logs
            WHERE DATE(timestamp) = ?
            GROUP BY transfer_reason
            ORDER BY count DESC
        ''', (date,))
        
        reason_distribution = cursor.fetchall()
        
        # 坐席表现
        cursor.execute('''
            SELECT 
                agent_id,
                COUNT(*) as calls_handled,
                AVG(CASE WHEN status = 'success' THEN 1 ELSE 0 END) as success_rate
            FROM transfer_logs
            WHERE DATE(timestamp) = ?
            GROUP BY agent_id
            ORDER BY calls_handled DESC
        ''', (date,))
        
        agent_performance = cursor.fetchall()
        
        # 回调统计
        cursor.execute('''
            SELECT 
                COUNT(*) as total_callbacks,
                COUNT(CASE WHEN status = 'completed' THEN 1 END) as completed_callbacks
            FROM callback_requests
            WHERE DATE(request_time) = ?
        ''', (date,))
        
        callback_stats = dict(zip(
            ['total_callbacks', 'completed_callbacks'],
            cursor.fetchone()
        ))
        
        conn.close()
        
        report = {
            'date': date,
            'transfer_statistics': transfer_stats,
            'reason_distribution': reason_distribution,
            'agent_performance': agent_performance,
            'callback_statistics': callback_stats,
            'generated_at': datetime.datetime.now().isoformat()
        }
        
        return report


def main():
    """主函数 - 用于测试"""
    manager = TransferAgentManager()
    
    # 连接到FreeSWITCH
    if not manager.connect():
        logger.error("无法连接到FreeSWITCH")
        return
    
    try:
        # 获取坐席状态
        agents = manager.get_agent_status_list()
        print("\n=== 坐席状态 ===")
        for agent in agents:
            print(f"{agent['agent_id']}: {agent['status']} - 今日通话: {agent['calls_today']}")
        
        # 获取队列状态
        queues = manager.get_queue_status()
        print("\n=== 队列状态 ===")
        for queue_name, info in queues.items():
            print(f"{queue_name}: 等待 {info['waiting']} | 坐席 {info['agents']}")
        
        # 生成报告
        report = manager.generate_report()
        print("\n=== 今日报告 ===")
        print(json.dumps(report, indent=2, ensure_ascii=False))
        
    finally:
        manager.disconnect()


if __name__ == "__main__":
    main()