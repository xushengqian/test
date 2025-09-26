const { Sequelize, DataTypes } = require('sequelize');
const bcrypt = require('bcrypt');
const winston = require('winston');

// 配置日志
const logger = winston.createLogger({
  level: 'info',
  format: winston.format.json(),
  transports: [
    new winston.transports.File({ filename: 'database.log' }),
    new winston.transports.Console()
  ]
});

class DatabaseService {
  constructor(config) {
    this.sequelize = new Sequelize(
      config.database,
      config.username,
      config.password,
      {
        host: config.host,
        port: config.port,
        dialect: 'mysql',
        logging: false,
        pool: {
          max: 10,
          min: 0,
          acquire: 30000,
          idle: 10000
        }
      }
    );

    this.models = {};
    this.defineModels();
  }

  // 定义数据模型
  defineModels() {
    // 管理员模型
    this.models.Admin = this.sequelize.define('Admin', {
      id: {
        type: DataTypes.UUID,
        defaultValue: DataTypes.UUIDV4,
        primaryKey: true
      },
      username: {
        type: DataTypes.STRING,
        unique: true,
        allowNull: false
      },
      password: {
        type: DataTypes.STRING,
        allowNull: false
      },
      name: {
        type: DataTypes.STRING,
        allowNull: false
      },
      email: {
        type: DataTypes.STRING,
        unique: true
      },
      role: {
        type: DataTypes.ENUM('admin', 'supervisor'),
        defaultValue: 'admin'
      },
      isActive: {
        type: DataTypes.BOOLEAN,
        defaultValue: true
      }
    });

    // 坐席模型
    this.models.Agent = this.sequelize.define('Agent', {
      id: {
        type: DataTypes.UUID,
        defaultValue: DataTypes.UUIDV4,
        primaryKey: true
      },
      username: {
        type: DataTypes.STRING,
        unique: true,
        allowNull: false
      },
      password: {
        type: DataTypes.STRING,
        allowNull: false
      },
      name: {
        type: DataTypes.STRING,
        allowNull: false
      },
      email: {
        type: DataTypes.STRING,
        unique: true
      },
      extension: {
        type: DataTypes.STRING
      },
      skills: {
        type: DataTypes.JSON,
        defaultValue: []
      },
      skillLevels: {
        type: DataTypes.JSON,
        defaultValue: {}
      },
      isActive: {
        type: DataTypes.BOOLEAN,
        defaultValue: true
      }
    });

    // 技能组模型
    this.models.Skill = this.sequelize.define('Skill', {
      id: {
        type: DataTypes.UUID,
        defaultValue: DataTypes.UUIDV4,
        primaryKey: true
      },
      name: {
        type: DataTypes.STRING,
        unique: true,
        allowNull: false
      },
      description: {
        type: DataTypes.TEXT
      },
      priority: {
        type: DataTypes.INTEGER,
        defaultValue: 5
      }
    });

    // 外呼任务模型
    this.models.Campaign = this.sequelize.define('Campaign', {
      id: {
        type: DataTypes.UUID,
        defaultValue: DataTypes.UUIDV4,
        primaryKey: true
      },
      name: {
        type: DataTypes.STRING,
        allowNull: false
      },
      description: {
        type: DataTypes.TEXT
      },
      script: {
        type: DataTypes.TEXT
      },
      phoneList: {
        type: DataTypes.JSON,
        defaultValue: []
      },
      startTime: {
        type: DataTypes.DATE
      },
      endTime: {
        type: DataTypes.DATE
      },
      status: {
        type: DataTypes.ENUM('pending', 'running', 'paused', 'completed', 'cancelled'),
        defaultValue: 'pending'
      },
      maxRetries: {
        type: DataTypes.INTEGER,
        defaultValue: 3
      },
      retryInterval: {
        type: DataTypes.INTEGER,
        defaultValue: 3600000
      },
      priority: {
        type: DataTypes.INTEGER,
        defaultValue: 5
      },
      stats: {
        type: DataTypes.JSON,
        defaultValue: {
          total: 0,
          completed: 0,
          answered: 0,
          failed: 0,
          transferred: 0,
          avgDuration: 0
        }
      }
    });

    // 通话记录模型
    this.models.CallRecord = this.sequelize.define('CallRecord', {
      id: {
        type: DataTypes.UUID,
        defaultValue: DataTypes.UUIDV4,
        primaryKey: true
      },
      callUuid: {
        type: DataTypes.STRING,
        unique: true
      },
      campaignId: {
        type: DataTypes.UUID
      },
      phoneNumber: {
        type: DataTypes.STRING,
        allowNull: false
      },
      direction: {
        type: DataTypes.ENUM('inbound', 'outbound'),
        defaultValue: 'outbound'
      },
      status: {
        type: DataTypes.ENUM('initiated', 'ringing', 'answered', 'completed', 'failed'),
        defaultValue: 'initiated'
      },
      agentId: {
        type: DataTypes.UUID
      },
      startTime: {
        type: DataTypes.DATE
      },
      answerTime: {
        type: DataTypes.DATE
      },
      endTime: {
        type: DataTypes.DATE
      },
      duration: {
        type: DataTypes.INTEGER
      },
      hangupCause: {
        type: DataTypes.STRING
      },
      isRobot: {
        type: DataTypes.BOOLEAN,
        defaultValue: true
      },
      transferredToAgent: {
        type: DataTypes.BOOLEAN,
        defaultValue: false
      },
      recordingFile: {
        type: DataTypes.STRING
      },
      transcription: {
        type: DataTypes.TEXT
      },
      metadata: {
        type: DataTypes.JSON,
        defaultValue: {}
      }
    });

    // 坐席活动日志模型
    this.models.AgentActivityLog = this.sequelize.define('AgentActivityLog', {
      id: {
        type: DataTypes.UUID,
        defaultValue: DataTypes.UUIDV4,
        primaryKey: true
      },
      agentId: {
        type: DataTypes.UUID,
        allowNull: false
      },
      action: {
        type: DataTypes.STRING,
        allowNull: false
      },
      data: {
        type: DataTypes.JSON,
        defaultValue: {}
      },
      timestamp: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW
      }
    });

