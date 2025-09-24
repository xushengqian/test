"""Example with multiple databases and dynamic metric scheduling."""
import asyncio
from metric_scheduler.config import Config, MetricConfig, DatabaseConfig
from metric_scheduler.app import MetricSchedulerApp
from metric_scheduler.executor import MetricResult


async def handle_metric_result(result: MetricResult):
    """Custom result handler."""
    print(f"Metric: {result.metric_name}")
    print(f"Success: {result.success}")
    print(f"Execution Time: {result.execution_time:.2f}s")
    
    if result.success:
        print(f"Data: {result.data}")
    else:
        print(f"Error: {result.error}")
    
    print("-" * 50)


async def multi_database_example():
    """Example with multiple databases."""
    
    # Base configuration
    config = Config()
    
    # Create application
    app = MetricSchedulerApp(config)
    await app.initialize()
    
    # Add result handler
    app.scheduler.add_result_handler(handle_metric_result)
    
    # Add multiple database pools
    await app.add_database_pool(
        "analytics_db",
        DatabaseConfig(
            host="analytics.db.example.com",
            port=3306,
            username="analytics_user",
            password="analytics_pass",
            database="analytics",
            pool_size=30,
            max_overflow=20
        ),
        "mysql"
    )
    
    await app.add_database_pool(
        "reporting_db",
        DatabaseConfig(
            host="reporting.db.example.com",
            port=5432,
            username="reporting_user",
            password="reporting_pass",
            database="reporting",
            pool_size=20,
            max_overflow=10
        ),
        "postgresql"
    )
    
    # Schedule metrics for different databases
    
    # Analytics metrics (MySQL)
    analytics_metrics = [
        MetricConfig(
            name="page_views_summary",
            query="""
                SELECT 
                    page_path,
                    COUNT(*) as views,
                    COUNT(DISTINCT session_id) as unique_sessions,
                    AVG(time_on_page) as avg_time
                FROM page_views
                WHERE timestamp >= NOW() - INTERVAL 1 HOUR
                GROUP BY page_path
                ORDER BY views DESC
                LIMIT 100
            """,
            schedule="*/10 * * * *",  # Every 10 minutes
            priority=8
        ),
        
        MetricConfig(
            name="conversion_funnel",
            query="""
                SELECT 
                    funnel_step,
                    COUNT(DISTINCT user_id) as users,
                    COUNT(*) as events
                FROM funnel_events
                WHERE timestamp >= NOW() - INTERVAL 1 DAY
                GROUP BY funnel_step
                ORDER BY funnel_step
            """,
            schedule="0 * * * *",  # Every hour
            priority=7
        )
    ]
    
    # Reporting metrics (PostgreSQL)
    reporting_metrics = [
        MetricConfig(
            name="financial_summary",
            query="""
                SELECT 
                    DATE_TRUNC('day', created_at) as date,
                    SUM(revenue) as total_revenue,
                    SUM(cost) as total_cost,
                    SUM(revenue - cost) as profit,
                    COUNT(*) as transaction_count
                FROM financial_transactions
                WHERE created_at >= CURRENT_DATE - INTERVAL '7 days'
                GROUP BY DATE_TRUNC('day', created_at)
                ORDER BY date DESC
            """,
            schedule="0 6 * * *",  # Daily at 6 AM
            priority=9,
            timeout=180
        ),
        
        MetricConfig(
            name="customer_segments",
            query="""
                WITH customer_stats AS (
                    SELECT 
                        customer_id,
                        SUM(order_value) as total_spent,
                        COUNT(*) as order_count,
                        MAX(order_date) as last_order_date
                    FROM orders
                    WHERE order_date >= CURRENT_DATE - INTERVAL '90 days'
                    GROUP BY customer_id
                )
                SELECT 
                    CASE 
                        WHEN total_spent >= 1000 THEN 'VIP'
                        WHEN total_spent >= 500 THEN 'Premium'
                        WHEN total_spent >= 100 THEN 'Regular'
                        ELSE 'New'
                    END as segment,
                    COUNT(*) as customer_count,
                    AVG(total_spent) as avg_spent,
                    AVG(order_count) as avg_orders
                FROM customer_stats
                GROUP BY segment
            """,
            schedule="0 3 * * *",  # Daily at 3 AM
            priority=6,
            timeout=120
        )
    ]
    
    # Schedule all metrics
    for metric in analytics_metrics:
        await app.schedule_metric(metric, "analytics_db")
    
    for metric in reporting_metrics:
        await app.schedule_metric(metric, "reporting_db")
    
    print("All metrics scheduled successfully!")
    
    # Get current status
    status = app.get_status()
    print(f"\nScheduled Jobs: {len(status['scheduler']['jobs'])}")
    for job in status['scheduler']['jobs']:
        print(f"  - {job['name']} (Next run: {job['next_run_time']})")
    
    # Run for demonstration
    await asyncio.sleep(600)  # 10 minutes
    
    await app.shutdown()


async def dynamic_scheduling_example():
    """Example of dynamic metric scheduling based on conditions."""
    
    config = Config()
    app = MetricSchedulerApp(config)
    await app.initialize()
    
    # Base metric that determines what other metrics to run
    health_check = MetricConfig(
        name="system_health_check",
        query="""
            SELECT 
                'database' as component,
                CASE 
                    WHEN COUNT(*) > 0 THEN 'healthy'
                    ELSE 'unhealthy'
                END as status,
                COUNT(*) as active_connections
            FROM information_schema.processlist
            WHERE command != 'Sleep'
        """,
        schedule="* * * * *",  # Every minute
        priority=10,
        timeout=5
    )
    
    # Schedule the health check
    await app.schedule_metric(health_check)
    
    # Custom result handler that schedules additional metrics based on results
    async def adaptive_scheduler(result: MetricResult):
        if result.metric_name == "system_health_check" and result.success:
            # Check the health status
            if result.data and result.data[0]['status'] == 'healthy':
                connections = result.data[0]['active_connections']
                
                # If system is under low load, run heavy metrics
                if connections < 50:
                    heavy_metric = MetricConfig(
                        name="heavy_analytics_query",
                        query="""
                            SELECT /* Heavy query */
                                category,
                                subcategory,
                                SUM(sales) as total_sales,
                                COUNT(DISTINCT customer_id) as unique_customers
                            FROM sales_data
                            WHERE sale_date >= NOW() - INTERVAL 30 DAY
                            GROUP BY category, subcategory
                            WITH ROLLUP
                        """,
                        schedule="",  # One-time execution
                        priority=3,
                        timeout=300
                    )
                    
                    # Execute immediately
                    await app.execute_metric_now(heavy_metric)
                    print(f"Triggered heavy metric due to low load ({connections} connections)")
    
    app.scheduler.add_result_handler(adaptive_scheduler)
    
    # Run for demonstration
    await asyncio.sleep(300)  # 5 minutes
    
    await app.shutdown()


if __name__ == "__main__":
    # Choose which example to run
    example = "multi_database"  # or "dynamic"
    
    if example == "multi_database":
        asyncio.run(multi_database_example())
    else:
        asyncio.run(dynamic_scheduling_example())