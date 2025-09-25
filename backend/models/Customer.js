const mongoose = require('mongoose');

const CustomerSchema = new mongoose.Schema({
  name: {
    type: String,
    required: true
  },
  phoneNumber: {
    type: String,
    required: true,
    unique: true
  },
  alternatePhones: [{
    type: String
  }],
  email: {
    type: String,
    lowercase: true
  },
  company: {
    type: String
  },
  tags: [{
    type: String
  }],
  status: {
    type: String,
    enum: ['active', 'inactive', 'do_not_call', 'blacklisted'],
    default: 'active'
  },
  preferredContactTime: {
    start: String, // e.g., "09:00"
    end: String    // e.g., "18:00"
  },
  timezone: {
    type: String,
    default: 'Asia/Shanghai'
  },
  language: {
    type: String,
    default: 'zh-CN'
  },
  callHistory: [{
    callId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'Call'
    },
    date: Date,
    outcome: String,
    notes: String
  }],
  lastContactDate: {
    type: Date
  },
  nextContactDate: {
    type: Date
  },
  customFields: {
    type: Map,
    of: mongoose.Schema.Types.Mixed
  },
  notes: {
    type: String
  },
  score: {
    type: Number,
    min: 0,
    max: 100,
    default: 50
  },
  segment: {
    type: String,
    enum: ['vip', 'regular', 'potential', 'churned'],
    default: 'regular'
  }
}, {
  timestamps: true
});

// Indexes
CustomerSchema.index({ phoneNumber: 1 });
CustomerSchema.index({ email: 1 });
CustomerSchema.index({ status: 1 });
CustomerSchema.index({ segment: 1 });

// Method to check if customer can be called
CustomerSchema.methods.canBeCalled = function() {
  if (this.status === 'do_not_call' || this.status === 'blacklisted') {
    return false;
  }
  
  if (this.preferredContactTime) {
    const now = new Date();
    const currentTime = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;
    
    if (currentTime < this.preferredContactTime.start || currentTime > this.preferredContactTime.end) {
      return false;
    }
  }
  
  return true;
};

module.exports = mongoose.model('Customer', CustomerSchema);