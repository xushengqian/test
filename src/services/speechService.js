const axios = require('axios');
const crypto = require('crypto');
const WebSocket = require('ws');
const winston = require('winston');
const EventEmitter = require('events');

// 配置日志
const logger = winston.createLogger({
  level: 'info',
  format: winston.format.json(),
  transports: [
    new winston.transports.File({ filename: 'speech-service.log' }),
    new winston.transports.Console()
  ]
});

// 阿里云语音服务
class AliSpeechService extends EventEmitter {
  constructor(config) {
    super();
    this.accessKeyId = config.accessKeyId;
    this.accessKeySecret = config.accessKeySecret;
    this.appKey = config.appKey;
    this.ttsAppKey = config.ttsAppKey;
    this.asrConnections = new Map();
  }

  // 生成Token
  async getToken() {
    const url = 'https://nls-meta.cn-shanghai.aliyuncs.com/';
    const params = {
      Action: 'CreateToken',
      AccessKeyId: this.accessKeyId,
      Format: 'JSON',
      RegionId: 'cn-shanghai',
      SignatureMethod: 'HMAC-SHA1',
      SignatureNonce: Math.random().toString(),
      SignatureVersion: '1.0',
      Timestamp: new Date().toISOString(),
      Version: '2019-02-28'
    };

    // 生成签名
    const signature = this.generateSignature(params, 'POST');
    params.Signature = signature;

    try {
      const response = await axios.post(url, null, { params });
      return response.data.Token;
    } catch (error) {
      logger.error('Failed to get Ali token:', error);
      throw error;
    }
  }

  // 生成签名
  generateSignature(params, method) {
    const sortedParams = Object.keys(params)
      .sort()
      .map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
      .join('&');

    const stringToSign = `${method}&${encodeURIComponent('/')}&${encodeURIComponent(sortedParams)}`;
    
    const hmac = crypto.createHmac('sha1', `${this.accessKeySecret}&`);
    hmac.update(stringToSign);
    return hmac.digest('base64');
  }

  // 开始实时语音识别
  async startRealtimeASR(callUuid, audioStream) {
    try {
      const token = await this.getToken();
      const wsUrl = `wss://nls-gateway.cn-shanghai.aliyuncs.com/ws/v1?token=${token}`;
      
      const ws = new WebSocket(wsUrl);
      const connection = {
        ws,
        callUuid,
        isConnected: false,
        taskId: null
      };

      this.asrConnections.set(callUuid, connection);

      ws.on('open', () => {
        logger.info(`ASR WebSocket connected for call ${callUuid}`);
        connection.isConnected = true;
        
        // 发送开始识别命令
        const startMessage = {
          header: {
            message_id: this.generateMessageId(),
            task_id: this.generateTaskId(),
            namespace: 'SpeechRecognizer',
            name: 'StartRecognition',
            appkey: this.appKey
          },
          payload: {
            format: 'pcm',
            sample_rate: 8000,
            enable_intermediate_result: true,
            enable_punctuation_prediction: true,
            enable_inverse_text_normalization: true
          }
        };
        
        connection.taskId = startMessage.header.task_id;
        ws.send(JSON.stringify(startMessage));
      });

      ws.on('message', (data) => {
        const message = JSON.parse(data);
        this.handleASRMessage(callUuid, message);
      });

      ws.on('error', (error) => {
        logger.error(`ASR WebSocket error for call ${callUuid}:`, error);
        this.emit('asr.error', { callUuid, error });
      });

      ws.on('close', () => {
        logger.info(`ASR WebSocket closed for call ${callUuid}`);
        connection.isConnected = false;
        this.asrConnections.delete(callUuid);
      });

      // 处理音频流
      if (audioStream) {
        audioStream.on('data', (chunk) => {
          if (connection.isConnected) {
            ws.send(chunk);
          }
        });

        audioStream.on('end', () => {
          this.stopRealtimeASR(callUuid);
        });
      }

      return connection;
    } catch (error) {
      logger.error(`Failed to start ASR for call ${callUuid}:`, error);
      throw error;
    }
  }

