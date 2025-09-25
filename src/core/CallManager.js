const { v4: uuidv4 } = require('uuid');
const moment = require('moment');

/**
 * 呼叫管理器 - 负责管理所有外呼通话的生命周期
 */
class CallManager {
  constructor(io) {
    this.io = io;
    this.calls = new Map(); // 存储所有通话
    this.callQueue = []; // 外呼队列
    this.isProcessingQueue = false;
  }

  /**
   * 创建新的外呼任务
   */
  createOutboundCall(customerInfo, campaignId = null) {
    const callId = uuidv4();
    const call = {
      id: callId,
      customerId: customerInfo.id,
      customerName: customerInfo.name,
      customerPhone: customerInfo.phone,
      campaignId,
      status: 'queued', // queued, dialing, robot_talking, agent_talking, ended
      createdAt: moment().toISOString(),
      startedAt: null,
      endedAt: null,
      agentId: null,
      robotMessages: [],
      agentMessages: [],
      customerResponses: [],
      callDuration: 0,
      transferReason: null, // 转接原因
      callResult: null, // 通话结果
      metadata: {
        retryCount: 0,
        maxRetries: 3,
        priority: customerInfo.priority || 'normal'
      }
    };

    this.calls.set(callId, call);
    this.addToQueue(callId);
    
    console.log(`创建外呼任务: ${callId} -> ${customerInfo.phone}`);
    
    // 通知管理后台
    this.io.emit('call_created', {
      callId,
      customerInfo,
      timestamp: call.createdAt
    });

    return callId;
  }

  /**
   * 添加到外呼队列
   */
  addToQueue(callId) {
    const call = this.calls.get(callId);
    if (call && call.status === 'queued') {
      this.callQueue.push(callId);
      this.processQueue();
    }
  }

  /**
   * 处理外呼队列
   */
  async processQueue() {
    if (this.isProcessingQueue || this.callQueue.length === 0) {
      return;
    }

    this.isProcessingQueue = true;

    while (this.callQueue.length > 0) {
      const callId = this.callQueue.shift();
      const call = this.calls.get(callId);

      if (call && call.status === 'queued') {
        await this.initiateCall(callId);
        // 添加延迟避免过于频繁的外呼
        await this.delay(2000);
      }
    }

    this.isProcessingQueue = false;
  }

  /**
   * 发起外呼
   */
  async initiateCall(callId) {
    const call = this.calls.get(callId);
    if (!call) return;

    try {
      console.log(`开始外呼: ${callId} -> ${call.customerPhone}`);
      
      // 更新通话状态
      call.status = 'dialing';
      call.startedAt = moment().toISOString();

      // 模拟拨号过程
      this.io.emit('call_status_change', {
        callId,
        status: 'dialing',
        customerPhone: call.customerPhone,
        timestamp: call.startedAt
      });

      // 模拟拨号延迟
      await this.delay(3000);

      // 检查通话是否被取消
      if (call.status !== 'dialing') return;

      // 模拟接通成功率 (90%)
      const isConnected = Math.random() > 0.1;
      
      if (isConnected) {
        await this.handleCallConnected(callId);
      } else {
        await this.handleCallFailed(callId, '客户未接听');
      }

    } catch (error) {
      console.error(`外呼失败: ${callId}`, error);
      await this.handleCallFailed(callId, error.message);
    }
  }

  /**
   * 处理通话接通
   */
  async handleCallConnected(callId) {
    const call = this.calls.get(callId);
    if (!call) return;

    console.log(`通话接通: ${callId}`);
    
    // 更新状态为机器人对话
    call.status = 'robot_talking';
    
    this.io.emit('call_connected', {
      callId,
      customerPhone: call.customerPhone,
      customerName: call.customerName,
      timestamp: moment().toISOString()
    });

    // 开始机器人对话
    await this.startRobotDialogue(callId);
  }

  /**
   * 开始机器人对话
   */
  async startRobotDialogue(callId) {
    const call = this.calls.get(callId);
    if (!call) return;

    // 机器人开场白
    const welcomeMessage = `您好，我是智能客服助手。请问您是${call.customerName}吗？`;
    
    call.robotMessages.push({
      message: welcomeMessage,
      timestamp: moment().toISOString(),
      type: 'welcome'
    });

    this.io.emit('robot_message', {
      callId,
      message: welcomeMessage,
      timestamp: moment().toISOString()
    });

    console.log(`机器人开始对话: ${callId} - ${welcomeMessage}`);
  }

  /**
   * 处理机器人响应
   */
  handleRobotResponse(callId, response) {
    const call = this.calls.get(callId);
    if (!call || call.status !== 'robot_talking') return;

    call.robotMessages.push({
      message: response.message,
      timestamp: moment().toISOString(),
      type: response.type || 'response',
      confidence: response.confidence || 0.8
    });

    this.io.emit('robot_message', {
      callId,
      message: response.message,
      timestamp: moment().toISOString(),
      needsTransfer: response.needsTransfer || false
    });

    // 检查是否需要转接坐席
    if (response.needsTransfer) {
      this.requestAgentTransfer(callId, response.transferReason);
    }
  }

