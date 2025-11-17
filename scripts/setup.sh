#!/bin/bash
# FreeSWITCH + MRCP 实时语音流项目安装脚本

set -e

echo "=========================================="
echo "FreeSWITCH + MRCP 实时语音流项目安装"
echo "=========================================="

# 检查是否为 root 用户
if [ "$EUID" -ne 0 ]; then 
    echo "请使用 root 权限运行此脚本"
    exit 1
fi

# FreeSWITCH 配置目录
FS_CONF_DIR="/etc/freeswitch"
FS_SCRIPTS_DIR="/usr/share/freeswitch/scripts"

# 检查 FreeSWITCH 是否安装
if [ ! -d "$FS_CONF_DIR" ]; then
    echo "错误: 未找到 FreeSWITCH 配置目录 $FS_CONF_DIR"
    echo "请先安装 FreeSWITCH"
    exit 1
fi

echo "1. 复制 FreeSWITCH 配置文件..."

# 复制 MRCP 配置
if [ -f "freeswitch/mrcp.conf.xml" ]; then
    cp freeswitch/mrcp.conf.xml "$FS_CONF_DIR/autoload_configs/"
    echo "  ✓ MRCP 配置文件已复制"
else
    echo "  ✗ 未找到 mrcp.conf.xml"
fi

# 复制 Dialplan 配置
if [ -d "freeswitch/dialplan" ]; then
    mkdir -p "$FS_CONF_DIR/dialplan"
    cp freeswitch/dialplan/*.xml "$FS_CONF_DIR/dialplan/"
    echo "  ✓ Dialplan 配置已复制"
else
    echo "  ✗ 未找到 Dialplan 配置"
fi

echo "2. 复制 Lua 脚本..."

# 复制 Lua 脚本
if [ -f "lua/mrcp_realtime_stream.lua" ]; then
    mkdir -p "$FS_SCRIPTS_DIR"
    cp lua/mrcp_realtime_stream.lua "$FS_SCRIPTS_DIR/"
    chmod +x "$FS_SCRIPTS_DIR/mrcp_realtime_stream.lua"
    echo "  ✓ Lua 脚本已复制"
else
    echo "  ✗ 未找到 Lua 脚本"
fi

echo "3. 检查 FreeSWITCH 模块..."

# 检查 mod_mrcp 是否已加载
if grep -q "mod_mrcp" "$FS_CONF_DIR/autoload_configs/modules.conf.xml" 2>/dev/null; then
    echo "  ✓ mod_mrcp 模块已配置"
else
    echo "  ⚠ mod_mrcp 模块未在 modules.conf.xml 中找到"
    echo "    请手动添加: <load module=\"mod_mrcp\"/>"
fi

echo "4. 设置权限..."

# 设置文件权限
chown -R freeswitch:freeswitch "$FS_CONF_DIR/autoload_configs/mrcp.conf.xml" 2>/dev/null || true
chown -R freeswitch:freeswitch "$FS_CONF_DIR/dialplan/" 2>/dev/null || true
chown -R freeswitch:freeswitch "$FS_SCRIPTS_DIR/mrcp_realtime_stream.lua" 2>/dev/null || true

echo ""
echo "=========================================="
echo "安装完成！"
echo "=========================================="
echo ""
echo "下一步："
echo "1. 检查并配置 MRCP 服务器"
echo "2. 重启 FreeSWITCH: systemctl restart freeswitch"
echo "3. 检查日志: fs_cli -x 'console loglevel debug'"
echo "4. 测试: 拨打配置的号码 (mrcp_stream, mrcp_asr, mrcp_tts)"
echo ""
