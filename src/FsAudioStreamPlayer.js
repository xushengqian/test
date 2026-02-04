/**
 * FsAudioStreamPlayer - 基于文件系统的音频流播放器
 * 
 * 支持播放完成事件监听，提供完整的音频播放生命周期管理
 * 
 * @author fs-audio-stream-player
 * @version 1.0.0
 */

const { EventEmitter } = require('events');
const fs = require('fs');
const path = require('path');

/**
 * 音频播放状态枚举
 */
const PlayState = {
  IDLE: 'idle',
  PLAYING: 'playing',
  PAUSED: 'paused',
  STOPPED: 'stopped',
  COMPLETED: 'completed',
  ERROR: 'error'
};

/**
 * 音频流播放器事件类型
 */
const AudioEvents = {
  START: 'start',           // 开始播放
  PROGRESS: 'progress',     // 播放进度
  PAUSE: 'pause',           // 暂停
  RESUME: 'resume',         // 恢复播放
  STOP: 'stop',             // 停止
  COMPLETE: 'complete',     // 播放完成 ⭐ 核心事件
  END: 'end',               // 播放结束（别名）
  ERROR: 'error',           // 错误
  STREAM_OPEN: 'stream:open',     // 流打开
  STREAM_CLOSE: 'stream:close',   // 流关闭
  BUFFER_UPDATE: 'buffer:update'  // 缓冲区更新
};

/**
 * FsAudioStreamPlayer 类
 * 
 * 提供基于文件系统的音频流播放功能，支持播放完成事件监听
 * 
 * @extends EventEmitter
 * 
 * @example
 * const player = new FsAudioStreamPlayer();
 * 
 * // 监听播放完成事件
 * player.on('complete', (info) => {
 *   console.log('音频播放完成', info);
 * });
 * 
 * // 开始播放
 * player.play('/path/to/audio.wav');
 */
class FsAudioStreamPlayer extends EventEmitter {
  /**
   * 创建音频流播放器实例
   * 
   * @param {Object} options - 配置选项
   * @param {number} [options.bufferSize=65536] - 缓冲区大小（字节）
   * @param {number} [options.sampleRate=44100] - 采样率
   * @param {number} [options.channels=2] - 声道数
   * @param {number} [options.bitDepth=16] - 位深度
   * @param {boolean} [options.autoClose=true] - 播放完成后自动关闭流
   * @param {number} [options.progressInterval=100] - 进度更新间隔（毫秒）
   */
  constructor(options = {}) {
    super();
    
    this.options = {
      bufferSize: options.bufferSize || 65536,
      sampleRate: options.sampleRate || 44100,
      channels: options.channels || 2,
      bitDepth: options.bitDepth || 16,
      autoClose: options.autoClose !== false,
      progressInterval: options.progressInterval || 100,
      ...options
    };
    
    // 当前状态
    this._state = PlayState.IDLE;
    
    // 当前播放信息
    this._currentFile = null;
    this._readStream = null;
    this._startTime = null;
    this._pauseTime = null;
    this._totalPausedTime = 0;
    this._bytesRead = 0;
    this._totalBytes = 0;
    this._progressTimer = null;
    
    // 绑定方法
    this._onStreamData = this._onStreamData.bind(this);
    this._onStreamEnd = this._onStreamEnd.bind(this);
    this._onStreamError = this._onStreamError.bind(this);
    this._onStreamClose = this._onStreamClose.bind(this);
  }
  
  /**
   * 获取当前播放状态
   * @returns {string} 播放状态
   */
  get state() {
    return this._state;
  }
  
  /**
   * 检查是否正在播放
   * @returns {boolean}
   */
  get isPlaying() {
    return this._state === PlayState.PLAYING;
  }
  
  /**
   * 检查是否已暂停
   * @returns {boolean}
   */
  get isPaused() {
    return this._state === PlayState.PAUSED;
  }
  
  /**
   * 获取当前播放文件路径
   * @returns {string|null}
   */
  get currentFile() {
    return this._currentFile;
  }
  
  /**
   * 获取播放进度（0-1）
   * @returns {number}
   */
  get progress() {
    if (this._totalBytes === 0) return 0;
    return Math.min(this._bytesRead / this._totalBytes, 1);
  }
  
  /**
   * 获取已播放时间（毫秒）
   * @returns {number}
   */
  get elapsedTime() {
    if (!this._startTime) return 0;
    
    if (this._state === PlayState.PAUSED && this._pauseTime) {
      return this._pauseTime - this._startTime - this._totalPausedTime;
    }
    
    if (this._state === PlayState.COMPLETED || this._state === PlayState.STOPPED) {
      return this._endTime ? this._endTime - this._startTime - this._totalPausedTime : 0;
    }
    
    return Date.now() - this._startTime - this._totalPausedTime;
  }
  
