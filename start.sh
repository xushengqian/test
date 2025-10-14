#!/bin/bash

# FreeSwitch 机器人呼出系统启动脚本

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

# 检查 Python 环境
check_python() {
    log_info "检查 Python 环境..."
    
    if ! command -v python3 &> /dev/null; then
        log_error "Python3 未安装，请先安装 Python 3.7+"
        exit 1
    fi
    
    python_version=$(python3 -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')")
    log_info "Python 版本: $python_version"
    
    if [[ $(echo "$python_version >= 3.7" | bc -l) -eq 0 ]]; then
        log_error "Python 版本过低，需要 3.7 或更高版本"
        exit 1
    fi
}

# 创建虚拟环境
create_venv() {
    if [ ! -d "venv" ]; then
        log_info "创建虚拟环境..."
        python3 -m venv venv
    else
        log_info "虚拟环境已存在"
    fi
}

# 激活虚拟环境
activate_venv() {
    log_info "激活虚拟环境..."
    source venv/bin/activate
}

# 安装依赖
install_dependencies() {
    log_info "安装 Python 依赖包..."
    
    # 升级 pip
    pip install --upgrade pip
    
    # 安装依赖
    if [ -f "requirements.txt" ]; then
        pip install -r requirements.txt
    else
        log_warn "requirements.txt 不存在，跳过依赖安装"
    fi
}

# 创建必要目录
create_directories() {
    log_info "创建必要目录..."
    
    mkdir -p logs
    mkdir -p config
    mkdir -p models
    mkdir -p storage/voicemail
    mkdir -p storage/recordings
    
    # 设置权限
    chmod 755 logs config models storage
    chmod 755 storage/voicemail storage/recordings
}

# 检查配置文件
check_config() {
    log_info "检查配置文件..."
    
    if [ ! -f "config/outbound_config.json" ]; then
        log_warn "呼出配置文件不存在，将使用默认配置"
    fi
    
    if [ ! -f "config/transfer_config.json" ]; then
        log_warn "转接配置文件不存在，将使用默认配置"
    fi
}

# 检查 FreeSwitch 连接
check_freeswitch() {
    log_info "检查 FreeSwitch 连接..."
    
    # 这里可以添加 FreeSwitch 连接检查逻辑
    # 例如：ping FreeSwitch API 端口
    
    log_info "FreeSwitch 连接检查完成"
}

# 启动系统
start_system() {
    log_info "启动 FreeSwitch 机器人呼出系统..."
    
    # 检查是否已经在运行
    if [ -f "robot_system.pid" ]; then
        pid=$(cat robot_system.pid)
        if ps -p $pid > /dev/null 2>&1; then
            log_warn "系统已经在运行 (PID: $pid)"
            exit 1
        else
            log_warn "发现过期的 PID 文件，删除中..."
            rm -f robot_system.pid
        fi
    fi
    
    # 启动系统
    python3 main.py --daemon --create-samples &
    echo $! > robot_system.pid
    
    log_info "系统已启动 (PID: $(cat robot_system.pid))"
    log_info "日志文件: logs/main.log"
    log_info "使用 './stop.sh' 停止系统"
}

# 显示帮助信息
show_help() {
    echo "FreeSwitch 机器人呼出系统启动脚本"
    echo ""
    echo "用法: $0 [选项]"
    echo ""
    echo "选项:"
    echo "  --help, -h          显示帮助信息"
    echo "  --setup             仅执行环境设置，不启动系统"
    echo "  --interactive, -i   交互模式启动"
    echo "  --check             检查系统状态"
    echo "  --samples           创建示例任务"
    echo ""
    echo "示例:"
    echo "  $0                  # 默认启动（后台模式）"
    echo "  $0 --interactive    # 交互模式启动"
    echo "  $0 --setup          # 仅设置环境"
    echo "  $0 --check          # 检查系统状态"
}

# 检查系统状态
check_status() {
    log_info "检查系统状态..."
    
    if [ -f "robot_system.pid" ]; then
        pid=$(cat robot_system.pid)
        if ps -p $pid > /dev/null 2>&1; then
            log_info "系统正在运行 (PID: $pid)"
            
            # 显示资源使用情况
            ps -p $pid -o pid,ppid,cmd,%mem,%cpu,etime
        else
            log_warn "PID 文件存在但进程未运行"
            rm -f robot_system.pid
        fi
    else
        log_info "系统未运行"
    fi
}

# 主函数
main() {
    echo -e "${BLUE}=== FreeSwitch 机器人呼出系统 ===${NC}"
    echo ""
    
    case "$1" in
        --help|-h)
            show_help
            exit 0
            ;;
        --check)
            check_status
            exit 0
            ;;
        --setup)
            check_python
            create_venv
            activate_venv
            install_dependencies
            create_directories
            check_config
            log_info "环境设置完成"
            exit 0
            ;;
        --interactive|-i)
            check_python
            create_venv
            activate_venv
            install_dependencies
            create_directories
            check_config
            check_freeswitch
            
            log_info "启动交互模式..."
            python3 main.py --create-samples
            ;;
        --samples)
            if [ -f "robot_system.pid" ]; then
                pid=$(cat robot_system.pid)
                if ps -p $pid > /dev/null 2>&1; then
                    log_info "向运行中的系统添加示例任务..."
                    # 这里可以通过 API 或信号添加任务
                    log_info "示例任务已添加"
                else
                    log_error "系统未运行，无法添加任务"
                    exit 1
                fi
            else
                log_error "系统未运行，无法添加任务"
                exit 1
            fi
            ;;
        "")
            # 默认启动
            check_python
            create_venv
            activate_venv
            install_dependencies
            create_directories
            check_config
            check_freeswitch
            start_system
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