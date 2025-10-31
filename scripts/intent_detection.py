#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
意图识别服务
集成语音识别和自然语言处理，识别用户是否想要转人工
"""

import os
import sys
import json
import logging
from typing import Optional, Dict

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class IntentDetector:
    """意图识别器"""
    
    def __init__(self):
        # 转人工相关的关键词
        self.transfer_keywords = [
            "转人工", "人工服务", "人工客服", "转接人工",
            "人工", "客服", "人工座席", "人工接线员",
            "我要人工", "找人工", "联系人工", "接人工"
        ]
        
        # 可以在这里初始化第三方服务
        # 例如：百度语音识别、讯飞语音识别、阿里云等
        # self.asr_client = BaiduASRClient()
        # self.nlp_client = SomeNLPClient()
    
    def detect_intent(self, audio_file: str) -> Dict[str, any]:
        """
        检测意图
        
        Args:
            audio_file: 音频文件路径
            
        Returns:
            {
                "intent": "transfer_to_agent" | "continue_robot" | "unknown",
                "confidence": 0.0-1.0,
                "text": "识别到的文本"
            }
        """
        try:
            # 步骤1: 语音识别 (ASR)
            text = self.speech_to_text(audio_file)
            
            if not text:
                logger.warning("语音识别失败或无内容")
                return {
                    "intent": "unknown",
                    "confidence": 0.0,
                    "text": ""
                }
            
            logger.info(f"识别文本: {text}")
            
            # 步骤2: 意图识别
            intent, confidence = self.analyze_intent(text)
            
            return {
                "intent": intent,
                "confidence": confidence,
                "text": text
            }
            
        except Exception as e:
            logger.error(f"意图识别出错: {e}", exc_info=True)
            return {
                "intent": "unknown",
                "confidence": 0.0,
                "text": ""
            }
    
    def speech_to_text(self, audio_file: str) -> Optional[str]:
        """
        语音转文字
        
        实际项目中应该调用：
        - 百度语音识别 API
        - 讯飞语音识别 API
        - 阿里云语音识别 API
        - 或其他 ASR 服务
        """
        if not os.path.exists(audio_file):
            logger.error(f"音频文件不存在: {audio_file}")
            return None
        
        # TODO: 集成实际的语音识别服务
        # 示例：
        # result = self.asr_client.recognize(audio_file)
        # return result['text']
        
        # 临时示例：使用简单的关键词匹配
        # 实际应该调用 ASR API
        logger.info(f"处理音频文件: {audio_file}")
        
        # 这里应该调用实际的 ASR 服务
        # 返回 None 表示识别失败或未实现
        return None
    
    def analyze_intent(self, text: str) -> tuple:
        """
        分析文本意图
        
        Args:
            text: 输入文本
            
        Returns:
            (intent, confidence) 元组
        """
        if not text:
            return ("unknown", 0.0)
        
        text_lower = text.lower().strip()
        
        # 检查是否包含转人工关键词
        for keyword in self.transfer_keywords:
            if keyword in text_lower:
                logger.info(f"检测到转人工关键词: {keyword}")
                return ("transfer_to_agent", 0.9)
        
        # 可以使用更高级的 NLP 模型
        # 例如：BERT、GPT 等模型进行意图分类
        
        # 默认继续机器人对话
        return ("continue_robot", 0.7)


def main():
    """命令行入口"""
    if len(sys.argv) < 2:
        print("Usage: python intent_detection.py <audio_file>")
        sys.exit(1)
    
    audio_file = sys.argv[1]
    detector = IntentDetector()
    result = detector.detect_intent(audio_file)
    
    # 输出 JSON 格式结果，供 Lua 脚本调用
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
