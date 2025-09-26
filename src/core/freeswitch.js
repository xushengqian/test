const esl = require('modesl');
const EventEmitter = require('events');
const winston = require('winston');
const { v4: uuidv4 } = require('uuid');

// 配置日志
const logger = winston.createLogger({
  level: 'info',
  format: winston.format.json(),
  transports: [
    new winston.transports.File({ filename: 'error.log', level: 'error' }),
    new winston.transports.File({ filename: 'combined.log' }),
    new winston.transports.Console({
      format: winston.format.simple()
    })
  ]
});

class FreeSwitchManager extends EventEmitter {
  constructor(config) {
    super();
    this.config = config;
    this.connection = null;
    this.activeCalls = new Map();
    this.agentPool = new Map();
    this.reconnectInterval = 5000;
    this.isConnected = false;
  }

  // 连接到FreeSWITCH
  async connect() {
    return new Promise((resolve, reject) => {
      this.connection = new esl.Connection(
        this.config.host,
        this.config.port,
        this.config.password
      );

      this.connection.on('esl::ready', () => {
        logger.info('Connected to FreeSWITCH');
        this.isConnected = true;
        this.setupEventListeners();
        resolve();
      });

      this.connection.on('error', (error) => {
        logger.error('FreeSWITCH connection error:', error);
        this.isConnected = false;
        reject(error);
        this.scheduleReconnect();
      });

      this.connection.on('esl::end', () => {
        logger.warn('FreeSWITCH connection ended');
        this.isConnected = false;
        this.scheduleReconnect();
      });
    });
  }

  // 设置事件监听器
  setupEventListeners() {
    // 订阅所有事件
    this.connection.events('json', 'ALL', () => {
      logger.info('Subscribed to all FreeSWITCH events');
    });

    // 监听通道事件
    this.connection.on('esl::event::**', (event) => {
      const eventName = event.getHeader('Event-Name');
      const callUuid = event.getHeader('Unique-ID');

      switch (eventName) {
        case 'CHANNEL_CREATE':
          this.handleChannelCreate(event);
          break;
        case 'CHANNEL_ANSWER':
          this.handleChannelAnswer(event);
          break;
        case 'CHANNEL_HANGUP':
          this.handleChannelHangup(event);
          break;
        case 'DTMF':
          this.handleDTMF(event);
          break;
        case 'CUSTOM':
          this.handleCustomEvent(event);
          break;
      }
    });
  }

  // 发起外呼
  async makeOutboundCall(phoneNumber, callbackUrl, metadata = {}) {
    const callUuid = uuidv4();
    const gateway = this.config.gateway || 'default_gateway';
    const callerId = this.config.callerId || '+8613800138000';
    
    const dialString = `{origination_uuid=${callUuid},origination_caller_id_number=${callerId}}sofia/gateway/${gateway}/${phoneNumber}`;
    
    const callInfo = {
      uuid: callUuid,
      phoneNumber,
      callbackUrl,
      metadata,
      startTime: new Date(),
      status: 'initiating',
      isRobot: true,
      transferredToAgent: false
    };

    this.activeCalls.set(callUuid, callInfo);

    return new Promise((resolve, reject) => {
      const command = `originate ${dialString} &park()`;
      
      this.connection.api(command, (response) => {
        if (response.body.includes('+OK')) {
          logger.info(`Outbound call initiated: ${callUuid} to ${phoneNumber}`);
          callInfo.status = 'ringing';
          this.emit('call.initiated', callInfo);
          resolve(callInfo);
        } else {
          logger.error(`Failed to initiate call: ${response.body}`);
          this.activeCalls.delete(callUuid);
          reject(new Error(response.body));
        }
      });
    });
  }

  // 处理通道创建事件
  handleChannelCreate(event) {
    const callUuid = event.getHeader('Unique-ID');
    const direction = event.getHeader('Call-Direction');
    
    if (direction === 'outbound') {
      const callInfo = this.activeCalls.get(callUuid);
      if (callInfo) {
        callInfo.status = 'ringing';
        this.emit('call.ringing', callInfo);
      }
    }
  }

  // 处理通道应答事件
  handleChannelAnswer(event) {
    const callUuid = event.getHeader('Unique-ID');
    const callInfo = this.activeCalls.get(callUuid);
    
    if (callInfo) {
      callInfo.status = 'answered';
      callInfo.answerTime = new Date();
      this.emit('call.answered', callInfo);
      
      // 如果是机器人模式，开始播放欢迎语
      if (callInfo.isRobot) {
        this.startRobotConversation(callUuid);
      }
    }
  }

  // 处理通道挂断事件
  handleChannelHangup(event) {
    const callUuid = event.getHeader('Unique-ID');
    const hangupCause = event.getHeader('Hangup-Cause');
    const callInfo = this.activeCalls.get(callUuid);
    
    if (callInfo) {
      callInfo.status = 'completed';
      callInfo.endTime = new Date();
      callInfo.hangupCause = hangupCause;
      
      // 计算通话时长
      if (callInfo.answerTime) {
        callInfo.duration = Math.floor((callInfo.endTime - callInfo.answerTime) / 1000);
      }
      
      this.emit('call.completed', callInfo);
      this.activeCalls.delete(callUuid);
    }
  }

