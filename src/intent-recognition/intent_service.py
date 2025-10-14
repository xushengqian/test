#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
意图识别服务
提供用户意图识别和情感分析功能
"""

import re
import json
import logging
from typing import Dict, List, Optional, Tuple
from datetime import datetime
import jieba
import jieba.posseg as pseg
from collections import Counter
import pickle
import os

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class IntentClassifier:
    """意图分类器"""
    
    def __init__(self, model_path: str = "models/intent_model.pkl"):
        self.model_path = model_path
        self.intent_rules = self.load_intent_rules()
        self.emotion_rules = self.load_emotion_rules()
        self.keyword_weights = self.load_keyword_weights()
        
        # 初始化 jieba 分词
        self.init_jieba()
        
    def init_jieba(self):
        """初始化 jieba 分词器"""
        try:
            # 添加自定义词典
            custom_words = [
                "转人工", "人工客服", "人工服务", "客服",
                "投诉", "抱怨", "不满意", "退款", "赔偿",
                "产品咨询", "技术支持", "使用帮助",
                "机器人", "智能客服", "语音助手"
            ]
            
            for word in custom_words:
                jieba.add_word(word)
                
            logger.info("jieba 分词器初始化完成")
            
        except Exception as e:
            logger.error(f"初始化 jieba 失败: {str(e)}")
    
    def load_intent_rules(self) -> Dict:
        """加载意图识别规则"""
        return {
            "GREETING": {
                "keywords": [
                    "你好", "您好", "hello", "hi", "早上好", "下午好", 
                    "晚上好", "喂", "哈喽"
                ],
                "patterns": [
                    r"^(你好|您好|hello|hi)",
                    r"(早上好|下午好|晚上好)",
                    r"^喂"
                ],
                "weight": 0.9
            },
            
            "TRANSFER_TO_AGENT": {
                "keywords": [
                    "转人工", "人工客服", "人工服务", "转接人工", "我要找人工",
                    "要人工", "人工", "客服", "转接", "转人", "找人", "真人",
                    "不要机器人", "机器人不行", "你不行", "转接客服",
                    "找客服", "联系客服", "人工坐席", "真人客服"
                ],
                "patterns": [
                    r"转.*人工",
                    r"人工.*客服",
                    r"不要.*机器人",
                    r"机器人.*不行",
                    r"找.*客服",
                    r"要.*人工"
                ],
                "weight": 0.95
            },
            
            "COMPLAINT": {
                "keywords": [
                    "投诉", "抱怨", "不满意", "问题", "故障", "错误", "失望",
                    "糟糕", "差劲", "不好", "退款", "赔偿", "解决不了",
                    "有问题", "出问题", "不满", "气愤", "愤怒"
                ],
                "patterns": [
                    r"投诉|抱怨|不满",
                    r"有问题|出问题",
                    r"退款|赔偿",
                    r"解决不了",
                    r"不满意"
                ],
                "weight": 0.8,
                "emotion": "angry"
            },
            
            "PRODUCT_INQUIRY": {
                "keywords": [
                    "产品", "服务", "价格", "费用", "多少钱", "怎么样", "介绍",
                    "功能", "特点", "优势", "购买", "订购", "了解", "咨询",
                    "套餐", "方案", "收费", "资费"
                ],
                "patterns": [
                    r"产品.*怎么样",
                    r"多少钱|什么价格",
                    r"介绍.*产品",
                    r"了解.*产品",
                    r"咨询.*产品"
                ],
                "weight": 0.7
            },
            
            "TECHNICAL_SUPPORT": {
                "keywords": [
                    "技术", "支持", "帮助", "使用", "操作", "设置", "配置",
                    "安装", "连接", "登录", "密码", "账号", "故障", "修复",
                    "不会用", "怎么用", "如何操作"
                ],
                "patterns": [
                    r"技术.*支持",
                    r"怎么.*使用",
                    r"如何.*操作",
                    r"不会.*用",
                    r"怎么.*设置"
                ],
                "weight": 0.7
            },
            
            "GOODBYE": {
                "keywords": [
                    "再见", "拜拜", "谢谢", "好的", "没事了", "结束",
                    "挂了", "不用了", "可以了", "明白了"
                ],
                "patterns": [
                    r"再见|拜拜",
                    r"谢谢.*再见",
                    r"没事了|不用了",
                    r"可以了|明白了"
                ],
                "weight": 0.8
            },
            
            "AFFIRMATION": {
                "keywords": [
                    "是的", "对的", "没错", "正确", "对", "是", "嗯", "好",
                    "可以", "行", "同意", "确认"
                ],
                "patterns": [
                    r"^(是的|对的|没错|正确)$",
                    r"^(对|是|嗯|好)$",
                    r"^(可以|行)$"
                ],
                "weight": 0.6
            },
            
            "NEGATION": {
                "keywords": [
                    "不是", "不对", "错了", "不", "没有", "不要", "不行",
                    "不可以", "不同意", "拒绝"
                ],
                "patterns": [
                    r"^(不是|不对|错了)$",
                    r"^(不|没有|不要)$",
                    r"不可以|不同意"
                ],
                "weight": 0.6
            }
        }
    
    def load_emotion_rules(self) -> Dict:
        """加载情感识别规则"""
        return {
            "angry": {
                "keywords": [
                    "气死了", "太气人", "愤怒", "生气", "火大", "烦死了",
                    "什么破", "垃圾", "差劲", "糟糕透了", "受不了",
                    "太过分", "不像话", "气人", "讨厌"
                ],
                "patterns": [
                    r"气死|愤怒|生气|火大",
                    r"什么破|垃圾|差劲",
                    r"受不了|太过分"
                ],
                "weight": 0.8
            },
            
            "frustrated": {
                "keywords": [
                    "郁闷", "烦躁", "无语", "头疼", "麻烦", "复杂",
                    "搞不懂", "弄不明白", "太难了", "困惑", "纠结"
                ],
                "patterns": [
                    r"郁闷|烦躁|无语",
                    r"搞不懂|弄不明白",
                    r"太难了|困惑"
                ],
                "weight": 0.7
            },
            
            "happy": {
                "keywords": [
                    "开心", "高兴", "满意", "不错", "很好", "棒",
                    "谢谢", "感谢", "太好了", "完美", "赞"
                ],
                "patterns": [
                    r"开心|高兴|满意",
                    r"很好|太好了|完美",
                    r"谢谢|感谢"
                ],
                "weight": 0.7
            },
            
            "worried": {
                "keywords": [
                    "担心", "忧虑", "不安", "紧张", "焦虑", "害怕",
                    "不放心", "顾虑", "犹豫"
                ],
                "patterns": [
                    r"担心|忧虑|不安",
                    r"紧张|焦虑|害怕",
                    r"不放心|顾虑"
                ],
                "weight": 0.6
            }
        }
    
    def load_keyword_weights(self) -> Dict:
        """加载关键词权重"""
        return {
            # 强转人工信号
            "转人工": 1.0,
            "人工客服": 1.0,
            "不要机器人": 1.0,
            
            # 投诉相关
            "投诉": 0.9,
            "退款": 0.8,
            "赔偿": 0.8,
            
            # 情感强度
            "气死了": 0.9,
            "太好了": 0.8,
            "谢谢": 0.7,
            
            # 否定词
            "不": 0.3,
            "没有": 0.3,
            "不是": 0.4
        }
    
    def preprocess_text(self, text: str) -> str:
        """文本预处理"""
        if not text:
            return ""
        
        # 转换为小写
        text = text.lower()
        
        # 去除多余空格
        text = re.sub(r'\s+', ' ', text).strip()
        
        # 数字转换
        number_map = {
            '零': '0', '一': '1', '二': '2', '三': '3', '四': '4',
            '五': '5', '六': '6', '七': '7', '八': '8', '九': '9', '十': '10'
        }
        
        for chinese, digit in number_map.items():
            text = text.replace(chinese, digit)
        
        # 常见错误修正
        corrections = {
            "人工客户": "人工客服",
            "转人公": "转人工",
            "我要找人": "我要找人工",
            "机器人不行": "机器人不行"
        }
        
        for wrong, correct in corrections.items():
            text = text.replace(wrong, correct)
        
        return text
    
    def extract_features(self, text: str) -> Dict:
        """提取文本特征"""
        features = {
            'length': len(text),
            'word_count': len(text.split()),
            'has_question': '?' in text or '？' in text,
            'has_exclamation': '!' in text or '！' in text,
            'keywords': [],
            'pos_tags': [],
            'sentiment_words': []
        }
        
        # 分词和词性标注
        words = pseg.cut(text)
        for word, flag in words:
            features['pos_tags'].append((word, flag))
            
            # 收集关键词
            if len(word) > 1 and flag in ['n', 'v', 'a', 'nr', 'ns', 'nt']:
                features['keywords'].append(word)
        
        return features
    
    def calculate_intent_score(self, text: str, intent_rule: Dict) -> float:
        """计算意图匹配分数"""
        score = 0.0
        text_lower = text.lower()
        
        # 关键词匹配
        keyword_matches = 0
        if 'keywords' in intent_rule:
            for keyword in intent_rule['keywords']:
                if keyword in text_lower:
                    keyword_matches += 1
                    # 应用关键词权重
                    weight = self.keyword_weights.get(keyword, 0.5)
                    score += weight * 0.6
        
        # 模式匹配
        pattern_matches = 0
        if 'patterns' in intent_rule:
            for pattern in intent_rule['patterns']:
                if re.search(pattern, text_lower):
                    pattern_matches += 1
                    score += 0.4
        
        # 应用规则权重
        if score > 0 and 'weight' in intent_rule:
            score *= intent_rule['weight']
        
        # 归一化分数
        return min(score, 1.0)
    
    def recognize_intent(self, text: str) -> Dict:
        """识别用户意图"""
        if not text or not isinstance(text, str):
            return {
                'intent': 'UNKNOWN',
                'confidence': 0.0,
                'emotion': 'neutral',
                'keywords': [],
                'raw_text': text
            }
        
        # 预处理文本
        processed_text = self.preprocess_text(text)
        
        # 提取特征
        features = self.extract_features(processed_text)
        
        # 计算各意图的分数
        intent_scores = {}
        for intent_type, rule in self.intent_rules.items():
            score = self.calculate_intent_score(processed_text, rule)
            if score > 0:
                intent_scores[intent_type] = score
        
        # 选择最高分数的意图
        if intent_scores:
            best_intent = max(intent_scores.items(), key=lambda x: x[1])
            intent_type, confidence = best_intent
        else:
            intent_type, confidence = 'UNKNOWN', 0.0
        
        # 识别情感
        emotion = self.recognize_emotion(processed_text)
        
        # 提取匹配的关键词
        matched_keywords = self.extract_matched_keywords(processed_text, intent_type)
        
        result = {
            'intent': intent_type,
            'confidence': confidence,
            'emotion': emotion,
            'keywords': matched_keywords,
            'raw_text': text,
            'processed_text': processed_text,
            'features': features,
            'all_scores': intent_scores,
            'timestamp': datetime.now().isoformat()
        }
        
        logger.info(f"意图识别结果: {intent_type} (置信度: {confidence:.2f}, 情感: {emotion})")
        
        return result
    
    def recognize_emotion(self, text: str) -> str:
        """识别情感"""
        emotion_scores = {}
        
        for emotion_type, rule in self.emotion_rules.items():
            score = 0.0
            
            # 关键词匹配
            if 'keywords' in rule:
                for keyword in rule['keywords']:
                    if keyword in text:
                        score += 0.3
            
            # 模式匹配
            if 'patterns' in rule:
                for pattern in rule['patterns']:
                    if re.search(pattern, text):
                        score += 0.5
            
            # 应用权重
            if score > 0 and 'weight' in rule:
                score *= rule['weight']
            
            if score > 0:
                emotion_scores[emotion_type] = score
        
        # 选择最高分数的情感
        if emotion_scores:
            return max(emotion_scores.items(), key=lambda x: x[1])[0]
        else:
            return 'neutral'
    
    def extract_matched_keywords(self, text: str, intent_type: str) -> List[str]:
        """提取匹配的关键词"""
        matched = []
        
        if intent_type in self.intent_rules:
            rule = self.intent_rules[intent_type]
            if 'keywords' in rule:
                for keyword in rule['keywords']:
                    if keyword in text:
                        matched.append(keyword)
        
        return matched
    
    def should_transfer_to_agent(self, intent_result: Dict, 
                                conversation_history: List[Dict] = None) -> bool:
        """判断是否应该转人工"""
        intent = intent_result['intent']
        confidence = intent_result['confidence']
        emotion = intent_result['emotion']
        
        # 明确的转人工意图
        if intent == 'TRANSFER_TO_AGENT' and confidence > 0.7:
            return True
        
        # 投诉且情感为愤怒
        if intent == 'COMPLAINT' and emotion == 'angry':
            return True
        
        # 连续的未知意图
        if conversation_history:
            recent_unknowns = sum(1 for h in conversation_history[-3:] 
                                if h.get('intent') == 'UNKNOWN')
            if recent_unknowns >= 2:
                return True
        
        # 置信度过低且用户表达复杂
        if confidence < 0.3 and len(intent_result['raw_text']) > 20:
            return True
        
        # 负面情感累积
        if conversation_history:
            negative_emotions = sum(1 for h in conversation_history[-5:]
                                  if h.get('emotion') in ['angry', 'frustrated'])
            if negative_emotions >= 2:
                return True
        
        return False
    
    def analyze_conversation_trend(self, conversation_history: List[Dict]) -> Dict:
        """分析对话趋势"""
        if not conversation_history:
            return {'trend': 'neutral', 'recommendation': 'continue'}
        
        recent_intents = [h.get('intent', 'UNKNOWN') for h in conversation_history[-5:]]
        recent_emotions = [h.get('emotion', 'neutral') for h in conversation_history[-5:]]
        
        # 意图分布
        intent_counter = Counter(recent_intents)
        emotion_counter = Counter(recent_emotions)
        
        # 趋势分析
        trend = 'neutral'
        recommendation = 'continue'
        
        # 检查负面趋势
        negative_count = sum(emotion_counter[e] for e in ['angry', 'frustrated'])
        if negative_count >= 2:
            trend = 'negative'
            recommendation = 'transfer'
        
        # 检查未知意图过多
        if intent_counter.get('UNKNOWN', 0) >= 2:
            trend = 'confused'
            recommendation = 'clarify_or_transfer'
        
        # 检查转人工请求
        if intent_counter.get('TRANSFER_TO_AGENT', 0) >= 1:
            trend = 'transfer_requested'
            recommendation = 'transfer'
        
        return {
            'trend': trend,
            'recommendation': recommendation,
            'intent_distribution': dict(intent_counter),
            'emotion_distribution': dict(emotion_counter),
            'conversation_turns': len(conversation_history)
        }


class IntentService:
    """意图识别服务"""
    
    def __init__(self):
        self.classifier = IntentClassifier()
        self.conversation_sessions = {}  # 存储对话会话
        
    def process_user_input(self, session_id: str, user_input: str, 
                          context: Dict = None) -> Dict:
        """处理用户输入"""
        try:
            # 识别意图
            intent_result = self.classifier.recognize_intent(user_input)
            
            # 获取或创建对话会话
            if session_id not in self.conversation_sessions:
                self.conversation_sessions[session_id] = {
                    'history': [],
                    'start_time': datetime.now(),
                    'context': context or {}
                }
            
            session = self.conversation_sessions[session_id]
            
            # 添加到对话历史
            session['history'].append(intent_result)
            
            # 分析对话趋势
            trend_analysis = self.classifier.analyze_conversation_trend(session['history'])
            
            # 判断是否需要转人工
            should_transfer = self.classifier.should_transfer_to_agent(
                intent_result, session['history']
            )
            
            # 构建响应
            response = {
                'session_id': session_id,
                'intent_result': intent_result,
                'should_transfer': should_transfer,
                'trend_analysis': trend_analysis,
                'conversation_turns': len(session['history']),
                'timestamp': datetime.now().isoformat()
            }
            
            return response
            
        except Exception as e:
            logger.error(f"处理用户输入异常: {str(e)}")
            return {
                'session_id': session_id,
                'intent_result': {
                    'intent': 'ERROR',
                    'confidence': 0.0,
                    'emotion': 'neutral',
                    'keywords': [],
                    'raw_text': user_input
                },
                'should_transfer': True,  # 出错时建议转人工
                'error': str(e)
            }
    
    def get_session_info(self, session_id: str) -> Optional[Dict]:
        """获取会话信息"""
        return self.conversation_sessions.get(session_id)
    
    def clear_session(self, session_id: str):
        """清除会话"""
        if session_id in self.conversation_sessions:
            del self.conversation_sessions[session_id]
    
    def get_statistics(self) -> Dict:
        """获取统计信息"""
        total_sessions = len(self.conversation_sessions)
        total_turns = sum(len(session['history']) for session in self.conversation_sessions.values())
        
        # 意图统计
        all_intents = []
        all_emotions = []
        
        for session in self.conversation_sessions.values():
            for turn in session['history']:
                all_intents.append(turn.get('intent', 'UNKNOWN'))
                all_emotions.append(turn.get('emotion', 'neutral'))
        
        intent_stats = Counter(all_intents)
        emotion_stats = Counter(all_emotions)
        
        return {
            'total_sessions': total_sessions,
            'total_turns': total_turns,
            'avg_turns_per_session': total_turns / max(total_sessions, 1),
            'intent_distribution': dict(intent_stats),
            'emotion_distribution': dict(emotion_stats),
            'timestamp': datetime.now().isoformat()
        }


def main():
    """测试主函数"""
    service = IntentService()
    
    # 测试用例
    test_cases = [
        "你好，我想咨询一下产品",
        "转人工客服",
        "我要投诉，你们的服务太差了",
        "机器人不行，我要找真人",
        "谢谢，再见",
        "这个怎么用啊，搞不懂",
        "退款！我要退款！"
    ]
    
    session_id = "test_session_001"
    
    for i, text in enumerate(test_cases):
        print(f"\n=== 测试 {i+1}: {text} ===")
        result = service.process_user_input(session_id, text)
        
        intent_result = result['intent_result']
        print(f"意图: {intent_result['intent']}")
        print(f"置信度: {intent_result['confidence']:.2f}")
        print(f"情感: {intent_result['emotion']}")
        print(f"关键词: {intent_result['keywords']}")
        print(f"是否转人工: {result['should_transfer']}")
        print(f"趋势分析: {result['trend_analysis']}")
    
    # 打印统计信息
    print(f"\n=== 统计信息 ===")
    stats = service.get_statistics()
    print(json.dumps(stats, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()