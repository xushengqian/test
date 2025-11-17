# FreeSWITCH 结合 MRCP 获取实时语音流

本项目提供了 FreeSWITCH 与 MRCP (Media Resource Control Protocol) 集成，用于获取和处理实时语音流的完整解决方案。

## 项目结构

```
.
├── conf/
│   ├── autoload_configs/
│   │   └── mrcp.conf.xml          # MRCP 配置文件
│   └── dialplan/
│       └── default/
│           └── mrcp_stream.xml    # 拨号计划配置
├── scripts/
│   ├── mrcp_realtime_stream.lua   # Lua 脚本处理实时流
│   ├── mrcp_esl_realtime_stream.py # Python ESL 客户端示例
│   └── mrcp_stream_handler.js     # Node.js ESL 客户端示例
└── README.md
```

## 功能特性

- ✅ FreeSWITCH MRCP 模块配置
- ✅ 实时语音流获取和处理
- ✅ 多种编程语言支持 (Lua, Python, Node.js)
- ✅ ESL (Event Socket Library) 集成
- ✅ 音频流录制和保存
- ✅ 实时音频处理示例

## 前置要求

### 1. FreeSWITCH 安装

确保已安装 FreeSWITCH 并启用以下模块：

```bash
# 启用必要的模块
fs_cli -x "module_load mod_mrcp"
fs_cli -x "module_load mod_event_socket"
fs_cli -x "module_load mod_lua"
fs_cli -x "module_load mod_sofia"
```

### 2. MRCP 服务器

需要配置 MRCP 服务器（如 UniMRCP、Kaldi 等）。确保 MRCP 服务器运行在配置的地址和端口上。

### 3. 依赖库

**Python:**
```bash
pip install -r requirements.txt
```

**Node.js:**
```bash
npm install
```

## 配置说明

### 1. MRCP 配置

编辑 `conf/autoload_configs/mrcp.conf.xml`：

```xml
<param name="server" value="127.0.0.1:1544"/>
<param name="enable-realtime-stream" value="true"/>
<param name="audio-format" value="PCMU"/>
```

**重要参数：**
- `server`: MRCP 服务器地址和端口
- `enable-realtime-stream`: 启用实时流（必须设为 true）
- `audio-format`: 音频格式 (PCMU/PCMA)
- `sample-rate`: 采样率（默认 8000）

### 2. 拨号计划配置

将 `conf/dialplan/default/mrcp_stream.xml` 复制到 FreeSWITCH 的拨号计划目录：

```bash
cp conf/dialplan/default/mrcp_stream.xml /usr/local/freeswitch/conf/dialplan/default/
```

**可用的扩展：**
- `9999`: 基本实时语音流
- `9998`: 实时语音流 + 录音

### 3. Lua 脚本配置

将 Lua 脚本复制到 FreeSWITCH 脚本目录：

```bash
cp scripts/mrcp_realtime_stream.lua /usr/local/freeswitch/scripts/
chmod +x /usr/local/freeswitch/scripts/mrcp_realtime_stream.lua
```

## 使用方法

### 方法 1: 使用拨号计划（Lua 脚本）

1. **配置完成后重新加载配置：**
```bash
fs_cli -x "reloadxml"
fs_cli -x "reload mod_mrcp"
```

2. **发起呼叫：**
```bash
# 使用 fs_cli
fs_cli -x "originate user/1000 9999"

# 或使用 SIP 客户端呼叫 9999
```

3. **查看日志：**
```bash
tail -f /usr/local/freeswitch/log/freeswitch.log
```

4. **流数据文件位置：**
   - 原始音频流: `/tmp/mrcp_stream_{uuid}.raw`
   - 录音文件: `/tmp/mrcp_stream_{uuid}.wav` (如果使用 9998)

### 方法 2: 使用 Python ESL 客户端

```bash
# 运行 Python 示例
python3 scripts/mrcp_esl_realtime_stream.py
```

**自定义使用：**
```python
from scripts.mrcp_esl_realtime_stream import FreeSwitchESL

esl = FreeSwitchESL(host='127.0.0.1', port=8021, password='ClueCon')
esl.connect()
esl.start_event_listener()

# 发起呼叫
esl.originate_call('9999')

# 获取实时流
uuid = 'your-call-uuid'
output_file = esl.get_realtime_stream(uuid, '/tmp/stream.raw')

# 处理完成后停止
esl.stop_record(uuid)
esl.disconnect()
```

