#!/usr/bin/env python3
"""
PCMToWAVConverter 测试脚本
生成测试用的 PCM 数据并转换为 WAV
"""

import struct
import os
import math
from PCMToWAVConverter import PCMToWAVConverter


def generate_test_pcm(filename: str, duration: float = 1.0, 
                     sample_rate: int = 44100, frequency: float = 440.0):
    """
    生成测试用的 PCM 数据（正弦波）
    
    Args:
        filename: 输出文件名
        duration: 持续时间（秒）
        sample_rate: 采样率
        frequency: 频率（Hz）
    """
    num_samples = int(sample_rate * duration)
    pcm_data = bytearray()
    
    for i in range(num_samples):
        # 生成正弦波样本
        t = i / sample_rate
        sample_value = math.sin(2.0 * math.pi * frequency * t)
        # 转换为 16 位 PCM（范围：-32768 到 32767）
        sample = int(32767 * 0.5 * sample_value)
        # 打包为小端序 16 位整数
        pcm_data.extend(struct.pack('<h', sample))
    
    with open(filename, 'wb') as f:
        f.write(pcm_data)
    
    print(f"生成测试 PCM 文件: {filename} ({len(pcm_data)} 字节)")


def main():
    """运行测试"""
    print("=== PCMToWAVConverter 测试 ===\n")
    
    # 生成测试 PCM 文件
    test_pcm = "test_440hz.pcm"
    generate_test_pcm(test_pcm, duration=2.0, sample_rate=44100, frequency=440.0)
    
    # 转换
    converter = PCMToWAVConverter(sample_rate=44100, channels=1, bits_per_sample=16)
    output_wav = "test_440hz.wav"
    
    print(f"\n正在转换: {test_pcm} -> {output_wav}")
    if converter.convert(test_pcm, output_wav):
        print(f"✓ 转换成功: {output_wav}")
        
        # 显示文件大小
        pcm_size = os.path.getsize(test_pcm)
        wav_size = os.path.getsize(output_wav)
        print(f"  PCM 文件大小: {pcm_size} 字节")
        print(f"  WAV 文件大小: {wav_size} 字节")
        print(f"  文件头大小: {wav_size - pcm_size} 字节")
    else:
        print("✗ 转换失败")
        return
    
    # 清理测试文件（可选）
    # os.remove(test_pcm)
    # os.remove(output_wav)


if __name__ == '__main__':
    main()
