const EventEmitter = require('events');
const Call = require('../models/Call');
const Agent = require('../models/Agent');
const { v4: uuidv4 } = require('uuid');

class CallManager extends EventEmitter {
  constructor(io) {
    super();
    this.io = io;
    this.activeCalls = new Map();
    this.callQueue = [];
    this.availableAgents = new Set();
    this.agentCalls = new Map(); // agentId -> callId mapping
  }

  // 发起外呼
  async initiateOutboundCall(customerId, campaignId, phoneNumber) {
    const callId = uuidv4();
    const call = {
      id: callId,
      customerId,
      campaignId,
      phoneNumber,
      status: 'initiating',
      startTime: new Date(),
      isRobotHandling: true,
      agentId: null,
      transcript: [],
      events: [{
        type: 'call_initiated',
        timestamp: new Date(),
        data: { phoneNumber }
      }]
    };

    this.activeCalls.set(callId, call);
    
    // Save to database
    const dbCall = new Call(call);
    await dbCall.save();

    // Notify all connected agents
    this.io.emit('call:new', call);

    // Simulate robot starting the call
    setTimeout(() => {
      this.updateCallStatus(callId, 'connected');
      this.startRobotConversation(callId);
    }, 2000);

    return call;
  }

  // 机器人对话逻辑
  startRobotConversation(callId) {
    const call = this.activeCalls.get(callId);
    if (!call || !call.isRobotHandling) return;

    // Simulate robot greeting
    const greeting = "您好，这里是客服中心。请问有什么可以帮助您的吗？";
    this.addTranscript(callId, 'robot', greeting);

    // Notify agents about the conversation
    this.io.emit('call:transcript', {
      callId,
      speaker: 'robot',
      text: greeting,
      timestamp: new Date()
    });
  }

  // 坐席介入
  agentIntervene(callId, agentId) {
    const call = this.activeCalls.get(callId);
    if (!call) return false;

    call.agentId = agentId;
    call.interventionMode = 'monitoring'; // Agent can listen and suggest
    call.events.push({
      type: 'agent_intervened',
      timestamp: new Date(),
      data: { agentId }
    });

    this.agentCalls.set(agentId, callId);
    
    // Notify all parties
    this.io.emit('call:agent_intervened', {
      callId,
      agentId,
      mode: 'monitoring'
    });

    this.io.to(`agent-${agentId}`).emit('call:joined', call);

    return true;
  }

  // 坐席完全接管
  agentTakeover(callId, agentId) {
    const call = this.activeCalls.get(callId);
    if (!call) return false;

    call.agentId = agentId;
    call.isRobotHandling = false;
    call.interventionMode = 'full_control';
    call.takeoverTime = new Date();
    call.events.push({
      type: 'agent_takeover',
      timestamp: new Date(),
      data: { agentId }
    });

    this.agentCalls.set(agentId, callId);

    // Notify all parties
    this.io.emit('call:agent_takeover', {
      callId,
      agentId
    });

    // Send notification to customer (in real implementation)
    this.addTranscript(callId, 'system', '人工客服已接入');
    
    return true;
  }

  // 释放回机器人
  releaseToRobot(callId, agentId) {
    const call = this.activeCalls.get(callId);
    if (!call || call.agentId !== agentId) return false;

    call.isRobotHandling = true;
    call.agentId = null;
    call.interventionMode = null;
    call.events.push({
      type: 'released_to_robot',
      timestamp: new Date(),
      data: { agentId }
    });

    this.agentCalls.delete(agentId);

    // Notify all parties
    this.io.emit('call:released_to_robot', {
      callId,
      agentId
    });

    // Resume robot conversation
    this.startRobotConversation(callId);

    return true;
  }

  // 添加对话记录
  addTranscript(callId, speaker, text) {
    const call = this.activeCalls.get(callId);
    if (!call) return;

    const entry = {
      speaker,
      text,
      timestamp: new Date()
    };

    call.transcript.push(entry);

    // Broadcast to all listening agents
    this.io.emit('call:transcript', {
      callId,
      ...entry
    });
  }

