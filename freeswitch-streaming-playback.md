# FreeSWITCH 基于推流实现语音播放和语音播放打断

## 概述

FreeSWITCH 提供了多种方式来实现基于推流的语音播放，以及在播放过程中的打断机制。本文档详细介绍这些技术方案。

---

## 一、推流语音播放方式

### 1.1 mod_shout - HTTP/Shout 流媒体播放

`mod_shout` 模块支持从 HTTP URL 或 Shout 流媒体服务器播放音频。

**启用模块**：
```xml
<!-- conf/autoload_configs/modules.conf.xml -->
<load module="mod_shout"/>
```

**使用示例**：
```xml
<!-- 在 dialplan 中播放 HTTP 流 -->
<action application="playback" data="shout://stream.example.com:8000/audio.mp3"/>

<!-- 播放 HTTP URL -->
<action application="playback" data="http://example.com/audio.mp3"/>
```

**API 命令**：
```bash
# 通过 fs_cli 控制
uuid_broadcast <uuid> shout://stream.example.com:8000/audio.mp3
```

### 1.2 mod_httapi - HTTP API 流媒体

`mod_httapi` 允许通过 HTTP API 动态控制播放内容。

**配置示例**：
```xml
<!-- conf/autoload_configs/httapi.conf.xml -->
<configuration name="httapi.conf" description="HT-TAPI Hypertext Telephony API">
  <settings>
    <param name="debug" value="false"/>
  </settings>
  <profiles>
    <profile name="default">
      <param name="url" value="http://your-server/api/tts"/>
      <param name="method" value="POST"/>
    </profile>
  </profiles>
</configuration>
```

### 1.3 mod_audio_stream - WebSocket 音频流（推荐方案）

`mod_audio_stream` 是实现实时推流播放的最佳选择，支持双向 WebSocket 音频流。

**安装与配置**：
```bash
# 从源码编译
git clone https://github.com/drachtio/freeswitch-modules.git
cd freeswitch-modules/mod_audio_stream
make && make install
```

**启用模块**：
```xml
<load module="mod_audio_stream"/>
```

**使用方式**：

```xml
<!-- dialplan 中启动音频流 -->
<action application="audio_stream" data="ws://your-server:8080/audio start"/>
```

**Lua 脚本控制**：
```lua
-- 启动音频流
session:execute("audio_stream", "ws://tts-server:8080/stream start")

-- 停止音频流
session:execute("audio_stream", "ws://tts-server:8080/stream stop")
```

### 1.4 mod_unimrcp - MRCP 协议集成

通过 MRCP 协议与 TTS 服务器集成，实现语音合成和播放。

```xml
<action application="speak" data="unimrcp:voice|这是要播放的文本"/>
```

---

## 二、自定义推流播放实现

### 2.1 基于 ESL (Event Socket Library) 的推流

**Python ESL 客户端示例**：
```python
import ESL
import io
import wave

class FreeSwitchStreamer:
    def __init__(self, host, port, password):
        self.conn = ESL.ESLconnection(host, port, password)
        
    def stream_audio(self, uuid, audio_data):
        """
        向指定通道推送音频数据
        """
        # 方法1: 使用 uuid_broadcast
        self.conn.api(f"uuid_broadcast {uuid} playback::tone_stream://%(200,100,440)")
        
        # 方法2: 写入临时文件并播放
        temp_file = f"/tmp/stream_{uuid}.wav"
        with wave.open(temp_file, 'wb') as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(8000)
            wav.writeframes(audio_data)
        
        self.conn.api(f"uuid_broadcast {uuid} playback::{temp_file}")
        
    def stop_playback(self, uuid):
        """
        停止当前播放（打断）
        """
        self.conn.api(f"uuid_break {uuid}")
```

### 2.2 基于 WebSocket 的实时推流服务器

**Node.js WebSocket 服务器示例**：
```javascript
const WebSocket = require('ws');
const wss = new WebSocket.Server({ port: 8080 });

wss.on('connection', (ws) => {
    console.log('FreeSWITCH connected');
    
    // 接收来自 TTS 服务的音频流
    ttsService.on('audio', (audioChunk) => {
        if (ws.readyState === WebSocket.OPEN) {
            // 发送 L16 格式音频数据
            ws.send(audioChunk);
        }
    });
    
    ws.on('message', (message) => {
        // 处理来自 FreeSWITCH 的消息
        console.log('Received:', message);
    });
    
    ws.on('close', () => {
        console.log('Connection closed');
    });
});
```

---

## 三、语音播放打断机制

### 3.1 DTMF 按键打断

