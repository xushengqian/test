#!/bin/bash

# FreeSWITCH 通道监控脚本
# 用途：持续监控通道数量和状态，发送告警
# 建议：通过 cron 或 systemd timer 定期执行

set -euo pipefail

# 配置参数
THRESHOLD_WARNING=50     # 警告阈值
THRESHOLD_CRITICAL=100   # 严重阈值
LONG_CALL_DURATION=3600  # 长通话时长（秒）
FS_CLI="fs_cli -x"

# 告警配置
ENABLE_EMAIL=false
EMAIL_TO="admin@example.com"
ENABLE_WEBHOOK=false
WEBHOOK_URL="https://hooks.example.com/alert"

# Slack 配置示例（可选）
ENABLE_SLACK=false
SLACK_WEBHOOK_URL=""

# 颜色定义
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

# 时间戳
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')

# 检查 FreeSWITCH 是否运行
if ! pgrep -x "freeswitch" > /dev/null; then
    echo -e "${RED}[$TIMESTAMP] 错误: FreeSWITCH 未运行${NC}"
    exit 1
fi

# 获取当前通道数
CURRENT_CHANNELS=$($FS_CLI "show channels count" 2>/dev/null | grep -oP '^\d+' || echo "0")

# 获取系统状态
FS_STATUS=$($FS_CLI "status" 2>/dev/null || echo "无法获取状态")

# 提取关键指标
UPTIME=$(echo "$FS_STATUS" | grep -oP 'UP \K.*' || echo "N/A")
SESSION_SINCE_STARTUP=$(echo "$FS_STATUS" | grep -oP 'session\(s\) since startup' | grep -oP '\d+' || echo "0")
SESSIONS_PEAK=$(echo "$FS_STATUS" | grep -oP '\d+ session\(s\) - peak' | grep -oP '^\d+' || echo "0")

echo "========================================"
echo "FreeSWITCH 通道监控报告"
echo "时间: $TIMESTAMP"
echo "========================================"
echo ""
echo "系统信息："
echo "  运行时间: $UPTIME"
echo "  当前通道数: $CURRENT_CHANNELS"
echo "  峰值通道数: $SESSIONS_PEAK"
echo "  启动以来总会话数: $SESSION_SINCE_STARTUP"
echo ""

# 判断告警级别
ALERT_LEVEL="OK"
ALERT_COLOR=$GREEN

if [ "$CURRENT_CHANNELS" -ge "$THRESHOLD_CRITICAL" ]; then
    ALERT_LEVEL="CRITICAL"
    ALERT_COLOR=$RED
elif [ "$CURRENT_CHANNELS" -ge "$THRESHOLD_WARNING" ]; then
    ALERT_LEVEL="WARNING"
    ALERT_COLOR=$YELLOW
fi

echo -e "告警级别: ${ALERT_COLOR}${ALERT_LEVEL}${NC}"
echo ""

# 检查长时间运行的通道
echo "检查长时间运行的通道..."
LONG_CALLS=0

if [ "$CURRENT_CHANNELS" -gt 0 ]; then
    $FS_CLI "show channels as delim |" 2>/dev/null | tail -n +2 | while IFS='|' read -r uuid direction created_time name state cid_name cid_num ip_addr dest rest; do
        
        # 跳过空行
        if [ -z "$uuid" ] || [[ "$uuid" =~ ^[[:space:]]*$ ]] || [[ "$uuid" =~ "total" ]]; then
            continue
        fi
        
        # 计算时长
        if [[ "$created_time" =~ ([0-9]{4})-([0-9]{2})-([0-9]{2})[[:space:]]([0-9]{2}):([0-9]{2}):([0-9]{2}) ]]; then
            created_epoch=$(date -d "$created_time" +%s 2>/dev/null || echo "0")
            current_epoch=$(date +%s)
            duration=$((current_epoch - created_epoch))
            
            if [ $duration -gt $LONG_CALL_DURATION ]; then
                hours=$((duration / 3600))
                minutes=$(((duration % 3600) / 60))
                echo -e "  ${YELLOW}长通话${NC}: $cid_num -> $dest (时长: ${hours}h ${minutes}m)"
                ((LONG_CALLS++))
            fi
        fi
    done
    
    if [ $LONG_CALLS -eq 0 ]; then
        echo "  没有发现异常长时间通话"
    fi