  // 处理ASR消息
  handleASRMessage(callUuid, message) {
    const { header, payload } = message;
    
    switch (header.name) {
      case 'RecognitionStarted':
        logger.info(`ASR started for call ${callUuid}`);
        this.emit('asr.started', { callUuid });
        break;
        
      case 'RecognitionResultChanged':
        // 中间识别结果
        if (payload && payload.result) {
          this.emit('asr.partial', {
            callUuid,
            text: payload.result,
            timestamp: new Date()
          });
        }
        break;
        
      case 'RecognitionCompleted':
        // 最终识别结果
        if (payload && payload.result) {
          logger.info(`ASR result for ${callUuid}: ${payload.result}`);
          this.emit('asr.final', {
            callUuid,
            text: payload.result,
            timestamp: new Date()
          });
        }
        break;
        
      case 'RecognitionError':
        logger.error(`ASR error for ${callUuid}:`, payload);
        this.emit('asr.error', { callUuid, error: payload });
        break;
    }
  }

  // 停止实时语音识别
  stopRealtimeASR(callUuid) {
    const connection = this.asrConnections.get(callUuid);
    if (!connection) return;

    if (connection.isConnected && connection.ws) {
      // 发送停止识别命令
      const stopMessage = {
        header: {
          message_id: this.generateMessageId(),
          task_id: connection.taskId,
          namespace: 'SpeechRecognizer',
          name: 'StopRecognition',
          appkey: this.appKey
        }
      };
      
      connection.ws.send(JSON.stringify(stopMessage));
      connection.ws.close();
    }
    
    this.asrConnections.delete(callUuid);
    logger.info(`ASR stopped for call ${callUuid}`);
  }

  // 文本转语音
  async textToSpeech(text, options = {}) {
    try {
      const token = await this.getToken();
      const url = 'https://nls-gateway.cn-shanghai.aliyuncs.com/stream/v1/tts';
      
      const params = {
        appkey: this.ttsAppKey,
        token,
        text,
        format: options.format || 'wav',
        sample_rate: options.sampleRate || 8000,
        voice: options.voice || 'xiaoyun',
        volume: options.volume || 50,
        speech_rate: options.speechRate || 0,
        pitch_rate: options.pitchRate || 0
      };

      const response = await axios.post(url, null, {
        params,
        responseType: 'stream'
      });

      return response.data;
    } catch (error) {
      logger.error('TTS failed:', error);
      throw error;
    }
  }

  // 生成消息ID
  generateMessageId() {
    return `msg_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  // 生成任务ID
  generateTaskId() {
    return `task_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }
}

// 腾讯云语音服务（备选方案）
class TencentSpeechService extends EventEmitter {
  constructor(config) {
    super();
    this.secretId = config.secretId;
    this.secretKey = config.secretKey;
    this.region = config.region || 'ap-shanghai';
  }

  // 实时语音识别
  async startRealtimeASR(callUuid, audioStream) {
    // 腾讯云ASR实现
    const wsUrl = `wss://asr.cloud.tencent.com/asr/v2/${this.generateRequestId()}`;
    
    const ws = new WebSocket(wsUrl, {
      headers: {
        'Authorization': this.generateAuth(),
        'Content-Type': 'application/json'
      }
    });

    const connection = {
      ws,
      callUuid,
      isConnected: false
    };

    ws.on('open', () => {
      logger.info(`Tencent ASR connected for call ${callUuid}`);
      connection.isConnected = true;
      
      // 发送配置
      ws.send(JSON.stringify({
        type: 'start',
        config: {
          engine_model_type: '8k_zh',
          voice_format: 1, // PCM
          needvad: 1,
          filter_dirty: 1,
          filter_modal: 1,
          filter_punc: 0,
          convert_num_mode: 1
        }
      }));
    });

    ws.on('message', (data) => {
      const message = JSON.parse(data);
      if (message.result) {
        this.emit('asr.result', {
          callUuid,
          text: message.result.voice_text_str,
          isFinal: message.result.slice_type === 2
        });
      }
    });

    ws.on('error', (error) => {
      logger.error(`Tencent ASR error:`, error);
      this.emit('asr.error', { callUuid, error });
    });

    ws.on('close', () => {
      connection.isConnected = false;
    });

    // 处理音频流
    if (audioStream) {
      audioStream.on('data', (chunk) => {
        if (connection.isConnected) {
          ws.send(chunk);
        }
      });
    }

    return connection;
  }

