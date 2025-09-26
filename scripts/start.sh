#!/bin/bash

# FS机器人外呼系统启动脚本

set -e

echo "=== FS机器人外呼系统启动脚本 ==="

# 检查Docker和Docker Compose
check_dependencies() {
    echo "检查依赖..."
    
    if ! command -v docker &> /dev/null; then
        echo "错误: Docker未安装"
        exit 1
    fi
    
    if ! command -v docker-compose &> /dev/null; then
        echo "错误: Docker Compose未安装"
        exit 1
    fi
    
    echo "依赖检查通过"
}

# 创建必要的目录
create_directories() {
    echo "创建必要的目录..."
    
    mkdir -p logs
    mkdir -p data/postgres
    mkdir -p data/redis
    mkdir -p data/recordings
    mkdir -p docker/ssl
    
    echo "目录创建完成"
}

# 设置权限
set_permissions() {
    echo "设置目录权限..."
    
    # 设置录音目录权限
    sudo chown -R 999:999 data/recordings 2>/dev/null || true
    chmod -R 755 data/recordings
    
    # 设置日志目录权限
    chmod -R 755 logs
    
    echo "权限设置完成"
}

# 检查配置文件
check_config() {
    echo "检查配置文件..."
    
    if [ ! -f ".env" ]; then
        echo "创建默认.env文件..."
        cat > .env << EOF
# 数据库配置
POSTGRES_DB=robot_call_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=password123

# Redis配置
REDIS_PASSWORD=

# FreeSwitch配置
FREESWITCH_PASSWORD=ClueCon

# 后端配置
DEBUG=false
SECRET_KEY=your-secret-key-change-in-production

# 外呼配置
MAX_CONCURRENT_CALLS=100
OUTBOUND_CALLER_ID=10086

# AI服务配置（可选）
OPENAI_API_KEY=
AZURE_SPEECH_KEY=
AZURE_SPEECH_REGION=eastasia
EOF
        echo "请编辑.env文件配置相关参数"
    fi
    
    echo "配置文件检查完成"
}

# 启动服务
start_services() {
    echo "启动服务..."
    
    cd docker
    
    # 拉取最新镜像
    echo "拉取Docker镜像..."
    docker-compose pull
    
    # 构建自定义镜像
    echo "构建应用镜像..."
    docker-compose build
    
    # 启动服务
    echo "启动所有服务..."
    docker-compose up -d
    
    echo "服务启动完成"
}

# 等待服务就绪
wait_for_services() {
    echo "等待服务就绪..."
    
    # 等待数据库
    echo "等待数据库启动..."
    timeout=60
    while [ $timeout -gt 0 ]; do
        if docker-compose exec -T postgres pg_isready -U postgres &>/dev/null; then
            echo "数据库已就绪"
            break
        fi
        sleep 2
        timeout=$((timeout-2))
    done
    
    if [ $timeout -le 0 ]; then
        echo "警告: 数据库启动超时"
    fi
    
    # 等待后端服务
    echo "等待后端服务启动..."
    timeout=60
    while [ $timeout -gt 0 ]; do
        if curl -s http://localhost:8000/health &>/dev/null; then
            echo "后端服务已就绪"
            break
        fi
        sleep 2
        timeout=$((timeout-2))
    done
    
    if [ $timeout -le 0 ]; then
        echo "警告: 后端服务启动超时"
    fi
    
    echo "服务就绪检查完成"
}

# 显示服务状态
show_status() {
    echo "=== 服务状态 ==="
    cd docker
    docker-compose ps
    
    echo ""
    echo "=== 访问地址 ==="
    echo "前端管理界面: http://localhost"
    echo "API文档: http://localhost:8000/docs"
    echo "系统状态: http://localhost:8000/health"
    
    echo ""
    echo "=== 日志查看 ==="
    echo "查看所有日志: docker-compose logs -f"
    echo "查看后端日志: docker-compose logs -f backend"
    echo "查看FreeSwitch日志: docker-compose logs -f freeswitch"
}

# 主函数
main() {
    echo "开始启动FS机器人外呼系统..."
    
    check_dependencies
    create_directories
    set_permissions
    check_config
    start_services
    wait_for_services
    show_status
    
    echo ""
    echo "=== 启动完成 ==="
    echo "系统已成功启动！"
    echo ""
    echo "如需停止系统，请运行: ./scripts/stop.sh"
    echo "如需查看日志，请运行: docker-compose logs -f"
}

# 执行主函数
main "$@"