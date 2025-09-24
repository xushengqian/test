#!/bin/bash

# 指标调度系统启动脚本

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

# 检查Python版本
check_python() {
    if ! command -v python3 &> /dev/null; then
        log_error "Python3 is not installed"
        exit 1
    fi
    
    python_version=$(python3 -c 'import sys; print(".".join(map(str, sys.version_info[:2])))')
    if [[ $(echo "$python_version < 3.8" | bc -l) -eq 1 ]]; then
        log_error "Python 3.8+ is required, current version: $python_version"
        exit 1
    fi
    
    log_info "Python version: $python_version"
}

# 检查依赖
check_dependencies() {
    log_info "Checking dependencies..."
    
    if [ ! -f "requirements.txt" ]; then
        log_error "requirements.txt not found"
        exit 1
    fi
    
    # 检查虚拟环境
    if [ ! -d "venv" ]; then
        log_info "Creating virtual environment..."
        python3 -m venv venv
    fi
    
    # 激活虚拟环境
    source venv/bin/activate
    
    # 安装依赖
    log_info "Installing dependencies..."
    pip install -r requirements.txt
}

# 检查配置文件
check_config() {
    log_info "Checking configuration..."
    
    if [ ! -f ".env" ]; then
        if [ -f ".env.example" ]; then
            log_warn ".env file not found, copying from .env.example"
            cp .env.example .env
            log_warn "Please edit .env file with your configuration"
        else
            log_error ".env file not found and no .env.example available"
            exit 1
        fi
    fi
}

# 检查数据库连接
check_database() {
    log_info "Checking database connection..."
    
    # 从.env文件读取数据库配置
    if [ -f ".env" ]; then
        export $(grep -v '^#' .env | xargs)
    fi
    
    # 使用Python检查数据库连接
    python3 -c "
import os
import pymysql
try:
    conn = pymysql.connect(
        host=os.getenv('DB_HOST', 'localhost'),
        port=int(os.getenv('DB_PORT', 3306)),
        user=os.getenv('DB_USERNAME', 'root'),
        password=os.getenv('DB_PASSWORD', ''),
        database=os.getenv('DB_DATABASE', 'metric_scheduler')
    )
    conn.close()
    print('Database connection successful')
except Exception as e:
    print(f'Database connection failed: {e}')
    exit(1)
"
    
    if [ $? -eq 0 ]; then
        log_info "Database connection successful"
    else
        log_error "Database connection failed"
        exit 1
    fi
}

# 初始化数据库
init_database() {
    log_info "Initializing database..."
    
    if [ -f "database/schema.sql" ]; then
        # 使用Python执行数据库初始化
        python3 -c "
import sys
sys.path.insert(0, '.')
from src.database import init_database
try:
    init_database()
    print('Database initialized successfully')
except Exception as e:
    print(f'Database initialization failed: {e}')
    exit(1)
"
    else
        log_warn "database/schema.sql not found, skipping database initialization"
    fi
}

# 创建必要的目录
create_directories() {
    log_info "Creating necessary directories..."
    
    mkdir -p logs
    mkdir -p config
    
    log_info "Directories created"
}

# 启动服务
start_services() {
    log_info "Starting Metric Scheduler services..."
    
    # 激活虚拟环境
    source venv/bin/activate
    
    # 设置Python路径
    export PYTHONPATH=$(pwd)
    
    # 启动API服务器（后台运行）
    log_info "Starting API server..."
    nohup python -m uvicorn src.api:app --host 0.0.0.0 --port 8000 > logs/api.log 2>&1 &
    API_PID=$!
    echo $API_PID > logs/api.pid
    
    # 等待API服务器启动
    sleep 5
    
    # 检查API服务器是否启动成功
    if curl -f http://localhost:8000/health > /dev/null 2>&1; then
        log_info "API server started successfully (PID: $API_PID)"
    else
        log_error "API server failed to start"
        kill $API_PID 2>/dev/null || true
        exit 1
    fi
    
    # 启动调度器和执行器
    log_info "Starting scheduler and executor..."
    nohup python src/main.py > logs/scheduler.log 2>&1 &
    SCHEDULER_PID=$!
    echo $SCHEDULER_PID > logs/scheduler.pid
    
    log_info "Scheduler and executor started (PID: $SCHEDULER_PID)"
    
    # 保存所有PID
    echo "API_PID=$API_PID" > logs/services.env
    echo "SCHEDULER_PID=$SCHEDULER_PID" >> logs/services.env
    
    log_info "All services started successfully!"
    log_info "API server: http://localhost:8000"
    log_info "API documentation: http://localhost:8000/docs"
    log_info "Health check: http://localhost:8000/health"
    log_info "Metrics: http://localhost:8000/metrics"
}

# 主函数
main() {
    log_info "Starting Metric Scheduler System..."
    
    # 检查Python
    check_python
    
    # 检查依赖
    check_dependencies
    
    # 检查配置
    check_config
    
    # 检查数据库
    check_database
    
    # 初始化数据库
    init_database
    
    # 创建目录
    create_directories
    
    # 启动服务
    start_services
    
    log_info "Metric Scheduler System started successfully!"
    log_info "Use './scripts/stop.sh' to stop the services"
    log_info "Use './scripts/status.sh' to check service status"
}

# 处理中断信号
trap 'log_warn "Interrupted by user"; exit 130' INT TERM

# 执行主函数
main "$@"