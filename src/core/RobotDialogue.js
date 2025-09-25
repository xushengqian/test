const moment = require('moment');

/**
 * 机器人对话模块 - 处理AI对话逻辑和语音交互
 */
class RobotDialogue {
  constructor() {
    this.conversations = new Map(); // 存储对话上下文
    this.intents = this.initializeIntents();
    this.responses = this.initializeResponses();
    this.transferTriggers = this.initializeTransferTriggers();
  }

  /**
   * 初始化意图识别规则
   */
  initializeIntents() {
    return {
      greeting: {
        patterns: ['你好', '您好', '喂', 'hello', 'hi'],
        confidence: 0.9
      },
      confirmation: {
        patterns: ['是的', '对', '是', '没错', '正确', '对的'],
        confidence: 0.8
      },
      denial: {
        patterns: ['不是', '不对', '错了', '不', '没有'],
        confidence: 0.8
      },
      request_human: {
        patterns: ['人工', '转人工', '客服', '人工客服', '转接', '找人'],
        confidence: 0.9
      },
      complaint: {
        patterns: ['投诉', '不满意', '问题', '故障', '错误', '不好'],
        confidence: 0.7
      },
      inquiry: {
        patterns: ['咨询', '了解', '问一下', '请问', '想知道'],
        confidence: 0.6
      },
      goodbye: {
        patterns: ['再见', '拜拜', '挂了', '结束', '不需要'],
        confidence: 0.8
      },
      unclear: {
        patterns: ['什么', '听不清', '没听懂', '重复', '再说一遍'],
        confidence: 0.7
      }
    };
  }

  /**
   * 初始化响应模板
   */
  initializeResponses() {
    return {
      greeting: [
        '您好！我是智能客服助手，很高兴为您服务。',
        '您好！请问有什么可以帮助您的吗？'
      ],
      confirmation: [
        '好的，我明白了。',
        '收到，让我为您处理。',
        '明白了，请稍等。'
      ],
      name_confirmation: [
        '好的，{name}先生/女士，请问今天联系您是想了解我们的{product}服务。',
        '谢谢确认，{name}，我想向您介绍一下我们最新的{product}产品。'
      ],
      name_denial: [
        '不好意思，请问您是哪位？我可能拨错了。',
        '抱歉打扰了，请问这里是{phone}吗？'
      ],
      product_introduction: [
        '我们的{product}服务具有以下优势：高效、安全、便捷。您是否有兴趣了解更多详情？',
        '关于{product}，我们提供专业的解决方案，可以帮助您{benefit}。您想了解具体的服务内容吗？'
      ],
      transfer_to_human: [
        '好的，我马上为您转接人工客服，请稍等。',
        '明白了，正在为您联系专业客服人员，请不要挂机。'
      ],
      complaint_handling: [
        '非常抱歉给您带来不便，我立即为您转接专业客服处理您的问题。',
        '我理解您的困扰，让我为您安排专门的客服人员来解决这个问题。'
      ],
      unclear_response: [
        '不好意思，我没有听清楚，您能再说一遍吗？',
        '抱歉，信号可能不太好，请您再重复一下。'
      ],
      goodbye: [
        '好的，感谢您的时间，祝您生活愉快！',
        '谢谢您，如有需要随时联系我们，再见！'
      ],
      default: [
        '我理解您的意思，让我为您转接专业的客服人员来详细解答。',
        '关于这个问题，建议您与我们的专业顾问详细沟通，我为您转接。'
      ]
    };
  }

  /**
   * 初始化转接触发条件
   */
  initializeTransferTriggers() {
    return {
      explicit_request: {
        intents: ['request_human'],
        threshold: 0.8,
        reason: '客户要求人工服务'
      },
      complaint: {
        intents: ['complaint'],
        threshold: 0.7,
        reason: '客户投诉需要人工处理'
      },
      complex_inquiry: {
        intents: ['inquiry'],
        consecutive_count: 3,
        reason: '复杂咨询需要专业人员'
      },
      unclear_communication: {
        intents: ['unclear'],
        consecutive_count: 2,
        reason: '沟通不畅需要人工协助'
      },
      conversation_length: {
        max_turns: 10,
        reason: '对话轮次过多，转接人工'
      }
    };
  }

  /**
   * 处理客户响应
   */
  async processCustomerResponse(callId, customerMessage) {
    try {
      // 获取或创建对话上下文
      let context = this.conversations.get(callId);
      if (!context) {
        context = this.createConversationContext(callId);
        this.conversations.set(callId, context);
      }

      // 更新对话历史
      context.messages.push({
        type: 'customer',
        message: customerMessage,
        timestamp: moment().toISOString()
      });

      context.turnCount++;
      context.lastActivity = moment().toISOString();

      // 意图识别
      const intent = this.recognizeIntent(customerMessage);
      context.lastIntent = intent;

      console.log(`客户意图识别: ${callId} - ${intent.name} (置信度: ${intent.confidence})`);

      // 检查是否需要转接
      const transferCheck = this.checkTransferConditions(context, intent);
      if (transferCheck.needsTransfer) {
        return this.generateTransferResponse(transferCheck.reason);
      }

      // 生成响应
      const response = await this.generateResponse(context, intent, customerMessage);
      
      // 记录机器人响应
      context.messages.push({
        type: 'robot',
        message: response.message,
        timestamp: moment().toISOString(),
        intent: intent.name,
        confidence: response.confidence
      });

      return response;

    } catch (error) {
      console.error(`处理客户响应失败: ${callId}`, error);
      return this.generateErrorResponse();
    }
  }

