/**
 * fs-audio-stream-player
 * 
 * 基于 Node.js 文件系统的音频流播放器
 * 支持播放完成事件监听
 * 
 * @module fs-audio-stream-player
 */

const { 
  FsAudioStreamPlayer, 
  AudioEvents, 
  PlayState 
} = require('./FsAudioStreamPlayer');

/**
 * 创建播放器实例的工厂函数
 * 
 * @param {Object} options - 配置选项
 * @returns {FsAudioStreamPlayer} 播放器实例
 * 
 * @example
 * const { createPlayer } = require('fs-audio-stream-player');
 * 
 * const player = createPlayer({ bufferSize: 32768 });
 * player.on('complete', () => console.log('播放完成'));
 * player.play('audio.wav');
 */
function createPlayer(options = {}) {
  return new FsAudioStreamPlayer(options);
}

/**
 * 播放音频文件并等待完成
 * 
 * @param {string} filePath - 音频文件路径
 * @param {Object} [options] - 配置选项
 * @returns {Promise<Object>} 完成信息
 * 
 * @example
 * const { playAndWait } = require('fs-audio-stream-player');
 * 
 * // 播放并等待完成
 * const result = await playAndWait('audio.wav');
 * console.log('播放完成:', result);
 */
async function playAndWait(filePath, options = {}) {
  const player = new FsAudioStreamPlayer(options);
  
  return new Promise((resolve, reject) => {
    player.on(AudioEvents.COMPLETE, (info) => {
      player.destroy();
      resolve(info);
    });
    
    player.on(AudioEvents.ERROR, (error) => {
      player.destroy();
      reject(error);
    });
    
    player.play(filePath).catch(reject);
  });
}

/**
 * 监听音频文件播放完成事件（一次性）
 * 
 * @param {string} filePath - 音频文件路径
 * @param {Function} callback - 完成回调
 * @param {Object} [options] - 配置选项
 * @returns {FsAudioStreamPlayer} 播放器实例
 * 
 * @example
 * const { onComplete } = require('fs-audio-stream-player');
 * 
 * onComplete('audio.wav', (info) => {
 *   console.log('音频播放完成!', info);
 * });
 */
function onComplete(filePath, callback, options = {}) {
  const player = new FsAudioStreamPlayer(options);
  
  player.once(AudioEvents.COMPLETE, (info) => {
    callback(info);
    player.destroy();
  });
  
  player.play(filePath).catch((error) => {
    callback({ error, success: false });
    player.destroy();
  });
  
  return player;
}

module.exports = {
  // 主类
  FsAudioStreamPlayer,
  
  // 枚举
  AudioEvents,
  PlayState,
  
  // 工具函数
  createPlayer,
  playAndWait,
  onComplete,
  
  // 默认导出
  default: FsAudioStreamPlayer
};
