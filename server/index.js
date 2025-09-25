require('dotenv').config();
const express = require('express');
const http = require('http');
const socketIo = require('socket.io');
const cors = require('cors');
const path = require('path');

// Import routes and services
const callRouter = require('./routes/calls');
const transcriptionService = require('./services/transcription');
const callManager = require('./services/callManager');
const agentManager = require('./services/agentManager');

const app = express();
const server = http.createServer(app);
const io = socketIo(server, {
  cors: {
    origin: process.env.CLIENT_URL || "http://localhost:5173",
    methods: ["GET", "POST"]
  }
});

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Serve static files in production
if (process.env.NODE_ENV === 'production') {
  app.use(express.static(path.join(__dirname, '../client/dist')));
}

// API Routes
app.use('/api/calls', callRouter);

// WebSocket connection handling
io.on('connection', (socket) => {
  console.log('New client connected:', socket.id);

  // Agent authentication
  socket.on('agent:authenticate', async (data) => {
    const { agentId, token } = data;
    try {
      const agent = await agentManager.authenticateAgent(agentId, token);
      socket.join(`agent:${agentId}`);
      socket.emit('agent:authenticated', { agent });
      
      // Send current active calls
      const activeCalls = await callManager.getActiveCalls();
      socket.emit('calls:active', activeCalls);
    } catch (error) {
      socket.emit('error', { message: 'Authentication failed' });
    }
  });

  // Monitor specific call
  socket.on('call:monitor', async (callId) => {
    socket.join(`call:${callId}`);
    const callDetails = await callManager.getCallDetails(callId);
    socket.emit('call:details', callDetails);
  });

  // Agent takes over call
  socket.on('call:takeover', async (data) => {
    const { callId, agentId } = data;
    try {
      await callManager.transferToAgent(callId, agentId);
      socket.emit('call:takeover:success', { callId });
      
      // Notify all clients monitoring this call
      io.to(`call:${callId}`).emit('call:agent_joined', { 
        callId, 
        agentId,
        timestamp: new Date()
      });
    } catch (error) {
      socket.emit('error', { message: 'Failed to take over call', error: error.message });
    }
  });

  // Send message in call
  socket.on('call:send_message', async (data) => {
    const { callId, message, isAgent } = data;
    try {
      await callManager.sendMessage(callId, message, isAgent);
      
      // Broadcast to all monitoring this call
      io.to(`call:${callId}`).emit('call:message', {
        callId,
        message,
        isAgent,
        timestamp: new Date()
      });
    } catch (error) {
      socket.emit('error', { message: 'Failed to send message' });
    }
  });

  // End call
  socket.on('call:end', async (callId) => {
    try {
      await callManager.endCall(callId);
      io.to(`call:${callId}`).emit('call:ended', { callId });
    } catch (error) {
      socket.emit('error', { message: 'Failed to end call' });
    }
  });

  socket.on('disconnect', () => {
    console.log('Client disconnected:', socket.id);
  });
});

// Export io for use in other modules
app.set('io', io);

// Start server
const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`Server running on port ${PORT}`);
  
  // Initialize services
  callManager.initialize(io);
  transcriptionService.initialize(io);
});