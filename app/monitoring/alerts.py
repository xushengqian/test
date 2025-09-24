import asyncio
from typing import Dict, Any, List, Optional, Callable
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from enum import Enum
import json
from loguru import logger

from ..config import settings


class AlertSeverity(Enum):
    """告警严重程度"""
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


class AlertStatus(Enum):
    """告警状态"""
    ACTIVE = "active"
    RESOLVED = "resolved"
    SUPPRESSED = "suppressed"


@dataclass
class AlertRule:
    """告警规则"""
    name: str
    description: str
    condition: Callable[[Dict[str, Any]], bool]
    severity: AlertSeverity
    threshold_duration: int = 60  # 持续时间阈值（秒）
    cooldown_duration: int = 300  # 冷却时间（秒）
    enabled: bool = True
    tags: Dict[str, str] = field(default_factory=dict)


@dataclass
class Alert:
    """告警实例"""
    rule_name: str
    severity: AlertSeverity
    message: str
    details: Dict[str, Any]
    status: AlertStatus = AlertStatus.ACTIVE
    created_at: datetime = field(default_factory=datetime.utcnow)
    resolved_at: Optional[datetime] = None
    tags: Dict[str, str] = field(default_factory=dict)
    
    def to_dict(self) -> Dict[str, Any]:
        """转换为字典"""
        return {
            "rule_name": self.rule_name,
            "severity": self.severity.value,
            "message": self.message,
            "details": self.details,
            "status": self.status.value,
            "created_at": self.created_at.isoformat(),
            "resolved_at": self.resolved_at.isoformat() if self.resolved_at else None,
            "tags": self.tags
        }


