#!/bin/bash

# FreeSWITCH 通道残留问题诊断脚本
# 用于检查和分析残留的通道

FS_CLI="/usr/local/freeswitch/bin/fs_cli"
LOG_FILE="/tmp/freeswitch_channel_diagnosis_$(date +%Y%m%d_%H%M%S).log"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log() {
    echo -e "$1" | tee -a "$LOG_FILE"
}

log_info() {
    log "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    log "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    log "${RED}[ERROR]${NC} $1"
}

# 检查 fs_cli 是否存在
if [ ! -f "$FS_CLI" ]; then
    # 尝试其他常见路径
    if [ -f "/usr/bin/fs_cli" ]; then
        FS_CLI="/usr/bin/fs_cli"
    elif [ -f "/opt/freeswitch/bin/fs_cli" ]; then
        FS_CLI="/opt/freeswitch/bin/fs_cli"
    else
        log_error "无法找到 fs_cli，请手动指定路径"
        exit 1
    fi
fi

log_info "开始诊断 FreeSWITCH 通道..."
log_info "日志文件: $LOG_FILE"

# 1. 获取所有通道
log_info "=== 步骤 1: 获取所有通道列表 ==="
CHANNELS_JSON=$($FS_CLI -x "show channels as json" 2>/dev/null)
if [ $? -ne 0 ]; then
    log_error "无法连接到 FreeSWITCH，请检查服务是否运行"
    exit 1
fi

CHANNEL_COUNT=$(echo "$CHANNELS_JSON" | grep -o '"uuid"' | wc -l)
log_info "当前通道数量: $CHANNEL_COUNT"

if [ "$CHANNEL_COUNT" -eq 0 ]; then
    log_info "没有发现通道，系统正常"
    exit 0
fi

# 2. 分析每个通道的详细信息
log_info "=== 步骤 2: 分析通道详细信息 ==="
echo "$CHANNELS_JSON" | grep -o '"uuid":"[^"]*"' | sed 's/"uuid":"\([^"]*\)"/\1/' | while read UUID; do
    if [ -z "$UUID" ]; then
        continue
    fi
    
    log_info "--- 分析通道: $UUID ---"
    
    # 获取通道详细信息
    CHANNEL_INFO=$($FS_CLI -x "show channels $UUID" 2>/dev/null)
    
    # 提取关键信息
    STATE=$(echo "$CHANNEL_INFO" | grep -i "state" | head -1)
    DURATION=$(echo "$CHANNEL_INFO" | grep -i "duration" | head -1)
    CREATED=$(echo "$CHANNEL_INFO" | grep -i "created" | head -1)
    
    log "状态: $STATE"
    log "持续时间: $DURATION"
    log "创建时间: $CREATED"
    
    # 检查是否可能是僵尸通道（持续时间过长）
    DURATION_SEC=$(echo "$DURATION" | grep -oE '[0-9]+' | head -1)
    if [ ! -z "$DURATION_SEC" ] && [ "$DURATION_SEC" -gt 3600 ]; then
        log_warn "通道 $UUID 持续时间超过 1 小时，可能是僵尸通道"
    fi
    
    # 检查状态
    if echo "$STATE" | grep -qi "hangup\|destroy\|done"; then
        log_warn "通道 $UUID 状态异常，应该已被销毁但仍存在"
    fi
done

# 3. 检查事件日志
log_info "=== 步骤 3: 检查最近的挂断事件 ==="
FS_LOG="/usr/local/freeswitch/log/freeswitch.log"
if [ ! -f "$FS_LOG" ]; then
    # 尝试其他路径
    if [ -f "/var/log/freeswitch/freeswitch.log" ]; then
        FS_LOG="/var/log/freeswitch/freeswitch.log"
    elif [ -f "/opt/freeswitch/log/freeswitch.log" ]; then
        FS_LOG="/opt/freeswitch/log/freeswitch.log"
    fi
fi

if [ -f "$FS_LOG" ]; then
    RECENT_HANGUPS=$(tail -n 100 "$FS_LOG" | grep -i "CHANNEL_HANGUP\|CHANNEL_DESTROY" | tail -10)
    if [ ! -z "$RECENT_HANGUPS" ]; then
        log_info "最近的挂断事件:"
        echo "$RECENT_HANGUPS" | while read line; do
            log "  $line"
        done
    else
        log_warn "日志中没有找到最近的挂断事件"
    fi
else
    log_warn "无法找到 FreeSWITCH 日志文件"
fi

# 4. 生成清理建议
log_info "=== 步骤 4: 生成清理建议 ==="
log_warn "如果发现僵尸通道，可以使用以下命令清理:"
log "  $FS_CLI -x 'uuid_kill <channel-uuid>'"
log "  或"
log "  $FS_CLI -x 'uuid_break <channel-uuid> all'"

# 5. 统计信息
log_info "=== 步骤 5: 统计信息 ==="
log_info "诊断完成，详细日志已保存到: $LOG_FILE"

# 输出 JSON 格式的通道列表（便于进一步处理）
echo "$CHANNELS_JSON" > "/tmp/freeswitch_channels_$(date +%Y%m%d_%H%M%S).json"
log_info "通道 JSON 数据已保存到: /tmp/freeswitch_channels_*.json"
