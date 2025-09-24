"""Example with custom alert handlers and result processors."""
import asyncio
import json
from datetime import datetime
from typing import Dict, Any, List
from metric_scheduler.config import Config, MetricConfig
from metric_scheduler.app import MetricSchedulerApp
from metric_scheduler.monitoring import Alert, AlertHandler
from metric_scheduler.executor import MetricResult


class SlackAlertHandler(AlertHandler):
    """Send alerts to Slack channel."""
    
    def __init__(self, webhook_url: str):
        self.webhook_url = webhook_url
    
    async def handle_alert(self, alert: Alert):
        """Send alert to Slack."""
        # In real implementation, use aiohttp to POST to webhook
        emoji = {
            'info': ':information_source:',
            'warning': ':warning:',
            'error': ':x:',
            'critical': ':rotating_light:'
        }.get(alert.severity, ':bell:')
        
        message = {
            'text': f"{emoji} *{alert.alert_type}*",
            'attachments': [{
                'color': {
                    'info': '#36a64f',
                    'warning': '#ff9800',
                    'error': '#f44336',
                    'critical': '#d32f2f'
                }.get(alert.severity, '#808080'),
                'fields': [
                    {
                        'title': 'Message',
                        'value': alert.message,
                        'short': False
                    },
                    {
                        'title': 'Time',
                        'value': alert.timestamp.strftime('%Y-%m-%d %H:%M:%S UTC'),
                        'short': True
                    },
                    {
                        'title': 'Severity',
                        'value': alert.severity.upper(),
                        'short': True
                    }
                ]
            }]
        }
        
        print(f"[SLACK ALERT] Would send to {self.webhook_url}: {json.dumps(message, indent=2)}")


class DataQualityChecker:
    """Check data quality and trigger alerts."""
    
    def __init__(self, app: MetricSchedulerApp):
        self.app = app
        self.quality_rules = {
            'user_activity': self._check_user_activity,
            'order_stats': self._check_order_stats,
            'inventory_levels': self._check_inventory
        }
    
    async def check_result(self, result: MetricResult):
        """Check result data quality."""
        if result.success and result.metric_name in self.quality_rules:
            checker = self.quality_rules[result.metric_name]
            issues = await checker(result.data)
            
            if issues:
                # Trigger data quality alert
                alert = Alert(
                    alert_type='data_quality',
                    severity='warning',
                    message=f"Data quality issues in {result.metric_name}: {', '.join(issues)}",
                    metadata={
                        'metric_name': result.metric_name,
                        'issues': issues,
                        'row_count': len(result.data) if result.data else 0
                    }
                )
                
                # Send to alert handlers
                if self.app.monitor:
                    for handler in self.app.monitor.alert_handlers:
                        await handler(alert)
    
    async def _check_user_activity(self, data: List[Dict[str, Any]]) -> List[str]:
        """Check user activity data quality."""
        issues = []
        
        if not data:
            issues.append("No data returned")
            return issues
        
        # Check for anomalies
        for row in data:
            if row.get('active_users', 0) < 0:
                issues.append("Negative user count detected")
            
            if row.get('total_activities', 0) < row.get('active_users', 0):
                issues.append("Activities less than active users")
        
        # Check for missing dates
        if len(data) < 7:
            issues.append(f"Expected 7 days of data, got {len(data)}")
        
        return issues
    
    async def _check_order_stats(self, data: List[Dict[str, Any]]) -> List[str]:
        """Check order statistics data quality."""
        issues = []
        
        if not data:
            issues.append("No order data in last 5 minutes")
            return issues
        
        row = data[0]
        if row.get('revenue', 0) < 0:
            issues.append("Negative revenue detected")
        
        if row.get('order_count', 0) > 0 and row.get('avg_order_value', 0) <= 0:
            issues.append("Invalid average order value")
        
        return issues
    
    async def _check_inventory(self, data: List[Dict[str, Any]]) -> List[str]:
        """Check inventory data quality."""
        issues = []
        
        # Check for critical stock levels
        critical_items = [row for row in data if row.get('current_stock', 0) < 0]
        if critical_items:
            issues.append(f"{len(critical_items)} items with negative stock")
        
        return issues


