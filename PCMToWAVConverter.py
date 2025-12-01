#!/usr/bin/env python3
"""
PCM 到 WAV 转换器
支持将原始 PCM 音频数据转换为标准 WAV 文件格式
"""

import struct
import sys
import os
from typing import Optional


class PCMToWAVConverter:
    """PCM 到 WAV 转换器类"""
    
    def __init__(self, sample_rate: int = 44100, channels: int = 2, 
                 bits_per_sample: int = 16):
        """
        初始化转换器
        
        Args:
            sample_rate: 采样率（Hz），默认 44100
            channels: 声道数，1=单声道，2=立体声，默认 2
            bits_per_sample: 每样本位数，默认 16
        """
        self.sample_rate = sample_rate
        self.channels = channels
        self.bits_per_sample = bits_per_sample
        self.byte_rate = sample_rate * channels * (bits_per_sample // 8)
        self.block_align = channels * (bits_per_sample // 8)
    
    def convert(self, pcm_file: str, wav_file: str) -> bool:
        """
        将 PCM 文件转换为 WAV 文件
        
        Args:
            pcm_file: 输入 PCM 文件路径
            wav_file: 输出 WAV 文件路径
            
        Returns:
            成功返回 True，失败返回 False
        """
        try:
            # 读取 PCM 数据
            with open(pcm_file, 'rb') as f:
                pcm_data = f.read()
            
            # 计算数据大小
            data_size = len(pcm_data)
            file_size = 36 + data_size  # WAV 文件总大小
            
            # 创建 WAV 文件
            with open(wav_file, 'wb') as f:
                # 写入 RIFF 头
                f.write(b'RIFF')
                f.write(struct.pack('<I', file_size))
                f.write(b'WAVE')
                
                # 写入 fmt 子块
                f.write(b'fmt ')
                f.write(struct.pack('<I', 16))  # fmt 块大小
                f.write(struct.pack('<H', 1))   # 音频格式（1=PCM）
                f.write(struct.pack('<H', self.channels))
                f.write(struct.pack('<I', self.sample_rate))
                f.write(struct.pack('<I', self.byte_rate))
                f.write(struct.pack('<H', self.block_align))
                f.write(struct.pack('<H', self.bits_per_sample))
                
                # 写入 data 子块
                f.write(b'data')
                f.write(struct.pack('<I', data_size))
                f.write(pcm_data)
            
            return True
            
        except FileNotFoundError:
            print(f"错误: 找不到文件 {pcm_file}")
            return False
        except Exception as e:
            print(f"错误: {str(e)}")
            return False
    
    def convert_from_bytes(self, pcm_data: bytes, wav_file: str) -> bool:
        """
        将 PCM 字节数据转换为 WAV 文件
        
        Args:
            pcm_data: PCM 音频数据（字节）
            wav_file: 输出 WAV 文件路径
            
        Returns:
            成功返回 True，失败返回 False
        """
        try:
            # 计算数据大小
            data_size = len(pcm_data)
            file_size = 36 + data_size
            
            # 创建 WAV 文件
            with open(wav_file, 'wb') as f:
                # 写入 RIFF 头
                f.write(b'RIFF')
                f.write(struct.pack('<I', file_size))
                f.write(b'WAVE')
                
                # 写入 fmt 子块
                f.write(b'fmt ')
                f.write(struct.pack('<I', 16))
                f.write(struct.pack('<H', 1))
                f.write(struct.pack('<H', self.channels))
                f.write(struct.pack('<I', self.sample_rate))
                f.write(struct.pack('<I', self.byte_rate))
                f.write(struct.pack('<H', self.block_align))
                f.write(struct.pack('<H', self.bits_per_sample))
                
                # 写入 data 子块
                f.write(b'data')
                f.write(struct.pack('<I', data_size))
                f.write(pcm_data)
            
            return True
            
        except Exception as e:
            print(f"错误: {str(e)}")
            return False


def main():
    """命令行主函数"""
    if len(sys.argv) < 3:
        print("用法: python PCMToWAVConverter.py <输入PCM文件> <输出WAV文件> [采样率] [声道数] [位深度]")
        print("示例: python PCMToWAVConverter.py input.pcm output.wav 44100 2 16")
        sys.exit(1)
    
    pcm_file = sys.argv[1]
    wav_file = sys.argv[2]
    
    # 解析可选参数
    sample_rate = int(sys.argv[3]) if len(sys.argv) > 3 else 44100
    channels = int(sys.argv[4]) if len(sys.argv) > 4 else 2
    bits_per_sample = int(sys.argv[5]) if len(sys.argv) > 5 else 16
    
    # 创建转换器并执行转换
    converter = PCMToWAVConverter(sample_rate, channels, bits_per_sample)
    
    print(f"正在转换: {pcm_file} -> {wav_file}")
    print(f"参数: 采样率={sample_rate}Hz, 声道数={channels}, 位深度={bits_per_sample}bit")
    
    if converter.convert(pcm_file, wav_file):
        print(f"转换成功: {wav_file}")
    else:
        print("转换失败")
        sys.exit(1)


if __name__ == '__main__':
    main()
