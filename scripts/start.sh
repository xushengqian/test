#!/bin/bash

# 开发环境启动脚本

echo "启动开发环境..."

# 检查Node.js
if ! command -v node &> /dev/null; then
    echo "错误: Node.js未安装"
    exit 1
fi

# 检查npm
if ! command -v npm &> /dev/null; then
    echo "错误: npm未安装"
    exit 1
fi

# 安装依赖
if [ ! -d "node_modules" ]; then
    echo "安装npm依赖..."
    npm install
fi

# 检查环境变量文件
if [ ! -f ".env" ]; then
    echo "创建.env文件..."
    cp .env.example .env
    echo "请编辑.env文件配置必要的参数"
    exit 1
fi

# 启动MySQL（如果使用Docker）
if command -v docker &> /dev/null; then
    echo "启动MySQL容器..."
    docker run -d \
        --name fs-mysql \
        -e MYSQL_ROOT_PASSWORD=rootpassword \
        -e MYSQL_DATABASE=fs_robot_call \
        -e MYSQL_USER=fsrobot \
        -e MYSQL_PASSWORD=fsrobot123 \
        -p 3306:3306 \
        mysql:8.0 2>/dev/null || echo "MySQL容器已存在"
    
    echo "启动Redis容器..."
    docker run -d \
        --name fs-redis \
        -p 6379:6379 \
        redis:7-alpine 2>/dev/null || echo "Redis容器已存在"
    
    echo "启动FreeSWITCH容器..."
    docker run -d \
        --name fs-freeswitch \
        -e FREESWITCH_PASSWORD=ClueCon \
        -p 8021:8021 \
        -p 5060:5060/tcp \
        -p 5060:5060/udp \
        -p 5080:5080/tcp \
        -p 5080:5080/udp \
        -p 16384-32768:16384-32768/udp \
        drachtio/freeswitch-grpc:latest 2>/dev/null || echo "FreeSWITCH容器已存在"
    
    # 等待服务启动
    echo "等待服务启动..."
    sleep 10
fi

# 创建必要的目录
mkdir -p logs uploads audio_files

# 启动应用
echo "启动应用..."
npm run dev