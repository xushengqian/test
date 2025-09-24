#!/bin/bash

# 指标调度系统停止脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 停止服务
stop_services() {
    log_info "Stopping Metric Scheduler services..."
    
    # 读取PID文件
    if [ -f "logs/services.env" ]; then
        source logs/services.env
        
        # 停止API服务器
        if [ ! -z "$API_PID" ]; then
            if kill -0 $API_PID 2>/dev/null; then
                log_info "Stopping API server (PID: $API_PID)..."
                kill $API_PID
                
                # 等待进程结束
                for i in {1..10}; do
                    if ! kill -0 $API_PID 2>/dev/null; then
                        break
                    fi
                    sleep 1
                done
                
                # 强制杀死进程
                if kill -0 $API_PID 2>/dev/null; then
                    log_warn "Force killing API server..."
                    kill -9 $API_PID 2>/dev/null || true
                fi
                
                log_info "API server stopped"
            else
                log_warn "API server process not found"
            fi
        fi
        
        # 停止调度器
        if [ ! -z "$SCHEDULER_PID" ]; then
            if kill -0 $SCHEDULER_PID 2>/dev/null; then
                log_info "Stopping scheduler (PID: $SCHEDULER_PID)..."
                kill $SCHEDULER_PID
                
                # 等待进程结束
                for i in {1..10}; do
                    if ! kill -0 $SCHEDULER_PID 2>/dev/null; then
                        break
                    fi
                    sleep 1
                done
                
                # 强制杀死进程
                if kill -0 $SCHEDULER_PID 2>/dev/null; then
                    log_warn "Force killing scheduler..."
                    kill -9 $SCHEDULER_PID 2>/dev/null || true
                fi
                
                log_info "Scheduler stopped"
            else
                log_warn "Scheduler process not found"
            fi
        fi
        
        # 清理PID文件
        rm -f logs/api.pid logs/scheduler.pid logs/services.env
        
    else
        log_warn "No service PID file found, trying to find processes by name..."
        
        # 查找并停止相关进程
        pkill -f "uvicorn src.api:app" || true
        pkill -f "python src/main.py" || true
        
        sleep 2
        
        # 强制停止
        pkill -9 -f "uvicorn src.api:app" || true
        pkill -9 -f "python src/main.py" || true
    fi
    
    log_info "All services stopped"
}

# 清理资源
cleanup() {
    log_info "Cleaning up resources..."
    
    # 清理临时文件
    rm -f logs/*.pid
    rm -f logs/services.env
    
    log_info "Cleanup completed"
}

# 主函数
main() {
    log_info "Stopping Metric Scheduler System..."
    
    # 停止服务
    stop_services
    
    # 清理资源
    cleanup
    
    log_info "Metric Scheduler System stopped successfully!"
}

# 执行主函数
main "$@"