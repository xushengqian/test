#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
快速开始脚本
用于快速测试和验证系统安装
"""

import asyncio
import sys
import os

sys.path.insert(0, os.path.dirname(__file__))

from src.mrcp_client import MRCPClient
from src.stream_processor import AudioStreamProcessor
from src.audio_handler import AudioHandler
from src.websocket_server import AudioWebSocketServer
import logging

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def print_header(title: str):
    """打印标题"""
    print(f"\n{'='*60}")
    print(f"  {title}")
    print(f"{'='*60}\n")


async def test_mrcp_connection():
    """测试MRCP连接"""
    print_header("测试 MRCP 连接")
    
    client = MRCPClient()
    
    try:
        if await client.connect():
            print("✓ MRCP服务器连接成功")
            await client.disconnect()
            return True
        else:
            print("✗ MRCP服务器连接失败")
            print("  请确保MRCP服务器正在运行")
            return False
    except Exception as e:
        print(f"✗ 连接错误: {e}")
        return False


async def test_audio_handler():
    """测试音频处理"""
    print_header("测试音频处理")
    
    try:
        handler = AudioHandler(sample_rate=16000, channels=1, sample_width=2)
        
        # 生成测试音频
        import struct
        duration = 1.0
        num_samples = int(handler.sample_rate * duration)
        test_audio = struct.pack(f"{num_samples}h", *[0] * num_samples)
        
        print(f"✓ 音频处理器初始化成功")
        print(f"  采样率: {handler.sample_rate} Hz")
        print(f"  声道数: {handler.channels}")
        print(f"  采样宽度: {handler.sample_width * 8} bit")
        print(f"  测试音频时长: {handler.get_duration(test_audio):.2f} 秒")
        
        # 测试转换
        wav_data = handler.pcm_to_wav(test_audio)
        print(f"✓ PCM转WAV成功 ({len(wav_data)} 字节)")
        
        return True
    except Exception as e:
        print(f"✗ 音频处理错误: {e}")
        return False


async def test_stream_processor():
    """测试流处理器"""
    print_header("测试流处理器")
    
    try:
        processor = AudioStreamProcessor()
        
        info = processor.get_audio_info()
        print("✓ 流处理器初始化成功")
        print(f"  配置: {info['sample_rate']}Hz, {info['channels']}ch")
        
        # 模拟添加音频数据
        test_data = bytes([0] * 1024)
        processor.add_audio_data(test_data)
        
        info = processor.get_audio_info()
        print(f"✓ 缓冲区测试成功 (缓冲区大小: {info['buffer_size']} 字节)")
        
        return True
    except Exception as e:
        print(f"✗ 流处理器错误: {e}")
        return False


async def test_websocket_server():
    """测试WebSocket服务器"""
    print_header("测试 WebSocket 服务器")
    
    try:
        server = AudioWebSocketServer()
        
        print(f"✓ WebSocket服务器初始化成功")
        print(f"  地址: ws://{server.host}:{server.port}")
        print(f"  (注: 未实际启动服务器，仅测试初始化)")
        
        return True
    except Exception as e:
        print(f"✗ WebSocket服务器错误: {e}")
        return False


async def run_all_tests():
    """运行所有测试"""
    print_header("FreeSWITCH + MRCP 系统快速验证")
    
    results = {
        'MRCP连接': False,
        '音频处理': False,
        '流处理器': False,
        'WebSocket服务器': False,
    }
    
    # 运行测试
    results['音频处理'] = await test_audio_handler()
    results['流处理器'] = await test_stream_processor()
    results['WebSocket服务器'] = await test_websocket_server()
    results['MRCP连接'] = await test_mrcp_connection()
    
    # 显示结果
    print_header("测试结果摘要")
    
    for test_name, passed in results.items():
        status = "✓ 通过" if passed else "✗ 失败"
        print(f"  {test_name:20s} {status}")
    
    # 总结
    total = len(results)
    passed = sum(1 for v in results.values() if v)
    
    print(f"\n总计: {passed}/{total} 测试通过")
    
    if passed == total:
        print("\n🎉 所有测试通过！系统已准备就绪。\n")
        print("下一步:")
        print("  1. 运行示例: python3 examples/basic_asr.py")
        print("  2. 查看文档: cat README.md")
    else:
        print("\n⚠️  部分测试失败，请检查:")
        print("  - MRCP服务器是否运行")
        print("  - 配置文件是否正确")
        print("  - 依赖包是否完整安装")
        print("\n运行: pip3 install -r requirements.txt")
    
    print()


async def interactive_menu():
    """交互式菜单"""
    while True:
        print_header("FreeSWITCH + MRCP 快速开始")
        
        print("请选择操作:")
        print("  1. 运行所有测试")
        print("  2. 测试MRCP连接")
        print("  3. 测试音频处理")
        print("  4. 测试流处理器")
        print("  5. 测试WebSocket服务器")
        print("  6. 查看系统信息")
        print("  0. 退出")
        print()
        
        try:
            choice = input("请输入选项 (0-6): ").strip()
            
            if choice == '0':
                print("\n再见！\n")
                break
            elif choice == '1':
                await run_all_tests()
            elif choice == '2':
                await test_mrcp_connection()
            elif choice == '3':
                await test_audio_handler()
            elif choice == '4':
                await test_stream_processor()
            elif choice == '5':
                await test_websocket_server()
            elif choice == '6':
                print_system_info()
            else:
                print("✗ 无效的选项，请重试\n")
                continue
            
            input("\n按Enter继续...")
            
        except KeyboardInterrupt:
            print("\n\n操作已取消\n")
            break
        except Exception as e:
            print(f"\n✗ 错误: {e}\n")
            input("按Enter继续...")


def print_system_info():
    """打印系统信息"""
    print_header("系统信息")
    
    import platform
    import sys
    
    print(f"操作系统: {platform.system()} {platform.release()}")
    print(f"Python版本: {sys.version.split()[0]}")
    print(f"工作目录: {os.getcwd()}")
    
    # 检查文件
    files_to_check = [
        'config/mrcp_config.ini',
        'config/freeswitch/mrcp_profiles.xml',
        'config/freeswitch/dialplan.xml',
        'requirements.txt'
    ]
    
    print("\n文件检查:")
    for file_path in files_to_check:
        exists = "✓" if os.path.exists(file_path) else "✗"
        print(f"  {exists} {file_path}")
    
    # 检查Python包
    print("\nPython包检查:")
    packages = ['asyncio', 'websockets', 'configparser']
    for package in packages:
        try:
            __import__(package)
            print(f"  ✓ {package}")
        except ImportError:
            print(f"  ✗ {package}")
    
    print()


def main():
    """主函数"""
    import argparse
    
    parser = argparse.ArgumentParser(
        description='FreeSWITCH + MRCP 快速开始脚本'
    )
    parser.add_argument(
        '--test-all',
        action='store_true',
        help='运行所有测试'
    )
    parser.add_argument(
        '--info',
        action='store_true',
        help='显示系统信息'
    )
    
    args = parser.parse_args()
    
    if args.info:
        print_system_info()
    elif args.test_all:
        asyncio.run(run_all_tests())
    else:
        # 交互式模式
        try:
            asyncio.run(interactive_menu())
        except KeyboardInterrupt:
            print("\n\n程序已退出\n")


if __name__ == "__main__":
    main()
