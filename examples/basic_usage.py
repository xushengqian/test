"""
基本使用示例
演示如何使用指标调度系统的基本功能
"""
import asyncio
import sys
from pathlib import Path

# 添加项目路径
sys.path.insert(0, str(Path(__file__).parent.parent))

from src.config import get_config
from src.database import init_database, DatabaseService
from src.models import MetricDefinition, ScheduleConfig, DataSource
from src.scheduler import scheduler
from src.executor import executor_manager
from src.logging_config import init_logging

# 初始化日志
logger = init_logging()


async def setup_example_data():
    """设置示例数据"""
    logger.info("Setting up example data...")
    
    with DatabaseService() as db:
        # 创建示例数据源
        datasource = DataSource(
            name="example_mysql",
            type="mysql",
            host="localhost",
            port=3306,
            database_name="test_db",
            username="test_user",
            password="test_password",
            max_connections=5,
            is_active=True
        )
        
        # 检查是否已存在
        existing_ds = db.query(DataSource).filter(
            DataSource.name == datasource.name
        ).first()
        
        if not existing_ds:
            db.add(datasource)
            logger.info(f"Created data source: {datasource.name}")
        else:
            datasource = existing_ds
            logger.info(f"Using existing data source: {datasource.name}")
        
        # 创建示例指标定义
        metrics = [
            {
                "name": "daily_user_count",
                "description": "每日用户数量统计",
                "sql_template": """
                    SELECT 
                        DATE(created_at) as date,
                        COUNT(DISTINCT user_id) as user_count
                    FROM users 
                    WHERE created_at >= '{{ start_date }}' 
                      AND created_at < '{{ end_date }}'
                    GROUP BY DATE(created_at)
                    ORDER BY date DESC
                """,
                "data_source": datasource.name,
                "category": "user_metrics",
                "tags": {"department": "analytics", "priority": "high"},
                "timeout_seconds": 300,
                "retry_count": 3
            },
            {
                "name": "hourly_sales_revenue",
                "description": "每小时销售收入统计",
                "sql_template": """
                    SELECT 
                        DATE_FORMAT(order_time, '%Y-%m-%d %H:00:00') as hour,
                        SUM(amount) as revenue,
                        COUNT(*) as order_count
                    FROM orders 
                    WHERE order_time >= '{{ start_time }}' 
                      AND order_time < '{{ end_time }}'
                      AND status = 'completed'
                    GROUP BY DATE_FORMAT(order_time, '%Y-%m-%d %H:00:00')
                    ORDER BY hour DESC
                """,
                "data_source": datasource.name,
                "category": "sales_metrics",
                "tags": {"department": "sales", "priority": "medium"},
                "timeout_seconds": 180,
                "retry_count": 2
            },
            {
                "name": "system_performance_check",
                "description": "系统性能检查",
                "sql_template": """
                    SELECT 
                        'database_connections' as metric_name,
                        COUNT(*) as value
                    FROM information_schema.processlist
                    UNION ALL
                    SELECT 
                        'table_count' as metric_name,
                        COUNT(*) as value
                    FROM information_schema.tables
                    WHERE table_schema = DATABASE()
                """,
                "data_source": datasource.name,
                "category": "system_metrics",
                "tags": {"department": "ops", "priority": "low"},
                "timeout_seconds": 60,
                "retry_count": 1
            }
        ]
        
        created_metrics = []
        for metric_data in metrics:
            existing_metric = db.query(MetricDefinition).filter(
                MetricDefinition.name == metric_data["name"]
            ).first()
            
            if not existing_metric:
                metric = MetricDefinition(**metric_data)
                db.add(metric)
                db.commit()
                db.refresh(metric)
                created_metrics.append(metric)
                logger.info(f"Created metric: {metric.name}")
            else:
                created_metrics.append(existing_metric)
                logger.info(f"Using existing metric: {existing_metric.name}")
        
        # 创建示例调度配置
        schedules = [
            {
                "metric_id": created_metrics[0].id,  # daily_user_count
                "cron_expression": "0 1 * * *",  # 每天凌晨1点执行
                "priority": 1,
                "max_concurrent": 1,
                "is_enabled": True,
                "parameters": {
                    "start_date": "{{ yesterday }}",
                    "end_date": "{{ today }}"
                }
            },
            {
                "metric_id": created_metrics[1].id,  # hourly_sales_revenue
                "cron_expression": "0 * * * *",  # 每小时执行
                "priority": 2,
                "max_concurrent": 2,
                "is_enabled": True,
                "parameters": {
                    "start_time": "{{ hour_ago }}",
                    "end_time": "{{ current_hour }}"
                }
            },
            {
                "metric_id": created_metrics[2].id,  # system_performance_check
                "cron_expression": "*/5 * * * *",  # 每5分钟执行
                "priority": 5,
                "max_concurrent": 1,
                "is_enabled": True,
                "parameters": {}
            }
        ]
        
        for schedule_data in schedules:
            existing_schedule = db.query(ScheduleConfig).filter(
                ScheduleConfig.metric_id == schedule_data["metric_id"]
            ).first()
            
            if not existing_schedule:
                schedule = ScheduleConfig(**schedule_data)
                db.add(schedule)
                logger.info(f"Created schedule for metric ID: {schedule_data['metric_id']}")
            else:
                logger.info(f"Using existing schedule for metric ID: {schedule_data['metric_id']}")
        
        db.commit()
        logger.info("Example data setup completed")


