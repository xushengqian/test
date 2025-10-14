"""
意图识别模块
识别用户是否有转人工的意图
"""
import re
from typing import Tuple, List, Dict, Any
from pathlib import Path

import yaml

from utils.logger import get_logger
from utils.database import db_manager

logger = get_logger(__name__)

# 加载配置
config_path = Path(__file__).parent.parent / "config" / "config.yaml"
with open(config_path, 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

intent_config = config.get('intent', {})

class IntentDetector:
    """意图识别器"""
    
    def __init__(self):
        # 转人工关键词
        self.transfer_keywords = intent_config.get('transfer_keywords', [
            "人工", "客服", "转接", "人工客服", "转人工",
            "找人", "专员", "人工服务", "不是机器人", "真人"
        ])
        
        # 置信度阈值
        self.confidence_threshold = intent_config.get('confidence_threshold', 0.7)
        
        # 构建正则表达式模式
        self._build_patterns()
        
        logger.info(f"意图识别器初始化成功，关键词: {self.transfer_keywords}")
    
    def _build_patterns(self):
        """构建正则表达式模式"""
        # 转人工意图模式
        self.transfer_patterns = []
        
        for keyword in self.transfer_keywords:
            # 创建模糊匹配的正则表达式
            pattern = re.compile(f".*{re.escape(keyword)}.*", re.IGNORECASE)
            self.transfer_patterns.append(pattern)
        
        # 额外的模式匹配
        additional_patterns = [
            r".*不想.*机器.*",
            r".*能不能.*人.*",
            r".*给我.*人.*",
            r".*要.*真.*人.*",
            r".*帮.*转.*",
            r".*接.*人工.*",
            r".*找.*客服.*",
        ]
        
        for pattern_str in additional_patterns:
            pattern = re.compile(pattern_str, re.IGNORECASE)
            self.transfer_patterns.append(pattern)
    
    def detect_intent(self, text: str, call_id: str = None) -> Tuple[str, float, Dict[str, Any]]:
        """
        识别用户意图
        
        Args:
            text: 用户输入的文本
            call_id: 通话ID（用于记录）
            
        Returns:
            (意图类型, 置信度, 额外信息)
        """
        if not text:
            return 'unknown', 0.0, {}
        
        # 移除空格，统一处理
        text = text.strip()
        
        # 检测转人工意图
        is_transfer, confidence = self._detect_transfer_intent(text)
        
        if is_transfer:
            intent = 'transfer_to_agent'
            extra = {'reason': '用户明确要求转人工'}
        else:
            # 这里可以添加其他意图的检测
            intent = 'continue_robot'
            confidence = 0.5
            extra = {}
        
        # 记录意图识别结果
        if call_id:
            db_manager.log_intent(call_id, text, intent, confidence)
        
        logger.info(f"意图识别结果 - 文本: {text}, 意图: {intent}, 置信度: {confidence}")
        
        return intent, confidence, extra
    
    def _detect_transfer_intent(self, text: str) -> Tuple[bool, float]:
        """
        检测是否有转人工意图
        
        Args:
            text: 用户输入的文本
            
        Returns:
            (是否转人工, 置信度)
        """
        # 直接关键词匹配
        for keyword in self.transfer_keywords:
            if keyword in text:
                # 完全匹配关键词，给高置信度
                return True, 0.95
        
        # 正则模式匹配
        match_count = 0
        for pattern in self.transfer_patterns:
            if pattern.match(text):
                match_count += 1
        
        if match_count > 0:
            # 根据匹配的模式数量计算置信度
            confidence = min(0.7 + (match_count * 0.1), 0.95)
            return True, confidence
        
        # 基于情感的判断（负面情绪可能想转人工）
        negative_words = ["不满意", "不行", "不好", "差", "烦", "生气", "投诉", "不懂"]
        negative_count = sum(1 for word in negative_words if word in text)
        
        if negative_count >= 2:
            return True, 0.6
        
        return False, 0.0
    
    def should_transfer(self, text: str, call_id: str = None) -> bool:
        """
        判断是否应该转接人工
        
        Args:
            text: 用户输入的文本
            call_id: 通话ID
            
        Returns:
            是否转接
        """
        intent, confidence, _ = self.detect_intent(text, call_id)
        
        # 如果意图是转人工且置信度超过阈值
        if intent == 'transfer_to_agent' and confidence >= self.confidence_threshold:
            return True
        
        return False
    
    def get_transfer_reason(self, text: str) -> str:
        """
        获取转人工的原因描述
        
        Args:
            text: 用户输入的文本
            
        Returns:
            原因描述
        """
        reasons = []
        
        # 检查具体原因
        if "投诉" in text:
            reasons.append("客户投诉")
        elif "不满" in text or "生气" in text:
            reasons.append("客户情绪不满")
        elif "紧急" in text or "着急" in text:
            reasons.append("紧急事务")
        elif "复杂" in text or "不懂" in text:
            reasons.append("问题复杂")
        else:
            # 检查匹配的关键词
            for keyword in self.transfer_keywords:
                if keyword in text:
                    reasons.append(f"客户明确要求: {keyword}")
                    break
        
        if not reasons:
            reasons.append("客户要求转人工")
        
        return ", ".join(reasons)

class IntentAnalyzer:
    """意图分析器（高级功能）"""
    
    def __init__(self):
        self.detector = IntentDetector()
        self.history = {}  # 存储会话历史
    
    def analyze_session(self, call_id: str, messages: List[str]) -> Dict[str, Any]:
        """
        分析整个会话的意图
        
        Args:
            call_id: 通话ID
            messages: 消息列表
            
        Returns:
            分析结果
        """
        results = {
            'call_id': call_id,
            'message_count': len(messages),
            'transfer_requested': False,
            'transfer_confidence': 0.0,
            'reasons': [],
            'user_satisfaction': 'unknown'
        }
        
        # 分析每条消息
        transfer_scores = []
        for msg in messages:
            intent, confidence, extra = self.detector.detect_intent(msg, call_id)
            
            if intent == 'transfer_to_agent':
                transfer_scores.append(confidence)
                reason = self.detector.get_transfer_reason(msg)
                if reason not in results['reasons']:
                    results['reasons'].append(reason)
        
        # 综合判断
        if transfer_scores:
            results['transfer_requested'] = True
            results['transfer_confidence'] = max(transfer_scores)
        
        # 分析用户满意度
        results['user_satisfaction'] = self._analyze_satisfaction(messages)
        
        return results
    
    def _analyze_satisfaction(self, messages: List[str]) -> str:
        """
        分析用户满意度
        
        Args:
            messages: 消息列表
            
        Returns:
            满意度评级
        """
        positive_words = ["好的", "谢谢", "明白", "可以", "行", "OK"]
        negative_words = ["不行", "不满意", "差", "烦", "投诉"]
        
        positive_count = 0
        negative_count = 0
        
        for msg in messages:
            for word in positive_words:
                if word in msg:
                    positive_count += 1
            for word in negative_words:
                if word in msg:
                    negative_count += 1
        
        if positive_count > negative_count * 2:
            return 'satisfied'
        elif negative_count > positive_count * 2:
            return 'dissatisfied'
        else:
            return 'neutral'

# 创建全局实例
intent_detector = IntentDetector()
intent_analyzer = IntentAnalyzer()

# 导出
__all__ = ['intent_detector', 'intent_analyzer', 'IntentDetector', 'IntentAnalyzer']