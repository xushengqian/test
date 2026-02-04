/**
 * fs-audio-stream 简单测试
 */

const path = require('path');
const fs = require('fs');
const { FsAudioStream, PlaybackState, createAudioStream, playFile } = require('../src');

const testFile = path.join(__dirname, 'test-data.bin');
let passedTests = 0;
let failedTests = 0;

function assert(condition, message) {
  if (condition) {
    console.log(`  ✓ ${message}`);
    passedTests++;
  } else {
    console.log(`  ✗ ${message}`);
    failedTests++;
  }
}

function createTestData() {
  const data = Buffer.alloc(10240); // 10KB
  for (let i = 0; i < data.length; i++) {
    data[i] = i % 256;
  }
  fs.writeFileSync(testFile, data);
}

function cleanup() {
  if (fs.existsSync(testFile)) {
    fs.unlinkSync(testFile);
  }
}

async function testBasicCreation() {
  console.log('\n测试 1: 基本创建');
  
  const stream = new FsAudioStream();
  assert(stream !== null, '创建 FsAudioStream 实例');
  assert(stream.state === PlaybackState.IDLE, '初始状态为 IDLE');
  assert(stream.progress === 0, '初始进度为 0');
  assert(stream.bytesRead === 0, '初始读取字节为 0');
  
  stream.destroy();
}

async function testLoadFile() {
  console.log('\n测试 2: 加载文件');
  
  const stream = new FsAudioStream();
  
  let loadEventFired = false;
  stream.on('load', () => {
    loadEventFired = true;
  });
  
  await stream.load(testFile);
  
  assert(loadEventFired, 'load 事件被触发');
  assert(stream.fileSize === 10240, '文件大小正确');
  assert(stream.state === PlaybackState.IDLE, '加载后状态仍为 IDLE');
  
  stream.destroy();
}

async function testPlayback() {
  console.log('\n测试 3: 播放流程');
  
  const stream = new FsAudioStream();
  
  let startFired = false;
  let endFired = false;
  let dataFired = false;
  let progressFired = false;
  let endData = null;
  
  stream.on('start', () => { startFired = true; });
  stream.on('data', () => { dataFired = true; });
  stream.on('progress', () => { progressFired = true; });
  stream.on('end', (data) => {
    endFired = true;
    endData = data;
  });
  
  await stream.load(testFile);
  await stream.play();
  
  assert(startFired, 'start 事件被触发');
  assert(dataFired, 'data 事件被触发');
  assert(progressFired, 'progress 事件被触发');
  assert(endFired, 'end 事件被触发 (播放完成)');
  assert(stream.state === PlaybackState.COMPLETED, '播放完成后状态为 COMPLETED');
  assert(endData && endData.totalBytes === 10240, '播放完成事件包含正确的字节数');
  assert(endData && endData.buffer && endData.buffer.length === 10240, '播放完成事件包含完整数据');
  assert(endData && typeof endData.duration === 'number', '播放完成事件包含持续时间');
  
  stream.destroy();
}

async function testStaticPlayFile() {
  console.log('\n测试 4: 静态 playFile 方法');
  
  const result = await playFile(testFile);
  
  assert(result !== null, 'playFile 返回结果');
  assert(result.buffer.length === 10240, '返回正确大小的数据');
  assert(typeof result.duration === 'number', '返回持续时间');
  assert(result.totalBytes === 10240, '返回正确的总字节数');
  
  result.stream.destroy();
}

async function testCreateAudioStream() {
  console.log('\n测试 5: createAudioStream 工厂函数');
  
  const stream = createAudioStream({ highWaterMark: 1024 });
  
  assert(stream instanceof FsAudioStream, '返回 FsAudioStream 实例');
  assert(stream.options.highWaterMark === 1024, '配置选项被正确应用');
  
  stream.destroy();
}

async function testErrorHandling() {
  console.log('\n测试 6: 错误处理');
  
  const stream = new FsAudioStream();
  
  let errorFired = false;
  stream.on('error', () => {
    errorFired = true;
  });
  
  try {
    await stream.load('/non/existent/file.mp3');
  } catch (e) {
    // 预期会抛出错误
  }
  
  assert(errorFired, '加载不存在的文件触发 error 事件');
  assert(stream.state === PlaybackState.ERROR, '错误后状态为 ERROR');
  
  stream.destroy();
}

async function testPlaybackState() {
  console.log('\n测试 7: PlaybackState 枚举');
  
  assert(PlaybackState.IDLE === 'idle', 'IDLE 状态值正确');
  assert(PlaybackState.PLAYING === 'playing', 'PLAYING 状态值正确');
  assert(PlaybackState.PAUSED === 'paused', 'PAUSED 状态值正确');
  assert(PlaybackState.COMPLETED === 'completed', 'COMPLETED 状态值正确');
  assert(PlaybackState.ERROR === 'error', 'ERROR 状态值正确');
}

async function runTests() {
  console.log('═'.repeat(50));
  console.log('fs-audio-stream 测试套件');
  console.log('═'.repeat(50));
  
  try {
    createTestData();
    
    await testBasicCreation();
    await testLoadFile();
    await testPlayback();
    await testStaticPlayFile();
    await testCreateAudioStream();
    await testErrorHandling();
    await testPlaybackState();
    
    console.log('\n' + '═'.repeat(50));
    console.log(`测试结果: ${passedTests} 通过, ${failedTests} 失败`);
    console.log('═'.repeat(50));
    
    if (failedTests > 0) {
      process.exit(1);
    }
  } catch (error) {
    console.error('测试运行错误:', error);
    process.exit(1);
  } finally {
    cleanup();
  }
}

runTests();
