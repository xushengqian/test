#!/bin/bash

# FreeSWITCH机器人外呼系统部署脚本

set -e

echo "================================================"
echo "   FreeSWITCH 机器人外呼系统部署脚本"
echo "================================================"
echo ""

# 检查依赖
check_dependencies() {
    echo "检查系统依赖..."
    
    # 检查Docker
    if ! command -v docker &> /dev/null; then
        echo "错误: Docker未安装。请先安装Docker。"
        exit 1
    fi
    
    # 检查Docker Compose
    if ! command -v docker-compose &> /dev/null; then
        echo "错误: Docker Compose未安装。请先安装Docker Compose。"
        exit 1
    fi
    
    echo "✓ 依赖检查通过"
}

# 创建必要的目录
create_directories() {
    echo "创建必要的目录..."
    
    mkdir -p freeswitch/conf
    mkdir -p freeswitch/sounds
    mkdir -p freeswitch/recordings
    mkdir -p nginx/ssl
    mkdir -p uploads
    mkdir -p logs
    mkdir -p audio_files
    mkdir -p sql
    
    echo "✓ 目录创建完成"
}

# 生成配置文件
generate_configs() {
    echo "生成配置文件..."
    
    # 复制环境变量模板
    if [ ! -f .env ]; then
        cp .env.example .env
        echo "请编辑 .env 文件，填入实际的API密钥和配置"
    fi
    
    echo "✓ 配置文件生成完成"
}

# 初始化数据库
init_database() {
    echo "初始化数据库..."
    
    # 创建数据库初始化脚本
    cat > sql/init.sql << 'EOF'
-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS fs_robot_call DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE fs_robot_call;

-- 授权用户
GRANT ALL PRIVILEGES ON fs_robot_call.* TO 'fsrobot'@'%';
FLUSH PRIVILEGES;

-- 初始化表结构将由Sequelize自动创建
EOF
    
    echo "✓ 数据库初始化脚本创建完成"
}

# 配置FreeSWITCH
configure_freeswitch() {
    echo "配置FreeSWITCH..."
    
    # 创建基本的FreeSWITCH配置
    cat > freeswitch/conf/autoload_configs/event_socket.conf.xml << 'EOF'
<configuration name="event_socket.conf" description="Socket Client">
  <settings>
    <param name="nat-map" value="false"/>
    <param name="listen-ip" value="0.0.0.0"/>
    <param name="listen-port" value="8021"/>
    <param name="password" value="ClueCon"/>
  </settings>
</configuration>
EOF
    
    echo "✓ FreeSWITCH配置完成"
}

# 配置Nginx
configure_nginx() {
    echo "配置Nginx..."
    
    cat > nginx/nginx.conf << 'EOF'
events {
    worker_connections 1024;
}

http {
    upstream app {
        server app:3000;
    }
    
    upstream ws {
        server app:3001;
    }
    
    server {
        listen 80;
        server_name localhost;
        
        # 静态文件
        location / {
            proxy_pass http://app;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
        
        # API接口
        location /api {
            proxy_pass http://app;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
        
        # WebSocket
        location /socket.io/ {
            proxy_pass http://ws;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }
    }
}
EOF
    
    echo "✓ Nginx配置完成"
}

# 启动服务
start_services() {
    echo "启动服务..."
    
    # 停止旧容器（如果存在）
    docker-compose down
    
    # 构建并启动服务
    docker-compose up -d --build
    
    echo "✓ 服务启动完成"
}

# 检查服务状态
check_status() {
    echo "检查服务状态..."
    
    sleep 10
    
    # 检查容器状态
    docker-compose ps
    
    # 检查应用健康状态
    if curl -f http://localhost:3000/api/health > /dev/null 2>&1; then
        echo "✓ 应用服务正常"
    else
        echo "⚠ 应用服务可能未完全启动，请稍后检查"
    fi
    
    echo ""
    echo "================================================"
    echo "部署完成！"
    echo ""
    echo "访问地址："
    echo "  Web界面: http://localhost"
    echo "  API接口: http://localhost:3000/api"
    echo ""
    echo "默认管理员账号："
    echo "  用户名: admin"
    echo "  密码: admin123"
    echo ""
    echo "查看日志："
    echo "  docker-compose logs -f app"
    echo ""
    echo "停止服务："
    echo "  docker-compose down"
    echo "================================================"
}

# 主函数
main() {
    check_dependencies
    create_directories
    generate_configs
    init_database
    configure_freeswitch
    configure_nginx
    start_services
    check_status
}

# 执行主函数
main