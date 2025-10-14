# FreeSwitch 机器人呼出系统

一个基于 FreeSwitch 的智能机器人呼出系统，支持自动外呼、意图识别和人工转接功能。

## 功能特性

### 🤖 机器人呼出
- **自动外呼**: 支持批量自动外呼任务
- **智能对话**: 基于规则的对话引擎
- **语音识别**: 支持中文语音识别 (ASR)
- **语音合成**: 支持中文语音合成 (TTS)
- **通话录音**: 自动录制通话内容

### 🧠 意图识别
- **多意图识别**: 支持问候、投诉、咨询、转人工等多种意图
- **情感分析**: 识别用户情感状态（愤怒、满意、困惑等）
- **上下文理解**: 基于对话历史的上下文分析
- **转人工判断**: 智能判断何时需要转接人工客服

### 👥 人工转接
- **客服管理**: 支持多客服在线状态管理
- **技能匹配**: 根据客服技能自动分配
- **队列管理**: VIP 客户优先级队列
- **负载均衡**: 智能分配客服负载

### 📊 监控统计
- **实时监控**: 系统运行状态实时监控
- **统计报表**: 呼出成功率、转接率等统计
- **日志记录**: 详细的操作日志和通话记录
- **健康检查**: 自动检测系统健康状态

## 系统架构

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   机器人呼出    │    │   意图识别服务   │    │   人工转接服务   │
│  OutboundCaller │◄──►│  IntentService  │◄──►│ TransferService │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   任务数据库    │    │   对话会话存储   │    │   客服状态管理   │
│   SQLite DB     │    │   Memory Store  │    │   SQLite DB     │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │                       │                       │
         └───────────────────────┼───────────────────────┘
                                 ▼
                    ┌─────────────────┐
                    │   FreeSwitch    │
                    │   电话交换系统   │
                    └─────────────────┘
```

## 目录结构

```
freeswitch-robot-transfer-to-agent/
├── README.md                           # 项目说明文档
├── requirements.txt                    # Python 依赖包
├── main.py                            # 主程序入口
├── start.sh                           # 启动脚本
├── stop.sh                            # 停止脚本
├── config/                            # 配置文件目录
│   ├── outbound_config.json          # 呼出配置
│   └── transfer_config.json          # 转接配置
├── src/                               # 源代码目录
│   ├── robot/                         # 机器人模块
│   │   └── outbound_caller.py        # 呼出控制器
│   ├── intent-recognition/            # 意图识别模块
│   │   └── intent_service.py         # 意图识别服务
│   └── agent-transfer/                # 转接模块
│       └── transfer_service.py       # 转接服务
├── freeswitch-config/                 # FreeSwitch 配置
│   ├── dialplan/                      # 拨号计划
│   │   └── robot_outbound.xml        # 机器人呼出拨号计划
│   └── scripts/                       # JavaScript 脚本
│       ├── robot_conversation.js     # 机器人对话脚本
│       ├── intent_recognition.js     # 意图识别脚本
│       ├── tts_handler.js            # TTS 处理脚本
│       ├── asr_handler.js            # ASR 处理脚本
│       ├── find_available_agent.js   # 查找可用客服脚本
│       └── voicemail_handler.js      # 语音信箱脚本
└── logs/                              # 日志目录
    ├── main.log                       # 主程序日志
    ├── outbound_caller.log           # 呼出服务日志
    ├── transfer_service.log          # 转接服务日志
    ├── calls.db                      # 通话记录数据库
    ├── agents.db                     # 客服信息数据库
    └── transfer_queue.db             # 转接队列数据库
```

## 快速开始

### 环境要求

- **Python**: 3.7 或更高版本
- **FreeSwitch**: 1.10 或更高版本
- **操作系统**: Linux (推荐 Ubuntu 18.04+)
- **内存**: 最少 2GB RAM
- **存储**: 最少 10GB 可用空间

### 安装步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd freeswitch-robot-transfer-to-agent
   ```