最基本的打断方式，用户按下按键即可打断当前播放。

```xml
<!-- dialplan 配置 -->
<extension name="play_with_dtmf_break">
    <condition field="destination_number" expression="^1234$">
        <!-- 设置允许 DTMF 打断 -->
        <action application="set" data="playback_terminators=#*0123456789"/>
        <action application="playback" data="/sounds/prompt.wav"/>
    </condition>
</extension>
```

**Lua 实现**：
```lua
-- 设置打断键
session:setVariable("playback_terminators", "#*")

-- 播放可被打断的音频
local digits = session:playAndGetDigits(
    1,      -- min_digits
    1,      -- max_digits
    1,      -- max_tries
    5000,   -- timeout
    "#",    -- terminators
    "/sounds/prompt.wav",  -- audio file
    "",     -- bad_input_audio
    "\\d"   -- digit_regex
)

if digits ~= "" then
    session:consoleLog("INFO", "用户按下: " .. digits .. "，播放被打断\n")
end
```

### 3.2 语音活动检测 (VAD) 打断

用户开口说话时自动打断播放。

```xml
<extension name="play_with_vad_break">
    <condition field="destination_number" expression="^1234$">
        <!-- 启用 VAD -->
        <action application="set" data="playback_sleep_val=100"/>
        <action application="set" data="playback_timeout_sec=30"/>
        
        <!-- 使用 detect_speech 配合播放 -->
        <action application="play_and_detect_speech" 
                data="/sounds/prompt.wav detect:unimrcp {start-input-timers=false}builtin:speech/transcribe"/>
    </condition>
</extension>
```

**Lua 脚本实现 VAD 打断**：
```lua
-- VAD 打断播放器
function playWithVadBreak(session, audioFile)
    local api = freeswitch.API()
    local uuid = session:getVariable("uuid")
    
    -- 启动 VAD 检测
    session:execute("start_dtmf")
    
    -- 设置 VAD 参数
    session:setVariable("vad_mode", "3")  -- 激进模式
    session:setVariable("vad_silence_ms", "300")
    session:setVariable("vad_voice_ms", "100")
    
    -- 启动后台播放
    session:execute("playback", audioFile)
    
    -- 监听语音事件
    local playing = true
    while playing and session:ready() do
        local event = session:getEvent()
        if event then
            local eventName = event:getHeader("Event-Name")
            if eventName == "DETECTED_SPEECH" then
                -- 检测到语音，打断播放
                api:executeString("uuid_break " .. uuid)
                playing = false
                session:consoleLog("INFO", "VAD 检测到语音，打断播放\n")
            end
        end
        session:sleep(50)
    end
end
```

### 3.3 API 命令打断

通过 ESL 或 fs_cli 主动打断播放。

**核心打断命令**：
```bash
# 打断当前播放
uuid_break <uuid>

# 打断并播放其他内容
uuid_broadcast <uuid> playback::/sounds/interrupt.wav

# 暂停播放
uuid_pause <uuid>

# 恢复播放
uuid_pause <uuid> off

# 完全停止并挂断
uuid_kill <uuid>
```

**Python ESL 打断实现**：
```python
import ESL
import threading
import time

class PlaybackController:
    def __init__(self, host='localhost', port='8021', password='ClueCon'):
        self.conn = ESL.ESLconnection(host, int(port), password)
        self.is_playing = {}
        
    def play_with_interrupt(self, uuid, audio_file, interrupt_callback=None):
        """
        带打断支持的播放
        """
        self.is_playing[uuid] = True
        
        # 启动播放
        self.conn.api(f"uuid_broadcast {uuid} playback::{audio_file}")
        
        # 启动打断监听线程
        if interrupt_callback:
            thread = threading.Thread(
                target=self._interrupt_listener,
                args=(uuid, interrupt_callback)
            )
            thread.start()
    
    def _interrupt_listener(self, uuid, callback):
        """
        监听打断信号
        """
        while self.is_playing.get(uuid, False):
            if callback():  # 检查是否需要打断
                self.interrupt(uuid)
                break
            time.sleep(0.1)
    
    def interrupt(self, uuid, reason="user_request"):
        """
        执行打断
        """
        if self.is_playing.get(uuid):
            self.conn.api(f"uuid_break {uuid}")
            self.is_playing[uuid] = False
            print(f"播放被打断: {reason}")
            
    def is_channel_playing(self, uuid):
        """
        检查通道是否在播放
        """
        result = self.conn.api(f"uuid_getvar {uuid} playback_seconds")
        return result.getBody() != "-ERR"
```

