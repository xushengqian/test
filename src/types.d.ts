/**
 * fs-audio-stream-player TypeScript 类型定义
 */

import { EventEmitter } from 'events';

/**
 * 播放状态枚举
 */
export enum PlayState {
  IDLE = 'idle',
  PLAYING = 'playing',
  PAUSED = 'paused',
  STOPPED = 'stopped',
  COMPLETED = 'completed',
  ERROR = 'error'
}

/**
 * 音频事件枚举
 */
export enum AudioEvents {
  START = 'start',
  PROGRESS = 'progress',
  PAUSE = 'pause',
  RESUME = 'resume',
  STOP = 'stop',
  COMPLETE = 'complete',
  END = 'end',
  ERROR = 'error',
  STREAM_OPEN = 'stream:open',
  STREAM_CLOSE = 'stream:close',
  BUFFER_UPDATE = 'buffer:update'
}

/**
 * 播放器配置选项
 */
export interface PlayerOptions {
  /** 缓冲区大小（字节），默认 65536 */
  bufferSize?: number;
  /** 采样率，默认 44100 */
  sampleRate?: number;
  /** 声道数，默认 2 */
  channels?: number;
  /** 位深度，默认 16 */
  bitDepth?: number;
  /** 播放完成后自动关闭流，默认 true */
  autoClose?: boolean;
  /** 进度更新间隔（毫秒），默认 100 */
  progressInterval?: number;
}

/**
 * 播放选项
 */
export interface PlayOptions {
  /** 开始位置（字节） */
  start?: number;
  /** 结束位置（字节） */
  end?: number;
}

/**
 * 开始播放事件信息
 */
export interface StartEventInfo {
  /** 文件路径 */
  file: string;
  /** 文件大小（字节） */
  size: number;
  /** 时间戳 */
  timestamp: number;
}

/**
 * 进度事件信息
 */
export interface ProgressEventInfo {
  /** 进度（0-1） */
  progress: number;
  /** 已读取字节数 */
  bytesRead: number;
  /** 总字节数 */
  totalBytes: number;
  /** 已播放时间（毫秒） */
  elapsedTime: number;
  /** 文件路径 */
  file: string;
}

/**
 * 播放完成事件信息 ⭐
 */
export interface CompleteEventInfo {
  /** 播放的文件路径 */
  file: string;
  /** 播放持续时间（毫秒） */
  duration: number;
  /** 已读取字节数 */
  bytesRead: number;
  /** 总字节数 */
  totalBytes: number;
  /** 完成时间戳 */
  timestamp: number;
  /** 是否成功完成 */
  success: boolean;
}

/**
 * 暂停/恢复事件信息
 */
export interface PauseResumeEventInfo {
  /** 文件路径 */
  file: string;
  /** 当前进度 */
  progress: number;
  /** 已播放时间 */
  elapsedTime: number;
}

/**
 * 停止事件信息
 */
export interface StopEventInfo {
  /** 文件路径 */
  file: string;
  /** 当前进度 */
  progress: number;
  /** 已播放时间 */
  elapsedTime: number;
  /** 已读取字节数 */
  bytesRead: number;
  /** 总字节数 */
  totalBytes: number;
  /** 停止前是否正在播放 */
  wasPlaying: boolean;
}

/**
 * 错误事件信息
 */
export interface ErrorEventInfo {
  /** 错误对象 */
  error: Error;
  /** 文件路径 */
  file: string;
  /** 错误消息 */
  message: string;
  /** 已读取字节数 */
  bytesRead?: number;
}

/**
 * 缓冲区更新事件信息
 */
export interface BufferUpdateEventInfo {
  /** 数据块 */
  chunk: Buffer;
  /** 已读取字节数 */
  bytesRead: number;
  /** 总字节数 */
  totalBytes: number;
  /** 进度 */
  progress: number;
}

/**
 * 流打开事件信息
 */
export interface StreamOpenEventInfo {
  /** 文件路径 */
  file: string;
  /** 文件大小 */
  size: number;
}

/**
 * 流关闭事件信息
 */
export interface StreamCloseEventInfo {
  /** 文件路径 */
  file: string;
  /** 已读取字节数 */
  bytesRead: number;
}

/**
 * FsAudioStreamPlayer 类
 * 
 * 基于文件系统的音频流播放器，支持播放完成事件监听
 */
