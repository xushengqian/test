const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const WebSocket = require('ws');
const path = require('path');
const http = require('http');

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

// 中间件
app.use(cors());
app.use(bodyParser.json());
app.use(express.static('.'));

// 存储客服队列和会话
const queue = [];
const sessions = new Map();
const agents = new Map();

// 客服人员数据（模拟）
const availableAgents = [
    { id: 'CS001', name: '李客服', status: 'available', skills: ['订单', '退款'] },
    { id: 'CS002', name: '王客服', status: 'available', skills: ['技术', '产品'] },
    { id: 'CS003', name: '张客服', status: 'available', skills: ['投诉', '建议'] }
];

// WebSocket连接管理
wss.on('connection', (ws) => {
    console.log('新的WebSocket连接');
    
    ws.on('message', (message) => {
        try {
            const data = JSON.parse(message);
            
            switch (data.type) {
                case 'auth':
                    // 用户认证
                    ws.userId = data.userId;
                    sessions.set(data.userId, ws);
                    console.log(`用户 ${data.userId} 已连接`);
                    break;
                    
                case 'agent_auth':
                    // 客服认证
                    ws.agentId = data.agentId;
                    agents.set(data.agentId, ws);
                    console.log(`客服 ${data.agentId} 已上线`);
                    break;
                    
                case 'message':
                    // 转发消息
                    handleMessage(data);
                    break;
            }
        } catch (error) {
            console.error('处理WebSocket消息错误:', error);
        }
    });
    
    ws.on('close', () => {
        if (ws.userId) {
            sessions.delete(ws.userId);
            console.log(`用户 ${ws.userId} 已断开连接`);
        }
        if (ws.agentId) {
            agents.delete(ws.agentId);
            console.log(`客服 ${ws.agentId} 已下线`);
        }
    });
});

// 转人工API
app.post('/api/transfer-to-agent', async (req, res) => {
    const { userId, messages, timestamp } = req.body;
    
    console.log(`用户 ${userId} 请求转人工服务`);
    
    try {
        // 分析用户问题类型
        const problemType = analyzeProblemType(messages);
        
        // 查找可用客服
        const agent = findAvailableAgent(problemType);
        
        if (agent) {
            // 分配客服
            const session = {
                userId,
                agentId: agent.id,
                agentName: agent.name,
                messages,
                startTime: timestamp,
                status: 'connected',
                queuePosition: 0
            };
            
            // 保存会话
            sessions.set(userId, session);
            
            // 更新客服状态
            agent.status = 'busy';
            agent.currentUser = userId;
            
            // 通知客服有新用户
            notifyAgent(agent.id, session);
            
            res.json({
                success: true,
                agentId: agent.id,
                agentName: agent.name,
                queuePosition: 0,
                estimatedWaitTime: 0
            });
            
            console.log(`已为用户 ${userId} 分配客服 ${agent.name}`);
        } else {
            // 加入等待队列
            const queuePosition = queue.length + 1;
            queue.push({
                userId,
                messages,
                timestamp,
                problemType,
                queuePosition
            });
            
            res.json({
                success: true,
                queuePosition,
                estimatedWaitTime: queuePosition * 60, // 每人预计1分钟
                message: '当前客服繁忙，您已进入等待队列'
            });
            
            console.log(`用户 ${userId} 已加入等待队列，位置: ${queuePosition}`);
        }
    } catch (error) {
        console.error('转人工处理错误:', error);
        res.status(500).json({
            success: false,
            message: '系统错误，请稍后重试'
        });
    }
});

// 发送消息到客服
app.post('/api/send-to-agent', async (req, res) => {
    const { userId, message, timestamp } = req.body;
    
    try {
        const session = sessions.get(userId);
        
        if (session && session.agentId) {
            // 记录消息
            if (!session.conversation) {
                session.conversation = [];
            }
            session.conversation.push({
                sender: 'user',
                message,
                timestamp
            });
            
            // 通过WebSocket发送给客服
            const agentWs = agents.get(session.agentId);
            if (agentWs && agentWs.readyState === WebSocket.OPEN) {
                agentWs.send(JSON.stringify({
                    type: 'user_message',
                    userId,
                    message,
                    timestamp
                }));
            }
            
            res.json({ success: true });
            console.log(`消息已转发: ${userId} -> ${session.agentId}`);
        } else {
            res.status(404).json({
                success: false,
                message: '未找到客服会话'
            });
        }
    } catch (error) {
        console.error('发送消息错误:', error);
        res.status(500).json({
            success: false,
            message: '发送失败'
        });
    }
});

// AI聊天API
app.post('/api/ai-chat', async (req, res) => {
    const { message, context } = req.body;
    
    try {
        // 简单的AI回复逻辑（可以接入实际的AI服务）
        const reply = generateAIReply(message, context);
        
        res.json({
            success: true,
            reply
        });
    } catch (error) {
        console.error('AI聊天错误:', error);
        res.status(500).json({
            success: false,
            message: '系统错误'
        });
    }
});

// 结束会话
app.post('/api/end-session', async (req, res) => {
    const { userId, agentId } = req.body;
    
    try {
        const session = sessions.get(userId);
        
        if (session) {
            session.status = 'ended';
            session.endTime = new Date().toISOString();
            
            // 释放客服
            const agent = availableAgents.find(a => a.id === agentId);
            if (agent) {
                agent.status = 'available';
                agent.currentUser = null;
            }
            
            // 处理队列中的下一个用户
            processQueue();
            
            res.json({ success: true });
            console.log(`会话已结束: ${userId}`);
        } else {
            res.status(404).json({
                success: false,
                message: '会话不存在'
            });
        }
    } catch (error) {
        console.error('结束会话错误:', error);
        res.status(500).json({
            success: false,
            message: '操作失败'
        });
    }
});

