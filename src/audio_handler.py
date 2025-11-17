#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
音频处理模块
提供音频格式转换、编解码等功能
"""

import io
import wave
import struct
import logging
from typing import Optional, Tuple
from enum import Enum

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class AudioFormat(Enum):
    """音频格式枚举"""
    PCM = "pcm"
    WAV = "wav"
    ULAW = "ulaw"
    ALAW = "alaw"
    OPUS = "opus"


class AudioHandler:
    """音频处理类"""
    
    def __init__(self, sample_rate: int = 16000, channels: int = 1, 
                 sample_width: int = 2):
        """
        初始化音频处理器
        
        Args:
            sample_rate: 采样率
            channels: 声道数
            sample_width: 采样宽度（字节）
        """
        self.sample_rate = sample_rate
        self.channels = channels
        self.sample_width = sample_width
        
        logger.info(f"音频处理器初始化: {sample_rate}Hz, {channels}ch, "
                   f"{sample_width*8}bit")
    
    def pcm_to_wav(self, pcm_data: bytes, output_file: Optional[str] = None) -> bytes:
        """
        将PCM数据转换为WAV格式
        
        Args:
            pcm_data: PCM音频数据
            output_file: 输出文件路径（可选）
            
        Returns:
            WAV格式的音频数据
        """
        # 创建WAV文件
        wav_buffer = io.BytesIO()
        
        with wave.open(wav_buffer, 'wb') as wav_file:
            wav_file.setnchannels(self.channels)
            wav_file.setsampwidth(self.sample_width)
            wav_file.setframerate(self.sample_rate)
            wav_file.writeframes(pcm_data)
        
        wav_data = wav_buffer.getvalue()
        
        # 如果指定了输出文件，保存到文件
        if output_file:
            with open(output_file, 'wb') as f:
                f.write(wav_data)
            logger.info(f"WAV文件已保存: {output_file}")
        
        return wav_data
    
    def wav_to_pcm(self, wav_data: bytes) -> bytes:
        """
        将WAV数据转换为PCM格式
        
        Args:
            wav_data: WAV音频数据
            
        Returns:
            PCM格式的音频数据
        """
        wav_buffer = io.BytesIO(wav_data)
        
        with wave.open(wav_buffer, 'rb') as wav_file:
            # 读取音频参数
            self.channels = wav_file.getnchannels()
            self.sample_width = wav_file.getsampwidth()
            self.sample_rate = wav_file.getframerate()
            
            # 读取PCM数据
            pcm_data = wav_file.readframes(wav_file.getnframes())
        
        return pcm_data
    
    def resample(self, audio_data: bytes, target_rate: int) -> bytes:
        """
        重采样音频数据（简单线性插值）
        
        Args:
            audio_data: 原始音频数据
            target_rate: 目标采样率
            
        Returns:
            重采样后的音频数据
        """
        if self.sample_rate == target_rate:
            return audio_data
        
        # 将字节转换为样本
        samples = struct.unpack(f"{len(audio_data)//self.sample_width}h", audio_data)
        
        # 计算重采样比率
        ratio = target_rate / self.sample_rate
        new_length = int(len(samples) * ratio)
        
        # 线性插值重采样
        resampled = []
        for i in range(new_length):
            pos = i / ratio
            index = int(pos)
            frac = pos - index
            
            if index + 1 < len(samples):
                # 线性插值
                sample = samples[index] * (1 - frac) + samples[index + 1] * frac
            else:
                sample = samples[index]
            
            resampled.append(int(sample))
        
        # 转换回字节
        return struct.pack(f"{len(resampled)}h", *resampled)
    
    def convert_to_mono(self, audio_data: bytes) -> bytes:
        """
        将立体声转换为单声道
        
        Args:
            audio_data: 音频数据
            
        Returns:
            单声道音频数据
        """
        if self.channels == 1:
            return audio_data
        
        # 将字节转换为样本
        samples = struct.unpack(f"{len(audio_data)//self.sample_width}h", audio_data)
        
        # 平均左右声道
        mono_samples = []
        for i in range(0, len(samples), self.channels):
            avg = sum(samples[i:i+self.channels]) // self.channels
            mono_samples.append(avg)
        
        self.channels = 1
        return struct.pack(f"{len(mono_samples)}h", *mono_samples)
    
    def normalize(self, audio_data: bytes, target_level: float = 0.9) -> bytes:
        """
        音频归一化
        
        Args:
            audio_data: 音频数据
            target_level: 目标电平（0-1）
            
        Returns:
            归一化后的音频数据
        """
        # 将字节转换为样本
        samples = list(struct.unpack(f"{len(audio_data)//self.sample_width}h", audio_data))
        
        # 找到最大振幅
        max_amplitude = max(abs(s) for s in samples)
        
        if max_amplitude == 0:
            return audio_data
        
        # 计算缩放因子
        max_value = 32767  # 16-bit最大值
        scale_factor = (max_value * target_level) / max_amplitude
        
        # 应用缩放
        normalized = [int(s * scale_factor) for s in samples]
        
        # 限幅
        normalized = [max(-32768, min(32767, s)) for s in normalized]
        
        return struct.pack(f"{len(normalized)}h", *normalized)
    
    def apply_gain(self, audio_data: bytes, gain_db: float) -> bytes:
        """
        应用增益
        
        Args:
            audio_data: 音频数据
            gain_db: 增益（分贝）
            
        Returns:
            应用增益后的音频数据
        """
        # 将分贝转换为线性增益
        gain_linear = 10 ** (gain_db / 20)
        
        # 将字节转换为样本
        samples = list(struct.unpack(f"{len(audio_data)//self.sample_width}h", audio_data))
        
        # 应用增益
        gained = [int(s * gain_linear) for s in samples]
        
        # 限幅
        gained = [max(-32768, min(32767, s)) for s in gained]
        
        return struct.pack(f"{len(gained)}h", *gained)
    
    def split_into_chunks(self, audio_data: bytes, chunk_duration_ms: int = 100) -> list:
        """
        将音频数据分割成块
        
        Args:
            audio_data: 音频数据
            chunk_duration_ms: 每块时长（毫秒）
            
        Returns:
            音频数据块列表
        """
        # 计算每块的字节数
        bytes_per_second = self.sample_rate * self.channels * self.sample_width
        chunk_size = int(bytes_per_second * chunk_duration_ms / 1000)
        
        # 确保块大小是采样宽度的整数倍
        chunk_size = (chunk_size // self.sample_width) * self.sample_width
        
        # 分割音频
        chunks = []
        for i in range(0, len(audio_data), chunk_size):
            chunk = audio_data[i:i+chunk_size]
            if len(chunk) > 0:
                chunks.append(chunk)
        
        logger.info(f"音频已分割为 {len(chunks)} 块，每块 {chunk_duration_ms}ms")
        return chunks
    
    def detect_silence(self, audio_data: bytes, threshold: int = 500) -> bool:
        """
        检测静音
        
        Args:
            audio_data: 音频数据
            threshold: 静音阈值
            
        Returns:
            是否为静音
        """
        # 将字节转换为样本
        samples = struct.unpack(f"{len(audio_data)//self.sample_width}h", audio_data)
        
        # 计算平均振幅
        avg_amplitude = sum(abs(s) for s in samples) / len(samples)
        
        return avg_amplitude < threshold
    
    def calculate_rms(self, audio_data: bytes) -> float:
        """
        计算音频的RMS（均方根）值
        
        Args:
            audio_data: 音频数据
            
        Returns:
            RMS值
        """
        # 将字节转换为样本
        samples = struct.unpack(f"{len(audio_data)//self.sample_width}h", audio_data)
        
        # 计算RMS
        rms = (sum(s**2 for s in samples) / len(samples)) ** 0.5
        return rms
    
    def get_duration(self, audio_data: bytes) -> float:
        """
        获取音频时长
        
        Args:
            audio_data: 音频数据
            
        Returns:
            时长（秒）
        """
        frames = len(audio_data) // (self.sample_width * self.channels)
        return frames / self.sample_rate
    
    def mix_audio(self, audio1: bytes, audio2: bytes, ratio: float = 0.5) -> bytes:
        """
        混音
        
        Args:
            audio1: 第一个音频数据
            audio2: 第二个音频数据
            ratio: 混音比例（0-1，audio1的比例）
            
        Returns:
            混音后的音频数据
        """
        # 确保两个音频长度相同
        min_length = min(len(audio1), len(audio2))
        audio1 = audio1[:min_length]
        audio2 = audio2[:min_length]
        
        # 将字节转换为样本
        samples1 = struct.unpack(f"{len(audio1)//self.sample_width}h", audio1)
        samples2 = struct.unpack(f"{len(audio2)//self.sample_width}h", audio2)
        
        # 混音
        mixed = []
        for s1, s2 in zip(samples1, samples2):
            mixed_sample = int(s1 * ratio + s2 * (1 - ratio))
            # 限幅
            mixed_sample = max(-32768, min(32767, mixed_sample))
            mixed.append(mixed_sample)
        
        return struct.pack(f"{len(mixed)}h", *mixed)


def main():
    """测试示例"""
    handler = AudioHandler(sample_rate=16000, channels=1, sample_width=2)
    
    # 生成测试音频数据（1秒的静音）
    duration = 1.0
    num_samples = int(handler.sample_rate * duration)
    test_audio = struct.pack(f"{num_samples}h", *[0] * num_samples)
    
    print(f"测试音频信息:")
    print(f"  时长: {handler.get_duration(test_audio):.2f} 秒")
    print(f"  RMS: {handler.calculate_rms(test_audio):.2f}")
    print(f"  是否静音: {handler.detect_silence(test_audio)}")
    
    # 转换为WAV
    wav_data = handler.pcm_to_wav(test_audio, "test_audio.wav")
    print(f"\n✓ 已生成WAV文件: test_audio.wav ({len(wav_data)} 字节)")


if __name__ == "__main__":
    main()