2. **自动安装和启动**
   ```bash
   # 设置环境并启动系统
   ./start.sh
   
   # 或者交互模式启动
   ./start.sh --interactive
   ```

3. **手动安装**
   ```bash
   # 创建虚拟环境
   python3 -m venv venv
   source venv/bin/activate
   
   # 安装依赖
   pip install -r requirements.txt
   
   # 创建必要目录
   mkdir -p logs config models storage/voicemail
   
   # 启动系统
   python3 main.py
   ```

### FreeSwitch 配置

1. **复制拨号计划**
   ```bash
   sudo cp freeswitch-config/dialplan/robot_outbound.xml /usr/local/freeswitch/conf/dialplan/
   ```

2. **复制脚本文件**
   ```bash
   sudo cp freeswitch-config/scripts/*.js /usr/local/freeswitch/scripts/
   ```

3. **重载 FreeSwitch 配置**
   ```bash
   fs_cli -x "reloadxml"
   ```

## 使用说明

### 添加呼出任务

#### 通过 API 添加
```python
from src.robot.outbound_caller import OutboundCaller

caller = OutboundCaller()

# 添加普通任务
task_id = caller.add_call_task(
    customer_phone="13800138000",
    priority=5,
    campaign_id="CAMPAIGN_001",
    customer_data={
        "name": "张三",
        "level": "VIP",
        "product": "智能客服系统"
    }
)
```

#### 通过命令行添加
```bash
# 启动交互模式
./start.sh --interactive

# 在交互模式中输入
add
# 然后输入电话号码
```

### 客服管理

#### 设置客服在线状态
```python
from src.agent_transfer.transfer_service import TransferService, AgentStatus

service = TransferService()

# 设置客服上线
service.agent_manager.update_agent_status("agent_001", AgentStatus.ONLINE)

# 设置客服忙碌
service.agent_manager.update_agent_status("agent_001", AgentStatus.BUSY)

# 设置客服离线
service.agent_manager.update_agent_status("agent_001", AgentStatus.OFFLINE)
```

### 监控系统状态

#### 查看系统状态
```bash
# 检查系统运行状态
./stop.sh --status

# 查看实时日志
tail -f logs/main.log
```

#### 获取统计信息
```python
# 获取呼出统计
outbound_stats = caller.get_statistics()

# 获取意图识别统计
intent_stats = intent_service.get_statistics()

# 获取转接服务统计
transfer_stats = transfer_service.get_service_statistics()
```

## 配置说明

### 呼出配置 (config/outbound_config.json)

```json
{
  "freeswitch_host": "localhost",
  "freeswitch_port": 8080,
  "max_concurrent_calls": 5,
  "working_hours": {
    "start": "09:00",
    "end": "18:00"
  },
  "rate_limit": {
    "calls_per_minute": 10,
    "calls_per_hour": 100
  }
}
```

### 转接配置 (config/transfer_config.json)

```json
{
  "transfer": {
    "max_wait_time": 300,
    "retry_interval": 30,
    "max_retries": 3
  },
  "queue": {
    "vip_priority": true,
    "max_queue_size": 50
  },
  "agents": {
    "default_max_calls": 3,
    "skill_matching": true
  }
}
```

## 意图识别规则

### 支持的意图类型

| 意图类型 | 描述 | 关键词示例 |
|---------|------|-----------|
| GREETING | 问候语 | "你好", "您好", "早上好" |
| TRANSFER_TO_AGENT | 转人工 | "转人工", "人工客服", "找客服" |
| COMPLAINT | 投诉 | "投诉", "不满意", "退款" |
| PRODUCT_INQUIRY | 产品咨询 | "产品", "价格", "功能" |
| TECHNICAL_SUPPORT | 技术支持 | "技术支持", "怎么用", "设置" |
| GOODBYE | 告别 | "再见", "谢谢", "没事了" |

### 情感识别

