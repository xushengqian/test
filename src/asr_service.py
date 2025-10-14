"""
语音识别（ASR）服务模块
支持多种 ASR 引擎：百度、阿里云、讯飞等
"""
import base64
import json
import time
from abc import ABC, abstractmethod
from typing import Optional, Tuple
from pathlib import Path

import yaml
import requests
from aip import AipSpeech

from utils.logger import get_logger

logger = get_logger(__name__)

# 加载配置
config_path = Path(__file__).parent.parent / "config" / "config.yaml"
with open(config_path, 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

asr_config = config.get('asr', {})

class ASREngine(ABC):
    """ASR 引擎基类"""
    
    @abstractmethod
    def recognize(self, audio_data: bytes, format: str = 'wav', rate: int = 8000) -> Tuple[bool, str]:
        """
        识别音频
        
        Args:
            audio_data: 音频数据
            format: 音频格式
            rate: 采样率
            
        Returns:
            (是否成功, 识别结果文本)
        """
        pass

class BaiduASR(ASREngine):
    """百度语音识别引擎"""
    
    def __init__(self):
        baidu_config = asr_config.get('baidu', {})
        self.app_id = baidu_config.get('app_id', '')
        self.api_key = baidu_config.get('api_key', '')
        self.secret_key = baidu_config.get('secret_key', '')
        
        if not all([self.app_id, self.api_key, self.secret_key]):
            logger.warning("百度 ASR 配置不完整，请检查 config.yaml")
            
        self.client = AipSpeech(self.app_id, self.api_key, self.secret_key)
        logger.info("百度 ASR 引擎初始化成功")
    
    def recognize(self, audio_data: bytes, format: str = 'wav', rate: int = 8000) -> Tuple[bool, str]:
        """
        使用百度 ASR 识别音频
        
        Args:
            audio_data: 音频数据
            format: 音频格式
            rate: 采样率
            
        Returns:
            (是否成功, 识别结果文本)
        """
        try:
            # 调用百度 ASR API
            result = self.client.asr(
                audio_data, 
                format, 
                rate,
                {
                    'dev_pid': 1537,  # 普通话模型
                    'lan': 'zh',
                    'cuid': 'freeswitch_bot'
                }
            )
            
            # 解析结果
            if result.get('err_no') == 0:
                text = ''.join(result.get('result', []))
                logger.info(f"ASR 识别成功: {text}")
                return True, text
            else:
                logger.error(f"ASR 识别失败: {result.get('err_msg')}")
                return False, ''
                
        except Exception as e:
            logger.error(f"ASR 识别异常: {str(e)}")
            return False, ''

class MockASR(ASREngine):
    """模拟 ASR 引擎（用于测试）"""
    
    def __init__(self):
        logger.info("使用模拟 ASR 引擎")
        self.responses = [
            "你好",
            "我要转人工",
            "请帮我转接客服",
            "我需要人工服务",
            "不要机器人，我要真人"
        ]
        self.index = 0
    
    def recognize(self, audio_data: bytes, format: str = 'wav', rate: int = 8000) -> Tuple[bool, str]:
        """
        模拟 ASR 识别
        
        Returns:
            (True, 模拟的识别结果)
        """
        # 模拟识别延迟
        time.sleep(0.5)
        
        # 循环返回预设的响应
        text = self.responses[self.index % len(self.responses)]
        self.index += 1
        
        logger.info(f"模拟 ASR 识别: {text}")
        return True, text

class ASRService:
    """ASR 服务管理器"""
    
    def __init__(self):
        self.engine_name = asr_config.get('engine', 'mock')
        self.engine = None
        self._init_engine()
    
    def _init_engine(self):
        """初始化 ASR 引擎"""
        logger.info(f"初始化 ASR 引擎: {self.engine_name}")
        
        if self.engine_name == 'baidu':
            self.engine = BaiduASR()
        elif self.engine_name == 'mock':
            self.engine = MockASR()
        else:
            logger.warning(f"不支持的 ASR 引擎: {self.engine_name}，使用模拟引擎")
            self.engine = MockASR()
    
    def recognize(self, audio_data: bytes, format: str = 'wav', rate: int = 8000) -> Tuple[bool, str]:
        """
        识别音频
        
        Args:
            audio_data: 音频数据
            format: 音频格式
            rate: 采样率
            
        Returns:
            (是否成功, 识别结果文本)
        """
        if not self.engine:
            logger.error("ASR 引擎未初始化")
            return False, ''
        
        return self.engine.recognize(audio_data, format, rate)
    
    def recognize_file(self, file_path: str) -> Tuple[bool, str]:
        """
        识别音频文件
        
        Args:
            file_path: 音频文件路径
            
        Returns:
            (是否成功, 识别结果文本)
        """
        try:
            with open(file_path, 'rb') as f:
                audio_data = f.read()
            
            # 从文件名推断格式
            format = Path(file_path).suffix[1:]  # 去掉点号
            
            return self.recognize(audio_data, format)
            
        except Exception as e:
            logger.error(f"读取音频文件失败: {str(e)}")
            return False, ''
    
    def is_silence(self, audio_data: bytes, threshold: float = 0.1) -> bool:
        """
        检测是否为静音
        
        Args:
            audio_data: 音频数据
            threshold: 静音阈值
            
        Returns:
            是否为静音
        """
        # TODO: 实现静音检测算法
        return False

# 创建全局 ASR 服务实例
asr_service = ASRService()

# 导出
__all__ = ['asr_service', 'ASRService']