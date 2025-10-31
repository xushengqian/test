# 快速开始指南

## 系统概述

这是一个 FreeSWITCH 机器人外呼系统，能够：
1. 自动拨打电话进行外呼
2. 识别用户的语音意图
3. 当用户表达转人工需求时，自动转接到人工座席

## 核心流程

```
外呼建立 → 播放欢迎语 → 录音 → 意图识别 → 判断是否需要转人工 → 转接座席
```

## 快速部署步骤

### 1. 文件部署

```bash
# 复制 dialplan 配置
cp dialplan/robot_outbound.xml /usr/local/freeswitch/conf/dialplan/default/

# 复制 Lua 脚本（修改脚本中的路径为实际路径）
cp scripts/robot_outbound.lua /usr/local/freeswitch/scripts/

# 复制 Python 脚本
cp scripts/*.py /opt/freeswitch_scripts/
chmod +x /opt/freeswitch_scripts/*.py
```

### 2. 修改脚本路径

编辑 `robot_outbound.lua`，修改 Python 脚本路径：

```lua
local python_script = "/opt/freeswitch_scripts/intent_detection.py"
```

### 3. 配置座席

编辑 `agent_manager.py`，配置座席分机号（确保与 FreeSWITCH 用户配置一致）。

### 4. 准备音频文件

准备以下音频文件并放置到 FreeSWITCH sounds 目录：
- `robot_welcome.wav` - 欢迎语
- `transferring.wav` - 转接提示
- `no_agent_available.wav` - 无座席提示
- `robot_goodbye.wav` - 结束语

### 5. 重载配置

```bash
fs_cli -x "reloadxml"
fs_cli -x "reload mod_lua"
```

## 测试方法

### 测试意图识别

```bash
# 测试文本意图识别
python3 scripts/test_intent.py

# 测试音频意图识别（如果有音频文件）
python3 scripts/test_intent.py /path/to/audio.wav
```

### 测试座席管理

```bash
python3 scripts/test_agent.py
```

### 发起测试外呼

```bash
# 通过 fs_cli
fs_cli -x "originate user/1000@default robot_outbound XML default"
```

## 转人工触发方式

1. **语音识别**：用户说"转人工"、"人工服务"等关键词
2. **按键转接**：用户按 `0` 键

## 下一步

- 集成真实的 ASR 服务（百度、讯飞等）
- 集成高级 NLP 模型进行意图理解
- 连接数据库或 Redis 管理座席状态
- 添加更多机器人对话逻辑

详细文档请参阅 `README.md`。
