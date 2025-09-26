import asyncio
import logging
from typing import Dict, List, Optional
import json
import httpx
from datetime import datetime

from app.core.config import settings

logger = logging.getLogger(__name__)

class RobotService:
    """机器人服务"""
    
    def __init__(self):
        self.conversation_contexts: Dict[str, dict] = {}
        self.intent_classifier = IntentClassifier()
        
    async def process_chat(self, user_input: str, customer_phone: str, 
                          call_uuid: str, context: dict = None) -> dict:
        """处理机器人对话"""
        try:
            # 获取或创建对话上下文
            if call_uuid not in self.conversation_contexts:
                self.conversation_contexts[call_uuid] = {
                    "customer_phone": customer_phone,
                    "conversation_history": [],
                    "customer_intent": "unknown",
                    "transfer_score": 0,
                    "created_at": datetime.utcnow()
                }
            
            context = self.conversation_contexts[call_uuid]
            
            # 添加用户输入到历史
            context["conversation_history"].append({
                "speaker": "customer",
                "content": user_input,
                "timestamp": datetime.utcnow()
            })
            
            # 意图识别
            intent_result = await self.intent_classifier.classify(user_input, context)
            context["customer_intent"] = intent_result.get("intent", "unknown")
            context["transfer_score"] += intent_result.get("transfer_score", 0)
            
            # 生成回复
            response = await self._generate_response(user_input, context, intent_result)
            
            # 添加机器人回复到历史
            if response.get("message"):
                context["conversation_history"].append({
                    "speaker": "robot",
                    "content": response["message"],
                    "timestamp": datetime.utcnow()
                })
            
            # 判断是否需要转人工
            if self._should_transfer_to_human(context, intent_result):
                response["action"] = "transfer_human"
                response["transfer_reason"] = self._get_transfer_reason(context, intent_result)
            
            # 更新上下文
            response["context"] = context
            
            logger.info(f"Robot response for {customer_phone}: {response.get('message', '')[:50]}...")
            return response
            
        except Exception as e:
            logger.error(f"Error processing chat: {e}")
            return {
                "message": "抱歉，我遇到了一些问题，正在为您转接人工客服",
                "action": "transfer_human",
                "transfer_reason": "system_error"
            }
    
    async def _generate_response(self, user_input: str, context: dict, intent_result: dict) -> dict:
        """生成机器人回复"""
        intent = intent_result.get("intent", "unknown")
        entities = intent_result.get("entities", {})
        
        # 根据意图生成回复
        if intent == "greeting":
            return await self._handle_greeting(user_input, context)
        
        elif intent == "product_inquiry":
            return await self._handle_product_inquiry(user_input, context, entities)
        
        elif intent == "price_inquiry":
            return await self._handle_price_inquiry(user_input, context, entities)
        
        elif intent == "complaint":
            return await self._handle_complaint(user_input, context)
        
        elif intent == "request_human":
            return {
                "message": "好的，我马上为您转接人工客服",
                "action": "transfer_human",
                "transfer_reason": "user_request"
            }
        
        elif intent == "negative_response":
            return await self._handle_negative_response(user_input, context)
        
        elif intent == "positive_response":
            return await self._handle_positive_response(user_input, context)
        
        else:
            return await self._handle_unknown_intent(user_input, context)
    
    async def _handle_greeting(self, user_input: str, context: dict) -> dict:
        """处理问候"""
        responses = [
            "您好！很高兴为您服务，请问有什么可以帮助您的吗？",
            "您好！我是智能客服，有什么问题可以咨询我",
            "您好！欢迎咨询，请问您需要了解什么呢？"
        ]
        
        # 简单轮询选择回复
        history_count = len(context.get("conversation_history", []))
        response_index = history_count % len(responses)
        
        return {
            "message": responses[response_index],
            "intent": "greeting"
        }
    
    async def _handle_product_inquiry(self, user_input: str, context: dict, entities: dict) -> dict:
        """处理产品咨询"""
        product = entities.get("product", "我们的产品")
        
        responses = [
            f"关于{product}，我们有多种选择。您具体想了解哪方面的信息呢？比如功能特点、价格或者使用方法？",
            f"{product}是我们的热门产品，具有很多优势。您是想了解产品详情还是有其他具体问题？",
            f"我来为您介绍一下{product}。请问您最关心的是什么方面呢？"
        ]
        
        history_count = len([h for h in context.get("conversation_history", []) if h.get("speaker") == "robot"])
        response_index = history_count % len(responses)
        
        return {
            "message": responses[response_index],
            "intent": "product_inquiry"
        }
    
    async def _handle_price_inquiry(self, user_input: str, context: dict, entities: dict) -> dict:
        """处理价格咨询"""
        return {
            "message": "关于价格方面的详细信息，我建议您直接与我们的专业顾问沟通，他们可以根据您的具体需求提供最优惠的方案。我现在就为您转接，好吗？",
            "action": "transfer_human",
            "transfer_reason": "price_inquiry"
        }
    
    async def _handle_complaint(self, user_input: str, context: dict) -> dict:
        """处理投诉"""
        return {
            "message": "非常抱歉给您带来了不便。您的问题我们非常重视，我立即为您转接专门的客服人员来处理，请稍等。",
            "action": "transfer_human",
            "transfer_reason": "complaint"
        }
    
    async def _handle_negative_response(self, user_input: str, context: dict) -> dict:
        """处理负面回应"""
        context["transfer_score"] += 2
        
        responses = [
            "我理解您的想法。不过我们的产品确实有很多优势，要不我为您详细介绍一下？",
            "没关系，每个人的需求不同。您可以先了解一下，没有任何压力。",
            "我明白，如果您现在不方便，我可以为您转接专业顾问，或者您可以留个联系方式，我们稍后联系您。"
        ]
        
        history_count = len([h for h in context.get("conversation_history", []) if h.get("speaker") == "robot"])
        response_index = history_count % len(responses)
        
        return {
            "message": responses[response_index],
            "intent": "negative_response"
        }
    
    async def _handle_positive_response(self, user_input: str, context: dict) -> dict:
        """处理积极回应"""
        context["transfer_score"] -= 1  # 降低转人工分数
        
        responses = [
            "太好了！那我为您详细介绍一下具体的方案和优惠政策。",
            "很高兴您感兴趣！我们现在有特别优惠，我来为您详细说明。",
            "非常好！为了给您提供最合适的方案，我建议您与我们的专业顾问详细沟通一下。"
        ]
        
        history_count = len([h for h in context.get("conversation_history", []) if h.get("speaker") == "robot"])
        response_index = history_count % len(responses)
        
        return {
            "message": responses[response_index],
            "intent": "positive_response"
        }
    
    async def _handle_unknown_intent(self, user_input: str, context: dict) -> dict:
        """处理未知意图"""
        context["transfer_score"] += 1
        
        responses = [
            "抱歉，我没有完全理解您的意思。您可以换个方式说一下吗？",
            "我可能没有理解清楚，您是想了解什么方面的信息呢？",
            "不好意思，您能再详细说明一下您的问题吗？或者我为您转接人工客服？"
        ]
        
        history_count = len([h for h in context.get("conversation_history", []) if h.get("speaker") == "robot"])
        response_index = history_count % len(responses)
        
        return {
            "message": responses[response_index],
            "intent": "unknown"
        }
    
    def _should_transfer_to_human(self, context: dict, intent_result: dict) -> bool:
        """判断是否应该转人工"""
        # 转人工的条件
        transfer_score = context.get("transfer_score", 0)
        conversation_count = len(context.get("conversation_history", []))
        intent = intent_result.get("intent", "unknown")
        
        # 直接转人工的意图
        if intent in ["request_human", "complaint", "price_inquiry"]:
            return True
        
        # 转人工分数过高
        if transfer_score >= 5:
            return True
        
        # 对话轮次过多
        if conversation_count >= 10:
            return True
        
        # 连续未知意图
        recent_intents = [h.get("intent") for h in context.get("conversation_history", [])[-3:] 
                         if h.get("speaker") == "robot"]
        if len(recent_intents) >= 2 and all(intent == "unknown" for intent in recent_intents):
            return True
        
        return False
    
    def _get_transfer_reason(self, context: dict, intent_result: dict) -> str:
        """获取转人工的原因"""
        intent = intent_result.get("intent", "unknown")
        
        if intent == "request_human":
            return "user_request"
        elif intent == "complaint":
            return "complaint"
        elif intent == "price_inquiry":
            return "price_inquiry"
        elif context.get("transfer_score", 0) >= 5:
            return "high_transfer_score"
        elif len(context.get("conversation_history", [])) >= 10:
            return "conversation_too_long"
        else:
            return "unknown_intent"
    
    def cleanup_context(self, call_uuid: str):
        """清理对话上下文"""
        if call_uuid in self.conversation_contexts:
            del self.conversation_contexts[call_uuid]


