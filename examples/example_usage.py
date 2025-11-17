#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FreeSWITCH + MRCP 实时语音流使用示例
"""

import sys
import os
import time
import logging

# 添加项目路径
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from python.mrcp_client import RealtimeVoiceStreamProcessor, MRCPClient, FreeSWITCHRTPClient
from python.freeswitch_esl_client import MRCPStreamController

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def example_1_basic_mrcp_client():
    """示例 1: 基本 MRCP 客户端使用"""
    logger.info("=== 示例 1: 基本 MRCP 客户端 ===")
    
    client = MRCPClient(server_ip="127.0.0.1", server_port=1544)
    
    if client.connect():
        # 发送测试音频数据
        test_audio = b'\x00' * 160  # 20ms 的静音数据
        client.send_audio_data(test_audio)
        logger.info("已发送测试音频数据")
        
        time.sleep(1)
        client.disconnect()
    else:
        logger.error("连接失败")


def example_2_rtp_client():
    """示例 2: RTP 客户端使用"""
    logger.info("=== 示例 2: RTP 客户端 ===")
    
    rtp_client = FreeSWITCHRTPClient(rtp_ip="127.0.0.1", rtp_port=16384)
    
    def audio_callback(audio_data):
        logger.info(f"收到音频数据: {len(audio_data)} 字节")
    
    rtp_client.set_audio_callback(audio_callback)
    
    if rtp_client.start_stream():
        logger.info("RTP 流运行中，5 秒后停止...")
        time.sleep(5)
        rtp_client.stop_stream()
    else:
        logger.error("启动 RTP 流失败")


def example_3_realtime_processor():
    """示例 3: 实时语音流处理器"""
    logger.info("=== 示例 3: 实时语音流处理器 ===")
    
    processor = RealtimeVoiceStreamProcessor(
        rtp_ip="127.0.0.1",
        rtp_port=16384,
        mrcp_ip="127.0.0.1",
        mrcp_port=1544
    )
    
    try:
        if processor.start():
            logger.info("实时语音流处理运行中，10 秒后停止...")
            time.sleep(10)
        else:
            logger.error("启动失败")
    finally:
        processor.stop()


def example_4_esl_controller():
    """示例 4: ESL 流控制器"""
    logger.info("=== 示例 4: ESL 流控制器 ===")
    
    controller = MRCPStreamController(
        esl_host="127.0.0.1",
        esl_port=8021,
        mrcp_ip="127.0.0.1",
        mrcp_port=1544
    )
    
    try:
        # 启动流（需要替换为实际的目标号码）
        destination = "1000"  # 修改为实际号码
        if controller.start_stream(destination):
            logger.info("流已启动，10 秒后停止...")
            
            # 定期检查状态
            for i in range(10):
                status = controller.get_stream_status()
                logger.info(f"状态检查 {i+1}: {status}")
                time.sleep(1)
        else:
            logger.error("启动失败")
    finally:
        controller.stop_stream()


def example_5_custom_audio_processing():
    """示例 5: 自定义音频处理"""
    logger.info("=== 示例 5: 自定义音频处理 ===")
    
    processor = RealtimeVoiceStreamProcessor(
        rtp_ip="127.0.0.1",
        rtp_port=16384,
        mrcp_ip="127.0.0.1",
        mrcp_port=1544
    )
    
    # 自定义音频处理函数
    original_callback = None
    
    def custom_audio_handler(audio_data):
        """自定义音频处理"""
        # 这里可以添加音频分析、特征提取等处理
        logger.debug(f"处理音频数据: {len(audio_data)} 字节")
        
        # 调用原始回调（如果存在）
        if original_callback:
            original_callback(audio_data)
    
    # 设置自定义处理
    processor.rtp_client.set_audio_callback(custom_audio_handler)
    
    try:
        if processor.start():
            logger.info("自定义音频处理运行中，10 秒后停止...")
            time.sleep(10)
        else:
            logger.error("启动失败")
    finally:
        processor.stop()


def main():
    """主函数 - 运行所有示例"""
    logger.info("FreeSWITCH + MRCP 实时语音流示例程序")
    logger.info("=" * 50)
    
    examples = [
        ("基本 MRCP 客户端", example_1_basic_mrcp_client),
        ("RTP 客户端", example_2_rtp_client),
        ("实时语音流处理器", example_3_realtime_processor),
        ("ESL 流控制器", example_4_esl_controller),
        ("自定义音频处理", example_5_custom_audio_processing),
    ]
    
    print("\n可用示例:")
    for i, (name, _) in enumerate(examples, 1):
        print(f"{i}. {name}")
    
    print("\n请输入要运行的示例编号 (1-5)，或按 Enter 运行所有示例:")
    choice = input().strip()
    
    if choice.isdigit() and 1 <= int(choice) <= len(examples):
        idx = int(choice) - 1
        name, func = examples[idx]
        logger.info(f"\n运行示例: {name}")
        func()
    elif choice == "":
        logger.info("\n运行所有示例...")
        for name, func in examples:
            logger.info(f"\n{'='*50}")
            logger.info(f"运行示例: {name}")
            logger.info(f"{'='*50}")
            try:
                func()
            except Exception as e:
                logger.error(f"示例运行出错: {e}")
            time.sleep(2)
    else:
        logger.error("无效的选择")


if __name__ == "__main__":
    main()
