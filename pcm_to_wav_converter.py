#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PCM到WAV转换器
支持将原始PCM音频数据转换为标准WAV格式文件
"""

import struct
import os
from typing import Union


class PCMToWAVConverter:
    """PCM到WAV格式转换器类"""
    
    def __init__(self, 
                 sample_rate: int = 16000, 
                 channels: int = 1, 
                 bit_depth: int = 16):
        """
        初始化转换器
        
        参数:
            sample_rate: 采样率（Hz），例如: 8000, 16000, 44100, 48000
            channels: 声道数，1=单声道，2=立体声
            bit_depth: 位深度，支持8或16位
        """
        if sample_rate <= 0:
            raise ValueError("采样率必须大于0")
        if channels not in (1, 2):
            raise ValueError("声道数必须是1（单声道）或2（立体声）")
        if bit_depth not in (8, 16):
            raise ValueError("位深度必须是8或16")
            
        self.sample_rate = sample_rate
        self.channels = channels
        self.bit_depth = bit_depth
        self.bytes_per_sample = bit_depth // 8
        
    def _create_wav_header(self, pcm_data_size: int) -> bytes:
        """
        创建WAV文件头
        
        参数:
            pcm_data_size: PCM数据大小（字节）
            
        返回:
            WAV文件头字节数据
        """
        # WAV文件格式规范
        # RIFF chunk
        riff_chunk_size = pcm_data_size + 36
        
        # Format chunk
        audio_format = 1  # PCM
        byte_rate = self.sample_rate * self.channels * self.bytes_per_sample
        block_align = self.channels * self.bytes_per_sample
        
        # 构建WAV头部
        header = b''
        
        # RIFF标识符
        header += b'RIFF'
        # 文件大小（不包括RIFF标识符和这个字段本身）
        header += struct.pack('<I', riff_chunk_size)
        # WAVE标识符
        header += b'WAVE'
        
        # fmt子块
        header += b'fmt '
        # fmt子块大小
        header += struct.pack('<I', 16)
        # 音频格式（PCM=1）
        header += struct.pack('<H', audio_format)
        # 声道数
        header += struct.pack('<H', self.channels)
        # 采样率
        header += struct.pack('<I', self.sample_rate)
        # 字节率
        header += struct.pack('<I', byte_rate)
        # 块对齐
        header += struct.pack('<H', block_align)
        # 位深度
        header += struct.pack('<H', self.bit_depth)
        
        # data子块
        header += b'data'
        # 数据大小
        header += struct.pack('<I', pcm_data_size)
        
        return header
    
    def convert(self, 
                pcm_data: bytes, 
                output_path: str) -> bool:
        """
        将PCM数据转换为WAV文件
        
        参数:
            pcm_data: 原始PCM字节数据
            output_path: 输出WAV文件路径
            
        返回:
            转换是否成功
        """
        try:
            # 创建WAV头部
            wav_header = self._create_wav_header(len(pcm_data))
            
            # 写入WAV文件
            with open(output_path, 'wb') as wav_file:
                wav_file.write(wav_header)
                wav_file.write(pcm_data)
            
            print(f"✓ 成功转换: {output_path}")
            print(f"  - 采样率: {self.sample_rate} Hz")
            print(f"  - 声道数: {self.channels}")
            print(f"  - 位深度: {self.bit_depth} bit")
            print(f"  - 文件大小: {len(pcm_data) + 44} 字节")
            
            return True
            
        except Exception as e:
            print(f"✗ 转换失败: {str(e)}")
            return False
    
    def convert_file(self, 
                     pcm_file_path: str, 
                     wav_file_path: str = None) -> bool:
        """
        将PCM文件转换为WAV文件
        
        参数:
            pcm_file_path: 输入PCM文件路径
            wav_file_path: 输出WAV文件路径（如果为None，自动生成）
            
        返回:
            转换是否成功
        """
        if not os.path.exists(pcm_file_path):
            print(f"✗ PCM文件不存在: {pcm_file_path}")
            return False
        
        # 自动生成输出文件名
        if wav_file_path is None:
            base_name = os.path.splitext(pcm_file_path)[0]
            wav_file_path = f"{base_name}.wav"
        
        try:
            # 读取PCM数据
            with open(pcm_file_path, 'rb') as pcm_file:
                pcm_data = pcm_file.read()
            
            print(f"读取PCM文件: {pcm_file_path} ({len(pcm_data)} 字节)")
            
            # 转换
            return self.convert(pcm_data, wav_file_path)
            
        except Exception as e:
            print(f"✗ 读取PCM文件失败: {str(e)}")
            return False


def main():
    """示例用法"""
    print("=" * 60)
    print("PCM到WAV转换器示例")
    print("=" * 60)
    
    # 创建一个示例PCM数据（1秒的440Hz正弦波）
    import math
    sample_rate = 16000
    duration = 1.0  # 秒
    frequency = 440  # Hz (A4音符)
    
    # 生成正弦波PCM数据
    samples = []
    for i in range(int(sample_rate * duration)):
        sample = int(32767 * math.sin(2 * math.pi * frequency * i / sample_rate))
        samples.append(struct.pack('<h', sample))  # 16位小端格式
    
    pcm_data = b''.join(samples)
    
    # 创建转换器实例
    converter = PCMToWAVConverter(
        sample_rate=16000,
        channels=1,
        bit_depth=16
    )
    
    # 转换为WAV
    output_file = "/workspace/example_output.wav"
    converter.convert(pcm_data, output_file)
    
    print("\n" + "=" * 60)
    print("转换完成！")
    print("=" * 60)


if __name__ == "__main__":
    main()
