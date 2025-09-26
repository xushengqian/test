from fastapi import APIRouter, HTTPException
from typing import Optional, Dict, Any
from datetime import datetime

from app.services.robot_service import RobotService

router = APIRouter()

# 全局服务实例
robot_service = RobotService()

@router.post("/chat")
async def robot_chat(
    user_input: str,
    customer_phone: str,
    call_uuid: str,
    context: Optional[Dict[str, Any]] = None
):
    """机器人对话处理"""
    try:
        response = await robot_service.process_chat(
            user_input, customer_phone, call_uuid, context
        )
        return response
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/context/{call_uuid}")
async def get_conversation_context(call_uuid: str):
    """获取对话上下文"""
    try:
        context = robot_service.conversation_contexts.get(call_uuid)
        if context:
            return {"success": True, "context": context}
        else:
            return {"success": False, "error": "Context not found"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.delete("/context/{call_uuid}")
async def clear_conversation_context(call_uuid: str):
    """清理对话上下文"""
    try:
        robot_service.cleanup_context(call_uuid)
        return {"success": True, "message": "Context cleared"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/intent/classify")
async def classify_intent(
    text: str,
    context: Optional[Dict[str, Any]] = None
):
    """意图分类"""
    try:
        result = await robot_service.intent_classifier.classify(text, context)
        return {"success": True, "result": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/statistics")
async def get_robot_statistics():
    """获取机器人统计信息"""
    try:
        active_contexts = len(robot_service.conversation_contexts)
        
        # 统计各种意图的分布
        intent_stats = {}
        for context in robot_service.conversation_contexts.values():
            intent = context.get("customer_intent", "unknown")
            intent_stats[intent] = intent_stats.get(intent, 0) + 1
        
        # 统计转人工的原因
        transfer_reasons = {}
        for context in robot_service.conversation_contexts.values():
            history = context.get("conversation_history", [])
            for item in history:
                if item.get("speaker") == "robot" and "transfer_reason" in item:
                    reason = item["transfer_reason"]
                    transfer_reasons[reason] = transfer_reasons.get(reason, 0) + 1
        
        return {
            "success": True,
            "statistics": {
                "active_conversations": active_contexts,
                "intent_distribution": intent_stats,
                "transfer_reasons": transfer_reasons
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/test/conversation")
async def test_conversation(
    messages: list,
    customer_phone: str = "test_phone"
):
    """测试对话流程"""
    try:
        call_uuid = f"test_{datetime.now().timestamp()}"
        responses = []
        
        for message in messages:
            response = await robot_service.process_chat(
                message, customer_phone, call_uuid
            )
            responses.append({
                "user_input": message,
                "robot_response": response
            })
            
            # 如果需要转人工，停止测试
            if response.get("action") == "transfer_human":
                break
        
        # 清理测试上下文
        robot_service.cleanup_context(call_uuid)
        
        return {"success": True, "conversation": responses}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/config")
async def get_robot_config():
    """获取机器人配置"""
    try:
        config = {
            "intent_keywords": robot_service.intent_classifier.intent_keywords,
            "transfer_thresholds": {
                "max_transfer_score": 5,
                "max_conversation_rounds": 10,
                "unknown_intent_threshold": 2
            }
        }
        return {"success": True, "config": config}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/config/update")
async def update_robot_config(
    intent_keywords: Optional[Dict[str, list]] = None,
    transfer_thresholds: Optional[Dict[str, int]] = None
):
    """更新机器人配置"""
    try:
        updated = False
        
        if intent_keywords:
            robot_service.intent_classifier.intent_keywords.update(intent_keywords)
            updated = True
        
        # 这里可以添加更多配置更新逻辑
        
        if updated:
            return {"success": True, "message": "Configuration updated"}
        else:
            return {"success": False, "error": "No configuration provided"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))