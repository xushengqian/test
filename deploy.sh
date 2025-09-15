#!/bin/bash

# FreeSWITCH 呼入转人工功能部署脚本

echo "开始部署 FreeSWITCH 呼入转人工功能..."

# 检查FreeSWITCH是否安装
if ! command -v fs_cli &> /dev/null; then
    echo "错误: FreeSWITCH 未安装或未在PATH中"
    exit 1
fi

# 设置FreeSWITCH目录（根据实际安装路径调整）
FS_CONF_DIR="/usr/local/freeswitch/conf"
FS_SCRIPTS_DIR="/usr/local/freeswitch/scripts"
FS_SOUNDS_DIR="/usr/local/freeswitch/sounds/en/us/callie/ivr"

# 创建必要的目录
echo "创建必要的目录..."
mkdir -p "$FS_CONF_DIR/dialplan/default"
mkdir -p "$FS_SCRIPTS_DIR"
mkdir -p "$FS_SOUNDS_DIR"

# 复制配置文件
echo "复制配置文件..."
cp dialplan.xml "$FS_CONF_DIR/dialplan/default/"
cp *.lua "$FS_SCRIPTS_DIR/"

# 设置权限
echo "设置文件权限..."
chmod +x "$FS_SCRIPTS_DIR"/*.lua
chmod 644 "$FS_CONF_DIR/dialplan/default/dialplan.xml"

# 检查音频文件
echo "检查音频文件..."
AUDIO_FILES=(
    "ivr-please_hold_while_party_answered.wav"
    "ivr-hold_music.wav"
    "ivr-please_wait.wav"
    "ivr-call_being_transferred.wav"
    "ivr-connecting_you.wav"
    "ivr-no_one_available.wav"
    "ivr-try_again_later.wav"
    "ivr-goodbye.wav"
    "ivr-welcome.wav"
)

echo "请确保以下音频文件存在于 $FS_SOUNDS_DIR:"
for file in "${AUDIO_FILES[@]}"; do
    if [ -f "$FS_SOUNDS_DIR/$file" ]; then
        echo "✓ $file"
    else
        echo "✗ $file (缺失)"
    fi
done

# 重新加载FreeSWITCH配置
echo "重新加载FreeSWITCH配置..."
fs_cli -x "reloadxml" 2>/dev/null || echo "警告: 无法自动重新加载配置，请手动执行: fs_cli -x 'reloadxml'"

echo ""
echo "部署完成！"
echo ""
echo "下一步操作："
echo "1. 检查音频文件是否完整"
echo "2. 修改 advanced_transfer_to_agent.lua 中的坐席分机配置"
echo "3. 重启FreeSWITCH服务"
echo "4. 测试呼入转人工功能"
echo ""
echo "配置文件位置："
echo "- 拨号计划: $FS_CONF_DIR/dialplan/default/dialplan.xml"
echo "- Lua脚本: $FS_SCRIPTS_DIR/"
echo "- 音频文件: $FS_SOUNDS_DIR/"
echo ""
echo "测试命令："
echo "fs_cli -x 'originate loopback/1001 &echo'"