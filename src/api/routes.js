const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const multer = require('multer');
const path = require('path');

// 文件上传配置
const upload = multer({
  dest: 'uploads/',
  limits: {
    fileSize: 10 * 1024 * 1024 // 10MB
  },
  fileFilter: (req, file, cb) => {
    const allowedTypes = ['.csv', '.xlsx', '.txt'];
    const ext = path.extname(file.originalname).toLowerCase();
    if (allowedTypes.includes(ext)) {
      cb(null, true);
    } else {
      cb(new Error('Invalid file type'));
    }
  }
});

// 中间件：验证JWT token
const authenticateToken = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];

  if (!token) {
    return res.status(401).json({ error: 'Access token required' });
  }

  jwt.verify(token, process.env.JWT_SECRET, (err, user) => {
    if (err) {
      return res.status(403).json({ error: 'Invalid or expired token' });
    }
    req.user = user;
    next();
  });
};

// 中间件：验证管理员权限
const requireAdmin = (req, res, next) => {
  if (req.user.role !== 'admin') {
    return res.status(403).json({ error: 'Admin privileges required' });
  }
  next();
};

// 中间件：验证坐席权限
const requireAgent = (req, res, next) => {
  if (!['agent', 'supervisor', 'admin'].includes(req.user.role)) {
    return res.status(403).json({ error: 'Agent privileges required' });
  }
  next();
};

