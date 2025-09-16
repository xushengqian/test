# FreeSWITCH 人工外呼实时音转文系统

一个基于FreeSWITCH的智能外呼系统，支持实时语音转写功能。

## 功能特性

- 🎯 **智能外呼管理**: 通过Web界面发起和管理外呼
- 🎙️ **实时语音转写**: 使用Whisper AI进行实时语音识别
- 📊 **通话监控**: 实时查看通话状态和转写内容
- 🔄 **WebSocket通信**: 实时双向通信
- 🐳 **Docker部署**: 一键部署完整系统

## 系统架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Web界面       │    │   FastAPI服务   │    │   FreeSWITCH    │
│   (前端)        │◄──►│   (后端API)     │◄──►│   (电话系统)    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                │
                                ▼
                       ┌─────────────────┐
                       │   Whisper AI    │
                       │   (语音识别)    │
                       └─────────────────┘
```

## 快速开始

### 1. 克隆项目
```bash
git clone <repository-url>
cd freeswitch-call-transcription
```

### 2. 启动服务
```bash
docker-compose up -d
```

### 3. 访问系统
打开浏览器访问: http://localhost:8000

## 配置说明

### FreeSWITCH配置
- 端口: 5060 (SIP), 8021 (ESL)
- 认证密码: ClueCon
- 录音格式: WAV, 16kHz, 单声道

### 数据库配置
- PostgreSQL 15
- 数据库名: freeswitch_calls
- 用户名: freeswitch
- 密码: freeswitch123

### Redis配置
- 端口: 6379
- 用于缓存和任务队列

## API接口

### 发起外呼
```http
POST /api/calls/start
Content-Type: application/x-www-form-urlencoded

phone_number=13800138000&agent_id=agent001
```

### 结束通话
```http
POST /api/calls/{call_id}/end
```

### 获取转写内容
```http
GET /api/calls/{call_id}/transcriptions
```

### WebSocket连接
```javascript
const ws = new WebSocket('ws://localhost:8000/ws/{call_id}');
ws.onmessage = function(event) {
    const data = JSON.parse(event.data);
    console.log('转写内容:', data.text);
};
```

## 技术栈

- **后端**: FastAPI, SQLAlchemy, WebSocket
- **前端**: HTML5, CSS3, JavaScript
- **数据库**: PostgreSQL, Redis
- **语音识别**: OpenAI Whisper
- **电话系统**: FreeSWITCH
- **部署**: Docker, Docker Compose

## 开发说明

### 项目结构
```
├── app/                    # 应用核心代码
│   ├── services/          # 服务层
│   ├── models.py          # 数据模型
│   └── database.py        # 数据库配置
├── freeswitch/            # FreeSWITCH配置
│   ├── conf/              # 配置文件
│   └── scripts/           # Lua脚本
├── templates/             # HTML模板
├── static/               # 静态资源
├── main.py               # 应用入口
├── requirements.txt      # Python依赖
└── docker-compose.yml    # Docker配置
```

### 环境变量
```bash
DATABASE_URL=postgresql://freeswitch:freeswitch123@postgres:5432/freeswitch_calls
REDIS_URL=redis://redis:6379
FREESWITCH_HOST=freeswitch
FREESWITCH_PORT=8021
```

## 故障排除

### 常见问题

1. **FreeSWITCH连接失败**
   - 检查FreeSWITCH服务是否启动
   - 验证ESL端口8021是否开放
   - 确认认证密码是否正确

2. **语音转写不工作**
   - 检查Whisper模型是否正确加载
   - 验证音频文件格式和采样率
   - 查看WebSocket连接状态

3. **外呼失败**
   - 检查SIP网关配置
   - 验证电话号码格式
   - 查看FreeSWITCH日志

### 日志查看
```bash
# 查看应用日志
docker-compose logs app

# 查看FreeSWITCH日志
docker-compose logs freeswitch

# 查看数据库日志
docker-compose logs postgres
```

## 许可证

MIT License

## 贡献

欢迎提交Issue和Pull Request来改进这个项目。

## 联系方式

如有问题，请通过以下方式联系：
- 邮箱: your-email@example.com
- GitHub: https://github.com/your-username/freeswitch-call-transcription