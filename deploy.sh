#!/bin/bash

# FreeSWITCH机器人外呼系统部署脚本

set -e

echo "=== FreeSWITCH机器人外呼系统部署脚本 ==="

# 检查是否为root用户
if [ "$EUID" -ne 0 ]; then
    echo "请使用root权限运行此脚本"
    exit 1
fi

# 系统信息
echo "检查系统信息..."
OS=$(lsb_release -si 2>/dev/null || echo "Unknown")
VERSION=$(lsb_release -sr 2>/dev/null || echo "Unknown")
echo "操作系统: $OS $VERSION"

# 创建必要的目录
echo "创建系统目录..."
mkdir -p /var/log/robot_calls
mkdir -p /var/recordings
mkdir -p /etc/freeswitch/dialplan
mkdir -p /etc/freeswitch/autoload_configs
mkdir -p /usr/share/freeswitch/scripts

# 设置目录权限
chown -R freeswitch:freeswitch /var/log/robot_calls
chown -R freeswitch:freeswitch /var/recordings
chmod 755 /var/log/robot_calls
chmod 755 /var/recordings

# 安装系统依赖
echo "安装系统依赖..."
apt-get update
apt-get install -y python3 python3-pip python3-venv
apt-get install -y sqlite3
apt-get install -y espeak espeak-data
apt-get install -y curl wget

# 检查FreeSWITCH是否已安装
if ! command -v freeswitch &> /dev/null; then
    echo "FreeSWITCH未安装，开始安装..."
    
    # 添加FreeSWITCH官方仓库
    wget -O - https://files.freeswitch.org/repo/deb/debian-release/fsstretch-archive-keyring.asc | apt-key add -
    echo "deb http://files.freeswitch.org/repo/deb/debian-release/ `lsb_release -sc` main" > /etc/apt/sources.list.d/freeswitch.list
    
    apt-get update
    apt-get install -y freeswitch-meta-all
    
    # 启动FreeSWITCH服务
    systemctl enable freeswitch
    systemctl start freeswitch
    
    echo "FreeSWITCH安装完成"
else
    echo "FreeSWITCH已安装"
fi

# 创建Python虚拟环境
echo "创建Python虚拟环境..."
cd /opt
python3 -m venv robot_call_env
source robot_call_env/bin/activate

# 安装Python依赖
echo "安装Python依赖..."
pip install --upgrade pip
pip install -r /workspace/requirements.txt

# 复制配置文件
echo "复制配置文件..."
cp /workspace/dialplan/outbound_robot.xml /etc/freeswitch/dialplan/
cp /workspace/conf/autoload_configs/conference.conf.xml /etc/freeswitch/autoload_configs/

# 复制Lua脚本
echo "复制Lua脚本..."
cp /workspace/scripts/*.lua /usr/share/freeswitch/scripts/
chown freeswitch:freeswitch /usr/share/freeswitch/scripts/*.lua
chmod 755 /usr/share/freeswitch/scripts/*.lua

# 创建API服务的systemd服务文件
echo "创建系统服务..."
cat > /etc/systemd/system/robot-call-api.service << EOF
[Unit]
Description=Robot Call API Service
After=network.target freeswitch.service
Requires=freeswitch.service

[Service]
Type=simple
User=freeswitch
Group=freeswitch
WorkingDirectory=/workspace/api
Environment=PATH=/opt/robot_call_env/bin
ExecStart=/opt/robot_call_env/bin/python agent_control.py
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# 创建Web服务的nginx配置
if command -v nginx &> /dev/null; then
    echo "配置Nginx..."
    cat > /etc/nginx/sites-available/robot-call-web << EOF
server {
    listen 80;
    server_name localhost;
    
    root /workspace/web;
    index index.html;
    
    location / {
        try_files \$uri \$uri/ =404;
    }
    
    location /api/ {
        proxy_pass http://localhost:8080/api/;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF
    
    ln -sf /etc/nginx/sites-available/robot-call-web /etc/nginx/sites-enabled/
    nginx -t && systemctl reload nginx
    echo "Nginx配置完成"
fi

# 初始化数据库
echo "初始化数据库..."
cd /workspace/api
/opt/robot_call_env/bin/python -c "
import sqlite3
from agent_control import init_database
init_database()
print('数据库初始化完成')
"

# 添加示例坐席数据
echo "添加示例数据..."
sqlite3 /workspace/api/robot_calls.db << EOF
INSERT OR REPLACE INTO agent_status (agent_id, agent_name, status) VALUES 
('agent001', '坐席001', 'available'),
('agent002', '坐席002', 'available'),
('agent003', '坐席003', 'offline');
EOF

# 启动服务
echo "启动服务..."
systemctl daemon-reload
systemctl enable robot-call-api
systemctl start robot-call-api

# 重启FreeSWITCH以加载新配置
echo "重启FreeSWITCH..."
systemctl restart freeswitch

# 检查服务状态
echo "检查服务状态..."
sleep 5

if systemctl is-active --quiet freeswitch; then
    echo "✓ FreeSWITCH服务运行正常"
else
    echo "✗ FreeSWITCH服务启动失败"
fi

if systemctl is-active --quiet robot-call-api; then
    echo "✓ API服务运行正常"
else
    echo "✗ API服务启动失败"
fi

if command -v nginx &> /dev/null && systemctl is-active --quiet nginx; then
    echo "✓ Nginx服务运行正常"
fi

# 显示访问信息
echo ""
echo "=== 部署完成 ==="
echo "Web管理界面: http://localhost/"
echo "API接口: http://localhost:8080/api/"
echo "FreeSWITCH ESL: localhost:8021"
echo ""
echo "日志文件位置:"
echo "- 系统日志: /var/log/robot_calls/"
echo "- FreeSWITCH日志: /var/log/freeswitch/"
echo "- API服务日志: journalctl -u robot-call-api -f"
echo ""
echo "配置文件位置:"
echo "- FreeSWITCH配置: /etc/freeswitch/"
echo "- 系统配置: /workspace/config/"
echo ""
echo "常用命令:"
echo "- 重启API服务: systemctl restart robot-call-api"
echo "- 重启FreeSWITCH: systemctl restart freeswitch"
echo "- 查看API日志: journalctl -u robot-call-api -f"
echo "- 查看FreeSWITCH状态: fs_cli -x 'status'"

echo ""
echo "部署完成！请根据实际情况修改配置文件中的网关设置。"