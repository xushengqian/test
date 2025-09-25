const express = require('express');
const router = express.Router();
const callManager = require('../services/callManager');
const agentManager = require('../services/agentManager');
const transcriptionService = require('../services/transcription');
const twilio = require('twilio');

// Middleware to validate agent session
const validateAgent = (req, res, next) => {
  const token = req.headers.authorization?.replace('Bearer ', '');
  if (!token) {
    return res.status(401).json({ error: 'No authorization token' });
  }

  const session = agentManager.validateSession(token);
  if (!session) {
    return res.status(401).json({ error: 'Invalid or expired session' });
  }

  req.agentId = session.agentId;
  next();
};

// Initiate a new outbound call
router.post('/initiate', async (req, res) => {
  try {
    const { phoneNumber, script, metadata } = req.body;

    if (!phoneNumber) {
      return res.status(400).json({ error: 'Phone number is required' });
    }

    const call = await callManager.initiateCall(phoneNumber, script || {}, metadata);
    
    res.json({
      success: true,
      callId: call.id,
      status: call.status
    });
  } catch (error) {
    console.error('Failed to initiate call:', error);
    res.status(500).json({ error: error.message });
  }
});

// Get all active calls
router.get('/active', validateAgent, async (req, res) => {
  try {
    const calls = await callManager.getActiveCalls();
    res.json(calls);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Get call details
router.get('/:callId', validateAgent, async (req, res) => {
  try {
    const call = await callManager.getCallDetails(req.params.callId);
    if (!call) {
      return res.status(404).json({ error: 'Call not found' });
    }
    res.json(call);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Agent takes over call
router.post('/:callId/takeover', validateAgent, async (req, res) => {
  try {
    const { callId } = req.params;
    const agentId = req.agentId;

    await agentManager.assignCallToAgent(callId, agentId);
    const call = await callManager.transferToAgent(callId, agentId);
    
    res.json({
      success: true,
      call
    });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// End call
router.post('/:callId/end', validateAgent, async (req, res) => {
  try {
    const call = await callManager.endCall(req.params.callId);
    
    // Release from agent if assigned
    if (call.agentId) {
      await agentManager.releaseCallFromAgent(call.id, call.agentId);
    }
    
    res.json({
      success: true,
      call
    });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Send message in call
router.post('/:callId/message', validateAgent, async (req, res) => {
  try {
    const { message } = req.body;
    const call = await callManager.sendMessage(req.params.callId, message, true);
    
    res.json({
      success: true,
      call
    });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// TwiML endpoint for call handling
router.post('/twiml/:callId', (req, res) => {
  const { callId } = req.params;
  const twiml = new twilio.twiml.VoiceResponse();

  // Initial greeting
  twiml.say({
    voice: 'alice',
    language: 'zh-CN'
  }, '您好，欢迎致电我们的客服中心。');

  // Start recording and streaming
  twiml.record({
    timeout: 0,
    transcribe: false,
    playBeep: false
  });

  // Keep the call open
  twiml.pause({ length: 3600 });

  res.type('text/xml');
  res.send(twiml.toString());
});

// Status callback endpoint
router.post('/status/:callId', async (req, res) => {
  const { callId } = req.params;
  const { CallStatus, CallDuration } = req.body;

  console.log(`Call ${callId} status: ${CallStatus}, duration: ${CallDuration}`);

  // Update call status in our system
  if (CallStatus === 'completed' || CallStatus === 'failed' || CallStatus === 'busy' || CallStatus === 'no-answer') {
    try {
      await callManager.endCall(callId);
    } catch (error) {
      console.error('Error ending call:', error);
    }
  }

  res.sendStatus(200);
});

// Recording callback endpoint
router.post('/recording/:callId', (req, res) => {
  const { callId } = req.params;
  const { RecordingUrl, RecordingSid } = req.body;

  console.log(`Recording for call ${callId}: ${RecordingUrl}`);
  
  // Store recording information
  // You would save this to database in production
  
  res.sendStatus(200);
});

// WebSocket endpoint for media streams (for real-time transcription)
router.ws('/stream/:callId', (ws, req) => {
  const { callId } = req.params;
  console.log(`Media stream connected for call ${callId}`);

  ws.on('message', (message) => {
    const msg = JSON.parse(message);
    
    if (msg.event === 'media') {
      // Process audio data for transcription
      transcriptionService.processAudioStream(callId, msg.media.payload);
    } else if (msg.event === 'start') {
      console.log(`Stream started for call ${callId}`);
      transcriptionService.startTranscription(callId);
    } else if (msg.event === 'stop') {
      console.log(`Stream stopped for call ${callId}`);
      transcriptionService.stopTranscription(callId);
    }
  });

  ws.on('close', () => {
    console.log(`Media stream disconnected for call ${callId}`);
    transcriptionService.stopTranscription(callId);
  });
});

// Agent authentication
router.post('/agent/login', async (req, res) => {
  try {
    const { agentId, password } = req.body;
    
    // In production, validate password against database
    // For demo, accept any password
    const agent = await agentManager.authenticateAgent(agentId, password);
    
    res.json({
      success: true,
      agent,
      token: agent.sessionToken
    });
  } catch (error) {
    res.status(401).json({ error: error.message });
  }
});

// Agent logout
router.post('/agent/logout', validateAgent, async (req, res) => {
  try {
    await agentManager.logoutAgent(req.agentId);
    res.json({ success: true });
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Get agent stats
router.get('/agent/stats', validateAgent, async (req, res) => {
  try {
    const stats = await agentManager.getAgentStats(req.agentId);
    res.json(stats);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

// Get all agents stats (for supervisor)
router.get('/agents/stats', validateAgent, async (req, res) => {
  try {
    const stats = await agentManager.getAllAgentsStats();
    res.json(stats);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

module.exports = router;