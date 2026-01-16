# 背景
文件名：2026-01-16_1_freeswitch-stream-playback
创建于：2026-01-16
创建者：Cloud Agent
主分支：cursor/freeswitch-c15c
任务分支：cursor/freeswitch-c15c
Yolo模式：Off

# 任务描述
FreeSWITCH 如何基于推流实现语音播放和语音播放打断。

# 分析
## 1. 语音播放（基于推流）
FreeSWITCH 支持播放本地文件和远程流。
- **协议**: HTTP, Shoutcast, RTP。
- **模块**: 
  - `mod_shout`: 用于播放 MP3 和 HTTP 流 (e.g., `shout://example.com/stream.mp3`)。
  - `mod_vlc`: 用于播放更多格式的流 (e.g., `vlc://http://example.com/stream.m3u8`)。
  - `mod_httapi`: 适合 HTTP 接口控制的播放。
- **命令**: `playback` app。

## 2. 语音打断 (Barge-in)
打断需要在播放的同时检测输入音频（VAD - Voice Activity Detection 或 ASR - Automatic Speech Recognition）。
- **机制**:
  - **同步检测**: 使用 `play_and_detect_speech` 或 `play_and_get_digits`。这是阻塞式的，应用会自动处理打断。
  - **异步检测 (推荐)**: 
    1. 开启 VAD/ASR 检测 (e.g., `mod_unimrcp`, `mod_vmd` (Voice Mail Detection), 或自带的 VAD)。
    2. 执行 `playback`。
    3. ESL 监听检测事件 (`DETECTED_SPEECH`, `VAD_DETECTED`)。
    4. 收到事件后，ESL 发送 `uuid_break` 停止当前播放。

## 3. 推荐架构
使用 ESL (Event Socket Library) 配合 Node.js 或 Python 脚本实现业务逻辑。
- 控制灵活。
- 支持复杂的打断逻辑（例如：只在检测到特定关键词时打断，或检测到足够大音量时打断）。

# 提议的解决方案
创建一个示例项目结构，包含：
1. **ESL 脚本 (Node.js)**: 演示如何发起播放并处理打断逻辑。因为 Node.js 的事件驱动模型非常适合处理 ESL 事件。
   - 使用 `mod_shout` 播放流。
   - 演示基于 VAD (Energy detection) 的打断。
2. **说明文档**: 解释配置要求（需要加载的模块等）。

# 实施计划
1.  **创建项目目录**: `fs-stream-demo`
2.  **创建 package.json**: 引入 `modesl` 依赖。
3.  **编写 demo.js**: 
    - 连接 ESL (Inbound/Outbound)。
    - 实现 `playback` 远程流。
    - 实现监听 `DETECTED_SPEECH` 并触发 `uuid_break`。
4.  **编写 README.md**: 详细说明 FreeSWITCH 配置 (`modules.conf.xml` 需要 `mod_shout`) 和运行方法。

# 任务进度
2026-01-16
- 已修改：创建 fs-stream-demo 目录及相关文件 (package.json, index.js, README.md)
- 更改：添加了 Node.js ESL 示例代码和文档
- 原因：演示推流播放和打断逻辑
- 状态：成功

# 最终审查
实施了一套基于 Node.js ESL 的 FreeSWITCH 控制脚本。
- **推流播放**: 通过 `playback shout://...` 实现。
- **语音打断**: 通过监听 `DETECTED_SPEECH` 事件并发送 `uuid_break` 实现。
代码结构清晰，包含必要的配置说明。
