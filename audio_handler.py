"""
???????
???Freeswitch?????????????????
"""
import logging
import struct
import threading
from typing import Callable, Optional
from config import Config

logger = logging.getLogger(__name__)


class AudioStreamHandler:
    """??????"""
    
    def __init__(self, sample_rate: int = None, channels: int = None):
        self.sample_rate = sample_rate or Config.AUDIO_SAMPLE_RATE
        self.channels = channels or Config.AUDIO_CHANNELS
        self.on_agent_audio: Optional[Callable] = None
        self.on_customer_audio: Optional[Callable] = None
        self.running = False
    
    def start(self):
        """???????"""
        self.running = True
        logger.info("????????")
    
    def stop(self):
        """???????"""
        self.running = False
        logger.info("????????")
    
    def process_audio_chunk(self, audio_data: bytes, source: str):
        """
        ???????
        :param audio_data: ?????PCM???
        :param source: ???? "agent" ? "customer"
        """
        if not self.running:
            return
        
        try:
            if source == "agent" and self.on_agent_audio:
                self.on_agent_audio(audio_data)
            elif source == "customer" and self.on_customer_audio:
                self.on_customer_audio(audio_data)
        except Exception as e:
            logger.error(f"????????: {e}")
    
    def convert_to_pcm(self, audio_data: bytes, source_format: str = "PCM") -> bytes:
        """
        ???????PCM
        :param audio_data: ??????
        :param source_format: ???
        :return: PCM??????
        """
        # TODO: ????????
        # ??????????G.711, G.729?????
        return audio_data


class FreeswitchAudioCapture:
    """
    Freeswitch????
    ??ESL?????
    """
    
    def __init__(self, fs_client, call_uuid: str):
        self.fs_client = fs_client
        self.call_uuid = call_uuid
        self.recording = False
        
    def start_capture(self, output_path: str = None):
        """
        ??????
        :param output_path: ????????????
        """
        if output_path:
            # ??uuid_record????
            result = self.fs_client.uuid_record(
                self.call_uuid,
                output_path,
                channels="both"  # ????????
            )
            logger.info(f"????: {result}")
        else:
            # TODO: ??mod_audio_fork????????????
            # ????????Freeswitch????
            logger.warning("???????????????????")
        
        self.recording = True
    
    def stop_capture(self):
        """??????"""
        # TODO: ????????
        self.recording = False
        logger.info("???????")
