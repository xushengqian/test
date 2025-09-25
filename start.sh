#!/bin/bash

# 机器人外呼系统启动脚本

echo "🤖 启动机器人外呼实时监控系统..."
echo "=================================="

# 检查虚拟环境是否存在
if [ ! -d "venv" ]; then
    echo "📦 创建虚拟环境..."
    python3 -m venv venv
fi

# 激活虚拟环境
echo "🔧 激活虚拟环境..."
source venv/bin/activate

# 安装依赖
echo "📥 安装依赖包..."
pip install -r requirements.txt

# 启动服务器
echo "🚀 启动服务器..."
echo ""
echo "访问地址："
echo "  监控界面: http://localhost:5000"
echo "  客服工作台: http://localhost:5000/agent"
echo ""
echo "按 Ctrl+C 停止服务器"
echo "=================================="

python app.py