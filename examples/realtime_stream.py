#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
实时音频流示例
演示如何从FreeSWITCH捕获实时音频流并通过WebSocket传输
"""

import asyncio
import sys
import os

# 添加父目录到路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from src.stream_processor import AudioStreamProcessor, RTPStreamProcessor
from src.websocket_server import AudioWebSocketServer
from src.mrcp_client import MRCPClient
from src.audio_handler import AudioHandler
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


async def capture_and_process_stream():
    """
    从FreeSWITCH捕获音频流并实时处理
    """
    print(f"\n{'='*60}")
    print("实时音频流捕获和处理")
    print(f"{'='*60}\n")
    
    # 初始化音频流处理器
    processor = AudioStreamProcessor()
    
    # 显示音频配置
    info = processor.get_audio_info()
    print("音频配置:")
    for key, value in info.items():
        print(f"  {key}: {value}")
    
    # 连接到FreeSWITCH
    print("\n🔌 连接到FreeSWITCH ESL...")
    if await processor.connect_to_freeswitch():
        print("✓ 已连接到FreeSWITCH")
        
        # 订阅事件
        await processor.subscribe_to_events()
        
        # 启动事件处理
        event_task = asyncio.create_task(processor.process_freeswitch_events())
        
        # 捕获并处理音频流
        print("\n🎤 开始捕获音频流 (10秒)...\n")
        
        chunk_count = 0
        async for audio_chunk in processor.get_audio_stream():
            chunk_count += 1
            print(f"📦 接收音频块 #{chunk_count}: {len(audio_chunk)} 字节")
            
            # 处理音频（这里只是打印）
            if chunk_count >= 100:  # 约10秒
                break
        
        # 停止录制
        processor.stop_recording()
        event_task.cancel()
        
        print("\n✓ 音频流捕获完成")
    else:
        print("✗ 连接FreeSWITCH失败")


async def stream_to_websocket():
    """
    通过WebSocket传输实时音频流
    """
    print(f"\n{'='*60}")
    print("实时音频流WebSocket传输")
    print(f"{'='*60}\n")
    
    # 初始化组件
    processor = AudioStreamProcessor()
    ws_server = AudioWebSocketServer()
    
    print("📡 启动WebSocket服务器...")
    
    # 启动WebSocket服务器
    server_task = asyncio.create_task(ws_server.start())
    
    # 等待服务器启动
    await asyncio.sleep(1)
    
    print("✓ WebSocket服务器已启动")
    print(f"   地址: ws://{ws_server.host}:{ws_server.port}")
    print("\n等待客户端连接...")
    
    # 等待客户端连接
    while len(ws_server.clients) == 0:
        await asyncio.sleep(0.5)
    
    print(f"✓ 有 {len(ws_server.clients)} 个客户端连接")
    print("\n🎤 开始传输音频流...\n")
    
    # 模拟音频流
    audio_handler = AudioHandler()
    import struct
    
    for i in range(100):
        # 生成测试音频（100ms每块）
        num_samples = audio_handler.sample_rate // 10
        audio_chunk = struct.pack(f"{num_samples}h", 
                                 *[int(1000 * (i % 10)) for _ in range(num_samples)])
        
        # 广播到所有客户端
        await ws_server.broadcast_audio(audio_chunk)
        print(f"📤 广播音频块 #{i+1}: {len(audio_chunk)} 字节 "
              f"-> {len(ws_server.clients)} 个客户端")
        
        await asyncio.sleep(0.1)
    
    print("\n✓ 音频流传输完成")
    
    # 显示统计信息
    stats = ws_server.get_statistics()
    print(f"\n统计信息:")
    print(f"  总连接数: {stats['total_connections']}")
    print(f"  当前连接数: {stats['current_connections']}")
    print(f"  发送字节数: {stats['bytes_sent']}")
    print(f"  运行时长: {stats['uptime_seconds']:.2f} 秒")
    
    server_task.cancel()


async def real_time_asr():
    """
    实时语音识别示例
    """
    print(f"\n{'='*60}")
    print("实时语音识别")
    print(f"{'='*60}\n")
    
    # 初始化组件
    processor = AudioStreamProcessor()
    mrcp_client = MRCPClient()
    audio_handler = AudioHandler()
    
    # 连接MRCP服务器
    print("🔌 连接MRCP服务器...")
    if not await mrcp_client.connect():
        print("✗ 连接MRCP服务器失败")
        return
    
    print("✓ 已连接到MRCP服务器")
    
    # 启动流式识别
    print("\n🎤 启动实时识别...\n")
    if await mrcp_client.start_recognition_stream():
        print("✓ 流式识别已启动")
        
        # 模拟音频流
        import struct
        buffer = bytearray()
        
        for i in range(50):
            # 生成测试音频
            num_samples = audio_handler.sample_rate // 10
            audio_chunk = struct.pack(f"{num_samples}h", 
                                     *[int(500 * (i % 5)) for _ in range(num_samples)])
            
            # 添加到缓冲区
            buffer.extend(audio_chunk)
            
            # 发送到MRCP服务器
            await mrcp_client.send_audio_chunk(audio_chunk)
            print(f"📤 发送音频块 #{i+1}")
            
            # 每1秒进行一次识别
            if (i + 1) % 10 == 0:
                print(f"\n⏸️  检查识别结果...")
                # 这里可以检查中间结果
                
            await asyncio.sleep(0.1)
        
        # 停止识别
        print("\n⏹️  停止识别...")
        result = await mrcp_client.stop_recognition()
        
        if result:
            print(f"\n{'='*60}")
            print("最终识别结果:")
            print(f"{'='*60}")
            print(f"📝 {result}")
            print(f"{'='*60}\n")
        else:
            print("✗ 无识别结果")
    else:
        print("✗ 启动流式识别失败")
    
    await mrcp_client.disconnect()
    print("✓ 已断开连接")


async def rtp_stream_capture():
    """
    RTP音频流捕获示例
    """
    print(f"\n{'='*60}")
    print("RTP音频流捕获")
    print(f"{'='*60}\n")
    
    # 初始化RTP处理器
    rtp_processor = RTPStreamProcessor(port=4000)
    audio_handler = AudioHandler()
    
    print("📡 启动RTP接收...")
    if await rtp_processor.start():
        print(f"✓ RTP接收器已启动，监听端口: {rtp_processor.port}")
        print("\n等待RTP数据包...\n")
        
        # 接收RTP流
        packet_count = 0
        total_bytes = 0
        
        async for rtp_payload in rtp_processor.receive_rtp_stream():
            packet_count += 1
            total_bytes += len(rtp_payload)
            
            print(f"📦 RTP包 #{packet_count}: {len(rtp_payload)} 字节 "
                  f"(累计: {total_bytes} 字节)")
            
            if packet_count >= 100:  # 接收100个包
                break
        
        # 停止接收
        rtp_processor.stop()
        
        print(f"\n✓ 接收完成")
        print(f"   总包数: {packet_count}")
        print(f"   总字节数: {total_bytes}")
        print(f"   平均包大小: {total_bytes/packet_count:.2f} 字节")
    else:
        print("✗ 启动RTP接收器失败")


async def integrated_demo():
    """
    综合演示：捕获 -> 处理 -> 识别 -> 传输
    """
    print(f"\n{'='*60}")
    print("综合演示")
    print(f"{'='*60}\n")
    
    # 初始化所有组件
    processor = AudioStreamProcessor()
    mrcp_client = MRCPClient()
    ws_server = AudioWebSocketServer()
    audio_handler = AudioHandler()
    
    # 启动WebSocket服务器
    print("📡 启动WebSocket服务器...")
    server_task = asyncio.create_task(ws_server.start())
    await asyncio.sleep(1)
    print("✓ WebSocket服务器已启动")
    
    # 连接MRCP服务器
    print("\n🔌 连接MRCP服务器...")
    if not await mrcp_client.connect():
        print("✗ 连接MRCP服务器失败")
        return
    print("✓ 已连接到MRCP服务器")
    
    # 启动流式识别
    print("\n🎤 启动实时处理...\n")
    await mrcp_client.start_recognition_stream()
    
    # 处理音频流
    import struct
    for i in range(30):
        # 生成音频
        num_samples = audio_handler.sample_rate // 10
        audio_chunk = struct.pack(f"{num_samples}h", 
                                 *[int(800 * ((i % 3) - 1)) for _ in range(num_samples)])
        
        # 1. 发送到MRCP进行识别
        await mrcp_client.send_audio_chunk(audio_chunk)
        
        # 2. 广播到WebSocket客户端
        if ws_server.clients:
            await ws_server.broadcast_audio(audio_chunk)
        
        print(f"📤 处理块 #{i+1}: MRCP识别 + WebSocket广播 "
              f"({len(ws_server.clients)} 客户端)")
        
        await asyncio.sleep(0.1)
    
    # 获取识别结果
    print("\n⏹️  获取识别结果...")
    result = await mrcp_client.stop_recognition()
    
    if result:
        print(f"\n📝 识别结果: {result}")
    
    # 清理
    await mrcp_client.disconnect()
    server_task.cancel()
    
    print("\n✓ 综合演示完成")


def print_menu():
    """打印菜单"""
    print(f"\n{'='*60}")
    print("FreeSWITCH + MRCP 实时音频流示例")
    print(f"{'='*60}")
    print("\n请选择示例:")
    print("  1. 捕获和处理音频流")
    print("  2. WebSocket音频流传输")
    print("  3. 实时语音识别")
    print("  4. RTP音频流捕获")
    print("  5. 综合演示")
    print("  0. 退出")
    print(f"\n{'='*60}")


async def main():
    """主函数"""
    while True:
        print_menu()
        
        try:
            choice = input("\n请输入选项 (0-5): ").strip()
            
            if choice == '0':
                print("\n再见！")
                break
            elif choice == '1':
                await capture_and_process_stream()
            elif choice == '2':
                await stream_to_websocket()
            elif choice == '3':
                await real_time_asr()
            elif choice == '4':
                await rtp_stream_capture()
            elif choice == '5':
                await integrated_demo()
            else:
                print("✗ 无效的选项，请重试")
        except KeyboardInterrupt:
            print("\n\n操作已取消")
            break
        except Exception as e:
            logger.error(f"错误: {e}", exc_info=True)
            print(f"\n✗ 错误: {e}")
        
        input("\n按Enter继续...")


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n\n程序已退出")