module.exports = function(app, services) {
  const { fsManager, callManager, agentManager, dbService, speechService } = services;

  // ==================== 认证相关 ====================
  
  // 用户登录
  router.post('/auth/login', async (req, res) => {
    try {
      const { username, password, type } = req.body;
      
      let user;
      if (type === 'agent') {
        // 坐席登录
        user = await dbService.getAgentByUsername(username);
        if (!user || !await bcrypt.compare(password, user.password)) {
          return res.status(401).json({ error: 'Invalid credentials' });
        }
      } else {
        // 管理员登录
        user = await dbService.getAdminByUsername(username);
        if (!user || !await bcrypt.compare(password, user.password)) {
          return res.status(401).json({ error: 'Invalid credentials' });
        }
      }

      // 生成JWT token
      const token = jwt.sign(
        { 
          id: user.id, 
          username: user.username, 
          role: user.role || type,
          name: user.name 
        },
        process.env.JWT_SECRET,
        { expiresIn: process.env.JWT_EXPIRE || '24h' }
      );

      res.json({
        success: true,
        token,
        user: {
          id: user.id,
          username: user.username,
          name: user.name,
          role: user.role || type
        }
      });
    } catch (error) {
      console.error('Login error:', error);
      res.status(500).json({ error: 'Login failed' });
    }
  });

  // 用户登出
  router.post('/auth/logout', authenticateToken, async (req, res) => {
    try {
      // 如果是坐席，执行坐席登出
      if (req.user.role === 'agent') {
        await agentManager.agentLogout(req.user.id);
      }
      
      res.json({ success: true, message: 'Logged out successfully' });
    } catch (error) {
      console.error('Logout error:', error);
      res.status(500).json({ error: 'Logout failed' });
    }
  });

  // ==================== 外呼任务管理 ====================
  
  // 创建外呼任务
  router.post('/campaigns', authenticateToken, requireAdmin, async (req, res) => {
    try {
      const campaign = await callManager.createCampaign(req.body);
      res.json({ success: true, campaign });
    } catch (error) {
      console.error('Create campaign error:', error);
      res.status(500).json({ error: 'Failed to create campaign' });
    }
  });

  // 获取所有外呼任务
  router.get('/campaigns', authenticateToken, async (req, res) => {
    try {
      const campaigns = await callManager.getAllCampaigns();
      res.json({ success: true, campaigns });
    } catch (error) {
      console.error('Get campaigns error:', error);
      res.status(500).json({ error: 'Failed to get campaigns' });
    }
  });

  // 获取单个外呼任务详情
  router.get('/campaigns/:id', authenticateToken, async (req, res) => {
    try {
      const campaign = callManager.getCampaignStatus(req.params.id);
      if (!campaign) {
        return res.status(404).json({ error: 'Campaign not found' });
      }
      res.json({ success: true, campaign });
    } catch (error) {
      console.error('Get campaign error:', error);
      res.status(500).json({ error: 'Failed to get campaign' });
    }
  });

  // 开始外呼任务
  router.post('/campaigns/:id/start', authenticateToken, requireAdmin, async (req, res) => {
    try {
      await callManager.startCampaign(req.params.id);
      res.json({ success: true, message: 'Campaign started' });
    } catch (error) {
      console.error('Start campaign error:', error);
      res.status(500).json({ error: 'Failed to start campaign' });
    }
  });

  // 暂停外呼任务
  router.post('/campaigns/:id/pause', authenticateToken, requireAdmin, async (req, res) => {
    try {
      const campaign = await callManager.pauseCampaign(req.params.id);
      res.json({ success: true, campaign });
    } catch (error) {
      console.error('Pause campaign error:', error);
      res.status(500).json({ error: 'Failed to pause campaign' });
    }
  });

  // 恢复外呼任务
  router.post('/campaigns/:id/resume', authenticateToken, requireAdmin, async (req, res) => {
    try {
      const campaign = await callManager.resumeCampaign(req.params.id);
      res.json({ success: true, campaign });
    } catch (error) {
      console.error('Resume campaign error:', error);
      res.status(500).json({ error: 'Failed to resume campaign' });
    }
  });

  // 取消外呼任务
  router.post('/campaigns/:id/cancel', authenticateToken, requireAdmin, async (req, res) => {
    try {
      const campaign = await callManager.cancelCampaign(req.params.id);
      res.json({ success: true, campaign });
    } catch (error) {
      console.error('Cancel campaign error:', error);
      res.status(500).json({ error: 'Failed to cancel campaign' });
    }
  });

  // 导入号码列表
  router.post('/campaigns/:id/import', authenticateToken, requireAdmin, upload.single('file'), async (req, res) => {
    try {
      const count = await callManager.importPhoneList(req.file.path, req.params.id);
      res.json({ success: true, imported: count });
    } catch (error) {
      console.error('Import phones error:', error);
      res.status(500).json({ error: 'Failed to import phone list' });
    }
  });

  // ==================== 呼叫控制 ====================
  
  // 发起单个外呼
  router.post('/calls/outbound', authenticateToken, requireAgent, async (req, res) => {
    try {
      const { phoneNumber, metadata } = req.body;
      const callInfo = await fsManager.makeOutboundCall(
        phoneNumber,
        `${process.env.API_URL}/api/callback`,
        metadata
      );
      res.json({ success: true, call: callInfo });
    } catch (error) {
      console.error('Make call error:', error);
      res.status(500).json({ error: 'Failed to make call' });
    }
  });

  // 挂断呼叫
  router.post('/calls/:uuid/hangup', authenticateToken, requireAgent, async (req, res) => {
    try {
      fsManager.hangupCall(req.params.uuid);
      res.json({ success: true, message: 'Call hung up' });
    } catch (error) {
      console.error('Hangup call error:', error);
      res.status(500).json({ error: 'Failed to hangup call' });
    }
  });

  // 转接到人工
  router.post('/calls/:uuid/transfer', authenticateToken, async (req, res) => {
    try {
      await fsManager.transferToAgent(req.params.uuid);
      res.json({ success: true, message: 'Call transferred to agent' });
    } catch (error) {
      console.error('Transfer call error:', error);
      res.status(500).json({ error: 'Failed to transfer call' });
    }
  });

  // 获取活动呼叫列表
  router.get('/calls/active', authenticateToken, async (req, res) => {
    try {
      const calls = fsManager.getActiveCalls();
      res.json({ success: true, calls });
    } catch (error) {
      console.error('Get active calls error:', error);
      res.status(500).json({ error: 'Failed to get active calls' });
    }
  });

  // 获取呼叫详情
  router.get('/calls/:uuid', authenticateToken, async (req, res) => {
    try {
      const call = fsManager.getCallStatus(req.params.uuid);
      if (!call) {
        return res.status(404).json({ error: 'Call not found' });
      }
      res.json({ success: true, call });
    } catch (error) {
      console.error('Get call error:', error);
      res.status(500).json({ error: 'Failed to get call' });
    }
  });

  // ==================== 坐席管理 ====================
  
  // 坐席签入
  router.post('/agents/login', authenticateToken, requireAgent, async (req, res) => {
    try {
      const { extension } = req.body;
      const result = await agentManager.agentLogin(
        req.user.id,
        extension,
        req.body.password
      );
      res.json(result);
    } catch (error) {
      console.error('Agent login error:', error);
      res.status(500).json({ error: error.message });
    }
  });

  // 坐席签出
  router.post('/agents/logout', authenticateToken, requireAgent, async (req, res) => {
    try {
      const result = await agentManager.agentLogout(req.user.id);
      res.json(result);
    } catch (error) {
      console.error('Agent logout error:', error);
      res.status(500).json({ error: error.message });
    }
  });

  // 更改坐席状态
  router.post('/agents/status', authenticateToken, requireAgent, async (req, res) => {
    try {
      const { status, reason } = req.body;
      const result = await agentManager.changeAgentStatus(
        req.user.id,
        status,
        reason
      );
      res.json(result);
    } catch (error) {
      console.error('Change agent status error:', error);
      res.status(500).json({ error: error.message });
    }
  });

  // 获取所有坐席状态
  router.get('/agents/status', authenticateToken, async (req, res) => {
    try {
      const agents = agentManager.getAllAgentsStatus();
      res.json({ success: true, agents });
    } catch (error) {
      console.error('Get agents status error:', error);
      res.status(500).json({ error: 'Failed to get agents status' });
    }
  });

  // 获取坐席详情
  router.get('/agents/:id', authenticateToken, async (req, res) => {
    try {
      const agent = agentManager.getAgentInfo(req.params.id);
      if (!agent) {
        return res.status(404).json({ error: 'Agent not found' });
      }
      res.json({ success: true, agent });
    } catch (error) {
      console.error('Get agent error:', error);
      res.status(500).json({ error: 'Failed to get agent' });
    }
  });

  // 获取坐席报表
  router.get('/agents/:id/report', authenticateToken, async (req, res) => {
    try {
      const { startDate, endDate } = req.query;
      const report = await agentManager.generateAgentReport(
        req.params.id,
        new Date(startDate),
        new Date(endDate)
      );
      res.json({ success: true, report });
    } catch (error) {
      console.error('Generate agent report error:', error);
      res.status(500).json({ error: 'Failed to generate report' });
    }
  });

  // ==================== 队列管理 ====================
  
  // 获取队列状态
  router.get('/queues', authenticateToken, async (req, res) => {
    try {
      const queues = agentManager.getQueueStatus();
      res.json({ success: true, queues });
    } catch (error) {
      console.error('Get queues error:', error);
      res.status(500).json({ error: 'Failed to get queues' });
    }
  });

  // 获取单个队列详情
  router.get('/queues/:id', authenticateToken, async (req, res) => {
    try {
      const queue = agentManager.getQueueStatus(req.params.id);
      if (!queue) {
        return res.status(404).json({ error: 'Queue not found' });
      }
      res.json({ success: true, queue });
    } catch (error) {
      console.error('Get queue error:', error);
      res.status(500).json({ error: 'Failed to get queue' });
    }
  });

  // ==================== 实时统计 ====================
  
  // 获取实时统计数据
  router.get('/stats/realtime', authenticateToken, async (req, res) => {
    try {
      const stats = await callManager.getRealTimeStats();
      res.json({ success: true, stats });
    } catch (error) {
      console.error('Get realtime stats error:', error);
      res.status(500).json({ error: 'Failed to get realtime stats' });
    }
  });

  // 获取历史统计
  router.get('/stats/history', authenticateToken, async (req, res) => {
    try {
      const { startDate, endDate, type } = req.query;
      const stats = await dbService.getHistoricalStats(
        new Date(startDate),
        new Date(endDate),
        type
      );
      res.json({ success: true, stats });
    } catch (error) {
      console.error('Get historical stats error:', error);
      res.status(500).json({ error: 'Failed to get historical stats' });
    }
  });

  // ==================== 监控功能 ====================
  
  // 监听呼叫
  router.post('/monitor/listen/:callUuid', authenticateToken, requireAdmin, async (req, res) => {
    try {
      const result = await agentManager.monitorCall(req.user.id, req.params.callUuid);
      res.json({ success: true, result });
    } catch (error) {
      console.error('Monitor call error:', error);
      res.status(500).json({ error: 'Failed to monitor call' });
    }
  });

  // 强插呼叫
  router.post('/monitor/barge/:callUuid', authenticateToken, requireAdmin, async (req, res) => {
    try {
      const result = await agentManager.bargeInCall(req.user.id, req.params.callUuid);
      res.json({ success: true, result });
    } catch (error) {
      console.error('Barge in call error:', error);
      res.status(500).json({ error: 'Failed to barge in call' });
    }
  });

  // 耳语指导
  router.post('/monitor/whisper/:agentId', authenticateToken, requireAdmin, async (req, res) => {
    try {
      const { message } = req.body;
      const result = await agentManager.whisperToAgent(
        req.user.id,
        req.params.agentId,
        message
      );
      res.json({ success: true, result });
    } catch (error) {
      console.error('Whisper error:', error);
      res.status(500).json({ error: 'Failed to whisper to agent' });
    }
  });

  // ==================== TTS/ASR 管理 ====================
  
  // 文本转语音
  router.post('/tts', authenticateToken, async (req, res) => {
    try {
      const { text, voice, format } = req.body;
      const audioStream = await speechService.textToSpeech(text, { voice, format });
      
      res.setHeader('Content-Type', `audio/${format || 'wav'}`);
      audioStream.pipe(res);
    } catch (error) {
      console.error('TTS error:', error);
      res.status(500).json({ error: 'Failed to generate speech' });
    }
  });

  // 批量生成语音文件
  router.post('/tts/batch', authenticateToken, requireAdmin, async (req, res) => {
    try {
      const { texts } = req.body;
      const results = await speechService.batchTTS(texts, './audio_files');
      res.json({ success: true, results });
    } catch (error) {
      console.error('Batch TTS error:', error);
      res.status(500).json({ error: 'Failed to generate batch speech' });
    }
  });

  // ==================== 系统管理 ====================
  
  // 获取系统配置
  router.get('/system/config', authenticateToken, requireAdmin, async (req, res) => {
    try {
      const config = await dbService.getSystemConfig();
      res.json({ success: true, config });
    } catch (error) {
      console.error('Get config error:', error);
      res.status(500).json({ error: 'Failed to get config' });
    }
  });

  // 更新系统配置
  router.put('/system/config', authenticateToken, requireAdmin, async (req, res) => {
    try {
      await dbService.updateSystemConfig(req.body);
      res.json({ success: true, message: 'Config updated' });
    } catch (error) {
      console.error('Update config error:', error);
      res.status(500).json({ error: 'Failed to update config' });
    }
  });

  // 获取系统日志
  router.get('/system/logs', authenticateToken, requireAdmin, async (req, res) => {
    try {
      const { level, limit, offset } = req.query;
      const logs = await dbService.getSystemLogs({ level, limit, offset });
      res.json({ success: true, logs });
    } catch (error) {
      console.error('Get logs error:', error);
      res.status(500).json({ error: 'Failed to get logs' });
    }
  });

  // ==================== WebHook 回调 ====================
  
  // FreeSWITCH事件回调
  router.post('/callback/freeswitch', async (req, res) => {
    try {
      const event = req.body;
      // 处理FreeSWITCH事件
      console.log('FreeSWITCH event:', event);
      res.json({ success: true });
    } catch (error) {
      console.error('FreeSWITCH callback error:', error);
      res.status(500).json({ error: 'Callback processing failed' });
    }
  });

  // ASR结果回调
  router.post('/callback/asr/:callUuid', async (req, res) => {
    try {
      const { text, confidence } = req.body;
      await fsManager.handleASRResult(req.params.callUuid, text, confidence);
      res.json({ success: true });
    } catch (error) {
      console.error('ASR callback error:', error);
      res.status(500).json({ error: 'ASR callback processing failed' });
    }
  });

  app.use('/api', router);
};