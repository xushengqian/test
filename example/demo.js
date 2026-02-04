/**
 * fs-audio-stream 使用示例
 * 演示如何使用音频流播放完成事件
 */

const path = require('path');
const fs = require('fs');
const { FsAudioStream, createAudioStream, playFile } = require('../src');

// 创建一个测试音频文件（用于演示）
const testAudioPath = path.join(__dirname, 'test-audio.raw');

/**
 * 创建测试数据文件
 */
function createTestFile() {
  // 创建一个模拟的音频数据（实际使用时替换为真实音频文件）
  const testData = Buffer.alloc(1024 * 100); // 100KB 测试数据
  for (let i = 0; i < testData.length; i++) {
    testData[i] = Math.floor(Math.random() * 256);
  }
  fs.writeFileSync(testAudioPath, testData);
  console.log(`✓ 创建测试文件: ${testAudioPath}`);
  console.log(`  文件大小: ${testData.length} 字节\n`);
}

/**
 * 示例 1: 基本用法 - 监听播放完成事件
 */
async function example1_BasicUsage() {
  console.log('═'.repeat(50));
  console.log('示例 1: 基本用法 - 监听播放完成事件');
  console.log('═'.repeat(50));

  const audioStream = new FsAudioStream();

  // 监听加载事件
  audioStream.on('load', (info) => {
    console.log(`📁 文件已加载: ${info.filePath}`);
    console.log(`   文件大小: ${info.fileSize} 字节`);
  });

  // 监听开始事件
  audioStream.on('start', (info) => {
    console.log(`▶️  开始播放: ${new Date(info.startTime).toLocaleTimeString()}`);
  });

  // 监听进度事件（每25%输出一次）
  let lastProgress = 0;
  audioStream.on('progress', (info) => {
    if (info.percent >= lastProgress + 25) {
      console.log(`   进度: ${info.percent}% (${info.bytesRead}/${info.totalBytes} 字节)`);
      lastProgress = info.percent;
    }
  });

  // ⭐ 核心: 监听播放完成事件
  audioStream.on('end', (info) => {
    console.log(`✅ 播放完成!`);
    console.log(`   总字节数: ${info.totalBytes}`);
    console.log(`   播放时长: ${info.duration} 毫秒`);
    console.log(`   结束时间: ${info.endTime.toLocaleTimeString()}`);
  });

  // 监听错误事件
  audioStream.on('error', (err) => {
    console.error(`❌ 错误: ${err.message}`);
  });

  // 加载并播放
  await audioStream.load(testAudioPath);
  await audioStream.play();
  
  // 清理
  audioStream.destroy();
  console.log('');
}

/**
 * 示例 2: 使用 Promise 方式等待播放完成
 */
async function example2_PromiseStyle() {
  console.log('═'.repeat(50));
  console.log('示例 2: 使用 Promise 方式等待播放完成');
  console.log('═'.repeat(50));

  const audioStream = createAudioStream();

  // 使用 once 监听一次性事件
  const endPromise = new Promise((resolve) => {
    audioStream.once('end', resolve);
  });

  await audioStream.load(testAudioPath);
  audioStream.play(); // 不等待

  console.log('⏳ 等待播放完成...');
  
  const result = await endPromise;
  console.log(`✅ 播放完成! 总共处理 ${result.totalBytes} 字节`);
  
  audioStream.destroy();
  console.log('');
}

/**
 * 示例 3: 使用静态方法快速播放
 */
async function example3_StaticMethod() {
  console.log('═'.repeat(50));
  console.log('示例 3: 使用静态方法快速播放');
  console.log('═'.repeat(50));

  console.log('⏳ 使用 playFile 快速播放...');
  
  const result = await playFile(testAudioPath);
  
  console.log(`✅ 播放完成!`);
  console.log(`   总字节数: ${result.totalBytes}`);
  console.log(`   播放时长: ${result.duration} 毫秒`);
  console.log(`   数据长度: ${result.buffer.length} 字节`);
  
  result.stream.destroy();
  console.log('');
}

/**
 * 示例 4: 暂停/恢复/停止控制
 */
async function example4_PlaybackControl() {
  console.log('═'.repeat(50));
  console.log('示例 4: 暂停/恢复/停止控制');
  console.log('═'.repeat(50));

  const audioStream = new FsAudioStream({
    highWaterMark: 1024 // 使用较小的缓冲区以便更好地演示
  });

  audioStream.on('pause', (info) => {
    console.log(`⏸️  已暂停，进度: ${info.progress}%`);
  });

  audioStream.on('resume', (info) => {
    console.log(`▶️  已恢复，进度: ${info.progress}%`);
  });

  audioStream.on('end', () => {
    console.log(`✅ 播放完成!`);
  });

  await audioStream.load(testAudioPath);
  
  // 开始播放但不等待完成
  const playPromise = audioStream.play();
  
  // 模拟暂停和恢复
  setTimeout(() => {
    audioStream.pause();
    
    setTimeout(() => {
      audioStream.resume();
    }, 100);
  }, 10);

  await playPromise;
  
  audioStream.destroy();
  console.log('');
}

/**
 * 示例 5: 同时处理多个音频流
 */
async function example5_MultipleStreams() {
  console.log('═'.repeat(50));
  console.log('示例 5: 同时处理多个音频流');
  console.log('═'.repeat(50));

  const streams = [
    new FsAudioStream(),
    new FsAudioStream(),
    new FsAudioStream()
  ];

  const playPromises = streams.map(async (stream, index) => {
    stream.on('end', (info) => {
      console.log(`✅ 流 ${index + 1} 播放完成 (${info.duration}ms)`);
    });

    await stream.load(testAudioPath);
    await stream.play();
    stream.destroy();
  });

  console.log('⏳ 同时播放 3 个音频流...');
  await Promise.all(playPromises);
  console.log('✅ 所有音频流播放完成!');
  console.log('');
}

/**
 * 清理测试文件
 */
function cleanup() {
  if (fs.existsSync(testAudioPath)) {
    fs.unlinkSync(testAudioPath);
    console.log('✓ 测试文件已清理');
  }
}

/**
 * 运行所有示例
 */
async function runAllExamples() {
  console.log('\n');
  console.log('╔════════════════════════════════════════════════╗');
  console.log('║     fs-audio-stream 音频流播放完成事件示例     ║');
  console.log('╚════════════════════════════════════════════════╝');
  console.log('\n');

  try {
    // 创建测试文件
    createTestFile();

    // 运行示例
    await example1_BasicUsage();
    await example2_PromiseStyle();
    await example3_StaticMethod();
    await example4_PlaybackControl();
    await example5_MultipleStreams();

    console.log('═'.repeat(50));
    console.log('所有示例运行完成!');
    console.log('═'.repeat(50));

  } catch (error) {
    console.error('运行示例时发生错误:', error);
  } finally {
    // 清理测试文件
    cleanup();
  }
}

// 运行示例
runAllExamples();
