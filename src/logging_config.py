"""
日志配置模块
"""
import os
import sys
import logging
from pathlib import Path
from loguru import logger
from typing import Dict, Any

from .config import get_config


class InterceptHandler(logging.Handler):
    """拦截标准库日志并转发到loguru"""
    
    def emit(self, record):
        # 获取对应的loguru级别
        try:
            level = logger.level(record.levelname).name
        except ValueError:
            level = record.levelno

        # 查找调用者
        frame, depth = logging.currentframe(), 2
        while frame.f_code.co_filename == logging.__file__:
            frame = frame.f_back
            depth += 1

        logger.opt(depth=depth, exception=record.exc_info).log(
            level, record.getMessage()
        )


def setup_logging():
    """设置日志配置"""
    config = get_config().log
    
    # 创建日志目录
    log_dir = Path(config.log_dir)
    log_dir.mkdir(exist_ok=True)
    
    # 移除默认的loguru处理器
    logger.remove()
    
    # 添加控制台输出
    logger.add(
        sys.stdout,
        format=config.format,
        level=config.level,
        colorize=True,
        backtrace=True,
        diagnose=True
    )
    
    # 添加文件输出 - 主日志文件
    logger.add(
        log_dir / "scheduler.log",
        format=config.format,
        level=config.level,
        rotation=config.rotation,
        retention=config.retention,
        compression="zip",
        backtrace=True,
        diagnose=True
    )
    
    # 添加错误日志文件
    logger.add(
        log_dir / "error.log",
        format=config.format,
        level="ERROR",
        rotation=config.rotation,
        retention=config.retention,
        compression="zip",
        backtrace=True,
        diagnose=True
    )
    
    # 添加任务执行日志文件
    logger.add(
        log_dir / "tasks.log",
        format=config.format,
        level="INFO",
        rotation=config.rotation,
        retention=config.retention,
        compression="zip",
        filter=lambda record: "task_execution" in record["extra"],
        backtrace=True,
        diagnose=True
    )
    
    # 添加性能日志文件
    logger.add(
        log_dir / "performance.log",
        format=config.format,
        level="INFO",
        rotation="1 day",
        retention=config.retention,
        compression="zip",
        filter=lambda record: "performance" in record["extra"],
        backtrace=True,
        diagnose=True
    )
    
    # 拦截标准库日志
    logging.basicConfig(handlers=[InterceptHandler()], level=0, force=True)
    
    # 设置第三方库日志级别
    logging.getLogger("sqlalchemy.engine").setLevel(logging.WARNING)
    logging.getLogger("urllib3").setLevel(logging.WARNING)
    logging.getLogger("asyncio").setLevel(logging.WARNING)
    
    logger.info("Logging system initialized")


def get_logger(name: str = None) -> Any:
    """获取日志记录器"""
    if name:
        return logger.bind(name=name)
    return logger


def log_task_execution(task_id: str, metric_name: str, status: str, duration_ms: int = None, error: str = None):
    """记录任务执行日志"""
    extra_data = {
        "task_execution": True,
        "task_id": task_id,
        "metric_name": metric_name,
        "status": status
    }
    
    if duration_ms is not None:
        extra_data["duration_ms"] = duration_ms
    
    if error:
        extra_data["error"] = error
    
    logger.bind(**extra_data).info(f"Task {task_id} [{metric_name}] {status}")


def log_performance_metrics(metrics: Dict[str, Any]):
    """记录性能指标日志"""
    logger.bind(performance=True, **metrics).info("Performance metrics collected")


def log_scheduler_event(event_type: str, details: Dict[str, Any] = None):
    """记录调度器事件日志"""
    extra_data = {"scheduler_event": True, "event_type": event_type}
    if details:
        extra_data.update(details)
    
    logger.bind(**extra_data).info(f"Scheduler event: {event_type}")


def log_database_operation(operation: str, table: str, duration_ms: int = None, error: str = None):
    """记录数据库操作日志"""
    extra_data = {
        "database_operation": True,
        "operation": operation,
        "table": table
    }
    
    if duration_ms is not None:
        extra_data["duration_ms"] = duration_ms
    
    if error:
        extra_data["error"] = error
        logger.bind(**extra_data).error(f"Database {operation} on {table} failed: {error}")
    else:
        logger.bind(**extra_data).debug(f"Database {operation} on {table} completed")


