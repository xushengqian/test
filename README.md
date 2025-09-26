# FreeSWITCH 机器人外呼系统

一个基于FreeSWITCH的智能外呼系统，支持机器人自动外呼、语音识别、人工坐席接入等功能。

## 功能特点

### 核心功能
- **自动外呼**: 批量自动拨打电话，支持任务调度和号码管理
- **机器人对话**: 集成语音识别(ASR)和语音合成(TTS)，实现智能对话
- **人工接入**: 支持在通话过程中无缝转接人工坐席
- **坐席管理**: 完整的坐席状态管理、技能组分配、队列管理
- **实时监控**: 实时查看通话状态、坐席状态、系统统计
- **任务管理**: 创建、暂停、恢复、取消外呼任务
- **报表分析**: 详细的通话记录、坐席绩效、任务统计报表

### 技术特性
- 基于Node.js + Express构建
- 使用FreeSWITCH作为电话交换核心
- WebSocket实时通信
- Redis缓存和会话管理
- MySQL数据持久化
- Docker容器化部署
- RESTful API设计

## 系统架构

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   Web前端界面    │────▶│   Node.js应用    │────▶│   FreeSWITCH    │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                               │                          │
                               ▼                          ▼
                        ┌─────────────┐           ┌─────────────┐
                        │    MySQL    │           │  电话网络    │
                        └─────────────┘           └─────────────┘
                               │
                               ▼
                        ┌─────────────┐
                        │    Redis    │
                        └─────────────┘
```

## 快速开始

### 前置要求

- Docker & Docker Compose
- Node.js 16+ (开发环境)
- MySQL 8.0+
- Redis 6.0+
- FreeSWITCH 1.10+

### 使用Docker部署（推荐）

1. 克隆项目
```bash
git clone <repository-url>
cd fs-robot-outbound
```

2. 配置环境变量
```bash
cp .env.example .env
# 编辑.env文件，填入实际的配置信息
```

3. 运行部署脚本
```bash
chmod +x scripts/deploy.sh
./scripts/deploy.sh
```

4. 访问系统
- Web界面: http://localhost
- API文档: http://localhost:3000/api/docs
- 默认账号: admin / admin123

### 手动安装

1. 安装依赖
```bash
npm install
```

2. 配置数据库
```sql
CREATE DATABASE fs_robot_call;
```

3. 配置环境变量
```bash
cp .env.example .env
# 编辑.env文件
```

4. 启动服务
```bash
# 开发模式
npm run dev

# 生产模式
npm start
```

## 配置说明

### FreeSWITCH配置
```env
FS_HOST=127.0.0.1          # FreeSWITCH服务器地址
FS_PORT=8021               # Event Socket端口
FS_PASSWORD=ClueCon        # Event Socket密码
FS_SIP_PROFILE=external    # SIP配置文件
```

### 语音服务配置（阿里云）
```env
ALI_ACCESS_KEY_ID=your_key_id
ALI_ACCESS_KEY_SECRET=your_secret
ALI_APP_KEY=your_app_key
ALI_TTS_APP_KEY=your_tts_key
```

### 外呼配置
```env
OUTBOUND_CALLER_ID=+8613800138000  # 外呼显示号码
OUTBOUND_GATEWAY=default_gateway    # SIP网关
MAX_CONCURRENT_CALLS=100           # 最大并发呼叫数
```

## 使用指南

### 1. 创建外呼任务

1. 登录系统后，点击"外呼任务"
2. 点击"创建任务"按钮
3. 填写任务信息：
   - 任务名称
   - 话术脚本
   - 号码列表（支持CSV导入）
   - 执行时间
4. 点击"创建"

### 2. 坐席登录

1. 使用坐席账号登录
2. 点击"坐席签入"
3. 输入分机号和密码
4. 系统自动分配来电

### 3. 人工接入流程

当客户在机器人对话中：
- 说出"人工"、"客服"等关键词
- 或按键盘"0"

系统会自动：
1. 查找空闲坐席
2. 转接到人工坐席
3. 如无空闲坐席，进入等待队列

### 4. 监控和报表

- **实时监控**: 查看当前通话、坐席状态、队列情况
- **历史记录**: 查询通话记录、录音文件
- **统计报表**: 生成各类统计分析报表

## API接口

### 认证
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123",
  "type": "admin"
}
```

### 创建外呼任务
```http
POST /api/campaigns
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "营销活动",
  "phoneList": ["13800138000", "13800138001"],
  "script": "您好，这里是...",
  "startTime": "2024-01-01T09:00:00",
  "endTime": "2024-01-01T18:00:00"
}
```

### 发起单个外呼
```http
POST /api/calls/outbound
Authorization: Bearer {token}
Content-Type: application/json

{
  "phoneNumber": "13800138000",
  "metadata": {
    "customer_id": "12345"
  }
}
```

## 开发指南

### 项目结构
```
├── src/
│   ├── core/           # 核心模块
│   │   └── freeswitch.js
│   ├── services/       # 业务服务
│   │   ├── callManager.js
│   │   ├── agentManager.js
│   │   ├── speechService.js
│   │   └── database.js
│   ├── api/            # API路由
│   │   └── routes.js
│   └── index.js        # 应用入口
├── public/             # 前端文件
│   ├── index.html
│   ├── css/
│   └── js/
├── scripts/            # 部署脚本
├── docker-compose.yml  # Docker编排
└── package.json
```

### 扩展开发

#### 添加新的语音服务提供商
```javascript
// src/services/speechService.js
class CustomSpeechService extends EventEmitter {
  async startRealtimeASR(callUuid, audioStream) {
    // 实现ASR逻辑
  }
  
  async textToSpeech(text, options) {
    // 实现TTS逻辑
  }
}
```

#### 自定义对话流程
```javascript
// src/core/freeswitch.js
async generateResponse(userInput, callInfo) {
  // 添加自定义对话逻辑
  // 可以集成Rasa、Dialogflow等
}
```

## 故障排除

### FreeSWITCH连接失败
- 检查FreeSWITCH是否运行
- 验证Event Socket配置
- 确认防火墙规则

### 语音识别不工作
- 检查API密钥配置
- 确认音频格式(8kHz, 16bit, PCM)
- 查看语音服务日志

### 坐席无法接收呼叫
- 确认坐席状态为"可用"
- 检查分机注册状态
- 验证队列配置

## 性能优化

- **并发控制**: 调整MAX_CONCURRENT_CALLS参数
- **数据库优化**: 添加适当的索引
- **Redis缓存**: 缓存热点数据
- **负载均衡**: 使用Nginx进行负载均衡

## 安全建议

1. 修改默认密码
2. 使用HTTPS部署
3. 配置防火墙规则
4. 定期备份数据
5. 启用日志审计

## 许可证

MIT License

## 支持与帮助

- 提交Issue: [GitHub Issues]
- 技术文档: [Wiki]
- 联系邮箱: support@example.com

## 更新日志

### v1.0.0 (2024-01-01)
- 初始版本发布
- 实现基础外呼功能
- 支持人工接入
- Web管理界面