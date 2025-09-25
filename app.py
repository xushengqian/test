from flask import Flask, render_template, request, jsonify
from flask_socketio import SocketIO, emit, join_room, leave_room
import json
import time
import threading
from datetime import datetime
import uuid

app = Flask(__name__)
app.config['SECRET_KEY'] = 'robot_call_system_secret_key'
socketio = SocketIO(app, cors_allowed_origins="*")

# 存储通话会话数据
call_sessions = {}
# 存储人工客服连接
agents = {}

class CallSession:
    def __init__(self, call_id):
        self.call_id = call_id
        self.status = "connecting"  # connecting, robot_talking, agent_talking, ended
        self.start_time = datetime.now()
        self.messages = []
        self.customer_phone = None
        self.agent_id = None
        self.robot_active = True
        
    def add_message(self, sender, content, message_type="text"):
        message = {
            "id": str(uuid.uuid4()),
            "sender": sender,
            "content": content,
            "type": message_type,
            "timestamp": datetime.now().isoformat()
        }
        self.messages.append(message)
        return message
    
    def to_dict(self):
        return {
            "call_id": self.call_id,
            "status": self.status,
            "start_time": self.start_time.isoformat(),
            "messages": self.messages,
            "customer_phone": self.customer_phone,
            "agent_id": self.agent_id,
            "robot_active": self.robot_active,
            "duration": str(datetime.now() - self.start_time)
        }

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/agent')
def agent_dashboard():
    return render_template('agent.html')

@app.route('/api/calls')
def get_calls():
    """获取所有通话会话"""
    calls = [session.to_dict() for session in call_sessions.values()]
    return jsonify(calls)

@app.route('/api/call/<call_id>')
def get_call(call_id):
    """获取特定通话会话详情"""
    if call_id in call_sessions:
        return jsonify(call_sessions[call_id].to_dict())
    return jsonify({"error": "Call not found"}), 404

@app.route('/api/start_call', methods=['POST'])
def start_call():
    """开始新的外呼"""
    data = request.json
    phone = data.get('phone')
    
    call_id = str(uuid.uuid4())
    session = CallSession(call_id)
    session.customer_phone = phone
    session.status = "robot_talking"
    
    call_sessions[call_id] = session
    
    # 添加初始消息
    session.add_message("system", f"开始呼叫 {phone}", "system")
    session.add_message("robot", "您好，我是智能客服机器人，请问有什么可以帮助您的吗？", "text")
    
    # 通知所有监控端
    socketio.emit('new_call', session.to_dict(), room='monitors')
    
    # 启动机器人对话模拟
    threading.Thread(target=simulate_robot_conversation, args=(call_id,)).start()
    
    return jsonify({"call_id": call_id, "status": "started"})

@socketio.on('connect')
def handle_connect():
    print(f'Client connected: {request.sid}')

@socketio.on('disconnect')
def handle_disconnect():
    print(f'Client disconnected: {request.sid}')
    # 如果是客服断开连接，更新状态
    if request.sid in agents:
        agent_info = agents[request.sid]
        if agent_info.get('current_call'):
            call_id = agent_info['current_call']
            if call_id in call_sessions:
                call_sessions[call_id].status = "robot_talking"
                call_sessions[call_id].agent_id = None
                call_sessions[call_id].robot_active = True
                socketio.emit('call_updated', call_sessions[call_id].to_dict(), room='monitors')
        del agents[request.sid]

@socketio.on('join_monitor')
def handle_join_monitor():
    """监控端加入房间"""
    join_room('monitors')
    emit('joined_monitor', {"status": "success"})

@socketio.on('join_agent')
def handle_join_agent(data):
    """客服加入系统"""
    agent_name = data.get('name', f'Agent_{request.sid[:8]}')
    agents[request.sid] = {
        "name": agent_name,
        "status": "available",
        "current_call": None
    }
    join_room('agents')
    emit('agent_joined', {"agent_id": request.sid, "name": agent_name})