async def demonstrate_manual_task_submission():
    """演示手动任务提交"""
    logger.info("Demonstrating manual task submission...")
    
    with DatabaseService() as db:
        # 获取第一个指标
        metric = db.query(MetricDefinition).first()
        
        if metric:
            # 提交手动任务
            task_id = await scheduler.submit_task(
                metric_id=metric.id,
                parameters={
                    "start_date": "2024-01-01",
                    "end_date": "2024-01-02"
                }
            )
            
            logger.info(f"Submitted manual task: {task_id} for metric: {metric.name}")
            
            # 等待一段时间让任务执行
            await asyncio.sleep(5)
            
            # 检查任务状态
            from src.models import ExecutionTask
            task = db.query(ExecutionTask).filter(
                ExecutionTask.task_id == task_id
            ).first()
            
            if task:
                logger.info(f"Task {task_id} status: {task.status}")
                if task.result_data:
                    logger.info(f"Task result: {task.result_data}")
            else:
                logger.warning(f"Task {task_id} not found")
        else:
            logger.warning("No metrics found for manual task submission")


async def demonstrate_scheduler_status():
    """演示调度器状态查询"""
    logger.info("Demonstrating scheduler status query...")
    
    status = scheduler.get_scheduler_status()
    logger.info(f"Scheduler status: {status}")


async def main():
    """主函数"""
    logger.info("Starting basic usage example...")
    
    try:
        # 初始化数据库
        init_database()
        
        # 设置示例数据
        await setup_example_data()
        
        # 启动调度器
        await scheduler.start()
        
        # 启动执行器
        node_id = get_config().node_id
        await executor_manager.start_executor(node_id)
        
        # 演示功能
        await demonstrate_scheduler_status()
        await demonstrate_manual_task_submission()
        
        # 运行一段时间观察调度器工作
        logger.info("Running scheduler for 30 seconds...")
        await asyncio.sleep(30)
        
        # 再次检查状态
        await demonstrate_scheduler_status()
        
    except Exception as e:
        logger.error(f"Example execution failed: {e}")
        raise
    
    finally:
        # 清理资源
        logger.info("Cleaning up resources...")
        await scheduler.stop()
        await executor_manager.stop_all_executors()
        
        logger.info("Basic usage example completed")


if __name__ == "__main__":
    asyncio.run(main())