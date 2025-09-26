const EventEmitter = require('events');
const winston = require('winston');
const { v4: uuidv4 } = require('uuid');

// 配置日志
const logger = winston.createLogger({
  level: 'info',
  format: winston.format.json(),
  transports: [
    new winston.transports.File({ filename: 'agent-manager.log' }),
    new winston.transports.Console()
  ]
});

// 坐席状态枚举
const AgentStatus = {
  OFFLINE: 'offline',
  AVAILABLE: 'available',
  BUSY: 'busy',
  BREAK: 'break',
  AFTER_CALL_WORK: 'after_call_work',
  TRAINING: 'training'
};

// 坐席管理器
class AgentManager extends EventEmitter {
  constructor(fsManager, dbService, redisClient) {
    super();
    this.fsManager = fsManager;
    this.dbService = dbService;
    this.redisClient = redisClient;
    this.agents = new Map();
    this.skills = new Map();
    this.queues = new Map();
    this.callDistribution = new Map();
    this.supervisors = new Map();
  }

  // 初始化
  async initialize() {
    try {
      // 从数据库加载坐席信息
      const agents = await this.dbService.getAllAgents();
      for (const agent of agents) {
        this.agents.set(agent.id, {
          ...agent,
          status: AgentStatus.OFFLINE,
          currentCall: null,
          loginTime: null,
          lastStateChange: null,
          statistics: {
            totalCalls: 0,
            answeredCalls: 0,
            missedCalls: 0,
            totalTalkTime: 0,
            avgTalkTime: 0,
            totalBreakTime: 0,
            totalACWTime: 0
          }
        });
      }

      // 加载技能组
      const skills = await this.dbService.getAllSkills();
      for (const skill of skills) {
        this.skills.set(skill.id, skill);
      }

      // 初始化队列
      this.initializeQueues();

      logger.info(`Agent Manager initialized with ${agents.length} agents and ${skills.length} skills`);
    } catch (error) {
      logger.error('Failed to initialize Agent Manager:', error);
      throw error;
    }
  }

  // 初始化队列
  initializeQueues() {
    // 创建默认队列
    this.queues.set('default', {
      id: 'default',
      name: '默认队列',
      priority: 5,
      calls: [],
      agents: []
    });

    // 为每个技能组创建队列
    for (const [skillId, skill] of this.skills) {
      this.queues.set(`skill_${skillId}`, {
        id: `skill_${skillId}`,
        name: `${skill.name}队列`,
        priority: skill.priority || 5,
        calls: [],
        agents: []
      });
    }
  }

  // 坐席登录
  async agentLogin(agentId, extension, password) {
    const agent = this.agents.get(agentId);
    if (!agent) {
      throw new Error('Agent not found');
    }

    // 验证密码
    const isValid = await this.dbService.validateAgentPassword(agentId, password);
    if (!isValid) {
      throw new Error('Invalid password');
    }

    // 检查分机是否已被占用
    for (const [id, a] of this.agents) {
      if (a.extension === extension && a.status !== AgentStatus.OFFLINE) {
        throw new Error('Extension already in use');
      }
    }

    // 更新坐席信息
    agent.extension = extension;
    agent.status = AgentStatus.AVAILABLE;
    agent.loginTime = new Date();
    agent.lastStateChange = new Date();

    // 在FreeSWITCH中注册坐席
    this.fsManager.registerAgent({
      id: agentId,
      name: agent.name,
      extension: extension,
      skills: agent.skills
    });

    // 将坐席加入相应的队列
    this.assignAgentToQueues(agent);

    // 保存到Redis（用于会话管理）
    await this.redisClient.setex(
      `agent:session:${agentId}`,
      86400, // 24小时
      JSON.stringify({
        agentId,
        extension,
        loginTime: agent.loginTime,
        status: agent.status
      })
    );

    // 记录登录日志
    await this.dbService.logAgentActivity(agentId, 'login', { extension });

    logger.info(`Agent ${agent.name} logged in with extension ${extension}`);
    this.emit('agent.login', { agent });

    return {
      success: true,
      agent: this.getAgentInfo(agentId)
    };
  }

