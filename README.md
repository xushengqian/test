# FS机器人外呼系统 - 支持人工接入

[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Python](https://img.shields.io/badge/python-3.11+-green.svg)](https://python.org)
[![Vue](https://img.shields.io/badge/vue-3.x-green.svg)](https://vuejs.org)
[![Docker](https://img.shields.io/badge/docker-ready-blue.svg)](https://docker.com)

一个基于FreeSwitch的智能外呼系统，支持机器人自动外呼和人工接入功能。系统采用现代化的微服务架构，提供完整的外呼管理、坐席管理和数据分析功能。

## ✨ 核心特性

### 🤖 智能机器人外呼
- **自动拨号**: 支持批量外呼任务，自动拨打客户电话
- **语音识别**: 集成ASR服务，实时识别客户语音
- **智能对话**: 基于NLP的意图识别和智能回复
- **语音合成**: 支持TTS服务，自然语音播报

### 👥 人工接入系统
- **智能转接**: 根据对话内容自动判断是否需要转人工
- **坐席管理**: 支持多坐席在线管理和状态监控
- **技能路由**: 根据坐席技能智能分配通话
- **队列管理**: 支持通话排队和等待时间预估

### 📊 管理监控
- **实时监控**: 实时显示通话状态和系统负载
- **数据统计**: 详细的通话数据分析和报表
- **录音管理**: 自动录音和通话记录管理
- **WebSocket**: 实时推送系统事件和状态更新

### 🔧 技术特性
- **微服务架构**: 模块化设计，易于扩展和维护
- **容器化部署**: 基于Docker的一键部署
- **高可用性**: 支持集群部署和负载均衡
- **RESTful API**: 完整的API接口，支持第三方集成

## 🏗️ 系统架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   前端管理界面   │    │   坐席工作台     │    │   监控大屏       │
│   (Vue.js)      │    │   (WebSocket)   │    │   (Dashboard)   │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌─────────────┴─────────────┐
                    │      Nginx 反向代理        │
                    └─────────────┬─────────────┘
                                 │
                    ┌─────────────┴─────────────┐
                    │     FastAPI 后端服务      │
                    │   ┌─────────────────────┐ │
                    │   │   通话管理服务       │ │
                    │   │   坐席管理服务       │ │
                    │   │   机器人服务        │ │
                    │   │   WebSocket服务     │ │
                    │   └─────────────────────┘ │
                    └─────────────┬─────────────┘
                                 │
          ┌──────────────────────┼──────────────────────┐
          │                      │                      │
┌─────────┴───────┐    ┌─────────┴───────┐    ┌─────────┴───────┐
│   FreeSwitch    │    │   PostgreSQL    │    │     Redis       │
│   (通信引擎)     │    │   (数据存储)     │    │   (缓存队列)     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## 🚀 快速开始

### 环境要求

- **操作系统**: Linux (推荐Ubuntu 20.04+)
- **Docker**: 20.10+
- **Docker Compose**: 2.0+
- **内存**: 8GB+
- **存储**: 100GB+

### 一键部署

1. **克隆项目**
```bash
git clone <repository-url>
cd fs-robot-call-system
```

2. **配置环境**
```bash
cp .env.example .env
# 编辑.env文件，配置数据库密码、SIP网关等参数
vim .env
```

3. **启动系统**
```bash
./scripts/start.sh
```

4. **访问系统**
- 管理界面: http://localhost
- API文档: http://localhost:8000/docs
- 系统状态: http://localhost:8000/health

### 手动部署

如果需要手动部署，请参考 [部署文档](docs/deployment.md)

## 📖 文档

- [部署指南](docs/deployment.md) - 详细的部署和配置说明
- [API文档](docs/api.md) - 完整的API接口文档
- [架构设计](docs/architecture.md) - 系统架构和设计说明
- [开发指南](docs/development.md) - 开发环境搭建和开发规范

## 🔧 配置说明

### FreeSwitch配置

系统需要配置SIP网关来连接电话运营商：

```xml
<!-- freeswitch/conf/sip_profiles.xml -->
<gateway name="outbound">
  <param name="username" data="your_sip_username"/>
  <param name="password" data="your_sip_password"/>
  <param name="realm" data="your_sip_provider.com"/>
  <param name="proxy" data="your_sip_provider.com"/>
  <param name="register" data="true"/>
</gateway>
```

### 机器人配置

在 `.env` 文件中配置AI服务：

```bash
# OpenAI配置
OPENAI_API_KEY=sk-your-openai-api-key

# Azure语音服务配置
AZURE_SPEECH_KEY=your-azure-speech-key
AZURE_SPEECH_REGION=eastasia
```

### 坐席配置

通过管理界面或API创建坐席：

```bash
curl -X POST http://localhost:8000/api/agent/create \
  -H "Content-Type: application/json" \
  -d '{
    "agent_id": "agent_001",
    "name": "张三",
    "extension": "1001",
    "department": "销售部",
    "skills": "[\"销售\", \"产品咨询\"]"
  }'
```

## 🎯 使用场景

### 销售外呼
- 产品推广和营销活动
- 客户回访和满意度调研
- 潜在客户开发和筛选

### 客户服务
- 服务到期提醒
- 账单催收和续费提醒
- 客户关怀和维护

### 通知服务
- 重要通知和公告
- 预约提醒和确认
- 活动邀请和报名

## 📊 系统监控

### 实时监控指标
- 活跃通话数量
- 坐席在线状态
- 系统负载情况
- 队列等待长度

### 统计分析
- 外呼成功率
- 人工转接率
- 平均通话时长
- 客户意向分析

### 告警机制
- 系统异常告警
- 通话质量监控
- 坐席状态异常
- 队列超时告警

## 🔒 安全特性

- **访问控制**: 基于角色的权限管理
- **数据加密**: 敏感数据加密存储
- **通信安全**: HTTPS/WSS加密传输
- **审计日志**: 完整的操作日志记录

## 🚦 系统状态

### 服务状态检查
```bash
# 检查所有服务状态
docker-compose ps

# 检查系统健康状态
curl http://localhost:8000/health
```

### 日志查看
```bash
# 查看所有服务日志
docker-compose logs -f

# 查看特定服务日志
docker-compose logs -f backend
docker-compose logs -f freeswitch
```

## 🛠️ 开发

### 本地开发环境

1. **后端开发**
```bash
cd backend
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload
```

2. **前端开发**
```bash
cd frontend
npm install
npm run dev
```

### 代码规范
- Python: 遵循PEP 8规范
- JavaScript: 使用ESLint和Prettier
- 提交信息: 遵循Conventional Commits

## 🤝 贡献

欢迎提交Issue和Pull Request来改进项目！

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 📞 支持

如果您在使用过程中遇到问题，可以通过以下方式获取帮助：

- 📧 邮件支持: support@example.com
- 💬 在线客服: 工作日 9:00-18:00
- 📖 文档中心: [docs.example.com](https://docs.example.com)
- 🐛 问题反馈: [GitHub Issues](https://github.com/example/fs-robot-call/issues)

## 🎉 致谢

感谢以下开源项目的支持：

- [FreeSwitch](https://freeswitch.org/) - 强大的通信平台
- [FastAPI](https://fastapi.tiangolo.com/) - 现代化的Python Web框架
- [Vue.js](https://vuejs.org/) - 渐进式JavaScript框架
- [Element Plus](https://element-plus.org/) - Vue 3组件库
- [PostgreSQL](https://postgresql.org/) - 强大的关系型数据库
- [Redis](https://redis.io/) - 高性能缓存数据库

---

⭐ 如果这个项目对您有帮助，请给我们一个Star！