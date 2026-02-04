/**
 * FsAudioStream - 文件系统音频流类
 * 支持从文件系统读取音频流并触发播放完成等事件
 */

const fs = require('fs');
const path = require('path');
const { EventEmitter } = require('events');

/**
 * 音频流播放状态枚举
 */
const PlaybackState = {
  IDLE: 'idle',
  PLAYING: 'playing',
  PAUSED: 'paused',
  COMPLETED: 'completed',
  ERROR: 'error'
};

/**
 * FsAudioStream 类
 * 用于处理文件系统音频流的播放，并在播放完成时触发事件
 * 
 * @extends EventEmitter
 * 
 * @fires FsAudioStream#start - 开始播放时触发
 * @fires FsAudioStream#data - 接收到数据块时触发
 * @fires FsAudioStream#progress - 播放进度更新时触发
 * @fires FsAudioStream#end - 播放完成时触发
 * @fires FsAudioStream#error - 发生错误时触发
 */
class FsAudioStream extends EventEmitter {
  /**
   * 创建 FsAudioStream 实例
   * @param {Object} options - 配置选项
   * @param {number} [options.highWaterMark=65536] - 流缓冲区大小（字节）
   * @param {boolean} [options.autoStart=false] - 是否自动开始播放
   */
  constructor(options = {}) {
    super();
    
    this.options = {
      highWaterMark: options.highWaterMark || 64 * 1024, // 64KB
      autoStart: options.autoStart || false
    };
    
    this._state = PlaybackState.IDLE;
    this._stream = null;
    this._filePath = null;
    this._fileSize = 0;
    this._bytesRead = 0;
    this._startTime = null;
    this._chunks = [];
  }

  /**
   * 获取当前播放状态
   * @returns {string} 当前状态
   */
  get state() {
    return this._state;
  }

  /**
   * 获取播放进度（0-100）
   * @returns {number} 播放进度百分比
   */
  get progress() {
    if (this._fileSize === 0) return 0;
    return Math.round((this._bytesRead / this._fileSize) * 100);
  }

  /**
   * 获取已读取的字节数
   * @returns {number} 已读取字节数
   */
  get bytesRead() {
    return this._bytesRead;
  }

  /**
   * 获取文件总大小
   * @returns {number} 文件大小（字节）
   */
  get fileSize() {
    return this._fileSize;
  }

  /**
   * 加载音频文件
   * @param {string} filePath - 音频文件路径
   * @returns {Promise<FsAudioStream>} 返回当前实例
   */
  async load(filePath) {
    return new Promise((resolve, reject) => {
      const resolvedPath = path.resolve(filePath);
      
      // 检查文件是否存在
      fs.stat(resolvedPath, (err, stats) => {
        if (err) {
          this._state = PlaybackState.ERROR;
          const error = new Error(`无法加载音频文件: ${err.message}`);
          this.emit('error', error);
          return reject(error);
        }

        if (!stats.isFile()) {
          this._state = PlaybackState.ERROR;
          const error = new Error(`路径不是有效的文件: ${resolvedPath}`);
          this.emit('error', error);
          return reject(error);
        }

        this._filePath = resolvedPath;
        this._fileSize = stats.size;
        this._bytesRead = 0;
        this._chunks = [];
        this._state = PlaybackState.IDLE;

        this.emit('load', {
          filePath: resolvedPath,
          fileSize: stats.size
        });

        if (this.options.autoStart) {
          this.play().then(() => resolve(this)).catch(reject);
        } else {
          resolve(this);
        }
      });
    });
  }

