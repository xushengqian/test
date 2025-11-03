"""
WebSocket???????????????
"""
import logging
import json
import asyncio
from typing import Set
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse
import uvicorn
from config import Config

logger = logging.getLogger(__name__)


class WebSocketServer:
    """WebSocket???"""
    
    def __init__(self):
        self.app = FastAPI()
        self.active_connections: Set[WebSocket] = set()
        self._setup_routes()
    
    def _setup_routes(self):
        """????"""
        
        @self.app.websocket("/ws/transcription")
        async def websocket_endpoint(websocket: WebSocket):
            await websocket.accept()
            self.active_connections.add(websocket)
            logger.info(f"??WebSocket????????: {len(self.active_connections)}")
            
            try:
                while True:
                    # ????????????????
                    data = await websocket.receive_text()
                    # ????????????
                    logger.debug(f"???????: {data}")
            except WebSocketDisconnect:
                self.active_connections.remove(websocket)
                logger.info(f"WebSocket??????????: {len(self.active_connections)}")
        
        @self.app.get("/")
        async def get():
            return HTMLResponse(self._get_html())
    
    def _get_html(self) -> str:
        """????HTML"""
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>???????</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            min-height: 100vh;
            padding: 20px;
        }
        
        .container {
            max-width: 1200px;
            margin: 0 auto;
            background: white;
            border-radius: 12px;
            box-shadow: 0 10px 40px rgba(0,0,0,0.2);
            overflow: hidden;
        }
        
        .header {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            padding: 20px 30px;
            text-align: center;
        }
        
        .header h1 {
            font-size: 24px;
            font-weight: 600;
        }
        
        .status {
            padding: 15px 30px;
            background: #f8f9fa;
            border-bottom: 1px solid #e9ecef;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        
        .status-indicator {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        
        .status-dot {
            width: 12px;
            height: 12px;
            border-radius: 50%;
            background: #dc3545;
            animation: pulse 2s infinite;
        }
        
        .status-dot.connected {
            background: #28a745;
        }
        
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        
        .transcription-area {
            padding: 30px;
            max-height: 600px;
            overflow-y: auto;
        }
        
        .message {
            margin-bottom: 20px;
            animation: fadeIn 0.3s ease-in;
        }
        
        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(10px); }
            to { opacity: 1; transform: translateY(0); }
        }
        
        .message-header {
            display: flex;
            align-items: center;
            gap: 10px;
            margin-bottom: 8px;
        }
        
        .speaker-badge {
            padding: 4px 12px;
            border-radius: 12px;
            font-size: 12px;
            font-weight: 600;
            text-transform: uppercase;
        }
        
        .speaker-badge.agent {
            background: #007bff;
            color: white;
        }
        
        .speaker-badge.customer {
            background: #28a745;
            color: white;
        }
        
        .message-time {
            font-size: 12px;
            color: #6c757d;
        }
        
        .message-text {
            padding: 12px 16px;
            background: #f8f9fa;
            border-radius: 8px;
            border-left: 4px solid #667eea;
            line-height: 1.6;
            color: #212529;
        }
        
        .message-text.agent {
            border-left-color: #007bff;
        }
        
        .message-text.customer {
            border-left-color: #28a745;
        }
        
        .message-text.partial {
            opacity: 0.7;
            font-style: italic;
        }
        
        .empty-state {
            text-align: center;
            padding: 60px 20px;
            color: #6c757d;
        }
        
        .empty-state svg {
            width: 80px;
            height: 80px;
            margin-bottom: 20px;
            opacity: 0.3;
        }
        
        ::-webkit-scrollbar {
            width: 8px;
        }
        
        ::-webkit-scrollbar-track {
            background: #f1f1f1;
            border-radius: 4px;
        }
        
        ::-webkit-scrollbar-thumb {
            background: #888;
            border-radius: 4px;
        }
        
        ::-webkit-scrollbar-thumb:hover {
            background: #555;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>?? ???????</h1>
        </div>
        
        <div class="status">
            <div class="status-indicator">
                <div class="status-dot" id="statusDot"></div>
                <span id="statusText">???...</span>
            </div>
            <div id="callInfo"></div>
        </div>
        
        <div class="transcription-area" id="transcriptionArea">
            <div class="empty-state">
                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z" />
                </svg>
                <p>??????...</p>
            </div>
        </div>
    </div>
    
    <script>
        const ws = new WebSocket(`ws://${window.location.host}/ws/transcription`);
        const statusDot = document.getElementById('statusDot');
        const statusText = document.getElementById('statusText');
        const callInfo = document.getElementById('callInfo');
        const transcriptionArea = document.getElementById('transcriptionArea');
        
        let isEmpty = true;
        
        ws.onopen = function() {
            statusDot.classList.add('connected');
            statusText.textContent = '???';
            console.log('WebSocket?????');
        };
        
        ws.onmessage = function(event) {
            const data = JSON.parse(event.data);
            console.log('??????:', data);
            
            if (isEmpty) {
                transcriptionArea.innerHTML = '';
                isEmpty = false;
            }
            
            addTranscription(data);
        };
        
        ws.onerror = function(error) {
            console.error('WebSocket??:', error);
            statusText.textContent = '????';
            statusDot.classList.remove('connected');
        };
        
        ws.onclose = function() {
            console.log('WebSocket?????');
            statusText.textContent = '?????';
            statusDot.classList.remove('connected');
        };
        
        function addTranscription(data) {
            const messageDiv = document.createElement('div');
            messageDiv.className = 'message';
            
            const speaker = data.speaker === 'agent' ? '??' : '??';
            const speakerClass = data.speaker;
            
            messageDiv.innerHTML = `
                <div class="message-header">
                    <span class="speaker-badge ${speakerClass}">${speaker}</span>
                    <span class="message-time">${new Date().toLocaleTimeString()}</span>
                </div>
                <div class="message-text ${speakerClass} ${data.is_final ? '' : 'partial'}">
                    ${escapeHtml(data.text)}
                </div>
            `;
            
            transcriptionArea.appendChild(messageDiv);
            transcriptionArea.scrollTop = transcriptionArea.scrollHeight;
            
            // ?????????????????
            if (data.is_final) {
                const partialMessages = messageDiv.querySelectorAll('.partial');
                partialMessages.forEach(msg => {
                    msg.classList.remove('partial');
                });
            }
        }
        
        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
    </script>
</body>
</html>
        """
    
    async def broadcast_transcription(self, data: dict):
        """??????"""
        if not self.active_connections:
            return
        
        message = json.dumps(data)
        disconnected = set()
        
        for connection in self.active_connections:
            try:
                await connection.send_text(message)
            except Exception as e:
                logger.error(f"??????: {e}")
                disconnected.add(connection)
        
        # ???????
        self.active_connections -= disconnected
    
    def run(self, host: str = None, port: int = None):
        """??WebSocket???"""
        host = host or Config.API_HOST
        port = port or Config.API_PORT
        
        uvicorn.run(self.app, host=host, port=port, log_level="info")
