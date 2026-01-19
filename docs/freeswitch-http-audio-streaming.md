# FreeSWITCH HTTP 音频流播放指南

本文档详细介绍如何在 FreeSWITCH 中通过 HTTP 拉取音频流并播放。

## 目录

1. [概述](#概述)
2. [必需模块](#必需模块)
3. [mod_shout - MP3/HTTP 流播放](#mod_shout---mp3http-流播放)
4. [mod_http_cache - HTTP 缓存播放](#mod_http_cache---http-缓存播放)
5. [Dialplan 配置示例](#dialplan-配置示例)
6. [Lua 脚本示例](#lua-脚本示例)
7. [ESL 调用示例](#esl-调用示例)
8. [常见问题与解决方案](#常见问题与解决方案)

---

## 概述

FreeSWITCH 提供了多种方式从 HTTP 服务器拉取音频流进行播放：

| 方式 | 协议前缀 | 支持格式 | 特点 |
|------|----------|----------|------|
| mod_shout | `shout://` | MP3, Shoutcast, Icecast | 实时流播放，支持网络电台 |
| mod_http_cache | `http_cache://` | WAV, MP3 等 | 先下载缓存，再播放，适合静态文件 |
| 直接 HTTP | `http://` (需配置) | 取决于配置 | 需要特定配置支持 |

---

## 必需模块

### 1. mod_shout

用于播放 MP3 格式和 Shoutcast/Icecast 流。

**编译安装：**

```bash
# 在 FreeSWITCH 源码目录
./configure --enable-mod-shout
make mod_shout-install
```

**加载模块（modules.conf.xml）：**

```xml
<load module="mod_shout"/>
```

**运行时加载：**

```bash
# fs_cli 中执行
load mod_shout
```

### 2. mod_http_cache

用于缓存 HTTP 文件到本地后播放。

**加载模块（modules.conf.xml）：**

```xml
<load module="mod_http_cache"/>
```

---

## mod_shout - MP3/HTTP 流播放

### 基本语法

```
shout://[username:password@]server[:port]/path/to/file.mp3
```

### 播放示例

**播放远程 MP3 文件：**

```xml
<action application="playback" data="shout://example.com/audio/welcome.mp3"/>
```

**播放 Shoutcast/Icecast 流：**

```xml
<action application="playback" data="shout://streaming.example.com:8000/live"/>
```

**带认证的播放：**

```xml
<action application="playback" data="shout://user:pass@example.com/protected/audio.mp3"/>
```

### 配置文件 (shout.conf.xml)

位置：`/etc/freeswitch/autoload_configs/shout.conf.xml`

```xml
<configuration name="shout.conf" description="Shout Configuration">
  <settings>
    <!-- 解码器缓冲区大小（毫秒） -->
    <param name="decoder-buffer" value="200"/>
    
    <!-- 音量调整 -->
    <param name="volume" value="1.0"/>
    
    <!-- 采样率转换质量 0-4，4最高 -->
    <param name="resample-quality" value="2"/>
    
    <!-- 超时时间（秒） -->
    <param name="timeout" value="30"/>
  </settings>
</configuration>
```

---

## mod_http_cache - HTTP 缓存播放

### 基本语法

```
http_cache://http://server/path/to/file.wav
```

### 配置文件 (http_cache.conf.xml)

位置：`/etc/freeswitch/autoload_configs/http_cache.conf.xml`

```xml
<configuration name="http_cache.conf" description="HTTP Cache Configuration">
  <settings>
    <!-- 缓存目录 -->
    <param name="location" value="/var/cache/freeswitch/http_cache"/>
    
    <!-- 默认缓存时间（秒），0表示永不过期 -->
    <param name="default-cache-time" value="86400"/>
    
    <!-- 最大缓存大小（MB） -->
    <param name="max-size" value="1000"/>
    
    <!-- 连接超时（秒） -->
    <param name="connect-timeout" value="10"/>
    
    <!-- 读取超时（秒） -->
    <param name="read-timeout" value="30"/>
    
    <!-- 预取线程数 -->
    <param name="prefetch-thread-count" value="4"/>
    
    <!-- SSL 证书验证 -->
    <param name="ssl-verify-peer" value="true"/>
    
    <!-- SSL CA 证书路径 -->
    <param name="ssl-ca-info" value="/etc/ssl/certs/ca-certificates.crt"/>
  </settings>
  
  <!-- 配置多个 HTTP 源 -->
  <profiles>
    <profile name="audio-server">
      <param name="domain" value="audio.example.com"/>
      <param name="aws-s3-access-key-id" value=""/>
      <param name="aws-s3-secret-access-key" value=""/>
    </profile>
  </profiles>
</configuration>
```

### 播放示例

**播放远程 WAV 文件：**

```xml
<action application="playback" data="http_cache://http://example.com/audio/welcome.wav"/>
```

**播放 HTTPS 文件：**

```xml
<action application="playback" data="http_cache://https://example.com/audio/message.wav"/>
```

### 预取文件（提前下载）

```xml
<!-- 在播放前预先下载 -->
<action application="set" data="http_prefetch=http://example.com/audio/long_audio.wav"/>
<action application="playback" data="http_cache://http://example.com/audio/long_audio.wav"/>
```

---

## Dialplan 配置示例

### 基本 HTTP 音频播放

文件：`/etc/freeswitch/dialplan/default/http_audio.xml`

```xml
<include>
  <extension name="play_http_audio">
    <condition field="destination_number" expression="^8001$">
      <!-- 接听电话 -->
      <action application="answer"/>
      <action application="sleep" data="500"/>
      
      <!-- 播放 HTTP MP3 文件 -->
      <action application="playback" data="shout://audio.example.com/welcome.mp3"/>
      
      <!-- 挂断 -->
      <action application="hangup"/>
    </condition>
  </extension>
  
  <extension name="play_cached_audio">
    <condition field="destination_number" expression="^8002$">
      <action application="answer"/>
      <action application="sleep" data="500"/>
      
      <!-- 播放缓存的 HTTP WAV 文件 -->
      <action application="playback" data="http_cache://http://audio.example.com/message.wav"/>
      
      <action application="hangup"/>
    </condition>
  </extension>
  
  <extension name="play_dynamic_tts_audio">
    <condition field="destination_number" expression="^8003$">
      <action application="answer"/>
      
      <!-- 从 TTS 服务获取动态音频 -->
      <action application="set" data="tts_url=http://tts.example.com/api/speak?text=Hello"/>
      <action application="playback" data="shout://${tts_url}"/>
      
      <action application="hangup"/>
    </condition>
  </extension>
</include>
```

### 带变量的动态 URL 播放

```xml
<extension name="dynamic_audio">
  <condition field="destination_number" expression="^800(\d+)$">
    <action application="answer"/>
    
    <!-- 从请求参数或数据库获取音频 URL -->
    <action application="set" data="audio_id=$1"/>
    <action application="set" data="audio_url=http://api.example.com/audio/${audio_id}.mp3"/>
    
    <!-- 播放动态音频 -->
    <action application="playback" data="shout://${audio_url}"/>
    
    <action application="hangup"/>
  </condition>
</extension>
```

---

## Lua 脚本示例

### 基础 HTTP 音频播放

文件：`/etc/freeswitch/scripts/play_http_audio.lua`

```lua
-- play_http_audio.lua
-- FreeSWITCH Lua 脚本：从 HTTP 拉取音频流播放

-- 获取会话对象
local session = session

-- 接听电话
session:answer()
session:sleep(500)

-- 定义音频 URL
local audio_url = "http://audio.example.com/welcome.mp3"

-- 使用 shout 协议播放 MP3
session:streamFile("shout://" .. audio_url)

-- 或者使用 http_cache 播放（会先缓存）
-- session:streamFile("http_cache://http://audio.example.com/welcome.wav")

-- 挂断
session:hangup()
```

### 动态 HTTP 音频播放

```lua
-- dynamic_http_audio.lua
-- 根据来电号码播放不同的音频

local session = session
local caller_id = session:getVariable("caller_id_number") or "unknown"

session:answer()
session:sleep(500)

-- 构建动态 URL
local base_url = "http://audio.example.com/api/greeting"
local audio_url = string.format("%s?caller=%s&format=mp3", base_url, caller_id)

-- 记录日志
freeswitch.consoleLog("INFO", "Playing audio from: " .. audio_url .. "\n")

-- 播放音频
local result = session:streamFile("shout://" .. audio_url)

if result then
    freeswitch.consoleLog("INFO", "Audio playback completed\n")
else
    freeswitch.consoleLog("WARNING", "Audio playback failed\n")
    -- 播放备用音频
    session:streamFile("local_stream://moh")
end

session:hangup()
```

### 带中断检测的播放

```lua
-- interruptible_http_audio.lua
-- 支持按键中断的 HTTP 音频播放

local session = session

session:answer()
session:sleep(500)

-- 设置 DTMF 回调
session:setVariable("playback_terminators", "#")

-- 播放音频，允许用户按 # 键中断
local audio_url = "http://audio.example.com/long_message.mp3"
local digit = session:playAndGetDigits(
    0,      -- min digits
    1,      -- max digits
    1,      -- tries
    5000,   -- timeout
    "#",    -- terminators
    "shout://" .. audio_url,  -- audio file
    "",     -- invalid file
    "\\d",  -- digit regex
    "",     -- variable name
    30000   -- digit timeout
)

if digit and digit ~= "" then
    freeswitch.consoleLog("INFO", "User pressed: " .. digit .. "\n")
end

session:hangup()
```

### 批量音频列表播放

```lua
-- playlist_http_audio.lua
-- 播放 HTTP 音频列表

local session = session
local cjson = require("cjson")

session:answer()
session:sleep(500)

-- 从 HTTP API 获取播放列表
local api = freeswitch.API()
local playlist_url = "http://api.example.com/playlist/123"

-- 使用 curl 获取播放列表（需要 mod_curl）
-- 或者通过其他方式获取

-- 假设播放列表格式
local playlist = {
    "http://audio.example.com/audio1.mp3",
    "http://audio.example.com/audio2.mp3",
    "http://audio.example.com/audio3.mp3"
}

-- 循环播放
for i, url in ipairs(playlist) do
    if not session:ready() then
        freeswitch.consoleLog("INFO", "Session ended, stopping playback\n")
        break
    end
    
    freeswitch.consoleLog("INFO", string.format("Playing %d/%d: %s\n", i, #playlist, url))
    session:streamFile("shout://" .. url)
    
    -- 音频之间的间隔
    session:sleep(500)
end

session:hangup()
```

### HTTP 请求 + 音频播放

```lua
-- http_request_and_play.lua
-- 先发送 HTTP 请求获取音频 URL，然后播放

local session = session
local api = freeswitch.API()

session:answer()
session:sleep(500)

-- 获取通道变量
local call_uuid = session:getVariable("uuid")
local caller_id = session:getVariable("caller_id_number")

-- 构建请求 URL
local request_url = string.format(
    "http://api.example.com/call/audio?uuid=%s&caller=%s",
    call_uuid or "",
    caller_id or ""
)

-- 发送 HTTP 请求获取音频 URL（使用 curl_sendfile 或其他方式）
-- 这里假设返回的是直接的音频 URL
local audio_url = api:executeString("curl " .. request_url)

if audio_url and audio_url ~= "" and not string.match(audio_url, "^-ERR") then
    -- 清理 URL（去除换行符等）
    audio_url = string.gsub(audio_url, "[\r\n]", "")
    
    freeswitch.consoleLog("INFO", "Got audio URL: " .. audio_url .. "\n")
    
    -- 判断是否为 MP3
    if string.match(audio_url, "%.mp3$") or string.match(audio_url, "%.mp3%?") then
        session:streamFile("shout://" .. audio_url)
    else
        -- WAV 或其他格式使用 http_cache
        session:streamFile("http_cache://" .. audio_url)
    end
else
    freeswitch.consoleLog("WARNING", "Failed to get audio URL, playing default\n")
    session:streamFile("local_stream://moh")
end

session:hangup()
```

---

## ESL 调用示例

### Java ESL 示例

```java
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.inbound.InboundConnectionFailure;
import org.freeswitch.esl.client.transport.message.EslMessage;

public class HttpAudioPlayer {
    
    private Client eslClient;
    
    public void connect(String host, int port, String password) throws InboundConnectionFailure {
        eslClient = new Client();
        eslClient.connect(host, port, password, 10);
    }
    
    /**
     * 在指定通道上播放 HTTP 音频
     * @param uuid 通道 UUID
     * @param audioUrl HTTP 音频 URL
     */
    public void playHttpAudio(String uuid, String audioUrl) {
        // 判断音频格式
        String playbackUrl;
        if (audioUrl.endsWith(".mp3") || audioUrl.contains(".mp3?")) {
            // MP3 格式使用 shout 协议
            playbackUrl = "shout://" + audioUrl;
        } else {
            // 其他格式使用 http_cache
            playbackUrl = "http_cache://" + audioUrl;
        }
        
        // 执行播放命令
        String command = String.format("uuid_broadcast %s %s both", uuid, playbackUrl);
        EslMessage response = eslClient.sendSyncApiCommand(command, "");
        
        System.out.println("Playback response: " + response.getBodyLines());
    }
    
    /**
     * 中断当前播放
     * @param uuid 通道 UUID
     */
    public void stopPlayback(String uuid) {
        String command = String.format("uuid_break %s all", uuid);
        eslClient.sendSyncApiCommand(command, "");
    }
    
    /**
     * 使用 uuid_displace 叠加播放音频
     * @param uuid 通道 UUID
     * @param audioUrl HTTP 音频 URL
     */
    public void overlayAudio(String uuid, String audioUrl) {
        String playbackUrl = "shout://" + audioUrl;
        String command = String.format("uuid_displace %s start %s 0 mux", uuid, playbackUrl);
        eslClient.sendSyncApiCommand(command, "");
    }
    
    public void disconnect() {
        if (eslClient != null) {
            eslClient.close();
        }
    }
    
    public static void main(String[] args) {
        HttpAudioPlayer player = new HttpAudioPlayer();
        try {
            player.connect("127.0.0.1", 8021, "ClueCon");
            
            // 播放 HTTP 音频
            String uuid = "your-channel-uuid";
            String audioUrl = "http://audio.example.com/message.mp3";
            player.playHttpAudio(uuid, audioUrl);
            
        } catch (InboundConnectionFailure e) {
            e.printStackTrace();
        } finally {
            player.disconnect();
        }
    }
}
```

### Python ESL 示例

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FreeSWITCH ESL HTTP Audio Player
通过 ESL 从 HTTP 拉取音频流播放
"""

import ESL
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class FreeSwitchHttpAudioPlayer:
    """FreeSWITCH HTTP 音频播放器"""
    
    def __init__(self, host: str = "127.0.0.1", port: int = 8021, password: str = "ClueCon"):
        self.host = host
        self.port = port
        self.password = password
        self.conn = None
    
    def connect(self) -> bool:
        """连接到 FreeSWITCH ESL"""
        try:
            self.conn = ESL.ESLconnection(self.host, str(self.port), self.password)
            if self.conn.connected():
                logger.info(f"Connected to FreeSWITCH at {self.host}:{self.port}")
                return True
            else:
                logger.error("Failed to connect to FreeSWITCH")
                return False
        except Exception as e:
            logger.error(f"Connection error: {e}")
            return False
    
    def disconnect(self):
        """断开连接"""
        if self.conn:
            self.conn.disconnect()
            logger.info("Disconnected from FreeSWITCH")
    
    def _get_playback_url(self, audio_url: str) -> str:
        """根据音频格式获取正确的播放 URL"""
        if audio_url.lower().endswith('.mp3') or '.mp3?' in audio_url.lower():
            return f"shout://{audio_url}"
        elif audio_url.startswith('http://') or audio_url.startswith('https://'):
            return f"http_cache://{audio_url}"
        else:
            return f"http_cache://http://{audio_url}"
    
    def play_audio(self, uuid: str, audio_url: str, leg: str = "both") -> dict:
        """
        在指定通道播放 HTTP 音频
        
        Args:
            uuid: 通道 UUID
            audio_url: HTTP 音频 URL
            leg: 播放方向 (aleg, bleg, both)
        
        Returns:
            执行结果
        """
        if not self.conn or not self.conn.connected():
            return {"success": False, "error": "Not connected"}
        
        playback_url = self._get_playback_url(audio_url)
        command = f"uuid_broadcast {uuid} {playback_url} {leg}"
        
        logger.info(f"Executing: {command}")
        
        result = self.conn.api(command)
        response = result.getBody() if result else ""
        
        success = "-ERR" not in response
        return {
            "success": success,
            "response": response,
            "playback_url": playback_url
        }
    
    def play_and_wait(self, uuid: str, audio_url: str) -> dict:
        """
        播放音频并等待完成
        
        Args:
            uuid: 通道 UUID
            audio_url: HTTP 音频 URL
        
        Returns:
            执行结果
        """
        if not self.conn or not self.conn.connected():
            return {"success": False, "error": "Not connected"}
        
        playback_url = self._get_playback_url(audio_url)
        
        # 使用 uuid_getvar 设置变量
        # 然后使用 playback app
        command = f"uuid_broadcast {uuid} playback::{playback_url}"
        
        result = self.conn.api(command)
        response = result.getBody() if result else ""
        
        return {
            "success": "-ERR" not in response,
            "response": response
        }
    
    def stop_playback(self, uuid: str) -> dict:
        """
        停止当前播放
        
        Args:
            uuid: 通道 UUID
        
        Returns:
            执行结果
        """
        command = f"uuid_break {uuid} all"
        result = self.conn.api(command)
        response = result.getBody() if result else ""
        
        return {
            "success": "-ERR" not in response,
            "response": response
        }
    
    def overlay_audio(self, uuid: str, audio_url: str, volume: int = 0) -> dict:
        """
        叠加播放音频（不中断当前音频）
        
        Args:
            uuid: 通道 UUID
            audio_url: HTTP 音频 URL
            volume: 音量调整 (-4 到 4)
        
        Returns:
            执行结果
        """
        playback_url = self._get_playback_url(audio_url)
        command = f"uuid_displace {uuid} start {playback_url} {volume} mux"
        
        result = self.conn.api(command)
        response = result.getBody() if result else ""
        
        return {
            "success": "-ERR" not in response,
            "response": response
        }
    
    def execute_app(self, uuid: str, app: str, args: str = "") -> dict:
        """
        在通道上执行应用
        
        Args:
            uuid: 通道 UUID
            app: 应用名称
            args: 应用参数
        
        Returns:
            执行结果
        """
        command = f"uuid_transfer {uuid} {args} inline"
        # 或使用 sendmsg
        
        msg = ESL.ESLevent("sendmsg", uuid)
        msg.addHeader("call-command", "execute")
        msg.addHeader("execute-app-name", app)
        if args:
            msg.addHeader("execute-app-arg", args)
        
        result = self.conn.sendEvent(msg)
        return {"success": result is not None}


def main():
    """示例用法"""
    player = FreeSwitchHttpAudioPlayer(
        host="127.0.0.1",
        port=8021,
        password="ClueCon"
    )
    
    if not player.connect():
        return
    
    try:
        # 示例：播放 HTTP 音频
        uuid = "your-channel-uuid-here"
        audio_url = "http://audio.example.com/welcome.mp3"
        
        result = player.play_audio(uuid, audio_url)
        logger.info(f"Play result: {result}")
        
        # 停止播放
        # player.stop_playback(uuid)
        
    finally:
        player.disconnect()


if __name__ == "__main__":
    main()
```

### Node.js ESL 示例

```javascript
/**
 * FreeSWITCH ESL HTTP Audio Player
 * Node.js 版本
 */

const esl = require('modesl');

class FreeSwitchHttpAudioPlayer {
    constructor(host = '127.0.0.1', port = 8021, password = 'ClueCon') {
        this.host = host;
        this.port = port;
        this.password = password;
        this.conn = null;
    }

    /**
     * 连接到 FreeSWITCH
     */
    connect() {
        return new Promise((resolve, reject) => {
            this.conn = new esl.Connection(this.host, this.port, this.password, () => {
                console.log(`Connected to FreeSWITCH at ${this.host}:${this.port}`);
                resolve(true);
            });

            this.conn.on('error', (err) => {
                console.error('Connection error:', err);
                reject(err);
            });
        });
    }

    /**
     * 断开连接
     */
    disconnect() {
        if (this.conn) {
            this.conn.disconnect();
            console.log('Disconnected from FreeSWITCH');
        }
    }

    /**
     * 获取播放 URL
     */
    _getPlaybackUrl(audioUrl) {
        if (audioUrl.toLowerCase().endsWith('.mp3') || audioUrl.toLowerCase().includes('.mp3?')) {
            return `shout://${audioUrl}`;
        } else if (audioUrl.startsWith('http://') || audioUrl.startsWith('https://')) {
            return `http_cache://${audioUrl}`;
        } else {
            return `http_cache://http://${audioUrl}`;
        }
    }

    /**
     * 播放 HTTP 音频
     * @param {string} uuid - 通道 UUID
     * @param {string} audioUrl - HTTP 音频 URL
     * @param {string} leg - 播放方向 (aleg, bleg, both)
     */
    playAudio(uuid, audioUrl, leg = 'both') {
        return new Promise((resolve, reject) => {
            if (!this.conn) {
                reject(new Error('Not connected'));
                return;
            }

            const playbackUrl = this._getPlaybackUrl(audioUrl);
            const command = `uuid_broadcast ${uuid} ${playbackUrl} ${leg}`;

            console.log(`Executing: ${command}`);

            this.conn.api(command, (res) => {
                const response = res.getBody();
                const success = !response.includes('-ERR');
                
                resolve({
                    success,
                    response,
                    playbackUrl
                });
            });
        });
    }

    /**
     * 停止播放
     * @param {string} uuid - 通道 UUID
     */
    stopPlayback(uuid) {
        return new Promise((resolve, reject) => {
            if (!this.conn) {
                reject(new Error('Not connected'));
                return;
            }

            const command = `uuid_break ${uuid} all`;

            this.conn.api(command, (res) => {
                const response = res.getBody();
                resolve({
                    success: !response.includes('-ERR'),
                    response
                });
            });
        });
    }

    /**
     * 叠加播放音频
     * @param {string} uuid - 通道 UUID
     * @param {string} audioUrl - HTTP 音频 URL
     * @param {number} volume - 音量调整
     */
    overlayAudio(uuid, audioUrl, volume = 0) {
        return new Promise((resolve, reject) => {
            if (!this.conn) {
                reject(new Error('Not connected'));
                return;
            }

            const playbackUrl = this._getPlaybackUrl(audioUrl);
            const command = `uuid_displace ${uuid} start ${playbackUrl} ${volume} mux`;

            this.conn.api(command, (res) => {
                const response = res.getBody();
                resolve({
                    success: !response.includes('-ERR'),
                    response
                });
            });
        });
    }
}

// 示例用法
async function main() {
    const player = new FreeSwitchHttpAudioPlayer('127.0.0.1', 8021, 'ClueCon');

    try {
        await player.connect();

        // 播放 HTTP 音频
        const uuid = 'your-channel-uuid-here';
        const audioUrl = 'http://audio.example.com/welcome.mp3';

        const result = await player.playAudio(uuid, audioUrl);
        console.log('Play result:', result);

    } catch (err) {
        console.error('Error:', err);
    } finally {
        player.disconnect();
    }
}

module.exports = FreeSwitchHttpAudioPlayer;

// 如果直接运行
if (require.main === module) {
    main();
}
```

---

## 常见问题与解决方案

### 1. mod_shout 无法播放 MP3

**问题原因：** mod_shout 模块未安装或未加载

**解决方案：**

```bash
# 检查模块是否加载
fs_cli -x "module_exists mod_shout"

# 如果返回 false，加载模块
fs_cli -x "load mod_shout"

# 永久启用，编辑 modules.conf.xml
# <load module="mod_shout"/>
```

### 2. HTTP 音频播放延迟高

**问题原因：** 网络延迟或缓冲区设置不当

**解决方案：**

```xml
<!-- shout.conf.xml -->
<param name="decoder-buffer" value="100"/>  <!-- 减小缓冲区 -->

<!-- http_cache.conf.xml -->
<param name="connect-timeout" value="5"/>
<param name="prefetch-thread-count" value="8"/>  <!-- 增加预取线程 -->
```

### 3. HTTPS 证书验证失败

**问题原因：** SSL 证书验证问题

**解决方案：**

```xml
<!-- http_cache.conf.xml -->
<param name="ssl-verify-peer" value="false"/>  <!-- 禁用验证（不推荐生产环境） -->

<!-- 或者配置正确的 CA 证书 -->
<param name="ssl-ca-info" value="/etc/ssl/certs/ca-certificates.crt"/>
```

### 4. 音频格式不支持

**问题原因：** FreeSWITCH 不支持该音频格式

**解决方案：**

- 将音频转换为支持的格式（WAV 8kHz/16kHz 单声道，或 MP3）
- 使用 FFmpeg 转换：

```bash
# 转换为 8kHz WAV
ffmpeg -i input.mp3 -ar 8000 -ac 1 -acodec pcm_s16le output.wav

# 转换为 16kHz WAV
ffmpeg -i input.mp3 -ar 16000 -ac 1 -acodec pcm_s16le output.wav
```

### 5. 内存占用过高

**问题原因：** HTTP 缓存文件过多

**解决方案：**

```xml
<!-- http_cache.conf.xml -->
<param name="max-size" value="500"/>  <!-- 限制缓存大小 -->
<param name="default-cache-time" value="3600"/>  <!-- 设置缓存过期时间 -->
```

清理缓存：

```bash
# 清理缓存目录
rm -rf /var/cache/freeswitch/http_cache/*

# 或通过 API 清理
fs_cli -x "http_cache clear"
```

### 6. 播放中断或卡顿

**问题原因：** 网络不稳定或服务器响应慢

**解决方案：**

1. 使用 http_cache 预先下载
2. 增加超时时间
3. 使用 CDN 加速音频服务器

```lua
-- 使用预取
session:setVariable("http_prefetch", audio_url)
session:sleep(1000)  -- 等待预取完成
session:streamFile("http_cache://" .. audio_url)
```

---

## API 命令参考

### 播放相关命令

| 命令 | 说明 | 示例 |
|------|------|------|
| `uuid_broadcast` | 向通道广播音频 | `uuid_broadcast <uuid> shout://url both` |
| `uuid_break` | 中断当前播放 | `uuid_break <uuid> all` |
| `uuid_displace` | 叠加/混音播放 | `uuid_displace <uuid> start shout://url 0 mux` |
| `uuid_fileman` | 文件播放管理 | `uuid_fileman <uuid> seek:+5000` |

### HTTP 缓存命令

| 命令 | 说明 | 示例 |
|------|------|------|
| `http_cache clear` | 清理所有缓存 | `http_cache clear` |
| `http_cache remove` | 移除指定缓存 | `http_cache remove http://url` |
| `http_cache prefetch` | 预取文件 | `http_cache prefetch http://url` |

---

## 最佳实践

1. **选择合适的协议**
   - 静态文件：使用 `http_cache://`
   - 实时流/MP3：使用 `shout://`

2. **音频格式**
   - 推荐：WAV (8kHz/16kHz, 单声道, PCM 16-bit)
   - MP3：需要 mod_shout

3. **性能优化**
   - 使用预取功能提前下载
   - 合理配置缓存大小和过期时间
   - 考虑使用 CDN

4. **错误处理**
   - 始终有备用音频
   - 设置合理的超时时间
   - 记录播放失败日志

5. **安全性**
   - 生产环境启用 SSL 验证
   - 音频 URL 做好鉴权
   - 限制可访问的域名
