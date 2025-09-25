// 客服工作台JavaScript
class AgentWorkspace {
    constructor() {
        this.socket = io();
        this.agentId = null;
        this.agentName = null;
        this.currentCallId = null;
        this.loginTime = null;
        this.onlineTimer = null;
        
        this.initializeElements();
        this.bindEvents();
        this.connectSocket();
    }
    
    initializeElements() {
        this.loginScreen = document.getElementById('loginScreen');
        this.workArea = document.getElementById('workArea');
        this.noActiveCall = document.getElementById('noActiveCall');
        this.activeCall = document.getElementById('activeCall');
        
        this.agentNameInput = document.getElementById('agentNameInput');
        this.loginBtn = document.getElementById('loginBtn');
        this.agentStatus = document.getElementById('agentStatus');
        this.agentNameDisplay = document.getElementById('agentName');
        this.onlineTimeDisplay = document.getElementById('onlineTime');
        
        this.pendingCalls = document.getElementById('pendingCalls');
        this.activeCallTitle = document.getElementById('activeCallTitle');
        this.activeCallPhone = document.getElementById('activeCallPhone');
        this.activeCallDuration = document.getElementById('activeCallDuration');
        this.activeMessagesList = document.getElementById('activeMessagesList');
        this.messageInput = document.getElementById('messageInput');
        this.sendMessageBtn = document.getElementById('sendMessageBtn');
        this.endActiveCallBtn = document.getElementById('endActiveCallBtn');
    }
    
