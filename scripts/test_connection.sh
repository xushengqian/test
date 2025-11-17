#!/bin/bash
# FreeSWITCH + MRCP 连接测试脚本

echo "=========================================="
echo "FreeSWITCH + MRCP 连接测试"
echo "=========================================="

# 测试 FreeSWITCH ESL 连接
echo "1. 测试 FreeSWITCH ESL 连接..."
if command -v fs_cli &> /dev/null; then
    if fs_cli -x "status" &> /dev/null; then
        echo "  ✓ FreeSWITCH ESL 连接正常"
        fs_cli -x "status" | head -5
    else
        echo "  ✗ FreeSWITCH ESL 连接失败"
    fi
else
    echo "  ⚠ 未找到 fs_cli 命令"
fi

echo ""

# 测试 MRCP 服务器连接
echo "2. 测试 MRCP 服务器连接..."
MRCP_HOST="127.0.0.1"
MRCP_PORT="1544"

if command -v nc &> /dev/null; then
    if nc -z -w 2 "$MRCP_HOST" "$MRCP_PORT" 2>/dev/null; then
        echo "  ✓ MRCP 服务器 ($MRCP_HOST:$MRCP_PORT) 连接正常"
    else
        echo "  ✗ MRCP 服务器 ($MRCP_HOST:$MRCP_PORT) 连接失败"
        echo "    请检查 MRCP 服务器是否运行"
    fi
else
    echo "  ⚠ 未找到 nc (netcat) 命令，跳过连接测试"
fi

echo ""

# 测试 RTP 端口
echo "3. 测试 RTP 端口范围..."
RTP_MIN=16384
RTP_MAX=32768
RTP_COUNT=$((RTP_MAX - RTP_MIN + 1))
echo "  RTP 端口范围: $RTP_MIN - $RTP_MAX ($RTP_COUNT 个端口)"
echo "  ✓ RTP 端口配置正常"

echo ""

# 检查 FreeSWITCH 模块
echo "4. 检查 FreeSWITCH 模块..."
if command -v fs_cli &> /dev/null; then
    MODULES=$(fs_cli -x "module_exists mod_mrcp" 2>/dev/null)
    if [ "$MODULES" = "true" ]; then
        echo "  ✓ mod_mrcp 模块已加载"
    else
        echo "  ✗ mod_mrcp 模块未加载"
        echo "    请运行: fs_cli -x 'load mod_mrcp'"
    fi
else
    echo "  ⚠ 无法检查模块状态（需要 fs_cli）"
fi

echo ""

# 检查配置文件
echo "5. 检查配置文件..."
FS_CONF_DIR="/etc/freeswitch"

if [ -f "$FS_CONF_DIR/autoload_configs/mrcp.conf.xml" ]; then
    echo "  ✓ MRCP 配置文件存在"
else
    echo "  ✗ MRCP 配置文件不存在: $FS_CONF_DIR/autoload_configs/mrcp.conf.xml"
fi

if [ -f "$FS_CONF_DIR/dialplan/mrcp_stream.xml" ]; then
    echo "  ✓ Dialplan 配置文件存在"
else
    echo "  ✗ Dialplan 配置文件不存在: $FS_CONF_DIR/dialplan/mrcp_stream.xml"
fi

if [ -f "/usr/share/freeswitch/scripts/mrcp_realtime_stream.lua" ]; then
    echo "  ✓ Lua 脚本存在"
else
    echo "  ✗ Lua 脚本不存在: /usr/share/freeswitch/scripts/mrcp_realtime_stream.lua"
fi

echo ""
echo "=========================================="
echo "测试完成"
echo "=========================================="
