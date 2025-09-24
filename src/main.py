"""
主程序入口
"""
import asyncio
import signal
import sys
from pathlib import Path

# 添加项目根目录到Python路径
sys.path.insert(0, str(Path(__file__).parent.parent))

from src.config import get_config
from src.database import init_database, close_database
from src.logging_config import init_logging
from src.scheduler import scheduler
from src.executor import executor_manager
from src.monitoring import metrics_collector, alert_manager, performance_monitor
from src.utils import generate_node_id

# 初始化日志
logger = init_logging()


class MetricSchedulerApp:
    """指标调度器应用主类"""
    
    def __init__(self):
        self.config = get_config()
        self.running = False
        self.shutdown_event = asyncio.Event()
    
    async def start(self):
        """启动应用"""
        logger.info("Starting Metric Scheduler Application...")
        
        try:
            # 初始化数据库
            logger.info("Initializing database...")
            init_database()
            
            # 启动监控系统
            logger.info("Starting monitoring system...")
            await metrics_collector.start()
            await alert_manager.start()
            await performance_monitor.start()
            
            # 启动调度器
            if self.config.scheduler.enabled:
                logger.info("Starting scheduler...")
                await scheduler.start()
            else:
                logger.info("Scheduler is disabled in configuration")
            
            # 启动执行器
            logger.info("Starting executor...")
            node_id = self.config.node_id or generate_node_id()
            await executor_manager.start_executor(node_id)
            
            self.running = True
            logger.info("Metric Scheduler Application started successfully")
            
            # 等待关闭信号
            await self.shutdown_event.wait()
            
        except Exception as e:
            logger.error(f"Failed to start application: {e}")
            raise
    
    async def stop(self):
        """停止应用"""
        if not self.running:
            return
        
        logger.info("Stopping Metric Scheduler Application...")
        
        try:
            # 停止调度器
            logger.info("Stopping scheduler...")
            await scheduler.stop()
            
            # 停止执行器
            logger.info("Stopping executors...")
            await executor_manager.stop_all_executors()
            
            # 停止监控系统
            logger.info("Stopping monitoring system...")
            await metrics_collector.stop()
            await alert_manager.stop()
            await performance_monitor.stop()
            
            # 关闭数据库连接
            logger.info("Closing database connections...")
            close_database()
            
            self.running = False
            logger.info("Metric Scheduler Application stopped successfully")
            
        except Exception as e:
            logger.error(f"Error during application shutdown: {e}")
        
        finally:
            self.shutdown_event.set()
    
    def handle_signal(self, signum, frame):
        """处理系统信号"""
        logger.info(f"Received signal {signum}, initiating shutdown...")
        asyncio.create_task(self.stop())


async def main():
    """主函数"""
    app = MetricSchedulerApp()
    
    # 注册信号处理器
    for sig in [signal.SIGTERM, signal.SIGINT]:
        signal.signal(sig, app.handle_signal)
    
    try:
        await app.start()
    except KeyboardInterrupt:
        logger.info("Received keyboard interrupt")
    except Exception as e:
        logger.error(f"Application error: {e}")
        sys.exit(1)
    finally:
        await app.stop()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        logger.info("Application interrupted by user")
    except Exception as e:
        logger.error(f"Fatal error: {e}")
        sys.exit(1)