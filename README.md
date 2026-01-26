# FreeSwitch Audio Stream Playback Demo

这个项目演示了如何在 FreeSwitch 中配置和播放网络音频流（如 Shoutcast/Icecast MP3 流）。

## 目录结构

- `conf/dialplan/default/01_stream_demo.xml`: Dialplan 配置示例。
- `scripts/play_stream.lua`: Lua 脚本播放示例。

## 前提条件

为了播放 MP3 或 HTTP 音频流，必须在 FreeSwitch 中加载 `mod_shout` 模块。
如果需要使用 Lua 脚本，必须加载 `mod_lua` 模块。

确保 `conf/autoload_configs/modules.conf.xml` 中包含：
```xml
<load module="mod_shout"/>
<load module="mod_lua"/>
```

## 使用方法

### 1. 安装文件

将 `conf` 和 `scripts` 目录下的文件复制到你的 FreeSwitch 安装目录对应位置（通常是 `/usr/local/freeswitch/` 或 `/etc/freeswitch/`）。

### 2. 重载配置

在 FreeSwitch 控制台 (fs_cli) 执行：
```bash
reloadxml
```

### 3. 测试播放

#### 方法 A: 直接 Dialplan (Extension 9999)
使用软电话（如 Linphone, Zoiper）注册并拨打 `9999`。
- 预期结果：听到 FIP Radio 的广播。
- 实现原理：直接调用 `playback` 应用程序，参数为 `shout://URL`。

#### 方法 B: Lua 脚本 (Extension 9998)
使用软电话拨打 `9998`。
- 预期结果：听到 FIP Radio 的广播，且控制台有 Lua 相关的日志输出。
- 实现原理：Dialplan 调用 `lua play_stream.lua`，脚本中使用 `session:streamFile(url)`。

## 注意事项

- **网络连接**：FreeSwitch 服务器必须能够访问互联网（或流媒体服务器）。
- **编解码器**：确保协商的编解码器支持音频传输（通常 PCMA/PCMU/G722 都可以，因为 FreeSwitch 会转码）。
- **流格式**：`shout://` 前缀强制使用 `mod_shout` 处理。对于某些 HTTPS 流，可能需要配置 SSL 证书或使用 `mod_vlc`。
