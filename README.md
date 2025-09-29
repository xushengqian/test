# FreeSWITCH 机器人外呼系统 - 支持坐席主动接入

## 系统概述

本系统实现了FreeSWITCH环境下的智能机器人外呼功能，支持在客户与机器人对话过程中，人工坐席可以随时主动接入并与客户进行通话。

### 核心功能

1. **机器人自动外呼**
   - 自动拨打客户电话
   - 语音识别（ASR）和语音合成（TTS）
   - 智能对话和意图识别

2. **坐席主动介入**
   - 实时监控所有通话状态
   - 一键接入正在进行的机器人通话
   - 无缝切换，不中断客户体验

3. **会议桥接技术**
   - 使用FreeSWITCH会议室功能
   - 支持多方通话
   - 灵活的成员控制

4. **Web控制面板**
   - 实时通话列表展示
   - 坐席状态管理
   - 一键发起外呼和接入通话

## 系统架构

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Web界面       │────▶│  WebSocket服务器  │────▶│  ESL控制器      │
│  (坐席控制台)    │◀────│   (实时通信)      │◀────│  (通话管理)     │
└─────────────────┘     └──────────────────┘     └─────────────────┘
                                                           │
                                                           ▼
                                                  ┌─────────────────┐
                                                  │   FreeSWITCH    │
                                                  │   ┌──────────┐  │
                                                  │   │ Dialplan │  │
                                                  │   └──────────┘  │
                                                  │   ┌──────────┐  │
                                                  │   │Conference│  │
                                                  │   └──────────┘  │
                                                  │   ┌──────────┐  │
                                                  │   │Lua Script│  │
                                                  │   └──────────┘  │
                                                  └─────────────────┘
```

## 安装部署

### 1. 环境要求

- FreeSWITCH 1.10+ 
- Python 3.7+
- 现代Web浏览器（Chrome/Firefox/Safari）

### 2. 安装依赖

```bash
# Python依赖
pip install python-ESL websockets asyncio

# FreeSWITCH模块
# 确保已启用以下模块：
# - mod_conference
# - mod_lua
# - mod_flite (TTS)
# - mod_pocketsphinx (ASR)
# - mod_event_socket
```

### 3. 配置FreeSWITCH

#### 3.1 复制dialplan配置

```bash
cp freeswitch_config/dialplan/robot_outbound.xml /etc/freeswitch/dialplan/
```

#### 3.2 复制Lua脚本

```bash
cp scripts/robot_handler.lua /usr/share/freeswitch/scripts/
```

#### 3.3 配置Event Socket

编辑 `/etc/freeswitch/autoload_configs/event_socket.conf.xml`:

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

#### 3.4 重载FreeSWITCH配置

```bash
fs_cli -x "reloadxml"
fs_cli -x "reload mod_lua"
fs_cli -x "reload mod_conference"
```

### 4. 启动系统

#### 4.1 启动ESL控制器和WebSocket服务器

```bash
cd scripts
python3 esl_controller.py
```

#### 4.2 访问Web控制面板

打开浏览器访问：
```
file:///workspace/web_interface/index.html
```

或者启动一个简单的HTTP服务器：

```bash
cd web_interface
python3 -m http.server 8080
# 然后访问 http://localhost:8080
```

## 使用说明

### 1. 发起机器人外呼

1. 在Web控制面板输入客户电话号码
2. 点击"发起呼叫"按钮
3. 系统自动拨打电话并启动机器人对话

### 2. 坐席接入通话

1. 在"当前通话列表"查看所有进行中的通话
2. 找到需要接入的通话
3. 点击"接入通话"按钮
4. 坐席将自动加入会议室与客户对话

### 3. 机器人对话流程

机器人支持以下对话场景：
- 基础问候和服务介绍
- 关键词识别（转人工、查询、投诉等）
- 自动转接人工（客户要求时）
- 静音检测和重复提醒

## 配置说明

### 1. SIP提供商配置

编辑 `robot_outbound.xml` 中的SIP网关：

```xml
<action application="bridge" data="sofia/external/$1@your_sip_provider"/>
```

替换 `your_sip_provider` 为实际的SIP提供商地址。

### 2. TTS/ASR引擎配置

编辑 `robot_handler.lua` 中的引擎设置：

```lua
local tts_engine = "flite"  -- 替换为您的TTS引擎
local asr_engine = "pocketsphinx"  -- 替换为您的ASR引擎
```

### 3. 录音存储路径

编辑 `robot_outbound.xml` 中的录音路径：

```xml
<action application="record_session" data="/var/freeswitch/recordings/${strftime(%Y-%m-%d-%H-%M-%S)}_${call_uuid}.wav"/>
```

## API接口

### WebSocket消息格式

#### 发起外呼
```json
{
  "type": "initiate_call",
  "customer_number": "13800138000"
}
```

#### 坐席接入
```json
{
  "type": "agent_join",
  "call_uuid": "uuid-string",
  "agent_id": "Agent001",
  "agent_number": "1001"
}
```

#### 获取通话列表
```json
{
  "type": "get_calls"
}
```

### FreeSWITCH事件

系统监听以下FreeSWITCH事件：
- `CHANNEL_CREATE` - 通道创建
- `CHANNEL_ANSWER` - 通话应答
- `CHANNEL_HANGUP` - 通话挂断
- `CUSTOM robot::agent_needed` - 需要人工介入

## 高级功能

### 1. 智能路由

可以根据客户信息或IVR选择自动路由到不同的坐席组。

### 2. 实时转写

集成实时语音转文字功能，坐席可以看到对话文本。

### 3. 情绪分析

通过语音情绪识别，自动检测需要人工介入的场景。

### 4. 多语言支持

配置不同的TTS/ASR引擎支持多语言服务。

## 故障排查

### 1. WebSocket连接失败

检查ESL控制器是否正常运行：
```bash
netstat -an | grep 8765
```

### 2. 无法发起外呼

检查FreeSWITCH日志：
```bash
tail -f /var/log/freeswitch/freeswitch.log
```

### 3. 坐席无法接入

确认会议模块已加载：
```bash
fs_cli -x "module_exists mod_conference"
```

### 4. 语音识别不工作

检查ASR模块状态：
```bash
fs_cli -x "pocketsphinx status"
```

## 性能优化

1. **并发处理**
   - 使用连接池管理ESL连接
   - 异步处理WebSocket消息

2. **资源管理**
   - 及时释放会议室资源
   - 定期清理过期录音文件

3. **负载均衡**
   - 多个FreeSWITCH节点分布式部署
   - 使用Redis共享会话状态

## 安全建议

1. **认证授权**
   - 为Web界面添加登录认证
   - 使用WSS（WebSocket Secure）

2. **网络安全**
   - 限制ESL访问IP
   - 使用防火墙规则

3. **数据保护**
   - 加密存储录音文件
   - 定期备份配置和日志

## 扩展开发

### 集成第三方服务

1. **CRM系统**
   - 获取客户信息
   - 同步通话记录

2. **AI服务**
   - 接入智能对话平台（如百度UNIT、阿里云智能对话）
   - 使用更先进的NLP模型

3. **监控告警**
   - 集成Prometheus/Grafana
   - 设置通话质量告警

## 许可证

MIT License

## 技术支持

如有问题或建议，请提交Issue或联系技术支持团队。

---

**版本**: 1.0.0  
**更新日期**: 2024-01  
**作者**: FreeSWITCH开发团队