@socketio.on('take_call')
def handle_take_call(data):
    """客服接入通话"""
    call_id = data.get('call_id')
    
    if call_id not in call_sessions:
        emit('error', {"message": "通话不存在"})
        return
    
    if request.sid not in agents:
        emit('error', {"message": "请先登录为客服"})
        return
    
    session = call_sessions[call_id]
    if session.status == "ended":
        emit('error', {"message": "通话已结束"})
        return
    
    # 更新通话状态
    session.status = "agent_talking"
    session.agent_id = request.sid
    session.robot_active = False
    agents[request.sid]['current_call'] = call_id
    agents[request.sid]['status'] = 'busy'
    
    # 添加系统消息
    agent_name = agents[request.sid]['name']
    session.add_message("system", f"人工客服 {agent_name} 已接入通话", "system")
    
    # 通知所有监控端
    socketio.emit('call_updated', session.to_dict(), room='monitors')
    
    # 通知客服加入通话房间
    join_room(f'call_{call_id}')
    emit('call_taken', session.to_dict())

@socketio.on('send_message')
def handle_send_message(data):
    """发送消息"""
    call_id = data.get('call_id')
    content = data.get('content')
    sender = data.get('sender', 'agent')
    
    if call_id not in call_sessions:
        emit('error', {"message": "通话不存在"})
        return
    
    session = call_sessions[call_id]
    
    # 添加消息
    if sender == 'agent' and request.sid in agents:
        agent_name = agents[request.sid]['name']
        message = session.add_message(f"agent_{agent_name}", content, "text")
    else:
        message = session.add_message(sender, content, "text")
    
    # 广播消息
    socketio.emit('new_message', {
        "call_id": call_id,
        "message": message
    }, room='monitors')
    
    socketio.emit('new_message', {
        "call_id": call_id,
        "message": message
    }, room=f'call_{call_id}')

@socketio.on('end_call')
def handle_end_call(data):
    """结束通话"""
    call_id = data.get('call_id')
    
    if call_id not in call_sessions:
        emit('error', {"message": "通话不存在"})
        return
    
    session = call_sessions[call_id]
    session.status = "ended"
    session.add_message("system", "通话已结束", "system")
    
    # 如果有客服在线，更新客服状态
    if session.agent_id and session.agent_id in agents:
        agents[session.agent_id]['status'] = 'available'
        agents[session.agent_id]['current_call'] = None
    
    # 通知所有相关方
    socketio.emit('call_updated', session.to_dict(), room='monitors')
    socketio.emit('call_ended', {"call_id": call_id}, room=f'call_{call_id}')

def simulate_robot_conversation(call_id):
    """模拟机器人对话"""
    if call_id not in call_sessions:
        return
    
    session = call_sessions[call_id]
    
    # 模拟客户回复和机器人响应
    robot_responses = [
        ("customer", "我想了解一下你们的产品"),
        ("robot", "好的，我们有多种产品可以为您介绍。请问您对哪类产品比较感兴趣呢？"),
        ("customer", "我想了解保险产品"),
        ("robot", "我们有多种保险产品，包括人寿保险、健康保险等。不过这个需要专业的客服为您详细介绍，我为您转接人工客服好吗？"),
        ("customer", "好的，谢谢"),
        ("robot", "请稍等，我正在为您转接人工客服...")
    ]
    
    for i, (sender, content) in enumerate(robot_responses):
        if not session.robot_active or session.status == "ended":
            break
            
        time.sleep(3)  # 模拟对话间隔
        
        if call_id in call_sessions and session.robot_active:
            message = session.add_message(sender, content, "text")
            
            # 广播消息
            socketio.emit('new_message', {
                "call_id": call_id,
                "message": message
            }, room='monitors')
            
            # 如果是最后一条机器人消息，标记需要人工接入
            if i == len(robot_responses) - 1:
                session.add_message("system", "等待人工客服接入...", "system")
                socketio.emit('call_updated', session.to_dict(), room='monitors')

if __name__ == '__main__':
    socketio.run(app, debug=True, host='0.0.0.0', port=5000)