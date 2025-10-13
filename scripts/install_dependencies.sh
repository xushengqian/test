#!/bin/bash
# FreeSWITCH 性能优化依赖安装脚本

set -e

echo "==================================================="
echo "安装 FreeSWITCH 性能优化所需依赖"
echo "==================================================="

# 检查操作系统
if [ -f /etc/debian_version ]; then
    OS="debian"
elif [ -f /etc/redhat-release ]; then
    OS="redhat"
else
    echo "不支持的操作系统"
    exit 1
fi

echo "检测到操作系统: $OS"

# Debian/Ubuntu 系统
if [ "$OS" = "debian" ]; then
    echo "更新软件包列表..."
    apt-get update
    
    echo "安装系统工具..."
    apt-get install -y \
        htop \
        iotop \
        sysstat \
        net-tools \
        ethtool \
        iftop \
        nethogs \
        tcpdump \
        ngrep \
        sngrep \
        sqlite3 \
        redis-server \
        memcached \
        fail2ban \
        rsyslog
    
    echo "安装 Python 依赖..."
    apt-get install -y \
        python3 \
        python3-pip \
        python3-venv
    
    echo "安装性能测试工具..."
    apt-get install -y \
        sipp \
        iperf3 \
        stress-ng
    
# RedHat/CentOS 系统
elif [ "$OS" = "redhat" ]; then
    echo "安装 EPEL 仓库..."
    yum install -y epel-release
    
    echo "安装系统工具..."
    yum install -y \
        htop \
        iotop \
        sysstat \
        net-tools \
        ethtool \
        iftop \
        nethogs \
        tcpdump \
        ngrep \
        sngrep \
        sqlite \
        redis \
        memcached \
        fail2ban \
        rsyslog
    
    echo "安装 Python 依赖..."
    yum install -y \
        python3 \
        python3-pip
    
    echo "安装性能测试工具..."
    yum install -y \
        sipp \
        iperf3 \
        stress-ng
fi

# 安装 Python 包
echo "安装 Python 监控库..."
pip3 install --upgrade pip
pip3 install \
    psutil \
    aiohttp \
    asyncio \
    redis \
    pymemcache \
    matplotlib \
    pandas \
    numpy

# 配置 Redis（如果需要）
if command -v redis-server >/dev/null 2>&1; then
    echo "配置 Redis..."
    cat >> /etc/redis/redis.conf << EOF

# FreeSWITCH 性能优化配置
maxmemory 2gb
maxmemory-policy allkeys-lru
save ""
appendonly no
tcp-backlog 511
tcp-keepalive 60
databases 16
EOF
    
    # 重启 Redis
    systemctl restart redis-server || systemctl restart redis
fi

# 配置 Memcached（如果需要）
if command -v memcached >/dev/null 2>&1; then
    echo "配置 Memcached..."
    cat > /etc/default/memcached << EOF
# FreeSWITCH 性能优化配置
ENABLE_MEMCACHED=yes
MEMCACHED_MEMORY=1024
MEMCACHED_USER=memcache
MEMCACHED_CONNECTIONS=10000
MEMCACHED_THREADS=4
EOF
    
    # 重启 Memcached
    systemctl restart memcached
fi

# 安装监控服务
echo "创建监控服务..."
cat > /etc/systemd/system/freeswitch-monitor.service << EOF
[Unit]
Description=FreeSWITCH Performance Monitor
After=network.target freeswitch.service

[Service]
Type=simple
User=freeswitch
Group=freeswitch
WorkingDirectory=/workspace/scripts
ExecStart=/usr/bin/python3 /workspace/scripts/performance_monitor.py --save
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# 重新加载 systemd
systemctl daemon-reload

echo ""
echo "==================================================="
echo "依赖安装完成！"
echo "==================================================="
echo ""
echo "可用的工具："
echo "  - htop: 系统资源监控"
echo "  - iotop: IO 监控"
echo "  - iftop: 网络流量监控"
echo "  - sngrep: SIP 消息捕获"
echo "  - sipp: SIP 性能测试"
echo "  - redis-cli: Redis 客户端"
echo ""
echo "启动监控服务："
echo "  systemctl start freeswitch-monitor"
echo ""
echo "运行压力测试："
echo "  python3 /workspace/scripts/stress_test.py --calls 100 --rate 10"
echo "==================================================="