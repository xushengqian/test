from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, Depends, BackgroundTasks
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
import asyncio
import json
import logging
from datetime import datetime
from typing import List, Dict, Any
import uuid
import time

from app.database import get_db, init_db
from app.models import CallSession, Transcription
from app.services.freeswitch_service import FreeSwitchService
from app.services.optimized_speech_service import OptimizedSpeechService
from app.services.websocket_manager import WebSocketManager
from app.services.rate_limiter import CallRateLimiter, TranscriptionRateLimiter
from app.services.monitoring_service import system_monitor, performance_tracker

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="FreeSWITCH 人工外呼实时音转文系统 (优化版)")

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Static files and templates
app.mount("/static", StaticFiles(directory="static"), name="static")
templates = Jinja2Templates(directory="templates")

# Initialize services
freeswitch_service = FreeSwitchService()
speech_service = OptimizedSpeechService(max_workers=3, chunk_duration=2.0)  # 优化参数
websocket_manager = WebSocketManager()
call_rate_limiter = CallRateLimiter(max_concurrent_calls=5, max_calls_per_minute=20)
transcription_rate_limiter = TranscriptionRateLimiter(max_transcriptions_per_second=3)

@app.on_event("startup")
async def startup_event():
    """Initialize database and services on startup"""
    await init_db()
    await freeswitch_service.connect()
    
    # 启动监控任务
    asyncio.create_task(monitoring_task())
    
    logger.info("Optimized application started successfully")

@app.on_event("shutdown")
async def shutdown_event():
    """Cleanup on shutdown"""
    await freeswitch_service.disconnect()
    speech_service.shutdown()
    logger.info("Application shutdown complete")

async def monitoring_task():
    """后台监控任务"""
    while True:
        try:
            # 更新性能统计
            performance_tracker.update_websocket_connections(websocket_manager.get_total_connections())
            performance_tracker.update_active_calls(len(websocket_manager.get_active_calls()))
            
            # 检查系统健康状态
            health = system_monitor.check_health()
            if health["status"] == "error":
                logger.error(f"System health error: {health['errors']}")
            elif health["status"] == "warning":
                logger.warning(f"System health warning: {health['warnings']}")
            
            await asyncio.sleep(30)  # 每30秒检查一次
        except Exception as e:
            logger.error(f"Monitoring task error: {e}")
            await asyncio.sleep(60)

@app.get("/", response_class=HTMLResponse)
async def get_index():
    """Main dashboard page"""
    return templates.TemplateResponse("index.html", {"request": {}})

@app.get("/monitor", response_class=HTMLResponse)
async def get_monitor():
    """System monitoring dashboard"""
    return templates.TemplateResponse("monitor.html", {"request": {}})

@app.websocket("/ws/{call_id}")
async def websocket_endpoint(websocket: WebSocket, call_id: str):
    """WebSocket endpoint for real-time transcription"""
    await websocket_manager.connect(websocket, call_id)
    
    try:
        while True:
            # 检查转写速率限制
            if not await transcription_rate_limiter.can_transcribe():
                await asyncio.sleep(0.2)  # 等待200ms
                continue
            
            # 接收音频数据
            data = await websocket.receive_bytes()
            
            # 添加到语音服务处理队列
            await speech_service.add_audio_data(call_id, data)
            
            # 检查是否有新的转写结果（这里简化处理）
            # 实际实现中应该通过回调或事件机制获取结果
            
    except WebSocketDisconnect:
        websocket_manager.disconnect(websocket, call_id)
        speech_service.cleanup_call(call_id)
    except Exception as e:
        logger.error(f"WebSocket error: {e}")
        websocket_manager.disconnect(websocket, call_id)
        speech_service.cleanup_call(call_id)

@app.post("/api/calls/start")
async def start_call(phone_number: str, agent_id: str = "default"):
    """Start a new outbound call with rate limiting"""
    call_id = str(uuid.uuid4())
    
    # 检查速率限制
    can_start, message = await call_rate_limiter.can_start_call(call_id)
    if not can_start:
        raise HTTPException(status_code=429, detail=message)
    
    try:
        # 创建通话会话
        session = CallSession(
            call_id=call_id,
            phone_number=phone_number,
            agent_id=agent_id,
            status="initiating",
            start_time=datetime.now()
        )
        
        # 发起通话
        success = await freeswitch_service.make_call(
            call_id=call_id,
            phone_number=phone_number,
            agent_id=agent_id
        )
        
        if success:
            session.status = "active"
            await session.save()
            await call_rate_limiter.start_call(call_id)
            
            return {
                "call_id": call_id,
                "status": "started",
                "phone_number": phone_number,
                "rate_limit_info": call_rate_limiter.get_stats()
            }
        else:
            session.status = "failed"
            await session.save()
            raise HTTPException(status_code=500, detail="Failed to start call")
            
    except Exception as e:
        logger.error(f"Error starting call: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/calls/{call_id}/end")
async def end_call(call_id: str):
    """End an active call"""
    try:
        success = await freeswitch_service.hangup_call(call_id)
        
        if success:
            # 更新通话会话状态
            session = await CallSession.get_by_call_id(call_id)
            if session:
                session.status = "ended"
                session.end_time = datetime.now()
                await session.save()
            
            # 清理资源
            await call_rate_limiter.end_call(call_id)
            speech_service.cleanup_call(call_id)
            
            return {"call_id": call_id, "status": "ended"}
        else:
            raise HTTPException(status_code=500, detail="Failed to end call")
            
    except Exception as e:
        logger.error(f"Error ending call: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/calls")
async def get_calls():
    """Get all call sessions"""
    try:
        calls = await CallSession.get_all()
        return {
            "calls": [
                {
                    "call_id": c.call_id,
                    "phone_number": c.phone_number,
                    "agent_id": c.agent_id,
                    "status": c.status,
                    "start_time": c.start_time.isoformat() if c.start_time else None,
                    "end_time": c.end_time.isoformat() if c.end_time else None
                }
                for c in calls
            ],
            "rate_limit_info": call_rate_limiter.get_stats()
        }
    except Exception as e:
        logger.error(f"Error getting calls: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/health")
async def health_check():
    """系统健康检查"""
    health = system_monitor.check_health()
    performance = performance_tracker.get_performance_stats()
    
    return {
        "status": health["status"],
        "timestamp": datetime.now().isoformat(),
        "system_health": health,
        "performance": performance,
        "rate_limits": call_rate_limiter.get_stats(),
        "transcription_rate": transcription_rate_limiter.get_current_rate()
    }

@app.get("/api/metrics")
async def get_metrics():
    """获取系统指标"""
    return {
        "current": system_monitor.get_system_metrics(),
        "summary_1h": system_monitor.get_metrics_summary(1),
        "summary_24h": system_monitor.get_metrics_summary(24),
        "performance": performance_tracker.get_performance_stats()
    }

@app.get("/api/speech/status")
async def get_speech_status():
    """获取语音服务状态"""
    return {
        "queue_size": speech_service.get_queue_size(),
        "buffer_info": speech_service.get_buffer_info(),
        "transcription_rate": transcription_rate_limiter.get_current_rate()
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)