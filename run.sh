#!/bin/bash

# 企业指标调度系统启动脚本

set -e

echo "🚀 启动企业指标调度系统..."

# 检查Docker是否安装
if ! command -v docker &> /dev/null; then
    echo "❌ Docker未安装，请先安装Docker"
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose未安装，请先安装Docker Compose"
    exit 1
fi

# 检查环境配置文件
if [ ! -f .env ]; then
    echo "📝 创建环境配置文件..."
    cp .env.example .env
    echo "✅ 已创建 .env 文件，请根据需要修改配置"
fi

# 创建日志目录
mkdir -p logs

# 构建并启动服务
echo "🔨 构建Docker镜像..."
docker-compose build

echo "🚀 启动服务..."
docker-compose up -d

# 等待服务启动
echo "⏳ 等待服务启动..."
sleep 10

# 检查服务状态
echo "📊 检查服务状态..."
docker-compose ps

# 健康检查
echo "🏥 执行健康检查..."
for i in {1..30}; do
    if curl -s http://localhost:8000/health > /dev/null; then
        echo "✅ 应用服务已就绪"
        break
    fi
    echo "⏳ 等待应用服务启动... ($i/30)"
    sleep 2
done

# 显示访问信息
echo ""
echo "🎉 企业指标调度系统启动成功！"
echo ""
echo "📋 服务访问地址："
echo "   - API文档:     http://localhost:8000/docs"
echo "   - 系统监控:    http://localhost:8000/api/v1/monitoring/dashboard"
echo "   - Celery监控:  http://localhost:5555"
echo "   - 健康检查:    http://localhost:8000/health"
echo ""
echo "📊 快速测试："
echo "   curl http://localhost:8000/api/v1/metrics/companies"
echo "   curl http://localhost:8000/api/v1/system/health"
echo ""
echo "📝 查看日志："
echo "   docker-compose logs -f app"
echo "   docker-compose logs -f worker"
echo ""
echo "🛑 停止服务："
echo "   docker-compose down"
echo ""

# 显示系统状态
echo "📈 当前系统状态："
curl -s http://localhost:8000/api/v1/system/health | python3 -m json.tool 2>/dev/null || echo "系统正在启动中..."