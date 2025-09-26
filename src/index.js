require('dotenv').config();
const express = require('express');
const http = require('http');
const socketIO = require('socket.io');
const cors = require('cors');
const bodyParser = require('body-parser');
const path = require('path');
const winston = require('winston');

// 导入服务模块
const FreeSwitchManager = require('./core/freeswitch');
const CallManager = require('./services/callManager');
const AgentManager = require('./services/agentManager');
const SpeechService = require('./services/speechService');
const DatabaseService = require('./services/database');
const setupRoutes = require('./api/routes');

// 配置日志
const logger = winston.createLogger({
  level: 'info',
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.json()
  ),
  transports: [
    new winston.transports.File({ filename: 'error.log', level: 'error' }),
    new winston.transports.File({ filename: 'combined.log' }),
    new winston.transports.Console({
      format: winston.format.simple()
    })
  ]
});

// 创建Express应用
const app = express();
const server = http.createServer(app);
const io = socketIO(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});

// 中间件配置
app.use(cors());
app.use(bodyParser.json());
app.use(bodyParser.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname, '../public')));

// Redis客户端配置
const redis = require('ioredis');
const redisClient = new redis({
  host: process.env.REDIS_HOST || 'localhost',
  port: process.env.REDIS_PORT || 6379,
  password: process.env.REDIS_PASSWORD || undefined
});

// 初始化服务
let services = {};

async function initializeServices() {
  try {
    logger.info('Initializing services...');

    // 初始化数据库服务
    const dbService = new DatabaseService({
      host: process.env.DB_HOST,
      port: process.env.DB_PORT,
      database: process.env.DB_NAME,
      username: process.env.DB_USER,
      password: process.env.DB_PASSWORD
    });
    await dbService.initialize();

    // 初始化FreeSWITCH管理器
    const fsManager = new FreeSwitchManager({
      host: process.env.FS_HOST,
      port: process.env.FS_PORT,
      password: process.env.FS_PASSWORD,
      gateway: process.env.FS_SIP_PROFILE,
      callerId: process.env.OUTBOUND_CALLER_ID
    });
    await fsManager.connect();

    // 初始化语音服务
    const speechService = new SpeechService('ali', {
      accessKeyId: process.env.ALI_ACCESS_KEY_ID,
      accessKeySecret: process.env.ALI_ACCESS_KEY_SECRET,
      appKey: process.env.ALI_APP_KEY,
      ttsAppKey: process.env.ALI_TTS_APP_KEY
    });

    // 初始化坐席管理器
    const agentManager = new AgentManager(fsManager, dbService, redisClient);
    await agentManager.initialize();

    // 初始化呼叫管理器
    const callManager = new CallManager(fsManager, dbService, redisClient);
    await callManager.initialize();

    // 保存服务实例
    services = {
      fsManager,
      callManager,
      agentManager,
      speechService,
      dbService
    };

    // 设置事件监听
    setupEventListeners();

    logger.info('All services initialized successfully');
    return true;
  } catch (error) {
    logger.error('Failed to initialize services:', error);
    throw error;
  }
}

// 设置事件监听器
function setupEventListeners() {
  const { fsManager, callManager, agentManager, speechService } = services;

  // FreeSWITCH事件
  fsManager.on('call.initiated', (data) => {
    io.emit('call.new', data);
    updateRealtimeStats();
  });

  fsManager.on('call.answered', (data) => {
    io.emit('call.answered', data);
    updateRealtimeStats();
  });

  fsManager.on('call.completed', async (data) => {
    io.emit('call.completed', data);
    await callManager.handleCallResult(data.uuid, data);
    updateRealtimeStats();
  });

  fsManager.on('call.transferred', (data) => {
    io.emit('call.transferred', data);
    updateRealtimeStats();
  });

  // 坐席事件
  agentManager.on('agent.login', (data) => {
    io.emit('agent.login', data);
    updateRealtimeStats();
  });

  agentManager.on('agent.logout', (data) => {
    io.emit('agent.logout', data);
    updateRealtimeStats();
  });

  agentManager.on('agent.statusChanged', (data) => {
    io.emit('agent.statusChanged', data);
    updateRealtimeStats();
  });

  // 外呼任务事件
  callManager.on('campaign.started', (data) => {
    io.emit('campaign.update', data);
  });

  callManager.on('campaign.completed', (data) => {
    io.emit('campaign.update', data);
  });

  // 语音识别事件
  speechService.on('asr.result', async (data) => {
    await fsManager.handleASRResult(data.callUuid, data.text, data.confidence);
  });

  // 对话管理事件
  fsManager.on('asr.result', async (data) => {
    // 处理对话逻辑
    const response = await processDialogue(data);
    if (response) {
      io.emit('dialogue.update', { callUuid: data.callUuid, userInput: data.text, botResponse: response });
    }
  });
}

