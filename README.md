# fs-audio-stream-player

基于 Node.js 文件系统的音频流播放器，**支持播放完成事件监听**。

## 特性

- ✅ **播放完成事件** - 核心功能，当音频流播放完毕时触发 `complete` 事件
- 📊 **进度跟踪** - 实时获取播放进度、已读取字节数
- ⏯ **播放控制** - 支持播放、暂停、恢复、停止操作
- 📡 **事件驱动** - 完整的事件系统，包括开始、进度、完成、错误等
- 🔧 **TypeScript 支持** - 完整的类型定义
- 💡 **简单易用** - 提供便捷的工具函数

## 安装

```bash
npm install fs-audio-stream-player
```

## 快速开始

### 基本用法 - 监听播放完成事件

```javascript
const { FsAudioStreamPlayer } = require('fs-audio-stream-player');

const player = new FsAudioStreamPlayer();

// ⭐ 监听播放完成事件
player.on('complete', (info) => {
  console.log('音频播放完成！');
  console.log('文件:', info.file);
  console.log('持续时间:', info.duration, 'ms');
  console.log('读取字节:', info.bytesRead);
});

// 开始播放
player.play('/path/to/audio.wav');
```

### 使用 Promise 等待播放完成

```javascript
const { playAndWait } = require('fs-audio-stream-player');

async function main() {
  const result = await playAndWait('/path/to/audio.wav');
  console.log('播放完成:', result);
}

main();
```

### 一次性完成回调

```javascript
const { onComplete } = require('fs-audio-stream-player');

onComplete('/path/to/audio.wav', (info) => {
  if (info.success) {
    console.log('音频已播放完成');
  } else {
    console.error('播放失败:', info.error);
  }
});
```

## API 文档

### FsAudioStreamPlayer

主类，继承自 `EventEmitter`。

#### 构造函数

```javascript
const player = new FsAudioStreamPlayer(options);
```

**配置选项：**

| 选项 | 类型 | 默认值 | 描述 |
|------|------|--------|------|
| `bufferSize` | number | 65536 | 缓冲区大小（字节） |
| `sampleRate` | number | 44100 | 采样率 |
| `channels` | number | 2 | 声道数 |
| `bitDepth` | number | 16 | 位深度 |
| `autoClose` | boolean | true | 播放完成后自动关闭流 |
| `progressInterval` | number | 100 | 进度更新间隔（毫秒） |

#### 方法

| 方法 | 描述 | 返回值 |
|------|------|--------|
| `play(filePath, options?)` | 播放音频文件 | `Promise<void>` |
| `pause()` | 暂停播放 | `boolean` |
| `resume()` | 恢复播放 | `boolean` |
| `stop()` | 停止播放 | `Promise<void>` |
| `destroy()` | 销毁播放器实例 | `void` |

#### 属性

| 属性 | 类型 | 描述 |
|------|------|------|
| `state` | string | 当前播放状态 |
| `isPlaying` | boolean | 是否正在播放 |
| `isPaused` | boolean | 是否已暂停 |
| `currentFile` | string | 当前播放文件路径 |
| `progress` | number | 播放进度（0-1） |
| `elapsedTime` | number | 已播放时间（毫秒） |

### 事件

#### complete（播放完成）⭐

**核心事件** - 当音频流播放完毕时触发。

```javascript
player.on('complete', (info) => {
  // info.file       - 播放的文件路径
  // info.duration   - 播放持续时间（毫秒）
  // info.bytesRead  - 已读取字节数
  // info.totalBytes - 总字节数
  // info.timestamp  - 完成时间戳
  // info.success    - 是否成功完成
});
```

#### 其他事件

| 事件 | 描述 |
|------|------|
| `start` | 开始播放 |
| `progress` | 播放进度更新 |
| `pause` | 暂停播放 |
| `resume` | 恢复播放 |
| `stop` | 停止播放 |
| `end` | 播放结束（`complete` 的别名） |
| `error` | 发生错误 |
| `stream:open` | 流打开 |
| `stream:close` | 流关闭 |
| `buffer:update` | 缓冲区更新 |

### 工具函数

#### createPlayer(options?)

创建播放器实例的工厂函数。

```javascript
const { createPlayer } = require('fs-audio-stream-player');
const player = createPlayer({ bufferSize: 32768 });
```

#### playAndWait(filePath, options?)

播放音频文件并等待完成（返回 Promise）。

```javascript
const { playAndWait } = require('fs-audio-stream-player');
const result = await playAndWait('audio.wav');
```

#### onComplete(filePath, callback, options?)

监听音频文件播放完成事件（一次性回调）。

```javascript
const { onComplete } = require('fs-audio-stream-player');
onComplete('audio.wav', (info) => {
  console.log('完成:', info);
});
```

### 状态枚举

```javascript
const { PlayState } = require('fs-audio-stream-player');

PlayState.IDLE       // 空闲
PlayState.PLAYING    // 播放中
PlayState.PAUSED     // 已暂停
PlayState.STOPPED    // 已停止
PlayState.COMPLETED  // 已完成
PlayState.ERROR      // 错误
```

### 事件枚举

```javascript
const { AudioEvents } = require('fs-audio-stream-player');

AudioEvents.START         // 'start'
AudioEvents.PROGRESS      // 'progress'
AudioEvents.PAUSE         // 'pause'
AudioEvents.RESUME        // 'resume'
AudioEvents.STOP          // 'stop'
AudioEvents.COMPLETE      // 'complete' ⭐
AudioEvents.END           // 'end'
AudioEvents.ERROR         // 'error'
AudioEvents.STREAM_OPEN   // 'stream:open'
AudioEvents.STREAM_CLOSE  // 'stream:close'
AudioEvents.BUFFER_UPDATE // 'buffer:update'
```

## 完整示例

```javascript
const { FsAudioStreamPlayer, AudioEvents } = require('fs-audio-stream-player');

const player = new FsAudioStreamPlayer({
  progressInterval: 500
});

// 监听开始事件
player.on(AudioEvents.START, (info) => {
  console.log(`开始播放: ${info.file}`);
});

// 监听进度事件
player.on(AudioEvents.PROGRESS, (info) => {
  console.log(`进度: ${(info.progress * 100).toFixed(1)}%`);
});

// ⭐ 监听播放完成事件
player.on(AudioEvents.COMPLETE, (info) => {
  console.log('🎉 音频播放完成！');
  console.log(`  文件: ${info.file}`);
  console.log(`  持续时间: ${info.duration}ms`);
  console.log(`  读取字节: ${info.bytesRead} / ${info.totalBytes}`);
});

// 监听错误事件
player.on(AudioEvents.ERROR, (info) => {
  console.error('错误:', info.message);
});

// 开始播放
player.play('/path/to/audio.wav');

// 手动控制
// player.pause();   // 暂停
// player.resume();  // 恢复
// player.stop();    // 停止
```

## 运行示例

```bash
# 基本示例
npm run example

# 事件处理示例
npm run example:events
```

## TypeScript 支持

项目包含完整的 TypeScript 类型定义文件 (`src/types.d.ts`)。

```typescript
import { 
  FsAudioStreamPlayer, 
  CompleteEventInfo,
  AudioEvents 
} from 'fs-audio-stream-player';

const player = new FsAudioStreamPlayer();

player.on('complete', (info: CompleteEventInfo) => {
  console.log(info.file, info.duration);
});
```

## License

MIT
