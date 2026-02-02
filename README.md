# fs 获取实时音频流（读取正在增长的文件）

这个示例演示一种常见思路：**把“实时音频流”落盘为一个不断增长的 PCM 文件**，服务端用 `fs` 持续读取新增字节（类似 tail -f），再通过 WebSocket 推给浏览器进行实时播放。

## 运行

安装依赖：

```bash
npm i
```

启动服务端（HTTP + WebSocket）：

```bash
npm run dev
```

浏览器打开终端输出的地址（默认 `http://localhost:5173`）。

再开一个终端，启动“模拟实时音频写入器”（不断往 `data/live.pcm` 追加 48kHz 单声道 PCM）：

```bash
npm run writer:sine
```

回到页面点击“开始播放”，即可听到正弦波（表示你已经用 `fs` 从文件得到了“实时流”）。

## 说明

- **音频格式约定**：默认 `PCM s16le / 48000Hz / mono`。
- **实时读取方式**：服务端轮询 `fs.stat` 检查文件大小增长，然后 `fs.open/read` 读取新增区间并广播。
- **前端播放**：AudioWorklet 接收二进制 PCM，转换为 float 并做简单线性插值重采样到当前设备的 `AudioContext.sampleRate`。

## 可选：接入你的真实音频源

只要你的音频采集/编码流程能持续把 PCM（或你定义的格式）写入 `data/live.pcm`，服务端就能用同样方式“实时获取”并推送。

- 修改输入文件路径：设置环境变量 `AUDIO_PATH`
- 修改采样率等格式：同步修改 `server.js` 的 `AUDIO_FMT` 和前端 worklet 的处理逻辑