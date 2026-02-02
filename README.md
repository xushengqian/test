# FreeSWitch 实时音频流获取

本项目提供了从 FreeSWitch 获取实时音频流的完整解决方案，支持通过 WebSocket 实时推送音频数据。

## 功能特性

- ✅ 通过 ESL (Event Socket Library) 连接 FreeSWitch
- ✅ 实时获取通话音频流 (支持单向/双向)
- ✅ WebSocket 实时推送音频数据给客户端
- ✅ HTTP 接口接收音频数据
- ✅ 支持 PCM/WAV 格式转换
- ✅ 音频重采样功能
- ✅ 通话事件监听和处理
- ✅ REST API 控制接口

## 技术架构

```
┌─────────────┐     ESL协议      ┌─────────────────┐     WebSocket      ┌─────────────┐
│  FreeSWitch │ ◄───────────────► │  Java应用服务    │ ◄────────────────► │  客户端应用  │
└─────────────┘                   └─────────────────┘                     └─────────────┘
       │                                  │
       │  音频流                          │  REST API
       ▼                                  ▼
  ┌──────────┐                     ┌──────────────┐
  │ mod_audio │                     │ 其他业务系统  │
  │ _stream  │                     │  (ASR/NLP等) │
  └──────────┘                     └──────────────┘
```

## 项目结构

```
.
├── pom.xml                              # Maven配置
├── src/main/java/com/example/fsaudio/
│   ├── FsAudioStreamApplication.java   # 应用入口
│   ├── config/
│   │   ├── FreeSwitchConfig.java       # FreeSWitch配置
│   │   ├── AudioStreamConfig.java      # 音频流配置
│   │   └── WebSocketConfig.java        # WebSocket配置
│   ├── esl/
│   │   ├── EslClient.java              # ESL客户端
│   │   └── EslEventHandler.java        # ESL事件处理
│   ├── audio/
│   │   ├── AudioStreamService.java     # 音频流服务
│   │   └── AudioFormatConverter.java   # 音频格式转换
│   ├── websocket/
│   │   ├── AudioWebSocketHandler.java  # WebSocket处理器
│   │   └── AudioStreamWebSocketServer.java
│   ├── controller/
│   │   ├── AudioStreamController.java  # REST控制器
│   │   └── HttpAudioReceiver.java      # HTTP音频接收
│   └── model/
│       ├── AudioFrame.java             # 音频帧模型
│       ├── CallSession.java            # 通话会话模型
│       └── AudioStreamRequest.java     # 请求模型
├── src/main/resources/
│   └── application.yml                 # 应用配置
└── freeswitch-config/
    ├── dialplan-audio-stream.xml       # Dialplan配置示例
    ├── mod_audio_stream.conf.xml       # 模块配置
    ├── event_socket.conf.xml           # ESL配置
    ├── modules.conf.xml                # 模块加载配置
    └── start_audio_stream.lua          # Lua脚本示例
```

## 快速开始

### 1. 环境要求

- JDK 11+
- Maven 3.6+
- FreeSWitch 1.10+
- (可选) mod_audio_stream 模块

### 2. 配置 FreeSWitch

#### 2.1 启用 Event Socket

编辑 `/usr/local/freeswitch/conf/autoload_configs/event_socket.conf.xml`:

```xml
<configuration name="event_socket.conf" description="Socket Client">
  <settings>
    <param name="listen-ip" value="0.0.0.0"/>
    <param name="listen-port" value="8021"/>
    <param name="password" value="ClueCon"/>
  </settings>
</configuration>
```

#### 2.2 配置 Dialplan

将 `freeswitch-config/dialplan-audio-stream.xml` 复制到 FreeSWitch 的 dialplan 目录。

### 3. 配置应用

编辑 `src/main/resources/application.yml`:

```yaml
freeswitch:
  esl:
    host: 127.0.0.1      # FreeSWitch地址
    port: 8021           # ESL端口
    password: ClueCon    # ESL密码

audio:
  stream:
    sample-rate: 8000    # 采样率
    bit-depth: 16        # 位深度
    channels: 1          # 声道数
```

### 4. 编译运行

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/fs-realtime-audio-stream-1.0.0-SNAPSHOT.jar
```

## API 接口

### 连接 ESL

```bash
POST /api/audio/esl/connect
```

### 获取 ESL 状态

```bash
GET /api/audio/esl/status
```

### 启动音频流

```bash
POST /api/audio/stream/start
Content-Type: application/json

