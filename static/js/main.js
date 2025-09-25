// 主监控页面JavaScript
class CallMonitor {
    constructor() {
        this.socket = io();
        this.calls = new Map();
        this.currentCallId = null;
        
        this.initializeElements();
        this.bindEvents();
        this.connectSocket();
        this.loadCalls();
    }
    
    initializeElements() {
        this.startCallBtn = document.getElementById('startCallBtn');
        this.startCallModal = document.getElementById('startCallModal');
        this.phoneInput = document.getElementById('phoneInput');
        this.confirmCallBtn = document.getElementById('confirmCallBtn');
        this.callList = document.getElementById('callList');
        this.welcomeScreen = document.getElementById('welcomeScreen');
        this.callDetail = document.getElementById('callDetail');
        this.callTitle = document.getElementById('callTitle');
        this.callPhone = document.getElementById('callPhone');
        this.callStatus = document.getElementById('callStatus');
        this.callDuration = document.getElementById('callDuration');
        this.messagesList = document.getElementById('messagesList');
        this.endCallBtn = document.getElementById('endCallBtn');
    }
    
    bindEvents() {
        // 发起外呼按钮
        this.startCallBtn.addEventListener('click', () => {
            this.showStartCallModal();
        });
        
        // 确认呼叫按钮
        this.confirmCallBtn.addEventListener('click', () => {
            this.startCall();
        });
        
        // 结束通话按钮
        this.endCallBtn.addEventListener('click', () => {
            this.endCall();
        });
        
        // 模态框关闭
        document.querySelectorAll('.modal-close').forEach(btn => {
            btn.addEventListener('click', () => {
                this.hideStartCallModal();
            });
        });
        
        // 点击模态框外部关闭
        this.startCallModal.addEventListener('click', (e) => {
            if (e.target === this.startCallModal) {
                this.hideStartCallModal();
            }
        });
        
        // 电话输入框回车
        this.phoneInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.startCall();
            }
        });
    }
    
    connectSocket() {
        this.socket.on('connect', () => {
            console.log('Connected to server');
            this.socket.emit('join_monitor');
        });
        
        this.socket.on('joined_monitor', () => {
            console.log('Joined monitor room');
        });
        
        this.socket.on('new_call', (callData) => {
            this.addCall(callData);
        });
        
        this.socket.on('call_updated', (callData) => {
            this.updateCall(callData);
        });
        
        this.socket.on('new_message', (data) => {
            this.addMessage(data.call_id, data.message);
        });
        
        this.socket.on('call_ended', (data) => {
            this.handleCallEnded(data.call_id);
        });
    }
    
    async loadCalls() {
        try {
            const response = await fetch('/api/calls');
            const calls = await response.json();
            
            calls.forEach(call => {
                this.addCall(call);
            });
        } catch (error) {
            console.error('Failed to load calls:', error);
        }
    }
    
    showStartCallModal() {
        this.startCallModal.style.display = 'block';
        this.phoneInput.focus();
    }
    
    hideStartCallModal() {
        this.startCallModal.style.display = 'none';
        this.phoneInput.value = '';
    }
    
    async startCall() {
        const phone = this.phoneInput.value.trim();
        if (!phone) {
            alert('请输入电话号码');
            return;
        }
        
        try {
            const response = await fetch('/api/start_call', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ phone })
            });
            
            const result = await response.json();
            if (result.call_id) {
                this.hideStartCallModal();
                // 通话会通过socket事件自动添加到列表
            } else {
                alert('发起外呼失败');
            }
        } catch (error) {
            console.error('Failed to start call:', error);
            alert('发起外呼失败');
        }
    }
    
    addCall(callData) {
        this.calls.set(callData.call_id, callData);
        this.renderCallList();
        
        // 如果是新通话，自动选中
        if (callData.status === 'robot_talking' && !this.currentCallId) {
            this.selectCall(callData.call_id);
        }
    }
    
    updateCall(callData) {
        this.calls.set(callData.call_id, callData);
        this.renderCallList();
        
        // 如果是当前选中的通话，更新详情
        if (this.currentCallId === callData.call_id) {
            this.renderCallDetail(callData);
        }
    }
    
    addMessage(callId, message) {
        const call = this.calls.get(callId);
        if (call) {
            call.messages.push(message);
            
            // 如果是当前选中的通话，添加消息到界面
            if (this.currentCallId === callId) {
                this.appendMessage(message);
            }
            
            // 更新通话列表中的最后消息
            this.renderCallList();
        }
    }
    
    handleCallEnded(callId) {
        const call = this.calls.get(callId);
        if (call) {
            call.status = 'ended';
            this.updateCall(call);
        }
    }
    
    renderCallList() {
        const callsArray = Array.from(this.calls.values()).sort((a, b) => 
            new Date(b.start_time) - new Date(a.start_time)
        );
        
        if (callsArray.length === 0) {
            this.callList.innerHTML = `
                <div class="no-calls">
                    <i class="fas fa-phone-slash"></i>
                    <p>暂无通话记录</p>
                </div>
            `;
            return;
        }
        
        this.callList.innerHTML = callsArray.map(call => {
            const lastMessage = call.messages.length > 0 ? 
                call.messages[call.messages.length - 1] : null;
            
            return `
                <div class="call-item ${this.currentCallId === call.call_id ? 'active' : ''}" 
                     data-call-id="${call.call_id}">
                    <div class="call-item-header">
                        <span class="call-phone">${call.customer_phone || '未知号码'}</span>
                        <span class="call-time">${this.formatTime(call.start_time)}</span>
                    </div>
                    <div class="call-status">
                        <span class="status-badge ${call.status}">${this.getStatusText(call.status)}</span>
                        <span class="duration">${call.duration}</span>
                    </div>
                    ${lastMessage ? `
                        <div class="call-last-message">
                            ${lastMessage.sender}: ${lastMessage.content}
                        </div>
                    ` : ''}
                </div>
            `;
        }).join('');
        
        // 绑定点击事件
        this.callList.querySelectorAll('.call-item').forEach(item => {
            item.addEventListener('click', () => {
                const callId = item.dataset.callId;
                this.selectCall(callId);
            });
        });
    }
    
    selectCall(callId) {
        this.currentCallId = callId;
        const call = this.calls.get(callId);
        
        if (call) {
            this.welcomeScreen.style.display = 'none';
            this.callDetail.style.display = 'flex';
            this.renderCallDetail(call);
            this.renderCallList(); // 更新选中状态
        }
    }
    
    renderCallDetail(call) {
        this.callTitle.textContent = `通话详情 - ${call.call_id.substring(0, 8)}`;
        this.callPhone.textContent = call.customer_phone || '未知号码';
        this.callStatus.textContent = this.getStatusText(call.status);
        this.callStatus.className = `status-badge ${call.status}`;
        this.callDuration.textContent = call.duration;
        
        // 渲染消息列表
        this.messagesList.innerHTML = '';
        call.messages.forEach(message => {
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
        
        this.messagesList.appendChild(messageEl);
        this.scrollToBottom();
    }
    
    scrollToBottom() {
        this.messagesList.scrollTop = this.messagesList.scrollHeight;
    }
    
    async endCall() {
        if (!this.currentCallId) return;
        
        try {
            this.socket.emit('end_call', { call_id: this.currentCallId });
        } catch (error) {
            console.error('Failed to end call:', error);
        }
    }
    
    getStatusText(status) {
        const statusMap = {
            'connecting': '连接中',
            'robot_talking': '机器人通话',
            'agent_talking': '人工通话',
            'ended': '已结束'
        };
        return statusMap[status] || status;
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

// 初始化监控系统
document.addEventListener('DOMContentLoaded', () => {
    new CallMonitor();
});