const jwt = require('jsonwebtoken');
const Agent = require('../models/Agent');

const auth = async (req, res, next) => {
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

    if (!agent.isActive) {
      return res.status(401).json({ error: 'Agent account is deactivated' });
    }

    req.user = {
      id: agent._id,
      username: agent.username,
      role: agent.role
    };
    req.agent = agent;

    next();
  } catch (error) {
    console.error('Auth middleware error:', error);
    res.status(401).json({ error: 'Invalid or expired token' });
  }
};

// Admin only middleware
const adminOnly = async (req, res, next) => {
  if (req.user.role !== 'admin') {
    return res.status(403).json({ error: 'Admin access required' });
  }
  next();
};

// Supervisor or admin middleware
const supervisorOrAdmin = async (req, res, next) => {
  if (!['supervisor', 'admin'].includes(req.user.role)) {
    return res.status(403).json({ error: 'Supervisor or admin access required' });
  }
  next();
};

module.exports = auth;
module.exports.adminOnly = adminOnly;
module.exports.supervisorOrAdmin = supervisorOrAdmin;