### 方法 3: 使用 Node.js ESL 客户端

```bash
# 运行 Node.js 示例
node scripts/mrcp_stream_handler.js
```

**自定义使用：**
```javascript
const FreeSwitchESL = require('./scripts/mrcp_stream_handler');

const esl = new FreeSwitchESL('127.0.0.1', 8021, 'ClueCon');
await esl.connect();
await esl.subscribeEvents();

// 发起呼叫
await esl.originateCall('9999');

// 获取实时流
const outputFile = await esl.getRealtimeStream(uuid, '/tmp/stream.raw');
```

## 实时流处理

### Lua 脚本中的处理

`mrcp_realtime_stream.lua` 脚本提供了以下功能：

1. **实时音频流读取**
   - 从 FreeSWITCH 会话读取音频数据
   - 可配置缓冲区大小（默认 20ms）

2. **音频数据处理**
   - `process_audio_chunk()` 函数处理每个音频块
   - 可以添加自定义处理逻辑：
     - 语音活动检测 (VAD)
     - 实时语音识别
     - 音频特征提取
     - 音频质量分析

3. **数据保存**
   - 自动保存原始音频流到文件
   - 支持 WAV 格式录音

### 自定义处理逻辑

在 `process_audio_chunk()` 函数中添加您的处理逻辑：

```lua
function process_audio_chunk(audio_data, chunk_number)
    -- 您的自定义处理逻辑
    -- 例如：发送到语音识别 API
    -- 例如：实时音频分析
    -- 例如：语音活动检测
end
```

## 配置参数说明

### MRCP 配置参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `server` | MRCP 服务器地址:端口 | 127.0.0.1:1544 |
| `enable-realtime-stream` | 启用实时流 | true |
| `audio-format` | 音频格式 | PCMU |
| `sample-rate` | 采样率 | 8000 |
| `channels` | 声道数 | 1 |
| `enable-vad` | 启用语音活动检测 | true |

### 拨号计划变量

| 变量 | 说明 |
|------|------|
| `mrcp_profile` | MRCP 配置文件名称 |
| `mrcp_enable_realtime_stream` | 启用实时流 |
| `mrcp_audio_format` | 音频格式 |
| `record_file` | 录音文件路径 |

## 故障排查

### 1. MRCP 连接失败

**检查：**
- MRCP 服务器是否运行
- 防火墙设置
- 配置文件中的服务器地址和端口

**调试：**
```bash
# 查看 MRCP 日志
fs_cli -x "console loglevel debug"
tail -f /usr/local/freeswitch/log/freeswitch.log | grep mrcp
```

### 2. 无法获取音频流

**检查：**
- 会话是否已建立
- `enable-realtime-stream` 是否设为 true
- 音频格式是否匹配

### 3. ESL 连接失败

**检查：**
- Event Socket 模块是否加载
- 端口 8021 是否开放
- 密码是否正确（默认: ClueCon）

**测试连接：**
```bash
telnet 127.0.0.1 8021
```

## 性能优化建议

1. **缓冲区大小调整**
   - 较小的缓冲区（10-20ms）降低延迟但增加 CPU 使用
   - 较大的缓冲区（50-100ms）降低 CPU 使用但增加延迟

2. **音频格式选择**
   - PCMU/PCMA: 低 CPU，标准质量
   - G.722: 更高音质，更高 CPU
   - 根据需求选择合适的格式

3. **并发处理**
   - 使用异步处理避免阻塞
   - 考虑使用消息队列处理大量并发流

## 扩展功能

### 集成语音识别

可以在 `process_audio_chunk()` 中集成语音识别服务：

```lua
function process_audio_chunk(audio_data, chunk_number)
    -- 发送到语音识别 API
    -- 例如：Google Speech-to-Text, Azure Speech, etc.
end
```

### 实时转写

结合 MRCP 的语音识别功能，可以实现实时转写：

```xml
<action application="mrcp" data="recognize"/>
```

### 音频分析

添加实时音频分析功能：
- 音量检测
- 噪声水平
- 语音质量评估

## 许可证

本项目采用 MIT 许可证。

## 贡献

欢迎提交 Issue 和 Pull Request！

## 参考资源

- [FreeSWITCH 官方文档](https://freeswitch.org/confluence/)
- [MRCP 协议规范](https://www.ietf.org/rfc/rfc4463.txt)
- [UniMRCP 文档](https://www.unimrcp.org/)
