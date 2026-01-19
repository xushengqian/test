# FreeSWITCH HTTP 音频流播放指南

本项目提供 FreeSWITCH 通过 HTTP 拉取音频流播放的完整解决方案，包括配置文档、代码示例和最佳实践。

## 目录结构

```
.
├── README.md                           # 本文件
├── docs/
│   └── freeswitch-http-audio-streaming.md  # 详细技术文档
└── examples/
    ├── lua/                            # Lua 脚本示例
    │   ├── play_http_audio.lua         # 基础 HTTP 音频播放
    │   ├── dynamic_tts_player.lua      # 动态 TTS 播放器
    │   └── http_audio_queue.lua        # 音频队列播放器
    ├── esl/                            # ESL 客户端示例
    │   ├── HttpAudioPlayer.java        # Java ESL 示例
    │   └── http_audio_player.py        # Python ESL 示例
    └── dialplan/                       # Dialplan 配置示例
        ├── http_audio.xml              # HTTP 音频播放 dialplan
        ├── http_cache.conf.xml         # mod_http_cache 配置
        └── shout.conf.xml              # mod_shout 配置
```

## 快速开始

### 1. 加载必需模块

```bash
# 在 fs_cli 中执行
load mod_shout
load mod_http_cache
```

或编辑 `/etc/freeswitch/autoload_configs/modules.conf.xml`：

```xml
<load module="mod_shout"/>
<load module="mod_http_cache"/>
```

### 2. 播放 HTTP 音频

**方式一：播放 MP3（使用 shout 协议）**

```xml
<action application="playback" data="shout://audio.example.com/welcome.mp3"/>
```

**方式二：播放 WAV（使用 http_cache 协议）**

```xml
<action application="playback" data="http_cache://http://audio.example.com/message.wav"/>
```

### 3. 在 Lua 脚本中使用

```lua
session:answer()
session:streamFile("shout://audio.example.com/welcome.mp3")
session:hangup()
```

### 4. 通过 ESL 播放

```bash
# 使用 fs_cli
uuid_broadcast <uuid> shout://audio.example.com/welcome.mp3 both
```

## 支持的协议

| 协议 | 格式 | 说明 |
|------|------|------|
| `shout://` | MP3, Shoutcast, Icecast | 实时流播放，支持网络电台 |
| `http_cache://` | WAV, MP3 等 | 先下载缓存，再播放 |

## 音频格式要求

### 推荐格式

- **WAV**: 8kHz/16kHz, 单声道, PCM 16-bit
- **MP3**: 任意比特率（需要 mod_shout）

### 转换命令

```bash
# 转换为 8kHz WAV
ffmpeg -i input.mp3 -ar 8000 -ac 1 -acodec pcm_s16le output.wav

# 转换为 16kHz WAV
ffmpeg -i input.mp3 -ar 16000 -ac 1 -acodec pcm_s16le output.wav
```

## 常用 API 命令

| 命令 | 说明 |
|------|------|
| `uuid_broadcast <uuid> <url> both` | 播放音频 |
| `uuid_break <uuid> all` | 停止播放 |
| `uuid_displace <uuid> start <url> 0 mux` | 叠加播放 |
| `http_cache clear` | 清理缓存 |
| `http_cache prefetch <url>` | 预取文件 |

## 示例场景

### 场景一：IVR 语音菜单

```xml
<extension name="ivr">
    <condition field="destination_number" expression="^1000$">
        <action application="answer"/>
        <action application="play_and_get_digits" 
                data="1 1 3 5000 # shout://audio.example.com/menu.mp3 shout://audio.example.com/invalid.mp3 choice \d"/>
        <action application="log" data="INFO User choice: ${choice}"/>
    </condition>
</extension>
```

### 场景二：动态 TTS 播放

```lua
local text = "您的验证码是 1234"
local tts_url = "http://tts.example.com/api/speak?text=" .. text
session:streamFile("shout://" .. tts_url)
```

### 场景三：等待队列音乐

```xml
<action application="playback" data="shout://streaming.example.com:8000/moh"/>
```

## 配置优化

### 减少延迟

```xml
<!-- shout.conf.xml -->
<param name="decoder-buffer" value="100"/>
```

### 增加稳定性

```xml
<!-- http_cache.conf.xml -->
<param name="prefetch-thread-count" value="8"/>
<param name="connect-timeout" value="5"/>
```

### 限制缓存大小

```xml
<!-- http_cache.conf.xml -->
<param name="max-size" value="500"/>
<param name="default-cache-time" value="3600"/>
```

## 故障排除

### 问题：mod_shout 未加载

```bash
# 检查模块
fs_cli -x "module_exists mod_shout"

# 加载模块
fs_cli -x "load mod_shout"
```

### 问题：HTTPS 证书错误

```xml
<!-- http_cache.conf.xml -->
<param name="ssl-verify-peer" value="false"/>
```

### 问题：播放延迟高

1. 检查网络连接
2. 减小缓冲区大小
3. 使用预取功能
4. 考虑使用 CDN

## 更多资源

- [详细技术文档](docs/freeswitch-http-audio-streaming.md)
- [FreeSWITCH 官方文档](https://freeswitch.org/confluence/)
- [mod_shout 文档](https://freeswitch.org/confluence/display/FREESWITCH/mod_shout)
- [mod_http_cache 文档](https://freeswitch.org/confluence/display/FREESWITCH/mod_http_cache)

## License

MIT License
