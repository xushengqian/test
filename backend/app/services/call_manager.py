import asyncio
import logging
from typing import Dict, List, Optional
from datetime import datetime, timedelta
import json
import httpx
from sqlalchemy.orm import Session

from app.models.database import CallRecord, CallTask, Customer, get_db
from app.core.config import settings
from app.services.freeswitch_client import FreeSwitchClient
from app.services.websocket_manager import WebSocketManager

logger = logging.getLogger(__name__)

class CallManager:
    """通话管理器"""
    
    def __init__(self):
        self.fs_client = FreeSwitchClient()
        self.websocket_manager = WebSocketManager()
        self.active_calls: Dict[str, dict] = {}
        self.call_queue = asyncio.Queue()
        self.running = False
        
    async def start_background_tasks(self):
        """启动后台任务"""
        self.running = True
        
        # 启动通话处理任务
        asyncio.create_task(self._process_call_queue())
        asyncio.create_task(self._monitor_active_calls())
        asyncio.create_task(self._cleanup_expired_calls())
        
        logger.info("Call manager background tasks started")
    
    async def initiate_outbound_call(self, task_id: int, phone: str, script: str = None) -> dict:
        """发起外呼"""
        try:
            db = next(get_db())
            
            # 检查并发限制
            if len(self.active_calls) >= settings.MAX_CONCURRENT_CALLS:
                return {"success": False, "error": "Maximum concurrent calls reached"}
            
            # 创建通话记录
            call_record = CallRecord(
                call_uuid=f"robot_call_{task_id}_{int(datetime.now().timestamp())}",
                task_id=task_id,
                callee_number=phone,
                direction="outbound",
                status="initiating",
                start_time=datetime.utcnow()
            )
            
            # 查找客户信息
            customer = db.query(Customer).filter(Customer.phone == phone).first()
            if customer:
                call_record.customer_id = customer.id
            
            db.add(call_record)
            db.commit()
            
            # 通过FreeSwitch发起呼叫
            fs_result = await self.fs_client.originate_call(
                destination=f"robot_call_{phone}",
                caller_id=settings.OUTBOUND_CALLER_ID,
                variables={
                    "customer_phone": phone,
                    "task_id": task_id,
                    "call_uuid": call_record.call_uuid,
                    "robot_script": script or ""
                }
            )
            
            if fs_result.get("success"):
                # 添加到活跃通话列表
                self.active_calls[call_record.call_uuid] = {
                    "call_id": call_record.id,
                    "task_id": task_id,
                    "phone": phone,
                    "status": "calling",
                    "start_time": datetime.utcnow(),
                    "fs_uuid": fs_result.get("uuid")
                }
                
                # 更新数据库状态
                call_record.status = "calling"
                db.commit()
                
                # 通知WebSocket客户端
                await self.websocket_manager.broadcast_to_type("admin", {
                    "type": "call_initiated",
                    "data": {
                        "call_uuid": call_record.call_uuid,
                        "phone": phone,
                        "status": "calling"
                    }
                })
                
                logger.info(f"Outbound call initiated: {phone}")
                return {"success": True, "call_uuid": call_record.call_uuid}
            else:
                call_record.status = "failed"
                call_record.end_time = datetime.utcnow()
                db.commit()
                return {"success": False, "error": fs_result.get("error")}
                
        except Exception as e:
            logger.error(f"Failed to initiate outbound call: {e}")
            return {"success": False, "error": str(e)}
        finally:
            db.close()
    
    async def handle_call_answered(self, call_uuid: str, answer_time: datetime = None):
        """处理通话接通"""
        try:
            db = next(get_db())
            
            call_record = db.query(CallRecord).filter(
                CallRecord.call_uuid == call_uuid
            ).first()
            
            if call_record:
                call_record.status = "answered"
                call_record.answer_time = answer_time or datetime.utcnow()
                db.commit()
                
                # 更新活跃通话状态
                if call_uuid in self.active_calls:
                    self.active_calls[call_uuid]["status"] = "answered"
                    self.active_calls[call_uuid]["answer_time"] = call_record.answer_time
                
                # 通知WebSocket客户端
                await self.websocket_manager.broadcast_to_type("admin", {
                    "type": "call_answered",
                    "data": {
                        "call_uuid": call_uuid,
                        "answer_time": call_record.answer_time.isoformat()
                    }
                })
                
                logger.info(f"Call answered: {call_uuid}")
                
        except Exception as e:
            logger.error(f"Failed to handle call answered: {e}")
        finally:
            db.close()
    
    async def handle_call_hangup(self, call_uuid: str, end_time: datetime = None, reason: str = None):
        """处理通话挂断"""
        try:
            db = next(get_db())
            
            call_record = db.query(CallRecord).filter(
                CallRecord.call_uuid == call_uuid
            ).first()
            
            if call_record:
                call_record.status = "hangup"
                call_record.end_time = end_time or datetime.utcnow()
                
                # 计算通话时长
                if call_record.answer_time:
                    duration = (call_record.end_time - call_record.answer_time).total_seconds()
                    call_record.duration = int(duration)
                
                if reason:
                    call_record.notes = reason
                
                db.commit()
                
                # 从活跃通话列表移除
                if call_uuid in self.active_calls:
                    del self.active_calls[call_uuid]
                
                # 通知WebSocket客户端
                await self.websocket_manager.broadcast_to_type("admin", {
                    "type": "call_ended",
                    "data": {
                        "call_uuid": call_uuid,
                        "duration": call_record.duration,
                        "end_time": call_record.end_time.isoformat()
                    }
                })
                
                logger.info(f"Call ended: {call_uuid}, duration: {call_record.duration}s")
                
        except Exception as e:
            logger.error(f"Failed to handle call hangup: {e}")
        finally:
            db.close()
    
    async def transfer_to_human(self, call_uuid: str, reason: str = "user_request") -> dict:
        """转接到人工"""
        try:
            db = next(get_db())
            
            call_record = db.query(CallRecord).filter(
                CallRecord.call_uuid == call_uuid
            ).first()
            
            if not call_record:
                return {"success": False, "error": "Call not found"}
            
            # 更新转人工标记
            call_record.human_transfer = True
            call_record.transfer_reason = reason
            call_record.transfer_time = datetime.utcnow()
            db.commit()
            
            # 通过FreeSwitch执行转接
            fs_result = await self.fs_client.transfer_call(
                call_uuid,
                f"transfer_human_{call_record.callee_number}"
            )
            
            if fs_result.get("success"):
                # 通知WebSocket客户端
                await self.websocket_manager.broadcast_to_type("admin", {
                    "type": "call_transferred",
                    "data": {
                        "call_uuid": call_uuid,
                        "reason": reason,
                        "transfer_time": call_record.transfer_time.isoformat()
                    }
                })
                
                logger.info(f"Call transferred to human: {call_uuid}, reason: {reason}")
                return {"success": True}
            else:
                return {"success": False, "error": fs_result.get("error")}
                
        except Exception as e:
            logger.error(f"Failed to transfer call to human: {e}")
            return {"success": False, "error": str(e)}
        finally:
            db.close()
    
    async def get_active_calls(self) -> List[dict]:
        """获取活跃通话列表"""
        return list(self.active_calls.values())
    
    async def get_active_calls_count(self) -> int:
        """获取活跃通话数量"""
        return len(self.active_calls)
    
    async def get_system_load(self) -> dict:
        """获取系统负载"""
        return {
            "active_calls": len(self.active_calls),
            "max_calls": settings.MAX_CONCURRENT_CALLS,
            "load_percentage": (len(self.active_calls) / settings.MAX_CONCURRENT_CALLS) * 100
        }
    
    async def _process_call_queue(self):
        """处理通话队列"""
        while self.running:
            try:
                # 这里可以添加队列处理逻辑
                await asyncio.sleep(1)
            except Exception as e:
                logger.error(f"Error processing call queue: {e}")
                await asyncio.sleep(5)
    
    async def _monitor_active_calls(self):
        """监控活跃通话"""
        while self.running:
            try:
                # 检查超时的通话
                current_time = datetime.utcnow()
                timeout_calls = []
                
                for call_uuid, call_info in self.active_calls.items():
                    start_time = call_info.get("start_time")
                    if start_time and (current_time - start_time).total_seconds() > settings.CALL_TIMEOUT:
                        timeout_calls.append(call_uuid)
                
                # 处理超时通话
                for call_uuid in timeout_calls:
                    await self.handle_call_hangup(call_uuid, reason="timeout")
                
                await asyncio.sleep(10)  # 每10秒检查一次
                
            except Exception as e:
                logger.error(f"Error monitoring active calls: {e}")
                await asyncio.sleep(10)
    
    async def _cleanup_expired_calls(self):
        """清理过期通话记录"""
        while self.running:
            try:
                # 每小时清理一次过期记录
                await asyncio.sleep(3600)
                
                db = next(get_db())
                
                # 清理7天前的通话记录（可配置）
                expire_time = datetime.utcnow() - timedelta(days=7)
                expired_calls = db.query(CallRecord).filter(
                    CallRecord.end_time < expire_time
                ).all()
                
                for call in expired_calls:
                    # 这里可以添加归档逻辑，而不是直接删除
                    pass
                
                db.close()
                
            except Exception as e:
                logger.error(f"Error cleaning up expired calls: {e}")
    
    async def cleanup(self):
        """清理资源"""
        self.running = False
        await self.fs_client.disconnect()
        logger.info("Call manager cleaned up")