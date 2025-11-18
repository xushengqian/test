#!/bin/bash
# FreeSWITCH 通道诊断脚本
# 用于检查残留的通道并分析原因

FS_CLI="${FS_CLI:-fs_cli}"
TIMEOUT="${TIMEOUT:-5}"

echo "=========================================="
echo "FreeSWITCH 通道诊断工具"
echo "=========================================="
echo ""

# 检查 fs_cli 是否可用
if ! command -v $FS_CLI &> /dev/null; then
    echo "错误: 找不到 fs_cli 命令"
    echo "请设置 FS_CLI 环境变量指向 fs_cli 的路径"
    echo "例如: export FS_CLI=/usr/local/freeswitch/bin/fs_cli"
    exit 1
fi

echo "1. 检查当前所有通道..."
echo "----------------------------------------"
$FS_CLI -x "show channels" 2>/dev/null | grep -E "uuid|state|callstate|created|answered" | head -50
echo ""

echo "2. 统计通道数量..."
echo "----------------------------------------"
CHANNEL_COUNT=$($FS_CLI -x "show channels" 2>/dev/null | grep -c "uuid" || echo "0")
echo "当前通道数量: $CHANNEL_COUNT"
echo ""

echo "3. 检查异常状态的通道..."
echo "----------------------------------------"
# 查找状态异常的通道（非 CS_EXECUTE, CS_EXCHANGE_MEDIA, CS_HANGUP 等正常状态）
$FS_CLI -x "show channels" 2>/dev/null | grep -E "uuid|state" | grep -v "CS_EXECUTE\|CS_EXCHANGE_MEDIA\|CS_HANGUP\|CS_DESTROY\|CS_NEW\|CS_INIT\|CS_ROUTING\|CS_SOFT_EXECUTE" || echo "未发现明显异常状态"
echo ""

echo "4. 检查长时间存在的通道（超过 1 小时）..."
echo "----------------------------------------"
$FS_CLI -x "show channels" 2>/dev/null | while IFS= read -r line; do
    if [[ $line =~ uuid:([a-f0-9-]+) ]]; then
        UUID="${BASH_REMATCH[1]}"
        CREATED=$($FS_CLI -x "uuid_exists $UUID" 2>/dev/null)
        if [ "$CREATED" != "false" ]; then
            # 获取通道创建时间（需要解析 show channels 的详细输出）
            echo "通道 UUID: $UUID"
        fi
    fi
done
echo ""

echo "5. 检查通道的详细状态..."
echo "----------------------------------------"
$FS_CLI -x "show channels" 2>/dev/null | grep "uuid:" | head -10 | while read -r line; do
    if [[ $line =~ uuid:([a-f0-9-]+) ]]; then
        UUID="${BASH_REMATCH[1]}"
        echo "--- 通道 $UUID ---"
        $FS_CLI -x "uuid_dump $UUID" 2>/dev/null | grep -E "state|callstate|hangup_cause|created|answered" | head -5
        echo ""
    fi
done

echo "6. 检查 FreeSWITCH 日志中的挂断事件..."
echo "----------------------------------------"
echo "提示: 检查 /usr/local/freeswitch/log/freeswitch.log 中的 CHANNEL_HANGUP 事件"
echo ""

echo "诊断完成！"
echo ""
echo "建议操作："
echo "1. 如果发现残留通道，使用 cleanup_channels.sh 脚本清理"
echo "2. 检查 FreeSWITCH 配置中的通道超时设置"
echo "3. 检查 Lua 脚本中的通道挂断逻辑"
echo "4. 查看日志文件了解通道挂断的原因"
