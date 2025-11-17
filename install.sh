#!/bin/bash
# FreeSWITCH + MRCP 实时语音流系统安装脚本

set -e

echo "========================================"
echo "FreeSWITCH + MRCP 安装脚本"
echo "========================================"
echo ""

# 检查是否为root用户
if [ "$EUID" -ne 0 ]; then 
    echo "⚠️  警告: 建议使用 sudo 运行此脚本"
    echo ""
fi

# 检测操作系统
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS=$NAME
    VER=$VERSION_ID
else
    echo "✗ 无法检测操作系统"
    exit 1
fi

echo "📋 检测到操作系统: $OS $VER"
echo ""

# 更新包管理器
echo "📦 更新包管理器..."
if [[ "$OS" == *"Ubuntu"* ]] || [[ "$OS" == *"Debian"* ]]; then
    apt-get update -qq
elif [[ "$OS" == *"CentOS"* ]] || [[ "$OS" == *"Red Hat"* ]]; then
    yum update -y -q
else
    echo "⚠️  警告: 未识别的操作系统，跳过系统包更新"
fi
echo "✓ 包管理器已更新"
echo ""

# 安装FreeSWITCH
echo "📡 安装FreeSWITCH..."
if command -v freeswitch &> /dev/null; then
    echo "  ✓ FreeSWITCH 已安装"
else
    if [[ "$OS" == *"Ubuntu"* ]] || [[ "$OS" == *"Debian"* ]]; then
        # 添加FreeSWITCH仓库
        apt-get install -y wget gnupg2
        wget -O - https://files.freeswitch.org/repo/deb/debian-release/fsstretch-archive-keyring.asc | apt-key add -
        echo "deb http://files.freeswitch.org/repo/deb/debian-release/ $(lsb_release -sc) main" > /etc/apt/sources.list.d/freeswitch.list
        
        apt-get update -qq
        apt-get install -y freeswitch freeswitch-mod-commands freeswitch-mod-console \
            freeswitch-mod-logfile freeswitch-mod-event-socket freeswitch-mod-unimrcp
        
        echo "  ✓ FreeSWITCH 安装完成"
    else
        echo "  ⚠️  请手动安装 FreeSWITCH: https://freeswitch.org/confluence/display/FREESWITCH/Installation"
    fi
fi
echo ""

# 安装UniMRCP
echo "📞 安装UniMRCP..."
if command -v unimrcpserver &> /dev/null; then
    echo "  ✓ UniMRCP 已安装"
else
    if [[ "$OS" == *"Ubuntu"* ]] || [[ "$OS" == *"Debian"* ]]; then
        apt-get install -y unimrcp-server libunimrcp-dev
        echo "  ✓ UniMRCP 安装完成"
    else
        echo "  ⚠️  请手动安装 UniMRCP: http://www.unimrcp.org/"
    fi
fi
echo ""

# 安装Python依赖
echo "🐍 安装Python依赖..."
if command -v python3 &> /dev/null; then
    PYTHON_VERSION=$(python3 --version | cut -d' ' -f2 | cut -d'.' -f1,2)
    echo "  检测到 Python $PYTHON_VERSION"
    
    # 检查pip
    if ! command -v pip3 &> /dev/null; then
        echo "  安装 pip..."
        if [[ "$OS" == *"Ubuntu"* ]] || [[ "$OS" == *"Debian"* ]]; then
            apt-get install -y python3-pip
        fi
    fi
    
    # 升级pip
    pip3 install --upgrade pip -q
    
    # 安装requirements
    if [ -f "requirements.txt" ]; then
        echo "  安装Python包..."
        pip3 install -r requirements.txt -q
        echo "  ✓ Python依赖安装完成"
    else
        echo "  ⚠️  找不到 requirements.txt"
    fi
else
    echo "  ✗ 未找到 Python 3"
    echo "  请安装 Python 3.8 或更高版本"
    exit 1
fi
echo ""

