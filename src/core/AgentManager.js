const { v4: uuidv4 } = require('uuid');
const moment = require('moment');

/**
 * 坐席管理器 - 负责管理所有坐席的状态和通话分配
 */
class AgentManager {
  constructor(io) {
    this.io = io;
    this.agents = new Map(); // 存储所有坐席
    this.agentSockets = new Map(); // Socket ID 到 Agent ID 的映射
    this.callAssignments = new Map(); // 通话分配映射
  }

  /**
   * 坐席连接
   */
  connectAgent(socket, agentInfo) {
    const agentId = agentInfo.id || uuidv4();
    
    const agent = {
      id: agentId,
      socketId: socket.id,
      name: agentInfo.name,
      extension: agentInfo.extension,
      department: agentInfo.department || 'default',
      skills: agentInfo.skills || [],
      status: 'available', // available, busy, break, offline
      currentCallId: null,
      connectedAt: moment().toISOString(),
      lastActivity: moment().toISOString(),
      totalCalls: 0,
      totalTalkTime: 0,
      averageHandleTime: 0,
      callHistory: [],
      performance: {
        callsHandled: 0,
        callsTransferred: 0,
        averageRating: 0,
        totalRating: 0,
        ratingCount: 0
      }
    };

    this.agents.set(agentId, agent);
    this.agentSockets.set(socket.id, agentId);

    console.log(`坐席连接: ${agentId} (${agent.name}) - Socket: ${socket.id}`);

    // 通知其他坐席和管理员
    this.io.emit('agent_connected', {
      agentId,
      name: agent.name,
      status: agent.status,
      timestamp: agent.connectedAt
    });

    // 发送欢迎消息给坐席
    socket.emit('agent_welcome', {
      agentId,
      message: `欢迎，${agent.name}！您已成功连接到系统。`,
      systemInfo: this.getSystemInfo()
    });

    // 发送当前等待转接的通话列表
    socket.emit('pending_transfers', this.getPendingTransfers());

    return agentId;
  }

  /**
   * 坐席断开连接
   */
  disconnectAgent(socketId) {
    const agentId = this.agentSockets.get(socketId);
    if (!agentId) return;

    const agent = this.agents.get(agentId);
    if (!agent) return;

    console.log(`坐席断开连接: ${agentId} (${agent.name})`);

    // 如果坐席正在通话中，需要处理
    if (agent.currentCallId) {
      this.handleAgentDisconnectDuringCall(agentId, agent.currentCallId);
    }

    // 更新状态
    agent.status = 'offline';
    agent.disconnectedAt = moment().toISOString();

    // 清理映射
    this.agentSockets.delete(socketId);

    // 通知其他人
    this.io.emit('agent_disconnected', {
      agentId,
      name: agent.name,
      timestamp: agent.disconnectedAt
    });
  }

  /**
   * 更新坐席状态
   */
  updateAgentStatus(socketId, newStatus) {
    const agentId = this.agentSockets.get(socketId);
    if (!agentId) return false;

    const agent = this.agents.get(agentId);
    if (!agent) return false;

    const oldStatus = agent.status;
    agent.status = newStatus;
    agent.lastActivity = moment().toISOString();

    console.log(`坐席状态更新: ${agentId} (${agent.name}) ${oldStatus} -> ${newStatus}`);

    // 通知状态变更
    this.io.emit('agent_status_changed', {
      agentId,
      name: agent.name,
      oldStatus,
      newStatus,
      timestamp: agent.lastActivity
    });

    // 如果坐席变为可用状态，检查是否有等待的转接
    if (newStatus === 'available') {
      this.checkPendingTransfers();
    }

    return true;
  }

