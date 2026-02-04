# fs-audio-stream

Node.js 文件系统音频流模块，支持播放完成事件（end event）。

## 功能特性

- 📁 从文件系统读取音频流
- ⏯️ 支持播放、暂停、恢复、停止控制
- 🎯 **播放完成事件** (`end`) - 核心功能
- 📊 实时进度跟踪
- 🔔 丰富的事件系统
- 💾 获取完整音频数据 Buffer

## 安装

```bash
npm install fs-audio-stream
```

## 快速开始

### 基本用法 - 监听播放完成事件

```javascript
const { FsAudioStream } = require('fs-audio-stream');

const audioStream = new FsAudioStream();

// 监听播放完成事件
audioStream.on('end', (info) => {
  console.log('播放完成!');
  console.log(`总字节数: ${info.totalBytes}`);
  console.log(`播放时长: ${info.duration} 毫秒`);
  console.log(`完整数据: ${info.buffer.length} 字节`);
});

// 加载并播放
await audioStream.load('./audio.mp3');
await audioStream.play();
```

### 使用 Promise 等待播放完成

```javascript
const { playFile } = require('fs-audio-stream');

// 一行代码播放并等待完成
const result = await playFile('./audio.mp3');
console.log(`播放完成! 时长: ${result.duration}ms`);
```

## API 文档

### 类: FsAudioStream

继承自 `EventEmitter`。

#### 构造函数

```javascript
new FsAudioStream(options)
```

**参数:**
- `options.highWaterMark` (number): 流缓冲区大小，默认 64KB
- `options.autoStart` (boolean): 加载后自动播放，默认 false

#### 属性

| 属性 | 类型 | 描述 |
|------|------|------|
| `state` | string | 当前播放状态 |
| `progress` | number | 播放进度 (0-100) |
| `bytesRead` | number | 已读取字节数 |
| `fileSize` | number | 文件总大小 |

#### 方法

##### `load(filePath): Promise<FsAudioStream>`

加载音频文件。

```javascript
await audioStream.load('./audio.mp3');
```

##### `play(): Promise<void>`

开始播放。返回的 Promise 在播放完成时 resolve。

```javascript
await audioStream.play();
```

##### `pause()`

暂停播放。

##### `resume()`

恢复播放。

##### `stop()`

停止播放。

##### `destroy()`

销毁实例，释放资源。

#### 静态方法

##### `FsAudioStream.playFile(filePath, options): Promise`

快速播放文件并等待完成。

```javascript
const result = await FsAudioStream.playFile('./audio.mp3');
// result: { stream, buffer, duration, totalBytes }
```

### 事件

| 事件 | 描述 | 回调参数 |
|------|------|----------|
| `load` | 文件加载完成 | `{ filePath, fileSize }` |
| `start` | 开始播放 | `{ filePath, fileSize, startTime }` |
| `data` | 接收数据块 | `{ chunk, bytesRead, progress }` |
| `progress` | 进度更新 | `{ bytesRead, totalBytes, percent }` |
| **`end`** | **播放完成** | `{ filePath, totalBytes, duration, endTime, buffer }` |
| `pause` | 暂停 | `{ bytesRead, progress }` |
| `resume` | 恢复 | `{ bytesRead, progress }` |
| `stop` | 停止 | `{ bytesRead, progress }` |
| `error` | 错误 | `Error` |
| `close` | 流关闭 | - |

### 播放完成事件详解

`end` 事件是本模块的核心功能，在音频流播放完成时触发：

```javascript
audioStream.on('end', (info) => {
  // info.filePath - 文件路径
  // info.totalBytes - 总字节数
  // info.duration - 播放持续时间（毫秒）
  // info.endTime - 结束时间 (Date)
  // info.buffer - 完整的音频数据 (Buffer)
});
```

### 播放状态枚举

```javascript
const { PlaybackState } = require('fs-audio-stream');

PlaybackState.IDLE       // 'idle' - 空闲
PlaybackState.PLAYING    // 'playing' - 播放中
PlaybackState.PAUSED     // 'paused' - 已暂停
PlaybackState.COMPLETED  // 'completed' - 已完成
PlaybackState.ERROR      // 'error' - 错误
```

## 工厂函数

```javascript
const { createAudioStream, playFile } = require('fs-audio-stream');

// 创建实例
const stream = createAudioStream({ highWaterMark: 1024 });

// 快速播放
const result = await playFile('./audio.mp3');
```

## 完整示例

### 监听所有事件

```javascript
const { FsAudioStream } = require('fs-audio-stream');

const stream = new FsAudioStream();

stream.on('load', (info) => console.log('已加载:', info.filePath));
stream.on('start', () => console.log('开始播放'));
stream.on('progress', (info) => console.log(`进度: ${info.percent}%`));
stream.on('end', (info) => console.log('播放完成!', info.duration + 'ms'));
stream.on('error', (err) => console.error('错误:', err));

await stream.load('./music.mp3');
await stream.play();

stream.destroy();
```

### 并行处理多个音频流

```javascript
const { FsAudioStream } = require('fs-audio-stream');

const files = ['./audio1.mp3', './audio2.mp3', './audio3.mp3'];

const promises = files.map(async (file) => {
  const stream = new FsAudioStream();
  
  return new Promise((resolve) => {
    stream.on('end', (info) => {
      console.log(`${file} 播放完成`);
      stream.destroy();
      resolve(info);
    });
    
    stream.load(file).then(() => stream.play());
  });
});

const results = await Promise.all(promises);
console.log('所有音频播放完成!');
```

## 运行测试

```bash
npm test
```

## 运行示例

```bash
npm run example
```

## 许可证

MIT
