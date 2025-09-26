const EventEmitter = require('events');
const schedule = require('node-schedule');
const { v4: uuidv4 } = require('uuid');
const winston = require('winston');

// 配置日志
const logger = winston.createLogger({
  level: 'info',
  format: winston.format.json(),
  transports: [
    new winston.transports.File({ filename: 'call-manager.log' }),
    new winston.transports.Console()
  ]
});

class CallManager extends EventEmitter {
  constructor(fsManager, dbService, redisClient) {
    super();
    this.fsManager = fsManager;
    this.dbService = dbService;
    this.redisClient = redisClient;
    this.campaigns = new Map();
    this.callQueue = [];
    this.isProcessing = false;
    this.maxConcurrentCalls = process.env.MAX_CONCURRENT_CALLS || 100;
    this.callInterval = 1000; // 呼叫间隔（毫秒）
  }

  // 创建外呼任务
  async createCampaign(campaignData) {
    const campaign = {
      id: uuidv4(),
      name: campaignData.name,
      description: campaignData.description,
      phoneList: campaignData.phoneList || [],
      script: campaignData.script,
      startTime: new Date(campaignData.startTime),
      endTime: new Date(campaignData.endTime),
      maxRetries: campaignData.maxRetries || 3,
      retryInterval: campaignData.retryInterval || 3600000, // 1小时
      priority: campaignData.priority || 5,
      status: 'pending',
      createdAt: new Date(),
      stats: {
        total: campaignData.phoneList.length,
        completed: 0,
        answered: 0,
        failed: 0,
        transferred: 0,
        avgDuration: 0
      }
    };

    // 保存到数据库
    await this.dbService.saveCampaign(campaign);
    
    // 缓存到内存
    this.campaigns.set(campaign.id, campaign);
    
    // 调度任务
    this.scheduleCampaign(campaign);
    
    logger.info(`Campaign created: ${campaign.id} - ${campaign.name}`);
    this.emit('campaign.created', campaign);
    
    return campaign;
  }

  // 调度外呼任务
  scheduleCampaign(campaign) {
    // 如果是立即执行
    if (campaign.startTime <= new Date()) {
      this.startCampaign(campaign.id);
    } else {
      // 定时执行
      const job = schedule.scheduleJob(campaign.startTime, () => {
        this.startCampaign(campaign.id);
      });
      
      campaign.scheduleJob = job;
    }
  }

  // 开始执行外呼任务
  async startCampaign(campaignId) {
    const campaign = this.campaigns.get(campaignId);
    if (!campaign) {
      logger.error(`Campaign not found: ${campaignId}`);
      return;
    }

    if (campaign.status === 'running') {
      logger.warn(`Campaign already running: ${campaignId}`);
      return;
    }

    campaign.status = 'running';
    campaign.startedAt = new Date();
    
    logger.info(`Starting campaign: ${campaign.name}`);
    this.emit('campaign.started', campaign);

    // 将号码加入呼叫队列
    for (const phone of campaign.phoneList) {
      this.callQueue.push({
        campaignId: campaign.id,
        phoneNumber: phone.number || phone,
        metadata: phone.metadata || {},
        retries: 0,
        priority: campaign.priority
      });
    }

    // 排序队列（按优先级）
    this.callQueue.sort((a, b) => b.priority - a.priority);

    // 开始处理队列
    if (!this.isProcessing) {
      this.processCallQueue();
    }
  }

  // 处理呼叫队列
  async processCallQueue() {
    if (this.isProcessing) return;
    
    this.isProcessing = true;
    
    while (this.callQueue.length > 0) {
      // 检查并发限制
      const activeCalls = this.fsManager.getActiveCalls();
      if (activeCalls.length >= this.maxConcurrentCalls) {
        // 等待一段时间后重试
        await this.sleep(5000);
        continue;
      }

      // 取出一个号码
      const callTask = this.callQueue.shift();
      
      // 发起呼叫
      this.makeCall(callTask);
      
      // 呼叫间隔
      await this.sleep(this.callInterval);
    }
    
    this.isProcessing = false;
  }