  /**
   * 播放音频文件
   * 
   * @param {string} filePath - 音频文件路径
   * @param {Object} [playOptions] - 播放选项
   * @param {number} [playOptions.start] - 开始位置（字节）
   * @param {number} [playOptions.end] - 结束位置（字节）
   * @returns {Promise<void>}
   * 
   * @fires FsAudioStreamPlayer#start
   * @fires FsAudioStreamPlayer#complete
   * @fires FsAudioStreamPlayer#error
   * 
   * @example
   * await player.play('/path/to/audio.wav');
   */
  async play(filePath, playOptions = {}) {
    // 如果正在播放，先停止
    if (this._state === PlayState.PLAYING || this._state === PlayState.PAUSED) {
      await this.stop();
    }
    
    try {
      // 验证文件是否存在
      const absolutePath = path.resolve(filePath);
      const stats = await fs.promises.stat(absolutePath);
      
      if (!stats.isFile()) {
        throw new Error(`路径不是文件: ${absolutePath}`);
      }
      
      this._currentFile = absolutePath;
      this._totalBytes = stats.size;
      this._bytesRead = 0;
      this._totalPausedTime = 0;
      this._endTime = null;
      
      // 创建读取流
      const streamOptions = {
        highWaterMark: this.options.bufferSize
      };
      
      if (playOptions.start !== undefined) {
        streamOptions.start = playOptions.start;
      }
      if (playOptions.end !== undefined) {
        streamOptions.end = playOptions.end;
      }
      
      this._readStream = fs.createReadStream(absolutePath, streamOptions);
      
      // 绑定事件监听器
      this._readStream.on('data', this._onStreamData);
      this._readStream.on('end', this._onStreamEnd);
      this._readStream.on('error', this._onStreamError);
      this._readStream.on('close', this._onStreamClose);
      
      // 更新状态
      this._state = PlayState.PLAYING;
      this._startTime = Date.now();
      
      // 触发流打开事件
      this.emit(AudioEvents.STREAM_OPEN, {
        file: absolutePath,
        size: this._totalBytes
      });
      
      // 触发开始播放事件
      /**
       * 开始播放事件
       * @event FsAudioStreamPlayer#start
       * @type {Object}
       * @property {string} file - 文件路径
       * @property {number} size - 文件大小
       * @property {number} timestamp - 时间戳
       */
      this.emit(AudioEvents.START, {
        file: absolutePath,
        size: this._totalBytes,
        timestamp: this._startTime
      });
      
      // 启动进度更新
      this._startProgressTimer();
      
    } catch (error) {
      this._state = PlayState.ERROR;
      this.emit(AudioEvents.ERROR, {
        error,
        file: filePath,
        message: error.message
      });
      throw error;
    }
  }
  
  /**
   * 暂停播放
   * 
   * @returns {boolean} 是否成功暂停
   * @fires FsAudioStreamPlayer#pause
   */
  pause() {
    if (this._state !== PlayState.PLAYING) {
      return false;
    }
    
    if (this._readStream) {
      this._readStream.pause();
    }
    
    this._state = PlayState.PAUSED;
    this._pauseTime = Date.now();
    
    this._stopProgressTimer();
    
    this.emit(AudioEvents.PAUSE, {
      file: this._currentFile,
      progress: this.progress,
      elapsedTime: this.elapsedTime
    });
    
    return true;
  }
  
  /**
   * 恢复播放
   * 
   * @returns {boolean} 是否成功恢复
   * @fires FsAudioStreamPlayer#resume
   */
  resume() {
    if (this._state !== PlayState.PAUSED) {
      return false;
    }
    
    if (this._readStream) {
      this._readStream.resume();
    }
    
    // 计算暂停时间
    if (this._pauseTime) {
      this._totalPausedTime += Date.now() - this._pauseTime;
      this._pauseTime = null;
    }
    
    this._state = PlayState.PLAYING;
    
    this._startProgressTimer();
    
    this.emit(AudioEvents.RESUME, {
      file: this._currentFile,
      progress: this.progress,
      elapsedTime: this.elapsedTime
    });
    
    return true;
  }
  
  /**
   * 停止播放
   * 
   * @returns {Promise<void>}
   * @fires FsAudioStreamPlayer#stop
   */
  async stop() {
    if (this._state === PlayState.IDLE || this._state === PlayState.STOPPED) {
      return;
    }
    
    this._stopProgressTimer();
    
    if (this._readStream) {
      this._readStream.removeListener('data', this._onStreamData);
      this._readStream.removeListener('end', this._onStreamEnd);
      this._readStream.removeListener('error', this._onStreamError);
      this._readStream.removeListener('close', this._onStreamClose);
      
      this._readStream.destroy();
      this._readStream = null;
    }
    
    const previousState = this._state;
    this._state = PlayState.STOPPED;
    this._endTime = Date.now();
    
    this.emit(AudioEvents.STOP, {
      file: this._currentFile,
      progress: this.progress,
      elapsedTime: this.elapsedTime,
      bytesRead: this._bytesRead,
      totalBytes: this._totalBytes,
      wasPlaying: previousState === PlayState.PLAYING
    });
    
    this._resetPlayState();
  }
  
