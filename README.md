# FreeSwtich 实时音频流获取

本项目提供从 FreeSwtich 获取实时音频流的完整解决方案，支持多种音频流获取方式和推送机制。

## 功能特性

- **ESL 连接管理**: 自动连接、认证、重连 FreeSwtich Event Socket
- **实时音频流获取**: 支持多种方式获取通话中的实时音频数据
- **WebSocket 推送**: 实时将音频数据推送给前端或其他消费者
- **音频文件保存**: 支持将音频流保存为 PCM/WAV 文件
- **REST API**: 提供完整的 HTTP API 接口
- **通话控制**: 支持发起呼叫、挂断、转接等操作

## 技术架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     FreeSwtich Server                            │
│  ┌─────────┐  ┌──────────────┐  ┌───────────────────────────┐  │
│  │   SIP   │  │  ESL (8021)  │  │  mod_audio_stream (TCP)   │  │
│  └────┬────┘  └──────┬───────┘  └─────────────┬─────────────┘  │
└───────┼──────────────┼──────────────────────────┼───────────────┘
        │              │                          │
        │              │ ESL Events               │ Audio Stream
        │              │                          │
┌───────┼──────────────┼──────────────────────────┼───────────────┐
│       │    ┌─────────▼──────────┐  ┌───────────▼──────────┐    │
│       │    │ EslConnectionManager│  │ RealTimeAudioStream │    │
│       │    └─────────┬──────────┘  └───────────┬──────────┘    │
│       │              │                          │               │
│       │    ┌─────────▼──────────┐  ┌───────────▼──────────┐    │
│       │    │  EslEventHandler   │  │  AudioStreamService  │    │
│       │    └─────────┬──────────┘  └───────────┬──────────┘    │
│       │              │                          │               │
│       │              └────────────┬─────────────┘               │
│       │                           │                             │
│       │              ┌────────────▼─────────────┐               │
│       │              │  AudioWebSocketHandler   │               │
│       │              └────────────┬─────────────┘               │
│       │                           │                             │
│       │                Java Spring Boot Application             │
└───────┼───────────────────────────┼─────────────────────────────┘
        │                           │
        │ SIP Call                  │ WebSocket
        │                           │
┌───────▼───────┐         ┌────────▼────────┐
│   SIP Phone   │         │  Web Browser /  │
│   / Gateway   │         │  ASR Service    │
└───────────────┘         └─────────────────┘
```

## 快速开始

### 1. 环境要求

- JDK 11+
- Maven 3.6+
- FreeSwtich 1.10+

### 2. 配置 FreeSwtich

确保 FreeSwtich 的 ESL 已启用，参考 [FreeSwtich 配置指南](docs/freeswitch-config.md)。

### 3. 修改配置

编辑 `src/main/resources/application.yml`:

```yaml
freeswitch:
  esl:
    host: 127.0.0.1      # FreeSwtich服务器地址
    port: 8021            # ESL端口
    password: ClueCon     # ESL密码

audio:
  stream:
    sample-rate: 8000     # 采样率
    channels: 1           # 通道数
    save-path: /tmp/fs-audio  # 音频保存路径
```

### 4. 编译运行

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/fs-realtime-audio-stream-1.0.0-SNAPSHOT.jar
```

## API 接口

### 音频流管理

#### 启动音频流

```bash
POST /api/audio/stream/start
Content-Type: application/json

{
  "uuid": "通话UUID",
  "direction": "both",
  "saveToFile": true
}
```

#### 停止音频流

```bash
POST /api/audio/stream/stop?uuid=通话UUID
```

#### 获取音频数据

```bash
GET /api/audio/data/{uuid}?maxFrames=100
```

#### 获取活跃音频流

```bash
GET /api/audio/streams
```

### 实时音频流服务器

#### 启动TCP服务器

```bash
POST /api/realtime/tcp/start?port=9000
```

#### 停止TCP服务器

```bash
POST /api/realtime/tcp/stop
```

### FreeSwtich 操作

#### 获取状态

```bash
GET /api/freeswitch/status
```

#### 发起呼叫

```bash
POST /api/freeswitch/originate?callerNumber=1001&calleeNumber=18888888888&gateway=gw1
```

#### 挂断通话

```bash
POST /api/freeswitch/hangup?uuid=通话UUID
```

#### 执行命令

```bash
POST /api/freeswitch/command?command=show channels
```

## WebSocket 使用

### 连接地址

```
ws://localhost:8080/ws/audio
```

### 消息格式

#### 订阅音频流