| 情感类型 | 描述 | 关键词示例 |
|---------|------|-----------|
| angry | 愤怒 | "气死了", "太气人", "垃圾" |
| frustrated | 沮丧 | "郁闷", "搞不懂", "太难了" |
| happy | 高兴 | "开心", "满意", "太好了" |
| worried | 担心 | "担心", "不安", "害怕" |

## API 接口

### 呼出服务 API

```python
# 添加呼出任务
POST /api/outbound/tasks
{
    "customer_phone": "13800138000",
    "priority": 5,
    "campaign_id": "CAMPAIGN_001",
    "customer_data": {...}
}

# 获取任务状态
GET /api/outbound/tasks/{task_id}

# 获取统计信息
GET /api/outbound/statistics
```

### 转接服务 API

```python
# 请求转接
POST /api/transfer/request
{
    "session_id": "session_001",
    "customer_phone": "13800138000",
    "customer_priority": "VIP",
    "required_skills": ["technical"]
}

# 获取转接状态
GET /api/transfer/status/{request_id}

# 获取客服状态
GET /api/agents/status
```

## 日志说明

### 日志级别
- **INFO**: 一般信息，如系统启动、任务完成等
- **WARN**: 警告信息，如配置缺失、性能问题等
- **ERROR**: 错误信息，如连接失败、处理异常等

### 主要日志文件
- `logs/main.log`: 主程序日志
- `logs/outbound_caller.log`: 呼出服务日志
- `logs/transfer_service.log`: 转接服务日志

### 日志格式
```
2024-01-01 10:00:00,000 - service_name - INFO - 消息内容
```

## 故障排除

### 常见问题

1. **系统无法启动**
   - 检查 Python 版本是否 >= 3.7
   - 检查依赖包是否正确安装
   - 查看 `logs/main.log` 获取详细错误信息

2. **FreeSwitch 连接失败**
   - 检查 FreeSwitch 是否正在运行
   - 验证配置文件中的连接参数
   - 确认防火墙设置

3. **呼出失败**
   - 检查电话号码格式是否正确
   - 验证 FreeSwitch 拨号计划配置
   - 查看呼出日志获取失败原因

4. **转接失败**
   - 检查是否有在线客服
   - 验证客服分机号配置
   - 查看转接服务日志

### 调试模式

```bash
# 启动调试模式
python3 main.py --debug

# 查看详细日志
tail -f logs/main.log | grep ERROR
```

## 性能优化

### 系统配置建议

1. **并发呼出数量**: 根据服务器性能调整 `max_concurrent_calls`
2. **数据库优化**: 定期清理过期数据
3. **日志管理**: 配置日志轮转，避免日志文件过大
4. **内存监控**: 监控内存使用，及时重启服务

### 扩展性考虑

1. **水平扩展**: 支持多实例部署
2. **负载均衡**: 使用 Redis 或消息队列
3. **数据库**: 可替换为 PostgreSQL 或 MySQL
4. **缓存**: 添加 Redis 缓存提升性能

## 开发指南

### 添加新的意图类型

1. 在 `intent_service.py` 中添加意图规则
2. 更新 `robot_conversation.js` 中的处理逻辑
3. 测试新意图的识别效果

### 扩展客服技能

1. 在 `transfer_config.json` 中定义新技能
2. 更新客服信息添加相应技能
3. 修改技能匹配逻辑

### 自定义对话流程

1. 修改 `robot_conversation.js` 中的对话逻辑
2. 添加新的 TTS 模板
3. 更新意图处理分支

## 许可证

本项目采用 MIT 许可证，详见 [LICENSE](LICENSE) 文件。

## 贡献指南

欢迎提交 Issue 和 Pull Request 来改进项目。

## 联系方式

如有问题或建议，请通过以下方式联系：

- 邮箱: support@company.com
- 电话: 400-888-8888

---

**注意**: 本系统仅供学习和研究使用，商业使用请确保遵守相关法律法规。