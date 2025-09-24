#!/bin/bash

# 指标调度系统状态检查脚本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

log_status() {
    echo -e "${BLUE}[STATUS]${NC} $1"
}

# 检查进程状态
check_process_status() {
    local pid=$1
    local name=$2
    
    if [ -z "$pid" ]; then
        log_error "$name: PID not found"
        return 1
    fi
    
    if kill -0 $pid 2>/dev/null; then
        log_info "$name: Running (PID: $pid)"
        return 0
    else
        log_error "$name: Not running (PID: $pid)"
        return 1
    fi
}

# 检查端口状态
check_port_status() {
    local port=$1
    local service=$2
    
    if netstat -tuln 2>/dev/null | grep -q ":$port "; then
        log_info "$service: Port $port is listening"
        return 0
    else
        log_error "$service: Port $port is not listening"
        return 1
    fi
}

# 检查HTTP服务状态
check_http_status() {
    local url=$1
    local service=$2
    
    if curl -f -s "$url" > /dev/null 2>&1; then
        log_info "$service: HTTP service is responding"
        return 0
    else
        log_error "$service: HTTP service is not responding"
        return 1
    fi
}

# 检查数据库连接
check_database_status() {
    log_status "Checking database connection..."
    
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
        database=os.getenv('DB_DATABASE', 'metric_scheduler'),
        connect_timeout=5
    )
    cursor = conn.cursor()
    cursor.execute('SELECT COUNT(*) FROM metric_definitions')
    metrics_count = cursor.fetchone()[0]
    cursor.execute('SELECT COUNT(*) FROM execution_tasks WHERE status = \"RUNNING\"')
    running_tasks = cursor.fetchone()[0]
    conn.close()
    print(f'Database: Connected (Metrics: {metrics_count}, Running tasks: {running_tasks})')
except Exception as e:
    print(f'Database: Connection failed - {e}')
    exit(1)
" 2>/dev/null
    
    if [ $? -eq 0 ]; then
        log_info "Database connection successful"
        return 0
    else
        log_error "Database connection failed"
        return 1
    fi
}

# 获取系统统计信息
get_system_stats() {
    log_status "Getting system statistics..."
    
    # API统计
    if curl -f -s "http://localhost:8000/statistics/tasks" > /dev/null 2>&1; then
        stats=$(curl -s "http://localhost:8000/statistics/tasks")
        echo -e "${BLUE}Task Statistics:${NC}"
        echo "$stats" | python3 -m json.tool 2>/dev/null || echo "$stats"
        echo
    fi
    
    # 节点统计
    if curl -f -s "http://localhost:8000/statistics/nodes" > /dev/null 2>&1; then
        stats=$(curl -s "http://localhost:8000/statistics/nodes")
        echo -e "${BLUE}Node Statistics:${NC}"
        echo "$stats" | python3 -m json.tool 2>/dev/null || echo "$stats"
        echo
    fi
    
    # 系统状态
    if curl -f -s "http://localhost:8000/status" > /dev/null 2>&1; then
        status=$(curl -s "http://localhost:8000/status")
        echo -e "${BLUE}System Status:${NC}"
        echo "$status" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(f\"Scheduler Running: {data['scheduler']['running']}\")
    print(f\"Health Status: {data['health']['healthy']}\")
    print(f\"Timestamp: {data['timestamp']}\")
except:
    pass
" 2>/dev/null
        echo
    fi
}

# 检查日志文件
check_logs() {
    log_status "Checking log files..."
    
    if [ -d "logs" ]; then
        for logfile in logs/*.log; do
            if [ -f "$logfile" ]; then
                size=$(du -h "$logfile" | cut -f1)
                lines=$(wc -l < "$logfile")
                log_info "$(basename $logfile): $size ($lines lines)"
                
                # 检查最近的错误
                recent_errors=$(tail -100 "$logfile" | grep -i error | wc -l)
                if [ $recent_errors -gt 0 ]; then
                    log_warn "$(basename $logfile): $recent_errors recent errors found"
                fi
            fi
        done
    else
        log_warn "Logs directory not found"
    fi
}

# 主函数
main() {
    echo -e "${BLUE}=== Metric Scheduler System Status ===${NC}\n"
    
    # 检查服务进程状态
    log_status "Checking service processes..."
    
    api_running=false
    scheduler_running=false
    
    if [ -f "logs/services.env" ]; then
        source logs/services.env
        
        if check_process_status "$API_PID" "API Server"; then
            api_running=true
        fi
        
        if check_process_status "$SCHEDULER_PID" "Scheduler"; then
            scheduler_running=true
        fi
    else
        log_warn "Service PID file not found, checking by process name..."
        
        if pgrep -f "uvicorn src.api:app" > /dev/null; then
            log_info "API Server: Running"
            api_running=true
        else
            log_error "API Server: Not running"
        fi
        
        if pgrep -f "python src/main.py" > /dev/null; then
            log_info "Scheduler: Running"
            scheduler_running=true
        else
            log_error "Scheduler: Not running"
        fi
    fi
    
    echo
    
    # 检查端口状态
    log_status "Checking port status..."
    check_port_status 8000 "API Server"
    check_port_status 9090 "Metrics Endpoint"
    echo
    
    # 检查HTTP服务状态
    log_status "Checking HTTP services..."
    check_http_status "http://localhost:8000/health" "Health Check"
    check_http_status "http://localhost:8000/metrics" "Metrics Endpoint"
    echo
    
    # 检查数据库状态
    check_database_status
    echo
    
    # 获取系统统计信息
    if [ "$api_running" = true ]; then
        get_system_stats
    else
        log_warn "API server not running, skipping statistics"
        echo
    fi
    
    # 检查日志文件
    check_logs
    echo
    
    # 总结状态
    echo -e "${BLUE}=== Summary ===${NC}"
    if [ "$api_running" = true ] && [ "$scheduler_running" = true ]; then
        log_info "System Status: All services are running"
        echo -e "${GREEN}✓ System is healthy${NC}"
    elif [ "$api_running" = true ] || [ "$scheduler_running" = true ]; then
        log_warn "System Status: Some services are running"
        echo -e "${YELLOW}⚠ System is partially running${NC}"
    else
        log_error "System Status: No services are running"
        echo -e "${RED}✗ System is down${NC}"
    fi
    
    echo
    echo "Use './scripts/start.sh' to start services"
    echo "Use './scripts/stop.sh' to stop services"
    echo "Use 'tail -f logs/scheduler.log' to view logs"
}

# 执行主函数
main "$@"