from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List, Optional
from datetime import datetime

from app.models.database import get_db, Agent
from app.services.agent_manager import AgentManager

router = APIRouter()

# 全局服务实例
agent_manager = AgentManager()

@router.post("/register")
async def register_agent(
    agent_id: str,
    websocket_id: str,
    skills: Optional[List[str]] = None
):
    """坐席上线注册"""
    try:
        result = await agent_manager.register_agent(agent_id, websocket_id, skills)
        if result["success"]:
            return result
        else:
            raise HTTPException(status_code=400, detail=result["error"])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/unregister")
async def unregister_agent(agent_id: str):
    """坐席下线注销"""
    try:
        result = await agent_manager.unregister_agent(agent_id)
        if result["success"]:
            return result
        else:
            raise HTTPException(status_code=400, detail=result["error"])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/find")
async def find_available_agent(
    customer_phone: str,
    call_uuid: str,
    transfer_reason: str = "unknown",
    required_skills: Optional[List[str]] = None
):
    """查找可用坐席"""
    try:
        result = await agent_manager.find_available_agent(
            customer_phone, call_uuid, transfer_reason, required_skills
        )
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/notify")
async def notify_agent(
    agent_id: str,
    customer_phone: str,
    call_uuid: str,
    transfer_reason: str,
    customer_info: Optional[dict] = None
):
    """通知坐席有新通话"""
    try:
        result = await agent_manager.notify_agent(
            agent_id, customer_phone, call_uuid, transfer_reason, customer_info
        )
        if result["success"]:
            return result
        else:
            raise HTTPException(status_code=400, detail=result["error"])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/accept")
async def accept_call(
    agent_id: str,
    call_uuid: str
):
    """坐席接受通话"""
    try:
        result = await agent_manager.accept_call(agent_id, call_uuid)
        if result["success"]:
            return result
        else:
            raise HTTPException(status_code=400, detail=result["error"])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/reject")
async def reject_call(
    agent_id: str,
    call_uuid: str,
    reason: str = "busy"
):
    """坐席拒绝通话"""
    try:
        result = await agent_manager.reject_call(agent_id, call_uuid, reason)
        if result["success"]:
            return result
        else:
            raise HTTPException(status_code=400, detail=result["error"])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/end")
async def end_call(
    agent_id: str,
    call_uuid: str
):
    """结束通话"""
    try:
        result = await agent_manager.end_call(agent_id, call_uuid)
        if result["success"]:
            return result
        else:
            raise HTTPException(status_code=400, detail=result["error"])
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/list")
async def get_agents(
    status: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """获取坐席列表"""
    try:
        query = db.query(Agent)
        
        if status:
            query = query.filter(Agent.status == status)
        
        agents = query.all()
        
        result = []
        for agent in agents:
            result.append({
                "id": agent.id,
                "agent_id": agent.agent_id,
                "name": agent.name,
                "extension": agent.extension,
                "email": agent.email,
                "department": agent.department,
                "status": agent.status,
                "max_concurrent_calls": agent.max_concurrent_calls,
                "skills": agent.skills,
                "created_at": agent.created_at,
                "updated_at": agent.updated_at
            })
        
        return {"success": True, "agents": result}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/statistics")
async def get_agent_statistics():
    """获取坐席统计信息"""
    try:
        stats = await agent_manager.get_agent_statistics()
        return {"success": True, "statistics": stats}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/queue")
async def get_queue_info():
    """获取队列信息"""
    try:
        queue_length = await agent_manager.get_queue_length()
        available_agents = await agent_manager.get_available_agents_count()
        
        return {
            "success": True,
            "queue": {
                "length": queue_length,
                "available_agents": available_agents
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/create")
async def create_agent(
    agent_id: str,
    name: str,
    extension: Optional[str] = None,
    email: Optional[str] = None,
    department: Optional[str] = None,
    max_concurrent_calls: int = 1,
    skills: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """创建坐席"""
    try:
        # 检查坐席ID是否已存在
        existing_agent = db.query(Agent).filter(Agent.agent_id == agent_id).first()
        if existing_agent:
            raise HTTPException(status_code=400, detail="Agent ID already exists")
        
        # 创建新坐席
        agent = Agent(
            agent_id=agent_id,
            name=name,
            extension=extension,
            email=email,
            department=department,
            max_concurrent_calls=max_concurrent_calls,
            skills=skills,
            status="offline"
        )
        
        db.add(agent)
        db.commit()
        db.refresh(agent)
        
        return {
            "success": True,
            "agent": {
                "id": agent.id,
                "agent_id": agent.agent_id,
                "name": agent.name,
                "status": agent.status
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.put("/{agent_id}")
async def update_agent(
    agent_id: str,
    name: Optional[str] = None,
    extension: Optional[str] = None,
    email: Optional[str] = None,
    department: Optional[str] = None,
    max_concurrent_calls: Optional[int] = None,
    skills: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """更新坐席信息"""
    try:
        agent = db.query(Agent).filter(Agent.agent_id == agent_id).first()
        if not agent:
            raise HTTPException(status_code=404, detail="Agent not found")
        
        # 更新字段
        if name is not None:
            agent.name = name
        if extension is not None:
            agent.extension = extension
        if email is not None:
            agent.email = email
        if department is not None:
            agent.department = department
        if max_concurrent_calls is not None:
            agent.max_concurrent_calls = max_concurrent_calls
        if skills is not None:
            agent.skills = skills
        
        agent.updated_at = datetime.utcnow()
        db.commit()
        
        return {"success": True, "message": "Agent updated"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.delete("/{agent_id}")
async def delete_agent(
    agent_id: str,
    db: Session = Depends(get_db)
):
    """删除坐席"""
    try:
        agent = db.query(Agent).filter(Agent.agent_id == agent_id).first()
        if not agent:
            raise HTTPException(status_code=404, detail="Agent not found")
        
        # 检查坐席是否在线
        if agent.status in ["online", "busy"]:
            raise HTTPException(status_code=400, detail="Cannot delete online agent")
        
        db.delete(agent)
        db.commit()
        
        return {"success": True, "message": "Agent deleted"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))