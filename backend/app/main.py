from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Depends, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
import uvicorn
import asyncio
import logging
from contextlib import asynccontextmanager

from app.api import router as api_router
from app.services.call_manager import CallManager
from app.services.agent_manager import AgentManager
from app.services.robot_service import RobotService
from app.services.websocket_manager import WebSocketManager
from app.models.database import init_db
from app.core.config import settings

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 全局服务实例
call_manager = CallManager()
agent_manager = AgentManager()
robot_service = RobotService()
websocket_manager = WebSocketManager()

@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    # 启动时初始化
    logger.info("Starting FS Robot Call System...")
    
    # 初始化数据库
    await init_db()
    
    # 启动后台任务
    asyncio.create_task(call_manager.start_background_tasks())
    asyncio.create_task(agent_manager.start_monitoring())
    
    logger.info("System started successfully")
    
    yield
    
    # 关闭时清理
    logger.info("Shutting down system...")
    await call_manager.cleanup()
    await agent_manager.cleanup()

# 创建FastAPI应用
app = FastAPI(
    title="FS机器人外呼系统",
    description="支持人工接入的智能外呼系统",
    version="1.0.0",
    lifespan=lifespan
)

# 配置CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境应该限制具体域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 注册API路由
app.include_router(api_router, prefix="/api")

# WebSocket连接管理
@app.websocket("/ws/{client_type}/{client_id}")
async def websocket_endpoint(websocket: WebSocket, client_type: str, client_id: str):
    """WebSocket连接端点"""
    await websocket_manager.connect(websocket, client_type, client_id)
    try:
        while True:
            data = await websocket.receive_text()
            await websocket_manager.handle_message(client_type, client_id, data)
    except WebSocketDisconnect:
        websocket_manager.disconnect(client_type, client_id)

# 健康检查
@app.get("/health")
async def health_check():
    """系统健康检查"""
    return {
        "status": "healthy",
        "services": {
            "database": "connected",
            "redis": "connected",
            "freeswitch": "connected"
        }
    }

# 系统状态
@app.get("/status")
async def system_status():
    """获取系统状态"""
    return {
        "active_calls": await call_manager.get_active_calls_count(),
        "available_agents": await agent_manager.get_available_agents_count(),
        "queue_length": await agent_manager.get_queue_length(),
        "system_load": await call_manager.get_system_load()
    }

# 静态文件服务（用于前端）
app.mount("/static", StaticFiles(directory="static"), name="static")

if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host=settings.HOST,
        port=settings.PORT,
        reload=settings.DEBUG,
        log_level="info"
    )