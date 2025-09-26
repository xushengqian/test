from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from sqlalchemy import func, and_
from typing import List, Optional
from datetime import datetime, timedelta

from app.models.database import get_db, CallRecord, CallTask, Customer, Agent
from app.services.call_manager import CallManager
from app.services.agent_manager import AgentManager
from app.services.websocket_manager import WebSocketManager

router = APIRouter()

# 全局服务实例
call_manager = CallManager()
agent_manager = AgentManager()
websocket_manager = WebSocketManager()

@router.get("/dashboard")
async def get_dashboard_data(db: Session = Depends(get_db)):
    """获取仪表板数据"""
    try:
        # 今日统计
        today = datetime.utcnow().date()
        today_start = datetime.combine(today, datetime.min.time())
        today_end = datetime.combine(today, datetime.max.time())
        
        # 通话统计
        total_calls_today = db.query(CallRecord).filter(
            and_(CallRecord.start_time >= today_start, CallRecord.start_time <= today_end)
        ).count()
        
        answered_calls_today = db.query(CallRecord).filter(
            and_(
                CallRecord.start_time >= today_start,
                CallRecord.start_time <= today_end,
                CallRecord.status == "answered"
            )
        ).count()
        
        transferred_calls_today = db.query(CallRecord).filter(
            and_(
                CallRecord.start_time >= today_start,
                CallRecord.start_time <= today_end,
                CallRecord.human_transfer == True
            )
        ).count()
        
        # 平均通话时长
        avg_duration = db.query(func.avg(CallRecord.duration)).filter(
            and_(
                CallRecord.start_time >= today_start,
                CallRecord.start_time <= today_end,
                CallRecord.duration > 0
            )
        ).scalar() or 0
        
        # 实时数据
        active_calls = await call_manager.get_active_calls_count()
        available_agents = await agent_manager.get_available_agents_count()
        queue_length = await agent_manager.get_queue_length()
        
        # 系统负载
        system_load = await call_manager.get_system_load()
        
        return {
            "success": True,
            "data": {
                "today_stats": {
                    "total_calls": total_calls_today,
                    "answered_calls": answered_calls_today,
                    "transferred_calls": transferred_calls_today,
                    "answer_rate": (answered_calls_today / total_calls_today * 100) if total_calls_today > 0 else 0,
                    "transfer_rate": (transferred_calls_today / answered_calls_today * 100) if answered_calls_today > 0 else 0,
                    "avg_duration": int(avg_duration)
                },
                "realtime": {
                    "active_calls": active_calls,
                    "available_agents": available_agents,
                    "queue_length": queue_length,
                    "system_load": system_load
                }
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/calls/realtime")
async def get_realtime_calls():
    """获取实时通话数据"""
    try:
        active_calls = await call_manager.get_active_calls()
        return {"success": True, "calls": active_calls}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/agents/status")
async def get_agents_status():
    """获取坐席状态"""
    try:
        stats = await agent_manager.get_agent_statistics()
        return {"success": True, "status": stats}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/statistics/hourly")
async def get_hourly_statistics(
    date: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """获取小时统计数据"""
    try:
        if date:
            target_date = datetime.strptime(date, "%Y-%m-%d").date()
        else:
            target_date = datetime.utcnow().date()
        
        start_time = datetime.combine(target_date, datetime.min.time())
        end_time = datetime.combine(target_date, datetime.max.time())
        
        # 按小时统计通话数量
        hourly_stats = []
        for hour in range(24):
            hour_start = start_time + timedelta(hours=hour)
            hour_end = start_time + timedelta(hours=hour+1)
            
            total_calls = db.query(CallRecord).filter(
                and_(
                    CallRecord.start_time >= hour_start,
                    CallRecord.start_time < hour_end
                )
            ).count()
            
            answered_calls = db.query(CallRecord).filter(
                and_(
                    CallRecord.start_time >= hour_start,
                    CallRecord.start_time < hour_end,
                    CallRecord.status == "answered"
                )
            ).count()
            
            transferred_calls = db.query(CallRecord).filter(
                and_(
                    CallRecord.start_time >= hour_start,
                    CallRecord.start_time < hour_end,
                    CallRecord.human_transfer == True
                )
            ).count()
            
            hourly_stats.append({
                "hour": hour,
                "total_calls": total_calls,
                "answered_calls": answered_calls,
                "transferred_calls": transferred_calls
            })
        
        return {"success": True, "statistics": hourly_stats}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/statistics/daily")
async def get_daily_statistics(
    days: int = 7,
    db: Session = Depends(get_db)
):
    """获取每日统计数据"""
    try:
        end_date = datetime.utcnow().date()
        start_date = end_date - timedelta(days=days-1)
        
        daily_stats = []
        for i in range(days):
            current_date = start_date + timedelta(days=i)
            day_start = datetime.combine(current_date, datetime.min.time())
            day_end = datetime.combine(current_date, datetime.max.time())
            
            total_calls = db.query(CallRecord).filter(
                and_(
                    CallRecord.start_time >= day_start,
                    CallRecord.start_time <= day_end
                )
            ).count()
            
            answered_calls = db.query(CallRecord).filter(
                and_(
                    CallRecord.start_time >= day_start,
                    CallRecord.start_time <= day_end,
                    CallRecord.status == "answered"
                )
            ).count()
            
            transferred_calls = db.query(CallRecord).filter(
                and_(
                    CallRecord.start_time >= day_start,
                    CallRecord.start_time <= day_end,
                    CallRecord.human_transfer == True
                )
            ).count()
            
            avg_duration = db.query(func.avg(CallRecord.duration)).filter(
                and_(
                    CallRecord.start_time >= day_start,
                    CallRecord.start_time <= day_end,
                    CallRecord.duration > 0
                )
            ).scalar() or 0
            
            daily_stats.append({
                "date": current_date.isoformat(),
                "total_calls": total_calls,
                "answered_calls": answered_calls,
                "transferred_calls": transferred_calls,
                "avg_duration": int(avg_duration)
            })
        
        return {"success": True, "statistics": daily_stats}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/tasks")
async def get_call_tasks(
    status: Optional[str] = None,
    limit: int = 50,
    offset: int = 0,
    db: Session = Depends(get_db)
):
    """获取外呼任务列表"""
    try:
        query = db.query(CallTask)
        
        if status:
            query = query.filter(CallTask.status == status)
        
        tasks = query.order_by(CallTask.created_at.desc()).offset(offset).limit(limit).all()
        
        result = []
        for task in tasks:
            result.append({
                "id": task.id,
                "task_name": task.task_name,
                "phone": task.phone,
                "customer_name": task.customer.name if task.customer else None,
                "priority": task.priority,
                "status": task.status,
                "scheduled_time": task.scheduled_time,
                "current_attempts": task.current_attempts,
                "max_attempts": task.max_attempts,
                "created_at": task.created_at
            })
        
        return {"success": True, "tasks": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/tasks")
async def create_call_task(
    task_name: str,
    phone: str,
    priority: int = 1,
    scheduled_time: Optional[datetime] = None,
    max_attempts: int = 3,
    robot_script: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """创建外呼任务"""
    try:
        # 查找或创建客户
        customer = db.query(Customer).filter(Customer.phone == phone).first()
        if not customer:
            customer = Customer(phone=phone)
            db.add(customer)
            db.flush()
        
        # 创建任务
        task = CallTask(
            task_name=task_name,
            customer_id=customer.id,
            phone=phone,
            priority=priority,
            scheduled_time=scheduled_time,
            max_attempts=max_attempts,
            robot_script=robot_script
        )
        
        db.add(task)
        db.commit()
        db.refresh(task)
        
        return {
            "success": True,
            "task": {
                "id": task.id,
                "task_name": task.task_name,
                "phone": task.phone,
                "status": task.status
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/tasks/{task_id}/execute")
async def execute_call_task(
    task_id: int,
    db: Session = Depends(get_db)
):
    """执行外呼任务"""
    try:
        task = db.query(CallTask).filter(CallTask.id == task_id).first()
        if not task:
            raise HTTPException(status_code=404, detail="Task not found")
        
        if task.status != "pending":
            raise HTTPException(status_code=400, detail="Task is not in pending status")
        
        # 更新任务状态
        task.status = "calling"
        task.current_attempts += 1
        db.commit()
        
        # 发起外呼
        result = await call_manager.initiate_outbound_call(
            task_id, task.phone, task.robot_script
        )
        
        if result["success"]:
            return {"success": True, "call_uuid": result["call_uuid"]}
        else:
            # 恢复任务状态
            task.status = "pending"
            task.current_attempts -= 1
            db.commit()
            raise HTTPException(status_code=400, detail=result["error"])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/websocket/connections")
async def get_websocket_connections():
    """获取WebSocket连接状态"""
    try:
        stats = websocket_manager.get_connection_stats()
        clients = websocket_manager.get_connected_clients()
        
        return {
            "success": True,
            "connections": {
                "statistics": stats,
                "clients": clients
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/broadcast")
async def broadcast_message(
    client_type: Optional[str] = None,
    message: dict = None
):
    """广播消息"""
    try:
        if not message:
            raise HTTPException(status_code=400, detail="Message is required")
        
        if client_type:
            await websocket_manager.broadcast_to_type(client_type, message)
        else:
            await websocket_manager.broadcast_to_all(message)
        
        return {"success": True, "message": "Message broadcasted"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))