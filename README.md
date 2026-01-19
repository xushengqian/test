# FreeSWITCH HTTP 音频流播放指南

本项目演示了如何配置 FreeSWITCH 从 HTTP 源拉取并播放音频流（如网络电台、实时广播等）。

## 核心前提：启用 mod_shout

播放 HTTP MP3 流主要依赖 `mod_shout` 模块。请确保在 FreeSWITCH 中已编译并加载该模块。

1. **检查编译配置** (`modules.conf`):
   确保 `formats/mod_shout` 没有被注释掉。

2. **检查运行配置** (`conf/autoload_configs/modules.conf.xml`):
   确保包含 `<load module="mod_shout"/>`。

3. **控制台验证**:
   在 `fs_cli` 中输入 `module_exists mod_shout` 确认。如果未加载，使用 `load mod_shout` 命令加载。

## 方法一：使用 XML Dialplan

最简单的方法是直接在 Dialplan 中配置 `playback` 动作。

参见 [dialplan/http_stream.xml](dialplan/http_stream.xml)

**配置示例：**
```xml
<action application="set" data="shout_buffer_seconds=5"/>
<action application="playback" data="shout://your.stream.url/file.mp3"/>
```

**关键点：**
- 使用 `shout://` 协议前缀（而不是 `http://`），这告诉 FreeSWITCH 这是一个流，不需要等待下载完成。
- 设置 `shout_buffer_seconds` 变量来优化网络缓冲。

## 方法二：使用 Lua 脚本

如果需要更复杂的控制逻辑（如根据 API 结果选择流），可以使用 Lua 脚本。

参见 [scripts/play_http_stream.lua](scripts/play_http_stream.lua)

**调用方式：**
在 Dialplan 中配置：
```xml
<action application="lua" data="play_http_stream.lua"/>
```

## 参数说明

### URL 格式
- **推荐**: `shout://domain.com/stream.mp3` (强制流模式，适用于直播流和长文件)
- **可选**: `http://domain.com/stream.mp3` (可能被识别为文件下载，FreeSWITCH 可能会尝试下载整个文件后再播放，不适合直播)

### 关键通道变量
- `shout_buffer_seconds`: 缓冲区的音频秒数。默认值较低，建议设置为 5-10 秒以应对网络抖动。
  - XML: `<action application="set" data="shout_buffer_seconds=5"/>`
  - Lua: `session:setVariable("shout_buffer_seconds", "5")`

## 支持的格式
`mod_shout` 主要支持 MP3 格式的流。
- 如果需要播放 **WAV** 文件（非流），可以使用标准的 `http://` 路径，FreeSWITCH 会使用 `mod_http_cache` 下载并缓存。
- 对于其他流格式（如 HLS, RTSP 转 HTTP），可能需要使用 `mod_vlc` 模块，配置类似：`playback(vlc://http://...)`。
