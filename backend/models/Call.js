const mongoose = require('mongoose');

const CallSchema = new mongoose.Schema({
  id: {
    type: String,
    required: true,
    unique: true
  },
  customerId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Customer',
    required: true
  },
  campaignId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Campaign'
  },
  phoneNumber: {
    type: String,
    required: true
  },
  status: {
    type: String,
    enum: ['initiating', 'ringing', 'connected', 'on_hold', 'ended', 'failed', 'no_answer'],
    default: 'initiating'
  },
  isRobotHandling: {
    type: Boolean,
    default: true
  },
  agentId: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Agent'
  },
  interventionMode: {
    type: String,
    enum: ['monitoring', 'suggesting', 'full_control'],
    default: null
  },
  startTime: {
    type: Date,
    required: true
  },
  endTime: {
    type: Date
  },
  duration: {
    type: Number, // in seconds
    default: 0
  },
  takeoverTime: {
    type: Date
  },
  transcript: [{
    speaker: {
      type: String,
      enum: ['robot', 'customer', 'agent', 'system'],
      required: true
    },
    text: {
      type: String,
      required: true
    },
    timestamp: {
      type: Date,
      required: true
    },
    sentiment: {
      type: String,
      enum: ['positive', 'neutral', 'negative']
    }
  }],
  events: [{
    type: {
      type: String,
      required: true
    },
    timestamp: {
      type: Date,
      required: true
    },
    data: {
      type: mongoose.Schema.Types.Mixed
    }
  }],
  recording: {
    url: String,
    duration: Number
  },
  outcome: {
    type: String,
    enum: ['successful', 'callback_scheduled', 'not_interested', 'wrong_number', 'do_not_call', 'voicemail', 'failed']
  },
  notes: {
    type: String
  },
  satisfaction: {
    score: {
      type: Number,
      min: 1,
      max: 5
    },
    feedback: String
  },
  metadata: {
    type: mongoose.Schema.Types.Mixed
  }
}, {
  timestamps: true
});

// Indexes for better query performance
CallSchema.index({ customerId: 1 });
CallSchema.index({ agentId: 1 });
CallSchema.index({ campaignId: 1 });
CallSchema.index({ status: 1 });
CallSchema.index({ startTime: -1 });

module.exports = mongoose.model('Call', CallSchema);