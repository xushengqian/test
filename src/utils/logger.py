"""
日志工具模块
"""
import sys
from pathlib import Path
from loguru import logger
import yaml

# 加载配置
config_path = Path(__file__).parent.parent.parent / "config" / "config.yaml"
with open(config_path, 'r', encoding='utf-8') as f:
    config = yaml.safe_load(f)

# 日志配置
log_config = config.get('logging', {})
log_level = log_config.get('level', 'INFO')
log_file = log_config.get('file', 'logs/outbound_bot.log')
rotation = log_config.get('rotation', '1 day')
retention = log_config.get('retention', '30 days')

# 创建日志目录
log_path = Path(log_file)
log_path.parent.mkdir(parents=True, exist_ok=True)

# 移除默认的 handler
logger.remove()

# 添加控制台输出
logger.add(
    sys.stdout,
    level=log_level,
    format="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> - <level>{message}</level>",
    colorize=True
)

# 添加文件输出
logger.add(
    log_file,
    level=log_level,
    format="{time:YYYY-MM-DD HH:mm:ss} | {level: <8} | {name}:{function}:{line} - {message}",
    rotation=rotation,
    retention=retention,
    encoding='utf-8',
    backtrace=True,
    diagnose=True
)

def get_logger(name: str = None):
    """
    获取日志实例
    
    Args:
        name: 模块名称
        
    Returns:
        logger instance
    """
    if name:
        return logger.bind(name=name)
    return logger

# 导出
__all__ = ['logger', 'get_logger']