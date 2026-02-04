/**
 * fs-audio-stream 模块入口
 * 
 * 提供文件系统音频流播放功能，支持播放完成事件
 */

const { FsAudioStream, PlaybackState } = require('./fs-audio-stream');

module.exports = {
  FsAudioStream,
  PlaybackState,
  
  /**
   * 快速创建音频流实例
   * @param {Object} options - 配置选项
   * @returns {FsAudioStream} 音频流实例
   */
  createAudioStream(options) {
    return new FsAudioStream(options);
  },
  
  /**
   * 快速播放音频文件并等待完成
   * @param {string} filePath - 音频文件路径
   * @param {Object} options - 配置选项
   * @returns {Promise<Object>} 播放结果
   */
  async playFile(filePath, options) {
    return FsAudioStream.playFile(filePath, options);
  }
};