class IntentClassifier:
    """意图分类器"""
    
    def __init__(self):
        # 简单的关键词匹配，实际项目中可以使用更复杂的NLP模型
        self.intent_keywords = {
            "greeting": ["你好", "您好", "hello", "hi", "喂"],
            "product_inquiry": ["产品", "功能", "特点", "介绍", "了解", "什么"],
            "price_inquiry": ["价格", "多少钱", "费用", "收费", "优惠", "折扣"],
            "complaint": ["投诉", "问题", "不满", "差", "糟糕", "不好"],
            "request_human": ["人工", "客服", "转接", "真人", "人工客服"],
            "negative_response": ["不要", "不需要", "没兴趣", "不感兴趣", "算了", "不用了"],
            "positive_response": ["好的", "可以", "感兴趣", "想了解", "不错", "挺好"]
        }
    
    async def classify(self, text: str, context: dict = None) -> dict:
        """分类意图"""
        text_lower = text.lower()
        
        # 计算每个意图的匹配分数
        intent_scores = {}
        for intent, keywords in self.intent_keywords.items():
            score = 0
            for keyword in keywords:
                if keyword in text_lower:
                    score += 1
            intent_scores[intent] = score
        
        # 找到最高分的意图
        best_intent = max(intent_scores, key=intent_scores.get)
        best_score = intent_scores[best_intent]
        
        # 如果没有匹配的关键词，返回unknown
        if best_score == 0:
            best_intent = "unknown"
        
        # 计算转人工分数
        transfer_score = 0
        if best_intent in ["complaint", "request_human"]:
            transfer_score = 5  # 直接转人工
        elif best_intent == "negative_response":
            transfer_score = 2
        elif best_intent == "unknown":
            transfer_score = 1
        
        return {
            "intent": best_intent,
            "confidence": best_score / len(self.intent_keywords.get(best_intent, [])) if best_score > 0 else 0,
            "transfer_score": transfer_score,
            "entities": self._extract_entities(text, best_intent)
        }
    
    def _extract_entities(self, text: str, intent: str) -> dict:
        """提取实体"""
        entities = {}
        
        # 简单的实体提取
        if intent == "product_inquiry":
            # 提取可能的产品名称
            product_keywords = ["手机", "电脑", "软件", "服务", "系统", "平台"]
            for keyword in product_keywords:
                if keyword in text:
                    entities["product"] = keyword
                    break
        
        return entities