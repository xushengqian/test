"""Main application for the metric scheduler."""
import asyncio
import signal
import sys
from typing import Optional
import redis.asyncio as redis
from loguru import logger
from .config import Config, MetricConfig, DatabaseConfig
from .database import ConnectionPoolManager
from .rate_limiter import DistributedRateLimiter
from .scheduler import DistributedMetricScheduler
from .monitoring import MetricMonitor, LogAlertHandler
from .executor import MetricResult


class MetricSchedulerApp:
    """Main application class for metric scheduler."""
    
    def __init__(self, config: Config):
        self.config = config
        self.redis_client: Optional[redis.Redis] = None
        self.pool_manager: Optional[ConnectionPoolManager] = None
        self.rate_limiter: Optional[DistributedRateLimiter] = None
        self.scheduler: Optional[DistributedMetricScheduler] = None
        self.monitor: Optional[MetricMonitor] = None
        self.running = False
        
        # Configure logging
        logger.remove()
        logger.add(
            sys.stderr,
            level=config.monitoring.log_level,
            format="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> - <level>{message}</level>"
        )
    
    async def initialize(self):
        """Initialize all components."""
        logger.info("Initializing metric scheduler application...")
        
        # Initialize Redis client
        self.redis_client = redis.Redis(
            host=self.config.redis.host,
            port=self.config.redis.port,
            password=self.config.redis.password,
            db=self.config.redis.db,
            decode_responses=self.config.redis.decode_responses,
            max_connections=self.config.redis.max_connections
        )
        
        # Test Redis connection
        await self.redis_client.ping()
        logger.info("Redis connection established")
        
        # Initialize connection pool manager
        self.pool_manager = ConnectionPoolManager()
        
        # Add database pools
        await self.pool_manager.add_pool("default", self.config.database, "mysql")
        
        # Initialize rate limiter
        self.rate_limiter = DistributedRateLimiter(
            self.redis_client,
            self.config.rate_limiter
        )
        logger.info("Rate limiter initialized")
        
        # Initialize scheduler
        self.scheduler = DistributedMetricScheduler(
            self.config.scheduler,
            self.redis_client,
            self.pool_manager,
            self.rate_limiter
        )
        await self.scheduler.initialize()
        
        # Initialize monitoring
        if self.config.monitoring.enable_logging:
            self.monitor = MetricMonitor(self.config.monitoring, self.redis_client)
            self.monitor.add_alert_handler(LogAlertHandler().handle_alert)
            await self.monitor.start_monitoring()
        
        # Add result handler to update monitoring metrics
        self.scheduler.add_result_handler(self._handle_metric_result)
        
        logger.info("Metric scheduler application initialized successfully")
    
    async def _handle_metric_result(self, result: MetricResult):
        """Handle metric execution results for monitoring."""
        if self.monitor:
            self.monitor.record_metric_execution(
                result.metric_name,
                result.success,
                result.execution_time
            )
    
    async def start(self):
        """Start the application."""
        self.running = True
        logger.info("Starting metric scheduler application...")
        
        # Schedule all configured metrics
        if self.config.metrics:
            await self.scheduler.schedule_metrics_batch(self.config.metrics)
            logger.info(f"Scheduled {len(self.config.metrics)} metrics")
        
        # Setup signal handlers
        for sig in (signal.SIGTERM, signal.SIGINT):
            signal.signal(sig, lambda s, f: asyncio.create_task(self.shutdown()))
        
        logger.info("Metric scheduler application started")
        
        # Keep the application running
        while self.running:
            await asyncio.sleep(1)
    
    async def shutdown(self):
        """Gracefully shutdown the application."""
        logger.info("Shutting down metric scheduler application...")
        self.running = False
        
        # Stop monitoring
        if self.monitor:
            await self.monitor.stop_monitoring()
        
        # Shutdown scheduler
        if self.scheduler:
            await self.scheduler.shutdown()
        
        # Close all database pools
        if self.pool_manager:
            await self.pool_manager.close_all()
        
        # Close Redis connection
        if self.redis_client:
            await self.redis_client.close()
        
        logger.info("Metric scheduler application shutdown complete")
    
    async def add_database_pool(self, name: str, config: DatabaseConfig, db_type: str = "mysql"):
        """Add a new database pool dynamically."""
        await self.pool_manager.add_pool(name, config, db_type)
        logger.info(f"Added database pool: {name}")
    
    async def schedule_metric(self, metric: MetricConfig, pool_name: str = "default"):
        """Schedule a new metric dynamically."""
        await self.scheduler.schedule_metric(metric, pool_name)
    
    async def execute_metric_now(self, metric: MetricConfig, pool_name: str = "default"):
        """Execute a metric immediately."""
        await self.scheduler.execute_metric_now(metric, pool_name)
    
    def get_status(self) -> dict:
        """Get application status."""
        status = {
            'running': self.running,
            'config': self.config.to_dict()
        }
        
        if self.scheduler:
            status['scheduler'] = {
                'metrics': self.scheduler.get_metrics(),
                'jobs': self.scheduler.get_scheduled_jobs()
            }
        
        if self.pool_manager:
            status['database_pools'] = self.pool_manager.get_all_status()
        
        if self.rate_limiter:
            status['rate_limiter'] = self.rate_limiter.get_metrics()
        
        return status


async def main():
    """Main entry point."""
    # Load configuration from environment or default
    config = Config.from_env()
    
    # Add some example metrics if none configured
    if not config.metrics:
        config.metrics = [
            MetricConfig(
                name="active_users",
                query="SELECT COUNT(*) as count FROM users WHERE last_login > NOW() - INTERVAL 1 DAY",
                schedule="*/5 * * * *",  # Every 5 minutes
                priority=8,
                timeout=30
            ),
            MetricConfig(
                name="order_stats",
                query="SELECT COUNT(*) as total_orders, SUM(amount) as total_revenue FROM orders WHERE created_at > NOW() - INTERVAL 1 HOUR",
                schedule="0 * * * *",  # Every hour
                priority=5,
                timeout=60
            ),
            MetricConfig(
                name="system_health",
                query="SELECT 1 as healthy",
                schedule="* * * * *",  # Every minute
                priority=10,
                timeout=10
            )
        ]
    
    # Create and start application
    app = MetricSchedulerApp(config)
    
    try:
        await app.initialize()
        await app.start()
    except KeyboardInterrupt:
        logger.info("Received interrupt signal")
    except Exception as e:
        logger.error(f"Application error: {e}")
    finally:
        await app.shutdown()


if __name__ == "__main__":
    asyncio.run(main())