  // 坐席登出
  async agentLogout(agentId) {
    const agent = this.agents.get(agentId);
    if (!agent || agent.status === AgentStatus.OFFLINE) {
      throw new Error('Agent not logged in');
    }

    // 检查是否有正在进行的通话
    if (agent.currentCall) {
      throw new Error('Cannot logout while on call');
    }

    // 计算在线时长
    const onlineTime = Date.now() - agent.loginTime.getTime();
    agent.statistics.totalOnlineTime = (agent.statistics.totalOnlineTime || 0) + onlineTime;

    // 从FreeSWITCH注销
    this.fsManager.unregisterAgent(agentId);

    // 从队列中移除
    this.removeAgentFromQueues(agent);

    // 更新状态
    agent.status = AgentStatus.OFFLINE;
    agent.extension = null;
    agent.loginTime = null;
    agent.lastStateChange = new Date();

    // 清除Redis会话
    await this.redisClient.del(`agent:session:${agentId}`);

    // 记录登出日志
    await this.dbService.logAgentActivity(agentId, 'logout', {
      onlineTime: Math.floor(onlineTime / 1000)
    });

    logger.info(`Agent ${agent.name} logged out`);
    this.emit('agent.logout', { agent });

    return { success: true };
  }

  // 更改坐席状态
  async changeAgentStatus(agentId, newStatus, reason = '') {
    const agent = this.agents.get(agentId);
    if (!agent || agent.status === AgentStatus.OFFLINE) {
      throw new Error('Agent not available');
    }

    const oldStatus = agent.status;

    // 验证状态转换的合法性
    if (!this.isValidStatusTransition(oldStatus, newStatus)) {
      throw new Error(`Invalid status transition from ${oldStatus} to ${newStatus}`);
    }

    // 如果正在通话中，不能改为可用状态
    if (agent.currentCall && newStatus === AgentStatus.AVAILABLE) {
      throw new Error('Cannot change to available while on call');
    }

    // 记录状态持续时间
    const statusDuration = Date.now() - agent.lastStateChange.getTime();
    this.updateAgentStatistics(agent, oldStatus, statusDuration);

    // 更新状态
    agent.status = newStatus;
    agent.lastStateChange = new Date();
    agent.statusReason = reason;

    // 更新FreeSWITCH中的状态
    this.fsManager.updateAgentStatus(agentId, newStatus);

    // 更新队列可用性
    if (newStatus === AgentStatus.AVAILABLE) {
      this.assignAgentToQueues(agent);
    } else {
      this.removeAgentFromQueues(agent);
    }

    // 保存状态到Redis
    await this.redisClient.hset(
      `agent:session:${agentId}`,
      'status', newStatus,
      'lastStateChange', agent.lastStateChange.toISOString()
    );

    // 记录状态变更日志
    await this.dbService.logAgentActivity(agentId, 'status_change', {
      from: oldStatus,
      to: newStatus,
      reason: reason
    });

    logger.info(`Agent ${agent.name} status changed from ${oldStatus} to ${newStatus}`);
    this.emit('agent.statusChanged', { agent, oldStatus, newStatus, reason });

    return { success: true, agent: this.getAgentInfo(agentId) };
  }

  // 验证状态转换的合法性
  isValidStatusTransition(from, to) {
    const validTransitions = {
      [AgentStatus.OFFLINE]: [AgentStatus.AVAILABLE],
      [AgentStatus.AVAILABLE]: [AgentStatus.BUSY, AgentStatus.BREAK, AgentStatus.TRAINING, AgentStatus.OFFLINE],
      [AgentStatus.BUSY]: [AgentStatus.AFTER_CALL_WORK, AgentStatus.AVAILABLE],
      [AgentStatus.BREAK]: [AgentStatus.AVAILABLE, AgentStatus.OFFLINE],
      [AgentStatus.AFTER_CALL_WORK]: [AgentStatus.AVAILABLE, AgentStatus.BREAK],
      [AgentStatus.TRAINING]: [AgentStatus.AVAILABLE, AgentStatus.OFFLINE]
    };

    return validTransitions[from]?.includes(to) || false;
  }

