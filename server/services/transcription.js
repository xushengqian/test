const speech = require('@google-cloud/speech');
const EventEmitter = require('events');

class TranscriptionService extends EventEmitter {
  constructor() {
    super();
    this.client = null;
    this.activeStreams = new Map();
    this.io = null;
    this.initialize();
  }

  initialize(io = null) {
    if (io) {
      this.io = io;
    }

    // Initialize Google Cloud Speech client if credentials are available
    if (process.env.GOOGLE_APPLICATION_CREDENTIALS) {
      this.client = new speech.SpeechClient();
      console.log('Google Cloud Speech client initialized');
    } else {
      console.warn('Google Cloud Speech credentials not configured');
      // Use mock transcription for development
      this.useMockTranscription = true;
    }
  }

  async startTranscription(callId, callback) {
    if (this.activeStreams.has(callId)) {
      console.log(`Transcription already active for call ${callId}`);
      return;
    }

    if (this.useMockTranscription) {
      this.startMockTranscription(callId, callback);
      return;
    }

    const request = {
      config: {
        encoding: 'MULAW',
        sampleRateHertz: 8000,
        languageCode: 'zh-CN',
        alternativeLanguageCodes: ['en-US'],
        enableAutomaticPunctuation: true,
        enableSpeakerDiarization: true,
        diarizationSpeakerCount: 2,
        model: 'phone_call',
        useEnhanced: true
      },
      interimResults: true
    };

    const recognizeStream = this.client
      .streamingRecognize(request)
      .on('error', (error) => {
        console.error(`Transcription error for call ${callId}:`, error);
        this.handleTranscriptionError(callId, error);
      })
      .on('data', (data) => {
        this.processTranscriptionData(callId, data, callback);
      });

    this.activeStreams.set(callId, {
      stream: recognizeStream,
      startTime: Date.now(),
      callback
    });

    console.log(`Started transcription for call ${callId}`);
  }

  processTranscriptionData(callId, data, callback) {
    if (!data.results || data.results.length === 0) return;

    data.results.forEach(result => {
      if (result.alternatives && result.alternatives.length > 0) {
        const alternative = result.alternatives[0];
        const transcription = {
          text: alternative.transcript,
          confidence: alternative.confidence || 0,
          isFinal: result.isFinal,
          speaker: this.detectSpeaker(result),
          timestamp: new Date()
        };

        // Call the callback with transcription
        if (callback) {
          callback(transcription);
        }

        // Emit event
        this.emit('transcription', {
          callId,
          transcription
        });

        // Send to WebSocket clients if io is available
        if (this.io) {
          this.io.to(`call:${callId}`).emit('transcription:update', {
            callId,
            transcription
          });
        }
      }
    });
  }

  detectSpeaker(result) {
    // If speaker diarization is enabled, use it
    if (result.speakerTag) {
      return result.speakerTag === 1 ? 'customer' : 'agent';
    }
    
    // Default to customer for now
    return 'customer';
  }

  startMockTranscription(callId, callback) {
    // Mock transcription for development/testing
    const mockMessages = [
      { text: '您好，请问有什么可以帮助您的吗？', speaker: 'ai', delay: 1000 },
      { text: '我想查询一下我的订单状态', speaker: 'customer', delay: 3000 },
      { text: '好的，请问您的订单号是多少？', speaker: 'ai', delay: 5000 },
      { text: '订单号是 2024123456', speaker: 'customer', delay: 7000 },
      { text: '正在为您查询订单信息，请稍等...', speaker: 'ai', delay: 9000 },
      { text: '您的订单已经发货，预计明天送达', speaker: 'ai', delay: 11000 },
      { text: '好的，谢谢', speaker: 'customer', delay: 13000 },
      { text: '不客气，还有其他需要帮助的吗？', speaker: 'ai', delay: 15000 }
    ];

    const streamData = {
      intervalId: null,
      messageIndex: 0
    };

    streamData.intervalId = setInterval(() => {
      if (streamData.messageIndex < mockMessages.length) {
        const message = mockMessages[streamData.messageIndex];
        
        setTimeout(() => {
          const transcription = {
            text: message.text,
            speaker: message.speaker,
            confidence: 0.95,
            isFinal: true,
            timestamp: new Date()
          };

          if (callback) {
            callback(transcription);
          }

          this.emit('transcription', {
            callId,
            transcription
          });

          if (this.io) {
            this.io.to(`call:${callId}`).emit('transcription:update', {
              callId,
              transcription
            });
          }
        }, message.delay);

        streamData.messageIndex++;
      } else {
        clearInterval(streamData.intervalId);
      }
    }, 1000);

    this.activeStreams.set(callId, streamData);
    console.log(`Started mock transcription for call ${callId}`);
  }

  stopTranscription(callId) {
    const streamData = this.activeStreams.get(callId);
    if (!streamData) {
      console.log(`No active transcription for call ${callId}`);
      return;
    }

    if (this.useMockTranscription) {
      if (streamData.intervalId) {
        clearInterval(streamData.intervalId);
      }
    } else {
      if (streamData.stream) {
        streamData.stream.end();
      }
    }

    this.activeStreams.delete(callId);
    console.log(`Stopped transcription for call ${callId}`);
  }

  handleTranscriptionError(callId, error) {
    console.error(`Transcription error for call ${callId}:`, error);
    
    // Emit error event
    this.emit('error', {
      callId,
      error: error.message
    });

    // Notify WebSocket clients
    if (this.io) {
      this.io.to(`call:${callId}`).emit('transcription:error', {
        callId,
        error: error.message
      });
    }

    // Try to restart the stream
    setTimeout(() => {
      const streamData = this.activeStreams.get(callId);
      if (streamData && streamData.callback) {
        this.stopTranscription(callId);
        this.startTranscription(callId, streamData.callback);
      }
    }, 1000);
  }

  // Process audio stream from Twilio Media Streams
  processAudioStream(callId, audioData) {
    const streamData = this.activeStreams.get(callId);
    if (!streamData || !streamData.stream) {
      console.log(`No active stream for call ${callId}`);
      return;
    }

    // Convert Twilio's mulaw audio to the format expected by Google Speech
    const audioBuffer = Buffer.from(audioData, 'base64');
    streamData.stream.write(audioBuffer);
  }
}

module.exports = new TranscriptionService();