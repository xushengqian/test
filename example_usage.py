#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PCMToWAVConverter 使用示例
"""

from pcm_to_wav_converter import PCMToWAVConverter
import struct
import math


def example_1_basic_conversion():
    """示例1: 基本的PCM到WAV转换"""
    print("\n【示例1】基本转换")
    print("-" * 40)
    
    # 创建转换器（16kHz, 单声道, 16位）
    converter = PCMToWAVConverter(
        sample_rate=16000,
        channels=1,
        bit_depth=16
    )
    
    # 生成一些测试PCM数据（1秒静音）
    silence_duration = 1.0
    num_samples = int(16000 * silence_duration)
    pcm_data = struct.pack('<' + 'h' * num_samples, *([0] * num_samples))
    
    # 转换
    converter.convert(pcm_data, "/workspace/silence.wav")


def example_2_tone_generation():
    """示例2: 生成不同频率的音调"""
    print("\n【示例2】生成音调")
    print("-" * 40)
    
    converter = PCMToWAVConverter(sample_rate=44100, channels=1, bit_depth=16)
    
    # 生成C大调音阶
    notes = {
        'C4': 261.63,
        'D4': 293.66,
        'E4': 329.63,
        'F4': 349.23,
        'G4': 392.00,
        'A4': 440.00,
        'B4': 493.88,
        'C5': 523.25
    }
    
    for note_name, frequency in notes.items():
        samples = []
        duration = 0.5  # 每个音符0.5秒
        
        for i in range(int(44100 * duration)):
            sample = int(16000 * math.sin(2 * math.pi * frequency * i / 44100))
            samples.append(struct.pack('<h', sample))
        
        pcm_data = b''.join(samples)
        converter.convert(pcm_data, f"/workspace/note_{note_name}.wav")


def example_3_stereo_conversion():
    """示例3: 立体声转换"""
    print("\n【示例3】立体声转换")
    print("-" * 40)
    
    converter = PCMToWAVConverter(
        sample_rate=48000,
        channels=2,  # 立体声
        bit_depth=16
    )
    
    # 生成立体声数据（左声道440Hz，右声道880Hz）
    samples = []
    duration = 1.0
    
    for i in range(int(48000 * duration)):
        # 左声道
        left = int(16000 * math.sin(2 * math.pi * 440 * i / 48000))
        # 右声道
        right = int(16000 * math.sin(2 * math.pi * 880 * i / 48000))
        samples.append(struct.pack('<hh', left, right))
    
    pcm_data = b''.join(samples)
    converter.convert(pcm_data, "/workspace/stereo_output.wav")


def example_4_file_conversion():
    """示例4: 从PCM文件转换"""
    print("\n【示例4】从文件转换")
    print("-" * 40)
    
    # 首先创建一个PCM文件
    pcm_file_path = "/workspace/test.pcm"
    
    # 生成测试数据
    samples = []
    for i in range(16000):  # 1秒数据
        sample = int(20000 * math.sin(2 * math.pi * 1000 * i / 16000))
        samples.append(struct.pack('<h', sample))
    
    with open(pcm_file_path, 'wb') as f:
        f.write(b''.join(samples))
    
    print(f"创建测试PCM文件: {pcm_file_path}")
    
    # 从PCM文件转换
    converter = PCMToWAVConverter(sample_rate=16000, channels=1, bit_depth=16)
    converter.convert_file(pcm_file_path, "/workspace/test_output.wav")


def example_5_different_formats():
    """示例5: 不同采样率和位深度"""
    print("\n【示例5】不同格式")
    print("-" * 40)
    
    formats = [
        (8000, 1, 16, "8kHz_mono_16bit.wav"),
        (16000, 1, 16, "16kHz_mono_16bit.wav"),
        (44100, 2, 16, "44.1kHz_stereo_16bit.wav"),
        (48000, 2, 16, "48kHz_stereo_16bit.wav"),
    ]
    
    for sample_rate, channels, bit_depth, filename in formats:
        converter = PCMToWAVConverter(sample_rate, channels, bit_depth)
        
        # 生成0.5秒的500Hz音调
        samples = []
        duration = 0.5
        num_samples = int(sample_rate * duration)
        
        for i in range(num_samples):
            sample = int(16000 * math.sin(2 * math.pi * 500 * i / sample_rate))
            if channels == 1:
                samples.append(struct.pack('<h', sample))
            else:
                samples.append(struct.pack('<hh', sample, sample))
        
        pcm_data = b''.join(samples)
        converter.convert(pcm_data, f"/workspace/{filename}")


def main():
    """运行所有示例"""
    print("=" * 60)
    print("PCMToWAVConverter 使用示例集")
    print("=" * 60)
    
    example_1_basic_conversion()
    example_2_tone_generation()
    example_3_stereo_conversion()
    example_4_file_conversion()
    example_5_different_formats()
    
    print("\n" + "=" * 60)
    print("所有示例运行完成！")
    print("=" * 60)


if __name__ == "__main__":
    main()
