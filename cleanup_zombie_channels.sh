#!/bin/bash

# FreeSWITCH 僵尸通道清理脚本
# 自动检测并清理长时间存在的通道

FS_CLI="/usr/local/freeswitch/bin/fs_cli"
MAX_DURATION=3600  # 最大允许持续时间（秒），默认 1 小时
DRY_RUN=true       # 是否只是预览，不实际执行清理

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

usage() {
    echo "用法: $0 [选项]"
    echo "选项:"
    echo "  -d, --duration SECONDS  最大允许持续时间（秒），默认 3600"
    echo "  -e, --execute           实际执行清理（默认只是预览）"
    echo "  -h, --help              显示帮助信息"
    exit 1
}

# 解析参数
while [[ $# -gt 0 ]]; do
    case $1 in
        -d|--duration)
            MAX_DURATION="$2"
            shift 2
            ;;
        -e|--execute)
            DRY_RUN=false
            shift
            ;;
        -h|--help)
            usage
            ;;
        *)
            echo "未知参数: $1"
            usage
            ;;
    esac
done

# 检查 fs_cli
if [ ! -f "$FS_CLI" ]; then
    if [ -f "/usr/bin/fs_cli" ]; then
        FS_CLI="/usr/bin/fs_cli"
    elif [ -f "/opt/freeswitch/bin/fs_cli" ]; then
        FS_CLI="/opt/freeswitch/bin/fs_cli"
    else
        echo -e "${RED}错误: 无法找到 fs_cli${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}开始清理僵尸通道...${NC}"
if [ "$DRY_RUN" = true ]; then
    echo -e "${YELLOW}模式: 预览模式（不会实际清理）${NC}"
else
    echo -e "${RED}模式: 执行模式（将实际清理通道）${NC}"
fi
echo "最大允许持续时间: ${MAX_DURATION} 秒"
echo ""

# 获取所有通道
CHANNELS_JSON=$($FS_CLI -x "show channels as json" 2>/dev/null)
if [ $? -ne 0 ]; then
    echo -e "${RED}错误: 无法连接到 FreeSWITCH${NC}"
    exit 1
fi

# 解析 JSON 并提取通道信息
# 注意：这个脚本使用简单的 grep/sed，对于复杂的 JSON 可能需要 jq
ZOMBIE_COUNT=0
CLEANED_COUNT=0

echo "$CHANNELS_JSON" | grep -o '"uuid":"[^"]*"' | sed 's/"uuid":"\([^"]*\)"/\1/' | while read UUID; do
    if [ -z "$UUID" ]; then
        continue
    fi
    
    # 获取通道详细信息
    CHANNEL_INFO=$($FS_CLI -x "show channels $UUID" 2>/dev/null)
    
    # 提取持续时间（秒）
    # FreeSWITCH 输出格式可能是 "Duration: 00:00:30" 或类似格式
    DURATION_LINE=$(echo "$CHANNEL_INFO" | grep -i "duration" | head -1)
    
    # 尝试提取秒数（简化处理，实际可能需要更复杂的解析）
    # 这里假设格式是 "Duration: HH:MM:SS" 或包含秒数
    DURATION_SEC=0
    
    # 尝试多种格式解析
    if echo "$DURATION_LINE" | grep -qE '[0-9]+:[0-9]+:[0-9]+'; then
        # 格式 HH:MM:SS
        HOURS=$(echo "$DURATION_LINE" | grep -oE '[0-9]+:[0-9]+:[0-9]+' | cut -d: -f1)
        MINUTES=$(echo "$DURATION_LINE" | grep -oE '[0-9]+:[0-9]+:[0-9]+' | cut -d: -f2)
        SECONDS=$(echo "$DURATION_LINE" | grep -oE '[0-9]+:[0-9]+:[0-9]+' | cut -d: -f3)
        DURATION_SEC=$((HOURS * 3600 + MINUTES * 60 + SECONDS))
    elif echo "$DURATION_LINE" | grep -qE '[0-9]+\s+sec'; then
        # 格式 "123 sec"
        DURATION_SEC=$(echo "$DURATION_LINE" | grep -oE '[0-9]+' | head -1)
    fi
    
    # 检查是否是僵尸通道
    if [ "$DURATION_SEC" -gt "$MAX_DURATION" ]; then
        ZOMBIE_COUNT=$((ZOMBIE_COUNT + 1))
        echo -e "${YELLOW}发现僵尸通道: $UUID (持续时间: ${DURATION_SEC} 秒)${NC}"
        
        if [ "$DRY_RUN" = false ]; then
            echo "  正在清理..."
            RESULT=$($FS_CLI -x "uuid_kill $UUID" 2>&1)
            if [ $? -eq 0 ]; then
                echo -e "  ${GREEN}✓ 清理成功${NC}"
                CLEANED_COUNT=$((CLEANED_COUNT + 1))
            else
                echo -e "  ${RED}✗ 清理失败: $RESULT${NC}"
            fi
        else
            echo "  [预览] 将执行: uuid_kill $UUID"
        fi
    fi
done

echo ""
if [ "$DRY_RUN" = true ]; then
    echo -e "${YELLOW}预览完成。发现 $ZOMBIE_COUNT 个可能的僵尸通道${NC}"
    echo "使用 -e 或 --execute 参数来实际执行清理"
else
    echo -e "${GREEN}清理完成。已清理 $CLEANED_COUNT 个通道${NC}"
fi
