// 客服系统状态管理
let chatState = {
    isConnectedToAgent: false,
    messages: [],
    waitingForAgent: false
};

// 获取当前时间
function getCurrentTime() {
    const now = new Date();
    return now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

// 添加消息到聊天界面
function addMessage(content, type = 'bot', sender = 'AI') {
    const chatContainer = document.getElementById('chatContainer');
    const messageDiv = document.createElement('div');
    messageDiv.className = `message ${type}`;
    
    const avatarText = type === 'user' ? '我' : (type === 'agent' ? '客服' : 'AI');
    
    messageDiv.innerHTML = `
        <div class="avatar">${avatarText}</div>
        <div>
            <div class="message-content">
                ${content}
                <div class="message-time">${getCurrentTime()}</div>
            </div>
        </div>
    `;
    
    chatContainer.appendChild(messageDiv);
    chatContainer.scrollTop = chatContainer.scrollHeight;
    
    // 保存消息历史
    chatState.messages.push({
        content,
        type,
        sender,
        time: getCurrentTime()
    });
}

// 发送消息
async function sendMessage() {
    const input = document.getElementById('messageInput');
    const message = input.value.trim();
    
    if (!message) return;
    
    // 添加用户消息
    addMessage(message, 'user');
    input.value = '';
    
    // 检查是否需要转人工
    if (checkTransferKeywords(message)) {
        showTransferSection();
        return;
    }
    
    // 如果已连接到人工客服
    if (chatState.isConnectedToAgent) {
        // 发送到人工客服
        sendToAgent(message);
    } else {
        // AI回复
        await aiReply(message);
    }
}

// 检查转人工关键词
function checkTransferKeywords(message) {
    const keywords = ['转人工', '人工客服', '真人', '客服', '投诉', '不满意', '转接'];
    return keywords.some(keyword => message.includes(keyword));
}

// 显示转人工选项
function showTransferSection() {
    const transferSection = document.getElementById('transferSection');
    transferSection.classList.add('show');
    
    // AI回复
    setTimeout(() => {
        addMessage('我理解您希望转接人工客服。请点击下方按钮确认转接，我们的客服团队会尽快为您服务。');
    }, 500);
}

// 隐藏转人工选项
function cancelTransfer() {
    const transferSection = document.getElementById('transferSection');
    transferSection.classList.remove('show');
    addMessage('好的，我会继续为您服务。请问还有什么可以帮助您的吗？');
}

// 确认转人工
async function confirmTransfer() {
    const transferSection = document.getElementById('transferSection');
    const connectingOverlay = document.getElementById('connectingOverlay');
    
    transferSection.classList.remove('show');
    connectingOverlay.classList.add('show');
    
    try {
        // 调用转人工API
        const response = await fetch('/api/transfer-to-agent', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                userId: getUserId(),
                messages: chatState.messages,
                timestamp: new Date().toISOString()
            })
        });
        
        // 模拟连接延迟
        setTimeout(async () => {
            connectingOverlay.classList.remove('show');
            
            if (response && response.ok) {
                const data = await response.json();
                onAgentConnected(data);
            } else {
                // 模拟成功连接（用于演示）
                onAgentConnected({
                    agentName: '李客服',
                    agentId: 'CS001',
                    queuePosition: 0
                });
            }
        }, 2000);
    } catch (error) {
        // 模拟成功连接（用于演示）
        setTimeout(() => {
            connectingOverlay.classList.remove('show');
            onAgentConnected({
                agentName: '李客服',
                agentId: 'CS001',
                queuePosition: 0
            });
        }, 2000);
    }
}

// 人工客服连接成功
function onAgentConnected(data) {
    chatState.isConnectedToAgent = true;
    
    // 更新状态显示
    document.getElementById('statusText').textContent = '人工客服在线';
    
    // 添加客服欢迎消息
    addMessage(`您好，我是人工客服${data.agentName}，很高兴为您服务。我已经看到了您之前的对话记录，请问有什么可以帮助您的吗？`, 'agent');
    
    // 更新发送按钮状态
    updateSendButton();
}

