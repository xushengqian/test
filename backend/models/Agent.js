const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');

const AgentSchema = new mongoose.Schema({
  username: {
    type: String,
    required: true,
    unique: true
  },
  email: {
    type: String,
    required: true,
    unique: true,
    lowercase: true
  },
  password: {
    type: String,
    required: true
  },
  name: {
    type: String,
    required: true
  },
  role: {
    type: String,
    enum: ['agent', 'supervisor', 'admin'],
    default: 'agent'
  },
  status: {
    type: String,
    enum: ['available', 'busy', 'break', 'offline'],
    default: 'offline'
  },
  skills: [{
    type: String
  }],
  languages: [{
    type: String,
    default: ['zh-CN']
  }],
  currentCall: {
    type: mongoose.Schema.Types.ObjectId,
    ref: 'Call'
  },
  statistics: {
    totalCalls: {
      type: Number,
      default: 0
    },
    totalTalkTime: {
      type: Number,
      default: 0
    },
    averageHandleTime: {
      type: Number,
      default: 0
    },
    interventions: {
      type: Number,
      default: 0
    },
    takeovers: {
      type: Number,
      default: 0
    },
    satisfactionScore: {
      type: Number,
      default: 0
    }
  },
  lastLogin: {
    type: Date
  },
  isActive: {
    type: Boolean,
    default: true
  }
}, {
  timestamps: true
});

// Hash password before saving
AgentSchema.pre('save', async function(next) {
  if (!this.isModified('password')) return next();
  
  try {
    const salt = await bcrypt.genSalt(10);
    this.password = await bcrypt.hash(this.password, salt);
    next();
  } catch (error) {
    next(error);
  }
});

// Compare password method
AgentSchema.methods.comparePassword = async function(candidatePassword) {
  return await bcrypt.compare(candidatePassword, this.password);
};

// Update statistics method
AgentSchema.methods.updateStatistics = async function(callData) {
  this.statistics.totalCalls += 1;
  this.statistics.totalTalkTime += callData.duration || 0;
  this.statistics.averageHandleTime = this.statistics.totalTalkTime / this.statistics.totalCalls;
  
  if (callData.interventionType === 'intervention') {
    this.statistics.interventions += 1;
  } else if (callData.interventionType === 'takeover') {
    this.statistics.takeovers += 1;
  }
  
  await this.save();
};

module.exports = mongoose.model('Agent', AgentSchema);