### 3.4 事件驱动打断

基于 FreeSWITCH 事件实现智能打断。

**订阅相关事件**：
```python
import ESL

class EventDrivenInterrupt:
    def __init__(self):
        self.conn = ESL.ESLconnection('localhost', 8021, 'ClueCon')
        
        # 订阅相关事件
        self.conn.events('plain', 'DTMF DETECTED_SPEECH CHANNEL_ANSWER PLAYBACK_START PLAYBACK_STOP')
        
    def run(self):
        while True:
            event = self.conn.recvEvent()
            if event:
                event_name = event.getHeader("Event-Name")
                uuid = event.getHeader("Unique-ID")
                
                if event_name == "DTMF":
                    digit = event.getHeader("DTMF-Digit")
                    self._handle_dtmf(uuid, digit)
                    
                elif event_name == "DETECTED_SPEECH":
                    speech_type = event.getHeader("Speech-Type")
                    if speech_type == "begin-speaking":
                        self._handle_speech_start(uuid)
                        
                elif event_name == "PLAYBACK_STOP":
                    self._handle_playback_complete(uuid)
                    
    def _handle_dtmf(self, uuid, digit):
        """处理 DTMF 打断"""
        if digit in ['#', '*']:
            self.conn.api(f"uuid_break {uuid}")
            print(f"DTMF 打断: {digit}")
            
    def _handle_speech_start(self, uuid):
        """处理语音开始（VAD 打断）"""
        self.conn.api(f"uuid_break {uuid}")
        print("检测到语音，打断播放")
        
    def _handle_playback_complete(self, uuid):
        """播放完成处理"""
        print(f"通道 {uuid} 播放完成")
```

---

## 四、完整的推流播放打断架构

### 4.1 架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        应用层                                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │  TTS 服务   │  │   ASR 服务   │  │   业务逻辑   │              │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘              │
│         │                │                │                      │
│         ▼                ▼                ▼                      │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │              WebSocket / HTTP 流媒体网关                  │    │
│  └─────────────────────────┬───────────────────────────────┘    │
└─────────────────────────────┼───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      FreeSWITCH                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │
│  │mod_audio_   │  │ mod_shout   │  │ mod_event_  │              │
│  │  stream     │  │             │  │   socket    │              │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘              │
│         │                │                │                      │
│         ▼                ▼                ▼                      │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    核心交换引擎                           │    │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────┐            │    │
│  │  │ 播放控制  │  │ 打断检测  │  │ 事件分发  │            │    │
│  │  └───────────┘  └───────────┘  └───────────┘            │    │
│  └─────────────────────────┬───────────────────────────────┘    │
└─────────────────────────────┼───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      电话网络                                    │
│        SIP Trunk / WebRTC / PSTN Gateway                        │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 完整示例：智能语音交互系统

