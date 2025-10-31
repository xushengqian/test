# -*- coding: utf-8 -*-
"""
配置文件
"""

# FreeSWITCH配置
FREESWITCH_CONFIG = {
    "host": "127.0.0.1",
    "port": 8021,
    "password": "ClueCon",  # 修改为你的FreeSWITCH密码
    "esl_timeout": 10,
}

# 外呼配置
OUTBOUND_CONFIG = {
    "caller_id": "1000",  # 主叫号码
    "gateway": "your_gateway",  # SIP网关名称
    "human_agent_number": "1002",  # 默认人工坐席号码
}

# 意图识别配置
INTENT_CONFIG = {
    # 转人工关键词
    "transfer_keywords": [
        "转人工", "人工服务", "人工客服", "转接人工",
        "我要人工", "找人工", "人工坐席", "人工台",
        "转接客服", "找客服", "人工", "客服"
    ],
    # 转人工模式（可以使用AI模型进行更精确的识别）
    "use_ai_model": False,  # 是否使用AI模型进行意图识别
    "ai_model_endpoint": "",  # AI模型API端点
    "ai_model_api_key": "",  # AI模型API密钥
}

# 语音识别配置
ASR_CONFIG = {
    # 本地语音识别引擎
    "engine": "pocketsphinx",  # 可选: pocketsphinx, vosk, google, baidu, etc.
    
    # 如果使用云端ASR
    "use_cloud_asr": False,
    "cloud_asr_provider": "baidu",  # baidu, google, aliyun, etc.
    "cloud_asr_api_key": "",
    "cloud_asr_secret_key": "",
}

# 日志配置
LOG_CONFIG = {
    "level": "INFO",  # DEBUG, INFO, WARNING, ERROR
    "file": "robot_call.log",
    "format": "%(asctime)s - %(name)s - %(levelname)s - %(message)s",
}

# 呼叫超时配置
CALL_TIMEOUT_CONFIG = {
    "max_call_duration": 300,  # 最大通话时长（秒）
    "max_interactions": 10,  # 最大交互次数
    "recording_duration": 5000,  # 单次录音时长（毫秒）
    "response_timeout": 10000,  # 响应超时（毫秒）
}