class ResultArchiver:
    """Archive metric results to long-term storage."""
    
    def __init__(self, archive_path: str = "/tmp/metric_archive"):
        self.archive_path = archive_path
        import os
        os.makedirs(archive_path, exist_ok=True)
    
    async def archive_result(self, result: MetricResult):
        """Archive result to file system."""
        if result.success and result.data:
            # Create daily archive files
            date_str = result.timestamp.strftime('%Y-%m-%d')
            file_path = f"{self.archive_path}/{result.metric_name}_{date_str}.jsonl"
            
            # Append result to file
            with open(file_path, 'a') as f:
                archive_entry = {
                    'timestamp': result.timestamp.isoformat(),
                    'metric_name': result.metric_name,
                    'execution_time': result.execution_time,
                    'row_count': len(result.data),
                    'data': result.data
                }
                f.write(json.dumps(archive_entry) + '\n')
            
            print(f"[ARCHIVE] Saved {result.metric_name} to {file_path}")


async def custom_handlers_example():
    """Example with custom handlers."""
    
    config = Config()
    app = MetricSchedulerApp(config)
    await app.initialize()
    
    # Add custom alert handlers
    if app.monitor:
        slack_handler = SlackAlertHandler("https://hooks.slack.com/services/YOUR/WEBHOOK/URL")
        app.monitor.add_alert_handler(slack_handler.handle_alert)
    
    # Create data quality checker
    quality_checker = DataQualityChecker(app)
    app.scheduler.add_result_handler(quality_checker.check_result)
    
    # Create result archiver
    archiver = ResultArchiver()
    app.scheduler.add_result_handler(archiver.archive_result)
    
    # Define metrics with potential data quality issues
    metrics = [
        MetricConfig(
            name="user_activity",
            query="""
                SELECT 
                    DATE(created_at) as date,
                    COUNT(DISTINCT user_id) as active_users,
                    COUNT(*) as total_activities
                FROM (
                    SELECT 
                        user_id,
                        created_at,
                        CASE WHEN RAND() < 0.1 THEN -1 ELSE 1 END as multiplier
                    FROM user_activities
                    WHERE created_at >= NOW() - INTERVAL 7 DAY
                ) t
                GROUP BY DATE(created_at)
            """,
            schedule="*/2 * * * *",  # Every 2 minutes for testing
            priority=8
        ),
        
        MetricConfig(
            name="order_stats",
            query="""
                SELECT 
                    COUNT(*) as order_count,
                    SUM(CASE WHEN RAND() < 0.05 THEN -100 ELSE total END) as revenue,
                    AVG(total) as avg_order_value
                FROM orders
                WHERE created_at >= NOW() - INTERVAL 5 MINUTE
            """,
            schedule="*/1 * * * *",  # Every minute for testing
            priority=9
        ),
        
        MetricConfig(
            name="inventory_levels",
            query="""
                SELECT 
                    product_id,
                    product_name,
                    CASE WHEN RAND() < 0.02 THEN -5 ELSE current_stock END as current_stock,
                    reorder_level
                FROM products
                WHERE is_active = 1
                LIMIT 100
            """,
            schedule="*/3 * * * *",  # Every 3 minutes
            priority=7
        )
    ]
    
    # Schedule all metrics
    for metric in metrics:
        await app.schedule_metric(metric)
    
    print("Custom handlers configured:")
    print("- Slack alert handler")
    print("- Data quality checker")
    print("- Result archiver")
    print("\nRunning for 10 minutes to demonstrate handlers...")
    
    # Run for demonstration
    await asyncio.sleep(600)  # 10 minutes
    
    await app.shutdown()


if __name__ == "__main__":
    asyncio.run(custom_handlers_example())