# 创建配置目录
echo "📁 配置目录..."
FREESWITCH_CONF_DIR="/etc/freeswitch"
if [ -d "$FREESWITCH_CONF_DIR" ]; then
    echo "  FreeSWITCH配置目录: $FREESWITCH_CONF_DIR"
    
    # 备份现有配置
    if [ -f "$FREESWITCH_CONF_DIR/autoload_configs/unimrcp.conf.xml" ]; then
        cp "$FREESWITCH_CONF_DIR/autoload_configs/unimrcp.conf.xml" \
           "$FREESWITCH_CONF_DIR/autoload_configs/unimrcp.conf.xml.bak.$(date +%Y%m%d_%H%M%S)"
        echo "  ✓ 已备份现有配置"
    fi
    
    # 复制配置文件
    if [ -d "config/freeswitch" ]; then
        cp -r config/freeswitch/* "$FREESWITCH_CONF_DIR/" || true
        echo "  ✓ 配置文件已复制"
    fi
else
    echo "  ⚠️  FreeSWITCH配置目录不存在: $FREESWITCH_CONF_DIR"
fi
echo ""

# 创建日志目录
echo "📝 创建日志目录..."
mkdir -p /var/log/mrcp_stream
chmod 755 /var/log/mrcp_stream
echo "  ✓ 日志目录已创建: /var/log/mrcp_stream"
echo ""

# 配置防火墙
echo "🔥 配置防火墙..."
if command -v ufw &> /dev/null; then
    # Ubuntu/Debian UFW
    ufw allow 5060/udp comment 'FreeSWITCH SIP' || true
    ufw allow 4000:5000/udp comment 'FreeSWITCH RTP' || true
    ufw allow 8021/tcp comment 'FreeSWITCH ESL' || true
    ufw allow 1544/tcp comment 'MRCP' || true
    ufw allow 8765/tcp comment 'WebSocket' || true
    echo "  ✓ UFW规则已添加"
elif command -v firewall-cmd &> /dev/null; then
    # CentOS/RHEL firewalld
    firewall-cmd --permanent --add-port=5060/udp || true
    firewall-cmd --permanent --add-port=4000-5000/udp || true
    firewall-cmd --permanent --add-port=8021/tcp || true
    firewall-cmd --permanent --add-port=1544/tcp || true
    firewall-cmd --permanent --add-port=8765/tcp || true
    firewall-cmd --reload || true
    echo "  ✓ firewalld规则已添加"
else
    echo "  ⚠️  未检测到防火墙，请手动配置以下端口:"
    echo "      - 5060/udp (SIP)"
    echo "      - 4000-5000/udp (RTP)"
    echo "      - 8021/tcp (ESL)"
    echo "      - 1544/tcp (MRCP)"
    echo "      - 8765/tcp (WebSocket)"
fi
echo ""

# 启动服务
echo "🚀 配置服务..."
if command -v systemctl &> /dev/null; then
    # 启用FreeSWITCH
    systemctl enable freeswitch || true
    
    echo "  服务已配置"
    echo ""
    echo "  启动FreeSWITCH: sudo systemctl start freeswitch"
    echo "  查看状态: sudo systemctl status freeswitch"
else
    echo "  ⚠️  systemd未找到，请手动启动FreeSWITCH"
fi
echo ""

# 完成
echo "========================================"
echo "✓ 安装完成!"
echo "========================================"
echo ""
echo "后续步骤:"
echo ""
echo "1. 启动FreeSWITCH:"
echo "   sudo systemctl start freeswitch"
echo ""
echo "2. 检查FreeSWITCH状态:"
echo "   sudo systemctl status freeswitch"
echo ""
echo "3. 连接到FreeSWITCH控制台:"
echo "   fs_cli"
echo ""
echo "4. 运行示例:"
echo "   python3 examples/basic_asr.py"
echo "   python3 examples/realtime_stream.py"
echo ""
echo "5. 查看日志:"
echo "   tail -f /var/log/freeswitch/freeswitch.log"
echo "   tail -f /var/log/mrcp_stream/mrcp_stream.log"
echo ""
echo "配置文件位置:"
echo "  - FreeSWITCH: /etc/freeswitch/"
echo "  - MRCP: ./config/mrcp_config.ini"
echo ""
echo "文档: 请查看 README.md"
echo ""
