const express = require('express');
const router = express.Router();

/**
 * 通话相关路由
 */
function createCallRoutes(callManager, agentManager, robotDialogue) {
  
  /**
   * 创建外呼任务
   */
  router.post('/create', (req, res) => {
    try {
      const { customerInfo, campaignId } = req.body;
      
      if (!customerInfo || !customerInfo.phone) {
        return res.status(400).json({
          success: false,
          message: '客户信息不完整'
        });
      }

      const callId = callManager.createOutboundCall(customerInfo, campaignId);
      
      res.json({
        success: true,
        callId,
        message: '外呼任务创建成功'
      });
    } catch (error) {
      console.error('创建外呼任务失败:', error);
      res.status(500).json({
        success: false,
        message: '创建外呼任务失败'
      });
    }
  });

  /**
   * 批量创建外呼任务
   */
  router.post('/batch-create', (req, res) => {
    try {
      const { customers, campaignId } = req.body;
      
      if (!customers || !Array.isArray(customers)) {
        return res.status(400).json({
          success: false,
          message: '客户列表格式错误'
        });
      }

      const callIds = [];
      const errors = [];

      customers.forEach((customer, index) => {
        try {
          if (customer.phone) {
            const callId = callManager.createOutboundCall(customer, campaignId);
            callIds.push({ index, callId, phone: customer.phone });
          } else {
            errors.push({ index, error: '缺少电话号码' });
          }
        } catch (error) {
          errors.push({ index, error: error.message });
        }
      });

      res.json({
        success: true,
        created: callIds.length,
        errors: errors.length,
        callIds,
        errors
      });
    } catch (error) {
      console.error('批量创建外呼任务失败:', error);
      res.status(500).json({
        success: false,
        message: '批量创建外呼任务失败'
      });
    }
  });

  /**
   * 获取通话详情
   */
  router.get('/:callId', (req, res) => {
    try {
      const { callId } = req.params;
      const call = callManager.getCall(callId);
      
      if (!call) {
        return res.status(404).json({
          success: false,
          message: '通话不存在'
        });
      }

      // 获取对话统计
      const conversationStats = robotDialogue.getConversationStatistics(callId);
      
      res.json({
        success: true,
        call: {
          ...call,
          conversationStats
        }
      });
    } catch (error) {
      console.error('获取通话详情失败:', error);
      res.status(500).json({
        success: false,
        message: '获取通话详情失败'
      });
    }
  });

  /**
   * 获取所有通话列表
   */
  router.get('/', (req, res) => {
    try {
      const { status, page = 1, limit = 20 } = req.query;
      let calls = callManager.getAllCalls();

      // 状态过滤
      if (status) {
        calls = calls.filter(call => call.status === status);
      }

      // 分页
      const startIndex = (page - 1) * limit;
      const endIndex = startIndex + parseInt(limit);
      const paginatedCalls = calls.slice(startIndex, endIndex);

      res.json({
        success: true,
        calls: paginatedCalls,
        pagination: {
          page: parseInt(page),
          limit: parseInt(limit),
          total: calls.length,
          pages: Math.ceil(calls.length / limit)
        }
      });
    } catch (error) {
      console.error('获取通话列表失败:', error);
      res.status(500).json({
        success: false,
        message: '获取通话列表失败'
      });
    }
  });

  /**
   * 获取活跃通话
   */
  router.get('/active/list', (req, res) => {
    try {
      const activeCalls = callManager.getActiveCalls();
      
      res.json({
        success: true,
        calls: activeCalls,
        count: activeCalls.length
      });
    } catch (error) {
      console.error('获取活跃通话失败:', error);
      res.status(500).json({
        success: false,
        message: '获取活跃通话失败'
      });
    }
  });

  /**
   * 获取等待转接的通话
   */
  router.get('/pending-transfer/list', (req, res) => {
    try {
      const pendingCalls = callManager.getPendingTransferCalls();
      
      res.json({
        success: true,
        calls: pendingCalls,
        count: pendingCalls.length
      });
    } catch (error) {
      console.error('获取等待转接通话失败:', error);
      res.status(500).json({
        success: false,
        message: '获取等待转接通话失败'
      });
    }
  });

  /**
   * 手动结束通话
   */
  router.post('/:callId/end', (req, res) => {
    try {
      const { callId } = req.params;
      const { result } = req.body;
      
      const call = callManager.getCall(callId);
      if (!call) {
        return res.status(404).json({
          success: false,
          message: '通话不存在'
        });
      }

      callManager.endCall(callId, result);
      
      // 清理对话上下文
      robotDialogue.clearConversationContext(callId);
      
      res.json({
        success: true,
        message: '通话已结束'
      });
    } catch (error) {
      console.error('结束通话失败:', error);
      res.status(500).json({
        success: false,
        message: '结束通话失败'
      });
    }
  });

  /**
   * 模拟客户响应（测试用）
   */
  router.post('/:callId/customer-response', async (req, res) => {
    try {
      const { callId } = req.params;
      const { message } = req.body;
      
      const call = callManager.getCall(callId);
      if (!call) {
        return res.status(404).json({
          success: false,
          message: '通话不存在'
        });
      }

      // 记录客户响应
      callManager.handleCustomerResponse(callId, message);

      // 如果是机器人对话状态，处理响应
      if (call.status === 'robot_talking') {
        const response = await robotDialogue.processCustomerResponse(callId, message);
        callManager.handleRobotResponse(callId, response);
        
        res.json({
          success: true,
          robotResponse: response
        });
      } else {
        res.json({
          success: true,
          message: '客户响应已记录'
        });
      }
    } catch (error) {
      console.error('处理客户响应失败:', error);
      res.status(500).json({
        success: false,
        message: '处理客户响应失败'
      });
    }
  });

  /**
   * 请求转接坐席
   */
  router.post('/:callId/request-transfer', (req, res) => {
    try {
      const { callId } = req.params;
      const { reason } = req.body;
      
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
          message: '当前状态不支持转接'
        });
      }

      callManager.requestAgentTransfer(callId, reason);
      
      res.json({
        success: true,
        message: '转接请求已发送'
      });
    } catch (error) {
      console.error('请求转接失败:', error);
      res.status(500).json({
        success: false,
        message: '请求转接失败'
      });
    }
  });

  /**
   * 获取通话统计
   */
  router.get('/statistics/overview', (req, res) => {
    try {
      const stats = callManager.getStatistics();
      
      res.json({
        success: true,
        statistics: stats
      });
    } catch (error) {
      console.error('获取通话统计失败:', error);
      res.status(500).json({
        success: false,
        message: '获取通话统计失败'
      });
    }
  });

  /**
   * 获取对话历史
   */
  router.get('/:callId/conversation', (req, res) => {
    try {
      const { callId } = req.params;
      const call = callManager.getCall(callId);
      
      if (!call) {
        return res.status(404).json({
          success: false,
          message: '通话不存在'
        });
      }

      const conversationContext = robotDialogue.getConversationContext(callId);
      
      res.json({
        success: true,
        conversation: {
          callInfo: {
            id: call.id,
            customerName: call.customerName,
            customerPhone: call.customerPhone,
            status: call.status,
            startedAt: call.startedAt,
            duration: call.callDuration
          },
          messages: conversationContext ? conversationContext.messages : [],
          robotMessages: call.robotMessages || [],
          agentMessages: call.agentMessages || [],
          customerResponses: call.customerResponses || []
        }
      });
    } catch (error) {
      console.error('获取对话历史失败:', error);
      res.status(500).json({
        success: false,
        message: '获取对话历史失败'
      });
    }
  });

  return router;
}

module.exports = createCallRoutes;