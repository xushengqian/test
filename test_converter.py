#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
PCMToWAVConverter 测试脚本
验证转换器的正确性
"""

import os
import struct
from pcm_to_wav_converter import PCMToWAVConverter


def test_wav_header(wav_path, expected_sample_rate, expected_channels, expected_bit_depth):
    """验证WAV文件头是否正确"""
    with open(wav_path, 'rb') as f:
        # 读取RIFF标识符
        riff = f.read(4)
        if riff != b'RIFF':
            return False, "RIFF标识符不正确"
        
        # 读取文件大小
        file_size = struct.unpack('<I', f.read(4))[0]
        
        # 读取WAVE标识符
        wave = f.read(4)
        if wave != b'WAVE':
            return False, "WAVE标识符不正确"
        
        # 读取fmt标识符
        fmt = f.read(4)
        if fmt != b'fmt ':
            return False, "fmt标识符不正确"
        
        # 读取fmt块大小
        fmt_size = struct.unpack('<I', f.read(4))[0]
        
        # 读取音频格式
        audio_format = struct.unpack('<H', f.read(2))[0]
        if audio_format != 1:
            return False, f"音频格式应该是1(PCM)，但是{audio_format}"
        
        # 读取声道数
        channels = struct.unpack('<H', f.read(2))[0]
        if channels != expected_channels:
            return False, f"声道数应该是{expected_channels}，但是{channels}"
        
        # 读取采样率
        sample_rate = struct.unpack('<I', f.read(4))[0]
        if sample_rate != expected_sample_rate:
            return False, f"采样率应该是{expected_sample_rate}，但是{sample_rate}"
        
        # 读取字节率
        byte_rate = struct.unpack('<I', f.read(4))[0]
        expected_byte_rate = expected_sample_rate * expected_channels * (expected_bit_depth // 8)
        if byte_rate != expected_byte_rate:
            return False, f"字节率应该是{expected_byte_rate}，但是{byte_rate}"
        
        # 读取块对齐
        block_align = struct.unpack('<H', f.read(2))[0]
        expected_block_align = expected_channels * (expected_bit_depth // 8)
        if block_align != expected_block_align:
            return False, f"块对齐应该是{expected_block_align}，但是{block_align}"
        
        # 读取位深度
        bit_depth = struct.unpack('<H', f.read(2))[0]
        if bit_depth != expected_bit_depth:
            return False, f"位深度应该是{expected_bit_depth}，但是{bit_depth}"
        
        # 读取data标识符
        data = f.read(4)
        if data != b'data':
            return False, "data标识符不正确"
        
        # 读取数据大小
        data_size = struct.unpack('<I', f.read(4))[0]
        
        return True, "WAV文件头验证通过"


def run_tests():
    """运行所有测试"""
    print("=" * 60)
    print("PCMToWAVConverter 测试套件")
    print("=" * 60)
    
    tests_passed = 0
    tests_failed = 0
    
    # 测试1: 基本转换（单声道，16位，16kHz）
    print("\n【测试1】单声道 16位 16kHz")
    converter = PCMToWAVConverter(sample_rate=16000, channels=1, bit_depth=16)
    pcm_data = struct.pack('<' + 'h' * 1000, *([0] * 1000))
    output_path = "/tmp/test1.wav"
    
    try:
        converter.convert(pcm_data, output_path)
        success, msg = test_wav_header(output_path, 16000, 1, 16)
        if success:
            print(f"✓ 通过: {msg}")
            tests_passed += 1
        else:
            print(f"✗ 失败: {msg}")
            tests_failed += 1
        os.remove(output_path)
    except Exception as e:
        print(f"✗ 失败: {str(e)}")
        tests_failed += 1
    
    # 测试2: 立体声转换
    print("\n【测试2】立体声 16位 44100Hz")
    converter = PCMToWAVConverter(sample_rate=44100, channels=2, bit_depth=16)
    pcm_data = struct.pack('<' + 'h' * 2000, *([0] * 2000))
    output_path = "/tmp/test2.wav"
    
    try:
        converter.convert(pcm_data, output_path)
        success, msg = test_wav_header(output_path, 44100, 2, 16)
        if success:
            print(f"✓ 通过: {msg}")
            tests_passed += 1
        else:
            print(f"✗ 失败: {msg}")
            tests_failed += 1
        os.remove(output_path)
    except Exception as e:
        print(f"✗ 失败: {str(e)}")
        tests_failed += 1
    
    # 测试3: 48kHz采样率
    print("\n【测试3】单声道 16位 48000Hz")
    converter = PCMToWAVConverter(sample_rate=48000, channels=1, bit_depth=16)
    pcm_data = struct.pack('<' + 'h' * 1000, *([0] * 1000))
    output_path = "/tmp/test3.wav"
    
    try:
        converter.convert(pcm_data, output_path)
        success, msg = test_wav_header(output_path, 48000, 1, 16)
        if success:
            print(f"✓ 通过: {msg}")
            tests_passed += 1
        else:
            print(f"✗ 失败: {msg}")
            tests_failed += 1
        os.remove(output_path)
    except Exception as e:
        print(f"✗ 失败: {str(e)}")
        tests_failed += 1
    
    # 测试4: 错误处理 - 无效采样率
    print("\n【测试4】错误处理 - 无效采样率")
    try:
        converter = PCMToWAVConverter(sample_rate=0, channels=1, bit_depth=16)
        print("✗ 失败: 应该抛出异常")
        tests_failed += 1
    except ValueError as e:
        print(f"✓ 通过: 正确捕获异常 - {str(e)}")
        tests_passed += 1
    
    # 测试5: 错误处理 - 无效声道数
    print("\n【测试5】错误处理 - 无效声道数")
    try:
        converter = PCMToWAVConverter(sample_rate=16000, channels=3, bit_depth=16)
        print("✗ 失败: 应该抛出异常")
        tests_failed += 1
    except ValueError as e:
        print(f"✓ 通过: 正确捕获异常 - {str(e)}")
        tests_passed += 1
    
    # 测试6: 错误处理 - 无效位深度
    print("\n【测试6】错误处理 - 无效位深度")
    try:
        converter = PCMToWAVConverter(sample_rate=16000, channels=1, bit_depth=24)
        print("✗ 失败: 应该抛出异常")
        tests_failed += 1
    except ValueError as e:
        print(f"✓ 通过: 正确捕获异常 - {str(e)}")
        tests_passed += 1
    
    # 测试7: 文件转换功能
    print("\n【测试7】文件转换功能")
    converter = PCMToWAVConverter(sample_rate=16000, channels=1, bit_depth=16)
    pcm_path = "/tmp/test.pcm"
    wav_path = "/tmp/test.wav"
    
    try:
        # 创建PCM文件
        pcm_data = struct.pack('<' + 'h' * 1000, *([0] * 1000))
        with open(pcm_path, 'wb') as f:
            f.write(pcm_data)
        
        # 转换
        result = converter.convert_file(pcm_path, wav_path)
        
        if result and os.path.exists(wav_path):
            success, msg = test_wav_header(wav_path, 16000, 1, 16)
            if success:
                print(f"✓ 通过: 文件转换成功")
                tests_passed += 1
            else:
                print(f"✗ 失败: {msg}")
                tests_failed += 1
        else:
            print("✗ 失败: 转换失败")
            tests_failed += 1
        
        # 清理
        if os.path.exists(pcm_path):
            os.remove(pcm_path)
        if os.path.exists(wav_path):
            os.remove(wav_path)
    except Exception as e:
        print(f"✗ 失败: {str(e)}")
        tests_failed += 1
    
    # 总结
    print("\n" + "=" * 60)
    print("测试结果总结")
    print("=" * 60)
    print(f"通过: {tests_passed}")
    print(f"失败: {tests_failed}")
    print(f"总计: {tests_passed + tests_failed}")
    
    if tests_failed == 0:
        print("\n🎉 所有测试通过！")
    else:
        print(f"\n⚠️  {tests_failed} 个测试失败")
    
    return tests_failed == 0


if __name__ == "__main__":
    success = run_tests()
    exit(0 if success else 1)
