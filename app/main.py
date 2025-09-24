from fastapi import FastAPI, Request, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from contextlib import asynccontextmanager
import time
from loguru import logger
import sys
import os

from .config import settings
from .database import init_database, close_database
from .api import metrics, system


# 配置日志
logger.remove()
logger.add(
    sys.stdout,
    format="<green>{time:YYYY-MM-DD HH:mm:ss}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan>:<cyan>{function}</cyan>:<cyan>{line}</cyan> - <level>{message}</level>",
    level=settings.log_level
)

# 如果日志目录不存在，创建它
os.makedirs(os.path.dirname(settings.log_file), exist_ok=True)

logger.add(
    settings.log_file,
    format="{time:YYYY-MM-DD HH:mm:ss} | {level: <8} | {name}:{function}:{line} - {message}",
    level=settings.log_level,
    rotation="100 MB",
    retention="30 days",
    compression="zip"
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    # 启动时执行
    logger.info("Starting Metrics Scheduler Application")
    
    try:
        # 初始化数据库
        await init_database()
        logger.info("Database initialized successfully")
        
        # 启动告警监控
        from .api.monitoring import start_alert_monitoring
        start_alert_monitoring()
        logger.info("Alert monitoring started")
        
        yield
        
    except Exception as e:
        logger.error(f"Failed to initialize application: {e}")
        raise
    finally:
        # 关闭时执行
        logger.info("Shutting down Metrics Scheduler Application")
        try:
            await close_database()
            logger.info("Database connections closed")
        except Exception as e:
            logger.error(f"Error during shutdown: {e}")


# 创建FastAPI应用
app = FastAPI(
    title="企业指标调度系统",
    description="多家企业指标同时执行调度，防止数据库被压力打垮",
    version="1.0.0",
    lifespan=lifespan,
    docs_url="/docs",
    redoc_url="/redoc"
)

# 添加CORS中间件
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境应该限制具体域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# 请求处理时间中间件
@app.middleware("http")
async def add_process_time_header(request: Request, call_next):
    start_time = time.time()
    response = await call_next(request)
    process_time = time.time() - start_time
    response.headers["X-Process-Time"] = str(process_time)
    
    # 记录慢请求
    if process_time > 5.0:  # 超过5秒的请求
        logger.warning(f"Slow request: {request.method} {request.url} took {process_time:.2f}s")
    
    return response


# 全局异常处理
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error(f"Global exception: {request.method} {request.url} - {str(exc)}")
    
    return JSONResponse(
        status_code=500,
        content={
            "error": "Internal server error",
            "message": str(exc),
            "path": str(request.url),
            "method": request.method
        }
    )


# 404处理
@app.exception_handler(404)
async def not_found_handler(request: Request, exc: HTTPException):
    return JSONResponse(
        status_code=404,
        content={
            "error": "Not found",
            "message": f"Path {request.url.path} not found",
            "path": str(request.url),
            "method": request.method
        }
    )


# 注册路由
app.include_router(metrics.router)
app.include_router(system.router)

# 注册监控路由
from .api import monitoring
app.include_router(monitoring.router)


# 根路径
@app.get("/")
async def root():
    """根路径，返回API信息"""
    return {
        "name": "企业指标调度系统",
        "version": "1.0.0",
        "description": "多家企业指标同时执行调度，防止数据库被压力打垮",
        "features": [
            "数据库连接池管理",
            "任务队列和调度",
            "限流和熔断保护",
            "系统监控和告警",
            "批量指标执行",
            "实时状态查询"
        ],
        "endpoints": {
            "docs": "/docs",
            "redoc": "/redoc",
            "metrics_api": "/api/v1/metrics",
            "system_api": "/api/v1/system",
            "monitoring_api": "/api/v1/monitoring"
        }
    }


# 健康检查端点
@app.get("/health")
async def health_check():
    """简单的健康检查"""
    return {
        "status": "healthy",
        "timestamp": time.time(),
        "version": "1.0.0"
    }


# 就绪检查端点
@app.get("/ready")
async def readiness_check():
    """就绪检查，检查依赖服务"""
    from .database import check_database_health
    from .celery_app import redis_client
    
    checks = {}
    
    # 检查数据库
    try:
        db_healthy = await check_database_health()
        checks["database"] = {"status": "healthy" if db_healthy else "unhealthy"}
    except Exception as e:
        checks["database"] = {"status": "unhealthy", "error": str(e)}
    
    # 检查Redis
    try:
        await redis_client.ping()
        checks["redis"] = {"status": "healthy"}
    except Exception as e:
        checks["redis"] = {"status": "unhealthy", "error": str(e)}
    
    # 总体状态
    all_healthy = all(check["status"] == "healthy" for check in checks.values())
    
    return {
        "status": "ready" if all_healthy else "not_ready",
        "timestamp": time.time(),
        "checks": checks
    }


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host="0.0.0.0",
        port=8000,
        reload=True,
        log_level=settings.log_level.lower()
    )