    bindEvents() {
        // 登录按钮
        this.loginBtn.addEventListener('click', () => {
            this.login();
        });
        
        // 姓名输入框回车
        this.agentNameInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.login();
            }
        });
        
        // 发送消息按钮
        this.sendMessageBtn.addEventListener('click', () => {
            this.sendMessage();
        });
        
        // 消息输入框回车
        this.messageInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.sendMessage();
            }
        });
        
        // 结束通话按钮
        this.endActiveCallBtn.addEventListener('click', () => {
            this.endCall();
        });
    }
    
    connectSocket() {
        this.socket.on('connect', () => {
            console.log('Connected to server');
        });
        
        this.socket.on('agent_joined', (data) => {
            console.log('Agent joined:', data);
        });
        
        this.socket.on('new_call', (callData) => {
            this.handleNewCall(callData);
        });
        
        this.socket.on('call_updated', (callData) => {
            this.handleCallUpdated(callData);
        });
        
        this.socket.on('new_message', (data) => {
            this.handleNewMessage(data);
        });
        
        this.socket.on('call_taken', (callData) => {
            this.handleCallTaken(callData);
        });
        
        this.socket.on('call_ended', (data) => {
            this.handleCallEnded(data.call_id);
        });
        
        this.socket.on('error', (data) => {
            alert(data.message);
        });
    }
    
    login() {
        const name = this.agentNameInput.value.trim();
        if (!name) {
            alert('请输入您的姓名');
            return;
        }
        
        this.agentName = name;
        this.loginTime = new Date();
        
        // 发送登录请求
        this.socket.emit('join_agent', { name: name });
        
        // 更新界面
        this.loginScreen.style.display = 'none';
        this.workArea.style.display = 'flex';
        this.agentNameDisplay.textContent = name;
        this.agentStatus.textContent = '在线';
        this.agentStatus.className = 'status-badge available';
        
        // 启动在线时间计时器
        this.startOnlineTimer();
        
        // 加载待接入通话
        this.loadPendingCalls();
    }
    
    startOnlineTimer() {
        this.onlineTimer = setInterval(() => {
            if (this.loginTime) {
                const duration = new Date() - this.loginTime;
                const hours = Math.floor(duration / 3600000);
                const minutes = Math.floor((duration % 3600000) / 60000);
                const seconds = Math.floor((duration % 60000) / 1000);
                
                this.onlineTimeDisplay.textContent = 
                    `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
            }
        }, 1000);
    }
    
    async loadPendingCalls() {
        try {
            const response = await fetch('/api/calls');
            const calls = await response.json();
            
            // 筛选出需要人工接入的通话
            const pendingCalls = calls.filter(call => 
                call.status === 'robot_talking' && !call.agent_id
            );
            
            this.renderPendingCalls(pendingCalls);
        } catch (error) {
            console.error('Failed to load pending calls:', error);
        }
    }
    
    renderPendingCalls(calls) {
        if (calls.length === 0) {
            this.pendingCalls.innerHTML = `
                <div class="no-calls">
                    <i class="fas fa-phone-slash"></i>
                    <p>暂无待接入通话</p>
                </div>
            `;
            return;
        }
        
        this.pendingCalls.innerHTML = calls.map(call => {
            const lastMessage = call.messages.length > 0 ? 
                call.messages[call.messages.length - 1] : null;
            
            return `
                <div class="call-item" data-call-id="${call.call_id}">
                    <div class="call-item-header">
                        <span class="call-phone">${call.customer_phone || '未知号码'}</span>
                        <span class="call-time">${this.formatTime(call.start_time)}</span>
                    </div>
                    <div class="call-status">
                        <span class="status-badge ${call.status}">等待接入</span>
                        <span class="duration">${call.duration}</span>
                    </div>
                    ${lastMessage ? `
                        <div class="call-last-message">
                            ${this.getSenderName(lastMessage.sender)}: ${lastMessage.content}
                        </div>
                    ` : ''}
                    <button class="btn btn-primary btn-sm take-call-btn" 
                            data-call-id="${call.call_id}" style="margin-top: 0.5rem; width: 100%;">
                        <i class="fas fa-headset"></i> 接入通话
                    </button>
                </div>
            `;
        }).join('');
        
        // 绑定接入按钮事件
        this.pendingCalls.querySelectorAll('.take-call-btn').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const callId = btn.dataset.callId;
                this.takeCall(callId);
            });
        });
    }
    
    takeCall(callId) {
        this.socket.emit('take_call', { call_id: callId });
    }
    
    handleNewCall(callData) {
        // 如果是需要人工接入的通话，添加到待接入列表
        if (callData.status === 'robot_talking' && !callData.agent_id) {
            this.loadPendingCalls();
        }
    }
    
    handleCallUpdated(callData) {
        // 更新待接入列表
        this.loadPendingCalls();
        
        // 如果是当前通话，更新界面
        if (this.currentCallId === callData.call_id) {
            this.updateActiveCall(callData);
        }
    }
    
    handleNewMessage(data) {
        // 如果是当前通话的消息，添加到界面
        if (this.currentCallId === data.call_id) {
            this.appendMessage(data.message);
        }
    }
    
    handleCallTaken(callData) {
        this.currentCallId = callData.call_id;
        
        // 更新状态
        this.agentStatus.textContent = '通话中';
        this.agentStatus.className = 'status-badge busy';
        
        // 显示通话界面
        this.noActiveCall.style.display = 'none';
        this.activeCall.style.display = 'block';
        
        // 更新通话信息
        this.updateActiveCall(callData);
        
        // 更新待接入列表
        this.loadPendingCalls();
    }
    
    handleCallEnded(callId) {
        if (this.currentCallId === callId) {
            this.currentCallId = null;
            
            // 更新状态
            this.agentStatus.textContent = '在线';
            this.agentStatus.className = 'status-badge available';
            
            // 隐藏通话界面
            this.activeCall.style.display = 'none';
            this.noActiveCall.style.display = 'block';
            
            // 清空消息输入框
            this.messageInput.value = '';
        }
        
        // 更新待接入列表
        this.loadPendingCalls();
    }
    
    updateActiveCall(callData) {
        this.activeCallTitle.textContent = `通话中 - ${callData.call_id.substring(0, 8)}`;
        this.activeCallPhone.textContent = callData.customer_phone || '未知号码';
        this.activeCallDuration.textContent = callData.duration;
        
        // 渲染消息列表
        this.activeMessagesList.innerHTML = '';
        callData.messages.forEach(message => {
            this.appendMessage(message);
        });
        
        // 滚动到底部
        this.scrollToBottom();
    }
    
    appendMessage(message) {
        const messageEl = document.createElement('div');
        messageEl.className = `message ${this.getMessageClass(message.sender)}`;
        
        messageEl.innerHTML = `
            <div class="message-header">
                <span class="message-sender">${this.getSenderName(message.sender)}</span>
                <span class="message-time">${this.formatTime(message.timestamp)}</span>
            </div>
            <div class="message-content">${message.content}</div>
        `;
        
        this.activeMessagesList.appendChild(messageEl);
        this.scrollToBottom();
    }
    
    scrollToBottom() {
        this.activeMessagesList.scrollTop = this.activeMessagesList.scrollHeight;
    }
    
    sendMessage() {
        const content = this.messageInput.value.trim();
        if (!content || !this.currentCallId) return;
        
        this.socket.emit('send_message', {
            call_id: this.currentCallId,
            content: content,
            sender: 'agent'
        });
        
        this.messageInput.value = '';
    }
    
    endCall() {
        if (!this.currentCallId) return;
        
        this.socket.emit('end_call', { call_id: this.currentCallId });
    }
    
    getMessageClass(sender) {
        if (sender === 'robot') return 'robot';
        if (sender === 'customer') return 'customer';
        if (sender === 'system') return 'system';
        if (sender.startsWith('agent_')) return 'agent';
        return 'system';
    }
    
    getSenderName(sender) {
        if (sender === 'robot') return '机器人';
        if (sender === 'customer') return '客户';
        if (sender === 'system') return '系统';
        if (sender.startsWith('agent_')) return sender.replace('agent_', '客服-');
        return sender;
    }
    
    formatTime(timestamp) {
        const date = new Date(timestamp);
        return date.toLocaleTimeString('zh-CN', { 
            hour: '2-digit', 
            minute: '2-digit',
            second: '2-digit'
        });
    }
}

// 初始化客服工作台
document.addEventListener('DOMContentLoaded', () => {
    new AgentWorkspace();
});