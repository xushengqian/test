import asyncio
import logging
from typing import Dict, List, Optional
from datetime import datetime, timedelta
import json
from sqlalchemy.orm import Session

from app.models.database import Agent, CallRecord, get_db
from app.core.config import settings
from app.services.websocket_manager import WebSocketManager

logger = logging.getLogger(__name__)

class AgentManager:
    """坐席管理器"""
    
    def __init__(self):
        self.websocket_manager = WebSocketManager()
        self.agent_queue: Dict[str, List[dict]] = {}  # 按技能分组的队列
        self.agent_status: Dict[str, dict] = {}  # 坐席状态缓存
        self.call_queue: List[dict] = []  # 等待分配的通话队列
        self.running = False
        
    async def start_monitoring(self):
        """启动监控任务"""
        self.running = True
        
        # 启动后台任务
        asyncio.create_task(self._monitor_agent_status())
        asyncio.create_task(self._process_call_queue())
        asyncio.create_task(self._update_queue_statistics())
        
        # 初始化坐席状态
        await self._load_agent_status()
        
        logger.info("Agent manager monitoring started")
    
    async def register_agent(self, agent_id: str, websocket_id: str, skills: List[str] = None) -> dict:
        """注册坐席上线"""
        try:
            db = next(get_db())
            
            # 查找坐席信息
            agent = db.query(Agent).filter(Agent.agent_id == agent_id).first()
            if not agent:
                return {"success": False, "error": "Agent not found"}
            
            # 更新坐席状态
            agent.status = "online"
            agent.updated_at = datetime.utcnow()
            db.commit()
            
            # 更新内存状态
            self.agent_status[agent_id] = {
                "agent_id": agent_id,
                "name": agent.name,
                "status": "online",
                "websocket_id": websocket_id,
                "skills": skills or [],
                "current_calls": 0,
                "max_calls": agent.max_concurrent_calls,
                "last_activity": datetime.utcnow(),
                "queue_position": None
            }
            
            # 通知其他客户端
            await self.websocket_manager.broadcast_to_type("admin", {
                "type": "agent_online",
                "data": {
                    "agent_id": agent_id,
                    "name": agent.name,
                    "status": "online"
                }
            })
            
            logger.info(f"Agent registered: {agent_id}")
            return {"success": True, "agent_info": self.agent_status[agent_id]}
            
        except Exception as e:
            logger.error(f"Failed to register agent: {e}")
            return {"success": False, "error": str(e)}
        finally:
            db.close()
    
    async def unregister_agent(self, agent_id: str) -> dict:
        """注销坐席下线"""
        try:
            db = next(get_db())
            
            # 更新数据库状态
            agent = db.query(Agent).filter(Agent.agent_id == agent_id).first()
            if agent:
                agent.status = "offline"
                agent.updated_at = datetime.utcnow()
                db.commit()
            
            # 从内存中移除
            if agent_id in self.agent_status:
                agent_info = self.agent_status[agent_id]
                del self.agent_status[agent_id]
                
                # 通知其他客户端
                await self.websocket_manager.broadcast_to_type("admin", {
                    "type": "agent_offline",
                    "data": {
                        "agent_id": agent_id,
                        "name": agent_info.get("name", ""),
                        "status": "offline"
                    }
                })
            
            logger.info(f"Agent unregistered: {agent_id}")
            return {"success": True}
            
        except Exception as e:
            logger.error(f"Failed to unregister agent: {e}")
            return {"success": False, "error": str(e)}
        finally:
            db.close()
    
    async def find_available_agent(self, customer_phone: str, call_uuid: str, 
                                 transfer_reason: str = "unknown", 
                                 required_skills: List[str] = None) -> dict:
        """查找可用坐席"""
        try:
            # 查找匹配技能的可用坐席
            available_agents = []
            
            for agent_id, agent_info in self.agent_status.items():
                if (agent_info["status"] == "online" and 
                    agent_info["current_calls"] < agent_info["max_calls"]):
                    
                    # 检查技能匹配
                    if required_skills:
                        agent_skills = set(agent_info.get("skills", []))
                        required_skills_set = set(required_skills)
                        if not required_skills_set.issubset(agent_skills):
                            continue
                    
                    available_agents.append(agent_info)
            
            if available_agents:
                # 按负载排序，选择最空闲的坐席
                available_agents.sort(key=lambda x: x["current_calls"])
                selected_agent = available_agents[0]
                
                # 分配坐席
                await self._assign_agent_to_call(selected_agent["agent_id"], call_uuid, customer_phone)
                
                return {
                    "status": "available",
                    "agent_id": selected_agent["agent_id"],
                    "agent_name": selected_agent["name"],
                    "extension": selected_agent.get("extension", ""),
                    "estimated_wait_time": 0
                }
            else:
                # 没有可用坐席，加入队列
                queue_position = await self._add_to_queue(call_uuid, customer_phone, transfer_reason, required_skills)
                estimated_wait_time = await self._estimate_wait_time(queue_position)
                
                return {
                    "status": "queue",
                    "queue_position": queue_position,
                    "estimated_wait_time": estimated_wait_time
                }
                
        except Exception as e:
            logger.error(f"Failed to find available agent: {e}")
            return {"status": "error", "error": str(e)}
    
    async def notify_agent(self, agent_id: str, customer_phone: str, call_uuid: str, 
                          transfer_reason: str, customer_info: dict = None) -> dict:
        """通知坐席有新通话"""
        try:
            if agent_id not in self.agent_status:
                return {"success": False, "error": "Agent not found"}
            
            agent_info = self.agent_status[agent_id]
            websocket_id = agent_info.get("websocket_id")
            
            if websocket_id:
                # 通过WebSocket通知坐席
                await self.websocket_manager.send_to_client("agent", websocket_id, {
                    "type": "incoming_call",
                    "data": {
                        "call_uuid": call_uuid,
                        "customer_phone": customer_phone,
                        "transfer_reason": transfer_reason,
                        "customer_info": customer_info or {},
                        "timestamp": datetime.utcnow().isoformat()
                    }
                })
                
                logger.info(f"Notified agent {agent_id} about call {call_uuid}")
                return {"success": True}
            else:
                return {"success": False, "error": "Agent websocket not found"}
                
        except Exception as e:
            logger.error(f"Failed to notify agent: {e}")
            return {"success": False, "error": str(e)}
    
    async def accept_call(self, agent_id: str, call_uuid: str) -> dict:
        """坐席接受通话"""
        try:
            if agent_id not in self.agent_status:
                return {"success": False, "error": "Agent not found"}
            
            # 更新坐席状态
            self.agent_status[agent_id]["current_calls"] += 1
            self.agent_status[agent_id]["status"] = "busy" if self.agent_status[agent_id]["current_calls"] >= self.agent_status[agent_id]["max_calls"] else "online"
            
            # 更新数据库
            db = next(get_db())
            call_record = db.query(CallRecord).filter(CallRecord.call_uuid == call_uuid).first()
            if call_record:
                agent = db.query(Agent).filter(Agent.agent_id == agent_id).first()
                if agent:
                    call_record.agent_id = agent.id
                    db.commit()
            
            # 通知管理员
            await self.websocket_manager.broadcast_to_type("admin", {
                "type": "call_accepted",
                "data": {
                    "agent_id": agent_id,
                    "call_uuid": call_uuid,
                    "timestamp": datetime.utcnow().isoformat()
                }
            })
            
            logger.info(f"Agent {agent_id} accepted call {call_uuid}")
            return {"success": True}
            
        except Exception as e:
            logger.error(f"Failed to accept call: {e}")
            return {"success": False, "error": str(e)}
        finally:
            db.close()
    
    async def reject_call(self, agent_id: str, call_uuid: str, reason: str = "busy") -> dict:
        """坐席拒绝通话"""
        try:
            # 重新分配给其他坐席或加入队列
            await self._reassign_call(call_uuid, excluded_agents=[agent_id])
            
            logger.info(f"Agent {agent_id} rejected call {call_uuid}, reason: {reason}")
            return {"success": True}
            
        except Exception as e:
            logger.error(f"Failed to reject call: {e}")
            return {"success": False, "error": str(e)}
    
    async def end_call(self, agent_id: str, call_uuid: str) -> dict:
        """结束通话"""
        try:
            if agent_id in self.agent_status:
                # 更新坐席状态
                self.agent_status[agent_id]["current_calls"] = max(0, self.agent_status[agent_id]["current_calls"] - 1)
                if self.agent_status[agent_id]["current_calls"] < self.agent_status[agent_id]["max_calls"]:
                    self.agent_status[agent_id]["status"] = "online"
                
                # 检查是否有排队的通话
                await self._process_next_in_queue(agent_id)
            
            logger.info(f"Agent {agent_id} ended call {call_uuid}")
            return {"success": True}
            
        except Exception as e:
            logger.error(f"Failed to end call: {e}")
            return {"success": False, "error": str(e)}
    
    async def get_available_agents_count(self) -> int:
        """获取可用坐席数量"""
        count = 0
        for agent_info in self.agent_status.values():
            if (agent_info["status"] == "online" and 
                agent_info["current_calls"] < agent_info["max_calls"]):
                count += 1
        return count
    
    async def get_queue_length(self) -> int:
        """获取队列长度"""
        return len(self.call_queue)
    
    async def get_agent_statistics(self) -> dict:
        """获取坐席统计信息"""
        total_agents = len(self.agent_status)
        online_agents = sum(1 for agent in self.agent_status.values() if agent["status"] in ["online", "busy"])
        busy_agents = sum(1 for agent in self.agent_status.values() if agent["status"] == "busy")
        
        return {
            "total_agents": total_agents,
            "online_agents": online_agents,
            "available_agents": online_agents - busy_agents,
            "busy_agents": busy_agents,
            "queue_length": len(self.call_queue)
        }
    
    async def _assign_agent_to_call(self, agent_id: str, call_uuid: str, customer_phone: str):
        """分配坐席到通话"""
        # 这里可以添加更复杂的分配逻辑
        pass
    
    async def _add_to_queue(self, call_uuid: str, customer_phone: str, 
                           transfer_reason: str, required_skills: List[str] = None) -> int:
        """添加到队列"""
        queue_item = {
            "call_uuid": call_uuid,
            "customer_phone": customer_phone,
            "transfer_reason": transfer_reason,
            "required_skills": required_skills or [],
            "queue_time": datetime.utcnow(),
            "priority": 1  # 可以根据客户等级设置优先级
        }
        
        self.call_queue.append(queue_item)
        
        # 按优先级和等待时间排序
        self.call_queue.sort(key=lambda x: (-x["priority"], x["queue_time"]))
        
        # 返回队列位置
        for i, item in enumerate(self.call_queue):
            if item["call_uuid"] == call_uuid:
                return i + 1
        
        return len(self.call_queue)
    
    async def _estimate_wait_time(self, queue_position: int) -> int:
        """估算等待时间"""
        # 简单的等待时间估算，可以根据历史数据优化
        average_call_duration = 180  # 3分钟
        available_agents = await self.get_available_agents_count()
        
        if available_agents > 0:
            return max(30, (queue_position * average_call_duration) // available_agents)
        else:
            return queue_position * 60  # 如果没有可用坐席，按1分钟/人估算
    
    async def _reassign_call(self, call_uuid: str, excluded_agents: List[str] = None):
        """重新分配通话"""
        # 从队列中查找并重新分配
        for item in self.call_queue:
            if item["call_uuid"] == call_uuid:
                # 重新查找坐席
                result = await self.find_available_agent(
                    item["customer_phone"],
                    call_uuid,
                    item["transfer_reason"],
                    item["required_skills"]
                )
                break
    
    async def _process_next_in_queue(self, agent_id: str):
        """处理队列中的下一个通话"""
        if not self.call_queue:
            return
        
        agent_info = self.agent_status.get(agent_id)
        if not agent_info or agent_info["current_calls"] >= agent_info["max_calls"]:
            return
        
        # 查找匹配的队列项
        for i, queue_item in enumerate(self.call_queue):
            # 检查技能匹配
            required_skills = set(queue_item.get("required_skills", []))
            agent_skills = set(agent_info.get("skills", []))
            
            if required_skills.issubset(agent_skills):
                # 分配给坐席
                await self._assign_agent_to_call(
                    agent_id,
                    queue_item["call_uuid"],
                    queue_item["customer_phone"]
                )
                
                # 从队列中移除
                self.call_queue.pop(i)
                break
    
    async def _load_agent_status(self):
        """加载坐席状态"""
        try:
            db = next(get_db())
            agents = db.query(Agent).filter(Agent.status.in_(["online", "busy"])).all()
            
            for agent in agents:
                self.agent_status[agent.agent_id] = {
                    "agent_id": agent.agent_id,
                    "name": agent.name,
                    "status": "offline",  # 重启后默认离线
                    "websocket_id": None,
                    "skills": json.loads(agent.skills) if agent.skills else [],
                    "current_calls": 0,
                    "max_calls": agent.max_concurrent_calls,
                    "last_activity": datetime.utcnow()
                }
                
                # 更新数据库状态为离线
                agent.status = "offline"
            
            db.commit()
            
        except Exception as e:
            logger.error(f"Failed to load agent status: {e}")
        finally:
            db.close()
    
    async def _monitor_agent_status(self):
        """监控坐席状态"""
        while self.running:
            try:
                current_time = datetime.utcnow()
                inactive_agents = []
                
                # 检查不活跃的坐席
                for agent_id, agent_info in self.agent_status.items():
                    last_activity = agent_info.get("last_activity")
                    if last_activity and (current_time - last_activity).total_seconds() > 300:  # 5分钟无活动
                        inactive_agents.append(agent_id)
                
                # 处理不活跃坐席
                for agent_id in inactive_agents:
                    await self.unregister_agent(agent_id)
                
                await asyncio.sleep(60)  # 每分钟检查一次
                
            except Exception as e:
                logger.error(f"Error monitoring agent status: {e}")
                await asyncio.sleep(60)
    
    async def _process_call_queue(self):
        """处理通话队列"""
        while self.running:
            try:
                # 检查超时的队列项
                current_time = datetime.utcnow()
                timeout_items = []
                
                for i, queue_item in enumerate(self.call_queue):
                    queue_time = queue_item.get("queue_time")
                    if queue_time and (current_time - queue_time).total_seconds() > settings.QUEUE_TIMEOUT:
                        timeout_items.append(i)
                
                # 移除超时项（从后往前删除以避免索引问题）
                for i in reversed(timeout_items):
                    removed_item = self.call_queue.pop(i)
                    logger.warning(f"Queue item timeout: {removed_item['call_uuid']}")
                
                await asyncio.sleep(30)  # 每30秒检查一次
                
            except Exception as e:
                logger.error(f"Error processing call queue: {e}")
                await asyncio.sleep(30)
    
    async def _update_queue_statistics(self):
        """更新队列统计"""
        while self.running:
            try:
                # 广播队列统计信息
                stats = await self.get_agent_statistics()
                await self.websocket_manager.broadcast_to_type("admin", {
                    "type": "queue_statistics",
                    "data": stats
                })
                
                await asyncio.sleep(10)  # 每10秒更新一次
                
            except Exception as e:
                logger.error(f"Error updating queue statistics: {e}")
                await asyncio.sleep(10)
    
    async def cleanup(self):
        """清理资源"""
        self.running = False
        logger.info("Agent manager cleaned up")