  // 发起单个呼叫
  async makeCall(callTask) {
    const campaign = this.campaigns.get(callTask.campaignId);
    if (!campaign) return;

    try {
      // 记录呼叫开始
      const callRecord = {
        id: uuidv4(),
        campaignId: callTask.campaignId,
        phoneNumber: callTask.phoneNumber,
        startTime: new Date(),
        status: 'calling',
        retries: callTask.retries
      };

      // 保存到Redis（用于实时状态）
      await this.redisClient.setex(
        `call:${callRecord.id}`,
        3600,
        JSON.stringify(callRecord)
      );

      // 通过FreeSWITCH发起呼叫
      const callInfo = await this.fsManager.makeOutboundCall(
        callTask.phoneNumber,
        `http://localhost:3000/api/callback/${callRecord.id}`,
        {
          campaignId: callTask.campaignId,
          callRecordId: callRecord.id,
          script: campaign.script
        }
      );

      // 更新记录
      callRecord.fsCallUuid = callInfo.uuid;
      await this.redisClient.setex(
        `call:${callRecord.id}`,
        3600,
        JSON.stringify(callRecord)
      );

      logger.info(`Call initiated: ${callTask.phoneNumber} (Campaign: ${campaign.name})`);
      this.emit('call.initiated', { callRecord, campaign });

    } catch (error) {
      logger.error(`Failed to make call to ${callTask.phoneNumber}:`, error);
      
      // 重试逻辑
      if (callTask.retries < campaign.maxRetries) {
        callTask.retries++;
        
        // 延迟后重新加入队列
        setTimeout(() => {
          this.callQueue.push(callTask);
          if (!this.isProcessing) {
            this.processCallQueue();
          }
        }, campaign.retryInterval);
      } else {
        // 标记为失败
        campaign.stats.failed++;
        this.emit('call.failed', { phoneNumber: callTask.phoneNumber, campaign });
      }
    }
  }

  // 处理呼叫结果
  async handleCallResult(callUuid, result) {
    // 从Redis获取呼叫记录
    const callRecordKey = await this.redisClient.get(`call:uuid:${callUuid}`);
    if (!callRecordKey) return;

    const callRecord = JSON.parse(await this.redisClient.get(callRecordKey));
    const campaign = this.campaigns.get(callRecord.campaignId);
    
    if (!campaign) return;

    // 更新统计
    campaign.stats.completed++;
    
    if (result.status === 'answered') {
      campaign.stats.answered++;
      
      if (result.transferredToAgent) {
        campaign.stats.transferred++;
      }
      
      // 更新平均通话时长
      const currentAvg = campaign.stats.avgDuration;
      const newAvg = ((currentAvg * (campaign.stats.answered - 1)) + result.duration) / campaign.stats.answered;
      campaign.stats.avgDuration = Math.round(newAvg);
    }

    // 保存通话记录到数据库
    await this.dbService.saveCallRecord({
      ...callRecord,
      ...result,
      endTime: new Date()
    });

    // 清理Redis
    await this.redisClient.del(`call:${callRecord.id}`);
    await this.redisClient.del(`call:uuid:${callUuid}`);

    // 检查任务是否完成
    if (campaign.stats.completed >= campaign.stats.total) {
      this.completeCampaign(campaign.id);
    }

    this.emit('call.result', { callRecord, result, campaign });
  }

  // 完成外呼任务
  async completeCampaign(campaignId) {
    const campaign = this.campaigns.get(campaignId);
    if (!campaign) return;

    campaign.status = 'completed';
    campaign.completedAt = new Date();
    
    // 计算任务执行时间
    campaign.duration = Math.floor((campaign.completedAt - campaign.startedAt) / 1000);
    
    // 保存最终状态到数据库
    await this.dbService.updateCampaign(campaign);
    
    logger.info(`Campaign completed: ${campaign.name}`);
    logger.info(`Stats: ${JSON.stringify(campaign.stats)}`);
    
    this.emit('campaign.completed', campaign);
    
    // 清理内存
    this.campaigns.delete(campaignId);
  }

  // 暂停外呼任务
  async pauseCampaign(campaignId) {
    const campaign = this.campaigns.get(campaignId);
    if (!campaign || campaign.status !== 'running') return;

    campaign.status = 'paused';
    campaign.pausedAt = new Date();
    
    // 从队列中移除该任务的号码
    this.callQueue = this.callQueue.filter(task => task.campaignId !== campaignId);
    
    await this.dbService.updateCampaign(campaign);
    
    logger.info(`Campaign paused: ${campaign.name}`);
    this.emit('campaign.paused', campaign);
    
    return campaign;
  }