  /**
   * 坐席主动接管通话
   */
  takeoverCall(socketId, callId, callManager) {
    const agentId = this.agentSockets.get(socketId);
    if (!agentId) return false;

    const agent = this.agents.get(agentId);
    if (!agent || agent.status !== 'available') {
      this.io.to(socketId).emit('takeover_failed', {
        reason: '坐席状态不可用',
        callId
      });
      return false;
    }

    // 检查通话是否存在且可以被接管
    const call = callManager.getCall(callId);
    if (!call || call.status !== 'robot_talking') {
      this.io.to(socketId).emit('takeover_failed', {
        reason: '通话状态不允许接管',
        callId
      });
      return false;
    }

    // 执行接管
    const success = callManager.agentTakeover(callId, agentId);
    if (success) {
      this.assignCallToAgent(agentId, callId);
      
      console.log(`坐席主动接管通话: ${agentId} -> ${callId}`);
      
      // 通知坐席接管成功
      this.io.to(socketId).emit('takeover_success', {
        callId,
        customerInfo: {
          name: call.customerName,
          phone: call.customerPhone
        },
        conversationHistory: {
          robotMessages: call.robotMessages,
          customerResponses: call.customerResponses
        },
        timestamp: moment().toISOString()
      });

      return true;
    }

    return false;
  }

  /**
   * 自动分配通话给坐席
   */
  autoAssignCall(callId, callManager, priority = 'normal') {
    const availableAgents = this.getAvailableAgents();
    
    if (availableAgents.length === 0) {
      console.log(`没有可用坐席处理通话: ${callId}`);
      return false;
    }

    // 根据优先级和技能匹配选择最佳坐席
    const bestAgent = this.selectBestAgent(availableAgents, callId, priority);
    
    if (bestAgent) {
      const success = callManager.agentTakeover(callId, bestAgent.id);
      if (success) {
        this.assignCallToAgent(bestAgent.id, callId);
        
        console.log(`自动分配通话: ${bestAgent.id} (${bestAgent.name}) -> ${callId}`);
        
        // 通知坐席
        this.io.to(bestAgent.socketId).emit('call_assigned', {
          callId,
          customerInfo: {
            name: callManager.getCall(callId).customerName,
            phone: callManager.getCall(callId).customerPhone
          },
          priority,
          timestamp: moment().toISOString()
        });

        return true;
      }
    }

    return false;
  }

  /**
   * 分配通话给坐席
   */
  assignCallToAgent(agentId, callId) {
    const agent = this.agents.get(agentId);
    if (!agent) return false;

    agent.status = 'busy';
    agent.currentCallId = callId;
    agent.totalCalls++;
    agent.lastActivity = moment().toISOString();

    this.callAssignments.set(callId, agentId);

    // 记录通话开始时间
    agent.currentCallStartTime = moment().toISOString();

    return true;
  }

  /**
   * 释放坐席（通话结束）
   */
  releaseAgent(socketId) {
    const agentId = this.agentSockets.get(socketId);
    if (!agentId) return;

    const agent = this.agents.get(agentId);
    if (!agent) return;

    if (agent.currentCallId) {
      // 计算通话时长
      if (agent.currentCallStartTime) {
        const callDuration = moment().diff(moment(agent.currentCallStartTime), 'seconds');
        agent.totalTalkTime += callDuration;
        agent.averageHandleTime = Math.round(agent.totalTalkTime / agent.totalCalls);
      }

      // 记录通话历史
      agent.callHistory.push({
        callId: agent.currentCallId,
        startTime: agent.currentCallStartTime,
        endTime: moment().toISOString(),
        duration: moment().diff(moment(agent.currentCallStartTime), 'seconds')
      });

      // 清理分配
      this.callAssignments.delete(agent.currentCallId);
      agent.currentCallId = null;
      agent.currentCallStartTime = null;
    }

    // 设置为可用状态
    agent.status = 'available';
    agent.lastActivity = moment().toISOString();

    console.log(`释放坐席: ${agentId} (${agent.name})`);

    // 通知状态变更
    this.io.emit('agent_status_changed', {
      agentId,
      name: agent.name,
      oldStatus: 'busy',
      newStatus: 'available',
      timestamp: agent.lastActivity
    });

    // 检查是否有等待的转接
    this.checkPendingTransfers();
  }

  /**
   * 获取可用坐席
   */
  getAvailableAgents() {
    return Array.from(this.agents.values()).filter(
      agent => agent.status === 'available'
    );
  }

  /**
   * 选择最佳坐席
   */
  selectBestAgent(availableAgents, callId, priority) {
    if (availableAgents.length === 0) return null;

    // 简单的负载均衡：选择处理通话数最少的坐席
    return availableAgents.reduce((best, current) => {
      if (!best) return current;
      
      // 优先考虑通话数较少的坐席
      if (current.totalCalls < best.totalCalls) return current;
      if (current.totalCalls > best.totalCalls) return best;
      
      // 通话数相同时，选择平均处理时间较短的
      return current.averageHandleTime < best.averageHandleTime ? current : best;
    });
  }