// 处理对话逻辑
async function processDialogue(data) {
  const { callUuid, text, callInfo } = data;
  
  // 这里可以集成更复杂的NLU和对话管理系统
  // 简单示例：关键词匹配
  
  if (text.includes('人工') || text.includes('客服')) {
    // 转人工处理已在FreeSWITCH管理器中实现
    return null;
  }
  
  // 可以集成第三方对话系统，如：
  // - Rasa
  // - Dialogflow
  // - 自定义NLU模型
  
  return null;
}

// 更新实时统计
async function updateRealtimeStats() {
  try {
    const stats = await services.callManager.getRealTimeStats();
    io.emit('stats.update', stats);
  } catch (error) {
    logger.error('Failed to update realtime stats:', error);
  }
}

// WebSocket连接处理
io.on('connection', (socket) => {
  logger.info('New WebSocket connection:', socket.id);

  // 验证token
  const token = socket.handshake.auth.token;
  if (!token) {
    socket.disconnect();
    return;
  }

  // 发送初始统计数据
  services.callManager.getRealTimeStats().then(stats => {
    socket.emit('stats.update', stats);
  });

  // 处理客户端事件
  socket.on('get.stats', async () => {
    const stats = await services.callManager.getRealTimeStats();
    socket.emit('stats.update', stats);
  });

  socket.on('agent.status', async (data) => {
    try {
      await services.agentManager.changeAgentStatus(data.agentId, data.status);
    } catch (error) {
      socket.emit('error', { message: error.message });
    }
  });

  socket.on('disconnect', () => {
    logger.info('WebSocket disconnected:', socket.id);
  });
});

// 设置API路由
setupRoutes(app, services);

// 错误处理中间件
app.use((err, req, res, next) => {
  logger.error('Unhandled error:', err);
  res.status(500).json({
    success: false,
    error: 'Internal server error'
  });
});

// 优雅关闭处理
process.on('SIGTERM', gracefulShutdown);
process.on('SIGINT', gracefulShutdown);

async function gracefulShutdown() {
  logger.info('Shutting down gracefully...');
  
  // 停止接受新的连接
  server.close(() => {
    logger.info('HTTP server closed');
  });

  // 关闭所有WebSocket连接
  io.close(() => {
    logger.info('WebSocket server closed');
  });

  // 断开服务连接
  if (services.fsManager) {
    services.fsManager.disconnect();
  }
  
  if (services.dbService) {
    await services.dbService.close();
  }

  // 关闭Redis连接
  redisClient.disconnect();

  logger.info('Shutdown complete');
  process.exit(0);
}

// 启动服务器
async function startServer() {
  try {
    // 初始化所有服务
    await initializeServices();

    // 启动HTTP服务器
    const port = process.env.SERVER_PORT || 3000;
    server.listen(port, () => {
      logger.info(`Server is running on port ${port}`);
      logger.info(`WebSocket server is ready`);
      logger.info(`API endpoint: http://localhost:${port}/api`);
      logger.info(`Web interface: http://localhost:${port}`);
    });

    // 定期更新统计数据
    setInterval(updateRealtimeStats, 5000);

  } catch (error) {
    logger.error('Failed to start server:', error);
    process.exit(1);
  }
}

// 启动应用
startServer();