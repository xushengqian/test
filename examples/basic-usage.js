/**
 * 基本使用示例
 * 
 * 演示如何使用 FsAudioStreamPlayer 播放音频并监听播放完成事件
 */

const { FsAudioStreamPlayer, AudioEvents } = require('../src');
const path = require('path');

// 创建播放器实例
const player = new FsAudioStreamPlayer({
  bufferSize: 65536,      // 64KB 缓冲区
  progressInterval: 500    // 每500ms更新一次进度
});

// ⭐ 核心功能：监听播放完成事件
player.on(AudioEvents.COMPLETE, (info) => {
  console.log('\n🎉 音频播放完成！');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log(`  文件: ${info.file}`);
  console.log(`  持续时间: ${info.duration}ms`);
  console.log(`  读取字节: ${info.bytesRead} / ${info.totalBytes}`);
  console.log(`  完成时间: ${new Date(info.timestamp).toLocaleString()}`);
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n');
});

// 监听开始播放事件
player.on(AudioEvents.START, (info) => {
  console.log(`\n▶ 开始播放: ${path.basename(info.file)}`);
  console.log(`  文件大小: ${(info.size / 1024).toFixed(2)} KB`);
});

// 监听进度事件
player.on(AudioEvents.PROGRESS, (info) => {
  const percent = (info.progress * 100).toFixed(1);
  const bar = '█'.repeat(Math.floor(info.progress * 20)) + '░'.repeat(20 - Math.floor(info.progress * 20));
  process.stdout.write(`\r  进度: [${bar}] ${percent}%`);
});

// 监听错误事件
player.on(AudioEvents.ERROR, (info) => {
  console.error('\n❌ 播放错误:', info.message);
});

// 开始播放
async function main() {
  // 获取命令行参数中的文件路径，或使用测试文件
  const audioFile = process.argv[2] || './test-audio.wav';
  
  console.log('═══════════════════════════════════════');
  console.log('  FsAudioStreamPlayer - 播放完成事件示例');
  console.log('═══════════════════════════════════════');
  
  try {
    await player.play(audioFile);
    console.log('  正在播放音频流...\n');
  } catch (error) {
    console.error('无法播放文件:', error.message);
    console.log('\n提示: 请提供有效的音频文件路径');
    console.log('用法: node basic-usage.js <音频文件路径>');
  }
}

main();
