#!/usr/bin/env python3
"""
实时语音转写服务
从音频文件流式读取并发送到WebSocket进行实时转写
"""

import sys
import asyncio
import websockets
import json
import wave
import time
import logging
from pathlib import Path

# 设置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class RealTimeTranscription:
    def __init__(self, audio_file, websocket_url):
        self.audio_file = audio_file
        self.websocket_url = websocket_url
        self.chunk_size = 1024  # 音频块大小
        self.sample_rate = 16000
        
    async def start_transcription(self):
        """开始实时转写"""
        try:
            async with websockets.connect(self.websocket_url) as websocket:
                logger.info(f"Connected to WebSocket: {self.websocket_url}")
                
                # 等待音频文件创建
                while not Path(self.audio_file).exists():
                    await asyncio.sleep(0.1)
                
                # 流式读取音频文件
                with wave.open(self.audio_file, 'rb') as wav_file:
                    while True:
                        # 读取音频数据
                        audio_data = wav_file.readframes(self.chunk_size)
                        if not audio_data:
                            break
                        
                        # 发送到WebSocket
                        await websocket.send(audio_data)
                        
                        # 等待一小段时间
                        await asyncio.sleep(0.1)
                        
        except Exception as e:
            logger.error(f"Transcription error: {e}")

async def main():
    if len(sys.argv) != 3:
        print("Usage: python3 real_time_transcription.py <audio_file> <websocket_url>")
        sys.exit(1)
    
    audio_file = sys.argv[1]
    websocket_url = sys.argv[2]
    
    transcription = RealTimeTranscription(audio_file, websocket_url)
    await transcription.start_transcription()

if __name__ == "__main__":
    asyncio.run(main())