  // 分配呼叫给坐席
  async assignCall(callInfo, skillRequired = null) {
    let selectedAgent = null;
    let selectedQueue = null;

    // 根据技能要求选择队列
    if (skillRequired) {
      selectedQueue = this.queues.get(`skill_${skillRequired}`);
    }
    
    if (!selectedQueue) {
      selectedQueue = this.queues.get('default');
    }

    // 查找可用坐席（使用不同的分配策略）
    selectedAgent = await this.selectAgent(selectedQueue, callInfo);

    if (selectedAgent) {
      // 分配呼叫
      selectedAgent.status = AgentStatus.BUSY;
      selectedAgent.currentCall = callInfo.uuid;
      selectedAgent.callStartTime = new Date();

      // 更新呼叫分配记录
      this.callDistribution.set(callInfo.uuid, {
        agentId: selectedAgent.id,
        assignedAt: new Date(),
        queueId: selectedQueue.id
      });

      // 记录分配日志
      await this.dbService.logCallAssignment(callInfo.uuid, selectedAgent.id, selectedQueue.id);

      logger.info(`Call ${callInfo.uuid} assigned to agent ${selectedAgent.name}`);
      this.emit('call.assigned', {
        call: callInfo,
        agent: selectedAgent,
        queue: selectedQueue
      });

      return selectedAgent;
    } else {
      // 加入队列等待
      selectedQueue.calls.push({
        ...callInfo,
        queuedAt: new Date(),
        priority: callInfo.priority || 5
      });

      // 排序队列（按优先级和等待时间）
      selectedQueue.calls.sort((a, b) => {
        if (a.priority !== b.priority) {
          return b.priority - a.priority;
        }
        return a.queuedAt - b.queuedAt;
      });

      logger.info(`Call ${callInfo.uuid} queued in ${selectedQueue.name}`);
      this.emit('call.queued', {
        call: callInfo,
        queue: selectedQueue,
        position: selectedQueue.calls.length
      });

      return null;
    }
  }

  // 选择坐席（支持多种分配策略）
  async selectAgent(queue, callInfo) {
    const strategy = queue.strategy || 'round_robin';
    const availableAgents = [];

    // 获取队列中的可用坐席
    for (const agentId of queue.agents) {
      const agent = this.agents.get(agentId);
      if (agent && agent.status === AgentStatus.AVAILABLE) {
        availableAgents.push(agent);
      }
    }

    if (availableAgents.length === 0) {
      return null;
    }

    let selectedAgent = null;

    switch (strategy) {
      case 'round_robin':
        // 轮询分配
        selectedAgent = this.roundRobinSelect(availableAgents, queue);
        break;
        
      case 'least_busy':
        // 选择最空闲的坐席
        selectedAgent = this.leastBusySelect(availableAgents);
        break;
        
      case 'skill_based':
        // 基于技能匹配
        selectedAgent = this.skillBasedSelect(availableAgents, callInfo);
        break;
        
      case 'longest_idle':
        // 选择空闲时间最长的坐席
        selectedAgent = this.longestIdleSelect(availableAgents);
        break;
        
      default:
        // 默认使用轮询
        selectedAgent = availableAgents[0];
    }

    return selectedAgent;
  }

  // 轮询选择
  roundRobinSelect(agents, queue) {
    if (!queue.lastAssignedIndex) {
      queue.lastAssignedIndex = 0;
    }
    
    queue.lastAssignedIndex = (queue.lastAssignedIndex + 1) % agents.length;
    return agents[queue.lastAssignedIndex];
  }

