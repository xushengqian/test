# -*- coding: utf-8 -*-
"""
意图识别模块
支持基于关键词和AI模型的意图识别
"""

import logging
from enum import Enum
from typing import Optional, List
import re

logger = logging.getLogger(__name__)


class IntentType(Enum):
    """意图类型枚举"""
    GREETING = "greeting"
    QUESTION = "question"
    TRANSFER_TO_HUMAN = "transfer_to_human"  # 转人工意图
    GOODBYE = "goodbye"
    UNKNOWN = "unknown"


class IntentRecognizer:
    """意图识别器"""
    
    # 转人工关键词列表
    TRANSFER_KEYWORDS = [
        "转人工", "人工服务", "人工客服", "转接人工",
        "我要人工", "找人工", "人工坐席", "人工台",
        "转接客服", "找客服", "人工", "客服",
        "转人工服务", "人工帮助", "人工咨询"
    ]
    
    # 转人工意图的相似表达
    TRANSFER_PATTERNS = [
        r"我想和.*?说话",
        r"我要找.*?人工",
        r"给我转.*?人工",
        r"帮我转.*?人工",
        r"需要.*?人工.*?帮助",
        r"要.*?人工.*?服务",
        r"联系.*?人工",
        r"接通.*?人工",
    ]
    
    # 问候语关键词
    GREETING_KEYWORDS = [
        "你好", "您好", "hello", "hi", "早上好", "下午好", "晚上好"
    ]
    
    # 告别语关键词
    GOODBYE_KEYWORDS = [
        "再见", "拜拜", "goodbye", "bye", "结束", "好了", "没事了"
    ]
    
    def __init__(self, use_ai_model: bool = False, ai_endpoint: Optional[str] = None):
        """
        初始化意图识别器
        
        Args:
            use_ai_model: 是否使用AI模型
            ai_endpoint: AI模型API端点
        """
        self.use_ai_model = use_ai_model
        self.ai_endpoint = ai_endpoint
    
    def recognize(self, text: str) -> IntentType:
        """
        识别用户意图
        
        Args:
            text: 用户输入的文本
            
        Returns:
            IntentType: 识别的意图类型
        """
        if not text:
            return IntentType.UNKNOWN
        
        text_lower = text.lower().strip()
        
        # 如果使用AI模型，调用AI接口
        if self.use_ai_model and self.ai_endpoint:
            return self._recognize_with_ai(text)
        
        # 基于关键词和模式的识别
        return self._recognize_with_keywords(text_lower)
    
    def _recognize_with_keywords(self, text_lower: str) -> IntentType:
        """基于关键词识别意图"""
        
        # 检查转人工意图（优先级最高）
        for keyword in self.TRANSFER_KEYWORDS:
            if keyword in text_lower:
                logger.info(f"识别到转人工意图（关键词匹配）: {text_lower}")
                return IntentType.TRANSFER_TO_HUMAN
        
        # 检查转人工模式
        for pattern in self.TRANSFER_PATTERNS:
            if re.search(pattern, text_lower):
                logger.info(f"识别到转人工意图（模式匹配）: {text_lower}")
                return IntentType.TRANSFER_TO_HUMAN
        
        # 检查问候语
        for keyword in self.GREETING_KEYWORDS:
            if keyword in text_lower:
                return IntentType.GREETING
        
        # 检查告别语
        for keyword in self.GOODBYE_KEYWORDS:
            if keyword in text_lower:
                return IntentType.GOODBYE
        
        # 默认作为问题处理
        if len(text_lower) > 0:
            return IntentType.QUESTION
        
        return IntentType.UNKNOWN
    
    def _recognize_with_ai(self, text: str) -> IntentType:
        """
        使用AI模型识别意图
        
        Args:
            text: 用户输入的文本
            
        Returns:
            IntentType: 识别的意图类型
        """
        # TODO: 实现AI模型调用
        # 示例：调用大模型API进行意图识别
        try:
            import requests
            
            payload = {
                "text": text,
                "intent_types": ["transfer_to_human", "greeting", "question", "goodbye"]
            }
            
            response = requests.post(self.ai_endpoint, json=payload, timeout=5)
            if response.status_code == 200:
                result = response.json()
                intent_str = result.get("intent", "unknown")
                
                # 转换为IntentType
                try:
                    return IntentType(intent_str)
                except ValueError:
                    return IntentType.UNKNOWN
            else:
                logger.warning(f"AI模型调用失败，回退到关键词识别")
                return self._recognize_with_keywords(text.lower())
                
        except Exception as e:
            logger.error(f"AI模型识别出错: {e}，回退到关键词识别")
            return self._recognize_with_keywords(text.lower())
    
    def is_transfer_intent(self, text: str) -> bool:
        """
        快速检查是否为转人工意图
        
        Args:
            text: 用户输入的文本
            
        Returns:
            bool: 是否为转人工意图
        """
        intent = self.recognize(text)
        return intent == IntentType.TRANSFER_TO_HUMAN
