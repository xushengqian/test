#!/bin/bash

# 机器人外呼系统启动脚本

echo "=========================================="
echo "    机器人外呼系统启动脚本"
echo "=========================================="

# 检查Node.js是否安装
if ! command -v node &> /dev/null; then
    echo "错误: Node.js 未安装，请先安装 Node.js 14.0 或更高版本"
    exit 1
fi

# 检查npm是否安装
if ! command -v npm &> /dev/null; then
    echo "错误: npm 未安装，请先安装 npm"
    exit 1
fi

# 显示Node.js版本
echo "Node.js 版本: $(node --version)"
echo "npm 版本: $(npm --version)"
echo ""

# 检查package.json是否存在
if [ ! -f "package.json" ]; then
    echo "错误: package.json 文件不存在"
    exit 1
fi

# 安装依赖
echo "正在安装项目依赖..."
npm install

if [ $? -ne 0 ]; then
    echo "错误: 依赖安装失败"
    exit 1
fi

echo "依赖安装完成！"
echo ""

# 创建必要的目录
echo "创建必要的目录..."
mkdir -p logs
mkdir -p data
mkdir -p uploads

# 设置权限
chmod +x start.sh

echo "目录创建完成！"
echo ""

# 显示启动信息
echo "=========================================="
echo "    系统即将启动"
echo "=========================================="
echo "访问地址:"
echo "  主页:       http://localhost:3000"
echo "  坐席工作台: http://localhost:3000/agent"
echo "  管理后台:   http://localhost:3000/admin"
echo ""
echo "按 Ctrl+C 停止服务器"
echo "=========================================="
echo ""

# 启动服务器
echo "正在启动服务器..."
npm start