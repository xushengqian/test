import asyncio
import io
import logging
import numpy as np
import torch
import whisper
from pydub import AudioSegment
import soundfile as sf
from typing import Optional, List
import queue
import threading
from concurrent.futures import ThreadPoolExecutor
import time

logger = logging.getLogger(__name__)

class OptimizedSpeechService:
    def __init__(self, max_workers=2, chunk_duration=3.0):
        """
        优化的语音识别服务
        Args:
            max_workers: 最大并发处理线程数
            chunk_duration: 音频块处理时长（秒）
        """
        self.model = None
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.sample_rate = 16000
        self.chunk_duration = chunk_duration
        self.chunk_samples = int(self.sample_rate * chunk_duration)
        
        # 线程池用于并发处理
        self.executor = ThreadPoolExecutor(max_workers=max_workers)
        
        # 音频缓冲区
        self.audio_buffers = {}
        
        # 处理队列
        self.processing_queue = queue.Queue(maxsize=100)
        
        # 启动处理线程
        self._start_processing_thread()
        
        self._load_model()
    
    def _load_model(self):
        """加载Whisper模型"""
        try:
            # 使用更小的模型以提高性能
            self.model = whisper.load_model("tiny", device=self.device)
            logger.info(f"Whisper tiny model loaded on {self.device}")
        except Exception as e:
            logger.error(f"Failed to load Whisper model: {e}")
            self.model = None
    
    def _start_processing_thread(self):
        """启动音频处理线程"""
        def process_worker():
            while True:
                try:
                    call_id, audio_data = self.processing_queue.get(timeout=1)
                    if call_id is None:  # 停止信号
                        break
                    
                    # 处理音频
                    asyncio.create_task(self._process_audio_chunk(call_id, audio_data))
                    self.processing_queue.task_done()
                except queue.Empty:
                    continue
                except Exception as e:
                    logger.error(f"Processing thread error: {e}")
        
        thread = threading.Thread(target=process_worker, daemon=True)
        thread.start()
    
    async def add_audio_data(self, call_id: str, audio_data: bytes):
        """添加音频数据到缓冲区"""
        if call_id not in self.audio_buffers:
            self.audio_buffers[call_id] = []
        
        # 将字节数据转换为音频数组
        audio_array = self._bytes_to_audio_array(audio_data)
        if audio_array is not None:
            self.audio_buffers[call_id].extend(audio_array)
            
            # 检查是否达到处理阈值
            if len(self.audio_buffers[call_id]) >= self.chunk_samples:
                # 提取一个chunk进行处理
                chunk = self.audio_buffers[call_id][:self.chunk_samples]
                self.audio_buffers[call_id] = self.audio_buffers[call_id][self.chunk_samples:]
                
                # 添加到处理队列
                try:
                    self.processing_queue.put_nowait((call_id, chunk))
                except queue.Full:
                    logger.warning(f"Processing queue full, dropping audio for call {call_id}")
    
    async def _process_audio_chunk(self, call_id: str, audio_chunk: np.ndarray):
        """处理音频块"""
        if not self.model or len(audio_chunk) == 0:
            return None
        
        try:
            # 在线程池中运行Whisper推理
            loop = asyncio.get_event_loop()
            result = await loop.run_in_executor(
                self.executor,
                self._transcribe_chunk,
                audio_chunk
            )
            
            if result and result.strip():
                logger.info(f"Transcribed for {call_id}: {result}")
                return result
            
            return None
            
        except Exception as e:
            logger.error(f"Error processing audio chunk for {call_id}: {e}")
            return None
    
    def _transcribe_chunk(self, audio_chunk: np.ndarray) -> str:
        """在线程池中运行Whisper转写"""
        try:
            result = self.model.transcribe(
                audio_chunk,
                language="zh",
                fp16=False,
                verbose=False,
                no_speech_threshold=0.6,  # 提高静音检测阈值
                logprob_threshold=-1.0,   # 降低置信度阈值
                compression_ratio_threshold=2.4  # 提高压缩比阈值
            )
            return result["text"].strip()
        except Exception as e:
            logger.error(f"Whisper transcription error: {e}")
            return ""
    
    def _bytes_to_audio_array(self, audio_data: bytes) -> Optional[np.ndarray]:
        """将字节数据转换为numpy音频数组"""
        try:
            audio = AudioSegment.from_wav(io.BytesIO(audio_data))
            
            # 转换为单声道
            if audio.channels > 1:
                audio = audio.set_channels(1)
            
            # 设置采样率
            if audio.frame_rate != self.sample_rate:
                audio = audio.set_frame_rate(self.sample_rate)
            
            # 转换为numpy数组
            audio_array = np.array(audio.get_array_of_samples(), dtype=np.float32)
            
            # 归一化
            if audio_array.max() > 0:
                audio_array = audio_array / audio_array.max()
            
            return audio_array
            
        except Exception as e:
            logger.error(f"Audio processing error: {e}")
            return None
    
    def cleanup_call(self, call_id: str):
        """清理通话的音频缓冲区"""
        if call_id in self.audio_buffers:
            del self.audio_buffers[call_id]
            logger.info(f"Cleaned up audio buffer for call {call_id}")
    
    def get_queue_size(self) -> int:
        """获取处理队列大小"""
        return self.processing_queue.qsize()
    
    def get_buffer_info(self) -> dict:
        """获取缓冲区信息"""
        return {
            call_id: len(buffer) for call_id, buffer in self.audio_buffers.items()
        }
    
    def shutdown(self):
        """关闭服务"""
        self.executor.shutdown(wait=True)
        # 发送停止信号
        self.processing_queue.put((None, None))