  /**
   * 创建对话上下文
   */
  createConversationContext(callId) {
    return {
      callId,
      createdAt: moment().toISOString(),
      lastActivity: moment().toISOString(),
      turnCount: 0,
      messages: [],
      customerInfo: {},
      currentStage: 'greeting', // greeting, name_verification, product_intro, inquiry, closing
      lastIntent: null,
      intentHistory: [],
      transferRequested: false,
      metadata: {
        unclearCount: 0,
        inquiryCount: 0,
        complaintCount: 0
      }
    };
  }

  /**
   * 意图识别
   */
  recognizeIntent(message) {
    const normalizedMessage = message.toLowerCase().trim();
    let bestMatch = { name: 'unknown', confidence: 0 };

    for (const [intentName, intentData] of Object.entries(this.intents)) {
      for (const pattern of intentData.patterns) {
        if (normalizedMessage.includes(pattern.toLowerCase())) {
          const confidence = intentData.confidence * this.calculatePatternMatch(normalizedMessage, pattern);
          if (confidence > bestMatch.confidence) {
            bestMatch = {
              name: intentName,
              confidence: confidence,
              pattern: pattern
            };
          }
        }
      }
    }

    return bestMatch;
  }

  /**
   * 计算模式匹配度
   */
  calculatePatternMatch(message, pattern) {
    const patternLength = pattern.length;
    const messageLength = message.length;
    
    // 简单的匹配度计算
    if (message === pattern.toLowerCase()) return 1.0;
    if (message.includes(pattern.toLowerCase())) return 0.8;
    
    return 0.6; // 基础匹配度
  }

  /**
   * 检查转接条件
   */
  checkTransferConditions(context, intent) {
    // 显式转接请求
    if (intent.name === 'request_human' && intent.confidence > 0.8) {
      return {
        needsTransfer: true,
        reason: '客户要求人工服务'
      };
    }

    // 投诉处理
    if (intent.name === 'complaint' && intent.confidence > 0.7) {
      context.metadata.complaintCount++;
      return {
        needsTransfer: true,
        reason: '客户投诉需要人工处理'
      };
    }

    // 连续不清楚
    if (intent.name === 'unclear') {
      context.metadata.unclearCount++;
      if (context.metadata.unclearCount >= 2) {
        return {
          needsTransfer: true,
          reason: '沟通不畅需要人工协助'
        };
      }
    } else {
      context.metadata.unclearCount = 0; // 重置计数
    }

    // 连续咨询
    if (intent.name === 'inquiry') {
      context.metadata.inquiryCount++;
      if (context.metadata.inquiryCount >= 3) {
        return {
          needsTransfer: true,
          reason: '复杂咨询需要专业人员'
        };
      }
    }

    // 对话轮次过多
    if (context.turnCount >= 10) {
      return {
        needsTransfer: true,
        reason: '对话轮次过多，转接人工'
      };
    }

    return { needsTransfer: false };
  }

  /**
   * 生成响应
   */
  async generateResponse(context, intent, customerMessage) {
    const stage = context.currentStage;
    let responseType = intent.name;
    let message = '';
    let confidence = intent.confidence;
    let needsTransfer = false;

    switch (stage) {
      case 'greeting':
        if (intent.name === 'greeting' || intent.name === 'confirmation') {
          message = this.getRandomResponse('greeting');
          context.currentStage = 'name_verification';
        } else {
          message = this.getRandomResponse('greeting');
        }
        break;

      case 'name_verification':
        if (intent.name === 'confirmation') {
          message = this.getRandomResponse('name_confirmation');
          context.currentStage = 'product_intro';
        } else if (intent.name === 'denial') {
          message = this.getRandomResponse('name_denial');
        } else {
          message = '请问您是我们要联系的客户吗？';
        }
        break;

      case 'product_intro':
        if (intent.name === 'inquiry') {
          message = this.getRandomResponse('product_introduction');
        } else if (intent.name === 'confirmation') {
          message = '太好了！我们的专业顾问会为您提供详细的方案，我为您转接。';
          needsTransfer = true;
        } else {
          message = this.getRandomResponse('product_introduction');
        }
        break;

      default:
        message = this.getRandomResponse('default');
        needsTransfer = true;
    }

    // 特殊意图处理
    if (intent.name === 'request_human') {
      message = this.getRandomResponse('transfer_to_human');
      needsTransfer = true;
    } else if (intent.name === 'complaint') {
      message = this.getRandomResponse('complaint_handling');
      needsTransfer = true;
    } else if (intent.name === 'unclear') {
      message = this.getRandomResponse('unclear_response');
    } else if (intent.name === 'goodbye') {
      message = this.getRandomResponse('goodbye');
      context.currentStage = 'closing';
    }

    return {
      message: this.personalizeMessage(message, context),
      type: responseType,
      confidence: confidence,
      needsTransfer: needsTransfer,
      transferReason: needsTransfer ? this.getTransferReason(intent, context) : null,
      stage: context.currentStage
    };
  }

