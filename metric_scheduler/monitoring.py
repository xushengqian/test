"""Monitoring and alerting system for metric scheduler."""
import asyncio
import psutil
import time
from typing import Dict, Any, List, Optional, Callable
from datetime import datetime, timedelta
from prometheus_client import Counter, Gauge, Histogram, Summary, start_http_server
from loguru import logger
import redis.asyncio as redis
from .config import MonitoringConfig


# Prometheus metrics
METRIC_EXECUTIONS = Counter('metric_executions_total', 'Total number of metric executions', ['metric_name', 'status'])
METRIC_EXECUTION_TIME = Histogram('metric_execution_duration_seconds', 'Metric execution duration', ['metric_name'])
DB_CONNECTIONS = Gauge('database_connections', 'Number of database connections', ['pool_name', 'state'])
RATE_LIMIT_REJECTIONS = Counter('rate_limit_rejections_total', 'Total number of rate limit rejections')
QUEUE_SIZE = Gauge('metric_queue_size', 'Current size of metric execution queue', ['pool_name'])
SYSTEM_CPU = Gauge('system_cpu_percent', 'System CPU usage percentage')
SYSTEM_MEMORY = Gauge('system_memory_percent', 'System memory usage percentage')
ALERT_TRIGGERED = Counter('alerts_triggered_total', 'Total number of alerts triggered', ['alert_type'])


class Alert:
    """Container for alert information."""
    
    def __init__(self, alert_type: str, severity: str, message: str, 
                 metadata: Optional[Dict[str, Any]] = None):
        self.alert_type = alert_type
        self.severity = severity  # 'info', 'warning', 'error', 'critical'
        self.message = message
        self.metadata = metadata or {}
        self.timestamp = datetime.utcnow()
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            'alert_type': self.alert_type,
            'severity': self.severity,
            'message': self.message,
            'metadata': self.metadata,
            'timestamp': self.timestamp.isoformat()
        }


