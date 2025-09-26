from fastapi import APIRouter
from .call_api import router as call_router
from .agent_api import router as agent_router
from .robot_api import router as robot_router
from .admin_api import router as admin_router

router = APIRouter()

# 注册子路由
router.include_router(call_router, prefix="/call", tags=["calls"])
router.include_router(agent_router, prefix="/agent", tags=["agents"])
router.include_router(robot_router, prefix="/robot", tags=["robot"])
router.include_router(admin_router, prefix="/admin", tags=["admin"])