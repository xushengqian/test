"""
并行指标调度器 - 主程序入口
"""
import sys
import signal
from loguru import logger

from config.config import config
from src.models import init_db
from src.parallel_scheduler import ParallelMetricScheduler


def setup_logger():
    """配置日志"""
    logger.remove()  # 移除默认handler
    
    # 控制台输出
    logger.add(
        sys.stdout,
        format="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> - <level>{message}</level>",
        level=config.scheduler.log_level,
        colorize=True
    )
    
    # 文件输出
    logger.add(
        config.scheduler.log_file,
        format="{time:YYYY-MM-DD HH:mm:ss} | {level: <8} | {name}:{function}:{line} - {message}",
        level=config.scheduler.log_level,
        rotation="100 MB",
        retention="30 days",
        compression="zip",
        encoding="utf-8"
    )


def main():
    """主函数"""
    # 配置日志
    setup_logger()
    
    logger.info("=" * 80)
    logger.info("🚀 并行指标调度系统启动")
    logger.info("=" * 80)
    
    # 初始化数据库
    try:
        logger.info("初始化数据库...")
        init_db()
        logger.info("✓ 数据库初始化完成")
    except Exception as e:
        logger.error(f"❌ 数据库初始化失败: {e}")
        sys.exit(1)
    
    # 创建并启动调度器
    scheduler = ParallelMetricScheduler()
    
    # 设置信号处理
    def signal_handler(signum, frame):
        logger.info(f"接收到信号 {signum}，准备停止...")
        scheduler.stop()
        sys.exit(0)
    
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    try:
        # 启动调度器（阻塞）
        scheduler.start()
    except Exception as e:
        logger.error(f"❌ 调度器运行异常: {e}", exc_info=True)
        sys.exit(1)


if __name__ == '__main__':
    main()