```json
{
  "type": "subscribe",
  "uuid": "通话UUID"
}
```

#### 取消订阅

```json
{
  "type": "unsubscribe",
  "uuid": "通话UUID"
}
```

#### 接收音频数据

```json
{
  "type": "audio",
  "uuid": "通话UUID",
  "timestamp": 1706745600000,
  "sequence": 1,
  "direction": "in",
  "sampleRate": 8000,
  "channels": 1,
  "data": "Base64编码的PCM数据"
}
```

### JavaScript 示例

```javascript
const ws = new WebSocket('ws://localhost:8080/ws/audio');

ws.onopen = () => {
  console.log('WebSocket已连接');
  // 订阅特定通话
  ws.send(JSON.stringify({
    type: 'subscribe',
    uuid: '通话UUID'
  }));
};

ws.onmessage = (event) => {
  const msg = JSON.parse(event.data);
  
  if (msg.type === 'audio') {
    // 解码音频数据
    const audioData = atob(msg.data);
    const arrayBuffer = new ArrayBuffer(audioData.length);
    const view = new Uint8Array(arrayBuffer);
    for (let i = 0; i < audioData.length; i++) {
      view[i] = audioData.charCodeAt(i);
    }
    
    // 处理音频数据 (例如: 播放、ASR等)
    processAudio(arrayBuffer, msg.sampleRate, msg.channels);
  }
};

ws.onerror = (error) => {
  console.error('WebSocket错误:', error);
};

ws.onclose = () => {
  console.log('WebSocket已关闭');
};
```

## 音频流获取方式

### 方式一: ESL 事件 (推荐)

通过 ESL 订阅音频相关事件，适用于配合自定义 FreeSwtich 模块使用。

### 方式二: mod_audio_stream

使用 FreeSwtich 的 mod_audio_stream 模块，将音频流通过 WebSocket 发送。

```
uuid_audio_stream <uuid> start ws://localhost:8080/ws/audio both
```

### 方式三: TCP Socket

启动 TCP 服务器，FreeSwtich 通过 socket 连接发送音频数据。

### 方式四: 命名管道

FreeSwtich 录音到命名管道，应用程序从管道读取音频数据。

```bash
# 创建管道
mkfifo /tmp/audio_pipe

# FreeSwtich录音到管道
uuid_record <uuid> start /tmp/audio_pipe

# 应用程序读取
curl -X POST "http://localhost:8080/api/realtime/pipe/start?pipePath=/tmp/audio_pipe&uuid=通话UUID"
```

## 项目结构

```
src/main/java/com/example/fsaudio/
├── FsAudioStreamApplication.java    # 主应用类
├── config/
│   ├── FreeSwitchConfig.java        # FreeSwtich配置
│   └── AudioStreamConfig.java       # 音频流配置
├── esl/
│   └── EslConnectionManager.java    # ESL连接管理
├── handler/
│   ├── EslEventHandler.java         # ESL事件处理
│   └── RealTimeAudioStreamHandler.java  # 实时音频流处理
├── model/
│   ├── AudioFrame.java              # 音频帧模型
│   ├── CallSession.java             # 通话会话模型
│   └── StreamRequest.java           # 流请求模型
├── service/
│   ├── AudioStreamService.java      # 音频流服务
│   └── FreeSwitchApiService.java    # FreeSwtich API服务
├── websocket/
│   ├── WebSocketConfig.java         # WebSocket配置
│   └── AudioWebSocketHandler.java   # WebSocket处理器
└── controller/
    ├── AudioStreamController.java   # 音频流控制器
    ├── FreeSwitchController.java    # FreeSwtich控制器
    └── RealTimeStreamController.java # 实时流控制器
```

## 常见问题

### Q: ESL 连接失败怎么办？

A: 检查以下几点:
1. FreeSwtich 是否已启动
2. ESL 端口 (默认8021) 是否开放
3. 密码是否正确
4. 防火墙是否允许连接

### Q: 如何获取双向音频流？

A: 使用 `direction: "both"` 参数，或在 FreeSwtich 命令中指定 `rw` 方向。

### Q: 音频数据格式是什么？

A: 默认为 16位线性 PCM (L16)，采样率 8000Hz，单声道。可以通过配置修改。

### Q: 如何将 PCM 转为 WAV？

A: 使用 `RealTimeAudioStreamHandler.pcmToWav()` 方法，或使用 ffmpeg:

```bash
ffmpeg -f s16le -ar 8000 -ac 1 -i input.pcm output.wav
```

## 许可证

MIT License
