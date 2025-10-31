# FreeSWITCH 机器人外呼转人工系统

实现 FreeSWITCH 机器人外呼系统，当识别出转人工意图时自动转接人工座席。

## 功能特性

- 🤖 机器人外呼自动拨打电话
- 🎤 语音识别和意图理解
- 👤 自动转接人工座席
- 📊 座席状态管理
- 🎯 智能座席分配

## 系统架构

```
┌─────────────┐
│  FreeSWITCH │
│   Dialplan  │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│  Lua 脚本       │
│ robot_outbound  │
└────┬────────────┘
     │
     ├──────────┬──────────────┐
     ▼          ▼              ▼
┌─────────┐ ┌──────────┐ ┌──────────┐
│ 意图识别 │ │ 座席管理 │ │ 呼叫控制 │
│ Python  │ │ Python   │ │ FreeSWITCH│
└─────────┘ └──────────┘ └──────────┘
```

## 文件结构

```
/workspace/
├── dialplan/
│   └── robot_outbound.xml      # FreeSWITCH dialplan 配置
├── scripts/
│   ├── robot_outbound.lua      # 主 Lua 脚本（呼叫流程控制）
│   ├── intent_detection.py     # 意图识别服务
│   └── agent_manager.py        # 座席管理服务
├── config/
│   └── freeswitch.conf.example # 配置示例
└── README.md                   # 本文档
```

## 安装部署

### 1. 前置要求

- FreeSWITCH 已安装并运行
- Python 3.6+
- Lua 模块已启用（mod_lua）

### 2. 配置 FreeSWITCH

#### 2.1 复制 dialplan 配置

```bash
# 复制 dialplan 文件到 FreeSWITCH 配置目录
cp dialplan/robot_outbound.xml /usr/local/freeswitch/conf/dialplan/default/

# 或在 default.xml 中包含该文件
# <include>
#   <X-PRE-PROCESS cmd="include" data="default/robot_outbound.xml"/>
# </include>
```

#### 2.2 复制 Lua 脚本

```bash
# 复制 Lua 脚本到 FreeSWITCH Lua 目录
cp scripts/robot_outbound.lua /usr/local/freeswitch/scripts/

# 或修改脚本中的路径为绝对路径
```

#### 2.3 复制 Python 脚本

```bash
# 复制 Python 脚本
cp scripts/*.py /opt/freeswitch_scripts/

# 添加执行权限
chmod +x /opt/freeswitch_scripts/*.py
```

### 3. 配置音频文件

准备以下音频文件（或使用 TTS 生成）：

- `robot_welcome.wav` - 欢迎语
- `transferring.wav` - 转接提示音
- `no_agent_available.wav` - 无座席提示
- `robot_goodbye.wav` - 结束语

放置到：`/usr/local/freeswitch/sounds/zh/cn/`

### 4. 配置座席

编辑 `scripts/agent_manager.py`，配置座席信息：

```python
self.agents = {
    "1001": Agent("1001", "1001", AgentStatus.IDLE, ["general"], 0, 1),
    "1002": Agent("1002", "1002", AgentStatus.IDLE, ["general"], 0, 1),
    # 添加更多座席...
}
```

确保座席分机号在 FreeSWITCH 用户配置中存在。

## 使用方法

### 1. 发起外呼

通过 FreeSWITCH API 或 ESL 发起外呼：

```bash
# 使用 fs_cli
fs_cli -x "originate user/1000@default robot_outbound XML default"
```

或通过 ESL：

```python
import freeswitchESL

con = freeswitchESL.ESLconnection("localhost", "8021", "ClueCon")
con.bgapi("originate", "user/1000@default robot_outbound XML default")
```

### 2. 呼叫流程

1. **外呼建立**：FreeSWITCH 建立与用户的呼叫连接
2. **播放欢迎语**：播放机器人欢迎语
3. **语音交互**：录音并识别用户语音
4. **意图识别**：调用 Python 服务识别用户意图
5. **判断转接**：如果识别到转人工意图，执行转接
6. **座席分配**：调用座席管理服务分配可用座席
7. **转接执行**：将呼叫转接到分配的人工座席

### 3. 转人工触发方式

- **语音识别**：用户说"转人工"、"人工服务"等关键词
- **按键转接**：用户按 `0` 键直接转人工

## 意图识别

### 当前实现

当前使用简单的关键词匹配，支持以下转人工关键词：

- 转人工
- 人工服务
- 人工客服
- 转接人工
- 人工
- 客服
- 人工座席
- 人工接线员

### 扩展为高级 NLP

可以集成以下服务进行更准确的意图识别：

1. **语音识别 (ASR)**
   - 百度语音识别 API
   - 讯飞语音识别 API
   - 阿里云语音识别 API
   - Google Speech-to-Text

2. **自然语言处理 (NLP)**
   - 使用 BERT/GPT 模型进行意图分类
   - 集成 Rasa、Dialogflow 等对话平台
   - 自定义 NLP 模型

修改 `scripts/intent_detection.py` 中的 `speech_to_text()` 和 `analyze_intent()` 方法。

## 座席管理

### 功能

- 座席状态管理（空闲/忙碌/离线）
- 智能座席分配（负载均衡）
- 技能标签匹配
- 并发控制

### 扩展

可以集成以下系统：

- Redis 存储座席状态
- 数据库存储座席信息
- CRM 系统集成
- 呼叫中心系统（如 Asterisk、Asterisk）集成

## 调试和日志

### FreeSWITCH 日志

```bash
# 查看 FreeSWITCH 日志
tail -f /usr/local/freeswitch/log/freeswitch.log

# 查看 Lua 脚本日志
grep "ROBOT_OUTBOUND" /usr/local/freeswitch/log/freeswitch.log
```

### Python 脚本日志

Python 脚本的日志输出到标准输出，可以在 FreeSWITCH 日志中查看。

### 测试意图识别

```bash
# 测试意图识别服务
python3 scripts/intent_detection.py /path/to/audio.wav
```

### 测试座席管理

```bash
# 获取可用座席
python3 scripts/agent_manager.py get_agent

# 释放座席
python3 scripts/agent_manager.py release_agent 1001
```

## 常见问题

### 1. Lua 脚本无法执行

- 检查 `mod_lua` 模块是否已加载
- 检查脚本路径是否正确
- 检查脚本权限

### 2. Python 脚本无法调用

- 检查 Python 路径（使用 `which python3`）
- 检查脚本执行权限
- 检查脚本路径是否正确

### 3. 无法转接座席

- 检查座席分机号是否在 FreeSWITCH 用户配置中存在
- 检查座席状态是否正确
- 查看 FreeSWITCH 日志获取详细错误信息

### 4. 意图识别不准确

- 集成更高级的 ASR 和 NLP 服务
- 增加更多训练数据
- 调整置信度阈值

## 开发扩展

### 添加新的意图

1. 在 `intent_detection.py` 中添加新的意图处理逻辑
2. 在 `robot_outbound.lua` 中添加对应的响应处理

### 集成第三方服务

1. 修改 `intent_detection.py` 集成 ASR/NLP API
2. 修改 `agent_manager.py` 集成座席管理系统
3. 添加配置文件管理 API 密钥

## 许可证

本项目采用 MIT 许可证。

## 贡献

欢迎提交 Issue 和 Pull Request。
