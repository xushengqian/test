# 机器人外呼系统 - Robot Outbound Call System

一个功能完善的机器人外呼系统，支持AI自动外呼和坐席实时介入功能。系统采用前后端分离架构，提供实时通话监控、坐席介入、通话接管等核心功能。

## 🌟 核心功能

### 1. 机器人自动外呼
- 🤖 AI驱动的自动外呼系统
- 📞 批量外呼任务管理
- 🎯 智能话术配置
- 📊 实时通话状态监控

### 2. 坐席介入机制
- 👂 **实时监听**: 坐席可以实时监听机器人与客户的对话
- 💬 **辅助建议**: 坐席可以给机器人提供实时建议
- 🔄 **无缝接管**: 坐席可以随时接管通话，与客户直接交流
- 🤝 **灵活切换**: 支持在机器人和人工之间灵活切换

### 3. 实时通话管理
- 📱 WebRTC语音通话支持
- 💬 实时语音转文字
- 📝 通话记录自动保存
- 📊 通话质量监控

### 4. 数据分析与报表
- 📈 实时数据仪表盘
- 📊 通话统计分析
- 📉 坐席绩效报表
- 🎯 转化率分析

## 🛠 技术架构

### 后端技术栈
- **Node.js + Express**: RESTful API服务
- **Socket.io**: 实时双向通信
- **MongoDB**: 数据持久化存储
- **Redis**: 会话管理和缓存
- **JWT**: 身份认证和授权
- **WebRTC**: 语音通话支持

### 前端技术栈
- **React 18**: 用户界面框架
- **TypeScript**: 类型安全
- **Material-UI**: UI组件库
- **Socket.io-client**: 实时通信
- **Chart.js**: 数据可视化
- **React Router**: 路由管理

## 📁 项目结构

```
robot-outbound-call-system/
├── backend/                 # 后端服务
│   ├── models/             # 数据模型
│   │   ├── Agent.js       # 坐席模型
│   │   ├── Call.js        # 通话记录模型
│   │   ├── Customer.js    # 客户模型
│   │   └── Campaign.js    # 活动模型
│   ├── routes/             # API路由
│   │   ├── auth.js        # 认证路由
│   │   ├── calls.js       # 通话管理
│   │   ├── agents.js      # 坐席管理
│   │   ├── customers.js   # 客户管理
│   │   └── campaigns.js   # 活动管理
│   ├── services/           # 业务服务
│   │   └── CallManager.js # 通话管理核心逻辑
│   ├── middleware/         # 中间件
│   │   └── auth.js        # 认证中间件
│   ├── server.js          # 服务入口
│   └── seed.js            # 数据初始化脚本
├── frontend/               # 前端应用
│   ├── src/
│   │   ├── components/    # 通用组件
│   │   ├── contexts/      # React上下文
│   │   ├── pages/         # 页面组件
│   │   └── App.tsx        # 应用入口
│   └── package.json
├── docker-compose.yml      # Docker编排配置
├── Dockerfile.backend      # 后端Docker镜像
├── Dockerfile.frontend     # 前端Docker镜像
└── README.md              # 项目文档
```

## 🚀 快速开始

### 环境要求
- Node.js 16+
- MongoDB 5.0+
- Redis 6.0+
- npm 或 yarn

### 本地开发

1. **克隆项目**
```bash
git clone <repository-url>
cd robot-outbound-call-system
```

2. **安装依赖**
```bash
# 安装后端依赖
npm install

# 安装前端依赖
cd frontend
npm install
```

3. **配置环境变量**
```bash
# 复制环境变量模板
cp .env.example .env

# 编辑.env文件，配置数据库连接等信息
```

4. **启动MongoDB和Redis**
```bash
# 使用Docker启动
docker-compose up -d mongodb redis

# 或使用本地安装的服务
```

5. **初始化数据库**
```bash
node backend/seed.js
```

6. **启动服务**
```bash
# 启动后端服务
npm run server

# 新终端窗口，启动前端
cd frontend
npm start
```

7. **访问系统**
- 前端界面: http://localhost:3000
- 后端API: http://localhost:5000

### Docker部署

1. **构建并启动所有服务**
```bash
docker-compose up -d
```

2. **初始化数据库**
```bash
docker-compose exec backend node backend/seed.js
```

