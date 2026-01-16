# FreeSWITCH 推流播放与打断 Demo

本示例演示如何使用 Node.js (ESL) 控制 FreeSWITCH：
1. **推流播放**: 使用 `playback` 播放远程音频流 (`shout://` 或 `http://`)。
2. **打断**: 监听 DTMF 或 VAD 事件，使用 `uuid_break` 实现打断。

## 前置条件

1. 安装 Node.js。
2. 安装 FreeSWITCH 并确保已编译以下模块 (`modules.conf.xml`):
   - `mod_esl` (支持 ESL 连接)
   - `mod_shout` (支持 HTTP/MP3 流播放)
   - `mod_dptools` (基础 APP)

## 配置 FreeSWITCH

### 1. 配置 Dialplan
在你的 dialplan (例如 `conf/dialplan/default.xml`) 中添加一个 extension，将通话路由到 Node.js 服务：

```xml
<extension name="esl_stream_demo">
  <condition field="destination_number" expression="^5000$">
    <!-- 127.0.0.1:8021 是 Node.js 脚本监听的地址 -->
    <!-- async: 异步模式，允许后台处理事件 -->
    <!-- full: 获取完整的事件数据 -->
    <action application="socket" data="127.0.0.1:8021 async full"/>
  </condition>
</extension>
```

## 运行 Demo

1. 安装依赖:
   ```bash
   cd fs-stream-demo
   npm install
   ```

2. 启动 Node.js 服务:
   ```bash
   node index.js
   ```

3. 发起呼叫:
   使用软电话 (如 Linphone, MicroSIP) 呼叫 `5000`。

## 实现原理

1. **推流播放**: 
   FreeSWITCH 的 `mod_shout` 允许将 URL 作为文件路径播放。
   代码: `conn.execute('playback', 'shout://server/stream.mp3')`

2. **打断 (Barge-in)**:
   - **机制**: 在播放的同时，Node.js 通过 ESL 监听输入事件（DTMF 按键或 VAD 语音检测）。
   - **触发**: 一旦收到事件 (e.g. `DTMF`), 立即调用 `uuid_break` API。
   - **效果**: `uuid_break` 强制停止当前的 `playback` 应用，实现立即打断。

## 扩展：真实的语音打断 (VAD)

本 Demo 使用 DTMF 模拟打断。要实现真实的“说话即打断”，你需要：

1. 启用 VAD 模块 (如 `mod_unimrcp` 连接阿里云/百度 ASR，或 `mod_vmd` 语音检测)。
2. 在 Node.js 的 `answer` 后执行检测指令:
   ```javascript
   conn.execute('detect_speech', 'unimrcp:default default');
   ```
3. 监听 `DETECTED_SPEECH` 事件代替 `DTMF`。
