const { v4: uuidv4 } = require('uuid');
const twilioService = require('./twilioService');
const transcriptionService = require('./transcription');
const Redis = require('redis');

class CallManager {
  constructor() {
    this.activeCalls = new Map();
    this.io = null;
    this.redis = null;
  }

  async initialize(io) {
    this.io = io;
    
    // Initialize Redis for distributed state management
    if (process.env.REDIS_HOST) {
      this.redis = Redis.createClient({
        host: process.env.REDIS_HOST,
        port: process.env.REDIS_PORT,
        password: process.env.REDIS_PASSWORD
      });
      
      await this.redis.connect();
      console.log('Redis connected for call state management');
    }
  }

  async initiateCall(phoneNumber, script, metadata = {}) {
    const callId = uuidv4();
    
    const callData = {
      id: callId,
      phoneNumber,
      script,
      metadata,
      status: 'initiating',
      startTime: new Date(),
      transcript: [],
      isAgentConnected: false,
      agentId: null,
      duration: 0,
      recordings: []
    };

    this.activeCalls.set(callId, callData);
    
    // Store in Redis if available
    if (this.redis) {
      await this.redis.set(`call:${callId}`, JSON.stringify(callData), 'EX', 3600);
    }

    try {
      // Initiate call through Twilio
      const twilioCall = await twilioService.makeCall(phoneNumber, callId);
      
      callData.twilioCallSid = twilioCall.sid;
      callData.status = 'connecting';
      
      // Emit event to all connected clients
      this.io.emit('call:initiated', {
        callId,
        phoneNumber: this.maskPhoneNumber(phoneNumber),
        status: 'connecting',
        timestamp: new Date()
      });

      // Start transcription
      this.startTranscription(callId);

      return callData;
    } catch (error) {
      console.error('Failed to initiate call:', error);
      callData.status = 'failed';
      callData.error = error.message;
      
      this.io.emit('call:failed', {
        callId,
        error: error.message
      });
      
      throw error;
    }
  }

  async startTranscription(callId) {
    const call = this.activeCalls.get(callId);
    if (!call) return;

    // Set up real-time transcription
    transcriptionService.startTranscription(callId, (transcription) => {
      // Add to transcript
      call.transcript.push({
        text: transcription.text,
        speaker: transcription.speaker,
        timestamp: new Date(),
        confidence: transcription.confidence
      });

      // Emit to all clients monitoring this call
      this.io.to(`call:${callId}`).emit('call:transcription', {
        callId,
        transcription: {
          text: transcription.text,
          speaker: transcription.speaker,
          timestamp: new Date(),
          confidence: transcription.confidence
        }
      });

      // Process AI response if it's from customer and no agent connected
      if (transcription.speaker === 'customer' && !call.isAgentConnected) {
        this.processAIResponse(callId, transcription.text);
      }
    });
  }

  async processAIResponse(callId, customerText) {
    const call = this.activeCalls.get(callId);
    if (!call || call.isAgentConnected) return;

    try {
      // Generate AI response based on script and context
      const response = await this.generateAIResponse(call.script, call.transcript, customerText);
      
      // Send response through call
      await twilioService.sayInCall(call.twilioCallSid, response);
      
      // Add to transcript
      call.transcript.push({
        text: response,
        speaker: 'ai',
        timestamp: new Date()
      });

      // Emit AI response
      this.io.to(`call:${callId}`).emit('call:ai_response', {
        callId,
        response,
        timestamp: new Date()
      });
    } catch (error) {
      console.error('Failed to process AI response:', error);
    }
  }

  async generateAIResponse(script, transcript, customerText) {
    // This would integrate with an AI service like OpenAI
    // For now, return a simple scripted response
    
    const lowerText = customerText.toLowerCase();
    
    if (lowerText.includes('hello') || lowerText.includes('hi')) {
      return script.greeting || '您好，我是智能客服助手，有什么可以帮助您的吗？';
    }
    
    if (lowerText.includes('bye') || lowerText.includes('goodbye')) {
      return script.farewell || '感谢您的来电，祝您生活愉快，再见！';
    }
    
    // Default response
    return script.default || '我正在为您查询相关信息，请稍等片刻。';
  }

  async transferToAgent(callId, agentId) {
    const call = this.activeCalls.get(callId);
    if (!call) {
      throw new Error('Call not found');
    }

    if (call.isAgentConnected) {
      throw new Error('Agent already connected to this call');
    }

    // Update call data
    call.isAgentConnected = true;
    call.agentId = agentId;
    call.agentConnectedTime = new Date();

    // Bridge agent to the call
    await twilioService.bridgeAgentToCall(call.twilioCallSid, agentId);

    // Notify all clients
    this.io.emit('call:agent_connected', {
      callId,
      agentId,
      timestamp: new Date()
    });

    // Update Redis if available
    if (this.redis) {
      await this.redis.set(`call:${callId}`, JSON.stringify(call), 'EX', 3600);
    }

    return call;
  }

  async sendMessage(callId, message, isAgent = false) {
    const call = this.activeCalls.get(callId);
    if (!call) {
      throw new Error('Call not found');
    }

    // Add to transcript
    call.transcript.push({
      text: message,
      speaker: isAgent ? 'agent' : 'system',
      timestamp: new Date()
    });

    // If agent message, send through call
    if (isAgent && call.isAgentConnected) {
      await twilioService.sayInCall(call.twilioCallSid, message);
    }

    return call;
  }

  async endCall(callId) {
    const call = this.activeCalls.get(callId);
    if (!call) {
      throw new Error('Call not found');
    }

    call.status = 'ended';
    call.endTime = new Date();
    call.duration = Math.floor((call.endTime - call.startTime) / 1000);

    // End Twilio call
    if (call.twilioCallSid) {
      await twilioService.endCall(call.twilioCallSid);
    }

    // Stop transcription
    transcriptionService.stopTranscription(callId);

    // Save call record for history
    await this.saveCallRecord(call);

    // Remove from active calls
    this.activeCalls.delete(callId);

    // Remove from Redis if available
    if (this.redis) {
      await this.redis.del(`call:${callId}`);
    }

    return call;
  }

  async saveCallRecord(call) {
    // Save to database or file system
    // This is a placeholder for actual implementation
    console.log('Saving call record:', call.id);
  }

  async getActiveCalls() {
    return Array.from(this.activeCalls.values()).map(call => ({
      id: call.id,
      phoneNumber: this.maskPhoneNumber(call.phoneNumber),
      status: call.status,
      startTime: call.startTime,
      duration: this.calculateDuration(call.startTime),
      isAgentConnected: call.isAgentConnected,
      agentId: call.agentId
    }));
  }

  async getCallDetails(callId) {
    const call = this.activeCalls.get(callId);
    if (!call) {
      // Try to get from Redis
      if (this.redis) {
        const redisCall = await this.redis.get(`call:${callId}`);
        if (redisCall) {
          return JSON.parse(redisCall);
        }
      }
      return null;
    }
    return call;
  }

  maskPhoneNumber(phoneNumber) {
    // Mask phone number for privacy
    if (!phoneNumber) return '';
    const cleaned = phoneNumber.replace(/\D/g, '');
    if (cleaned.length >= 7) {
      return cleaned.slice(0, 3) + '****' + cleaned.slice(-4);
    }
    return phoneNumber;
  }

  calculateDuration(startTime) {
    const now = new Date();
    const start = new Date(startTime);
    return Math.floor((now - start) / 1000);
  }
}

module.exports = new CallManager();