  // 处理DTMF按键
  handleDTMF(event) {
    const callUuid = event.getHeader('Unique-ID');
    const digit = event.getHeader('DTMF-Digit');
    const callInfo = this.activeCalls.get(callUuid);
    
    if (callInfo) {
      logger.info(`DTMF received: ${digit} for call ${callUuid}`);
      
      // 如果按0，转人工
      if (digit === '0' && callInfo.isRobot && !callInfo.transferredToAgent) {
        this.transferToAgent(callUuid);
      }
      
      this.emit('call.dtmf', { callUuid, digit, callInfo });
    }
  }

  // 开始机器人对话
  async startRobotConversation(callUuid) {
    const callInfo = this.activeCalls.get(callUuid);
    if (!callInfo) return;

    // 播放欢迎语
    const welcomeText = '您好，我是智能客服助手。请问有什么可以帮助您的吗？如需转人工服务，请按0。';
    await this.playTTS(callUuid, welcomeText);
    
    // 开始录音和语音识别
    this.startRecording(callUuid);
    this.startASR(callUuid);
  }

  // 播放TTS语音
  async playTTS(callUuid, text) {
    return new Promise((resolve, reject) => {
      // 这里可以集成阿里云或其他TTS服务
      // 为简化示例，使用FreeSWITCH内置TTS
      const command = `uuid_broadcast ${callUuid} speak::flite|kal|${text}`;
      
      this.connection.api(command, (response) => {
        if (response.body.includes('+OK')) {
          logger.info(`TTS played for call ${callUuid}`);
          resolve();
        } else {
          logger.error(`Failed to play TTS: ${response.body}`);
          reject(new Error(response.body));
        }
      });
    });
  }

  // 开始录音
  startRecording(callUuid) {
    const recordFile = `/var/lib/freeswitch/recordings/${callUuid}.wav`;
    const command = `uuid_record ${callUuid} start ${recordFile}`;
    
    this.connection.api(command, (response) => {
      if (response.body.includes('+OK')) {
        logger.info(`Recording started for call ${callUuid}`);
        const callInfo = this.activeCalls.get(callUuid);
        if (callInfo) {
          callInfo.recordingFile = recordFile;
        }
      }
    });
  }

  // 开始语音识别
  startASR(callUuid) {
    // 这里需要集成实际的ASR服务
    // 可以使用阿里云、腾讯云、百度等ASR服务
    const callInfo = this.activeCalls.get(callUuid);
    if (callInfo) {
      callInfo.asrEnabled = true;
      this.emit('asr.started', { callUuid });
    }
  }

  // 转接到人工坐席
  async transferToAgent(callUuid) {
    const callInfo = this.activeCalls.get(callUuid);
    if (!callInfo || callInfo.transferredToAgent) return;

    logger.info(`Transferring call ${callUuid} to agent`);
    
    // 查找空闲坐席
    const availableAgent = this.findAvailableAgent();
    
    if (availableAgent) {
      // 播放转接提示
      await this.playTTS(callUuid, '正在为您转接人工客服，请稍候...');
      
      // 执行转接
      const bridgeCommand = `uuid_bridge ${callUuid} ${availableAgent.extension}`;
      
      this.connection.api(bridgeCommand, (response) => {
        if (response.body.includes('+OK')) {
          callInfo.transferredToAgent = true;
          callInfo.agentId = availableAgent.id;
          callInfo.transferTime = new Date();
          availableAgent.status = 'busy';
          availableAgent.currentCall = callUuid;
          
          this.emit('call.transferred', {
            callUuid,
            agentId: availableAgent.id,
            agentName: availableAgent.name
          });
          
          logger.info(`Call ${callUuid} transferred to agent ${availableAgent.name}`);
        } else {
          logger.error(`Failed to transfer call: ${response.body}`);
          this.playTTS(callUuid, '暂时没有空闲的客服，请稍后再试。');
        }
      });
    } else {
      // 没有空闲坐席，加入队列
      await this.addToQueue(callUuid);
    }
  }

  // 查找空闲坐席
  findAvailableAgent() {
    for (const [agentId, agent] of this.agentPool) {
      if (agent.status === 'available' && agent.isOnline) {
        return agent;
      }
    }
    return null;
  }

  // 加入等待队列
  async addToQueue(callUuid) {
    const callInfo = this.activeCalls.get(callUuid);
    if (!callInfo) return;

    await this.playTTS(callUuid, '所有客服正在忙碌中，您已进入等待队列，请耐心等待。');
    
    // 播放等待音乐
    const command = `uuid_broadcast ${callUuid} playback::/var/lib/freeswitch/sounds/music/default/8000/suite-espanola-op-47-leyenda.wav`;
    
    this.connection.api(command, (response) => {
      if (response.body.includes('+OK')) {
        callInfo.inQueue = true;
        callInfo.queueTime = new Date();
        this.emit('call.queued', { callUuid });
      }
    });
  }