**Lua 主控脚本**：
```lua
-- smart_ivr.lua
-- 智能语音交互系统，支持推流播放和多种打断方式

local api = freeswitch.API()
local uuid = session:getVariable("uuid")

-- 配置参数
local config = {
    ws_server = "ws://tts-server:8080/stream",
    asr_server = "ws://asr-server:8080/recognize",
    vad_enabled = true,
    dtmf_break = true,
    break_keys = "#*"
}

-- 初始化打断控制
local interrupt_flag = false
local current_playback_id = nil

-- DTMF 回调处理
function onDTMF(s, type, obj, arg)
    if type == "dtmf" then
        local digit = obj.digit
        freeswitch.consoleLog("INFO", "收到 DTMF: " .. digit .. "\n")
        
        if string.find(config.break_keys, digit) then
            interrupt_flag = true
            if current_playback_id then
                api:executeString("uuid_break " .. uuid)
            end
            return "break"
        end
    end
    return ""
end

-- 注册 DTMF 回调
if config.dtmf_break then
    session:setInputCallback("onDTMF", "")
end

-- 推流播放函数
function streamPlay(text)
    interrupt_flag = false
    current_playback_id = os.time()
    
    -- 启动 WebSocket 音频流
    local stream_cmd = config.ws_server .. "?text=" .. session:urlEncode(text) .. " start"
    session:execute("audio_stream", stream_cmd)
    
    -- 等待播放完成或被打断
    while session:ready() and not interrupt_flag do
        -- 检查 VAD
        if config.vad_enabled then
            local vad_state = session:getVariable("vad_state")
            if vad_state == "speaking" then
                freeswitch.consoleLog("INFO", "VAD 检测到语音，打断播放\n")
                api:executeString("uuid_break " .. uuid)
                interrupt_flag = true
                break
            end
        end
        
        -- 检查播放状态
        local playback_status = session:getVariable("playback_status")
        if playback_status == "done" then
            break
        end
        
        session:sleep(50)
    end
    
    -- 停止音频流
    session:execute("audio_stream", config.ws_server .. " stop")
    current_playback_id = nil
    
    return interrupt_flag
end

-- 语音识别函数
function recognize(timeout)
    local result = nil
    
    -- 启动 ASR 流
    session:execute("audio_stream", config.asr_server .. " start both")
    
    -- 等待识别结果
    local start_time = os.time()
    while session:ready() and (os.time() - start_time) < timeout do
        local event = session:getEvent()
        if event then
            local event_name = event:getHeader("Event-Name")
            if event_name == "DETECTED_SPEECH" then
                result = event:getBody()
                break
            end
        end
        session:sleep(50)
    end
    
    -- 停止 ASR
    session:execute("audio_stream", config.asr_server .. " stop")
    
    return result
end

-- 主交互循环
function main()
    session:answer()
    session:sleep(500)
    
    -- 欢迎语
    local interrupted = streamPlay("您好，欢迎致电智能客服，请问有什么可以帮您？")
    
    if interrupted then
        freeswitch.consoleLog("INFO", "欢迎语被打断，直接进入识别\n")
    end
    
    -- 交互循环
    local max_turns = 10
    for i = 1, max_turns do
        -- 识别用户输入
        local user_input = recognize(10)
        
        if user_input then
            freeswitch.consoleLog("INFO", "用户说: " .. user_input .. "\n")
            
            -- 这里调用业务逻辑获取回复
            local response = getResponse(user_input)
            
            -- 播放回复（可被打断）
            interrupted = streamPlay(response)
            
            if string.find(user_input, "再见") or string.find(user_input, "挂断") then
                streamPlay("感谢您的来电，再见！")
                break
            end
        else
            -- 超时无输入
            streamPlay("抱歉，我没有听清，请再说一遍")
        end
    end
    
    session:hangup()
end

-- 模拟获取回复（实际应调用 AI 服务）
function getResponse(input)
    -- TODO: 集成实际的 AI 对话服务
    return "好的，我已收到您的问题：" .. input .. "，请稍等，正在为您处理。"
end

-- 启动主流程
main()
```

### 4.3 ESL 外部控制示例

**Python 控制器**：
```python
#!/usr/bin/env python3
"""
FreeSWITCH 推流播放控制器
支持实时推送音频和打断控制
"""

import ESL
import asyncio
import websockets
import json
from concurrent.futures import ThreadPoolExecutor

class StreamingPlaybackController:
    def __init__(self, fs_host='localhost', fs_port=8021, fs_password='ClueCon'):
        self.fs_host = fs_host
        self.fs_port = fs_port
        self.fs_password = fs_password
        self.active_sessions = {}
        self.executor = ThreadPoolExecutor(max_workers=10)
        
    def get_connection(self):
        """获取 ESL 连接"""
        return ESL.ESLconnection(self.fs_host, self.fs_port, self.fs_password)
    
    async def stream_tts_audio(self, uuid, text, tts_ws_url):
        """
        从 TTS 服务获取音频并推送到 FreeSWITCH
        """
        conn = self.get_connection()
        
        try:
            async with websockets.connect(tts_ws_url) as ws:
                # 发送 TTS 请求
                await ws.send(json.dumps({
                    "text": text,
                    "voice": "zh-CN-XiaoxiaoNeural",
                    "format": "pcm",
                    "sample_rate": 8000
                }))
                
                # 启动 FreeSWITCH 音频流接收
                conn.api(f"uuid_audio_stream {uuid} start ws://localhost:8765/{uuid}")
                
                self.active_sessions[uuid] = {
                    "playing": True,
                    "ws": ws
                }
                
                # 接收并转发音频数据
                while self.active_sessions.get(uuid, {}).get("playing", False):
                    try:
                        audio_chunk = await asyncio.wait_for(ws.recv(), timeout=0.1)
                        # 转发到 FreeSWITCH
                        # 实际实现需要通过 WebSocket 桥接
                    except asyncio.TimeoutError:
                        continue
                    except websockets.ConnectionClosed:
                        break
                        
        finally:
            if uuid in self.active_sessions:
                del self.active_sessions[uuid]
    
    def interrupt_playback(self, uuid, reason="api_request"):
        """
        打断指定通道的播放
        """
        conn = self.get_connection()
        
        if uuid in self.active_sessions:
            self.active_sessions[uuid]["playing"] = False
            
        # 发送打断命令
        result = conn.api(f"uuid_break {uuid}")
        
        print(f"打断播放 [{uuid}]: {reason}")
        return result.getBody()
    
    def pause_playback(self, uuid):
        """暂停播放"""
        conn = self.get_connection()
        return conn.api(f"uuid_pause {uuid} on").getBody()
    
    def resume_playback(self, uuid):
        """恢复播放"""
        conn = self.get_connection()
        return conn.api(f"uuid_pause {uuid} off").getBody()
    
    def get_playback_status(self, uuid):
        """获取播放状态"""
        conn = self.get_connection()
        
        # 获取播放进度
        seconds = conn.api(f"uuid_getvar {uuid} playback_seconds").getBody()
        samples = conn.api(f"uuid_getvar {uuid} playback_samples").getBody()
        
        return {
            "uuid": uuid,
            "seconds": seconds,
            "samples": samples,
            "is_playing": uuid in self.active_sessions
        }


# 使用示例
if __name__ == "__main__":
    controller = StreamingPlaybackController()
    
    # 示例：播放并在3秒后打断
    import time
    
    uuid = "your-channel-uuid"
    
    # 开始播放
    asyncio.run(controller.stream_tts_audio(
        uuid, 
        "这是一段测试语音，可以被打断",
        "ws://tts-server:8080/synthesize"
    ))
    
    # 3秒后打断
    time.sleep(3)
    controller.interrupt_playback(uuid, "timeout")
```

