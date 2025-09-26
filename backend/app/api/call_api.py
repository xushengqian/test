from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List, Optional
from datetime import datetime
import json

from app.models.database import get_db, CallRecord, CallTask, Customer
from app.services.call_manager import CallManager
from app.services.agent_manager import AgentManager

router = APIRouter()

# 全局服务实例
call_manager = CallManager()
agent_manager = AgentManager()

@router.post("/start")
async def start_call(
    customer_phone: str,
    call_uuid: str,
    start_time: Optional[datetime] = None,
    db: Session = Depends(get_db)
):
    """通话开始回调"""
    try:
        # 更新通话记录
        call_record = db.query(CallRecord).filter(
            CallRecord.call_uuid == call_uuid
        ).first()
        
        if call_record:
            call_record.start_time = start_time or datetime.utcnow()
            call_record.status = "started"
            db.commit()
            
            return {"success": True, "message": "Call started"}
        else:
            return {"success": False, "error": "Call record not found"}
            
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/answer")
async def call_answered(
    call_uuid: str,
    answer_time: Optional[datetime] = None
):
    """通话接通回调"""
    try:
        await call_manager.handle_call_answered(call_uuid, answer_time)
        return {"success": True, "message": "Call answered"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/hangup")
async def call_hangup(
    call_uuid: str,
    end_time: Optional[datetime] = None,
    reason: Optional[str] = None
):
    """通话挂断回调"""
    try:
        await call_manager.handle_call_hangup(call_uuid, end_time, reason)
        return {"success": True, "message": "Call ended"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/failed")
async def call_failed(
    customer_phone: str,
    reason: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """外呼失败回调"""
    try:
        # 查找对应的通话记录
        call_record = db.query(CallRecord).filter(
            CallRecord.callee_number == customer_phone,
            CallRecord.status.in_(["initiating", "calling"])
        ).order_by(CallRecord.start_time.desc()).first()
        
        if call_record:
            call_record.status = "failed"
            call_record.end_time = datetime.utcnow()
            call_record.notes = reason or "Call failed"
            db.commit()
        
        return {"success": True, "message": "Call failure recorded"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/initiate")
async def initiate_call(
    task_id: int,
    phone: str,
    script: Optional[str] = None
):
    """发起外呼"""
    try:
        result = await call_manager.initiate_outbound_call(task_id, phone, script)
        if result["success"]:
            return {"success": True, "call_uuid": result["call_uuid"]}
        else:
            raise HTTPException(status_code=400, detail=result["error"])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/transfer")
async def transfer_call(
    call_uuid: str,
    reason: str = "user_request"
):
    """转接到人工"""
    try:
        result = await call_manager.transfer_to_human(call_uuid, reason)
        if result["success"]:
            return {"success": True, "message": "Call transferred"}
        else:
            raise HTTPException(status_code=400, detail=result["error"])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/active")
async def get_active_calls():
    """获取活跃通话列表"""
    try:
        calls = await call_manager.get_active_calls()
        return {"success": True, "calls": calls}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/history")
async def get_call_history(
    customer_phone: Optional[str] = None,
    agent_id: Optional[str] = None,
    limit: int = 50,
    offset: int = 0,
    db: Session = Depends(get_db)
):
    """获取通话历史"""
    try:
        query = db.query(CallRecord)
        
        if customer_phone:
            query = query.filter(CallRecord.callee_number == customer_phone)
        
        if agent_id:
            query = query.join(CallRecord.agent).filter(
                CallRecord.agent.has(agent_id=agent_id)
            )
        
        calls = query.order_by(CallRecord.start_time.desc()).offset(offset).limit(limit).all()
        
        result = []
        for call in calls:
            result.append({
                "id": call.id,
                "call_uuid": call.call_uuid,
                "customer_phone": call.callee_number,
                "agent_name": call.agent.name if call.agent else None,
                "status": call.status,
                "start_time": call.start_time,
                "end_time": call.end_time,
                "duration": call.duration,
                "human_transfer": call.human_transfer,
                "transfer_reason": call.transfer_reason,
                "call_result": call.call_result
            })
        
        return {"success": True, "calls": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/{call_uuid}")
async def get_call_details(
    call_uuid: str,
    db: Session = Depends(get_db)
):
    """获取通话详情"""
    try:
        call = db.query(CallRecord).filter(CallRecord.call_uuid == call_uuid).first()
        
        if not call:
            raise HTTPException(status_code=404, detail="Call not found")
        
        # 获取对话记录
        conversations = []
        for conv in call.conversations:
            conversations.append({
                "sequence": conv.sequence,
                "speaker": conv.speaker,
                "content": conv.content,
                "confidence": conv.confidence,
                "intent": conv.intent,
                "timestamp": conv.timestamp
            })
        
        result = {
            "id": call.id,
            "call_uuid": call.call_uuid,
            "customer_phone": call.callee_number,
            "customer_name": call.customer.name if call.customer else None,
            "agent_name": call.agent.name if call.agent else None,
            "status": call.status,
            "start_time": call.start_time,
            "answer_time": call.answer_time,
            "end_time": call.end_time,
            "duration": call.duration,
            "robot_duration": call.robot_duration,
            "human_transfer": call.human_transfer,
            "transfer_reason": call.transfer_reason,
            "transfer_time": call.transfer_time,
            "call_result": call.call_result,
            "customer_intent": call.customer_intent,
            "recording_file": call.recording_file,
            "transcript": call.transcript,
            "notes": call.notes,
            "conversations": conversations
        }
        
        return {"success": True, "call": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/{call_uuid}/recording")
async def update_recording(
    call_uuid: str,
    recording_file: str,
    transcript: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """更新录音文件信息"""
    try:
        call = db.query(CallRecord).filter(CallRecord.call_uuid == call_uuid).first()
        
        if not call:
            raise HTTPException(status_code=404, detail="Call not found")
        
        call.recording_file = recording_file
        if transcript:
            call.transcript = transcript
        
        db.commit()
        
        return {"success": True, "message": "Recording updated"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))