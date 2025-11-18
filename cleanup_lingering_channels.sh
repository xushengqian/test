#!/bin/bash

# FreeSWITCH 残留通道清理脚本
# 用途：自动清理长时间存在的通道
# 建议：通过 cron 定期执行

set -euo pipefail

# 颜色定义
RED='\033[0;31m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
NC='\033[0m'

# 配置参数
MAX_DURATION=7200  # 最大通话时长（秒），默认2小时
DRY_RUN=false      # 是否为演练模式（不实际挂断）
LOG_FILE="/var/log/freeswitch/channel_cleanup.log"
FS_CLI="fs_cli -x"

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--duration)
            MAX_DURATION="$2"
            shift 2
            ;;
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        -l|--log)
            LOG_FILE="$2"
            shift 2
            ;;
        -h|--help)
            echo "用法: $0 [选项]"
            echo ""
            echo "选项："
            echo "  -d, --duration SECONDS  设置最大通话时长（秒），默认7200"
            echo "  --dry-run               演练模式，不实际挂断通道"
            echo "  -l, --log FILE          日志文件路径"
            echo "  -h, --help              显示此帮助信息"
            echo ""
            echo "示例："
            echo "  $0 -d 3600              # 清理超过1小时的通道"
            echo "  $0 --dry-run            # 演练模式"
            exit 0
            ;;
        *)
            echo "未知选项: $1"
            echo "使用 -h 或 --help 查看帮助"
            exit 1
            ;;
    esac
done

# 日志函数
log() {
    local level=$1
    shift
    local message="$@"
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] [$level] $message" | tee -a "$LOG_FILE"
}

# 检查 FreeSWITCH 是否运行
if ! pgrep -x "freeswitch" > /dev/null; then
    log "ERROR" "FreeSWITCH 未运行"
    exit 1
fi

# 检查 fs_cli 是否可用
if ! command -v fs_cli &> /dev/null; then
    log "ERROR" "fs_cli 命令未找到"
    exit 1
fi

log "INFO" "开始清理残留通道 (最大时长: ${MAX_DURATION}秒)"

if [ "$DRY_RUN" = true ]; then
    log "INFO" "*** 演练模式 - 不会实际挂断通道 ***"
fi

# 统计变量
total_channels=0
killed_channels=0
failed_channels=0

# 获取所有通道
CHANNELS_OUTPUT=$($FS_CLI "show channels as delim |" 2>/dev/null || echo "")

if [ -z "$CHANNELS_OUTPUT" ]; then
    log "INFO" "没有活动通道"
    exit 0
fi

# 解析通道信息
echo "$CHANNELS_OUTPUT" | tail -n +2 | while IFS='|' read -r uuid direction created_time name state cid_name cid_num ip_addr dest read_codec read_rate read_bit_rate write_codec write_rate write_bit_rate; do
    
    # 跳过空行和表尾
    if [ -z "$uuid" ] || [[ "$uuid" =~ ^[[:space:]]*$ ]] || [[ "$uuid" =~ "total" ]]; then
        continue
    fi
    
    ((total_channels++))
    
    # 计算通话时长
    if [[ "$created_time" =~ ([0-9]{4})-([0-9]{2})-([0-9]{2})[[:space:]]([0-9]{2}):([0-9]{2}):([0-9]{2}) ]]; then
        created_epoch=$(date -d "$created_time" +%s 2>/dev/null || echo "0")
        current_epoch=$(date +%s)
        duration=$((current_epoch - created_epoch))
    else
        duration=0
    fi
    
    # 检查是否超过最大时长
    if [ $duration -gt $MAX_DURATION ]; then
        hours=$((duration / 3600))
        minutes=$(((duration % 3600) / 60))
        seconds=$((duration % 60))
        duration_formatted=$(printf "%02d:%02d:%02d" $hours $minutes $seconds)
        
        log "WARNING" "发现残留通道: UUID=$uuid, 时长=$duration_formatted, 主叫=$cid_num, 被叫=$dest"
        
        if [ "$DRY_RUN" = false ]; then
            # 尝试挂断通道
            if $FS_CLI "uuid_kill $uuid MANAGER_REQUEST" &> /dev/null; then
                log "INFO" "成功挂断通道: $uuid"
                ((killed_channels++))
            else
                log "ERROR" "挂断通道失败: $uuid"
                ((failed_channels++))
            fi
        else
            log "INFO" "[演练] 将挂断通道: $uuid"
            ((killed_channels++))
        fi
    fi
done

# 输出统计信息
log "INFO" "清理完成"
log "INFO" "统计: 总通道数=$total_channels, 已清理=$killed_channels, 失败=$failed_channels"

if [ "$DRY_RUN" = false ] && [ $killed_channels -gt 0 ]; then
    echo -e "${GREEN}成功清理 $killed_channels 个残留通道${NC}"
fi

if [ $failed_channels -gt 0 ]; then
    echo -e "${RED}警告: $failed_channels 个通道清理失败${NC}"
    exit 1
fi

exit 0
