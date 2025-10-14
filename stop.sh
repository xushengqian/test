#!/bin/bash

# FreeSwitch 机器人呼出系统停止脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

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

# 停止系统
stop_system() {
    log_info "停止 FreeSwitch 机器人呼出系统..."
    
    if [ ! -f "robot_system.pid" ]; then
        log_warn "PID 文件不存在，系统可能未运行"
        return 1
    fi
    
    pid=$(cat robot_system.pid)
    
    if ! ps -p $pid > /dev/null 2>&1; then
        log_warn "进程不存在 (PID: $pid)，清理 PID 文件"
        rm -f robot_system.pid
        return 1
    fi
    
    log_info "发送 SIGTERM 信号到进程 $pid"
    kill -TERM $pid
    
    # 等待进程优雅退出
    local timeout=30
    local count=0
    
    while ps -p $pid > /dev/null 2>&1; do
        if [ $count -ge $timeout ]; then
            log_warn "进程未在 $timeout 秒内退出，发送 SIGKILL 信号"
            kill -KILL $pid
            break
        fi
        
        sleep 1
        count=$((count + 1))
        
        if [ $((count % 5)) -eq 0 ]; then
            log_info "等待进程退出... ($count/$timeout 秒)"
        fi
    done
    
    # 清理 PID 文件
    if [ -f "robot_system.pid" ]; then
        rm -f robot_system.pid
        log_info "已清理 PID 文件"
    fi
    
    log_info "系统已停止"
}

# 强制停止
force_stop() {
    log_warn "强制停止系统..."
    
    # 查找所有相关进程
    pids=$(pgrep -f "main.py" || true)
    
    if [ -z "$pids" ]; then
        log_info "未找到运行中的进程"
        return 0
    fi
    
    log_info "找到进程: $pids"
    
    for pid in $pids; do
        log_info "强制终止进程 $pid"
        kill -KILL $pid 2>/dev/null || true
    done
    
    # 清理 PID 文件
    rm -f robot_system.pid
    
    log_info "强制停止完成"
}

# 检查系统状态
check_status() {
    if [ -f "robot_system.pid" ]; then
        pid=$(cat robot_system.pid)
        if ps -p $pid > /dev/null 2>&1; then
            log_info "系统正在运行 (PID: $pid)"
            return 0
        else
            log_warn "PID 文件存在但进程未运行"
            rm -f robot_system.pid
            return 1
        fi
    else
        log_info "系统未运行"
        return 1
    fi
}

# 清理资源
cleanup() {
    log_info "清理系统资源..."
    
    # 清理临时文件
    find logs/ -name "*.tmp" -delete 2>/dev/null || true
    find logs/ -name "*.lock" -delete 2>/dev/null || true
    
    # 清理过期日志（保留最近7天）
    find logs/ -name "*.log.*" -mtime +7 -delete 2>/dev/null || true
    
    # 清理数据库锁文件
    find . -name "*.db-shm" -delete 2>/dev/null || true
    find . -name "*.db-wal" -delete 2>/dev/null || true
    
    log_info "资源清理完成"
}

# 显示帮助信息
show_help() {
    echo "FreeSwitch 机器人呼出系统停止脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  --help, -h      显示帮助信息"
    echo "  --force, -f     强制停止所有相关进程"
    echo "  --status, -s    检查系统状态"
    echo "  --cleanup, -c   清理系统资源"
    echo ""
    echo "示例:"
    echo "  $0              # 正常停止系统"
    echo "  $0 --force      # 强制停止系统"
    echo "  $0 --status     # 检查系统状态"
    echo "  $0 --cleanup    # 清理系统资源"
}

# 主函数
main() {
    echo -e "${BLUE}=== FreeSwitch 机器人呼出系统停止脚本 ===${NC}"
    echo ""
    
    case "$1" in
        --help|-h)
            show_help
            exit 0
            ;;
        --force|-f)
            force_stop
            cleanup
            ;;
        --status|-s)
            check_status
            ;;
        --cleanup|-c)
            cleanup
            ;;
        "")
            # 默认停止
            if check_status; then
                stop_system
                cleanup
            fi
            ;;
        *)
            log_error "未知选项: $1"
            show_help
            exit 1
            ;;
    esac
}

# 捕获信号
trap 'log_warn "收到中断信号，正在退出..."; exit 130' INT TERM

# 执行主函数
main "$@"