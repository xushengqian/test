"""
FreeSWITCH + MRCP 实时语音流处理系统
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

这个包提供了FreeSWITCH与MRCP集成的完整解决方案。

:copyright: (c) 2024
:license: MIT
"""

__version__ = '1.0.0'
__author__ = 'FreeSWITCH MRCP Team'

from .mrcp_client import MRCPClient
from .stream_processor import AudioStreamProcessor, RTPStreamProcessor
from .audio_handler import AudioHandler, AudioFormat
from .websocket_server import AudioWebSocketServer, AudioStreamClient

__all__ = [
    'MRCPClient',
    'AudioStreamProcessor',
    'RTPStreamProcessor',
    'AudioHandler',
    'AudioFormat',
    'AudioWebSocketServer',
    'AudioStreamClient',
]
