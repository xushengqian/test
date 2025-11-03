"""
?????
"""
import asyncio
import logging
from fastapi import FastAPI, WebSocket, WebSocketDisconnect, Request
from fastapi.staticfiles import StaticFiles
from fastapi.responses import HTMLResponse, StreamingResponse
from config import Config
from freeswitch_client import FreeswitchClient
from call_manager import CallManager
from websocket_server import WebSocketServer
import uvicorn

# ????
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# ??FastAPI??
app = FastAPI(title="Freeswitch ???????")

# ????
fs_client: FreeswitchClient = None
call_manager: CallManager = None
ws_server: WebSocketServer = None

# ??????????????????
audio_streams = {}  # (call_uuid, stream_type) -> queue


@app.on_event("startup")
async def startup():
    """??????"""
    global fs_client, call_manager, ws_server
    
    # ????
    if not Config.validate():
        logger.error("????????????")
        return
    
    # ??Freeswitch
    try:
        fs_client = FreeswitchClient(
            Config.FS_HOST,
            Config.FS_PORT,
            Config.FS_PASSWORD
        )
        await fs_client.connect()
        logger.info("Freeswitch????")
    except Exception as e:
        logger.error(f"Freeswitch????: {e}")
        return
    
    # ???????
    call_manager = CallManager(fs_client)
    
    # ??WebSocket???
    ws_server = WebSocketServer(call_manager, Config.WS_PORT)
    
    # ??WebSocket?????????
    asyncio.create_task(ws_server.start())
    
    logger.info("??????")


@app.on_event("shutdown")
async def shutdown():
    """?????"""
    global fs_client
    if fs_client:
        await fs_client.disconnect()
    logger.info("?????")


@app.get("/")
async def index():
    """??"""
    return HTMLResponse(content=open("static/index.html", "r", encoding="utf-8").read())


@app.post("/api/call/make")
async def make_call(request: Request):
    """????"""
    form_data = await request.form()
    caller = form_data.get("caller", "")
    callee = form_data.get("callee", "")
    
    try:
        call_uuid = await call_manager.make_outbound_call(caller, callee)
        return {"success": True, "call_uuid": call_uuid}
    except Exception as e:
        logger.error(f"????: {e}")
        return {"success": False, "error": str(e)}


@app.get("/api/call/status/{call_uuid}")
async def get_call_status(call_uuid: str):
    """??????"""
    session = call_manager.sessions.get(call_uuid)
    if session:
        return {
            "call_uuid": call_uuid,
            "robot_active": session.robot_active,
            "agent_active": session.agent_active
        }
    return {"error": "?????"}


@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    """WebSocket??"""
    await websocket.accept()
    
    try:
        while True:
            message = await websocket.receive_text()
            
            import json
            try:
                data = json.loads(message)
                msg_type = data.get("type")
                
                if msg_type == "subscribe":
                    call_uuid = data.get("call_uuid")
                    if call_uuid:
                        await ws_server.subscribe_call(websocket, call_uuid)
                        await websocket.send_text(json.dumps({
                            "type": "subscribed",
                            "call_uuid": call_uuid
                        }, ensure_ascii=False))
                
                elif msg_type == "ping":
                    await websocket.send_text(json.dumps({"type": "pong"}, ensure_ascii=False))
                    
            except json.JSONDecodeError:
                await websocket.send_text(json.dumps({
                    "type": "error",
                    "message": "???JSON"
                }, ensure_ascii=False))
    
    except WebSocketDisconnect:
        logger.info("WebSocket????")


@app.post("/audio/agent/{call_uuid}")
async def receive_agent_audio(call_uuid: str, request: Request):
    """??????????Freeswitch audio_fork?"""
    # ???????
    key = (call_uuid, "agent")
    if key not in audio_streams:
        audio_streams[key] = asyncio.Queue()
    
    data = await request.body()
    await audio_streams[key].put(data)
    
    return {"status": "ok"}


@app.post("/audio/customer/{call_uuid}")
async def receive_customer_audio(call_uuid: str, request: Request):
    """??????????Freeswitch audio_fork?"""
    # ???????
    key = (call_uuid, "customer")
    if key not in audio_streams:
        audio_streams[key] = asyncio.Queue()
    
    data = await request.body()
    await audio_streams[key].put(data)
    
    return {"status": "ok"}


if __name__ == "__main__":
    uvicorn.run(
        "main:app",
        host=Config.SERVER_HOST,
        port=Config.SERVER_PORT,
        log_level="info"
    )