  // 选择最空闲的坐席
  leastBusySelect(agents) {
    return agents.reduce((least, agent) => {
      const agentCalls = agent.statistics.totalCalls || 0;
      const leastCalls = least.statistics.totalCalls || 0;
      return agentCalls < leastCalls ? agent : least;
    });
  }

  // 基于技能选择
  skillBasedSelect(agents, callInfo) {
    // 根据呼叫需要的技能筛选坐席
    const requiredSkills = callInfo.requiredSkills || [];
    
    const matchedAgents = agents.filter(agent => {
      const agentSkills = agent.skills || [];
      return requiredSkills.every(skill => agentSkills.includes(skill));
    });

    // 如果有匹配的，选择技能评分最高的
    if (matchedAgents.length > 0) {
      return matchedAgents.reduce((best, agent) => {
        const agentScore = this.calculateSkillScore(agent, requiredSkills);
        const bestScore = this.calculateSkillScore(best, requiredSkills);
        return agentScore > bestScore ? agent : best;
      });
    }

    // 没有完全匹配的，返回第一个可用的
    return agents[0];
  }

  // 选择空闲时间最长的坐席
  longestIdleSelect(agents) {
    return agents.reduce((longest, agent) => {
      const agentIdleTime = Date.now() - agent.lastStateChange.getTime();
      const longestIdleTime = Date.now() - longest.lastStateChange.getTime();
      return agentIdleTime > longestIdleTime ? agent : longest;
    });
  }

  // 计算技能评分
  calculateSkillScore(agent, requiredSkills) {
    let score = 0;
    const agentSkills = agent.skillLevels || {};
    
    for (const skill of requiredSkills) {
      score += agentSkills[skill] || 0;
    }
    
    return score;
  }

  // 呼叫结束处理
  async handleCallEnd(callUuid, callStats) {
    const distribution = this.callDistribution.get(callUuid);
    if (!distribution) return;

    const agent = this.agents.get(distribution.agentId);
    if (!agent) return;

    // 更新坐席统计
    agent.statistics.totalCalls++;
    agent.statistics.answeredCalls++;
    agent.statistics.totalTalkTime += callStats.duration || 0;
    agent.statistics.avgTalkTime = Math.floor(
      agent.statistics.totalTalkTime / agent.statistics.answeredCalls
    );

    // 进入话后处理状态
    agent.status = AgentStatus.AFTER_CALL_WORK;
    agent.currentCall = null;
    agent.lastCallEndTime = new Date();

    // 自动回到可用状态（可配置）
    const acwTimeout = 30000; // 30秒
    setTimeout(() => {
      if (agent.status === AgentStatus.AFTER_CALL_WORK) {
        this.changeAgentStatus(agent.id, AgentStatus.AVAILABLE, 'Auto ready after ACW');
      }
    }, acwTimeout);

    // 清理分配记录
    this.callDistribution.delete(callUuid);

    // 检查队列中的等待呼叫
    this.checkQueuedCalls();

    logger.info(`Call ${callUuid} ended for agent ${agent.name}`);
    this.emit('call.ended', { callUuid, agent, stats: callStats });
  }

  // 检查并分配队列中的呼叫
  async checkQueuedCalls() {
    for (const [queueId, queue] of this.queues) {
      while (queue.calls.length > 0) {
        const call = queue.calls[0];
        const agent = await this.selectAgent(queue, call);
        
        if (agent) {
          // 移除队列中的呼叫
          queue.calls.shift();
          
          // 分配给坐席
          await this.assignCall(call);
          
          // 通知FreeSWITCH转接
          this.fsManager.transferToAgent(call.uuid);
        } else {
          // 没有可用坐席，停止检查
          break;
        }
      }
    }
  }