class MetricMonitor:
    """Monitors system metrics and triggers alerts."""
    
    def __init__(self, config: MonitoringConfig, redis_client: redis.Redis):
        self.config = config
        self.redis_client = redis_client
        self.alert_handlers: List[Callable] = []
        self.monitoring_task: Optional[asyncio.Task] = None
        
        # Alert state tracking
        self.alert_state = {
            'query_error_rate': {'triggered': False, 'last_value': 0.0},
            'query_latency_p99': {'triggered': False, 'last_value': 0.0},
            'db_connection_usage': {'triggered': False, 'last_value': 0.0},
            'queue_backlog': {'triggered': False, 'last_value': 0}
        }
        
        # Metrics aggregation
        self.metrics_buffer = {
            'executions': [],
            'errors': [],
            'latencies': []
        }
        
        if self.config.enable_prometheus:
            self._start_prometheus_server()
    
    def _start_prometheus_server(self):
        """Start Prometheus metrics server."""
        try:
            start_http_server(self.config.prometheus_port)
            logger.info(f"Prometheus metrics server started on port {self.config.prometheus_port}")
        except Exception as e:
            logger.error(f"Failed to start Prometheus server: {e}")
    
    def add_alert_handler(self, handler: Callable):
        """Add an alert handler."""
        self.alert_handlers.append(handler)
    
    async def start_monitoring(self):
        """Start the monitoring loop."""
        if self.monitoring_task:
            logger.warning("Monitoring already started")
            return
        
        self.monitoring_task = asyncio.create_task(self._monitoring_loop())
        logger.info("Metric monitoring started")
    
    async def stop_monitoring(self):
        """Stop the monitoring loop."""
        if self.monitoring_task:
            self.monitoring_task.cancel()
            try:
                await self.monitoring_task
            except asyncio.CancelledError:
                pass
            self.monitoring_task = None
            logger.info("Metric monitoring stopped")
    
    async def _monitoring_loop(self):
        """Main monitoring loop."""
        while True:
            try:
                # Collect system metrics
                await self._collect_system_metrics()
                
                # Check alert conditions
                await self._check_alerts()
                
                # Clean up old metrics
                await self._cleanup_old_metrics()
                
                # Sleep for monitoring interval
                await asyncio.sleep(10)  # Check every 10 seconds
                
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Monitoring loop error: {e}")
                await asyncio.sleep(10)
    
    async def _collect_system_metrics(self):
        """Collect system resource metrics."""
        # CPU usage
        cpu_percent = psutil.cpu_percent(interval=1)
        SYSTEM_CPU.set(cpu_percent)
        
        # Memory usage
        memory = psutil.virtual_memory()
        SYSTEM_MEMORY.set(memory.percent)
        
        # Store in Redis for historical tracking
        timestamp = int(time.time())
        await self.redis_client.zadd(
            'system:cpu_usage',
            {f"{timestamp}:{cpu_percent}": timestamp}
        )
        await self.redis_client.zadd(
            'system:memory_usage',
            {f"{timestamp}:{memory.percent}": timestamp}
        )
        
        # Expire old entries (keep 24 hours)
        expire_time = timestamp - 86400
        await self.redis_client.zremrangebyscore('system:cpu_usage', 0, expire_time)
        await self.redis_client.zremrangebyscore('system:memory_usage', 0, expire_time)
    
    async def _check_alerts(self):
        """Check alert conditions based on thresholds."""
        # Get current metrics from Redis
        metrics = await self._get_current_metrics()
        
        # Check query error rate
        error_rate = metrics.get('error_rate', 0.0)
        if error_rate > self.config.alert_thresholds['query_error_rate']:
            await self._trigger_alert(
                'query_error_rate',
                f"Query error rate {error_rate:.2%} exceeds threshold {self.config.alert_thresholds['query_error_rate']:.2%}",
                'error',
                {'error_rate': error_rate}
            )
        else:
            self._clear_alert('query_error_rate')
        
        # Check query latency
        latency_p99 = metrics.get('latency_p99', 0.0)
        if latency_p99 > self.config.alert_thresholds['query_latency_p99']:
            await self._trigger_alert(
                'query_latency_p99',
                f"Query latency P99 {latency_p99:.2f}s exceeds threshold {self.config.alert_thresholds['query_latency_p99']}s",
                'warning',
                {'latency_p99': latency_p99}
            )
        else:
            self._clear_alert('query_latency_p99')
        
        # Check database connection usage
        connection_usage = metrics.get('connection_usage', 0.0)
        if connection_usage > self.config.alert_thresholds['db_connection_usage']:
            await self._trigger_alert(
                'db_connection_usage',
                f"Database connection usage {connection_usage:.2%} exceeds threshold {self.config.alert_thresholds['db_connection_usage']:.2%}",
                'critical',
                {'connection_usage': connection_usage}
            )
        else:
            self._clear_alert('db_connection_usage')
        
        # Check queue backlog
        queue_backlog = metrics.get('queue_backlog', 0)
        if queue_backlog > self.config.alert_thresholds['queue_backlog']:
            await self._trigger_alert(
                'queue_backlog',
                f"Queue backlog {queue_backlog} exceeds threshold {self.config.alert_thresholds['queue_backlog']}",
                'warning',
                {'queue_backlog': queue_backlog}
            )
        else:
            self._clear_alert('queue_backlog')
    
    async def _trigger_alert(self, alert_type: str, message: str, severity: str, 
                           metadata: Optional[Dict[str, Any]] = None):
        """Trigger an alert if not already triggered."""
        if not self.alert_state[alert_type]['triggered']:
            self.alert_state[alert_type]['triggered'] = True
            
            alert = Alert(alert_type, severity, message, metadata)
            ALERT_TRIGGERED.labels(alert_type=alert_type).inc()
            
            # Store alert in Redis
            alert_key = f"alert:{alert_type}:{alert.timestamp.timestamp()}"
            await self.redis_client.setex(
                alert_key,
                86400,  # 24 hour TTL
                alert.to_dict()
            )
            
            # Call alert handlers
            for handler in self.alert_handlers:
                try:
                    await handler(alert)
                except Exception as e:
                    logger.error(f"Alert handler failed: {e}")
            
            logger.warning(f"Alert triggered: {alert_type} - {message}")
    
    def _clear_alert(self, alert_type: str):
        """Clear an alert state."""
        if self.alert_state[alert_type]['triggered']:
            self.alert_state[alert_type]['triggered'] = False
            logger.info(f"Alert cleared: {alert_type}")
    
    async def _get_current_metrics(self) -> Dict[str, Any]:
        """Get current metrics from Redis."""
        # This is a placeholder - implement based on your metric storage
        # For now, return mock data
        return {
            'error_rate': 0.05,
            'latency_p99': 2.5,
            'connection_usage': 0.7,
            'queue_backlog': 500
        }
    
    async def _cleanup_old_metrics(self):
        """Clean up old metrics data."""
        # Clean up metrics older than 7 days
        expire_time = int(time.time()) - (7 * 86400)
        
        # Clean up various metric keys
        metric_keys = [
            'metric_result:*',
            'system:cpu_usage',
            'system:memory_usage',
            'alert:*'
        ]
        
        for pattern in metric_keys:
            cursor = 0
            while True:
                cursor, keys = await self.redis_client.scan(
                    cursor, match=pattern, count=100
                )
                
                if keys:
                    # Check each key's age and delete if too old
                    for key in keys:
                        # Implementation depends on your key structure
                        pass
                
                if cursor == 0:
                    break
    
    def record_metric_execution(self, metric_name: str, success: bool, duration: float):
        """Record metric execution for monitoring."""
        status = 'success' if success else 'failure'
        METRIC_EXECUTIONS.labels(metric_name=metric_name, status=status).inc()
        METRIC_EXECUTION_TIME.labels(metric_name=metric_name).observe(duration)
        
        # Buffer metrics for aggregation
        self.metrics_buffer['executions'].append({
            'metric_name': metric_name,
            'success': success,
            'duration': duration,
            'timestamp': time.time()
        })
        
        if not success:
            self.metrics_buffer['errors'].append({
                'metric_name': metric_name,
                'timestamp': time.time()
            })
        
        self.metrics_buffer['latencies'].append(duration)
    
    def update_connection_metrics(self, pool_name: str, active: int, idle: int, total: int):
        """Update database connection metrics."""
        DB_CONNECTIONS.labels(pool_name=pool_name, state='active').set(active)
        DB_CONNECTIONS.labels(pool_name=pool_name, state='idle').set(idle)
        DB_CONNECTIONS.labels(pool_name=pool_name, state='total').set(total)
    
    def update_queue_metrics(self, pool_name: str, size: int):
        """Update queue size metrics."""
        QUEUE_SIZE.labels(pool_name=pool_name).set(size)
    
    def record_rate_limit_rejection(self):
        """Record a rate limit rejection."""
        RATE_LIMIT_REJECTIONS.inc()


class AlertHandler:
    """Base class for alert handlers."""
    
    async def handle_alert(self, alert: Alert):
        """Handle an alert."""
        raise NotImplementedError


class LogAlertHandler(AlertHandler):
    """Alert handler that logs alerts."""
    
    async def handle_alert(self, alert: Alert):
        """Log the alert."""
        log_method = getattr(logger, alert.severity, logger.info)
        log_method(
            f"ALERT [{alert.alert_type}] {alert.message}",
            extra={'metadata': alert.metadata}
        )


class WebhookAlertHandler(AlertHandler):
    """Alert handler that sends alerts to a webhook."""
    
    def __init__(self, webhook_url: str):
        self.webhook_url = webhook_url
    
    async def handle_alert(self, alert: Alert):
        """Send alert to webhook."""
        # Implementation would use aiohttp to POST to webhook
        pass