{
  "callUuid": "通话UUID",
  "direction": "both"  // read/write/both
}
```

### 使用 WebSocket 启动音频流

```bash
POST /api/audio/stream/start-ws
Content-Type: application/json

{
  "callUuid": "通话UUID",
  "websocketUrl": "ws://your-server:8080/ws/audio-stream/{uuid}"
}
```

### 停止音频流

```bash
POST /api/audio/stream/stop?callUuid=通话UUID
```

### 获取活跃会话

```bash
GET /api/audio/sessions
```

### 开始录音

```bash
POST /api/audio/record/start?callUuid=通话UUID&filePath=/path/to/file.wav
```

### 停止录音

```bash
POST /api/audio/record/stop?callUuid=通话UUID
```

### 执行 ESL 命令

```bash
POST /api/audio/esl/api?command=show%20calls
```

## WebSocket 接口

### 连接地址

```
ws://your-server:8080/ws/audio-stream/{callUuid}
```

或者不带 UUID 连接，后续通过消息订阅：

```
ws://your-server:8080/ws/audio-stream
```

### 订阅通话音频

```json
{"action": "subscribe", "callUuid": "通话UUID"}
```

### 订阅所有通话

```json
{"action": "subscribe_all"}
```

### 取消订阅

```json
{"action": "unsubscribe", "callUuid": "通话UUID"}
```

### 心跳

```json
{"action": "ping"}
```

### 接收消息格式

**元数据消息 (Text):**
```json
{
  "type": "audio_frame",
  "callUuid": "xxx",
  "timestamp": 1234567890123,
  "sequenceNumber": 1,
  "sampleRate": 8000,
  "channels": 1,
  "size": 640
}
```

**音频数据 (Binary):**
PCM 格式的原始音频数据

## 音频流获取方式

### 方式一: 使用 mod_audio_stream 模块

这是推荐的方式，需要编译安装 `mod_audio_stream` 模块。

Dialplan 配置:
```xml
<action application="uuid_audio_stream" data="${uuid} start ws://your-server:8080/ws/audio-stream/${uuid} mixed"/>
```

### 方式二: 使用 ESL 命令

通过 ESL 连接发送 API 命令:
```
api uuid_audio_stream <uuid> start <ws_url> -b
```

### 方式三: 使用 HTTP 推送

FreeSWitch 通过 mod_shout 将音频 POST 到 HTTP 接口:
```xml
<action application="record" data="shout://your-server:8080/audio-stream/${uuid}/stream"/>
```

### 方式四: 录音 + 实时读取

先录音到本地文件，同时读取并推送:
```xml
<action application="uuid_record" data="${uuid} start /tmp/recordings/${uuid}.wav"/>
```

## 示例代码

### JavaScript WebSocket 客户端

```javascript
const ws = new WebSocket('ws://localhost:8080/ws/audio-stream');

ws.onopen = () => {
  console.log('Connected');
  // 订阅所有通话
  ws.send(JSON.stringify({action: 'subscribe_all'}));
};

ws.onmessage = (event) => {
  if (typeof event.data === 'string') {
    // 元数据
    const meta = JSON.parse(event.data);
    console.log('Audio frame:', meta);
  } else {
    // 音频数据 (ArrayBuffer)
    const audioData = event.data;
    // 处理音频数据...
  }
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};

ws.onclose = () => {
  console.log('Disconnected');
};
```

### Python 客户端示例

```python
import websocket
import json

def on_message(ws, message):
    if isinstance(message, str):
        data = json.loads(message)
        print(f"Metadata: {data}")
    else:
        # Binary audio data
        print(f"Audio data: {len(message)} bytes")

def on_open(ws):
    ws.send(json.dumps({"action": "subscribe_all"}))

ws = websocket.WebSocketApp(
    "ws://localhost:8080/ws/audio-stream",
    on_message=on_message,
    on_open=on_open
)
ws.run_forever()
```

## 常见问题

### 1. ESL 连接失败

- 检查 FreeSWitch 是否运行
- 确认 event_socket.conf.xml 配置正确
- 确认防火墙允许 8021 端口

### 2. 无法获取音频流

- 确认 mod_audio_stream 模块已加载
- 检查 Dialplan 配置是否正确
- 查看 FreeSWitch 日志排查问题

### 3. WebSocket 连接断开

- 检查网络稳定性
- 确认心跳机制正常工作
- 查看服务端日志

## 许可证

MIT License