  // 将坐席加入队列
  assignAgentToQueues(agent) {
    // 加入默认队列
    const defaultQueue = this.queues.get('default');
    if (!defaultQueue.agents.includes(agent.id)) {
      defaultQueue.agents.push(agent.id);
    }

    // 根据技能加入相应队列
    if (agent.skills) {
      for (const skillId of agent.skills) {
        const skillQueue = this.queues.get(`skill_${skillId}`);
        if (skillQueue && !skillQueue.agents.includes(agent.id)) {
          skillQueue.agents.push(agent.id);
        }
      }
    }
  }

  // 从队列中移除坐席
  removeAgentFromQueues(agent) {
    for (const [queueId, queue] of this.queues) {
      const index = queue.agents.indexOf(agent.id);
      if (index > -1) {
        queue.agents.splice(index, 1);
      }
    }
  }

  // 更新坐席统计
  updateAgentStatistics(agent, status, duration) {
    const durationSeconds = Math.floor(duration / 1000);
    
    switch (status) {
      case AgentStatus.BREAK:
        agent.statistics.totalBreakTime = (agent.statistics.totalBreakTime || 0) + durationSeconds;
        break;
      case AgentStatus.AFTER_CALL_WORK:
        agent.statistics.totalACWTime = (agent.statistics.totalACWTime || 0) + durationSeconds;
        break;
      case AgentStatus.BUSY:
        // 通话时长在呼叫结束时统计
        break;
    }
  }

  // 获取坐席信息
  getAgentInfo(agentId) {
    const agent = this.agents.get(agentId);
    if (!agent) return null;

    return {
      id: agent.id,
      name: agent.name,
      extension: agent.extension,
      status: agent.status,
      statusReason: agent.statusReason,
      skills: agent.skills,
      currentCall: agent.currentCall,
      loginTime: agent.loginTime,
      lastStateChange: agent.lastStateChange,
      statistics: agent.statistics
    };
  }

  // 获取所有坐席状态
  getAllAgentsStatus() {
    const agentsStatus = [];
    
    for (const [agentId, agent] of this.agents) {
      if (agent.status !== AgentStatus.OFFLINE) {
        agentsStatus.push(this.getAgentInfo(agentId));
      }
    }
    
    return agentsStatus;
  }

  // 获取队列状态
  getQueueStatus(queueId = null) {
    if (queueId) {
      const queue = this.queues.get(queueId);
      if (!queue) return null;
      
      return {
        id: queue.id,
        name: queue.name,
        waitingCalls: queue.calls.length,
        availableAgents: queue.agents.filter(id => {
          const agent = this.agents.get(id);
          return agent && agent.status === AgentStatus.AVAILABLE;
        }).length,
        totalAgents: queue.agents.length,
        longestWaitTime: queue.calls.length > 0 
          ? Math.floor((Date.now() - queue.calls[0].queuedAt.getTime()) / 1000)
          : 0
      };
    }

    // 返回所有队列状态
    const queuesStatus = [];
    for (const [id, queue] of this.queues) {
      queuesStatus.push(this.getQueueStatus(id));
    }
    
    return queuesStatus;
  }

  // 监听呼叫
  async monitorCall(supervisorId, callUuid) {
    const supervisor = this.supervisors.get(supervisorId);
    if (!supervisor) {
      throw new Error('Supervisor not found');
    }

    // 实现监听逻辑
    const result = await this.fsManager.monitorCall(callUuid, supervisor.extension);
    
    logger.info(`Supervisor ${supervisor.name} monitoring call ${callUuid}`);
    this.emit('call.monitored', { supervisor, callUuid });
    
    return result;
  }

  // 强插呼叫
  async bargeInCall(supervisorId, callUuid) {
    const supervisor = this.supervisors.get(supervisorId);
    if (!supervisor) {
      throw new Error('Supervisor not found');
    }

    // 实现强插逻辑
    const result = await this.fsManager.bargeInCall(callUuid, supervisor.extension);
    
    logger.info(`Supervisor ${supervisor.name} barged into call ${callUuid}`);
    this.emit('call.bargedIn', { supervisor, callUuid });
    
    return result;
  }

