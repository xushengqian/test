const express = require('express');
const http = require('http');
const socketIo = require('socket.io');
const cors = require('cors');
const bodyParser = require('body-parser');
const path = require('path');

// 导入核心模块
const CallManager = require('./src/core/CallManager');
const AgentManager = require('./src/core/AgentManager');
const RobotDialogue = require('./src/core/RobotDialogue');
const CallRoutes = require('./src/routes/callRoutes');
const AgentRoutes = require('./src/routes/agentRoutes');

const app = express();
const server = http.createServer(app);
const io = socketIo(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});

// 中间件配置
app.use(cors());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname, 'public')));

// 初始化核心管理器
const callManager = new CallManager(io);
const agentManager = new AgentManager(io);
const robotDialogue = new RobotDialogue();

// 将管理器实例传递给路由
app.use('/api/calls', CallRoutes(callManager, agentManager, robotDialogue));
app.use('/api/agents', AgentRoutes(agentManager, callManager));

// Socket.IO 连接处理
io.on('connection', (socket) => {
  console.log('新的Socket连接:', socket.id);

  // 坐席连接
  socket.on('agent_connect', (data) => {
    agentManager.connectAgent(socket, data);
  });

  // 坐席状态更新
  socket.on('agent_status_change', (data) => {
    agentManager.updateAgentStatus(socket.id, data.status);
  });

  // 坐席主动接入通话
  socket.on('agent_takeover', (data) => {
    const call = callManager.getCall(data.callId);
    if (call && call.status === 'robot_talking') {
      agentManager.takeoverCall(socket.id, data.callId, callManager);
    }
  });

  // 坐席发送消息
  socket.on('agent_message', (data) => {
    callManager.handleAgentMessage(data.callId, data.message, socket.id);
  });

  // 客户响应
  socket.on('customer_response', (data) => {
    const call = callManager.getCall(data.callId);
    if (call) {
      if (call.status === 'agent_talking') {
        // 转发给坐席
        const agent = agentManager.getAgentByCallId(data.callId);
        if (agent) {
          io.to(agent.socketId).emit('customer_message', {
            callId: data.callId,
            message: data.message,
            timestamp: new Date()
          });
        }
      } else if (call.status === 'robot_talking') {
        // 机器人处理
        robotDialogue.processCustomerResponse(data.callId, data.message)
          .then(response => {
            callManager.handleRobotResponse(data.callId, response);
          });
      }
    }
  });

  // 通话结束
  socket.on('call_end', (data) => {
    callManager.endCall(data.callId);
    agentManager.releaseAgent(socket.id);
  });

  // 断开连接
  socket.on('disconnect', () => {
    console.log('Socket断开连接:', socket.id);
    agentManager.disconnectAgent(socket.id);
  });
});

// 主页路由
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// 坐席工作台
app.get('/agent', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'agent.html'));
});

// 管理后台
app.get('/admin', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'admin.html'));
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`机器人外呼系统启动成功，端口: ${PORT}`);
  console.log(`坐席工作台: http://localhost:${PORT}/agent`);
  console.log(`管理后台: http://localhost:${PORT}/admin`);
});

module.exports = { app, server, io };