#!/bin/bash
# Docker启动脚本

set -e

echo "========================================"
echo "启动 FreeSWITCH + MRCP 容器"
echo "========================================"
echo ""

# 检查配置文件
if [ ! -f "/etc/freeswitch/freeswitch.xml" ]; then
    echo "✗ FreeSWITCH配置文件不存在"
    exit 1
fi

# 检查MRCP配置
if [ ! -f "/app/config/mrcp_config.ini" ]; then
    echo "⚠️  MRCP配置文件不存在，使用默认配置"
fi

# 设置权限
chown -R freeswitch:freeswitch /var/log/freeswitch || true
chown -R freeswitch:freeswitch /var/lib/freeswitch || true

echo "✓ 配置检查完成"
echo ""

# 启动服务
if [ "$1" = "freeswitch" ]; then
    echo "🚀 启动FreeSWITCH..."
    exec /usr/bin/freeswitch -nonat -c
elif [ "$1" = "stream-processor" ]; then
    echo "🚀 启动音频流处理器..."
    exec python3 /app/src/stream_processor.py
elif [ "$1" = "websocket-server" ]; then
    echo "🚀 启动WebSocket服务器..."
    exec python3 /app/src/websocket_server.py
else
    exec "$@"
fi