  /**
   * 开始播放音频流
   * @returns {Promise<void>} 播放完成时 resolve
   */
  play() {
    return new Promise((resolve, reject) => {
      if (!this._filePath) {
        const error = new Error('请先使用 load() 方法加载音频文件');
        this.emit('error', error);
        return reject(error);
      }

      if (this._state === PlaybackState.PLAYING) {
        const error = new Error('音频正在播放中');
        this.emit('error', error);
        return reject(error);
      }

      // 创建读取流
      this._stream = fs.createReadStream(this._filePath, {
        highWaterMark: this.options.highWaterMark
      });

      this._state = PlaybackState.PLAYING;
      this._startTime = Date.now();
      this._bytesRead = 0;
      this._chunks = [];

      /**
       * 开始播放事件
       * @event FsAudioStream#start
       * @type {Object}
       * @property {string} filePath - 文件路径
       * @property {number} fileSize - 文件大小
       * @property {Date} startTime - 开始时间
       */
      this.emit('start', {
        filePath: this._filePath,
        fileSize: this._fileSize,
        startTime: new Date(this._startTime)
      });

      // 监听数据事件
      this._stream.on('data', (chunk) => {
        this._bytesRead += chunk.length;
        this._chunks.push(chunk);

        /**
         * 数据接收事件
         * @event FsAudioStream#data
         * @type {Object}
         * @property {Buffer} chunk - 数据块
         * @property {number} bytesRead - 已读取字节数
         * @property {number} progress - 当前进度
         */
        this.emit('data', {
          chunk,
          bytesRead: this._bytesRead,
          progress: this.progress
        });

        /**
         * 进度更新事件
         * @event FsAudioStream#progress
         * @type {Object}
         * @property {number} bytesRead - 已读取字节数
         * @property {number} totalBytes - 总字节数
         * @property {number} percent - 进度百分比
         */
        this.emit('progress', {
          bytesRead: this._bytesRead,
          totalBytes: this._fileSize,
          percent: this.progress
        });
      });

      // 监听流结束事件 - 播放完成
      this._stream.on('end', () => {
        this._state = PlaybackState.COMPLETED;
        const endTime = Date.now();
        const duration = endTime - this._startTime;

        /**
         * 播放完成事件
         * @event FsAudioStream#end
         * @type {Object}
         * @property {string} filePath - 文件路径
         * @property {number} totalBytes - 总字节数
         * @property {number} duration - 播放持续时间（毫秒）
         * @property {Date} endTime - 结束时间
         * @property {Buffer} buffer - 完整的音频数据
         */
        this.emit('end', {
          filePath: this._filePath,
          totalBytes: this._bytesRead,
          duration,
          endTime: new Date(endTime),
          buffer: Buffer.concat(this._chunks)
        });

        resolve();
      });

      // 监听错误事件
      this._stream.on('error', (err) => {
        this._state = PlaybackState.ERROR;
        
        /**
         * 错误事件
         * @event FsAudioStream#error
         * @type {Error}
         */
        this.emit('error', err);
        reject(err);
      });

      // 监听关闭事件
      this._stream.on('close', () => {
        this.emit('close');
      });
    });
  }

  /**
   * 暂停播放
   */
  pause() {
    if (this._stream && this._state === PlaybackState.PLAYING) {
      this._stream.pause();
      this._state = PlaybackState.PAUSED;
      this.emit('pause', {
        bytesRead: this._bytesRead,
        progress: this.progress
      });
    }
  }

  /**
   * 恢复播放
   */
  resume() {
    if (this._stream && this._state === PlaybackState.PAUSED) {
      this._stream.resume();
      this._state = PlaybackState.PLAYING;
      this.emit('resume', {
        bytesRead: this._bytesRead,
        progress: this.progress
      });
    }
  }

  /**
   * 停止播放
   */
  stop() {
    if (this._stream) {
      this._stream.destroy();
      this._stream = null;
      this._state = PlaybackState.IDLE;
      this.emit('stop', {
        bytesRead: this._bytesRead,
        progress: this.progress
      });
    }
  }

  /**
   * 销毁实例，释放资源
   */
  destroy() {
    this.stop();
    this.removeAllListeners();
    this._chunks = [];
    this._filePath = null;
    this._fileSize = 0;
    this._bytesRead = 0;
  }

  /**
   * 静态方法：创建并播放音频流
   * @param {string} filePath - 音频文件路径
   * @param {Object} [options] - 配置选项
   * @returns {Promise<{stream: FsAudioStream, buffer: Buffer}>} 返回流实例和完整数据
   */
  static async playFile(filePath, options = {}) {
    const stream = new FsAudioStream(options);
    
    return new Promise((resolve, reject) => {
      stream.on('end', (data) => {
        resolve({
          stream,
          buffer: data.buffer,
          duration: data.duration,
          totalBytes: data.totalBytes
        });
      });

      stream.on('error', reject);

      stream.load(filePath).then(() => stream.play()).catch(reject);
    });
  }
}

module.exports = {
  FsAudioStream,
  PlaybackState
};
