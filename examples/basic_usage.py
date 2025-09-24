"""Basic usage example of the metric scheduler."""
import asyncio
from metric_scheduler.config import Config, MetricConfig, DatabaseConfig
from metric_scheduler.app import MetricSchedulerApp


async def basic_example():
    """Basic example of using the metric scheduler."""
    
    # Create configuration
    config = Config(
        database=DatabaseConfig(
            host="localhost",
            port=3306,
            username="metrics_user",
            password="metrics_password",
            database="metrics_db",
            pool_size=20,
            max_overflow=10
        ),
        rate_limiter={
            "max_queries_per_second": 50,
            "max_queries_per_minute": 2000,
            "max_concurrent_queries": 30
        }
    )
    
    # Define metrics
    config.metrics = [
        MetricConfig(
            name="user_activity",
            query="""
                SELECT 
                    DATE(activity_time) as date,
                    COUNT(DISTINCT user_id) as active_users,
                    COUNT(*) as total_activities
                FROM user_activities
                WHERE activity_time >= NOW() - INTERVAL 7 DAY
                GROUP BY DATE(activity_time)
            """,
            schedule="0 2 * * *",  # Daily at 2 AM
            priority=7,
            timeout=120,
            retry_count=3
        ),
        
        MetricConfig(
            name="real_time_orders",
            query="""
                SELECT 
                    COUNT(*) as order_count,
                    SUM(total_amount) as revenue,
                    AVG(total_amount) as avg_order_value
                FROM orders
                WHERE created_at >= NOW() - INTERVAL 5 MINUTE
            """,
            schedule="*/5 * * * *",  # Every 5 minutes
            priority=9,
            timeout=30
        ),
        
        MetricConfig(
            name="inventory_alerts",
            query="""
                SELECT 
                    product_id,
                    product_name,
                    current_stock,
                    reorder_level
                FROM products
                WHERE current_stock <= reorder_level
                AND is_active = 1
            """,
            schedule="0 */4 * * *",  # Every 4 hours
            priority=6,
            timeout=60
        )
    ]
    
    # Create and start application
    app = MetricSchedulerApp(config)
    
    try:
        await app.initialize()
        
        # Schedule metrics
        for metric in config.metrics:
            await app.schedule_metric(metric)
        
        # Execute a metric immediately
        await app.execute_metric_now(config.metrics[0])
        
        # Get application status
        status = app.get_status()
        print(f"Application Status: {status}")
        
        # Run for a while
        await asyncio.sleep(300)  # 5 minutes
        
    finally:
        await app.shutdown()


if __name__ == "__main__":
    asyncio.run(basic_example())