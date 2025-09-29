#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
人工坐席接入控制API
提供坐席主动接入机器人通话的接口
"""

import json
import logging
import sqlite3
import threading
import time
from datetime import datetime
from flask import Flask, request, jsonify
from flask_cors import CORS
import requests
import ESL

# 配置日志
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)
CORS(app)

# FreeSWITCH ESL连接配置
ESL_HOST = 'localhost'
ESL_PORT = 8021
ESL_PASSWORD = 'ClueCon'

# 数据库初始化
def init_database():
    """初始化数据库"""
    conn = sqlite3.connect('robot_calls.db')
    cursor = conn.cursor()
    
    # 创建通话会话表
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS call_sessions (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT UNIQUE NOT NULL,
            customer_number TEXT NOT NULL,
            robot_start_time DATETIME,
            agent_join_time DATETIME,
            call_end_time DATETIME,
            call_state TEXT DEFAULT 'robot_active',
            agent_id TEXT,
            conversation_log TEXT,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    ''')
    
    # 创建坐席状态表
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS agent_status (
            agent_id TEXT PRIMARY KEY,
            agent_name TEXT NOT NULL,
            status TEXT DEFAULT 'available',
            current_session_id TEXT,
            last_activity DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    ''')
    
    # 创建转接请求表
    cursor.execute('''
        CREATE TABLE IF NOT EXISTS transfer_requests (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            session_id TEXT NOT NULL,
            customer_number TEXT NOT NULL,
            request_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            assigned_agent_id TEXT,
            status TEXT DEFAULT 'pending',
            conversation_summary TEXT
        )
    ''')
    
    conn.commit()
    conn.close()

class FreeSWITCHController:
    """FreeSWITCH控制器"""
    
    def __init__(self):
        self.esl_connection = None
        self.connect_esl()
    
    def connect_esl(self):
        """连接到FreeSWITCH ESL"""
        try:
            self.esl_connection = ESL.ESLconnection(ESL_HOST, ESL_PORT, ESL_PASSWORD)
            if self.esl_connection.connected():
                logger.info("Connected to FreeSWITCH ESL successfully")
                return True
            else:
                logger.error("Failed to connect to FreeSWITCH ESL")
                return False
        except Exception as e:
            logger.error(f"ESL connection error: {e}")
            return False
    
    def execute_api(self, command):
        """执行FreeSWITCH API命令"""
        if not self.esl_connection or not self.esl_connection.connected():
            self.connect_esl()
        
        try:
            event = self.esl_connection.api(command)
            if event:
                return event.getBody()
            return None
        except Exception as e:
            logger.error(f"API execution error: {e}")
            return None
    
    def get_active_calls(self):
        """获取活跃通话列表"""
        result = self.execute_api("show calls")
        if result:
            return result
        return ""
    
    def transfer_call(self, session_id, destination):
        """转移通话"""
        command = f"uuid_transfer {session_id} {destination}"
        return self.execute_api(command)
    
    def bridge_calls(self, session_id1, session_id2):
        """桥接两个通话"""
        command = f"uuid_bridge {session_id1} {session_id2}"
        return self.execute_api(command)
    
    def set_variable(self, session_id, variable, value):
        """设置会话变量"""
        command = f"uuid_setvar {session_id} {variable} {value}"
        return self.execute_api(command)
    
    def create_conference_call(self, session_id, conference_id, agent_number):
        """创建三方会议通话"""
        # 将客户转入会议室
        self.set_variable(session_id, "conference_id", conference_id)
        self.transfer_call(session_id, f"conf_{conference_id}")
        
        # 呼叫坐席加入会议
        originate_command = f"originate user/{agent_number} &conference({conference_id}@robot_call_profile)"
        return self.execute_api(originate_command)

# 全局FreeSWITCH控制器实例
fs_controller = FreeSWITCHController()

@app.route('/api/active_calls', methods=['GET'])
def get_active_calls():
    """获取当前活跃的机器人通话"""
    try:
        conn = sqlite3.connect('robot_calls.db')
        cursor = conn.cursor()
        
        cursor.execute('''
            SELECT session_id, customer_number, robot_start_time, call_state, conversation_log
            FROM call_sessions 
            WHERE call_state IN ('robot_active', 'robot_paused', 'transferring')
            ORDER BY robot_start_time DESC
        ''')
        
        calls = []
        for row in cursor.fetchall():
            session_id, customer_number, start_time, call_state, conversation_log = row
            
            # 解析对话记录
            try:
                conversation = json.loads(conversation_log) if conversation_log else []
            except:
                conversation = []
            
            calls.append({
                'session_id': session_id,
                'customer_number': customer_number,
                'start_time': start_time,
                'call_state': call_state,
                'conversation_preview': get_conversation_preview(conversation),
                'duration': calculate_call_duration(start_time)
            })
        
        conn.close()
        return jsonify({'success': True, 'calls': calls})
        
    except Exception as e:
        logger.error(f"Error getting active calls: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/agent_takeover', methods=['POST'])
def agent_takeover():
    """坐席主动接入通话"""
    try:
        data = request.get_json()
        session_id = data.get('session_id')
        agent_id = data.get('agent_id')
        agent_number = data.get('agent_number')
        
        if not all([session_id, agent_id, agent_number]):
            return jsonify({'success': False, 'error': 'Missing required parameters'}), 400
        
        # 检查会话是否存在
        conn = sqlite3.connect('robot_calls.db')
        cursor = conn.cursor()
        
        cursor.execute('SELECT customer_number, call_state FROM call_sessions WHERE session_id = ?', (session_id,))
        session_info = cursor.fetchone()
        
        if not session_info:
            conn.close()
            return jsonify({'success': False, 'error': 'Session not found'}), 404
        
        customer_number, current_state = session_info
        
        if current_state not in ['robot_active', 'robot_paused']:
            conn.close()
            return jsonify({'success': False, 'error': 'Call not available for takeover'}), 400
        
        # 生成会议室ID
        conference_id = f"robot_call_{session_id}"
        
        # 设置会话变量，触发转接
        fs_controller.set_variable(session_id, "agent_id", agent_id)
        fs_controller.set_variable(session_id, "transfer_to_human", "true")
        
        # 创建三方会议
        result = fs_controller.create_conference_call(session_id, conference_id, agent_number)
        
        if result:
            # 更新数据库状态
            cursor.execute('''
                UPDATE call_sessions 
                SET call_state = 'in_conference', agent_id = ?, agent_join_time = ?
                WHERE session_id = ?
            ''', (agent_id, datetime.now(), session_id))
            
            # 更新坐席状态
            cursor.execute('''
                INSERT OR REPLACE INTO agent_status (agent_id, status, current_session_id, last_activity)
                VALUES (?, 'busy', ?, ?)
            ''', (agent_id, session_id, datetime.now()))
            
            conn.commit()
            conn.close()
            
            logger.info(f"Agent {agent_id} successfully took over call {session_id}")
            return jsonify({
                'success': True, 
                'message': 'Agent takeover successful',
                'conference_id': conference_id
            })
        else:
            conn.close()
            return jsonify({'success': False, 'error': 'Failed to create conference call'}), 500
            
    except Exception as e:
        logger.error(f"Error in agent takeover: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/transfer_request', methods=['POST'])
def handle_transfer_request():
    """处理机器人发起的转接请求"""
    try:
        data = request.get_json()
        session_id = data.get('session_id')
        customer_number = data.get('customer_number')
        conversation_summary = data.get('conversation_summary', '')
        
        if not all([session_id, customer_number]):
            return jsonify({'success': False, 'error': 'Missing required parameters'}), 400
        
        # 保存转接请求
        conn = sqlite3.connect('robot_calls.db')
        cursor = conn.cursor()
        
        cursor.execute('''
            INSERT INTO transfer_requests (session_id, customer_number, conversation_summary)
            VALUES (?, ?, ?)
        ''', (session_id, customer_number, conversation_summary))
        
        # 更新会话状态
        cursor.execute('''
            UPDATE call_sessions 
            SET call_state = 'transfer_requested'
            WHERE session_id = ?
        ''', (session_id,))
        
        conn.commit()
        conn.close()
        
        # 通知可用坐席（这里可以集成WebSocket或其他实时通知机制）
        notify_available_agents(session_id, customer_number, conversation_summary)
        
        logger.info(f"Transfer request received for session {session_id}")
        return jsonify({'success': True, 'message': 'Transfer request processed'})
        
    except Exception as e:
        logger.error(f"Error handling transfer request: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/call_history', methods=['GET'])
def get_call_history():
    """获取通话历史记录"""
    try:
        page = int(request.args.get('page', 1))
        limit = int(request.args.get('limit', 20))
        offset = (page - 1) * limit
        
        conn = sqlite3.connect('robot_calls.db')
        cursor = conn.cursor()
        
        cursor.execute('''
            SELECT session_id, customer_number, robot_start_time, agent_join_time, 
                   call_end_time, call_state, agent_id
            FROM call_sessions 
            ORDER BY robot_start_time DESC
            LIMIT ? OFFSET ?
        ''', (limit, offset))
        
        history = []
        for row in cursor.fetchall():
            session_id, customer_number, start_time, agent_time, end_time, state, agent_id = row
            history.append({
                'session_id': session_id,
                'customer_number': customer_number,
                'start_time': start_time,
                'agent_join_time': agent_time,
                'end_time': end_time,
                'call_state': state,
                'agent_id': agent_id,
                'total_duration': calculate_total_duration(start_time, end_time)
            })
        
        conn.close()
        return jsonify({'success': True, 'history': history})
        
    except Exception as e:
        logger.error(f"Error getting call history: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/agents', methods=['GET'])
def get_agents():
    """获取坐席列表和状态"""
    try:
        conn = sqlite3.connect('robot_calls.db')
        cursor = conn.cursor()
        
        cursor.execute('''
            SELECT agent_id, agent_name, status, current_session_id, last_activity
            FROM agent_status
            ORDER BY last_activity DESC
        ''')
        
        agents = []
        for row in cursor.fetchall():
            agent_id, agent_name, status, current_session, last_activity = row
            agents.append({
                'agent_id': agent_id,
                'agent_name': agent_name,
                'status': status,
                'current_session_id': current_session,
                'last_activity': last_activity
            })
        
        conn.close()
        return jsonify({'success': True, 'agents': agents})
        
    except Exception as e:
        logger.error(f"Error getting agents: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

def get_conversation_preview(conversation):
    """获取对话预览"""
    if not conversation:
        return "暂无对话记录"
    
    preview = ""
    for entry in conversation[-3:]:  # 最后3条对话
        speaker = "客户" if entry.get('speaker') == 'customer' else "机器人"
        text = entry.get('text', '')[:50]  # 限制长度
        preview += f"{speaker}: {text}... "
    
    return preview.strip()

def calculate_call_duration(start_time):
    """计算通话时长"""
    if not start_time:
        return "0秒"
    
    try:
        start = datetime.fromisoformat(start_time.replace('Z', '+00:00'))
        duration = datetime.now() - start
        return f"{duration.seconds}秒"
    except:
        return "未知"

def calculate_total_duration(start_time, end_time):
    """计算总通话时长"""
    if not start_time or not end_time:
        return "未知"
    
    try:
        start = datetime.fromisoformat(start_time.replace('Z', '+00:00'))
        end = datetime.fromisoformat(end_time.replace('Z', '+00:00'))
        duration = end - start
        return f"{duration.seconds}秒"
    except:
        return "未知"

@app.route('/api/update_call_state', methods=['POST'])
def update_call_state():
    """更新通话状态"""
    try:
        data = request.get_json()
        session_id = data.get('session_id')
        call_state = data.get('call_state')
        additional_data = data.get('additional_data', {})
        
        if not all([session_id, call_state]):
            return jsonify({'success': False, 'error': 'Missing required parameters'}), 400
        
        conn = sqlite3.connect('robot_calls.db')
        cursor = conn.cursor()
        
        # 更新会话状态
        if call_state == 'ended':
            cursor.execute('''
                UPDATE call_sessions 
                SET call_state = ?, call_end_time = ?
                WHERE session_id = ?
            ''', (call_state, datetime.now(), session_id))
        else:
            cursor.execute('''
                UPDATE call_sessions 
                SET call_state = ?
                WHERE session_id = ?
            ''', (call_state, session_id))
        
        # 如果有坐席信息，更新坐席状态
        if 'agent_id' in additional_data:
            agent_id = additional_data['agent_id']
            if call_state == 'in_conference':
                cursor.execute('''
                    UPDATE call_sessions 
                    SET agent_id = ?, agent_join_time = ?
                    WHERE session_id = ?
                ''', (agent_id, datetime.now(), session_id))
        
        conn.commit()
        conn.close()
        
        return jsonify({'success': True, 'message': 'Call state updated'})
        
    except Exception as e:
        logger.error(f"Error updating call state: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/release_agent', methods=['POST'])
def release_agent():
    """释放坐席状态"""
    try:
        data = request.get_json()
        agent_id = data.get('agent_id')
        session_id = data.get('session_id')
        
        if not agent_id:
            return jsonify({'success': False, 'error': 'Missing agent_id'}), 400
        
        conn = sqlite3.connect('robot_calls.db')
        cursor = conn.cursor()
        
        # 更新坐席状态为可用
        cursor.execute('''
            UPDATE agent_status 
            SET status = 'available', current_session_id = NULL, last_activity = ?
            WHERE agent_id = ?
        ''', (datetime.now(), agent_id))
        
        conn.commit()
        conn.close()
        
        logger.info(f"Agent {agent_id} released from session {session_id}")
        return jsonify({'success': True, 'message': 'Agent released'})
        
    except Exception as e:
        logger.error(f"Error releasing agent: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/api/start_robot_call', methods=['POST'])
def start_robot_call():
    """启动机器人外呼"""
    try:
        data = request.get_json()
        customer_number = data.get('customer_number')
        
        if not customer_number:
            return jsonify({'success': False, 'error': 'Missing customer_number'}), 400
        
        # 生成会话ID
        import uuid
        session_id = str(uuid.uuid4())
        
        # 保存到数据库
        conn = sqlite3.connect('robot_calls.db')
        cursor = conn.cursor()
        
        cursor.execute('''
            INSERT INTO call_sessions (session_id, customer_number, robot_start_time, call_state)
            VALUES (?, ?, ?, ?)
        ''', (session_id, customer_number, datetime.now(), 'robot_active'))
        
        conn.commit()
        conn.close()
        
        # 发起FreeSWITCH外呼
        originate_command = f"originate {{origination_uuid={session_id},customer_number={customer_number}}}sofia/gateway/your_gateway/{customer_number} &transfer(robot_call_{customer_number} XML outbound_robot)"
        
        result = fs_controller.execute_api(originate_command)
        
        if result and "SUCCESS" in result:
            logger.info(f"Robot call started for {customer_number}, session: {session_id}")
            return jsonify({
                'success': True, 
                'message': 'Robot call started',
                'session_id': session_id
            })
        else:
            return jsonify({'success': False, 'error': 'Failed to start call'}), 500
            
    except Exception as e:
        logger.error(f"Error starting robot call: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

def notify_available_agents(session_id, customer_number, conversation_summary):
    """通知可用坐席有转接请求"""
    # 这里可以实现WebSocket推送、邮件通知等
    logger.info(f"Notifying agents about transfer request for {customer_number}")
    pass

if __name__ == '__main__':
    # 初始化数据库
    init_database()
    
    # 启动Flask应用
    app.run(host='0.0.0.0', port=8080, debug=True)