  // 恢复外呼任务
  async resumeCampaign(campaignId) {
    const campaign = this.campaigns.get(campaignId);
    if (!campaign || campaign.status !== 'paused') return;

    campaign.status = 'running';
    delete campaign.pausedAt;
    
    // 重新加载未完成的号码到队列
    const pendingPhones = await this.dbService.getPendingPhones(campaignId);
    
    for (const phone of pendingPhones) {
      this.callQueue.push({
        campaignId: campaign.id,
        phoneNumber: phone.number,
        metadata: phone.metadata || {},
        retries: phone.retries || 0,
        priority: campaign.priority
      });
    }
    
    await this.dbService.updateCampaign(campaign);
    
    logger.info(`Campaign resumed: ${campaign.name}`);
    this.emit('campaign.resumed', campaign);
    
    // 继续处理队列
    if (!this.isProcessing) {
      this.processCallQueue();
    }
    
    return campaign;
  }

  // 取消外呼任务
  async cancelCampaign(campaignId) {
    const campaign = this.campaigns.get(campaignId);
    if (!campaign) return;

    campaign.status = 'cancelled';
    campaign.cancelledAt = new Date();
    
    // 从队列中移除
    this.callQueue = this.callQueue.filter(task => task.campaignId !== campaignId);
    
    // 取消定时任务
    if (campaign.scheduleJob) {
      campaign.scheduleJob.cancel();
    }
    
    await this.dbService.updateCampaign(campaign);
    
    logger.info(`Campaign cancelled: ${campaign.name}`);
    this.emit('campaign.cancelled', campaign);
    
    // 清理内存
    this.campaigns.delete(campaignId);
    
    return campaign;
  }

  // 获取任务状态
  getCampaignStatus(campaignId) {
    const campaign = this.campaigns.get(campaignId);
    if (!campaign) return null;

    return {
      id: campaign.id,
      name: campaign.name,
      status: campaign.status,
      stats: campaign.stats,
      startTime: campaign.startTime,
      endTime: campaign.endTime,
      duration: campaign.duration
    };
  }

  // 获取所有任务
  getAllCampaigns() {
    return Array.from(this.campaigns.values()).map(campaign => ({
      id: campaign.id,
      name: campaign.name,
      status: campaign.status,
      stats: campaign.stats,
      createdAt: campaign.createdAt
    }));
  }

  // 获取实时呼叫状态
  async getRealTimeStats() {
    const activeCalls = this.fsManager.getActiveCalls();
    const agents = this.fsManager.getAgents();
    const queueLength = this.callQueue.length;
    
    const stats = {
      activeCalls: activeCalls.length,
      robotCalls: activeCalls.filter(c => c.isRobot && !c.transferredToAgent).length,
      agentCalls: activeCalls.filter(c => c.transferredToAgent).length,
      queuedCalls: activeCalls.filter(c => c.inQueue).length,
      pendingCalls: queueLength,
      totalAgents: agents.length,
      availableAgents: agents.filter(a => a.status === 'available').length,
      busyAgents: agents.filter(a => a.status === 'busy').length,
      campaigns: this.getAllCampaigns()
    };
    
    return stats;
  }

  // 导入号码列表
  async importPhoneList(filePath, campaignId) {
    const campaign = this.campaigns.get(campaignId);
    if (!campaign) {
      throw new Error('Campaign not found');
    }

    // 这里实现CSV/Excel文件导入逻辑
    // 简化示例，实际需要处理文件解析
    const phones = await this.parsePhoneFile(filePath);
    
    campaign.phoneList = campaign.phoneList.concat(phones);
    campaign.stats.total = campaign.phoneList.length;
    
    await this.dbService.updateCampaign(campaign);
    
    logger.info(`Imported ${phones.length} phones to campaign ${campaign.name}`);
    
    return phones.length;
  }

  // 解析号码文件
  async parsePhoneFile(filePath) {
    // 实际实现需要根据文件格式解析
    // 这里返回示例数据
    return [
      { number: '13800138001', metadata: { name: '张三' } },
      { number: '13800138002', metadata: { name: '李四' } },
      { number: '13800138003', metadata: { name: '王五' } }
    ];
  }

  // 辅助函数：延迟
  sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  // 初始化：从数据库加载未完成的任务
  async initialize() {
    try {
      const pendingCampaigns = await this.dbService.getPendingCampaigns();
      
      for (const campaign of pendingCampaigns) {
        this.campaigns.set(campaign.id, campaign);
        
        if (campaign.status === 'running') {
          // 恢复执行
          await this.resumeCampaign(campaign.id);
        } else if (campaign.status === 'pending' && campaign.startTime > new Date()) {
          // 重新调度
          this.scheduleCampaign(campaign);
        }
      }
      
      logger.info(`Initialized with ${pendingCampaigns.length} pending campaigns`);
    } catch (error) {
      logger.error('Failed to initialize CallManager:', error);
    }
  }
}

module.exports = CallManager;