  // 生成认证信息
  generateAuth() {
    const timestamp = Math.floor(Date.now() / 1000);
    const nonce = Math.random().toString(36).substr(2);
    
    const signStr = `POST\nasr.cloud.tencent.com\n/\n${timestamp}\n${nonce}`;
    const signature = crypto
      .createHmac('sha256', this.secretKey)
      .update(signStr)
      .digest('base64');
    
    return `TC3-HMAC-SHA256 Credential=${this.secretId}, SignedHeaders=content-type;host, Signature=${signature}`;
  }

  // 生成请求ID
  generateRequestId() {
    return `req_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
  }

  // 文本转语音
  async textToSpeech(text, options = {}) {
    const url = 'https://tts.cloud.tencent.com/stream';
    
    const params = {
      Text: text,
      SessionId: this.generateRequestId(),
      ModelType: options.modelType || 1,
      VoiceType: options.voiceType || 0,
      Codec: options.codec || 'wav',
      SampleRate: options.sampleRate || 8000,
      Speed: options.speed || 0,
      Volume: options.volume || 5
    };

    const headers = {
      'Authorization': this.generateAuth(),
      'Content-Type': 'application/json'
    };

    try {
      const response = await axios.post(url, params, {
        headers,
        responseType: 'stream'
      });
      
      return response.data;
    } catch (error) {
      logger.error('Tencent TTS failed:', error);
      throw error;
    }
  }
}

// 统一的语音服务接口
class SpeechService extends EventEmitter {
  constructor(provider = 'ali', config) {
    super();
    this.provider = provider;
    
    if (provider === 'ali') {
      this.service = new AliSpeechService(config);
    } else if (provider === 'tencent') {
      this.service = new TencentSpeechService(config);
    } else {
      throw new Error(`Unsupported speech service provider: ${provider}`);
    }

    // 转发事件
    this.service.on('asr.started', (data) => this.emit('asr.started', data));
    this.service.on('asr.partial', (data) => this.emit('asr.partial', data));
    this.service.on('asr.final', (data) => this.emit('asr.final', data));
    this.service.on('asr.result', (data) => this.emit('asr.result', data));
    this.service.on('asr.error', (data) => this.emit('asr.error', data));
  }

  // 开始实时语音识别
  startRealtimeASR(callUuid, audioStream) {
    return this.service.startRealtimeASR(callUuid, audioStream);
  }

  // 停止实时语音识别
  stopRealtimeASR(callUuid) {
    if (this.service.stopRealtimeASR) {
      return this.service.stopRealtimeASR(callUuid);
    }
  }

  // 文本转语音
  textToSpeech(text, options = {}) {
    return this.service.textToSpeech(text, options);
  }

  // 批量TTS（预生成语音文件）
  async batchTTS(texts, outputDir) {
    const fs = require('fs').promises;
    const path = require('path');
    const results = [];

    for (let i = 0; i < texts.length; i++) {
      const text = texts[i];
      const filename = `tts_${Date.now()}_${i}.wav`;
      const filepath = path.join(outputDir, filename);

      try {
        const audioStream = await this.textToSpeech(text);
        const writeStream = require('fs').createWriteStream(filepath);
        
        await new Promise((resolve, reject) => {
          audioStream.pipe(writeStream);
          writeStream.on('finish', resolve);
          writeStream.on('error', reject);
        });

        results.push({
          text,
          file: filepath,
          success: true
        });
        
        logger.info(`TTS generated: ${filename}`);
      } catch (error) {
        logger.error(`TTS failed for text ${i}:`, error);
        results.push({
          text,
          error: error.message,
          success: false
        });
      }
    }

    return results;
  }
}

module.exports = SpeechService;