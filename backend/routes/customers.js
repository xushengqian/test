const express = require('express');
const router = express.Router();
const Customer = require('../models/Customer');
const Call = require('../models/Call');
const auth = require('../middleware/auth');

// Get all customers
router.get('/', auth, async (req, res) => {
  try {
    const { status, segment, search, limit = 50, offset = 0 } = req.query;
    
    const query = {};
    
    if (status) query.status = status;
    if (segment) query.segment = segment;
    
    if (search) {
      query.$or = [
        { name: { $regex: search, $options: 'i' } },
        { phoneNumber: { $regex: search, $options: 'i' } },
        { email: { $regex: search, $options: 'i' } },
        { company: { $regex: search, $options: 'i' } }
      ];
    }

    const customers = await Customer.find(query)
      .sort({ createdAt: -1 })
      .limit(parseInt(limit))
      .skip(parseInt(offset));

    const total = await Customer.countDocuments(query);

    res.json({
      customers,
      total,
      limit: parseInt(limit),
      offset: parseInt(offset)
    });
  } catch (error) {
    console.error('Get customers error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get single customer
router.get('/:customerId', auth, async (req, res) => {
  try {
    const customer = await Customer.findById(req.params.customerId);

    if (!customer) {
      return res.status(404).json({ error: 'Customer not found' });
    }

    // Get call history
    const calls = await Call.find({ customerId: customer._id })
      .populate('agentId', 'name')
      .populate('campaignId', 'name')
      .sort({ startTime: -1 })
      .limit(20);

    res.json({
      customer,
      callHistory: calls
    });
  } catch (error) {
    console.error('Get customer error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Create new customer
router.post('/', auth, async (req, res) => {
  try {
    const customerData = req.body;
    
    // Check if customer already exists
    const existing = await Customer.findOne({ 
      phoneNumber: customerData.phoneNumber 
    });
    
    if (existing) {
      return res.status(400).json({ error: 'Customer with this phone number already exists' });
    }

    const customer = new Customer(customerData);
    await customer.save();

    res.status(201).json(customer);
  } catch (error) {
    console.error('Create customer error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Update customer
router.patch('/:customerId', auth, async (req, res) => {
  try {
    const updateData = req.body;
    
    // Don't allow updating phoneNumber to avoid duplicates
    delete updateData.phoneNumber;

    const customer = await Customer.findByIdAndUpdate(
      req.params.customerId,
      updateData,
      { new: true }
    );

    if (!customer) {
      return res.status(404).json({ error: 'Customer not found' });
    }

    res.json(customer);
  } catch (error) {
    console.error('Update customer error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Import customers in bulk
router.post('/import', auth, async (req, res) => {
  try {
    const { customers } = req.body;
    
    if (!Array.isArray(customers)) {
      return res.status(400).json({ error: 'Customers must be an array' });
    }

    const results = {
      success: 0,
      failed: 0,
      errors: []
    };

    for (const customerData of customers) {
      try {
        // Check if customer already exists
        const existing = await Customer.findOne({ 
          phoneNumber: customerData.phoneNumber 
        });
        
        if (existing) {
          results.failed++;
          results.errors.push({
            phoneNumber: customerData.phoneNumber,
            error: 'Already exists'
          });
          continue;
        }

        const customer = new Customer(customerData);
        await customer.save();
        results.success++;
      } catch (error) {
        results.failed++;
        results.errors.push({
          phoneNumber: customerData.phoneNumber,
          error: error.message
        });
      }
    }

    res.json(results);
  } catch (error) {
    console.error('Import customers error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Get customer statistics
router.get('/:customerId/stats', auth, async (req, res) => {
  try {
    const customer = await Customer.findById(req.params.customerId);
    
    if (!customer) {
      return res.status(404).json({ error: 'Customer not found' });
    }

    const stats = await Call.aggregate([
      { $match: { customerId: customer._id } },
      {
        $group: {
          _id: null,
          totalCalls: { $sum: 1 },
          avgDuration: { $avg: '$duration' },
          totalDuration: { $sum: '$duration' },
          successfulCalls: {
            $sum: { $cond: [{ $eq: ['$outcome', 'successful'] }, 1, 0] }
          }
        }
      }
    ]);

    const callsByOutcome = await Call.aggregate([
      { $match: { customerId: customer._id } },
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
        totalDuration: 0,
        successfulCalls: 0
      },
      byOutcome: callsByOutcome
    });
  } catch (error) {
    console.error('Get customer stats error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// Update customer status
router.patch('/:customerId/status', auth, async (req, res) => {
  try {
    const { status } = req.body;
    
    const customer = await Customer.findByIdAndUpdate(
      req.params.customerId,
      { status },
      { new: true }
    );

    if (!customer) {
      return res.status(404).json({ error: 'Customer not found' });
    }

    res.json(customer);
  } catch (error) {
    console.error('Update customer status error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

module.exports = router;