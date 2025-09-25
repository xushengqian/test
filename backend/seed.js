const mongoose = require('mongoose');
const bcrypt = require('bcryptjs');
require('dotenv').config();

// Import models
const Agent = require('./models/Agent');
const Customer = require('./models/Customer');
const Campaign = require('./models/Campaign');

async function seedDatabase() {
  try {
    // Connect to MongoDB
    await mongoose.connect(process.env.MONGODB_URI || 'mongodb://localhost:27017/robot_call_system', {
      useNewUrlParser: true,
      useUnifiedTopology: true
    });

    console.log('Connected to MongoDB');

    // Clear existing data
    await Agent.deleteMany({});
    await Customer.deleteMany({});
    await Campaign.deleteMany({});
    console.log('Cleared existing data');

    // Create admin agent
    const adminAgent = await Agent.create({
      username: 'admin',
      email: 'admin@example.com',
      password: 'admin123',
      name: '系统管理员',
      role: 'admin',
      status: 'offline',
      skills: ['技术支持', '销售', '客户服务'],
      languages: ['zh-CN', 'en-US']
    });

    // Create regular agents
    const agent1 = await Agent.create({
      username: 'agent1',
      email: 'agent1@example.com',
      password: 'agent123',
      name: '张三',
      role: 'agent',
      status: 'offline',
      skills: ['销售', '客户服务'],
      languages: ['zh-CN']
    });

    const agent2 = await Agent.create({
      username: 'agent2',
      email: 'agent2@example.com',
      password: 'agent123',
      name: '李四',
      role: 'agent',
      status: 'offline',
      skills: ['技术支持', '客户服务'],
      languages: ['zh-CN', 'en-US']
    });

    // Create supervisor
    const supervisor = await Agent.create({
      username: 'supervisor',
      email: 'supervisor@example.com',
      password: 'super123',
      name: '王主管',
      role: 'supervisor',
      status: 'offline',
      skills: ['管理', '培训', '质检'],
      languages: ['zh-CN']
    });

    console.log('Created agents');

    // Create sample customers
    const customers = await Customer.create([
      {
        name: '测试客户1',
        phoneNumber: '+8613800138001',
        email: 'customer1@example.com',
        company: 'ABC公司',
        tags: ['VIP', '高价值'],
        status: 'active',
        preferredContactTime: { start: '09:00', end: '18:00' },
        segment: 'vip'
      },
      {
        name: '测试客户2',
        phoneNumber: '+8613800138002',
        email: 'customer2@example.com',
        company: 'XYZ公司',
        tags: ['潜在客户'],
        status: 'active',
        preferredContactTime: { start: '10:00', end: '17:00' },
        segment: 'potential'
      },
      {
        name: '测试客户3',
        phoneNumber: '+8613800138003',
        email: 'customer3@example.com',
        tags: ['普通客户'],
        status: 'active',
        segment: 'regular'
      },
      {
        name: '测试客户4',
        phoneNumber: '+8613800138004',
        email: 'customer4@example.com',
        company: 'DEF公司',
        tags: ['企业客户'],
        status: 'active',
        preferredContactTime: { start: '09:00', end: '12:00' },
        segment: 'regular'
      },
      {
        name: '测试客户5',
        phoneNumber: '+8613800138005',
        tags: ['新客户'],
        status: 'active',
        segment: 'potential'
      }
    ]);

    console.log('Created customers');

    // Create sample campaigns
    const campaign1 = await Campaign.create({
      name: '产品推广活动',
      description: '推广新产品，收集客户反馈',
      type: 'sales',
      status: 'active',
      startDate: new Date(),
      endDate: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000), // 30 days from now
      script: {
        greeting: '您好！我是ABC公司的客服代表。今天给您打电话是想向您介绍我们的新产品。',
        questions: [
          {
            id: 'q1',
            text: '请问您对我们的产品有了解吗？',
            type: 'yes_no',
            required: true
          },
          {
            id: 'q2',
            text: '您目前使用的是什么品牌的产品？',
            type: 'open',
            required: false
          },
          {
            id: 'q3',
            text: '您对产品最看重的是什么？',
            type: 'multiple_choice',
            options: ['价格', '质量', '服务', '品牌'],
            required: true
          }
        ],
        closing: '感谢您的时间，我们会尽快给您发送产品资料。祝您生活愉快！'
      },
      targetCustomers: customers.map(c => c._id),
      callSettings: {
        maxAttempts: 3,
        retryInterval: 60,
        callTimeout: 30,
        simultaneousCalls: 5
      },
      aiSettings: {
        voiceId: 'default',
        speechRate: 1.0,
        personality: 'professional',
        allowAgentIntervention: true,
        interventionTriggers: ['customer_request', 'negative_sentiment', 'complaint']
      },
      createdBy: adminAgent._id
    });

    const campaign2 = await Campaign.create({
      name: '客户满意度调查',
      description: '收集客户对服务的满意度反馈',
      type: 'survey',
      status: 'scheduled',
      startDate: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000), // 7 days from now
      endDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000), // 14 days from now
      script: {
        greeting: '您好！我是客服中心，想占用您几分钟时间做个满意度调查。',
        questions: [
          {
            id: 'q1',
            text: '您对我们的服务总体满意吗？',
            type: 'rating',
            required: true
          },
          {
            id: 'q2',
            text: '您有什么建议或意见吗？',
            type: 'open',
            required: false
          }
        ],
        closing: '感谢您的反馈，您的意见对我们非常重要！'
      },
      targetSegments: ['vip', 'regular'],
      callSettings: {
        maxAttempts: 2,
        retryInterval: 120,
        callTimeout: 20,
        simultaneousCalls: 3
      },
      aiSettings: {
        voiceId: 'friendly',
        speechRate: 0.9,
        personality: 'friendly',
        allowAgentIntervention: true,
        interventionTriggers: ['complaint', 'negative_sentiment']
      },
      createdBy: supervisor._id
    });

    console.log('Created campaigns');

    console.log('\n=================================');
    console.log('Database seeded successfully!');
    console.log('=================================');
    console.log('\nTest Accounts:');
    console.log('Admin: username: admin, password: admin123');
    console.log('Agent 1: username: agent1, password: agent123');
    console.log('Agent 2: username: agent2, password: agent123');
    console.log('Supervisor: username: supervisor, password: super123');
    console.log('\nCreated:');
    console.log(`- ${4} agents`);
    console.log(`- ${customers.length} customers`);
    console.log(`- ${2} campaigns`);
    console.log('=================================\n');

    process.exit(0);
  } catch (error) {
    console.error('Seed error:', error);
    process.exit(1);
  }
}

seedDatabase();