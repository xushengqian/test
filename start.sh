#!/bin/bash

echo "🚀 启动智能外呼系统..."

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo "❌ 未找到 Node.js，请先安装 Node.js 18+"
    exit 1
fi

# 检查并安装依赖
if [ ! -d "node_modules" ]; then
    echo "📦 安装后端依赖..."
    npm install
fi

if [ ! -d "client/node_modules" ]; then
    echo "📦 安装前端依赖..."
    cd client && npm install && cd ..
fi

# 检查环境配置
if [ ! -f ".env" ]; then
    echo "📝 创建环境配置文件..."
    cp .env.example .env
    echo "⚠️  请编辑 .env 文件配置必要的 API 密钥"
fi

# 启动 Redis (如果已安装)
if command -v redis-server &> /dev/null; then
    echo "🔴 启动 Redis..."
    redis-server --daemonize yes
fi

# 启动应用
echo "✅ 启动应用..."
npm run dev

echo "🎉 系统已启动！"
echo "📱 访问 http://localhost:5173 查看界面"
echo "🔌 API 服务运行在 http://localhost:3000"