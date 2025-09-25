const express = require('express');
const router = express.Router();
const Call = require('../models/Call');
const Customer = require('../models/Customer');
const Campaign = require('../models/Campaign');
const auth = require('../middleware/auth');

// Get all calls with filters
router.get('/', auth, async (req, res) => {
  try {
    const { status, agentId, customerId, campaignId, startDate, endDate, limit = 50, offset = 0 } = req.query;
    
    const query = {};
    
    if (status) query.status = status;
    if (agentId) query.agentId = agentId;
    if (customerId) query.customerId = customerId;
    if (campaignId) query.campaignId = campaignId;
    
    if (startDate || endDate) {
      query.startTime = {};
      if (startDate) query.startTime.$gte = new Date(startDate);
      if (endDate) query.startTime.$lte = new Date(endDate);
    }

    const calls = await Call.find(query)
      .populate('customerId', 'name phoneNumber')
      .populate('agentId', 'name username')
      .populate('campaignId', 'name')
      .sort({ startTime: -1 })
      .limit(parseInt(limit))
      .skip(parseInt(offset));

    const total = await Call.countDocuments(query);

    res.json({
      calls,
      total,
      limit: parseInt(limit),
      offset: parseInt(offset)
    });
  } catch (error) {
    console.error('Get calls error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get single call details
router.get('/:callId', auth, async (req, res) => {
  try {
    const call = await Call.findOne({ id: req.params.callId })
      .populate('customerId')
      .populate('agentId', 'name username')
      .populate('campaignId');

    if (!call) {
      return res.status(404).json({ error: 'Call not found' });
    }

    res.json(call);
  } catch (error) {
    console.error('Get call error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Initiate outbound call
router.post('/initiate', auth, async (req, res) => {
  try {
    const { customerId, campaignId, phoneNumber } = req.body;

    // Validate customer
    const customer = await Customer.findById(customerId);
    if (!customer) {
      return res.status(404).json({ error: 'Customer not found' });
    }

    // Check if customer can be called
    if (!customer.canBeCalled()) {
      return res.status(400).json({ error: 'Customer cannot be called at this time' });
    }

    // Validate campaign if provided
    if (campaignId) {
      const campaign = await Campaign.findById(campaignId);
      if (!campaign) {
        return res.status(404).json({ error: 'Campaign not found' });
      }

      if (!campaign.isActive() || !campaign.isWorkingHours()) {
        return res.status(400).json({ error: 'Campaign is not active or outside working hours' });
      }
    }

    // Note: In a real implementation, this would trigger the CallManager
    // For now, we'll create a call record
    const call = new Call({
      id: require('uuid').v4(),
      customerId,
      campaignId,
      phoneNumber: phoneNumber || customer.phoneNumber,
      status: 'initiating',
      startTime: new Date(),
      isRobotHandling: true,
      transcript: [],
      events: [{
        type: 'call_initiated',
        timestamp: new Date(),
        data: { initiatedBy: req.user.id }
      }]
    });

    await call.save();

    res.status(201).json({
      message: 'Call initiated successfully',
      call
    });
  } catch (error) {
    console.error('Initiate call error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Update call status
router.patch('/:callId/status', auth, async (req, res) => {
  try {
    const { status } = req.body;
    
    const call = await Call.findOneAndUpdate(
      { id: req.params.callId },
      { 
        status,
        $push: {
          events: {
            type: 'status_change',
            timestamp: new Date(),
            data: { status, changedBy: req.user.id }
          }
        }
      },
      { new: true }
    );

    if (!call) {
      return res.status(404).json({ error: 'Call not found' });
    }

    res.json(call);
  } catch (error) {
    console.error('Update call status error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Add note to call
router.post('/:callId/notes', auth, async (req, res) => {
  try {
    const { note } = req.body;
    
    const call = await Call.findOneAndUpdate(
      { id: req.params.callId },
      { 
        notes: note,
        $push: {
          events: {
            type: 'note_added',
            timestamp: new Date(),
            data: { note, addedBy: req.user.id }
          }
        }
      },
      { new: true }
    );

    if (!call) {
      return res.status(404).json({ error: 'Call not found' });
    }

    res.json(call);
  } catch (error) {
    console.error('Add note error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get call transcript
router.get('/:callId/transcript', auth, async (req, res) => {
  try {
    const call = await Call.findOne({ id: req.params.callId })
      .select('transcript');

    if (!call) {
      return res.status(404).json({ error: 'Call not found' });
    }

    res.json(call.transcript);
  } catch (error) {
    console.error('Get transcript error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get call statistics
router.get('/stats/overview', auth, async (req, res) => {
  try {
    const { startDate, endDate, agentId, campaignId } = req.query;
    
    const match = {};
    
    if (startDate || endDate) {
      match.startTime = {};
      if (startDate) match.startTime.$gte = new Date(startDate);
      if (endDate) match.startTime.$lte = new Date(endDate);
    }
    
    if (agentId) match.agentId = agentId;
    if (campaignId) match.campaignId = campaignId;

    const stats = await Call.aggregate([
      { $match: match },
      {
        $group: {
          _id: null,
          totalCalls: { $sum: 1 },
          avgDuration: { $avg: '$duration' },
          totalDuration: { $sum: '$duration' },
          successfulCalls: {
            $sum: { $cond: [{ $eq: ['$outcome', 'successful'] }, 1, 0] }
          },
          robotHandledCalls: {
            $sum: { $cond: ['$isRobotHandling', 1, 0] }
          },
          agentInterventions: {
            $sum: { $cond: [{ $ne: ['$agentId', null] }, 1, 0] }
          }
        }
      }
    ]);

    // Get calls by status
    const callsByStatus = await Call.aggregate([
      { $match: match },
      {
        $group: {
          _id: '$status',
          count: { $sum: 1 }
        }
      }
    ]);

    // Get calls by hour
    const callsByHour = await Call.aggregate([
      { $match: match },
      {
        $group: {
          _id: { $hour: '$startTime' },
          count: { $sum: 1 }
        }
      },
      { $sort: { _id: 1 } }
    ]);

    res.json({
      overview: stats[0] || {
        totalCalls: 0,
        avgDuration: 0,
        totalDuration: 0,
        successfulCalls: 0,
        robotHandledCalls: 0,
        agentInterventions: 0
      },
      byStatus: callsByStatus,
      byHour: callsByHour
    });
  } catch (error) {
    console.error('Get stats error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;