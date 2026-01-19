#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FreeSWITCH ESL HTTP 音频播放器

通过 ESL (Event Socket Library) 从 HTTP 服务器拉取音频流播放

依赖安装：
    pip install python-ESL
    # 或者
    pip install greenswitch

使用示例：
    player = FreeSwitchHttpAudioPlayer('127.0.0.1', 8021, 'ClueCon')
    if player.connect():
        player.play_audio('channel-uuid', 'http://audio.example.com/welcome.mp3')
        player.disconnect()

作者：FreeSWITCH HTTP Audio Demo
版本：1.0.0
"""

import logging
import time
import threading
from typing import Optional, List, Dict, Any, Callable
from dataclasses import dataclass
from enum import Enum

# 尝试导入 ESL 库
try:
    import ESL
    ESL_AVAILABLE = True
except ImportError:
    ESL_AVAILABLE = False
    logging.warning("python-ESL not installed. Please install with: pip install python-ESL")

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class PlaybackLeg(Enum):
    """播放方向枚举"""
    ALEG = "aleg"
    BLEG = "bleg"
    BOTH = "both"


@dataclass
class PlaybackResult:
    """播放结果"""
    success: bool
    response: str
    playback_url: Optional[str] = None
    error: Optional[str] = None


class FreeSwitchHttpAudioPlayer:
    """FreeSWITCH HTTP 音频播放器"""
    
    def __init__(self, host: str = "127.0.0.1", port: int = 8021, password: str = "ClueCon"):
        """
        初始化播放器
        
        Args:
            host: FreeSWITCH 主机地址
            port: ESL 端口（默认 8021）
            password: ESL 密码
        """
        self.host = host
        self.port = port
        self.password = password
        self.conn: Optional['ESL.ESLconnection'] = None
        self._lock = threading.Lock()
    
    def connect(self) -> bool:
        """
        连接到 FreeSWITCH
        
        Returns:
            连接是否成功
        """
        if not ESL_AVAILABLE:
            logger.error("ESL library not available")
            return False
        
        try:
            self.conn = ESL.ESLconnection(self.host, str(self.port), self.password)
            
            if self.conn.connected():
                logger.info(f"Connected to FreeSWITCH at {self.host}:{self.port}")
                return True
            else:
                logger.error("Failed to connect to FreeSWITCH")
                return False
                
        except Exception as e:
            logger.error(f"Connection error: {e}")
            return False
    
    def disconnect(self):
        """断开连接"""
        if self.conn:
            self.conn.disconnect()
            self.conn = None
            logger.info("Disconnected from FreeSWITCH")
    
    def is_connected(self) -> bool:
        """检查是否已连接"""
        return self.conn is not None and self.conn.connected()
    
    def _get_playback_url(self, audio_url: str) -> str:
        """
        根据音频 URL 获取播放协议
        
        Args:
            audio_url: 原始音频 URL
        
        Returns:
            带协议前缀的播放 URL
        """
        lower_url = audio_url.lower()
        
        # MP3 格式使用 shout 协议
        if lower_url.endswith('.mp3') or '.mp3?' in lower_url:
            return f"shout://{audio_url}"
        
        # 其他格式使用 http_cache
        if audio_url.startswith('http://') or audio_url.startswith('https://'):
            return f"http_cache://{audio_url}"
        
        # 默认添加 http:// 前缀
        return f"http_cache://http://{audio_url}"
    
    def _api(self, command: str) -> str:
        """
        执行 API 命令
        
        Args:
            command: API 命令
        
        Returns:
            响应内容
        """
        if not self.is_connected():
            return "-ERR not connected"
        
        with self._lock:
            result = self.conn.api(command)
            return result.getBody() if result else ""
    
    def play_audio(
        self, 
        uuid: str, 
        audio_url: str, 
        leg: PlaybackLeg = PlaybackLeg.BOTH
    ) -> PlaybackResult:
        """
        在指定通道播放 HTTP 音频
        
        Args:
            uuid: 通道 UUID
            audio_url: HTTP 音频 URL
            leg: 播放方向
        
        Returns:
            播放结果
        """
        if not self.is_connected():
            return PlaybackResult(
                success=False,
                response="",
                error="Not connected to FreeSWITCH"
            )
        
        playback_url = self._get_playback_url(audio_url)
        command = f"uuid_broadcast {uuid} {playback_url} {leg.value}"
        
        logger.info(f"Executing: {command}")
        
        response = self._api(command)
        success = "-ERR" not in response
        
        if success:
            logger.info(f"Playback started for UUID: {uuid}")
        else:
            logger.warning(f"Playback failed for UUID: {uuid}, response: {response}")
        
        return PlaybackResult(
            success=success,
            response=response,
            playback_url=playback_url
        )
    
    def stop_playback(self, uuid: str) -> PlaybackResult:
        """
        停止当前播放
        
        Args:
            uuid: 通道 UUID
        
        Returns:
            执行结果
        """
        command = f"uuid_break {uuid} all"
        logger.info(f"Stopping playback for UUID: {uuid}")
        
        response = self._api(command)
        success = "-ERR" not in response
        
        return PlaybackResult(success=success, response=response)
    
    def overlay_audio(
        self, 
        uuid: str, 
        audio_url: str, 
        volume: int = 0
    ) -> PlaybackResult:
        """
        叠加播放音频（混音，不中断当前音频）
        
        Args:
            uuid: 通道 UUID
            audio_url: HTTP 音频 URL
            volume: 音量调整 (-4 到 4)
        
        Returns:
            执行结果
        """
        playback_url = self._get_playback_url(audio_url)
        command = f"uuid_displace {uuid} start {playback_url} {volume} mux"
        
        logger.info(f"Overlay audio for UUID: {uuid}")
        
        response = self._api(command)
        success = "-ERR" not in response
        
        return PlaybackResult(
            success=success,
            response=response,
            playback_url=playback_url
        )
    
    def stop_overlay(self, uuid: str, audio_url: str) -> PlaybackResult:
        """
        停止叠加音频
        
        Args:
            uuid: 通道 UUID
            audio_url: 要停止的音频 URL
        
        Returns:
            执行结果
        """
        playback_url = self._get_playback_url(audio_url)
        command = f"uuid_displace {uuid} stop {playback_url}"
        
        response = self._api(command)
        success = "-ERR" not in response
        
        return PlaybackResult(success=success, response=response)
    
    def play_audio_async(
        self, 
        uuid: str, 
        audio_url: str,
        callback: Optional[Callable[[PlaybackResult], None]] = None
    ) -> threading.Thread:
        """
        异步播放音频
        
        Args:
            uuid: 通道 UUID
            audio_url: HTTP 音频 URL
            callback: 播放完成后的回调函数
        
        Returns:
            线程对象
        """
        def _play():
            result = self.play_audio(uuid, audio_url)
            if callback:
                callback(result)
        
        thread = threading.Thread(target=_play, daemon=True)
        thread.start()
        return thread
    
    def play_playlist(
        self, 
        uuid: str, 
        audio_urls: List[str], 
        interval_ms: int = 500
    ) -> List[PlaybackResult]:
        """
        播放音频列表
        
        Args:
            uuid: 通道 UUID
            audio_urls: 音频 URL 列表
            interval_ms: 音频之间的间隔（毫秒）
        
        Returns:
            每个音频的播放结果
        """
        results = []
        
        for i, audio_url in enumerate(audio_urls):
            logger.info(f"Playing {i+1}/{len(audio_urls)}: {audio_url}")
            
            result = self.play_audio(uuid, audio_url)
            results.append(result)
            
            if not result.success:
                logger.warning(f"Failed to play: {audio_url}")
            
            if i < len(audio_urls) - 1:
                time.sleep(interval_ms / 1000.0)
        
        return results
    
    def set_variable(self, uuid: str, name: str, value: str) -> bool:
        """
        设置通道变量
        
        Args:
            uuid: 通道 UUID
            name: 变量名
            value: 变量值
        
        Returns:
            是否成功
        """
        command = f"uuid_setvar {uuid} {name} {value}"
        response = self._api(command)
        return "-ERR" not in response
    
    def get_variable(self, uuid: str, name: str) -> Optional[str]:
        """
        获取通道变量
        
        Args:
            uuid: 通道 UUID
            name: 变量名
        
        Returns:
            变量值
        """
        command = f"uuid_getvar {uuid} {name}"
        response = self._api(command)
        
        if "-ERR" not in response:
            return response.strip()
        return None
    
    def originate(
        self, 
        destination: str, 
        caller_id_number: str = "",
        caller_id_name: str = "",
        gateway: str = "default"
    ) -> Optional[str]:
        """
        发起呼叫
        
        Args:
            destination: 目标号码
            caller_id_number: 主叫号码
            caller_id_name: 主叫名称
            gateway: SIP 网关名称
        
        Returns:
            通道 UUID，失败返回 None
        """
        vars_str = ""
        if caller_id_number:
            vars_str += f"origination_caller_id_number={caller_id_number},"
        if caller_id_name:
            vars_str += f"origination_caller_id_name={caller_id_name},"
        
        vars_str = vars_str.rstrip(",")
        
        if vars_str:
            command = f"originate {{{vars_str}}}sofia/gateway/{gateway}/{destination} &park"
        else:
            command = f"originate sofia/gateway/{gateway}/{destination} &park"
        
        response = self._api(command)
        
        if "-ERR" not in response and response.startswith("+OK"):
            uuid = response.replace("+OK", "").strip()
            logger.info(f"Originate successful, UUID: {uuid}")
            return uuid
        
        logger.error(f"Originate failed: {response}")
        return None
    
    def hangup(self, uuid: str, cause: str = "NORMAL_CLEARING") -> bool:
        """
        挂断通道
        
        Args:
            uuid: 通道 UUID
            cause: 挂断原因
        
        Returns:
            是否成功
        """
        command = f"uuid_kill {uuid} {cause}"
        response = self._api(command)
        return "-ERR" not in response
    
    def transfer(self, uuid: str, destination: str, context: str = "default") -> bool:
        """
        转接通道
        
        Args:
            uuid: 通道 UUID
            destination: 目标分机/号码
            context: 上下文
        
        Returns:
            是否成功
        """
        command = f"uuid_transfer {uuid} {destination} XML {context}"
        response = self._api(command)
        return "-ERR" not in response


class HttpAudioPlayerWithEvents(FreeSwitchHttpAudioPlayer):
    """带事件监听的 HTTP 音频播放器"""
    
    def __init__(self, host: str = "127.0.0.1", port: int = 8021, password: str = "ClueCon"):
        super().__init__(host, port, password)
        self._event_handlers: Dict[str, List[Callable]] = {}
        self._event_thread: Optional[threading.Thread] = None
        self._running = False
    
    def connect(self) -> bool:
        """连接并开始监听事件"""
        if not super().connect():
            return False
        
        # 订阅事件
        self.conn.events("plain", "PLAYBACK_START PLAYBACK_STOP CHANNEL_HANGUP")
        
        # 启动事件监听线程
        self._running = True
        self._event_thread = threading.Thread(target=self._event_loop, daemon=True)
        self._event_thread.start()
        
        return True
    
    def disconnect(self):
        """断开连接"""
        self._running = False
        if self._event_thread:
            self._event_thread.join(timeout=2)
        super().disconnect()
    
    def _event_loop(self):
        """事件循环"""
        while self._running and self.is_connected():
            event = self.conn.recvEvent()
            if event:
                event_name = event.getHeader("Event-Name")
                if event_name and event_name in self._event_handlers:
                    event_data = self._parse_event(event)
                    for handler in self._event_handlers[event_name]:
                        try:
                            handler(event_data)
                        except Exception as e:
                            logger.error(f"Event handler error: {e}")
    
    def _parse_event(self, event) -> Dict[str, Any]:
        """解析事件"""
        return {
            "event_name": event.getHeader("Event-Name"),
            "uuid": event.getHeader("Unique-ID"),
            "caller_id_number": event.getHeader("Caller-Caller-ID-Number"),
            "destination_number": event.getHeader("Caller-Destination-Number"),
            "playback_file_path": event.getHeader("Playback-File-Path"),
        }
    
    def on_playback_start(self, handler: Callable[[Dict], None]):
        """注册播放开始事件处理器"""
        if "PLAYBACK_START" not in self._event_handlers:
            self._event_handlers["PLAYBACK_START"] = []
        self._event_handlers["PLAYBACK_START"].append(handler)
    
    def on_playback_stop(self, handler: Callable[[Dict], None]):
        """注册播放结束事件处理器"""
        if "PLAYBACK_STOP" not in self._event_handlers:
            self._event_handlers["PLAYBACK_STOP"] = []
        self._event_handlers["PLAYBACK_STOP"].append(handler)
    
    def on_hangup(self, handler: Callable[[Dict], None]):
        """注册挂断事件处理器"""
        if "CHANNEL_HANGUP" not in self._event_handlers:
            self._event_handlers["CHANNEL_HANGUP"] = []
        self._event_handlers["CHANNEL_HANGUP"].append(handler)


def main():
    """使用示例"""
    player = FreeSwitchHttpAudioPlayer(
        host="127.0.0.1",
        port=8021,
        password="ClueCon"
    )
    
    if not player.connect():
        logger.error("Failed to connect to FreeSWITCH")
        return
    
    try:
        # 示例 1：播放 HTTP 音频
        uuid = "your-channel-uuid-here"
        audio_url = "http://audio.example.com/welcome.mp3"
        
        result = player.play_audio(uuid, audio_url)
        logger.info(f"Play result: {result}")
        
        # 示例 2：播放音频列表
        playlist = [
            "http://audio.example.com/audio1.mp3",
            "http://audio.example.com/audio2.mp3",
            "http://audio.example.com/audio3.mp3"
        ]
        
        results = player.play_playlist(uuid, playlist, interval_ms=1000)
        for i, r in enumerate(results):
            logger.info(f"Playlist item {i+1}: {r.success}")
        
        # 示例 3：叠加音频
        overlay_result = player.overlay_audio(
            uuid, 
            "http://audio.example.com/notification.mp3",
            volume=0
        )
        logger.info(f"Overlay result: {overlay_result}")
        
        # 等待一段时间
        time.sleep(5)
        
        # 停止播放
        player.stop_playback(uuid)
        
    finally:
        player.disconnect()


def example_with_events():
    """带事件监听的示例"""
    player = HttpAudioPlayerWithEvents(
        host="127.0.0.1",
        port=8021,
        password="ClueCon"
    )
    
    # 注册事件处理器
    def on_playback_start(event):
        logger.info(f"Playback started: {event}")
    
    def on_playback_stop(event):
        logger.info(f"Playback stopped: {event}")
    
    player.on_playback_start(on_playback_start)
    player.on_playback_stop(on_playback_stop)
    
    if not player.connect():
        return
    
    try:
        uuid = "your-channel-uuid-here"
        player.play_audio(uuid, "http://audio.example.com/test.mp3")
        
        # 等待事件
        time.sleep(10)
        
    finally:
        player.disconnect()


if __name__ == "__main__":
    main()
