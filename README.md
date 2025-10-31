# FreeSWITCH 机器人外呼系统

一个基于 FreeSWITCH 的智能机器人外呼系统，支持自动识别转人工意图并执行转接操作。

## 功能特性

- 🤖 **机器人外呼**：自动发起外呼并处理通话
- 🎯 **意图识别**：识别用户的转人工意图（支持关键词匹配和AI模型）
- 🔄 **自动转接**：检测到转人工意图后，自动转接到人工坐席
- 📊 **事件监听**：实时监听呼叫状态和事件
- 📝 **日志记录**：完整的日志记录系统

## 系统架构

```
┌─────────────────┐
│  Python脚本     │  ← 使用ESL连接FreeSWITCH
│  (ESL客户端)    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  FreeSWITCH     │
│  (ESL服务)      │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Lua脚本        │  ← 处理呼叫流程、语音识别
│  (呼叫处理)     │
└─────────────────┘
```

## 文件说明

- `robot_outbound_call.py` - 基础机器人外呼类（使用ESL）
- `robot_call_with_event_listener.py` - 带事件监听的增强版
- `robot_call_handler.lua` - FreeSWITCH Lua脚本（处理呼叫流程）
- `intent_recognizer.py` - 意图识别模块
- `config.py` - 配置文件
- `requirements.txt` - Python依赖

## 安装依赖

### 1. 安装 FreeSWITCH

参考 [FreeSWITCH 官方文档](https://freeswitch.org/confluence/display/FREESWITCH/Installation) 安装 FreeSWITCH。

### 2. 安装 Python ESL 库

```bash
# Ubuntu/Debian
sudo apt-get install libesl-dev
sudo apt-get install python3-esl

# 或从源码编译
# 参考: https://freeswitch.org/confluence/display/FREESWITCH/Python+ESL
```

### 3. 安装 Python 依赖

```bash
pip install -r requirements.txt
```

### 4. 配置 FreeSWITCH

#### 4.1 配置 ESL

确保 FreeSWITCH 的 ESL 已启用（默认端口 8021）。

编辑 `/usr/local/freeswitch/conf/autoload_configs/event_socket.conf.xml`：

```xml
<configuration name="event_socket.conf" description="Socket Client">
  <settings>
    <param name="nat-map" value="false"/>
    <param name="listen-ip" value="127.0.0.1"/>
    <param name="listen-port" value="8021"/>
    <param name="password" value="ClueCon"/>
  </settings>
</configuration>
```

#### 4.2 复制 Lua 脚本

```bash
# 将 Lua 脚本复制到 FreeSWITCH 目录
sudo cp robot_call_handler.lua /usr/local/freeswitch/scripts/
```

#### 4.3 配置拨号计划（Dialplan）

在 `/usr/local/freeswitch/conf/dialplan/default.xml` 中添加：

```xml
<extension name="robot_outbound">
  <condition field="destination_number" expression="^(robot_call)$">
    <action application="lua" data="robot_call_handler.lua"/>
  </condition>
</extension>
```

#### 4.4 准备语音文件（可选）

如果需要播放自定义语音提示，将音频文件放在：
- `/usr/local/freeswitch/sounds/custom/greeting.wav` - 问候语
- `/usr/local/freeswitch/sounds/custom/transfer.wav` - 转接提示
- `/usr/local/freeswitch/sounds/custom/response.wav` - 机器人回复
- `/usr/local/freeswitch/sounds/custom/goodbye.wav` - 告别语

## 使用方法

### 1. 配置参数

编辑 `config.py` 文件，修改以下配置：

```python
FREESWITCH_CONFIG = {
    "host": "127.0.0.1",
    "port": 8021,
    "password": "ClueCon",  # 修改为你的FreeSWITCH密码
}

OUTBOUND_CONFIG = {
    "caller_id": "1000",  # 主叫号码
    "human_agent_number": "1002",  # 人工坐席号码
}
```

### 2. 运行基础版本

```bash
python3 robot_outbound_call.py
```

### 3. 运行事件监听版本（推荐）

```bash
python3 robot_call_with_event_listener.py
```

### 4. 在代码中使用

```python
from robot_outbound_call import RobotOutboundCall

# 创建实例
robot = RobotOutboundCall(
    host="127.0.0.1",
    port=8021,
    password="ClueCon"
)

# 连接
robot.connect()

# 发起外呼
robot.make_call(
    caller_id="1000",
    callee_number="1001",
    human_agent_number="1002"  # 人工坐席号码
)

# 处理意图识别
intent = robot.process_call_with_intent_recognition(
    uuid="call-uuid",
    audio_data="转人工"  # 或语音文件路径
)

# 断开连接
robot.disconnect()
```

## 意图识别

### 转人工关键词

系统默认识别以下关键词作为转人工意图：

- 转人工
- 人工服务
- 人工客服
- 转接人工
- 我要人工
- 找人工
- 人工坐席
- 等等...

### 自定义关键词

编辑 `intent_recognizer.py` 或 `config.py` 来添加自定义关键词：

```python
INTENT_CONFIG = {
    "transfer_keywords": [
        "转人工", "人工服务", 
        # 添加你的关键词...
    ],
}
```

### 使用 AI 模型

可以配置使用 AI 模型进行更精确的意图识别：

```python
INTENT_CONFIG = {
    "use_ai_model": True,
    "ai_model_endpoint": "https://your-ai-api.com/intent",
    "ai_model_api_key": "your-api-key",
}
```

## 转人工流程

1. **外呼建立**：机器人发起外呼
2. **语音交互**：播放问候语，开始语音识别
3. **意图识别**：检测用户是否说转人工相关关键词
4. **执行转接**：识别到转人工意图后，自动转接到指定的人工坐席号码
5. **桥接通话**：使用 `uuid_transfer` 或 `bridge` 命令完成转接

## 语音识别集成

Lua 脚本使用 FreeSWITCH 的语音识别模块（如 `mod_pocketsphinx` 或 `mod_vosk`）。

### 启用语音识别

在 Lua 脚本中：

```lua
-- 使用 pocketsphinx
session:execute("detect_speech", "pocketsphinx default default default")

-- 或使用 vosk
session:execute("detect_speech", "vosk default default default")
```

### 配置语音识别引擎

参考 [FreeSWITCH 语音识别文档](https://freeswitch.org/confluence/display/FREESWITCH/mod_pocketsphinx) 配置语音识别引擎。

## 日志

日志文件保存在 `robot_call.log`，包含：
- 呼叫状态变化
- 意图识别结果
- 转接操作记录
- 错误信息

## 故障排除

### 1. ESL 连接失败

- 检查 FreeSWITCH 是否运行：`fs_cli -x status`
- 检查 ESL 端口是否开放：`netstat -an | grep 8021`
- 检查密码是否正确

### 2. 呼叫无法发起

- 检查号码格式是否正确
- 检查 SIP 用户是否注册
- 查看 FreeSWITCH 日志：`tail -f /usr/local/freeswitch/log/freeswitch.log`

### 3. 转接失败

- 确认人工坐席号码有效
- 检查拨号计划配置
- 确认坐席已注册到 FreeSWITCH

### 4. 语音识别不工作

- 安装并启用语音识别模块（pocketsphinx/vosk）
- 检查语言模型配置
- 查看语音识别日志

## 扩展功能

- [ ] 集成云端 ASR（百度、阿里云等）
- [ ] 集成 TTS 语音合成
- [ ] 支持多轮对话管理
- [ ] 添加呼叫统计和报表
- [ ] 支持多人工坐席队列
- [ ] 添加呼叫录音功能

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！

## 联系方式

如有问题或建议，请提交 Issue。
