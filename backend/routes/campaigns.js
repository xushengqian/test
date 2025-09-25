const express = require('express');
const router = express.Router();
const Campaign = require('../models/Campaign');
const Customer = require('../models/Customer');
const Call = require('../models/Call');
const auth = require('../middleware/auth');

// Get all campaigns
router.get('/', auth, async (req, res) => {
  try {
    const { status, type } = req.query;
    const query = {};
    
    if (status) query.status = status;
    if (type) query.type = type;

    const campaigns = await Campaign.find(query)
      .populate('createdBy', 'name username')
      .sort({ createdAt: -1 });

    res.json(campaigns);
  } catch (error) {
    console.error('Get campaigns error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get single campaign
router.get('/:campaignId', auth, async (req, res) => {
  try {
    const campaign = await Campaign.findById(req.params.campaignId)
      .populate('createdBy', 'name username')
      .populate('targetCustomers', 'name phoneNumber');

    if (!campaign) {
      return res.status(404).json({ error: 'Campaign not found' });
    }

    res.json(campaign);
  } catch (error) {
    console.error('Get campaign error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Create new campaign
router.post('/', auth, async (req, res) => {
  try {
    const campaignData = {
      ...req.body,
      createdBy: req.user.id
    };

    const campaign = new Campaign(campaignData);
    await campaign.save();

    res.status(201).json(campaign);
  } catch (error) {
    console.error('Create campaign error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Update campaign
router.patch('/:campaignId', auth, async (req, res) => {
  try {
    const updateData = req.body;
    
    const campaign = await Campaign.findByIdAndUpdate(
      req.params.campaignId,
      updateData,
      { new: true }
    );

    if (!campaign) {
      return res.status(404).json({ error: 'Campaign not found' });
    }

    res.json(campaign);
  } catch (error) {
    console.error('Update campaign error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Start campaign
router.post('/:campaignId/start', auth, async (req, res) => {
  try {
    const campaign = await Campaign.findById(req.params.campaignId);

    if (!campaign) {
      return res.status(404).json({ error: 'Campaign not found' });
    }

    if (campaign.status === 'active') {
      return res.status(400).json({ error: 'Campaign is already active' });
    }

    campaign.status = 'active';
    await campaign.save();

    // TODO: Trigger campaign execution logic

    res.json({
      message: 'Campaign started successfully',
      campaign
    });
  } catch (error) {
    console.error('Start campaign error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Pause campaign
router.post('/:campaignId/pause', auth, async (req, res) => {
  try {
    const campaign = await Campaign.findById(req.params.campaignId);

    if (!campaign) {
      return res.status(404).json({ error: 'Campaign not found' });
    }

    if (campaign.status !== 'active') {
      return res.status(400).json({ error: 'Campaign is not active' });
    }

    campaign.status = 'paused';
    await campaign.save();

    res.json({
      message: 'Campaign paused successfully',
      campaign
    });
  } catch (error) {
    console.error('Pause campaign error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Stop campaign
router.post('/:campaignId/stop', auth, async (req, res) => {
  try {
    const campaign = await Campaign.findById(req.params.campaignId);

    if (!campaign) {
      return res.status(404).json({ error: 'Campaign not found' });
    }

    campaign.status = 'completed';
    await campaign.save();

    res.json({
      message: 'Campaign stopped successfully',
      campaign
    });
  } catch (error) {
    console.error('Stop campaign error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get campaign statistics
router.get('/:campaignId/stats', auth, async (req, res) => {
  try {
    const campaign = await Campaign.findById(req.params.campaignId);
    
    if (!campaign) {
      return res.status(404).json({ error: 'Campaign not found' });
    }

    const stats = await Call.aggregate([
      { $match: { campaignId: campaign._id } },
      {
        $group: {
          _id: null,
          totalCalls: { $sum: 1 },
          avgDuration: { $avg: '$duration' },
          successfulCalls: {
            $sum: { $cond: [{ $eq: ['$outcome', 'successful'] }, 1, 0] }
          },
          agentInterventions: {
            $sum: { $cond: [{ $ne: ['$agentId', null] }, 1, 0] }
          }
        }
      }
    ]);

    const callsByStatus = await Call.aggregate([
      { $match: { campaignId: campaign._id } },
      {
        $group: {
          _id: '$status',
          count: { $sum: 1 }
        }
      }
    ]);

    const callsByOutcome = await Call.aggregate([
      { $match: { campaignId: campaign._id } },
      {
        $group: {
          _id: '$outcome',
          count: { $sum: 1 }
        }
      }
    ]);

    res.json({
      overview: stats[0] || {
        totalCalls: 0,
        avgDuration: 0,
        successfulCalls: 0,
        agentInterventions: 0
      },
      byStatus: callsByStatus,
      byOutcome: callsByOutcome,
      conversionRate: stats[0] ? 
        (stats[0].successfulCalls / stats[0].totalCalls * 100).toFixed(2) : 0
    });
  } catch (error) {
    console.error('Get campaign stats error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Add customers to campaign
router.post('/:campaignId/customers', auth, async (req, res) => {
  try {
    const { customerIds } = req.body;
    
    const campaign = await Campaign.findById(req.params.campaignId);
    
    if (!campaign) {
      return res.status(404).json({ error: 'Campaign not found' });
    }

    // Add unique customer IDs
    const uniqueCustomerIds = [...new Set([
      ...campaign.targetCustomers.map(id => id.toString()),
      ...customerIds
    ])];

    campaign.targetCustomers = uniqueCustomerIds;
    await campaign.save();

    res.json({
      message: 'Customers added successfully',
      totalCustomers: campaign.targetCustomers.length
    });
  } catch (error) {
    console.error('Add customers error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Remove customers from campaign
router.delete('/:campaignId/customers', auth, async (req, res) => {
  try {
    const { customerIds } = req.body;
    
    const campaign = await Campaign.findById(req.params.campaignId);
    
    if (!campaign) {
      return res.status(404).json({ error: 'Campaign not found' });
    }

    campaign.targetCustomers = campaign.targetCustomers.filter(
      id => !customerIds.includes(id.toString())
    );
    await campaign.save();

    res.json({
      message: 'Customers removed successfully',
      totalCustomers: campaign.targetCustomers.length
    });
  } catch (error) {
    console.error('Remove customers error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;