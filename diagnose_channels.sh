#!/bin/bash

# FreeSWITCH 残留通道诊断脚本
# 用途：检测和分析可能存在问题的通道

set -euo pipefail

# 颜色定义
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

# 配置参数
WARN_DURATION=3600    # 警告阈值：1小时
CRITICAL_DURATION=7200  # 严重阈值：2小时
FS_CLI="fs_cli -x"

echo "========================================"
echo "FreeSWITCH 残留通道诊断工具"
echo "========================================"
echo ""

# 检查 FreeSWITCH 是否运行
if ! pgrep -x "freeswitch" > /dev/null; then
    echo -e "${RED}错误: FreeSWITCH 未运行${NC}"
    exit 1
fi

# 检查 fs_cli 是否可用
if ! command -v fs_cli &> /dev/null; then
    echo -e "${RED}错误: fs_cli 命令未找到${NC}"
    exit 1
fi

echo "1. 获取当前通道统计信息..."
echo "----------------------------------------"

# 获取通道总数
TOTAL_CHANNELS=$($FS_CLI "show channels count" 2>/dev/null | grep -oP '^\d+' || echo "0")
echo -e "总通道数: ${GREEN}${TOTAL_CHANNELS}${NC}"

if [ "$TOTAL_CHANNELS" -eq 0 ]; then
    echo -e "${GREEN}没有活动通道，系统正常。${NC}"
    exit 0
fi

echo ""
echo "2. 分析通道详情..."
echo "----------------------------------------"

# 获取所有通道的详细信息
CHANNELS_DATA=$($FS_CLI "show channels as delim |" 2>/dev/null)

if [ -z "$CHANNELS_DATA" ]; then
    echo -e "${YELLOW}无法获取通道详细信息${NC}"
    exit 1
fi

# 解析通道信息（跳过表头）
echo "$CHANNELS_DATA" | tail -n +2 | while IFS='|' read -r uuid direction created_time name state cid_name cid_num ip_addr dest read_codec read_rate read_bit_rate write_codec write_rate write_bit_rate; do
    
    # 跳过空行和表尾
    if [ -z "$uuid" ] || [[ "$uuid" =~ ^[[:space:]]*$ ]] || [[ "$uuid" =~ "total" ]]; then
        continue
    fi
    
    # 计算通话时长（秒）
    if [[ "$created_time" =~ ([0-9]{4})-([0-9]{2})-([0-9]{2})[[:space:]]([0-9]{2}):([0-9]{2}):([0-9]{2}) ]]; then
        created_epoch=$(date -d "$created_time" +%s 2>/dev/null || echo "0")
        current_epoch=$(date +%s)
        duration=$((current_epoch - created_epoch))
    else
        duration=0
    fi
    
    # 格式化时长显示
    hours=$((duration / 3600))
    minutes=$(((duration % 3600) / 60))
    seconds=$((duration % 60))
    duration_formatted=$(printf "%02d:%02d:%02d" $hours $minutes $seconds)
    
    # 判断通道状态
    status_color=$GREEN
    status_text="正常"
    
    if [ $duration -gt $CRITICAL_DURATION ]; then
        status_color=$RED
        status_text="严重"
    elif [ $duration -gt $WARN_DURATION ]; then
        status_color=$YELLOW
        status_text="警告"
    fi
    
    # 输出可疑通道信息
    if [ "$status_text" != "正常" ]; then
        echo -e "${status_color}[$status_text]${NC} UUID: $uuid"
        echo "  方向: $direction"
        echo "  状态: $state"
        echo "  时长: $duration_formatted ($duration 秒)"
        echo "  主叫号码: $cid_num"
        echo "  被叫号码: $dest"
        echo "  名称: $name"
        echo ""
    fi
done

echo ""
echo "3. 检查长时间运行的通道..."
echo "----------------------------------------"

# 列出所有超过警告阈值的通道
LONG_RUNNING=$($FS_CLI "show channels" 2>/dev/null | awk -v warn="$WARN_DURATION" '
    /^[a-f0-9]{8}-/ {
        # 提取时长字段（假设格式为 HH:MM:SS）
        if ($0 ~ /[0-9]{2}:[0-9]{2}:[0-9]{2}/) {
            match($0, /([0-9]{2}):([0-9]{2}):([0-9]{2})/, time)
            duration = time[1] * 3600 + time[2] * 60 + time[3]
            if (duration > warn) {
                print $1, duration
            }
        }
    }
')

if [ -z "$LONG_RUNNING" ]; then
    echo -e "${GREEN}没有发现长时间运行的通道${NC}"
else
    echo -e "${YELLOW}发现以下长时间运行的通道：${NC}"
    echo "$LONG_RUNNING" | while read -r uuid duration; do
        hours=$((duration / 3600))
        minutes=$(((duration % 3600) / 60))
        seconds=$((duration % 60))
        echo "  UUID: $uuid - 时长: ${hours}h ${minutes}m ${seconds}s"
    done
fi

echo ""
echo "4. 检查应用程序状态..."
echo "----------------------------------------"

# 检查每个通道当前执行的应用
$FS_CLI "show channels" 2>/dev/null | grep -oP '^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}' | while read -r uuid; do
    app_info=$($FS_CLI "uuid_dump $uuid" 2>/dev/null | grep -i "variable_current_application:" || echo "")
    if [ -n "$app_info" ]; then
        app_name=$(echo "$app_info" | cut -d':' -f2 | xargs)
        # 检查是否卡在某些应用上
        case "$app_name" in
            "park"|"sleep"|"wait"|"playback")
                echo -e "${YELLOW}通道 $uuid 卡在应用: $app_name${NC}"
                ;;
        esac
    fi
done

echo ""
echo "5. 生成诊断报告..."
echo "----------------------------------------"

REPORT_FILE="/tmp/freeswitch_channel_diagnosis_$(date +%Y%m%d_%H%M%S).txt"

{
    echo "FreeSWITCH 通道诊断报告"
    echo "生成时间: $(date)"
    echo "========================================"
    echo ""
    echo "系统状态："
    $FS_CLI "status" 2>/dev/null
    echo ""
    echo "========================================"
    echo "所有通道详情："
    $FS_CLI "show channels verbose" 2>/dev/null
    echo ""
    echo "========================================"
    echo "呼叫统计："
    $FS_CLI "show calls" 2>/dev/null
} > "$REPORT_FILE"

echo -e "${GREEN}诊断报告已保存到: $REPORT_FILE${NC}"

echo ""
echo "6. 建议操作..."
echo "----------------------------------------"

if [ "$TOTAL_CHANNELS" -gt 0 ]; then
    echo "发现活动通道，建议执行以下操作："
    echo ""
    echo "1. 查看特定通道详情："
    echo "   fs_cli -x \"uuid_dump <UUID>\""
    echo ""
    echo "2. 强制挂断单个通道："
    echo "   fs_cli -x \"uuid_kill <UUID>\""
    echo ""
    echo "3. 挂断所有超时通道（超过2小时）："
    echo "   fs_cli -x \"hupall MANAGER_REQUEST 7200\""
    echo ""
    echo "4. 查看实时日志："
    echo "   fs_cli -x \"console loglevel debug\""
    echo ""
    echo "5. 启用 SIP 跟踪（如果是 SIP 问题）："
    echo "   fs_cli -x \"sofia profile internal siptrace on\""
fi

echo ""
echo "========================================"
echo "诊断完成"
echo "========================================"