  /**
   * 销毁播放器实例
   */
  destroy() {
    this.stop();
    this.removeAllListeners();
  }
  
  /**
   * 处理流数据事件
   * @private
   */
  _onStreamData(chunk) {
    this._bytesRead += chunk.length;
    
    this.emit(AudioEvents.BUFFER_UPDATE, {
      chunk,
      bytesRead: this._bytesRead,
      totalBytes: this._totalBytes,
      progress: this.progress
    });
  }
  
  /**
   * 处理流结束事件 - 触发播放完成
   * @private
   * @fires FsAudioStreamPlayer#complete
   * @fires FsAudioStreamPlayer#end
   */
  _onStreamEnd() {
    this._stopProgressTimer();
    
    this._state = PlayState.COMPLETED;
    this._endTime = Date.now();
    
    const completionInfo = {
      file: this._currentFile,
      duration: this.elapsedTime,
      bytesRead: this._bytesRead,
      totalBytes: this._totalBytes,
      timestamp: this._endTime,
      success: true
    };
    
    /**
     * 播放完成事件 ⭐
     * 当音频流播放完毕时触发
     * 
     * @event FsAudioStreamPlayer#complete
     * @type {Object}
     * @property {string} file - 播放的文件路径
     * @property {number} duration - 播放持续时间（毫秒）
     * @property {number} bytesRead - 已读取字节数
     * @property {number} totalBytes - 总字节数
     * @property {number} timestamp - 完成时间戳
     * @property {boolean} success - 是否成功完成
     */
    this.emit(AudioEvents.COMPLETE, completionInfo);
    
    /**
     * 播放结束事件（complete 的别名）
     * @event FsAudioStreamPlayer#end
     */
    this.emit(AudioEvents.END, completionInfo);
    
    if (this.options.autoClose) {
      this._resetPlayState();
    }
  }
  
  /**
   * 处理流错误事件
   * @private
   */
  _onStreamError(error) {
    this._stopProgressTimer();
    
    this._state = PlayState.ERROR;
    
    this.emit(AudioEvents.ERROR, {
      error,
      file: this._currentFile,
      message: error.message,
      bytesRead: this._bytesRead
    });
  }
  
  /**
   * 处理流关闭事件
   * @private
   */
  _onStreamClose() {
    this.emit(AudioEvents.STREAM_CLOSE, {
      file: this._currentFile,
      bytesRead: this._bytesRead
    });
  }
  
  /**
   * 启动进度更新定时器
   * @private
   */
  _startProgressTimer() {
    this._stopProgressTimer();
    
    this._progressTimer = setInterval(() => {
      if (this._state === PlayState.PLAYING) {
        /**
         * 播放进度事件
         * @event FsAudioStreamPlayer#progress
         * @type {Object}
         * @property {number} progress - 进度（0-1）
         * @property {number} bytesRead - 已读取字节数
         * @property {number} totalBytes - 总字节数
         * @property {number} elapsedTime - 已播放时间
         */
        this.emit(AudioEvents.PROGRESS, {
          progress: this.progress,
          bytesRead: this._bytesRead,
          totalBytes: this._totalBytes,
          elapsedTime: this.elapsedTime,
          file: this._currentFile
        });
      }
    }, this.options.progressInterval);
  }
  
  /**
   * 停止进度更新定时器
   * @private
   */
  _stopProgressTimer() {
    if (this._progressTimer) {
      clearInterval(this._progressTimer);
      this._progressTimer = null;
    }
  }
  
  /**
   * 重置播放状态
   * @private
   */
  _resetPlayState() {
    this._currentFile = null;
    this._startTime = null;
    this._pauseTime = null;
    this._totalPausedTime = 0;
    this._bytesRead = 0;
    this._totalBytes = 0;
    this._readStream = null;
  }
  
  /**
   * 获取所有支持的事件类型
   * @returns {Object} 事件类型枚举
   */
  static get Events() {
    return AudioEvents;
  }
  
  /**
   * 获取所有播放状态
   * @returns {Object} 状态枚举
   */
  static get States() {
    return PlayState;
  }
}

module.exports = FsAudioStreamPlayer;
module.exports.FsAudioStreamPlayer = FsAudioStreamPlayer;
module.exports.AudioEvents = AudioEvents;
module.exports.PlayState = PlayState;
