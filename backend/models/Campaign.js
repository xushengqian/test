const mongoose = require('mongoose');

const CampaignSchema = new mongoose.Schema({
  name: {
    type: String,
    required: true
  },
  description: {
    type: String
  },
  type: {
    type: String,
    enum: ['sales', 'survey', 'notification', 'collection', 'support'],
    required: true
  },
  status: {
    type: String,
    enum: ['draft', 'scheduled', 'active', 'paused', 'completed', 'cancelled'],
    default: 'draft'
  },
  startDate: {
    type: Date,
    required: true
  },
  endDate: {
    type: Date,
    required: true
  },
  workingHours: {
    start: {
      type: String, // e.g., "09:00"
      default: "09:00"
    },
    end: {
      type: String, // e.g., "18:00"
      default: "18:00"
    }
  },
  workingDays: [{
    type: Number, // 0-6, where 0 is Sunday
    default: [1, 2, 3, 4, 5] // Monday to Friday
  }],
  script: {
    greeting: {
      type: String,
      required: true
    },
    questions: [{
      id: String,
      text: String,
      type: {
        type: String,
        enum: ['open', 'yes_no', 'multiple_choice', 'rating']
      },
      options: [String],
      required: Boolean
    }],
    closing: {
      type: String
    }
  },
  targetCustomers: [{
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Customer'
  }],
  targetSegments: [{
    type: String,
    enum: ['vip', 'regular', 'potential', 'churned']
  }],
  callSettings: {
    maxAttempts: {
      type: Number,
      default: 3
    },
    retryInterval: {
      type: Number, // in minutes
      default: 60
    },
    callTimeout: {
      type: Number, // in seconds
      default: 30
    },
    simultaneousCalls: {
      type: Number,
      default: 10
    }
  },
  aiSettings: {
    voiceId: {
      type: String,
      default: 'default'
    },
    speechRate: {
      type: Number,
      default: 1.0
    },
    personality: {
      type: String,
      enum: ['professional', 'friendly', 'casual', 'formal'],
      default: 'professional'
    },
    allowAgentIntervention: {
      type: Boolean,
      default: true
    },
    interventionTriggers: [{
      type: String,
      enum: ['customer_request', 'negative_sentiment', 'complex_query', 'high_value_customer', 'complaint']
    }]
  },
  statistics: {
    totalCalls: {
      type: Number,
      default: 0
    },
    successfulCalls: {
      type: Number,
      default: 0
    },
    failedCalls: {
      type: Number,
      default: 0
    },
    averageDuration: {
      type: Number,
      default: 0
    },
    agentInterventions: {
      type: Number,
      default: 0
    },
    conversionRate: {
      type: Number,
      default: 0
    }
  },
  createdBy: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Agent',
    required: true
  }
}, {
  timestamps: true
});

// Indexes
CampaignSchema.index({ status: 1 });
CampaignSchema.index({ startDate: 1, endDate: 1 });
CampaignSchema.index({ type: 1 });

// Method to check if campaign is active
CampaignSchema.methods.isActive = function() {
  const now = new Date();
  return this.status === 'active' && 
         now >= this.startDate && 
         now <= this.endDate;
};

// Method to check if it's working hours
CampaignSchema.methods.isWorkingHours = function() {
  const now = new Date();
  const currentDay = now.getDay();
  const currentTime = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;
  
  if (!this.workingDays.includes(currentDay)) {
    return false;
  }
  
  return currentTime >= this.workingHours.start && currentTime <= this.workingHours.end;
};

module.exports = mongoose.model('Campaign', CampaignSchema);