#!/bin/bash

# FS机器人外呼系统停止脚本

set -e

echo "=== FS机器人外呼系统停止脚本 ==="

# 停止服务
stop_services() {
    echo "停止服务..."
    
    cd docker
    
    # 优雅停止服务
    echo "正在停止所有服务..."
    docker-compose down
    
    echo "服务已停止"
}

# 清理资源（可选）
cleanup_resources() {
    read -p "是否清理所有数据（包括数据库和录音文件）？[y/N]: " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo "清理数据..."
        cd docker
        docker-compose down -v
        docker system prune -f
        echo "数据清理完成"
    else
        echo "保留数据"
    fi
}

# 显示状态
show_status() {
    echo "=== 当前状态 ==="
    cd docker
    docker-compose ps
}

# 主函数
main() {
    echo "开始停止FS机器人外呼系统..."
    
    stop_services
    show_status
    
    echo ""
    echo "是否需要清理数据？"
    cleanup_resources
    
    echo ""
    echo "=== 停止完成 ==="
    echo "系统已停止！"
    echo ""
    echo "如需重新启动，请运行: ./scripts/start.sh"
}

# 执行主函数
main "$@"