3. **访问系统**
- 系统界面: http://localhost
- API文档: http://localhost/api

## 👤 测试账号

系统预置了以下测试账号：

| 角色 | 用户名 | 密码 | 权限说明 |
|------|--------|------|----------|
| 管理员 | admin | admin123 | 全部权限 |
| 主管 | supervisor | super123 | 管理坐席、查看报表 |
| 坐席1 | agent1 | agent123 | 接听电话、客户服务 |
| 坐席2 | agent2 | agent123 | 接听电话、客户服务 |

## 📋 功能使用说明

### 坐席介入流程

1. **登录系统**: 使用坐席账号登录
2. **进入呼叫中心**: 点击左侧菜单"呼叫中心"
3. **查看活动通话**: 在左侧面板查看所有进行中的通话
4. **选择介入方式**:
   - 🔊 **监听**: 只听不说，了解对话内容
   - 💡 **介入**: 可以给机器人提供建议
   - 🎯 **接管**: 完全接管通话，直接与客户交流
5. **释放控制**: 可以随时将通话转回给机器人

### 通话管理

1. **发起外呼**: 在活动管理中创建外呼任务
2. **实时监控**: 在仪表盘查看实时通话数据
3. **历史记录**: 查看所有通话记录和录音
4. **数据分析**: 查看通话统计和转化率

## 🔧 配置说明

### 环境变量配置

```env
# 服务器配置
PORT=5000
NODE_ENV=development

# 数据库配置
MONGODB_URI=mongodb://localhost:27017/robot_call_system

# JWT配置
JWT_SECRET=your_jwt_secret_key

# Twilio配置（电话服务）
TWILIO_ACCOUNT_SID=your_twilio_sid
TWILIO_AUTH_TOKEN=your_twilio_token
TWILIO_PHONE_NUMBER=+1234567890

# WebRTC配置
STUN_SERVER=stun:stun.l.google.com:19302
TURN_SERVER=turn:your.turn.server:3478

# AI服务配置
TTS_API_URL=http://localhost:3001/api/tts
STT_API_URL=http://localhost:3001/api/stt
```

### 系统配置

- **并发通话数**: 在Campaign设置中配置
- **重试策略**: 可配置最大重试次数和间隔
- **工作时间**: 支持配置外呼工作时间段
- **介入触发**: 可配置自动请求人工介入的条件

## 📊 API文档

### 认证接口

| 方法 | 路径 | 描述 |
|------|------|------|
| POST | /api/auth/login | 用户登录 |
| POST | /api/auth/register | 用户注册 |
| POST | /api/auth/logout | 用户登出 |
| GET | /api/auth/verify | 验证Token |

### 通话管理

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | /api/calls | 获取通话列表 |
| GET | /api/calls/:id | 获取通话详情 |
| POST | /api/calls/initiate | 发起外呼 |
| PATCH | /api/calls/:id/status | 更新通话状态 |

### WebSocket事件

| 事件名 | 方向 | 描述 |
|--------|------|------|
| agent:login | Client→Server | 坐席登录 |
| agent:intervene | Client→Server | 坐席介入 |
| agent:takeover | Client→Server | 坐席接管 |
| call:new | Server→Client | 新通话通知 |
| call:transcript | Server→Client | 实时对话内容 |

## 🔒 安全性

- JWT Token认证
- 密码加密存储（bcrypt）
- API请求限流
- XSS/CSRF防护
- SSL/TLS加密传输

## 📈 性能优化

- Redis缓存热点数据
- MongoDB索引优化
- 前端代码分割和懒加载
- WebSocket连接池管理
- CDN静态资源加速

## 🤝 贡献指南

欢迎提交Issue和Pull Request！

1. Fork项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 提交Pull Request

## 📄 许可证

本项目采用MIT许可证 - 详见 [LICENSE](LICENSE) 文件

## 📞 联系方式

如有问题或建议，请通过以下方式联系：

- 提交Issue
- 发送邮件至: support@example.com

## 🙏 致谢

感谢所有为本项目做出贡献的开发者！

---

**注意**: 这是一个演示系统，生产环境使用前请：
1. 更改所有默认密码和密钥
2. 配置真实的语音服务API
3. 实施适当的安全措施
4. 进行充分的性能测试