---

## 五、配置参考

### 5.1 FreeSWITCH 核心配置

**vars.xml**:
```xml
<X-PRE-PROCESS cmd="set" data="default_asr_engine=unimrcp"/>
<X-PRE-PROCESS cmd="set" data="default_tts_engine=unimrcp"/>
```

**event_socket.conf.xml**:
```xml
<configuration name="event_socket.conf" description="Socket Client">
  <settings>
    <param name="nat-map" value="false"/>
    <param name="listen-ip" value="0.0.0.0"/>
    <param name="listen-port" value="8021"/>
    <param name="password" value="ClueCon"/>
    <param name="apply-inbound-acl" value="loopback.auto"/>
  </settings>
</configuration>
```

### 5.2 音频流模块配置

**audio_stream.conf.xml**:
```xml
<configuration name="audio_stream.conf" description="Audio Stream Module">
  <settings>
    <param name="buffer-size" value="8192"/>
    <param name="sample-rate" value="8000"/>
    <param name="channels" value="1"/>
    <param name="encoding" value="L16"/>
  </settings>
</configuration>
```

---

## 六、常见问题与解决方案

### 6.1 音频延迟问题

**问题**：推流播放时存在明显延迟

**解决方案**：
```xml
<!-- 减少 jitter buffer -->
<action application="set" data="jitterbuffer_msec=60:200:40"/>

<!-- 优化编解码 -->
<action application="set" data="absolute_codec_string=PCMU,PCMA"/>
```

### 6.2 打断不及时

**问题**：VAD 打断响应慢

**解决方案**：
```lua
-- 调整 VAD 参数
session:setVariable("vad_mode", "3")  -- 最激进模式
session:setVariable("vad_silence_ms", "200")  -- 减少静音检测时间
session:setVariable("vad_thresh", "100")  -- 降低阈值
```

### 6.3 推流中断

**问题**：WebSocket 连接不稳定

**解决方案**：
```python
# 实现重连机制
async def connect_with_retry(url, max_retries=3):
    for i in range(max_retries):
        try:
            ws = await websockets.connect(url)
            return ws
        except Exception as e:
            if i < max_retries - 1:
                await asyncio.sleep(2 ** i)  # 指数退避
            else:
                raise
```

---

## 七、总结

FreeSWITCH 提供了丰富的推流播放和打断机制：

| 功能 | 模块/方法 | 适用场景 |
|------|----------|---------|
| HTTP 流播放 | mod_shout | 静态音频流 |
| WebSocket 流 | mod_audio_stream | 实时 TTS 集成 |
| MRCP 集成 | mod_unimrcp | 企业级语音服务 |
| DTMF 打断 | playback_terminators | IVR 菜单 |
| VAD 打断 | detect_speech | 智能对话 |
| API 打断 | uuid_break | 外部控制 |

选择合适的方案取决于具体业务需求：
- 简单 IVR：使用 DTMF 打断 + HTTP 流播放
- 智能客服：使用 VAD 打断 + WebSocket 实时流
- 企业集成：使用 MRCP + 事件驱动打断