export class FsAudioStreamPlayer extends EventEmitter {
  /** 配置选项 */
  readonly options: Required<PlayerOptions>;
  
  /** 当前播放状态 */
  readonly state: PlayState;
  
  /** 是否正在播放 */
  readonly isPlaying: boolean;
  
  /** 是否已暂停 */
  readonly isPaused: boolean;
  
  /** 当前播放文件路径 */
  readonly currentFile: string | null;
  
  /** 播放进度（0-1） */
  readonly progress: number;
  
  /** 已播放时间（毫秒） */
  readonly elapsedTime: number;
  
  /** 事件类型枚举 */
  static readonly Events: typeof AudioEvents;
  
  /** 状态枚举 */
  static readonly States: typeof PlayState;
  
  /**
   * 创建播放器实例
   * @param options 配置选项
   */
  constructor(options?: PlayerOptions);
  
  /**
   * 播放音频文件
   * @param filePath 音频文件路径
   * @param playOptions 播放选项
   */
  play(filePath: string, playOptions?: PlayOptions): Promise<void>;
  
  /**
   * 暂停播放
   * @returns 是否成功暂停
   */
  pause(): boolean;
  
  /**
   * 恢复播放
   * @returns 是否成功恢复
   */
  resume(): boolean;
  
  /**
   * 停止播放
   */
  stop(): Promise<void>;
  
  /**
   * 销毁播放器实例
   */
  destroy(): void;
  
  // 事件监听方法重载
  
  on(event: 'start', listener: (info: StartEventInfo) => void): this;
  on(event: 'progress', listener: (info: ProgressEventInfo) => void): this;
  on(event: 'pause', listener: (info: PauseResumeEventInfo) => void): this;
  on(event: 'resume', listener: (info: PauseResumeEventInfo) => void): this;
  on(event: 'stop', listener: (info: StopEventInfo) => void): this;
  on(event: 'complete', listener: (info: CompleteEventInfo) => void): this;
  on(event: 'end', listener: (info: CompleteEventInfo) => void): this;
  on(event: 'error', listener: (info: ErrorEventInfo) => void): this;
  on(event: 'stream:open', listener: (info: StreamOpenEventInfo) => void): this;
  on(event: 'stream:close', listener: (info: StreamCloseEventInfo) => void): this;
  on(event: 'buffer:update', listener: (info: BufferUpdateEventInfo) => void): this;
  on(event: string, listener: (...args: any[]) => void): this;
  
  once(event: 'start', listener: (info: StartEventInfo) => void): this;
  once(event: 'progress', listener: (info: ProgressEventInfo) => void): this;
  once(event: 'pause', listener: (info: PauseResumeEventInfo) => void): this;
  once(event: 'resume', listener: (info: PauseResumeEventInfo) => void): this;
  once(event: 'stop', listener: (info: StopEventInfo) => void): this;
  once(event: 'complete', listener: (info: CompleteEventInfo) => void): this;
  once(event: 'end', listener: (info: CompleteEventInfo) => void): this;
  once(event: 'error', listener: (info: ErrorEventInfo) => void): this;
  once(event: 'stream:open', listener: (info: StreamOpenEventInfo) => void): this;
  once(event: 'stream:close', listener: (info: StreamCloseEventInfo) => void): this;
  once(event: 'buffer:update', listener: (info: BufferUpdateEventInfo) => void): this;
  once(event: string, listener: (...args: any[]) => void): this;
}

/**
 * 创建播放器实例的工厂函数
 * @param options 配置选项
 */
export function createPlayer(options?: PlayerOptions): FsAudioStreamPlayer;

/**
 * 播放音频文件并等待完成
 * @param filePath 音频文件路径
 * @param options 配置选项
 */
export function playAndWait(filePath: string, options?: PlayerOptions): Promise<CompleteEventInfo>;

/**
 * 监听音频文件播放完成事件（一次性）
 * @param filePath 音频文件路径
 * @param callback 完成回调
 * @param options 配置选项
 */
export function onComplete(
  filePath: string, 
  callback: (info: CompleteEventInfo | { error: Error; success: false }) => void, 
  options?: PlayerOptions
): FsAudioStreamPlayer;

export default FsAudioStreamPlayer;