  /**
   * 请求坐席转接
   */
  requestAgentTransfer(callId, reason = '客户要求人工服务') {
    const call = this.calls.get(callId);
    if (!call) return;

    call.transferReason = reason;
    
    console.log(`请求坐席转接: ${callId} - ${reason}`);
    
    this.io.emit('transfer_request', {
      callId,
      customerPhone: call.customerPhone,
      customerName: call.customerName,
      reason,
      timestamp: moment().toISOString(),
      priority: call.metadata.priority
    });
  }

  /**
   * 坐席接管通话
   */
  agentTakeover(callId, agentId) {
    const call = this.calls.get(callId);
    if (!call) return false;

    if (call.status !== 'robot_talking') {
      return false;
    }

    call.status = 'agent_talking';
    call.agentId = agentId;
    
    console.log(`坐席接管通话: ${callId} - Agent: ${agentId}`);
    
    this.io.emit('agent_takeover', {
      callId,
      agentId,
      customerPhone: call.customerPhone,
      customerName: call.customerName,
      timestamp: moment().toISOString(),
      conversationHistory: {
        robotMessages: call.robotMessages,
        customerResponses: call.customerResponses
      }
    });

    return true;
  }

  /**
   * 处理坐席消息
   */
  handleAgentMessage(callId, message, agentId) {
    const call = this.calls.get(callId);
    if (!call || call.status !== 'agent_talking' || call.agentId !== agentId) {
      return;
    }

    call.agentMessages.push({
      message,
      timestamp: moment().toISOString(),
      agentId
    });

    this.io.emit('agent_message', {
      callId,
      message,
      timestamp: moment().toISOString()
    });

    console.log(`坐席消息: ${callId} - ${message}`);
  }

  /**
   * 处理客户响应
   */
  handleCustomerResponse(callId, response) {
    const call = this.calls.get(callId);
    if (!call) return;

    call.customerResponses.push({
      message: response,
      timestamp: moment().toISOString()
    });

    this.io.emit('customer_response', {
      callId,
      message: response,
      timestamp: moment().toISOString()
    });

    console.log(`客户响应: ${callId} - ${response}`);
  }

  /**
   * 结束通话
   */
  endCall(callId, result = null) {
    const call = this.calls.get(callId);
    if (!call) return;

    call.status = 'ended';
    call.endedAt = moment().toISOString();
    call.callResult = result;
    
    if (call.startedAt) {
      call.callDuration = moment(call.endedAt).diff(moment(call.startedAt), 'seconds');
    }

    console.log(`通话结束: ${callId} - 时长: ${call.callDuration}秒`);
    
    this.io.emit('call_ended', {
      callId,
      result,
      duration: call.callDuration,
      timestamp: call.endedAt
    });
  }

  /**
   * 处理通话失败
   */
  async handleCallFailed(callId, reason) {
    const call = this.calls.get(callId);
    if (!call) return;

    call.metadata.retryCount++;
    
    console.log(`通话失败: ${callId} - ${reason} (重试次数: ${call.metadata.retryCount})`);
    
    if (call.metadata.retryCount < call.metadata.maxRetries) {
      // 重新加入队列
      call.status = 'queued';
      this.addToQueue(callId);
      
      this.io.emit('call_retry', {
        callId,
        reason,
        retryCount: call.metadata.retryCount,
        timestamp: moment().toISOString()
      });
    } else {
      // 标记为失败
      this.endCall(callId, `失败: ${reason}`);
    }
  }

  /**
   * 获取通话信息
   */
  getCall(callId) {
    return this.calls.get(callId);
  }

  /**
   * 获取所有通话
   */
  getAllCalls() {
    return Array.from(this.calls.values());
  }

  /**
   * 获取活跃通话
   */
  getActiveCalls() {
    return Array.from(this.calls.values()).filter(
      call => ['dialing', 'robot_talking', 'agent_talking'].includes(call.status)
    );
  }

  /**
   * 获取等待转接的通话
   */
  getPendingTransferCalls() {
    return Array.from(this.calls.values()).filter(
      call => call.status === 'robot_talking' && call.transferReason
    );
  }

  /**
   * 延迟函数
   */
  delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * 获取统计信息
   */
  getStatistics() {
    const calls = Array.from(this.calls.values());
    const now = moment();
    const today = now.startOf('day');

    return {
      total: calls.length,
      today: calls.filter(call => moment(call.createdAt).isAfter(today)).length,
      active: this.getActiveCalls().length,
      queued: calls.filter(call => call.status === 'queued').length,
      completed: calls.filter(call => call.status === 'ended').length,
      pendingTransfer: this.getPendingTransferCalls().length,
      averageDuration: this.calculateAverageDuration(calls),
      successRate: this.calculateSuccessRate(calls)
    };
  }

  calculateAverageDuration(calls) {
    const completedCalls = calls.filter(call => call.status === 'ended' && call.callDuration > 0);
    if (completedCalls.length === 0) return 0;
    
    const totalDuration = completedCalls.reduce((sum, call) => sum + call.callDuration, 0);
    return Math.round(totalDuration / completedCalls.length);
  }

  calculateSuccessRate(calls) {
    const attemptedCalls = calls.filter(call => call.status !== 'queued');
    if (attemptedCalls.length === 0) return 0;
    
    const successfulCalls = attemptedCalls.filter(call => 
      call.status === 'ended' && call.callDuration > 10
    );
    
    return Math.round((successfulCalls.length / attemptedCalls.length) * 100);
  }
}

module.exports = CallManager;