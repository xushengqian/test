from fastapi import FastAPI, WebSocket, WebSocketDisconnect, HTTPException, Depends
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from fastapi.responses import HTMLResponse
from fastapi.middleware.cors import CORSMiddleware
import asyncio
import json
import logging
from datetime import datetime
from typing import List, Dict, Any
import uuid

from app.database import get_db, init_db
from app.models import CallSession, Transcription
from app.services.freeswitch_service import FreeSwitchService
from app.services.speech_service import SpeechService
from app.services.websocket_manager import WebSocketManager

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="FreeSWITCH 人工外呼实时音转文系统")

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
speech_service = SpeechService()
websocket_manager = WebSocketManager()

@app.on_event("startup")
async def startup_event():
    """Initialize database and services on startup"""
    await init_db()
    await freeswitch_service.connect()
    logger.info("Application started successfully")

@app.on_event("shutdown")
async def shutdown_event():
    """Cleanup on shutdown"""
    await freeswitch_service.disconnect()
    logger.info("Application shutdown complete")

@app.get("/", response_class=HTMLResponse)
async def get_index():
    """Main dashboard page"""
    return templates.TemplateResponse("index.html", {"request": {}})

@app.websocket("/ws/{call_id}")
async def websocket_endpoint(websocket: WebSocket, call_id: str):
    """WebSocket endpoint for real-time transcription"""
    await websocket_manager.connect(websocket, call_id)
    try:
        while True:
            # Receive audio data from client
            data = await websocket.receive_bytes()
            
            # Process audio for transcription
            transcription = await speech_service.transcribe_audio(data)
            
            if transcription:
                # Store transcription in database
                await store_transcription(call_id, transcription)
                
                # Send transcription to all connected clients for this call
                await websocket_manager.broadcast_to_call(
                    call_id, 
                    {
                        "type": "transcription",
                        "text": transcription,
                        "timestamp": datetime.now().isoformat()
                    }
                )
                
    except WebSocketDisconnect:
        websocket_manager.disconnect(websocket, call_id)
    except Exception as e:
        logger.error(f"WebSocket error: {e}")
        websocket_manager.disconnect(websocket, call_id)

@app.post("/api/calls/start")
async def start_call(phone_number: str, agent_id: str = "default"):
    """Start a new outbound call"""
    call_id = str(uuid.uuid4())
    
    try:
        # Create call session in database
        session = CallSession(
            call_id=call_id,
            phone_number=phone_number,
            agent_id=agent_id,
            status="initiating",
            start_time=datetime.now()
        )
        
        # Start the call via FreeSWITCH
        success = await freeswitch_service.make_call(
            call_id=call_id,
            phone_number=phone_number,
            agent_id=agent_id
        )
        
        if success:
            session.status = "active"
            await session.save()
            return {
                "call_id": call_id,
                "status": "started",
                "phone_number": phone_number
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
            # Update call session status
            session = await CallSession.get_by_call_id(call_id)
            if session:
                session.status = "ended"
                session.end_time = datetime.now()
                await session.save()
            
            return {"call_id": call_id, "status": "ended"}
        else:
            raise HTTPException(status_code=500, detail="Failed to end call")
            
    except Exception as e:
        logger.error(f"Error ending call: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/calls/{call_id}/transcriptions")
async def get_transcriptions(call_id: str):
    """Get all transcriptions for a call"""
    try:
        transcriptions = await Transcription.get_by_call_id(call_id)
        return {
            "call_id": call_id,
            "transcriptions": [
                {
                    "text": t.text,
                    "timestamp": t.timestamp.isoformat(),
                    "confidence": t.confidence
                }
                for t in transcriptions
            ]
        }
    except Exception as e:
        logger.error(f"Error getting transcriptions: {e}")
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
            ]
        }
    except Exception as e:
        logger.error(f"Error getting calls: {e}")
        raise HTTPException(status_code=500, detail=str(e))

async def store_transcription(call_id: str, text: str, confidence: float = 0.9):
    """Store transcription in database"""
    try:
        transcription = Transcription(
            call_id=call_id,
            text=text,
            confidence=confidence,
            timestamp=datetime.now()
        )
        await transcription.save()
    except Exception as e:
        logger.error(f"Error storing transcription: {e}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)