else
    echo "  当前无活动通道"
fi

echo ""

# 发送告警函数
send_alert() {
    local level=$1
    local message=$2
    
    # 邮件告警
    if [ "$ENABLE_EMAIL" = true ]; then
        echo "$message" | mail -s "FreeSWITCH Alert: $level" "$EMAIL_TO"
    fi
    
    # Webhook 告警
    if [ "$ENABLE_WEBHOOK" = true ]; then
        curl -X POST "$WEBHOOK_URL" \
            -H "Content-Type: application/json" \
            -d "{\"level\":\"$level\",\"message\":\"$message\",\"timestamp\":\"$TIMESTAMP\"}" \
            &> /dev/null
    fi
    
    # Slack 告警
    if [ "$ENABLE_SLACK" = true ] && [ -n "$SLACK_WEBHOOK_URL" ]; then
        local slack_color="good"
        case "$level" in
            "CRITICAL") slack_color="danger" ;;
            "WARNING") slack_color="warning" ;;
        esac
        
        curl -X POST "$SLACK_WEBHOOK_URL" \
            -H "Content-Type: application/json" \
            -d "{\"attachments\":[{\"color\":\"$slack_color\",\"title\":\"FreeSWITCH Alert\",\"text\":\"$message\",\"footer\":\"$TIMESTAMP\"}]}" \
            &> /dev/null
    fi
}

# 根据告警级别发送通知
if [ "$ALERT_LEVEL" != "OK" ]; then
    ALERT_MESSAGE="FreeSWITCH 通道数告警: 当前通道数 $CURRENT_CHANNELS (阈值: 警告=$THRESHOLD_WARNING, 严重=$THRESHOLD_CRITICAL)"
    
    if [ $LONG_CALLS -gt 0 ]; then
        ALERT_MESSAGE="$ALERT_MESSAGE, 发现 $LONG_CALLS 个长时间运行的通道"
    fi
    
    echo -e "${ALERT_COLOR}发送告警: $ALERT_MESSAGE${NC}"
    send_alert "$ALERT_LEVEL" "$ALERT_MESSAGE"
fi

# 生成简单的指标文件（可用于 Prometheus 等监控系统）
METRICS_FILE="/tmp/freeswitch_metrics.txt"
cat > "$METRICS_FILE" << EOF
# HELP freeswitch_channels_current Current number of active channels
# TYPE freeswitch_channels_current gauge
freeswitch_channels_current $CURRENT_CHANNELS

# HELP freeswitch_channels_peak Peak number of channels
# TYPE freeswitch_channels_peak gauge
freeswitch_channels_peak $SESSIONS_PEAK

# HELP freeswitch_long_calls Number of long running calls
# TYPE freeswitch_long_calls gauge
freeswitch_long_calls $LONG_CALLS

# HELP freeswitch_alert_level Current alert level (0=OK, 1=WARNING, 2=CRITICAL)
# TYPE freeswitch_alert_level gauge
freeswitch_alert_level $([ "$ALERT_LEVEL" = "OK" ] && echo 0 || [ "$ALERT_LEVEL" = "WARNING" ] && echo 1 || echo 2)
EOF

echo "指标已导出到: $METRICS_FILE"
echo ""
echo "========================================"
echo "监控检查完成"
echo "========================================"

# 返回退出码（可用于监控系统）
case "$ALERT_LEVEL" in
    "OK") exit 0 ;;
    "WARNING") exit 1 ;;
    "CRITICAL") exit 2 ;;
esac
