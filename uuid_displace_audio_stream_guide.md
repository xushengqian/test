# FreeSWITCH uuid_displace 音频流播放与文件写入指南

## 目录
1. [uuid_displace 概述](#1-uuid_displace-概述)
2. [播放音频流](#2-播放音频流)
3. [音频流写入文件系统](#3-音频流写入文件系统)
4. [实际应用示例](#4-实际应用示例)
5. [常见问题与解决方案](#5-常见问题与解决方案)

---

## 1. uuid_displace 概述

### 1.1 基本定义

`uuid_displace` 是 FreeSWITCH 的 API 命令，用于在活动通话中：
- **混入音频**（mux 模式）：将新音频与原通话音频混合
- **替换音频**（默认模式）：用新音频替换原通话音频

### 1.2 基本语法

```bash
uuid_displace <uuid> <start|stop> <path> [<limit>] [mux]
```

| 参数 | 说明 |
|------|------|
| `uuid` | 通话的唯一标识符 |
| `start/stop` | 启动或停止播放 |
| `path` | 音频源路径（文件或流地址） |
| `limit` | 播放时长限制（毫秒），0 表示无限制 |
| `mux` | 混音模式标志 |

### 1.3 音频方向控制

默认情况下，`uuid_displace` 作用于通话的 **读端**（即对方听到的音频）。

要控制音频注入方向，可以在路径前添加前缀：
- 无前缀或 `m`：注入到读端（对方听到）
- `mr`：注入到写端（本地听到）

---

## 2. 播放音频流

### 2.1 本地文件播放

```bash
# 播放 WAV 文件
uuid_displace <uuid> start /var/sounds/audio.wav

# 播放并混音
uuid_displace <uuid> start /var/sounds/background.wav 0 mux

# 限时播放（5秒）
uuid_displace <uuid> start /var/sounds/prompt.wav 5000
```

### 2.2 HTTP/HTTPS 流播放

```bash
# 播放 HTTP 音频文件
uuid_displace <uuid> start http://example.com/audio.wav

# 播放 HTTPS 音频
uuid_displace <uuid> start https://cdn.example.com/music.mp3
```

### 2.3 使用 mod_shout 播放网络流

需要加载 `mod_shout` 模块：

```xml
<!-- conf/autoload_configs/modules.conf.xml -->
<load module="mod_shout"/>
```

播放 Icecast/Shoutcast 流：

```bash
# 播放 Shoutcast 流
uuid_displace <uuid> start shout://icecast.server.com:8000/live.mp3 0 mux

# 播放带认证的流
uuid_displace <uuid> start shout://user:password@server.com:8000/stream.mp3
```

### 2.4 使用文件句柄流播放

通过文件描述符或管道播放：

```bash
# 使用 tone_stream 生成音调
uuid_displace <uuid> start tone_stream://%(200,100,440);%(200,100,880)

# 使用 silence_stream 播放静音
uuid_displace <uuid> start silence_stream://1000
```

### 2.5 Lua 脚本中播放流

```lua
-- Lua 脚本示例
local uuid = argv[1]
local stream_url = "shout://radio.example.com:8000/stream"

-- 开始播放流
api = freeswitch.API()
api:execute("uuid_displace", uuid .. " start " .. stream_url .. " 0 mux")

-- 停止播放
-- api:execute("uuid_displace", uuid .. " stop " .. stream_url)
```

---

## 3. 音频流写入文件系统

### 3.1 使用 uuid_record 录制到文件

`uuid_record` 是将通话音频写入文件系统的主要方法：

```bash
# 基本录制
uuid_record <uuid> start /recordings/call.wav

# 录制为 MP3 格式（需要 mod_shout）
uuid_record <uuid> start /recordings/call.mp3

# 立体声录制（分离双方音频）
uuid_record <uuid> start /recordings/call_stereo.wav 0 stereo

# 限时录制（60秒）
uuid_record <uuid> start /recordings/call.wav 60
```

### 3.2 录制格式支持

| 格式 | 模块依赖 | 说明 |
|------|----------|------|
| WAV | 内置 | 无压缩，高质量 |
| MP3 | mod_shout | 压缩格式，节省空间 |
| OGG | mod_shout | 开源压缩格式 |

### 3.3 流式写入到远程服务器

```bash
# 实时流式写入到 Shoutcast 服务器
uuid_record <uuid> start shout://source:hackme@localhost:8000/recording.mp3

# 写入到 HTTP 端点（需要自定义模块）
uuid_record <uuid> start http://storage.example.com/upload/call.wav
```

### 3.4 使用 ESL 获取音频数据并写入文件

#### Python ESL 示例

```python
#!/usr/bin/env python3
import ESL
import wave
import struct

class AudioStreamWriter:
    def __init__(self, uuid, output_file):
        self.uuid = uuid
        self.output_file = output_file
        self.conn = ESL.ESLconnection("127.0.0.1", "8021", "ClueCon")
        
    def start_capture(self):
        if not self.conn.connected():
            print("Failed to connect to FreeSWITCH")
            return
            
        # 订阅自定义事件
        self.conn.events("plain", "CUSTOM sofia::media")
        
        # 设置音频捕获
        self.conn.api(f"uuid_debug_media {self.uuid} read on")
        
        # 打开输出文件
        with wave.open(self.output_file, 'wb') as wav_file:
            wav_file.setnchannels(1)
            wav_file.setsampwidth(2)  # 16-bit
            wav_file.setframerate(8000)  # 8kHz
            
            while True:
                event = self.conn.recvEvent()
                if event:
                    event_name = event.getHeader("Event-Name")
                    if event_name == "CUSTOM":
                        # 处理音频数据
                        audio_data = event.getBody()
                        if audio_data:
                            wav_file.writeframes(audio_data)

# 使用示例
# writer = AudioStreamWriter("call-uuid-here", "/recordings/output.wav")
# writer.start_capture()
```

#### Node.js ESL 示例

```javascript
const esl = require('modesl');
const fs = require('fs');
const WavEncoder = require('wav-encoder');

class AudioStreamWriter {
    constructor(uuid, outputFile) {
        this.uuid = uuid;
        this.outputFile = outputFile;
        this.audioBuffer = [];
    }

    connect() {
        this.conn = new esl.Connection('127.0.0.1', 8021, 'ClueCon', () => {
            console.log('Connected to FreeSWITCH');
            this.startCapture();
        });
    }

    startCapture() {
        // 订阅事件
        this.conn.subscribe('CUSTOM sofia::media');
        
        // 启用音频调试
        this.conn.api(`uuid_debug_media ${this.uuid} read on`);
        
        this.conn.on('esl::event::CUSTOM::*', (event) => {
            const audioData = event.getBody();
            if (audioData) {
                this.audioBuffer.push(Buffer.from(audioData, 'base64'));
            }
        });
    }

    async saveToFile() {
        const combinedBuffer = Buffer.concat(this.audioBuffer);
        const audioData = {
            sampleRate: 8000,
            channelData: [new Float32Array(combinedBuffer.length / 2)]
        };
        
        // 转换 16-bit PCM 到 Float32
        for (let i = 0; i < combinedBuffer.length / 2; i++) {
            audioData.channelData[0][i] = combinedBuffer.readInt16LE(i * 2) / 32768;
        }
        
        const encoded = await WavEncoder.encode(audioData);
        fs.writeFileSync(this.outputFile, Buffer.from(encoded));
    }
}

// 使用示例
// const writer = new AudioStreamWriter('call-uuid', '/recordings/output.wav');
// writer.connect();
```

### 3.5 Dialplan 中配置自动录制

```xml
<!-- conf/dialplan/default.xml -->
<extension name="record_all_calls">
    <condition field="destination_number" expression="^(\d+)$">
        <!-- 设置录音路径 -->
        <action application="set" data="RECORD_TITLE=Call Recording"/>
        <action application="set" data="RECORD_COPYRIGHT=Company Name"/>
        <action application="set" data="RECORD_SOFTWARE=FreeSWITCH"/>
        <action application="set" data="RECORD_ARTIST=Support Team"/>
        <action application="set" data="RECORD_COMMENT=Auto recorded"/>
        <action application="set" data="RECORD_DATE=${strftime(%Y-%m-%d %H:%M:%S)}"/>
        
        <!-- 开始录音 -->
        <action application="record_session" data="/recordings/${uuid}.wav"/>
        
        <!-- 继续呼叫流程 -->
        <action application="bridge" data="user/$1@${domain_name}"/>
    </condition>
</extension>
```

### 3.6 使用 mod_oreka 进行录音

mod_oreka 支持将音频流发送到 Oreka 录音服务器：

```xml
<!-- conf/autoload_configs/oreka.conf.xml -->
<configuration name="oreka.conf" description="Oreka Recording">
    <settings>
        <param name="sip-server-addr" value="192.168.1.100"/>
        <param name="sip-server-port" value="5060"/>
        <param name="mux-all-streams" value="true"/>
    </settings>
</configuration>
```

---

## 4. 实际应用示例

### 4.1 呼叫中心场景：播放等待音乐并录音

```xml
<extension name="call_center_queue">
    <condition field="destination_number" expression="^8000$">
        <!-- 开始录音 -->
        <action application="set" data="recording_file=/recordings/${strftime(%Y%m%d)}/${uuid}.mp3"/>
        <action application="record_session" data="${recording_file}"/>
        
        <!-- 播放等待音乐（混音模式） -->
        <action application="set" data="hold_music=shout://radio.example.com:8000/hold_music"/>
        
        <!-- 进入队列 -->
        <action application="callcenter" data="support@default"/>
    </condition>
</extension>
```

### 4.2 API 动态控制示例

```bash
#!/bin/bash
# 动态控制脚本

UUID=$1
ACTION=$2

case $ACTION in
    "play_stream")
        # 开始播放音频流
        fs_cli -x "uuid_displace $UUID start shout://stream.example.com:8000/live 0 mux"
        ;;
    "stop_stream")
        # 停止播放
        fs_cli -x "uuid_displace $UUID stop shout://stream.example.com:8000/live"
        ;;
    "start_record")
        # 开始录音
        fs_cli -x "uuid_record $UUID start /recordings/${UUID}.wav"
        ;;
    "stop_record")
        # 停止录音
        fs_cli -x "uuid_record $UUID stop /recordings/${UUID}.wav"
        ;;
esac
```

### 4.3 Lua 完整示例：流播放 + 录音

```lua
-- call_handler.lua
-- 处理呼叫的完整示例

local api = freeswitch.API()
local uuid = session:getVariable("uuid")
local caller_id = session:getVariable("caller_id_number")
local timestamp = os.date("%Y%m%d_%H%M%S")

-- 配置
local recording_path = "/recordings/" .. os.date("%Y/%m/%d/")
local recording_file = recording_path .. uuid .. ".wav"
local stream_url = "shout://music.example.com:8000/hold"

-- 创建录音目录
os.execute("mkdir -p " .. recording_path)

-- 开始录音
session:execute("record_session", recording_file)
freeswitch.consoleLog("INFO", "Recording started: " .. recording_file .. "\n")

-- 应答呼叫
session:answer()
session:sleep(1000)

-- 播放欢迎语
session:streamFile("/sounds/welcome.wav")

-- 开始播放背景音乐（混音模式）
api:execute("uuid_displace", uuid .. " start " .. stream_url .. " 0 mux")
freeswitch.consoleLog("INFO", "Background music started\n")

-- 等待用户输入
local digits = session:playAndGetDigits(1, 4, 3, 5000, "#", 
    "/sounds/enter_extension.wav", 
    "/sounds/invalid.wav", 
    "\\d+")

-- 停止背景音乐
api:execute("uuid_displace", uuid .. " stop " .. stream_url)
freeswitch.consoleLog("INFO", "Background music stopped\n")

-- 处理用户输入
if digits and digits ~= "" then
    freeswitch.consoleLog("INFO", "User entered: " .. digits .. "\n")
    session:execute("transfer", digits .. " XML default")
else
    session:streamFile("/sounds/goodbye.wav")
    session:hangup()
end
```

---

## 5. 常见问题与解决方案

### 5.1 问题：流播放没有声音

**可能原因：**
- mod_shout 未加载
- 流地址不可访问
- 编解码器不匹配

**解决方案：**
```bash
# 检查模块是否加载
fs_cli -x "module_exists mod_shout"

# 手动加载模块
fs_cli -x "load mod_shout"

# 测试流地址
curl -I "http://stream.example.com:8000/live"
```

### 5.2 问题：录音文件为空或损坏

**可能原因：**
- 目录权限问题
- 磁盘空间不足
- 编解码器问题

**解决方案：**
```bash
# 检查目录权限
ls -la /recordings/
chown -R freeswitch:freeswitch /recordings/

# 检查磁盘空间
df -h /recordings/

# 查看 FreeSWITCH 日志
tail -f /var/log/freeswitch/freeswitch.log | grep -i record
```

### 5.3 问题：uuid_displace 返回错误

**常见错误及解决：**

```bash
# 错误：-ERR no channel with uuid
# 解决：确认 UUID 正确且通话仍然活跃
fs_cli -x "show channels"

# 错误：-ERR Cannot open file
# 解决：检查文件路径和权限
ls -la /path/to/audio.wav

# 错误：-ERR File format not supported
# 解决：转换为支持的格式
ffmpeg -i input.mp3 -ar 8000 -ac 1 output.wav
```

### 5.4 最佳实践建议

1. **音频格式**：使用 8kHz 或 16kHz 采样率的 WAV 或 MP3 格式
2. **存储规划**：为录音文件规划足够的存储空间
3. **目录结构**：按日期组织录音文件，便于管理
4. **错误处理**：在脚本中添加错误处理逻辑
5. **性能监控**：监控 FreeSWITCH 的 CPU 和内存使用

---

## 附录：相关 API 命令

| 命令 | 说明 |
|------|------|
| `uuid_displace` | 在通话中混入或替换音频 |
| `uuid_record` | 录制通话音频到文件 |
| `uuid_broadcast` | 向通话播放音频 |
| `uuid_audio` | 控制通话音频设置 |
| `uuid_debug_media` | 调试媒体流 |
| `uuid_getvar` | 获取通道变量 |
| `uuid_setvar` | 设置通道变量 |

---

## 参考资源

- [FreeSWITCH 官方文档](https://freeswitch.org/confluence/)
- [mod_shout 模块文档](https://freeswitch.org/confluence/display/FREESWITCH/mod_shout)
- [uuid_displace API 文档](https://freeswitch.org/confluence/display/FREESWITCH/mod_dptools%3A+uuid_displace)
- [录音配置指南](https://freeswitch.org/confluence/display/FREESWITCH/Recording)
