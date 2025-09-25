# 智能外呼系统 - Robot Call Center System

一个功能完整的机器人外呼系统，支持实时语音交互显示和人工坐席接入功能。

## 🌟 主要功能

### 核心功能
- 🤖 **AI 自动外呼** - 基于预设脚本的智能语音交互
- 📞 **实时通话监控** - 实时显示通话内容和转写文本
- 👥 **人工接入** - 坐席可随时接管AI通话
- 📊 **数据统计** - 通话数据统计和分析
- 🎙️ **录音管理** - 通话录音和回放功能
- 📝 **实时转写** - 语音实时转换为文字

### 技术特点
- **实时通信**: WebSocket 实现低延迟通信
- **语音识别**: 集成 Google Cloud Speech-to-Text
- **电话系统**: Twilio API 集成
- **分布式架构**: Redis 支持多实例部署
- **响应式界面**: Material-UI 美观现代的用户界面

## 🚀 快速开始

### 环境要求
- Node.js 18+
- Redis (可选，用于生产环境)
- Twilio 账号 (用于真实通话)
- Google Cloud 账号 (用于语音识别)

### 安装步骤

1. **克隆项目**
```bash
git clone <repository-url>
cd robot-call-center
```

2. **安装依赖**
```bash
# 安装后端依赖
npm install

# 安装前端依赖
cd client
npm install
cd ..
```

3. **配置环境变量**
```bash
cp .env.example .env
# 编辑 .env 文件，配置必要的 API 密钥
```

4. **启动开发环境**
```bash
# 启动后端和前端
npm run dev
```

访问 http://localhost:5173 查看系统界面

### Docker 部署

```bash
# 使用 Docker Compose 启动所有服务
docker-compose up -d
```

## 📁 项目结构

```
/workspace/
├── server/                 # 后端服务
│   ├── index.js           # 主入口文件
│   ├── routes/            # API 路由
│   │   └── calls.js       # 通话相关接口
│   └── services/          # 业务服务
│       ├── callManager.js     # 通话管理
│       ├── twilioService.js   # Twilio 集成
│       ├── transcription.js   # 语音转写
│       └── agentManager.js    # 坐席管理
├── client/                # 前端应用
│   ├── src/
│   │   ├── pages/        # 页面组件
│   │   │   ├── Dashboard.jsx      # 监控面板
│   │   │   ├── CallMonitor.jsx    # 通话监控
│   │   │   ├── NewCall.jsx        # 发起呼叫
│   │   │   ├── CallHistory.jsx    # 通话记录
│   │   │   └── AgentLogin.jsx     # 坐席登录
│   │   ├── components/   # 通用组件
│   │   ├── contexts/     # React Context
│   │   └── stores/       # 状态管理
├── docker-compose.yml    # Docker 编排配置
└── README.md            # 项目文档
```

## 🔧 配置说明

### Twilio 配置
1. 注册 Twilio 账号
2. 获取 Account SID 和 Auth Token
3. 购买电话号码
4. 配置 Webhook URL

### Google Cloud Speech 配置
1. 创建 Google Cloud 项目
2. 启用 Speech-to-Text API
3. 创建服务账号密钥
4. 设置 GOOGLE_APPLICATION_CREDENTIALS

## 💡 使用指南

### 坐席登录
系统提供了三个演示账号：
- **张三** (agent001) - 销售坐席
- **李四** (agent002) - 技术支持
- **王五** (agent003) - VIP 客服

密码统一为: `demo123`

### 发起呼叫
1. 登录系统后，点击"发起呼叫"
2. 输入目标电话号码
3. 选择对话脚本模板
4. 点击"发起呼叫"按钮

### 监控通话
1. 在监控面板查看所有活跃通话
2. 点击"查看详情"进入通话监控页面
3. 实时查看对话内容和转写文本
4. 可随时点击"人工接入"接管通话

### 人工接入
1. 在通话监控页面点击"人工接入"
2. 系统将自动桥接坐席到通话中
3. 坐席可以直接与客户对话
4. 可通过文本框发送消息（TTS）

## 🔍 API 接口

### 认证接口
- `POST /api/calls/agent/login` - 坐席登录
- `POST /api/calls/agent/logout` - 坐席登出

### 通话管理
- `POST /api/calls/initiate` - 发起呼叫
- `GET /api/calls/active` - 获取活跃通话
- `GET /api/calls/:callId` - 获取通话详情
- `POST /api/calls/:callId/takeover` - 人工接入
- `POST /api/calls/:callId/end` - 结束通话

### WebSocket 事件
- `agent:authenticate` - 坐席认证
- `call:monitor` - 监控通话
- `call:takeover` - 接管通话
- `call:transcription` - 实时转写

## 🚨 注意事项

1. **开发环境**: 系统在没有配置 Twilio 和 Google Cloud 的情况下会使用模拟数据
2. **生产环境**: 请确保所有 API 密钥都已正确配置
3. **安全性**: 生产环境请使用 HTTPS 和 WSS
4. **录音合规**: 请遵守当地法律关于电话录音的规定

## 📊 性能优化

- 使用 Redis 缓存活跃通话数据
- WebSocket 连接池管理
- 前端组件懒加载
- 语音流分段处理

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

## 📄 许可证

MIT License

## 📞 技术支持

如有问题，请提交 Issue 或联系技术支持团队。

---

**注意**: 本系统仅供学习和演示用途。在生产环境使用前，请确保：
- 完成所有安全配置
- 遵守相关法律法规
- 配置适当的错误处理和日志记录
- 进行充分的测试