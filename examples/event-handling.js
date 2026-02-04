/**
 * 事件处理示例
 * 
 * 演示 FsAudioStreamPlayer 的所有事件和高级用法
 */

const { 
  FsAudioStreamPlayer, 
  AudioEvents, 
  PlayState,
  createPlayer,
  playAndWait,
  onComplete 
} = require('../src');

// ============================================
// 示例 1: 基本的播放完成事件监听
// ============================================
async function example1_BasicComplete() {
  console.log('\n【示例 1】基本的播放完成事件监听');
  console.log('─'.repeat(40));
  
  const player = createPlayer();
  
  // 使用 'complete' 事件
  player.on('complete', (info) => {
    console.log('✅ 播放完成!');
    console.log(`   文件: ${info.file}`);
    console.log(`   时长: ${info.duration}ms`);
  });
  
  // 也可以使用 'end' 事件（别名）
  player.on('end', () => {
    console.log('📍 触发 end 事件（complete 的别名）');
  });
  
  return player;
}

// ============================================
// 示例 2: 使用 Promise 等待播放完成
// ============================================
async function example2_PromiseWait() {
  console.log('\n【示例 2】使用 Promise 等待播放完成');
  console.log('─'.repeat(40));
  
  const audioFile = process.argv[2];
  if (!audioFile) {
    console.log('  跳过（需要提供音频文件）');
    return;
  }
  
  try {
    console.log('  开始播放，等待完成...');
    const result = await playAndWait(audioFile);
    console.log('  ✅ 播放完成:', result);
  } catch (error) {
    console.error('  ❌ 错误:', error.message);
  }
}

// ============================================
// 示例 3: 一次性完成回调
// ============================================
function example3_OnceComplete() {
  console.log('\n【示例 3】一次性完成回调');
  console.log('─'.repeat(40));
  
  const audioFile = process.argv[2];
  if (!audioFile) {
    console.log('  跳过（需要提供音频文件）');
    return;
  }
  
  // 使用 onComplete 便捷函数
  onComplete(audioFile, (info) => {
    if (info.success) {
      console.log('  ✅ 音频已播放完成');
    } else {
      console.log('  ❌ 播放失败:', info.error.message);
    }
  });
}

// ============================================
// 示例 4: 完整的事件生命周期
// ============================================
function example4_FullLifecycle() {
  console.log('\n【示例 4】完整的事件生命周期');
  console.log('─'.repeat(40));
  
  const player = new FsAudioStreamPlayer({
    progressInterval: 200
  });
  
  // 流打开
  player.on(AudioEvents.STREAM_OPEN, (info) => {
    console.log(`  📂 流已打开: ${info.file} (${info.size} bytes)`);
  });
  
  // 开始播放
  player.on(AudioEvents.START, (info) => {
    console.log(`  ▶ 开始播放: ${new Date(info.timestamp).toISOString()}`);
  });
  
  // 缓冲区更新
  let bufferCount = 0;
  player.on(AudioEvents.BUFFER_UPDATE, (info) => {
    bufferCount++;
    if (bufferCount % 10 === 0) {
      console.log(`  📦 缓冲区更新 #${bufferCount}: ${info.bytesRead}/${info.totalBytes} bytes`);
    }
  });
  
  // 进度更新
  player.on(AudioEvents.PROGRESS, (info) => {
    console.log(`  ⏳ 进度: ${(info.progress * 100).toFixed(1)}%`);
  });
  
  // 暂停
  player.on(AudioEvents.PAUSE, (info) => {
    console.log(`  ⏸ 已暂停，进度: ${(info.progress * 100).toFixed(1)}%`);
  });
  
  // 恢复
  player.on(AudioEvents.RESUME, (info) => {
    console.log(`  ⏯ 已恢复，进度: ${(info.progress * 100).toFixed(1)}%`);
  });
  
  // 停止
  player.on(AudioEvents.STOP, (info) => {
    console.log(`  ⏹ 已停止，已播放: ${info.elapsedTime}ms`);
  });
  
  // ⭐ 播放完成
  player.on(AudioEvents.COMPLETE, (info) => {
    console.log(`  ✅ 播放完成!`);
    console.log(`     - 持续时间: ${info.duration}ms`);
    console.log(`     - 读取字节: ${info.bytesRead}`);
    console.log(`     - 缓冲区更新次数: ${bufferCount}`);
  });
  
  // 流关闭
  player.on(AudioEvents.STREAM_CLOSE, (info) => {
    console.log(`  📁 流已关闭: 共读取 ${info.bytesRead} bytes`);
  });
  
  // 错误
  player.on(AudioEvents.ERROR, (info) => {
    console.error(`  ❌ 错误: ${info.message}`);
  });
  
  return player;
}

// ============================================
// 示例 5: 状态检查
// ============================================
function example5_StateCheck() {
  console.log('\n【示例 5】状态检查');
  console.log('─'.repeat(40));
  
  const player = new FsAudioStreamPlayer();
  
  console.log('  可用状态:', Object.values(PlayState));
  console.log('  可用事件:', Object.values(AudioEvents));
  console.log('  初始状态:', player.state);
  console.log('  是否播放中:', player.isPlaying);
  console.log('  是否已暂停:', player.isPaused);
  
  return player;
}

// ============================================
// 示例 6: 手动控制播放
// ============================================
async function example6_ManualControl() {
  console.log('\n【示例 6】手动控制播放');
  console.log('─'.repeat(40));
  
  const player = new FsAudioStreamPlayer();
  
  player.on('complete', (info) => {
    console.log('  ✅ 播放完成，时长:', info.duration, 'ms');
  });
  
  const audioFile = process.argv[2];
  if (!audioFile) {
    console.log('  跳过（需要提供音频文件）');
    console.log('\n  手动控制示例代码:');
    console.log('  ───────────────────');
    console.log('  await player.play("audio.wav");');
    console.log('  player.pause();   // 暂停');
    console.log('  player.resume();  // 恢复');
    console.log('  await player.stop();  // 停止');
    return;
  }
  
  try {
    await player.play(audioFile);
    console.log('  状态:', player.state);
    
    // 2秒后暂停
    setTimeout(() => {
      if (player.isPlaying) {
        player.pause();
        console.log('  ⏸ 已暂停');
        
        // 1秒后恢复
        setTimeout(() => {
          if (player.isPaused) {
            player.resume();
            console.log('  ⏯ 已恢复');
          }
        }, 1000);
      }
    }, 2000);
    
  } catch (error) {
    console.error('  错误:', error.message);
  }
  
  return player;
}

// ============================================
// 主函数
// ============================================
async function main() {
  console.log('═'.repeat(50));
  console.log('  FsAudioStreamPlayer - 事件处理示例');
  console.log('═'.repeat(50));
  
  if (process.argv[2]) {
    console.log(`  音频文件: ${process.argv[2]}`);
  } else {
    console.log('  提示: 可以提供音频文件路径来运行完整示例');
    console.log('  用法: node event-handling.js <音频文件>');
  }
  
  example5_StateCheck();
  example1_BasicComplete();
  example4_FullLifecycle();
  await example6_ManualControl();
  
  console.log('\n═'.repeat(50));
  console.log('  示例运行完成');
  console.log('═'.repeat(50));
}

main().catch(console.error);
