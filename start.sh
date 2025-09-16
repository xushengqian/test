#!/bin/bash

# FreeSWITCH 人工外呼实时音转文系统启动脚本

echo "🚀 启动 FreeSWITCH 人工外呼实时音转文系统..."

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo "❌ Docker 未安装，请先安装 Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose 未安装，请先安装 Docker Compose"
    exit 1
fi

# 创建必要的目录
echo "📁 创建必要的目录..."
mkdir -p freeswitch/conf
mkdir -p freeswitch/scripts
mkdir -p app/services
mkdir -p templates
mkdir -p static
mkdir -p logs
mkdir -p audio
mkdir -p models

# 设置权限
echo "🔐 设置文件权限..."
chmod +x freeswitch/scripts/*.py
chmod +x freeswitch/scripts/*.lua

# 启动服务
echo "🐳 启动 Docker 服务..."
docker-compose up -d

# 等待服务启动
echo "⏳ 等待服务启动..."
sleep 10

# 检查服务状态
echo "🔍 检查服务状态..."
docker-compose ps

# 显示访问信息
echo ""
echo "✅ 系统启动完成！"
echo ""
echo "🌐 Web界面: http://localhost:8000"
echo "📊 API文档: http://localhost:8000/docs"
echo ""
echo "📋 服务状态:"
echo "   - 应用服务: http://localhost:8000"
echo "   - FreeSWITCH: localhost:5060 (SIP)"
echo "   - PostgreSQL: localhost:5432"
echo "   - Redis: localhost:6379"
echo ""
echo "📝 查看日志:"
echo "   docker-compose logs -f app"
echo "   docker-compose logs -f freeswitch"
echo ""
echo "🛑 停止服务:"
echo "   docker-compose down"
echo ""