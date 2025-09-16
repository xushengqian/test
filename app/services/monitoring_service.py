import asyncio
import psutil
import time
import logging
from typing import Dict, Any
from datetime import datetime, timedelta
import json

logger = logging.getLogger(__name__)

class SystemMonitor:
    """系统监控服务"""
    
    def __init__(self):
        self.start_time = time.time()
        self.metrics_history = []
        self.max_history = 1000  # 保留最近1000条记录
    
    def get_system_metrics(self) -> Dict[str, Any]:
        """获取系统指标"""
        try:
            # CPU使用率
            cpu_percent = psutil.cpu_percent(interval=1)
            
            # 内存使用情况
            memory = psutil.virtual_memory()
            
            # 磁盘使用情况
            disk = psutil.disk_usage('/')
            
            # 网络IO
            network = psutil.net_io_counters()
            
            # 进程信息
            process = psutil.Process()
            process_memory = process.memory_info()
            
            metrics = {
                "timestamp": datetime.now().isoformat(),
                "uptime_seconds": time.time() - self.start_time,
                "cpu": {
                    "percent": cpu_percent,
                    "count": psutil.cpu_count()
                },
                "memory": {
                    "total_gb": round(memory.total / (1024**3), 2),
                    "available_gb": round(memory.available / (1024**3), 2),
                    "used_gb": round(memory.used / (1024**3), 2),
                    "percent": memory.percent
                },
                "disk": {
                    "total_gb": round(disk.total / (1024**3), 2),
                    "used_gb": round(disk.used / (1024**3), 2),
                    "free_gb": round(disk.free / (1024**3), 2),
                    "percent": round(disk.used / disk.total * 100, 2)
                },
                "network": {
                    "bytes_sent": network.bytes_sent,
                    "bytes_recv": network.bytes_recv,
                    "packets_sent": network.packets_sent,
                    "packets_recv": network.packets_recv
                },
                "process": {
                    "memory_mb": round(process_memory.rss / (1024**2), 2),
                    "cpu_percent": process.cpu_percent(),
                    "threads": process.num_threads()
                }
            }
            
            # 保存到历史记录
            self.metrics_history.append(metrics)
            if len(self.metrics_history) > self.max_history:
                self.metrics_history.pop(0)
            
            return metrics
            
        except Exception as e:
            logger.error(f"Error getting system metrics: {e}")
            return {}
    
    def get_metrics_summary(self, hours: int = 1) -> Dict[str, Any]:
        """获取指标摘要"""
        cutoff_time = datetime.now() - timedelta(hours=hours)
        recent_metrics = [
            m for m in self.metrics_history 
            if datetime.fromisoformat(m["timestamp"]) > cutoff_time
        ]
        
        if not recent_metrics:
            return {}
        
        # 计算平均值
        cpu_values = [m["cpu"]["percent"] for m in recent_metrics]
        memory_values = [m["memory"]["percent"] for m in recent_metrics]
        
        return {
            "period_hours": hours,
            "sample_count": len(recent_metrics),
            "avg_cpu_percent": round(sum(cpu_values) / len(cpu_values), 2),
            "max_cpu_percent": max(cpu_values),
            "avg_memory_percent": round(sum(memory_values) / len(memory_values), 2),
            "max_memory_percent": max(memory_values),
            "current_metrics": recent_metrics[-1] if recent_metrics else None
        }
    
    def check_health(self) -> Dict[str, Any]:
        """健康检查"""
        metrics = self.get_system_metrics()
        
        if not metrics:
            return {
                "status": "error",
                "message": "Unable to get system metrics"
            }
        
        warnings = []
        errors = []
        
        # CPU检查
        if metrics["cpu"]["percent"] > 90:
            errors.append(f"High CPU usage: {metrics['cpu']['percent']}%")
        elif metrics["cpu"]["percent"] > 80:
            warnings.append(f"CPU usage warning: {metrics['cpu']['percent']}%")
        
        # 内存检查
        if metrics["memory"]["percent"] > 95:
            errors.append(f"High memory usage: {metrics['memory']['percent']}%")
        elif metrics["memory"]["percent"] > 85:
            warnings.append(f"Memory usage warning: {metrics['memory']['percent']}%")
        
        # 磁盘检查
        if metrics["disk"]["percent"] > 95:
            errors.append(f"High disk usage: {metrics['disk']['percent']}%")
        elif metrics["disk"]["percent"] > 85:
            warnings.append(f"Disk usage warning: {metrics['disk']['percent']}%")
        
        # 进程内存检查
        if metrics["process"]["memory_mb"] > 2048:  # 2GB
            warnings.append(f"High process memory: {metrics['process']['memory_mb']}MB")
        
        status = "healthy"
        if errors:
            status = "error"
        elif warnings:
            status = "warning"
        
        return {
            "status": status,
            "warnings": warnings,
            "errors": errors,
            "metrics": metrics
        }

class PerformanceTracker:
    """性能跟踪器"""
    
    def __init__(self):
        self.transcription_times = []
        self.call_durations = []
        self.websocket_connections = 0
        self.active_calls = 0
    
    def record_transcription_time(self, duration: float):
        """记录转写时间"""
        self.transcription_times.append(duration)
        # 只保留最近1000条记录
        if len(self.transcription_times) > 1000:
            self.transcription_times.pop(0)
    
    def record_call_duration(self, duration: float):
        """记录通话时长"""
        self.call_durations.append(duration)
        if len(self.call_durations) > 1000:
            self.call_durations.pop(0)
    
    def update_websocket_connections(self, count: int):
        """更新WebSocket连接数"""
        self.websocket_connections = count
    
    def update_active_calls(self, count: int):
        """更新活跃通话数"""
        self.active_calls = count
    
    def get_performance_stats(self) -> Dict[str, Any]:
        """获取性能统计"""
        stats = {
            "websocket_connections": self.websocket_connections,
            "active_calls": self.active_calls,
            "transcription_count": len(self.transcription_times),
            "call_count": len(self.call_durations)
        }
        
        if self.transcription_times:
            stats["avg_transcription_time"] = round(
                sum(self.transcription_times) / len(self.transcription_times), 3
            )
            stats["max_transcription_time"] = max(self.transcription_times)
        
        if self.call_durations:
            stats["avg_call_duration"] = round(
                sum(self.call_durations) / len(self.call_durations), 2
            )
            stats["max_call_duration"] = max(self.call_durations)
        
        return stats

# 全局监控实例
system_monitor = SystemMonitor()
performance_tracker = PerformanceTracker()