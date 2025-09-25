const { v4: uuidv4 } = require('uuid');

class AgentManager {
  constructor() {
    this.agents = new Map();
    this.agentSessions = new Map();
    this.initializeDefaultAgents();
  }

  initializeDefaultAgents() {
    // Add some default agents for testing
    const defaultAgents = [
      {
        id: 'agent001',
        name: '张三',
        email: 'zhangsan@example.com',
        phone: '+8613800138001',
        status: 'offline',
        skills: ['sales', 'support'],
        maxConcurrentCalls: 3
      },
      {
        id: 'agent002',
        name: '李四',
        email: 'lisi@example.com',
        phone: '+8613800138002',
        status: 'offline',
        skills: ['technical', 'support'],
        maxConcurrentCalls: 2
      },
      {
        id: 'agent003',
        name: '王五',
        email: 'wangwu@example.com',
        phone: '+8613800138003',
        status: 'offline',
        skills: ['sales', 'vip'],
        maxConcurrentCalls: 5
      }
    ];

    defaultAgents.forEach(agent => {
      this.agents.set(agent.id, {
        ...agent,
        activeCalls: [],
        totalCallsHandled: 0,
        lastActiveTime: null
      });
    });
  }

  async authenticateAgent(agentId, token) {
    // In production, validate token against database or auth service
    // For now, simple validation
    const agent = this.agents.get(agentId);
    
    if (!agent) {
      throw new Error('Agent not found');
    }

    // Generate session token
    const sessionToken = uuidv4();
    const session = {
      agentId,
      token: sessionToken,
      loginTime: new Date(),
      lastActivity: new Date()
    };

    this.agentSessions.set(sessionToken, session);

    // Update agent status
    agent.status = 'available';
    agent.lastActiveTime = new Date();

    return {
      ...agent,
      sessionToken
    };
  }

  async logoutAgent(agentId) {
    const agent = this.agents.get(agentId);
    if (!agent) {
      throw new Error('Agent not found');
    }

    // Update status
    agent.status = 'offline';
    agent.lastActiveTime = new Date();

    // Remove sessions
    for (const [token, session] of this.agentSessions.entries()) {
      if (session.agentId === agentId) {
        this.agentSessions.delete(token);
      }
    }

    return agent;
  }

  async getAvailableAgents(skills = []) {
    const availableAgents = [];

    for (const agent of this.agents.values()) {
      if (agent.status === 'available' && 
          agent.activeCalls.length < agent.maxConcurrentCalls) {
        
        // Check if agent has required skills
        if (skills.length === 0 || 
            skills.some(skill => agent.skills.includes(skill))) {
          availableAgents.push(agent);
        }
      }
    }

    // Sort by number of active calls (load balancing)
    availableAgents.sort((a, b) => a.activeCalls.length - b.activeCalls.length);

    return availableAgents;
  }

  async assignCallToAgent(callId, agentId) {
    const agent = this.agents.get(agentId);
    if (!agent) {
      throw new Error('Agent not found');
    }

    if (agent.status !== 'available') {
      throw new Error('Agent is not available');
    }

    if (agent.activeCalls.length >= agent.maxConcurrentCalls) {
      throw new Error('Agent has reached maximum concurrent calls');
    }

    // Add call to agent's active calls
    agent.activeCalls.push({
      callId,
      startTime: new Date()
    });

    // Update status if at capacity
    if (agent.activeCalls.length >= agent.maxConcurrentCalls) {
      agent.status = 'busy';
    }

    agent.totalCallsHandled++;
    agent.lastActiveTime = new Date();

    return agent;
  }

  async releaseCallFromAgent(callId, agentId) {
    const agent = this.agents.get(agentId);
    if (!agent) {
      throw new Error('Agent not found');
    }

    // Remove call from active calls
    agent.activeCalls = agent.activeCalls.filter(call => call.callId !== callId);

    // Update status if was busy
    if (agent.status === 'busy' && 
        agent.activeCalls.length < agent.maxConcurrentCalls) {
      agent.status = 'available';
    }

    agent.lastActiveTime = new Date();

    return agent;
  }

  async updateAgentStatus(agentId, status) {
    const agent = this.agents.get(agentId);
    if (!agent) {
      throw new Error('Agent not found');
    }

    const validStatuses = ['available', 'busy', 'break', 'offline'];
    if (!validStatuses.includes(status)) {
      throw new Error('Invalid status');
    }

    agent.status = status;
    agent.lastActiveTime = new Date();

    return agent;
  }

  async getAgentStats(agentId) {
    const agent = this.agents.get(agentId);
    if (!agent) {
      throw new Error('Agent not found');
    }

    return {
      id: agent.id,
      name: agent.name,
      status: agent.status,
      activeCalls: agent.activeCalls.length,
      totalCallsHandled: agent.totalCallsHandled,
      maxConcurrentCalls: agent.maxConcurrentCalls,
      lastActiveTime: agent.lastActiveTime,
      skills: agent.skills
    };
  }

  async getAllAgentsStats() {
    const stats = [];

    for (const agent of this.agents.values()) {
      stats.push({
        id: agent.id,
        name: agent.name,
        status: agent.status,
        activeCalls: agent.activeCalls.length,
        totalCallsHandled: agent.totalCallsHandled,
        maxConcurrentCalls: agent.maxConcurrentCalls,
        lastActiveTime: agent.lastActiveTime
      });
    }

    return stats;
  }

  validateSession(token) {
    const session = this.agentSessions.get(token);
    if (!session) {
      return null;
    }

    // Update last activity
    session.lastActivity = new Date();

    // Check if session expired (24 hours)
    const sessionAge = Date.now() - session.loginTime.getTime();
    if (sessionAge > 24 * 60 * 60 * 1000) {
      this.agentSessions.delete(token);
      return null;
    }

    return session;
  }
}

module.exports = new AgentManager();