// 获取客服状态
app.get('/api/agent-status', (req, res) => {
    const status = availableAgents.map(agent => ({
        id: agent.id,
        name: agent.name,
        status: agent.status,
        currentUser: agent.currentUser
    }));
    
    res.json({
        agents: status,
        queueLength: queue.length,
        activeSessions: Array.from(sessions.values()).filter(s => s.status === 'connected').length
    });
});

// 辅助函数

// 分析问题类型
function analyzeProblemType(messages) {
    const keywords = {
        '订单': ['订单', '购买', '下单', '支付'],
        '退款': ['退款', '退货', '退换', '售后'],
        '技术': ['故障', '无法', '错误', 'bug'],
        '投诉': ['投诉', '不满', '差评', '建议']
    };
    
    // 分析最近的消息
    const recentMessages = messages.slice(-5).map(m => m.content).join(' ');
    
    for (const [type, words] of Object.entries(keywords)) {
        if (words.some(word => recentMessages.includes(word))) {
            return type;
        }
    }
    
    return '通用';
}

// 查找可用客服
function findAvailableAgent(problemType) {
    // 优先匹配技能相符的客服
    let agent = availableAgents.find(a => 
        a.status === 'available' && a.skills.includes(problemType)
    );
    
    // 如果没有技能匹配的，分配任意可用客服
    if (!agent) {
        agent = availableAgents.find(a => a.status === 'available');
    }
    
    return agent;
}

// 通知客服
function notifyAgent(agentId, session) {
    const agentWs = agents.get(agentId);
    
    if (agentWs && agentWs.readyState === WebSocket.OPEN) {
        agentWs.send(JSON.stringify({
            type: 'new_session',
            session
        }));
    }
}

// 处理等待队列
function processQueue() {
    if (queue.length === 0) return;
    
    const availableAgent = availableAgents.find(a => a.status === 'available');
    
    if (availableAgent) {
        const nextUser = queue.shift();
        
        // 分配客服
        const session = {
            userId: nextUser.userId,
            agentId: availableAgent.id,
            agentName: availableAgent.name,
            messages: nextUser.messages,
            startTime: new Date().toISOString(),
            status: 'connected',
            queuePosition: 0
        };
        
        sessions.set(nextUser.userId, session);
        availableAgent.status = 'busy';
        availableAgent.currentUser = nextUser.userId;
        
        // 通知用户
        const userWs = sessions.get(nextUser.userId);
        if (userWs && userWs.readyState === WebSocket.OPEN) {
            userWs.send(JSON.stringify({
                type: 'agent_connected',
                agentId: availableAgent.id,
                agentName: availableAgent.name
            }));
        }
        
        // 通知客服
        notifyAgent(availableAgent.id, session);
        
        console.log(`队列用户 ${nextUser.userId} 已分配给客服 ${availableAgent.name}`);
    }
}

// 生成AI回复（简化版）
function generateAIReply(message, context) {
    const responses = {
        greeting: ['您好！很高兴为您服务。', '您好！请问有什么可以帮助您的？'],
        order: ['我可以帮您查询订单信息，请提供您的订单号。', '关于订单问题，请告诉我您的订单号码。'],
        refund: ['退款申请需要提供订单号和退款原因，我来帮您处理。', '我理解您的退款需求，请提供相关订单信息。'],
        help: ['我在这里为您服务，请告诉我您遇到的问题。', '请详细描述您需要的帮助，我会尽力协助您。'],
        thanks: ['不客气！还有其他需要帮助的吗？', '很高兴能帮到您！'],
        default: ['我正在理解您的问题，请稍等。', '让我来帮您处理这个问题。']
    };
    
    // 简单的关键词匹配
    const lowerMessage = message.toLowerCase();
    
    if (lowerMessage.includes('你好') || lowerMessage.includes('hi')) {
        return responses.greeting[Math.floor(Math.random() * responses.greeting.length)];
    } else if (lowerMessage.includes('订单')) {
        return responses.order[Math.floor(Math.random() * responses.order.length)];
    } else if (lowerMessage.includes('退款') || lowerMessage.includes('退货')) {
        return responses.refund[Math.floor(Math.random() * responses.refund.length)];
    } else if (lowerMessage.includes('帮助') || lowerMessage.includes('help')) {
        return responses.help[Math.floor(Math.random() * responses.help.length)];
    } else if (lowerMessage.includes('谢谢') || lowerMessage.includes('thanks')) {
        return responses.thanks[Math.floor(Math.random() * responses.thanks.length)];
    } else {
        return responses.default[Math.floor(Math.random() * responses.default.length)];
    }
}

// 启动服务器
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
    console.log(`服务器运行在 http://localhost:${PORT}`);
    console.log(`WebSocket服务已启动`);
    console.log('\n可用的API端点:');
    console.log('- POST /api/transfer-to-agent  转人工客服');
    console.log('- POST /api/send-to-agent      发送消息到客服');
    console.log('- POST /api/ai-chat            AI聊天');
    console.log('- POST /api/end-session        结束会话');
    console.log('- GET  /api/agent-status       获取客服状态');
});