  // 更新通话状态
  updateCallStatus(callId, status) {
    const call = this.activeCalls.get(callId);
    if (!call) return;

    call.status = status;
    call.events.push({
      type: 'status_change',
      timestamp: new Date(),
      data: { status }
    });

    this.io.emit('call:status_update', {
      callId,
      status
    });

    // If call ended, clean up
    if (status === 'ended' || status === 'failed') {
      this.endCall(callId);
    }
  }

  // 结束通话
  async endCall(callId) {
    const call = this.activeCalls.get(callId);
    if (!call) return;

    call.endTime = new Date();
    call.duration = Math.floor((call.endTime - call.startTime) / 1000);

    // Update database
    await Call.findOneAndUpdate(
      { id: callId },
      {
        status: 'ended',
        endTime: call.endTime,
        duration: call.duration,
        transcript: call.transcript,
        events: call.events
      }
    );

    // Clean up
    if (call.agentId) {
      this.agentCalls.delete(call.agentId);
    }
    this.activeCalls.delete(callId);

    // Notify all parties
    this.io.emit('call:ended', {
      callId,
      duration: call.duration
    });
  }

  // 坐席下线
  agentOffline(agentId) {
    this.availableAgents.delete(agentId);
    
    // Check if agent was handling any calls
    const callId = this.agentCalls.get(agentId);
    if (callId) {
      this.releaseToRobot(callId, agentId);
    }
  }

  // 获取当前通话队列
  getCallQueue() {
    return Array.from(this.activeCalls.values()).map(call => ({
      id: call.id,
      customerId: call.customerId,
      phoneNumber: call.phoneNumber,
      status: call.status,
      isRobotHandling: call.isRobotHandling,
      agentId: call.agentId,
      startTime: call.startTime,
      duration: call.endTime ? call.duration : Math.floor((new Date() - call.startTime) / 1000)
    }));
  }

  // 获取通话详情
  getCallDetails(callId) {
    return this.activeCalls.get(callId);
  }

  // 坐席发送消息给客户
  agentMessage(callId, agentId, message) {
    const call = this.activeCalls.get(callId);
    if (!call || call.agentId !== agentId) return false;

    this.addTranscript(callId, 'agent', message);
    
    // In real implementation, this would be converted to speech and sent to customer
    this.io.emit('call:agent_message', {
      callId,
      agentId,
      message
    });

    return true;
  }

  // 处理客户语音输入
  handleCustomerSpeech(callId, text) {
    const call = this.activeCalls.get(callId);
    if (!call) return;

    this.addTranscript(callId, 'customer', text);

    if (call.isRobotHandling) {
      // Process with AI and generate response
      this.generateRobotResponse(callId, text);
    }
  }

  // 生成机器人回复
  async generateRobotResponse(callId, customerText) {
    const call = this.activeCalls.get(callId);
    if (!call || !call.isRobotHandling) return;

    // Simulate AI processing
    setTimeout(() => {
      // Simple rule-based responses for demo
      let response = "我理解您的需求，让我为您处理。";
      
      if (customerText.includes('人工') || customerText.includes('转接')) {
        response = "好的，我现在为您转接人工客服，请稍等。";
        this.requestAgentAssistance(callId);
      } else if (customerText.includes('查询') || customerText.includes('订单')) {
        response = "请提供您的订单号，我来为您查询。";
      } else if (customerText.includes('投诉') || customerText.includes('问题')) {
        response = "非常抱歉给您带来不便，我会立即为您处理这个问题。";
      }

      this.addTranscript(callId, 'robot', response);
    }, 1000);
  }

  // 请求人工协助
  requestAgentAssistance(callId) {
    const call = this.activeCalls.get(callId);
    if (!call) return;

    call.needsAgent = true;
    
    // Notify all available agents
    this.io.emit('call:needs_agent', {
      callId,
      priority: 'high',
      reason: 'customer_request'
    });
  }
}

module.exports = CallManager;