  // 耳语指导
  async whisperToAgent(supervisorId, agentId, message) {
    const supervisor = this.supervisors.get(supervisorId);
    const agent = this.agents.get(agentId);
    
    if (!supervisor || !agent || !agent.currentCall) {
      throw new Error('Invalid whisper request');
    }

    // 实现耳语逻辑
    const result = await this.fsManager.whisperToAgent(agent.currentCall, supervisor.extension);
    
    logger.info(`Supervisor ${supervisor.name} whispering to agent ${agent.name}`);
    this.emit('agent.whispered', { supervisor, agent, message });
    
    return result;
  }

  // 生成坐席报表
  async generateAgentReport(agentId, startDate, endDate) {
    const agent = this.agents.get(agentId);
    if (!agent) {
      throw new Error('Agent not found');
    }

    // 从数据库获取历史数据
    const callHistory = await this.dbService.getAgentCallHistory(agentId, startDate, endDate);
    const activityLog = await this.dbService.getAgentActivityLog(agentId, startDate, endDate);

    // 计算统计数据
    const report = {
      agent: {
        id: agent.id,
        name: agent.name
      },
      period: {
        start: startDate,
        end: endDate
      },
      summary: {
        totalCalls: callHistory.length,
        answeredCalls: callHistory.filter(c => c.status === 'answered').length,
        missedCalls: callHistory.filter(c => c.status === 'missed').length,
        transferredCalls: callHistory.filter(c => c.transferred).length,
        totalTalkTime: callHistory.reduce((sum, c) => sum + (c.duration || 0), 0),
        avgTalkTime: 0,
        totalLoginTime: 0,
        totalBreakTime: 0,
        totalACWTime: 0,
        utilization: 0
      },
      dailyStats: [],
      hourlyDistribution: [],
      performanceMetrics: {
        avgHandleTime: 0,
        firstCallResolution: 0,
        customerSatisfaction: 0,
        qualityScore: 0
      }
    };

    // 计算平均值
    if (report.summary.answeredCalls > 0) {
      report.summary.avgTalkTime = Math.floor(
        report.summary.totalTalkTime / report.summary.answeredCalls
      );
    }

    // 计算登录时间和利用率
    const loginSessions = this.calculateLoginSessions(activityLog);
    report.summary.totalLoginTime = loginSessions.totalTime;
    report.summary.totalBreakTime = loginSessions.breakTime;
    report.summary.totalACWTime = loginSessions.acwTime;
    
    if (report.summary.totalLoginTime > 0) {
      report.summary.utilization = Math.floor(
        ((report.summary.totalTalkTime + report.summary.totalACWTime) / report.summary.totalLoginTime) * 100
      );
    }

    return report;
  }

  // 计算登录会话
  calculateLoginSessions(activityLog) {
    let totalTime = 0;
    let breakTime = 0;
    let acwTime = 0;
    let currentSession = null;

    for (const activity of activityLog) {
      if (activity.action === 'login') {
        currentSession = {
          loginTime: new Date(activity.timestamp),
          breaks: [],
          acw: []
        };
      } else if (activity.action === 'logout' && currentSession) {
        const sessionTime = new Date(activity.timestamp) - currentSession.loginTime;
        totalTime += sessionTime;
        currentSession = null;
      } else if (activity.action === 'status_change' && currentSession) {
        if (activity.data.to === AgentStatus.BREAK) {
          currentSession.breaks.push({ start: new Date(activity.timestamp) });
        } else if (activity.data.from === AgentStatus.BREAK) {
          const lastBreak = currentSession.breaks[currentSession.breaks.length - 1];
          if (lastBreak && !lastBreak.end) {
            lastBreak.end = new Date(activity.timestamp);
            breakTime += lastBreak.end - lastBreak.start;
          }
        }
      }
    }

    return {
      totalTime: Math.floor(totalTime / 1000),
      breakTime: Math.floor(breakTime / 1000),
      acwTime: Math.floor(acwTime / 1000)
    };
  }
}

module.exports = AgentManager;