  /**
   * 生成转接响应
   */
  generateTransferResponse(reason) {
    return {
      message: this.getRandomResponse('transfer_to_human'),
      type: 'transfer',
      confidence: 1.0,
      needsTransfer: true,
      transferReason: reason,
      stage: 'transfer'
    };
  }

  /**
   * 生成错误响应
   */
  generateErrorResponse() {
    return {
      message: '抱歉，系统出现了一些问题，我为您转接人工客服。',
      type: 'error',
      confidence: 1.0,
      needsTransfer: true,
      transferReason: '系统错误',
      stage: 'error'
    };
  }

  /**
   * 获取随机响应
   */
  getRandomResponse(type) {
    const responses = this.responses[type];
    if (!responses || responses.length === 0) {
      return this.responses.default[0];
    }
    return responses[Math.floor(Math.random() * responses.length)];
  }

  /**
   * 个性化消息
   */
  personalizeMessage(message, context) {
    // 替换占位符
    let personalizedMessage = message;
    
    if (context.customerInfo.name) {
      personalizedMessage = personalizedMessage.replace('{name}', context.customerInfo.name);
    }
    
    personalizedMessage = personalizedMessage.replace('{product}', '智能解决方案');
    personalizedMessage = personalizedMessage.replace('{benefit}', '提高效率，降低成本');
    personalizedMessage = personalizedMessage.replace('{phone}', context.customerInfo.phone || '');

    return personalizedMessage;
  }

  /**
   * 获取转接原因
   */
  getTransferReason(intent, context) {
    switch (intent.name) {
      case 'request_human':
        return '客户要求人工服务';
      case 'complaint':
        return '客户投诉需要人工处理';
      case 'inquiry':
        return '复杂咨询需要专业人员';
      case 'unclear':
        return '沟通不畅需要人工协助';
      default:
        return '需要人工协助';
    }
  }

  /**
   * 获取对话上下文
   */
  getConversationContext(callId) {
    return this.conversations.get(callId);
  }

  /**
   * 清理对话上下文
   */
  clearConversationContext(callId) {
    this.conversations.delete(callId);
    console.log(`清理对话上下文: ${callId}`);
  }

  /**
   * 获取对话统计
   */
  getConversationStatistics(callId) {
    const context = this.conversations.get(callId);
    if (!context) return null;

    const robotMessages = context.messages.filter(msg => msg.type === 'robot');
    const customerMessages = context.messages.filter(msg => msg.type === 'customer');

    return {
      callId,
      turnCount: context.turnCount,
      duration: moment().diff(moment(context.createdAt), 'seconds'),
      robotMessageCount: robotMessages.length,
      customerMessageCount: customerMessages.length,
      currentStage: context.currentStage,
      lastIntent: context.lastIntent,
      transferRequested: context.transferRequested,
      averageConfidence: this.calculateAverageConfidence(robotMessages)
    };
  }

  /**
   * 计算平均置信度
   */
  calculateAverageConfidence(messages) {
    if (messages.length === 0) return 0;
    
    const totalConfidence = messages.reduce((sum, msg) => sum + (msg.confidence || 0), 0);
    return Math.round((totalConfidence / messages.length) * 100) / 100;
  }

  /**
   * 获取所有活跃对话
   */
  getActiveConversations() {
    return Array.from(this.conversations.values());
  }

  /**
   * 更新客户信息
   */
  updateCustomerInfo(callId, customerInfo) {
    const context = this.conversations.get(callId);
    if (context) {
      context.customerInfo = { ...context.customerInfo, ...customerInfo };
      console.log(`更新客户信息: ${callId}`, customerInfo);
    }
  }

  /**
   * 添加自定义响应模板
   */
  addResponseTemplate(type, templates) {
    if (!this.responses[type]) {
      this.responses[type] = [];
    }
    this.responses[type] = this.responses[type].concat(templates);
    console.log(`添加响应模板: ${type}`, templates);
  }

  /**
   * 添加意图识别规则
   */
  addIntentRule(intentName, patterns, confidence = 0.7) {
    this.intents[intentName] = {
      patterns: patterns,
      confidence: confidence
    };
    console.log(`添加意图规则: ${intentName}`, patterns);
  }
}

module.exports = RobotDialogue;