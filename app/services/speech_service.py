import asyncio
import io
import logging
import numpy as np
import torch
import whisper
from pydub import AudioSegment
import soundfile as sf
from typing import Optional

logger = logging.getLogger(__name__)

class SpeechService:
    def __init__(self):
        self.model = None
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.sample_rate = 16000
        self._load_model()
    
    def _load_model(self):
        """加载Whisper模型"""
        try:
            # 使用较小的模型以提高实时性能
            self.model = whisper.load_model("base", device=self.device)
            logger.info(f"Whisper model loaded on {self.device}")
        except Exception as e:
            logger.error(f"Failed to load Whisper model: {e}")
            self.model = None
    
    async def transcribe_audio(self, audio_data: bytes) -> Optional[str]:
        """转写音频数据为文本"""
        if not self.model:
            logger.warning("Whisper model not loaded")
            return None
        
        try:
            # 将字节数据转换为音频数组
            audio_array = self._bytes_to_audio_array(audio_data)
            
            if audio_array is None or len(audio_array) == 0:
                return None
            
            # 使用Whisper进行转写
            result = self.model.transcribe(
                audio_array,
                language="zh",  # 中文
                fp16=False,  # 避免精度问题
                verbose=False
            )
            
            text = result["text"].strip()
            if text:
                logger.info(f"Transcribed: {text}")
                return text
            
            return None
            
        except Exception as e:
            logger.error(f"Transcription error: {e}")
            return None
    
    def _bytes_to_audio_array(self, audio_data: bytes) -> Optional[np.ndarray]:
        """将字节数据转换为numpy音频数组"""
        try:
            # 使用pydub处理音频数据
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
    
    async def transcribe_file(self, file_path: str) -> Optional[str]:
        """转写音频文件"""
        try:
            with open(file_path, 'rb') as f:
                audio_data = f.read()
            return await self.transcribe_audio(audio_data)
        except Exception as e:
            logger.error(f"File transcription error: {e}")
            return None