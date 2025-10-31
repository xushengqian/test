#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
意图识别测试脚本
用于测试意图识别功能
"""

import sys
import json
from intent_detection import IntentDetector

def test_text_intent():
    """测试文本意图识别"""
    detector = IntentDetector()
    
    test_cases = [
        "我要转人工",
        "请帮我转接人工客服",
        "人工服务",
        "找个人工座席",
        "你好，我想咨询一下",
        "这个问题比较复杂，需要人工帮助",
        "转人工",
    ]
    
    print("=" * 50)
    print("文本意图识别测试")
    print("=" * 50)
    
    for text in test_cases:
        intent, confidence = detector.analyze_intent(text)
        result = "✓" if intent == "transfer_to_agent" else "✗"
        print(f"{result} 文本: {text}")
        print(f"  意图: {intent}, 置信度: {confidence:.2f}")
        print()

def test_audio_intent(audio_file):
    """测试音频意图识别"""
    detector = IntentDetector()
    
    print("=" * 50)
    print("音频意图识别测试")
    print("=" * 50)
    print(f"音频文件: {audio_file}")
    print()
    
    result = detector.detect_intent(audio_file)
    
    print("识别结果:")
    print(json.dumps(result, ensure_ascii=False, indent=2))

if __name__ == "__main__":
    if len(sys.argv) > 1:
        # 测试音频文件
        audio_file = sys.argv[1]
        test_audio_intent(audio_file)
    else:
        # 测试文本意图
        test_text_intent()