  /**
   * 检查等待转接的通话
   */
  checkPendingTransfers() {
    // 这里需要与 CallManager 协作
    // 暂时发送事件通知有可用坐席
    this.io.emit('agent_available', {
      availableCount: this.getAvailableAgents().length,
      timestamp: moment().toISOString()
    });
  }

  /**
   * 处理坐席通话中断开连接
   */
  handleAgentDisconnectDuringCall(agentId, callId) {
    console.log(`坐席通话中断开连接: ${agentId} - 通话: ${callId}`);
    
    // 通知系统需要重新分配通话或转回机器人
    this.io.emit('agent_disconnect_during_call', {
      agentId,
      callId,
      timestamp: moment().toISOString()
    });

    // 清理分配
    this.callAssignments.delete(callId);
  }

  /**
   * 获取坐席信息
   */
  getAgent(agentId) {
    return this.agents.get(agentId);
  }

  /**
   * 通过Socket ID获取坐席
   */
  getAgentBySocketId(socketId) {
    const agentId = this.agentSockets.get(socketId);
    return agentId ? this.agents.get(agentId) : null;
  }

  /**
   * 通过通话ID获取坐席
   */
  getAgentByCallId(callId) {
    const agentId = this.callAssignments.get(callId);
    return agentId ? this.agents.get(agentId) : null;
  }

  /**
   * 获取所有坐席
   */
  getAllAgents() {
    return Array.from(this.agents.values());
  }

  /**
   * 获取在线坐席
   */
  getOnlineAgents() {
    return Array.from(this.agents.values()).filter(
      agent => agent.status !== 'offline'
    );
  }

  /**
   * 获取等待转接的通话（需要与CallManager协作）
   */
  getPendingTransfers() {
    // 这里应该从CallManager获取等待转接的通话
    // 暂时返回空数组
    return [];
  }

  /**
   * 获取系统信息
   */
  getSystemInfo() {
    const agents = Array.from(this.agents.values());
    
    return {
      totalAgents: agents.length,
      onlineAgents: this.getOnlineAgents().length,
      availableAgents: this.getAvailableAgents().length,
      busyAgents: agents.filter(agent => agent.status === 'busy').length,
      timestamp: moment().toISOString()
    };
  }

  /**
   * 获取坐席统计信息
   */
  getAgentStatistics(agentId) {
    const agent = this.agents.get(agentId);
    if (!agent) return null;

    const today = moment().startOf('day');
    const todayCalls = agent.callHistory.filter(call => 
      moment(call.startTime).isAfter(today)
    );

    return {
      agentId,
      name: agent.name,
      status: agent.status,
      totalCalls: agent.totalCalls,
      todayCalls: todayCalls.length,
      totalTalkTime: agent.totalTalkTime,
      averageHandleTime: agent.averageHandleTime,
      connectedAt: agent.connectedAt,
      lastActivity: agent.lastActivity,
      performance: agent.performance,
      currentCall: agent.currentCallId ? {
        callId: agent.currentCallId,
        startTime: agent.currentCallStartTime,
        duration: agent.currentCallStartTime ? 
          moment().diff(moment(agent.currentCallStartTime), 'seconds') : 0
      } : null
    };
  }

  /**
   * 获取所有坐席统计
   */
  getAllAgentStatistics() {
    return Array.from(this.agents.keys()).map(agentId => 
      this.getAgentStatistics(agentId)
    );
  }

  /**
   * 更新坐席评分
   */
  updateAgentRating(agentId, rating, callId) {
    const agent = this.agents.get(agentId);
    if (!agent) return false;

    agent.performance.totalRating += rating;
    agent.performance.ratingCount++;
    agent.performance.averageRating = 
      Math.round((agent.performance.totalRating / agent.performance.ratingCount) * 10) / 10;

    console.log(`坐席评分更新: ${agentId} - 评分: ${rating} - 平均: ${agent.performance.averageRating}`);

    // 通知评分更新
    this.io.emit('agent_rating_updated', {
      agentId,
      rating,
      averageRating: agent.performance.averageRating,
      callId,
      timestamp: moment().toISOString()
    });

    return true;
  }
}

module.exports = AgentManager;