#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
基础ASR（语音识别）示例
演示如何使用MRCP客户端进行语音识别
"""

import asyncio
import sys
import os

# 添加父目录到路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from src.mrcp_client import MRCPClient
from src.audio_handler import AudioHandler
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


async def recognize_from_file(audio_file: str):
    """
    从音频文件进行语音识别
    
    Args:
        audio_file: 音频文件路径
    """
    print(f"\n{'='*60}")
    print("基础ASR示例 - 从文件识别")
    print(f"{'='*60}\n")
    
    # 初始化音频处理器
    audio_handler = AudioHandler()
    
    # 读取音频文件
    print(f"📂 读取音频文件: {audio_file}")
    try:
        with open(audio_file, 'rb') as f:
            wav_data = f.read()
        
        # 转换为PCM
        pcm_data = audio_handler.wav_to_pcm(wav_data)
        print(f"✓ 音频文件加载成功")
        print(f"  - 采样率: {audio_handler.sample_rate} Hz")
        print(f"  - 声道数: {audio_handler.channels}")
        print(f"  - 时长: {audio_handler.get_duration(pcm_data):.2f} 秒")
    except FileNotFoundError:
        print(f"✗ 错误: 找不到文件 {audio_file}")
        return
    except Exception as e:
        print(f"✗ 读取文件失败: {e}")
        return
    
    # 初始化MRCP客户端
    print("\n🔌 连接MRCP服务器...")
    client = MRCPClient()
    
    if await client.connect():
        print("✓ 已连接到MRCP服务器")
        
        # 进行语音识别
        print("\n🎤 开始语音识别...")
        result = await client.recognize(
            audio_data=pcm_data,
            language="zh-CN",
            grammar="builtin:grammar/boolean"
        )
        
        if result:
            print(f"\n{'='*60}")
            print("识别结果:")
            print(f"{'='*60}")
            print(f"📝 {result}")
            print(f"{'='*60}\n")
        else:
            print("✗ 识别失败或无结果")
        
        # 断开连接
        await client.disconnect()
        print("✓ 已断开连接")
    else:
        print("✗ 连接MRCP服务器失败")


async def recognize_streaming():
    """
    流式语音识别示例
    """
    print(f"\n{'='*60}")
    print("流式ASR示例")
    print(f"{'='*60}\n")
    
    # 初始化MRCP客户端
    print("🔌 连接MRCP服务器...")
    client = MRCPClient()
    
    if not await client.connect():
        print("✗ 连接MRCP服务器失败")
        return
    
    print("✓ 已连接到MRCP服务器")
    
    # 启动流式识别
    print("\n🎤 启动流式识别会话...")
    if await client.start_recognition_stream():
        print("✓ 流式识别已启动")
        
        # 模拟发送音频块
        print("\n📤 发送音频数据...")
        audio_handler = AudioHandler()
        
        # 生成测试音频（静音）
        import struct
        for i in range(10):
            # 生成100ms的音频数据
            num_samples = audio_handler.sample_rate // 10
            audio_chunk = struct.pack(f"{num_samples}h", *[0] * num_samples)
            
            if await client.send_audio_chunk(audio_chunk):
                print(f"  ✓ 发送音频块 {i+1}/10 ({len(audio_chunk)} 字节)")
            else:
                print(f"  ✗ 发送音频块 {i+1}/10 失败")
                break
            
            await asyncio.sleep(0.1)
        
        # 停止识别并获取结果
        print("\n⏸️  停止识别...")
        result = await client.stop_recognition()
        
        if result:
            print(f"\n{'='*60}")
            print("识别结果:")
            print(f"{'='*60}")
            print(f"📝 {result}")
            print(f"{'='*60}\n")
        else:
            print("✗ 无识别结果")
    else:
        print("✗ 启动流式识别失败")
    
    # 断开连接
    await client.disconnect()
    print("✓ 已断开连接")


async def recognize_with_vad():
    """
    带语音活动检测（VAD）的识别示例
    """
    print(f"\n{'='*60}")
    print("带VAD的ASR示例")
    print(f"{'='*60}\n")
    
    audio_handler = AudioHandler()
    client = MRCPClient()
    
    if not await client.connect():
        print("✗ 连接MRCP服务器失败")
        return
    
    print("✓ 已连接到MRCP服务器")
    print("\n🎤 开始监听音频并检测语音活动...\n")
    
    # 模拟音频流处理
    import struct
    silence_count = 0
    speech_detected = False
    audio_buffer = bytearray()
    
    for i in range(50):
        # 生成测试音频
        num_samples = audio_handler.sample_rate // 10
        if 10 <= i < 30:
            # 模拟语音（非零值）
            audio_chunk = struct.pack(f"{num_samples}h", 
                                     *[1000 * (i % 2) for _ in range(num_samples)])
        else:
            # 静音
            audio_chunk = struct.pack(f"{num_samples}h", *[0] * num_samples)
        
        # 检测静音
        is_silence = audio_handler.detect_silence(audio_chunk)
        
        if not is_silence:
            if not speech_detected:
                print("🎤 检测到语音活动")
                speech_detected = True
            audio_buffer.extend(audio_chunk)
            silence_count = 0
        else:
            if speech_detected:
                silence_count += 1
                audio_buffer.extend(audio_chunk)
                
                # 连续3次静音，认为语音结束
                if silence_count >= 3:
                    print("⏸️  语音结束，开始识别...\n")
                    
                    # 识别音频
                    result = await client.recognize(bytes(audio_buffer))
                    
                    if result:
                        print(f"📝 识别结果: {result}\n")
                    else:
                        print("✗ 识别失败\n")
                    
                    # 重置
                    audio_buffer.clear()
                    speech_detected = False
                    silence_count = 0
        
        await asyncio.sleep(0.1)
    
    await client.disconnect()
    print("\n✓ 示例结束")


def print_menu():
    """打印菜单"""
    print(f"\n{'='*60}")
    print("FreeSWITCH + MRCP ASR示例")
    print(f"{'='*60}")
    print("\n请选择示例:")
    print("  1. 从文件识别")
    print("  2. 流式识别")
    print("  3. 带VAD的识别")
    print("  0. 退出")
    print(f"\n{'='*60}")


async def main():
    """主函数"""
    while True:
        print_menu()
        
        try:
            choice = input("\n请输入选项 (0-3): ").strip()
            
            if choice == '0':
                print("\n再见！")
                break
            elif choice == '1':
                audio_file = input("请输入音频文件路径 (或按Enter使用默认): ").strip()
                if not audio_file:
                    audio_file = "test_audio.wav"
                await recognize_from_file(audio_file)
            elif choice == '2':
                await recognize_streaming()
            elif choice == '3':
                await recognize_with_vad()
            else:
                print("✗ 无效的选项，请重试")
        except KeyboardInterrupt:
            print("\n\n操作已取消")
            break
        except Exception as e:
            print(f"\n✗ 错误: {e}")
        
        input("\n按Enter继续...")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n\n程序已退出")