  // 注册坐席
  registerAgent(agentInfo) {
    const agent = {
      id: agentInfo.id,
      name: agentInfo.name,
      extension: agentInfo.extension,
      skills: agentInfo.skills || [],
      status: 'available',
      isOnline: true,
      currentCall: null,
      loginTime: new Date()
    };
    
    this.agentPool.set(agent.id, agent);
    logger.info(`Agent registered: ${agent.name} (${agent.extension})`);
    this.emit('agent.registered', agent);
    
    return agent;
  }

  // 坐席下线
  unregisterAgent(agentId) {
    const agent = this.agentPool.get(agentId);
    if (agent) {
      agent.isOnline = false;
      agent.status = 'offline';
      this.agentPool.delete(agentId);
      logger.info(`Agent unregistered: ${agent.name}`);
      this.emit('agent.unregistered', agent);
    }
  }

  // 更新坐席状态
  updateAgentStatus(agentId, status) {
    const agent = this.agentPool.get(agentId);
    if (agent) {
      const oldStatus = agent.status;
      agent.status = status;
      
      if (status === 'available' && agent.currentCall) {
        agent.currentCall = null;
      }
      
      logger.info(`Agent ${agent.name} status changed: ${oldStatus} -> ${status}`);
      this.emit('agent.statusChanged', { agent, oldStatus, newStatus: status });
      
      // 检查队列中是否有等待的呼叫
      if (status === 'available') {
        this.checkQueuedCalls();
      }
    }
  }

  // 检查队列中的呼叫
  checkQueuedCalls() {
    for (const [callUuid, callInfo] of this.activeCalls) {
      if (callInfo.inQueue && !callInfo.transferredToAgent) {
        this.transferToAgent(callUuid);
        break; // 一次只处理一个
      }
    }
  }

  // 挂断呼叫
  hangupCall(callUuid) {
    const command = `uuid_kill ${callUuid}`;
    
    this.connection.api(command, (response) => {
      if (response.body.includes('+OK')) {
        logger.info(`Call ${callUuid} hung up`);
      } else {
        logger.error(`Failed to hangup call: ${response.body}`);
      }
    });
  }

  // 获取呼叫状态
  getCallStatus(callUuid) {
    return this.activeCalls.get(callUuid);
  }

  // 获取所有活动呼叫
  getActiveCalls() {
    return Array.from(this.activeCalls.values());
  }

  // 获取所有坐席
  getAgents() {
    return Array.from(this.agentPool.values());
  }

  // 重连机制
  scheduleReconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }
    
    this.reconnectTimer = setTimeout(() => {
      logger.info('Attempting to reconnect to FreeSWITCH...');
      this.connect().catch((error) => {
        logger.error('Reconnection failed:', error);
      });
    }, this.reconnectInterval);
  }

  // 断开连接
  disconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
    }
    
    if (this.connection) {
      this.connection.disconnect();
      this.connection = null;
    }
    
    this.isConnected = false;
    logger.info('Disconnected from FreeSWITCH');
  }

  // 处理自定义事件
  handleCustomEvent(event) {
    const subclass = event.getHeader('Event-Subclass');
    const callUuid = event.getHeader('Unique-ID');
    
    // 处理ASR结果
    if (subclass === 'asr::result') {
      const asrText = event.getHeader('ASR-Text');
      const confidence = event.getHeader('ASR-Confidence');
      
      this.handleASRResult(callUuid, asrText, confidence);
    }
  }

  // 处理ASR识别结果
  async handleASRResult(callUuid, text, confidence) {
    const callInfo = this.activeCalls.get(callUuid);
    if (!callInfo || !callInfo.isRobot) return;

    logger.info(`ASR result for ${callUuid}: ${text} (confidence: ${confidence})`);
    
    // 发送到对话管理系统
    this.emit('asr.result', {
      callUuid,
      text,
      confidence,
      callInfo
    });
    
    // 根据识别结果生成回复
    const response = await this.generateResponse(text, callInfo);
    
    if (response) {
      await this.playTTS(callUuid, response);
    }
  }

  // 生成对话回复
  async generateResponse(userInput, callInfo) {
    // 这里可以集成对话管理系统、知识库等
    // 简单示例：关键词匹配
    
    if (userInput.includes('人工') || userInput.includes('客服') || userInput.includes('转接')) {
      await this.transferToAgent(callInfo.uuid);
      return null;
    }
    
    if (userInput.includes('查询') || userInput.includes('订单')) {
      return '请提供您的订单号，我来为您查询。';
    }
    
    if (userInput.includes('退款') || userInput.includes('退货')) {
      return '关于退款退货，请问您的订单是否在7天内？';
    }
    
    // 默认回复
    return '抱歉，我没有完全理解您的问题。请您再说一遍，或按0转人工客服。';
  }
}

module.exports = FreeSwitchManager;