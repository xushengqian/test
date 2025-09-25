const express = require('express');
const router = express.Router();
const jwt = require('jsonwebtoken');
const Agent = require('../models/Agent');

// Agent login
router.post('/login', async (req, res) => {
  try {
    const { username, password } = req.body;

    // Find agent by username
    const agent = await Agent.findOne({ username });
    if (!agent) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    // Check password
    const isMatch = await agent.comparePassword(password);
    if (!isMatch) {
      return res.status(401).json({ error: 'Invalid credentials' });
    }

    // Update last login
    agent.lastLogin = new Date();
    agent.status = 'available';
    await agent.save();

    // Generate JWT token
    const token = jwt.sign(
      { 
        id: agent._id, 
        username: agent.username,
        role: agent.role 
      },
      process.env.JWT_SECRET || 'your-secret-key',
      { expiresIn: '8h' }
    );

    res.json({
      token,
      agent: {
        id: agent._id,
        username: agent.username,
        name: agent.name,
        email: agent.email,
        role: agent.role,
        status: agent.status
      }
    });
  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Agent registration
router.post('/register', async (req, res) => {
  try {
    const { username, email, password, name, role = 'agent' } = req.body;

    // Check if agent already exists
    const existingAgent = await Agent.findOne({
      $or: [{ username }, { email }]
    });

    if (existingAgent) {
      return res.status(400).json({ error: 'Username or email already exists' });
    }

    // Create new agent
    const agent = new Agent({
      username,
      email,
      password,
      name,
      role
    });

    await agent.save();

    res.status(201).json({
      message: 'Agent registered successfully',
      agent: {
        id: agent._id,
        username: agent.username,
        name: agent.name,
        email: agent.email,
        role: agent.role
      }
    });
  } catch (error) {
    console.error('Registration error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Agent logout
router.post('/logout', async (req, res) => {
  try {
    const token = req.headers.authorization?.split(' ')[1];
    if (!token) {
      return res.status(401).json({ error: 'No token provided' });
    }

    const decoded = jwt.verify(token, process.env.JWT_SECRET || 'your-secret-key');
    
    // Update agent status
    await Agent.findByIdAndUpdate(decoded.id, {
      status: 'offline'
    });

    res.json({ message: 'Logged out successfully' });
  } catch (error) {
    console.error('Logout error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Verify token middleware
router.get('/verify', async (req, res) => {
  try {
    const token = req.headers.authorization?.split(' ')[1];
    if (!token) {
      return res.status(401).json({ error: 'No token provided' });
    }

    const decoded = jwt.verify(token, process.env.JWT_SECRET || 'your-secret-key');
    const agent = await Agent.findById(decoded.id).select('-password');

    if (!agent) {
      return res.status(401).json({ error: 'Agent not found' });
    }

    res.json({ agent });
  } catch (error) {
    console.error('Verify error:', error);
    res.status(401).json({ error: 'Invalid token' });
  }
});

module.exports = router;