// 发送消息到人工客服
async function sendToAgent(message) {
    try {
        const response = await fetch('/api/send-to-agent', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                userId: getUserId(),
                message: message,
                timestamp: new Date().toISOString()
            })
        });
        
        // 模拟客服回复
        setTimeout(() => {
            simulateAgentReply(message);
        }, 1000 + Math.random() * 2000);
    } catch (error) {
        // 模拟客服回复（用于演示）
        setTimeout(() => {
            simulateAgentReply(message);
        }, 1000 + Math.random() * 2000);
    }
}

// 模拟客服回复
function simulateAgentReply(userMessage) {
    const replies = [
        '我明白您的问题，让我为您查询一下相关信息。',
        '感谢您的耐心等待，我正在处理您的请求。',
        '我已经了解您的需求，这个问题我可以帮您解决。',
        '请您提供一下订单号或者相关信息，以便我更好地为您服务。',
        '非常抱歉给您带来的不便，我会立即为您处理。'
    ];
    
    const reply = replies[Math.floor(Math.random() * replies.length)];
    addMessage(reply, 'agent');
}

// AI回复
async function aiReply(message) {
    // 显示输入中状态
    const sendBtn = document.getElementById('sendBtn');
    sendBtn.disabled = true;
    sendBtn.textContent = '思考中...';
    
    try {
        const response = await fetch('/api/ai-chat', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                message: message,
                context: chatState.messages
            })
        });
        
        if (response && response.ok) {
            const data = await response.json();
            addMessage(data.reply);
        } else {
            // 模拟AI回复（用于演示）
            simulateAIReply(message);
        }
    } catch (error) {
        // 模拟AI回复（用于演示）
        simulateAIReply(message);
    } finally {
        sendBtn.disabled = false;
        sendBtn.textContent = '发送';
    }
}

// 模拟AI回复
function simulateAIReply(message) {
    const replies = {
        '查询订单': '请提供您的订单号，我会立即为您查询订单状态。',
        '退款': '关于退款申请，请告诉我您的订单号和退款原因，我会协助您处理。',
        '投诉': '非常抱歉给您带来不好的体验，请详细说明您遇到的问题，我会认真记录并反馈给相关部门。',
        '物流': '我可以帮您查询物流信息，请提供订单号或快递单号。',
        default: '感谢您的咨询，我正在理解您的问题。请问您需要什么帮助呢？'
    };
    
    let reply = replies.default;
    for (const key in replies) {
        if (message.includes(key)) {
            reply = replies[key];
            break;
        }
    }
    
    setTimeout(() => {
        addMessage(reply);
    }, 800);
}

// 快速消息
function quickMessage(text) {
    document.getElementById('messageInput').value = text;
    sendMessage();
}

// 处理键盘事件
function handleKeyPress(event) {
    if (event.key === 'Enter') {
        sendMessage();
    }
}

// 更新发送按钮状态
function updateSendButton() {
    const sendBtn = document.getElementById('sendBtn');
    if (chatState.isConnectedToAgent) {
        sendBtn.style.background = 'linear-gradient(135deg, #10b981 0%, #059669 100%)';
    }
}

// 获取或生成用户ID
function getUserId() {
    let userId = localStorage.getItem('userId');
    if (!userId) {
        userId = 'user_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
        localStorage.setItem('userId', userId);
    }
    return userId;
}

// WebSocket连接（用于实时通信）
function initWebSocket() {
    if (typeof WebSocket === 'undefined') {
        console.log('WebSocket not supported');
        return;
    }
    
    try {
        const ws = new WebSocket('ws://localhost:3000/ws');
        
        ws.onopen = function() {
            console.log('WebSocket connected');
            ws.send(JSON.stringify({
                type: 'auth',
                userId: getUserId()
            }));
        };
        
        ws.onmessage = function(event) {
            const data = JSON.parse(event.data);
            if (data.type === 'agent_message') {
                addMessage(data.content, 'agent');
            }
        };
        
        ws.onerror = function(error) {
            console.log('WebSocket error:', error);
        };
        
        ws.onclose = function() {
            console.log('WebSocket disconnected');
            // 尝试重连
            setTimeout(initWebSocket, 5000);
        };
        
        window.ws = ws;
    } catch (error) {
        console.log('WebSocket initialization failed:', error);
    }
}

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    // 初始化WebSocket
    initWebSocket();
    
    // 聚焦输入框
    document.getElementById('messageInput').focus();
    
    // 显示欢迎消息
    console.log('客服系统已就绪');
});