class AlertManager:
    """告警管理器"""
    
    def __init__(self):
        self.rules: Dict[str, AlertRule] = {}
        self.active_alerts: Dict[str, Alert] = {}
        self.alert_history: List[Alert] = []
        self.rule_states: Dict[str, Dict[str, Any]] = {}
        self.notification_handlers: List[Callable[[Alert], None]] = []
        
        # 初始化默认告警规则
        self._init_default_rules()
    
    def _init_default_rules(self):
        """初始化默认告警规则"""
        
        # 数据库连接池告警
        self.add_rule(AlertRule(
            name="database_pool_exhausted",
            description="数据库连接池耗尽",
            condition=lambda metrics: (
                metrics.get("db_checked_out", 0) >= 
                metrics.get("db_pool_size", 10) * 0.9
            ),
            severity=AlertSeverity.HIGH,
            threshold_duration=30,
            tags={"component": "database", "type": "resource"}
        ))
        
        # 高CPU使用率告警
        self.add_rule(AlertRule(
            name="high_cpu_usage",
            description="CPU使用率过高",
            condition=lambda metrics: metrics.get("cpu_usage_percent", 0) > 90,
            severity=AlertSeverity.MEDIUM,
            threshold_duration=120,
            tags={"component": "system", "type": "resource"}
        ))
        
        # 高内存使用率告警
        self.add_rule(AlertRule(
            name="high_memory_usage",
            description="内存使用率过高",
            condition=lambda metrics: metrics.get("memory_usage_percent", 0) > 90,
            severity=AlertSeverity.MEDIUM,
            threshold_duration=120,
            tags={"component": "system", "type": "resource"}
        ))
        
        # 磁盘空间不足告警
        self.add_rule(AlertRule(
            name="low_disk_space",
            description="磁盘空间不足",
            condition=lambda metrics: metrics.get("disk_usage_percent", 0) > 90,
            severity=AlertSeverity.HIGH,
            threshold_duration=60,
            tags={"component": "system", "type": "resource"}
        ))
        
        # 任务队列积压告警
        self.add_rule(AlertRule(
            name="task_queue_backlog",
            description="任务队列积压严重",
            condition=lambda metrics: (
                metrics.get("active_tasks", 0) >= 
                metrics.get("max_concurrent_tasks", 5)
            ),
            severity=AlertSeverity.MEDIUM,
            threshold_duration=300,
            tags={"component": "task_queue", "type": "performance"}
        ))
        
        # 指标执行失败率过高告警
        self.add_rule(AlertRule(
            name="high_metric_failure_rate",
            description="指标执行失败率过高",
            condition=lambda metrics: (
                metrics.get("failed_executions", 0) > 0 and
                (metrics.get("failed_executions", 0) / 
                 max(metrics.get("total_executions", 1), 1)) > 0.2
            ),
            severity=AlertSeverity.HIGH,
            threshold_duration=180,
            tags={"component": "metrics", "type": "reliability"}
        ))
        
        # 数据库健康检查失败告警
        self.add_rule(AlertRule(
            name="database_health_check_failed",
            description="数据库健康检查失败",
            condition=lambda metrics: not metrics.get("database_healthy", True),
            severity=AlertSeverity.CRITICAL,
            threshold_duration=30,
            tags={"component": "database", "type": "availability"}
        ))
        
        # Redis连接失败告警
        self.add_rule(AlertRule(
            name="redis_connection_failed",
            description="Redis连接失败",
            condition=lambda metrics: not metrics.get("redis_healthy", True),
            severity=AlertSeverity.CRITICAL,
            threshold_duration=30,
            tags={"component": "redis", "type": "availability"}
        ))
        
        # 长时间运行的指标告警
        self.add_rule(AlertRule(
            name="long_running_metric",
            description="指标执行时间过长",
            condition=lambda metrics: (
                metrics.get("max_execution_duration_ms", 0) > 300000  # 5分钟
            ),
            severity=AlertSeverity.MEDIUM,
            threshold_duration=60,
            tags={"component": "metrics", "type": "performance"}
        ))
        
        # 熔断器开启告警
        self.add_rule(AlertRule(
            name="circuit_breaker_open",
            description="熔断器开启",
            condition=lambda metrics: metrics.get("circuit_breaker_state") == "open",
            severity=AlertSeverity.HIGH,
            threshold_duration=0,  # 立即告警
            tags={"component": "circuit_breaker", "type": "reliability"}
        ))
    
    def add_rule(self, rule: AlertRule):
        """添加告警规则"""
        self.rules[rule.name] = rule
        self.rule_states[rule.name] = {
            "first_triggered": None,
            "last_checked": None,
            "last_alerted": None,
            "consecutive_triggers": 0
        }
        logger.info(f"Added alert rule: {rule.name}")
    
    def remove_rule(self, rule_name: str):
        """移除告警规则"""
        if rule_name in self.rules:
            del self.rules[rule_name]
            del self.rule_states[rule_name]
            logger.info(f"Removed alert rule: {rule_name}")
    
    def add_notification_handler(self, handler: Callable[[Alert], None]):
        """添加通知处理器"""
        self.notification_handlers.append(handler)
    
    async def check_alerts(self, metrics: Dict[str, Any]):
        """检查告警条件"""
        current_time = datetime.utcnow()
        
        for rule_name, rule in self.rules.items():
            if not rule.enabled:
                continue
            
            state = self.rule_states[rule_name]
            state["last_checked"] = current_time
            
            try:
                # 检查告警条件
                is_triggered = rule.condition(metrics)
                
                if is_triggered:
                    state["consecutive_triggers"] += 1
                    
                    # 第一次触发
                    if state["first_triggered"] is None:
                        state["first_triggered"] = current_time
                    
                    # 检查是否达到持续时间阈值
                    duration_seconds = (current_time - state["first_triggered"]).total_seconds()
                    
                    if duration_seconds >= rule.threshold_duration:
                        # 检查冷却时间
                        if (state["last_alerted"] is None or 
                            (current_time - state["last_alerted"]).total_seconds() >= rule.cooldown_duration):
                            
                            await self._trigger_alert(rule, metrics, current_time)
                            state["last_alerted"] = current_time
                
                else:
                    # 条件不满足，重置状态
                    if state["first_triggered"] is not None:
                        # 如果之前有活跃告警，现在解决它
                        await self._resolve_alert(rule_name, current_time)
                    
                    state["first_triggered"] = None
                    state["consecutive_triggers"] = 0
            
            except Exception as e:
                logger.error(f"Error checking alert rule {rule_name}: {e}")
    
    async def _trigger_alert(self, rule: AlertRule, metrics: Dict[str, Any], timestamp: datetime):
        """触发告警"""
        alert_key = f"{rule.name}_{timestamp.strftime('%Y%m%d_%H%M')}"
        
        if alert_key not in self.active_alerts:
            alert = Alert(
                rule_name=rule.name,
                severity=rule.severity,
                message=f"{rule.description} - {self._format_alert_message(rule, metrics)}",
                details=self._extract_relevant_metrics(rule, metrics),
                created_at=timestamp,
                tags=rule.tags
            )
            
            self.active_alerts[alert_key] = alert
            self.alert_history.append(alert)
            
            logger.warning(f"Alert triggered: {rule.name} - {alert.message}")
            
            # 发送通知
            await self._send_notifications(alert)
    
    async def _resolve_alert(self, rule_name: str, timestamp: datetime):
        """解决告警"""
        # 查找并解决相关的活跃告警
        resolved_alerts = []
        
        for alert_key, alert in list(self.active_alerts.items()):
            if alert.rule_name == rule_name and alert.status == AlertStatus.ACTIVE:
                alert.status = AlertStatus.RESOLVED
                alert.resolved_at = timestamp
                resolved_alerts.append(alert)
                del self.active_alerts[alert_key]
        
        for alert in resolved_alerts:
            logger.info(f"Alert resolved: {alert.rule_name}")
            await self._send_notifications(alert)
    
    def _format_alert_message(self, rule: AlertRule, metrics: Dict[str, Any]) -> str:
        """格式化告警消息"""
        relevant_metrics = self._extract_relevant_metrics(rule, metrics)
        
        if rule.name == "database_pool_exhausted":
            return f"连接池使用率: {relevant_metrics.get('pool_usage_percent', 0):.1f}%"
        elif rule.name == "high_cpu_usage":
            return f"CPU使用率: {relevant_metrics.get('cpu_usage_percent', 0):.1f}%"
        elif rule.name == "high_memory_usage":
            return f"内存使用率: {relevant_metrics.get('memory_usage_percent', 0):.1f}%"
        elif rule.name == "low_disk_space":
            return f"磁盘使用率: {relevant_metrics.get('disk_usage_percent', 0):.1f}%"
        elif rule.name == "task_queue_backlog":
            return f"活跃任务: {relevant_metrics.get('active_tasks', 0)}/{relevant_metrics.get('max_concurrent_tasks', 0)}"
        elif rule.name == "high_metric_failure_rate":
            return f"失败率: {relevant_metrics.get('failure_rate_percent', 0):.1f}%"
        else:
            return f"相关指标: {json.dumps(relevant_metrics, default=str)}"
    
    def _extract_relevant_metrics(self, rule: AlertRule, metrics: Dict[str, Any]) -> Dict[str, Any]:
        """提取相关指标"""
        relevant = {}
        
        if rule.name == "database_pool_exhausted":
            relevant.update({
                "db_checked_out": metrics.get("db_checked_out", 0),
                "db_pool_size": metrics.get("db_pool_size", 0),
                "pool_usage_percent": (metrics.get("db_checked_out", 0) / 
                                     max(metrics.get("db_pool_size", 1), 1)) * 100
            })
        elif rule.name in ["high_cpu_usage", "high_memory_usage", "low_disk_space"]:
            relevant.update({
                "cpu_usage_percent": metrics.get("cpu_usage_percent", 0),
                "memory_usage_percent": metrics.get("memory_usage_percent", 0),
                "disk_usage_percent": metrics.get("disk_usage_percent", 0)
            })
        elif rule.name == "task_queue_backlog":
            relevant.update({
                "active_tasks": metrics.get("active_tasks", 0),
                "max_concurrent_tasks": metrics.get("max_concurrent_tasks", 0)
            })
        elif rule.name == "high_metric_failure_rate":
            total = max(metrics.get("total_executions", 1), 1)
            failed = metrics.get("failed_executions", 0)
            relevant.update({
                "total_executions": total,
                "failed_executions": failed,
                "failure_rate_percent": (failed / total) * 100
            })
        else:
            # 包含所有指标
            relevant = metrics.copy()
        
        return relevant
    
    async def _send_notifications(self, alert: Alert):
        """发送通知"""
        for handler in self.notification_handlers:
            try:
                await asyncio.get_event_loop().run_in_executor(None, handler, alert)
            except Exception as e:
                logger.error(f"Error sending notification: {e}")
    
    def get_active_alerts(self) -> List[Alert]:
        """获取活跃告警"""
        return list(self.active_alerts.values())
    
    def get_alert_history(self, hours: int = 24) -> List[Alert]:
        """获取告警历史"""
        cutoff_time = datetime.utcnow() - timedelta(hours=hours)
        return [alert for alert in self.alert_history if alert.created_at >= cutoff_time]
    
    def get_alert_stats(self) -> Dict[str, Any]:
        """获取告警统计"""
        active_count = len(self.active_alerts)
        
        # 按严重程度统计
        severity_counts = {}
        for alert in self.active_alerts.values():
            severity = alert.severity.value
            severity_counts[severity] = severity_counts.get(severity, 0) + 1
        
        # 最近24小时的告警统计
        recent_alerts = self.get_alert_history(24)
        recent_count = len(recent_alerts)
        
        return {
            "active_alerts": active_count,
            "severity_breakdown": severity_counts,
            "recent_24h": recent_count,
            "total_rules": len(self.rules),
            "enabled_rules": len([r for r in self.rules.values() if r.enabled])
        }


# 全局告警管理器实例
alert_manager = AlertManager()


# 默认通知处理器
def log_notification_handler(alert: Alert):
    """日志通知处理器"""
    if alert.status == AlertStatus.ACTIVE:
        logger.warning(f"🚨 ALERT: {alert.message}")
    else:
        logger.info(f"✅ RESOLVED: {alert.rule_name}")


def console_notification_handler(alert: Alert):
    """控制台通知处理器"""
    timestamp = alert.created_at.strftime("%Y-%m-%d %H:%M:%S")
    
    if alert.status == AlertStatus.ACTIVE:
        print(f"\n🚨 ALERT [{timestamp}] {alert.severity.value.upper()}")
        print(f"   Rule: {alert.rule_name}")
        print(f"   Message: {alert.message}")
        print(f"   Details: {json.dumps(alert.details, indent=2, default=str)}")
    else:
        print(f"\n✅ RESOLVED [{timestamp}] {alert.rule_name}")


# 注册默认通知处理器
alert_manager.add_notification_handler(log_notification_handler)
alert_manager.add_notification_handler(console_notification_handler)