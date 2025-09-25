const express = require('express');
const http = require('http');
const socketIo = require('socket.io');
const cors = require('cors');
const mongoose = require('mongoose');
require('dotenv').config();

const app = express();
const server = http.createServer(app);
const io = socketIo(server, {
  cors: {
    origin: "http://localhost:3000",
    methods: ["GET", "POST"]
  }
});

// Middleware
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// MongoDB Connection
mongoose.connect(process.env.MONGODB_URI || 'mongodb://localhost:27017/robot_call_system', {
  useNewUrlParser: true,
  useUnifiedTopology: true
}).then(() => {
  console.log('✅ MongoDB connected successfully');
}).catch(err => {
  console.error('❌ MongoDB connection error:', err);
});

// Import routes
const authRoutes = require('./routes/auth');
const callRoutes = require('./routes/calls');
const agentRoutes = require('./routes/agents');
const customerRoutes = require('./routes/customers');
const campaignRoutes = require('./routes/campaigns');

// API Routes
app.use('/api/auth', authRoutes);
app.use('/api/calls', callRoutes);
app.use('/api/agents', agentRoutes);
app.use('/api/customers', customerRoutes);
app.use('/api/campaigns', campaignRoutes);

// Socket.io for real-time communication
const CallManager = require('./services/CallManager');
const callManager = new CallManager(io);

io.on('connection', (socket) => {
  console.log('New client connected:', socket.id);

  // Agent login
  socket.on('agent:login', (agentData) => {
    socket.join(`agent-${agentData.agentId}`);
    socket.agentId = agentData.agentId;
    console.log(`Agent ${agentData.agentId} logged in`);
    
    // Send current call queue to agent
    socket.emit('queue:update', callManager.getCallQueue());
  });

  // Agent requests to intervene in a call
  socket.on('agent:intervene', (data) => {
    const { callId, agentId } = data;
    callManager.agentIntervene(callId, agentId);
  });

  // Agent takes over the call completely
  socket.on('agent:takeover', (data) => {
    const { callId, agentId } = data;
    callManager.agentTakeover(callId, agentId);
  });

  // Agent releases call back to robot
  socket.on('agent:release', (data) => {
    const { callId, agentId } = data;
    callManager.releaseToRobot(callId, agentId);
  });

  // Handle WebRTC signaling
  socket.on('webrtc:offer', (data) => {
    socket.to(data.to).emit('webrtc:offer', {
      from: socket.id,
      offer: data.offer
    });
  });

  socket.on('webrtc:answer', (data) => {
    socket.to(data.to).emit('webrtc:answer', {
      from: socket.id,
      answer: data.answer
    });
  });

  socket.on('webrtc:ice-candidate', (data) => {
    socket.to(data.to).emit('webrtc:ice-candidate', {
      from: socket.id,
      candidate: data.candidate
    });
  });

  socket.on('disconnect', () => {
    console.log('Client disconnected:', socket.id);
    if (socket.agentId) {
      callManager.agentOffline(socket.agentId);
    }
  });
});

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({ 
    status: 'OK', 
    timestamp: new Date().toISOString(),
    mongodb: mongoose.connection.readyState === 1 ? 'connected' : 'disconnected'
  });
});

const PORT = process.env.PORT || 5000;
server.listen(PORT, () => {
  console.log(`🚀 Server running on port ${PORT}`);
});