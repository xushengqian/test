const express = require('express');
const router = express.Router();
const Agent = require('../models/Agent');
const Call = require('../models/Call');
const auth = require('../middleware/auth');

// Get all agents
router.get('/', auth, async (req, res) => {
  try {
    const { status, role } = req.query;
    const query = {};
    
    if (status) query.status = status;
    if (role) query.role = role;

    const agents = await Agent.find(query)
      .select('-password')
      .sort({ name: 1 });

    res.json(agents);
  } catch (error) {
    console.error('Get agents error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get single agent
router.get('/:agentId', auth, async (req, res) => {
  try {
    const agent = await Agent.findById(req.params.agentId)
      .select('-password');

    if (!agent) {
      return res.status(404).json({ error: 'Agent not found' });
    }

    res.json(agent);
  } catch (error) {
    console.error('Get agent error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Update agent status
router.patch('/:agentId/status', auth, async (req, res) => {
  try {
    const { status } = req.body;
    
    const agent = await Agent.findByIdAndUpdate(
      req.params.agentId,
      { status },
      { new: true }
    ).select('-password');

    if (!agent) {
      return res.status(404).json({ error: 'Agent not found' });
    }

    res.json(agent);
  } catch (error) {
    console.error('Update agent status error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get agent statistics
router.get('/:agentId/stats', auth, async (req, res) => {
  try {
    const { startDate, endDate } = req.query;
    
    const agent = await Agent.findById(req.params.agentId)
      .select('statistics');

    if (!agent) {
      return res.status(404).json({ error: 'Agent not found' });
    }

    // Get detailed call statistics
    const match = { agentId: req.params.agentId };
    
    if (startDate || endDate) {
      match.startTime = {};
      if (startDate) match.startTime.$gte = new Date(startDate);
      if (endDate) match.startTime.$lte = new Date(endDate);
    }

    const callStats = await Call.aggregate([
      { $match: match },
      {
        $group: {
          _id: null,
          totalCalls: { $sum: 1 },
          avgDuration: { $avg: '$duration' },
          totalDuration: { $sum: '$duration' },
          interventions: {
            $sum: { 
              $cond: [
                { $eq: ['$interventionMode', 'monitoring'] }, 
                1, 
                0
              ] 
            }
          },
          takeovers: {
            $sum: { 
              $cond: [
                { $eq: ['$interventionMode', 'full_control'] }, 
                1, 
                0
              ] 
            }
          }
        }
      }
    ]);

    // Get calls by outcome
    const callsByOutcome = await Call.aggregate([
      { $match: match },
      {
        $group: {
          _id: '$outcome',
          count: { $sum: 1 }
        }
      }
    ]);

    // Get recent calls
    const recentCalls = await Call.find(match)
      .populate('customerId', 'name phoneNumber')
      .populate('campaignId', 'name')
      .sort({ startTime: -1 })
      .limit(10)
      .select('id phoneNumber status startTime duration outcome');

    res.json({
      basic: agent.statistics,
      detailed: callStats[0] || {
        totalCalls: 0,
        avgDuration: 0,
        totalDuration: 0,
        interventions: 0,
        takeovers: 0
      },
      byOutcome: callsByOutcome,
      recentCalls
    });
  } catch (error) {
    console.error('Get agent stats error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Update agent profile
router.patch('/:agentId', auth, async (req, res) => {
  try {
    const { name, email, skills, languages } = req.body;
    
    const updateData = {};
    if (name) updateData.name = name;
    if (email) updateData.email = email;
    if (skills) updateData.skills = skills;
    if (languages) updateData.languages = languages;

    const agent = await Agent.findByIdAndUpdate(
      req.params.agentId,
      updateData,
      { new: true }
    ).select('-password');

    if (!agent) {
      return res.status(404).json({ error: 'Agent not found' });
    }

    res.json(agent);
  } catch (error) {
    console.error('Update agent error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get agent's current call
router.get('/:agentId/current-call', auth, async (req, res) => {
  try {
    const agent = await Agent.findById(req.params.agentId)
      .populate({
        path: 'currentCall',
        populate: {
          path: 'customerId',
          select: 'name phoneNumber'
        }
      });

    if (!agent) {
      return res.status(404).json({ error: 'Agent not found' });
    }

    res.json(agent.currentCall || null);
  } catch (error) {
    console.error('Get current call error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get available agents for intervention
router.get('/available/intervention', auth, async (req, res) => {
  try {
    const agents = await Agent.find({
      status: 'available',
      isActive: true
    })
    .select('name username skills languages statistics.totalCalls statistics.satisfactionScore')
    .sort({ 'statistics.satisfactionScore': -1 });

    res.json(agents);
  } catch (error) {
    console.error('Get available agents error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;