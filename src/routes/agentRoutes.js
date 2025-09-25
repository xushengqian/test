const express = require('express');
const router = express.Router();

/**
 * 坐席相关路由
 */
function createAgentRoutes(agentManager, callManager) {
  
  /**
   * 坐席登录/连接
   */
  router.post('/login', (req, res) => {
    try {
      const { agentInfo } = req.body;
      
      if (!agentInfo || !agentInfo.name) {
        return res.status(400).json({
          success: false,
          message: '坐席信息不完整'
        });
      }

      // 这里应该验证坐席身份，暂时简化处理
      res.json({
        success: true,
        message: '请通过WebSocket连接进行坐席登录',
        agentInfo: {
          ...agentInfo,
          loginTime: new Date().toISOString()
        }
      });
    } catch (error) {
      console.error('坐席登录失败:', error);
      res.status(500).json({
        success: false,
        message: '坐席登录失败'
      });
    }
  });

  /**
   * 获取坐席信息
   */
  router.get('/:agentId', (req, res) => {
    try {
      const { agentId } = req.params;
      const agent = agentManager.getAgent(agentId);
      
      if (!agent) {
        return res.status(404).json({
          success: false,
          message: '坐席不存在'
        });
      }

      const statistics = agentManager.getAgentStatistics(agentId);
      
      res.json({
        success: true,
        agent: {
          ...agent,
          statistics
        }
      });
    } catch (error) {
      console.error('获取坐席信息失败:', error);
      res.status(500).json({
        success: false,
        message: '获取坐席信息失败'
      });
    }
  });

  /**
   * 获取所有坐席列表
   */
  router.get('/', (req, res) => {
    try {
      const { status } = req.query;
      let agents = agentManager.getAllAgents();

      // 状态过滤
      if (status) {
        agents = agents.filter(agent => agent.status === status);
      }

      // 添加统计信息
      const agentsWithStats = agents.map(agent => ({
        ...agent,
        statistics: agentManager.getAgentStatistics(agent.id)
      }));

      res.json({
        success: true,
        agents: agentsWithStats,
        count: agentsWithStats.length
      });
    } catch (error) {
      console.error('获取坐席列表失败:', error);
      res.status(500).json({
        success: false,
        message: '获取坐席列表失败'
      });
    }
  });

  /**
   * 获取在线坐席
   */
  router.get('/online/list', (req, res) => {
    try {
      const onlineAgents = agentManager.getOnlineAgents();
      
      res.json({
        success: true,
        agents: onlineAgents,
        count: onlineAgents.length
      });
    } catch (error) {
      console.error('获取在线坐席失败:', error);
      res.status(500).json({
        success: false,
        message: '获取在线坐席失败'
      });
    }
  });

  /**
   * 获取可用坐席
   */
  router.get('/available/list', (req, res) => {
    try {
      const availableAgents = agentManager.getAvailableAgents();
      
      res.json({
        success: true,
        agents: availableAgents,
        count: availableAgents.length
      });
    } catch (error) {
      console.error('获取可用坐席失败:', error);
      res.status(500).json({
        success: false,
        message: '获取可用坐席失败'
      });
    }
  });

  /**
   * 手动分配通话给坐席
   */
  router.post('/:agentId/assign-call', (req, res) => {
    try {
      const { agentId } = req.params;
      const { callId } = req.body;
      
      const agent = agentManager.getAgent(agentId);
      if (!agent) {
        return res.status(404).json({
          success: false,
          message: '坐席不存在'
        });
      }

      if (agent.status !== 'available') {
        return res.status(400).json({
          success: false,
          message: '坐席当前不可用'
        });
      }

      const call = callManager.getCall(callId);
      if (!call) {
        return res.status(404).json({
          success: false,
          message: '通话不存在'
        });
      }

      if (call.status !== 'robot_talking') {
        return res.status(400).json({
          success: false,
          message: '通话状态不支持分配'
        });
      }

      // 执行分配
      const success = callManager.agentTakeover(callId, agentId);
      if (success) {
        agentManager.assignCallToAgent(agentId, callId);
        
        res.json({
          success: true,
          message: '通话分配成功'
        });
      } else {
        res.status(500).json({
          success: false,
          message: '通话分配失败'
        });
      }
    } catch (error) {
      console.error('分配通话失败:', error);
      res.status(500).json({
        success: false,
        message: '分配通话失败'
      });
    }
  });

  /**
   * 自动分配通话
   */
  router.post('/auto-assign', (req, res) => {
    try {
      const { callId, priority = 'normal' } = req.body;
      
      const call = callManager.getCall(callId);
      if (!call) {
        return res.status(404).json({
          success: false,
          message: '通话不存在'
        });
      }

      const success = agentManager.autoAssignCall(callId, callManager, priority);
      
      if (success) {
        res.json({
          success: true,
          message: '通话自动分配成功'
        });
      } else {
        res.status(400).json({
          success: false,
          message: '没有可用坐席或分配失败'
        });
      }
    } catch (error) {
      console.error('自动分配通话失败:', error);
      res.status(500).json({
        success: false,
        message: '自动分配通话失败'
      });
    }
  });

  /**
   * 坐席强制下线
   */
  router.post('/:agentId/force-offline', (req, res) => {
    try {
      const { agentId } = req.params;
      const { reason } = req.body;
      
      const agent = agentManager.getAgent(agentId);
      if (!agent) {
        return res.status(404).json({
          success: false,
          message: '坐席不存在'
        });
      }

      // 如果坐席正在通话中，需要特殊处理
      if (agent.currentCallId) {
        // 这里可以实现转接给其他坐席或转回机器人的逻辑
        console.log(`坐席 ${agentId} 在通话中被强制下线，通话ID: ${agent.currentCallId}`);
      }

      // 强制断开连接
      agentManager.disconnectAgent(agent.socketId);
      
      res.json({
        success: true,
        message: '坐席已强制下线',
        reason
      });
    } catch (error) {
      console.error('强制坐席下线失败:', error);
      res.status(500).json({
        success: false,
        message: '强制坐席下线失败'
      });
    }
  });

  /**
   * 更新坐席评分
   */
  router.post('/:agentId/rating', (req, res) => {
    try {
      const { agentId } = req.params;
      const { rating, callId, comment } = req.body;
      
      if (!rating || rating < 1 || rating > 5) {
        return res.status(400).json({
          success: false,
          message: '评分必须在1-5之间'
        });
      }

      const success = agentManager.updateAgentRating(agentId, rating, callId);
      
      if (success) {
        res.json({
          success: true,
          message: '坐席评分更新成功'
        });
      } else {
        res.status(404).json({
          success: false,
          message: '坐席不存在'
        });
      }
    } catch (error) {
      console.error('更新坐席评分失败:', error);
      res.status(500).json({
        success: false,
        message: '更新坐席评分失败'
      });
    }
  });

  /**
   * 获取坐席统计信息
   */
  router.get('/:agentId/statistics', (req, res) => {
    try {
      const { agentId } = req.params;
      const statistics = agentManager.getAgentStatistics(agentId);
      
      if (!statistics) {
        return res.status(404).json({
          success: false,
          message: '坐席不存在'
        });
      }

      res.json({
        success: true,
        statistics
      });
    } catch (error) {
      console.error('获取坐席统计失败:', error);
      res.status(500).json({
        success: false,
        message: '获取坐席统计失败'
      });
    }
  });

  /**
   * 获取所有坐席统计
   */
  router.get('/statistics/all', (req, res) => {
    try {
      const allStatistics = agentManager.getAllAgentStatistics();
      
      res.json({
        success: true,
        statistics: allStatistics
      });
    } catch (error) {
      console.error('获取所有坐席统计失败:', error);
      res.status(500).json({
        success: false,
        message: '获取所有坐席统计失败'
      });
    }
  });

  /**
   * 获取系统信息
   */
  router.get('/system/info', (req, res) => {
    try {
      const systemInfo = agentManager.getSystemInfo();
      
      res.json({
        success: true,
        systemInfo
      });
    } catch (error) {
      console.error('获取系统信息失败:', error);
      res.status(500).json({
        success: false,
        message: '获取系统信息失败'
      });
    }
  });

  /**
   * 坐席工作报告
   */
  router.get('/:agentId/work-report', (req, res) => {
    try {
      const { agentId } = req.params;
      const { date } = req.query;
      
      const agent = agentManager.getAgent(agentId);
      if (!agent) {
        return res.status(404).json({
          success: false,
          message: '坐席不存在'
        });
      }

      // 生成工作报告
      const report = {
        agentId,
        agentName: agent.name,
        date: date || new Date().toISOString().split('T')[0],
        workDuration: agent.totalTalkTime,
        callsHandled: agent.totalCalls,
        averageHandleTime: agent.averageHandleTime,
        performance: agent.performance,
        callHistory: agent.callHistory.slice(-10) // 最近10通电话
      };

      res.json({
        success: true,
        report
      });
    } catch (error) {
      console.error('获取工作报告失败:', error);
      res.status(500).json({
        success: false,
        message: '获取工作报告失败'
      });
    }
  });

  return router;
}

module.exports = createAgentRoutes;