def log_api_request(method: str, path: str, status_code: int, duration_ms: int, user_id: str = None):
    """记录API请求日志"""
    extra_data = {
        "api_request": True,
        "method": method,
        "path": path,
        "status_code": status_code,
        "duration_ms": duration_ms
    }
    
    if user_id:
        extra_data["user_id"] = user_id
    
    logger.bind(**extra_data).info(f"{method} {path} {status_code} ({duration_ms}ms)")


class LogContext:
    """日志上下文管理器"""
    
    def __init__(self, **context):
        self.context = context
        self.logger = logger.bind(**context)
    
    def __enter__(self):
        return self.logger
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        if exc_type:
            self.logger.error(f"Exception in context: {exc_val}")


def with_log_context(**context):
    """日志上下文装饰器"""
    def decorator(func):
        def wrapper(*args, **kwargs):
            with LogContext(**context) as ctx_logger:
                try:
                    result = func(*args, **kwargs)
                    ctx_logger.debug(f"Function {func.__name__} completed successfully")
                    return result
                except Exception as e:
                    ctx_logger.error(f"Function {func.__name__} failed: {e}")
                    raise
        return wrapper
    return decorator


# 日志级别映射
LOG_LEVELS = {
    "TRACE": 5,
    "DEBUG": 10,
    "INFO": 20,
    "SUCCESS": 25,
    "WARNING": 30,
    "ERROR": 40,
    "CRITICAL": 50
}


def set_log_level(level: str):
    """设置日志级别"""
    if level.upper() in LOG_LEVELS:
        logger.remove()
        setup_logging()
        logger.info(f"Log level set to {level.upper()}")
    else:
        logger.warning(f"Invalid log level: {level}")


def create_structured_log(
    event: str,
    level: str = "INFO",
    **kwargs
) -> None:
    """创建结构化日志"""
    log_data = {
        "event": event,
        "timestamp": logger._core.now().isoformat(),
        **kwargs
    }
    
    getattr(logger, level.lower())(f"Event: {event}", **log_data)


# 预定义的日志模板
class LogTemplates:
    """日志模板类"""
    
    @staticmethod
    def task_started(task_id: str, metric_name: str, scheduled_time: str):
        log_task_execution(task_id, metric_name, "STARTED")
    
    @staticmethod
    def task_completed(task_id: str, metric_name: str, duration_ms: int, result_rows: int):
        logger.bind(
            task_execution=True,
            task_id=task_id,
            metric_name=metric_name,
            duration_ms=duration_ms,
            result_rows=result_rows
        ).info(f"Task {task_id} [{metric_name}] completed successfully")
    
    @staticmethod
    def task_failed(task_id: str, metric_name: str, error: str, retry_count: int):
        logger.bind(
            task_execution=True,
            task_id=task_id,
            metric_name=metric_name,
            error=error,
            retry_count=retry_count
        ).error(f"Task {task_id} [{metric_name}] failed")
    
    @staticmethod
    def scheduler_started():
        log_scheduler_event("SCHEDULER_STARTED")
    
    @staticmethod
    def scheduler_stopped():
        log_scheduler_event("SCHEDULER_STOPPED")
    
    @staticmethod
    def worker_registered(node_id: str, max_tasks: int):
        log_scheduler_event("WORKER_REGISTERED", {
            "node_id": node_id,
            "max_tasks": max_tasks
        })
    
    @staticmethod
    def worker_disconnected(node_id: str):
        log_scheduler_event("WORKER_DISCONNECTED", {"node_id": node_id})
    
    @staticmethod
    def metric_created(metric_id: int, metric_name: str, created_by: str):
        logger.bind(
            metric_management=True,
            metric_id=metric_id,
            metric_name=metric_name,
            created_by=created_by
        ).info(f"Metric {metric_name} created")
    
    @staticmethod
    def schedule_created(schedule_id: int, metric_name: str, cron_expression: str):
        logger.bind(
            schedule_management=True,
            schedule_id=schedule_id,
            metric_name=metric_name,
            cron_expression=cron_expression
        ).info(f"Schedule created for metric {metric_name}")
    
    @staticmethod
    def alert_triggered(rule_name: str, severity: str, message: str):
        logger.bind(
            alert=True,
            rule_name=rule_name,
            severity=severity
        ).warning(f"Alert triggered: {message}")


# 初始化日志系统
def init_logging():
    """初始化日志系统"""
    setup_logging()
    logger.info("Metric Scheduler logging system initialized")
    return logger