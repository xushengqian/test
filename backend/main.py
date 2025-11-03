"""
??? - ??????
"""
import os
import asyncio
import logging
import signal
import sys
from dotenv import load_dotenv
from threading import Thread

# ??????
load_dotenv()

# ????
from services.asr_service import ASRService
from services.websocket_server import start_websocket_servers
from services.freeswitch_handler import FreeswitchEventHandler

# ????
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class TranscriptionSystem:
    """??????"""
    
    def __init__(self):
        self.asr_service = None
        self.ws_servers = None
        self.ws_handler = None
        self.fs_handler = None
        self.flask_thread = None
        self.running = False
        
    async def start_all_services(self):
        """??????"""
        logger.info("=" * 60)
        logger.info("??Freeswitch??????")
        logger.info("=" * 60)
        
        try:
            # 1. ??ASR??
            logger.info("???ASR??...")
            self.asr_service = ASRService()
            logger.info(f"? ASR???????????: {os.getenv('ASR_PROVIDER', 'mock')}")
            
            # 2. ??WebSocket???
            logger.info("??WebSocket???...")
            fs_port = int(os.getenv('WS_PORT', 8765))
            web_port = fs_port + 1
            
            fs_server, web_server, handler = await start_websocket_servers(
                self.asr_service,
                fs_host=os.getenv('WS_HOST', '0.0.0.0'),
                fs_port=fs_port,
                web_port=web_port
            )
            
            self.ws_servers = (fs_server, web_server)
            self.ws_handler = handler
            logger.info(f"? WebSocket???????")
            logger.info(f"  - Freeswitch???: ws://localhost:{fs_port}")
            logger.info(f"  - Web???: ws://localhost:{web_port}")
            
            # 3. ??Freeswitch?????
            logger.info("??Freeswitch ESL...")
            self.fs_handler = FreeswitchEventHandler(
                host=os.getenv('FREESWITCH_HOST', '127.0.0.1'),
                port=int(os.getenv('FREESWITCH_ESL_PORT', 8021)),
                password=os.getenv('FREESWITCH_ESL_PASSWORD', 'ClueCon')
            )
            
            if self.fs_handler.connect():
                logger.info("? Freeswitch ESL????")
                
                # ??????
                asyncio.create_task(self.fs_handler.start_event_loop())
            else:
                logger.warning("? Freeswitch ESL????????Freeswitch?????")
                self.fs_handler = None
            
            # 4. ??Flask API????????????
            logger.info("??Flask API???...")
            self.start_flask_server()
            logger.info(f"? Flask API???????")
            logger.info(f"  - API??: http://localhost:{os.getenv('API_PORT', 5000)}")
            
            # 5. ??Web??????
            logger.info("")
            logger.info("=" * 60)
            logger.info("?? ?????????")
            logger.info("=" * 60)
            logger.info("")
            logger.info("?? ?????")
            logger.info(f"  - Web??: http://localhost:{os.getenv('API_PORT', 5000)}/static/index.html")
            logger.info(f"  - API??: http://localhost:{os.getenv('API_PORT', 5000)}/api/health")
            logger.info("")
            logger.info("? Ctrl+C ????")
            logger.info("=" * 60)
            
            # ??Flask????????
            from app import active_services
            active_services['websocket_handler'] = self.ws_handler
            active_services['freeswitch_handler'] = self.fs_handler
            
            self.running = True
            
            # ????
            while self.running:
                await asyncio.sleep(1)
                
        except Exception as e:
            logger.error(f"???????: {e}", exc_info=True)
            await self.stop_all_services()
    
    def start_flask_server(self):
        """????????Flask???"""
        def run_flask():
            from app import app
            
            host = os.getenv('API_HOST', '0.0.0.0')
            port = int(os.getenv('API_PORT', 5000))
            
            # ??Flask?????????????????
            app.run(host=host, port=port, debug=False, use_reloader=False)
        
        self.flask_thread = Thread(target=run_flask, daemon=True)
        self.flask_thread.start()
    
    async def stop_all_services(self):
        """??????"""
        logger.info("????????...")
        
        self.running = False
        
        # ??Freeswitch?????
        if self.fs_handler:
            self.fs_handler.stop()
            self.fs_handler.disconnect()
        
        # ??WebSocket???
        if self.ws_servers:
            for server in self.ws_servers:
                server.close()
                await server.wait_closed()
        
        logger.info("???????")
    
    def handle_shutdown(self, signum, frame):
        """??????"""
        logger.info(f"?????? {signum}")
        asyncio.create_task(self.stop_all_services())
        sys.exit(0)


async def main():
    """???"""
    system = TranscriptionSystem()
    
    # ???????
    signal.signal(signal.SIGINT, system.handle_shutdown)
    signal.signal(signal.SIGTERM, system.handle_shutdown)
    
    # ??????
    await system.start_all_services()


if __name__ == '__main__':
    # ??Python??
    if sys.version_info < (3, 7):
        logger.error("??Python 3.7?????")
        sys.exit(1)
    
    # ?????
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        logger.info("???????")
    except Exception as e:
        logger.error(f"??????: {e}", exc_info=True)
        sys.exit(1)
