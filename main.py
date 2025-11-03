"""
?????
"""
import logging
import signal
import sys
from freeswitch_client import FreeswitchClient
from websocket_server import WebSocketServer
from call_manager import CallManager
from config import Config

# ????
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('app.log'),
        logging.StreamHandler(sys.stdout)
    ]
)

logger = logging.getLogger(__name__)


class Application:
    """??????"""
    
    def __init__(self):
        self.fs_client = FreeswitchClient()
        self.ws_server = WebSocketServer()
        self.call_manager = CallManager(self.fs_client, self.ws_server)
        self.running = False
    
    def start(self):
        """????"""
        logger.info("??????...")
        
        # ??Freeswitch
        if not self.fs_client.connect():
            logger.error("?????Freeswitch??????")
            return False
        
        # ????
        self.fs_client.subscribe_events()
        
        # ??????
        self.fs_client.start_listening()
        
        # ??WebSocket???????????
        import threading
        ws_thread = threading.Thread(target=self.ws_server.run, daemon=True)
        ws_thread.start()
        
        self.running = True
        logger.info("??????")
        logger.info(f"WebSocket????: http://{Config.API_HOST}:{Config.API_PORT}")
        logger.info(f"??????: http://{Config.API_HOST}:{Config.API_PORT}")
        
        return True
    
    def stop(self):
        """????"""
        logger.info("??????...")
        self.running = False
        self.fs_client.disconnect()
        logger.info("?????")
    
    def run(self):
        """????"""
        if not self.start():
            return
        
        # ??????
        def signal_handler(sig, frame):
            logger.info("??????")
            self.stop()
            sys.exit(0)
        
        signal.signal(signal.SIGINT, signal_handler)
        signal.signal(signal.SIGTERM, signal_handler)
        
        # ???????
        try:
            while self.running:
                import time
                time.sleep(1)
        except KeyboardInterrupt:
            logger.info("??????")
            self.stop()


if __name__ == "__main__":
    app = Application()
    app.run()