    // 系统日志模型
    this.models.SystemLog = this.sequelize.define('SystemLog', {
      id: {
        type: DataTypes.UUID,
        defaultValue: DataTypes.UUIDV4,
        primaryKey: true
      },
      level: {
        type: DataTypes.ENUM('debug', 'info', 'warning', 'error', 'critical'),
        defaultValue: 'info'
      },
      category: {
        type: DataTypes.STRING
      },
      message: {
        type: DataTypes.TEXT
      },
      data: {
        type: DataTypes.JSON,
        defaultValue: {}
      },
      timestamp: {
        type: DataTypes.DATE,
        defaultValue: DataTypes.NOW
      }
    });

    // 系统配置模型
    this.models.SystemConfig = this.sequelize.define('SystemConfig', {
      key: {
        type: DataTypes.STRING,
        primaryKey: true
      },
      value: {
        type: DataTypes.JSON
      },
      description: {
        type: DataTypes.STRING
      }
    });

    // 设置关联关系
    this.setupAssociations();
  }

  // 设置模型关联
  setupAssociations() {
    const { Campaign, CallRecord, Agent, AgentActivityLog } = this.models;

    // 外呼任务和通话记录
    Campaign.hasMany(CallRecord, { foreignKey: 'campaignId' });
    CallRecord.belongsTo(Campaign, { foreignKey: 'campaignId' });

    // 坐席和通话记录
    Agent.hasMany(CallRecord, { foreignKey: 'agentId' });
    CallRecord.belongsTo(Agent, { foreignKey: 'agentId' });

    // 坐席和活动日志
    Agent.hasMany(AgentActivityLog, { foreignKey: 'agentId' });
    AgentActivityLog.belongsTo(Agent, { foreignKey: 'agentId' });
  }

  // 初始化数据库
  async initialize() {
    try {
      // 测试连接
      await this.sequelize.authenticate();
      logger.info('Database connection established');

      // 同步模型
      await this.sequelize.sync({ alter: true });
      logger.info('Database models synchronized');

      // 创建默认数据
      await this.createDefaultData();

      return true;
    } catch (error) {
      logger.error('Database initialization failed:', error);
      throw error;
    }
  }

  // 创建默认数据
  async createDefaultData() {
    try {
      // 创建默认管理员
      const adminCount = await this.models.Admin.count();
      if (adminCount === 0) {
        const hashedPassword = await bcrypt.hash('admin123', 10);
        await this.models.Admin.create({
          username: 'admin',
          password: hashedPassword,
          name: '系统管理员',
          email: 'admin@example.com',
          role: 'admin'
        });
        logger.info('Default admin created');
      }

      // 创建默认技能组
      const skillCount = await this.models.Skill.count();
      if (skillCount === 0) {
        await this.models.Skill.bulkCreate([
          { name: '销售', description: '销售相关咨询', priority: 8 },
          { name: '技术支持', description: '技术问题解答', priority: 7 },
          { name: '客服', description: '一般客户服务', priority: 5 }
        ]);
        logger.info('Default skills created');
      }

      // 创建默认系统配置
      const configCount = await this.models.SystemConfig.count();
      if (configCount === 0) {
        await this.models.SystemConfig.bulkCreate([
          {
            key: 'max_concurrent_calls',
            value: 100,
            description: '最大并发呼叫数'
          },
          {
            key: 'call_timeout',
            value: 60,
            description: '呼叫超时时间（秒）'
          },
          {
            key: 'agent_acw_timeout',
            value: 30,
            description: '话后处理超时时间（秒）'
          },
          {
            key: 'queue_timeout',
            value: 300,
            description: '队列等待超时时间（秒）'
          }
        ]);
        logger.info('Default system config created');
      }
    } catch (error) {
      logger.error('Failed to create default data:', error);
    }
  }

  // ==================== 管理员相关方法 ====================

  async getAdminByUsername(username) {
    return await this.models.Admin.findOne({ where: { username } });
  }

  async createAdmin(data) {
    data.password = await bcrypt.hash(data.password, 10);
    return await this.models.Admin.create(data);
  }

  // ==================== 坐席相关方法 ====================

  async getAllAgents() {
    return await this.models.Agent.findAll({ where: { isActive: true } });
  }

  async getAgentByUsername(username) {
    return await this.models.Agent.findOne({ where: { username } });
  }

  async validateAgentPassword(agentId, password) {
    const agent = await this.models.Agent.findByPk(agentId);
    if (!agent) return false;
    return await bcrypt.compare(password, agent.password);
  }

  async createAgent(data) {
    data.password = await bcrypt.hash(data.password, 10);
    return await this.models.Agent.create(data);
  }

  async updateAgent(agentId, data) {
    if (data.password) {
      data.password = await bcrypt.hash(data.password, 10);
    }
    return await this.models.Agent.update(data, { where: { id: agentId } });
  }

  // ==================== 技能组相关方法 ====================

  async getAllSkills() {
    return await this.models.Skill.findAll();
  }

  async createSkill(data) {
    return await this.models.Skill.create(data);
  }

  // ==================== 外呼任务相关方法 ====================

  async saveCampaign(campaign) {
    return await this.models.Campaign.create(campaign);
  }

  async updateCampaign(campaign) {
    return await this.models.Campaign.update(campaign, { where: { id: campaign.id } });
  }

  async getPendingCampaigns() {
    return await this.models.Campaign.findAll({
      where: {
        status: ['pending', 'running', 'paused']
      }
    });
  }

  async getPendingPhones(campaignId) {
    const campaign = await this.models.Campaign.findByPk(campaignId);
    if (!campaign) return [];
    
    // 获取已完成的号码
    const completedCalls = await this.models.CallRecord.findAll({
      where: {
        campaignId,
        status: 'completed'
      },
      attributes: ['phoneNumber']
    });
    
    const completedNumbers = completedCalls.map(c => c.phoneNumber);
    
    // 返回未完成的号码
    return campaign.phoneList.filter(phone => {
      const number = phone.number || phone;
      return !completedNumbers.includes(number);
    });
  }

  // ==================== 通话记录相关方法 ====================

  async saveCallRecord(record) {
    return await this.models.CallRecord.create(record);
  }

  async updateCallRecord(callUuid, data) {
    return await this.models.CallRecord.update(data, { where: { callUuid } });
  }

  async getAgentCallHistory(agentId, startDate, endDate) {
    return await this.models.CallRecord.findAll({
      where: {
        agentId,
        startTime: {
          [Sequelize.Op.between]: [startDate, endDate]
        }
      },
      order: [['startTime', 'DESC']]
    });
  }

  // ==================== 活动日志相关方法 ====================

  async logAgentActivity(agentId, action, data = {}) {
    return await this.models.AgentActivityLog.create({
      agentId,
      action,
      data
    });
  }

  async getAgentActivityLog(agentId, startDate, endDate) {
    return await this.models.AgentActivityLog.findAll({
      where: {
        agentId,
        timestamp: {
          [Sequelize.Op.between]: [startDate, endDate]
        }
      },
      order: [['timestamp', 'ASC']]
    });
  }

  async logCallAssignment(callUuid, agentId, queueId) {
    return await this.logAgentActivity(agentId, 'call_assigned', {
      callUuid,
      queueId
    });
  }

  // ==================== 系统日志相关方法 ====================

  async logSystem(level, category, message, data = {}) {
    return await this.models.SystemLog.create({
      level,
      category,
      message,
      data
    });
  }

  async getSystemLogs(options = {}) {
    const { level, category, limit = 100, offset = 0 } = options;
    const where = {};
    
    if (level) where.level = level;
    if (category) where.category = category;
    
    return await this.models.SystemLog.findAll({
      where,
      limit,
      offset,
      order: [['timestamp', 'DESC']]
    });
  }

  // ==================== 系统配置相关方法 ====================

  async getSystemConfig(key = null) {
    if (key) {
      const config = await this.models.SystemConfig.findByPk(key);
      return config ? config.value : null;
    }
    
    const configs = await this.models.SystemConfig.findAll();
    const result = {};
    configs.forEach(config => {
      result[config.key] = config.value;
    });
    return result;
  }

  async updateSystemConfig(configs) {
    const promises = [];
    
    for (const [key, value] of Object.entries(configs)) {
      promises.push(
        this.models.SystemConfig.upsert({
          key,
          value
        })
      );
    }
    
    return await Promise.all(promises);
  }

  // ==================== 统计相关方法 ====================

  async getHistoricalStats(startDate, endDate, type = 'daily') {
    const calls = await this.models.CallRecord.findAll({
      where: {
        startTime: {
          [Sequelize.Op.between]: [startDate, endDate]
        }
      },
      attributes: [
        [Sequelize.fn('DATE', Sequelize.col('startTime')), 'date'],
        [Sequelize.fn('COUNT', Sequelize.col('id')), 'totalCalls'],
        [Sequelize.fn('SUM', Sequelize.literal("CASE WHEN status = 'answered' THEN 1 ELSE 0 END")), 'answeredCalls'],
        [Sequelize.fn('SUM', Sequelize.literal("CASE WHEN transferredToAgent = true THEN 1 ELSE 0 END")), 'transferredCalls'],
        [Sequelize.fn('AVG', Sequelize.col('duration')), 'avgDuration']
      ],
      group: [Sequelize.fn('DATE', Sequelize.col('startTime'))],
      order: [[Sequelize.fn('DATE', Sequelize.col('startTime')), 'ASC']]
    });

    return calls;
  }

  // ==================== 清理和维护方法 ====================

  async cleanOldLogs(daysToKeep = 30) {
    const cutoffDate = new Date();
    cutoffDate.setDate(cutoffDate.getDate() - daysToKeep);

    // 清理系统日志
    await this.models.SystemLog.destroy({
      where: {
        timestamp: {
          [Sequelize.Op.lt]: cutoffDate
        }
      }
    });

    // 清理坐席活动日志
    await this.models.AgentActivityLog.destroy({
      where: {
        timestamp: {
          [Sequelize.Op.lt]: cutoffDate
        }
      }
    });

    logger.info(`Cleaned logs older than ${daysToKeep} days`);
  }

  // 关闭数据库连接
  async close() {
    await this.sequelize.close();
    logger.info('Database connection closed');
  }
}

module.exports = DatabaseService;