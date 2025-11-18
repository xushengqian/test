#!/bin/bash
#
# FreeSWITCH 通道检查和终止脚本 (Shell 版本)
# 解决 uuid_kill 出现 "no such channel" 错误的问题
#

set -e

FS_CLI="fs_cli"
FORCE_KILL=false

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 执行 FreeSWITCH CLI 命令
execute_fs_cli() {
    local command="$1"
    $FS_CLI -x "$command" 2>/dev/null || return 1
}

# 检查通道是否存在
check_channel_exists() {
    local uuid="$1"
    local result=$(execute_fs_cli "uuid_exists $uuid")
    if [[ "$result" =~ (true|exists) ]]; then
        return 0
    else
        return 1
    fi
}

# 终止指定通道
kill_channel() {
    local uuid="$1"
    local force="$2"
    
    # 验证 UUID 格式
    if [[ ! "$uuid" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]; then
        echo -e "${RED}错误: 无效的 UUID 格式: $uuid${NC}"
        return 1
    fi
    
    # 检查通道是否存在
    if ! check_channel_exists "$uuid"; then
        echo -e "${YELLOW}警告: 通道 $uuid 不存在或已断开${NC}"
        return 1
    fi
    
    # 执行终止命令
    local kill_cmd="uuid_kill $uuid"
    if [[ "$force" == "true" ]]; then
        kill_cmd="$kill_cmd KILL"
    fi
    
    local result=$(execute_fs_cli "$kill_cmd")
    
    if [[ "$result" =~ (no such channel|not found) ]]; then
        echo -e "${RED}错误: 通道 $uuid 不存在 (no such channel)${NC}"
        return 1
    else
        echo -e "${GREEN}成功终止通道: $uuid${NC}"
        return 0
    fi
}

# 列出所有通道
list_channels() {
    echo "获取活动通道列表..."
    local output=$(execute_fs_cli "show channels")
    
    if [ -z "$output" ]; then
        echo "没有找到活动通道"
        return
    fi
    
    # 提取 UUID (第一列)
    echo "$output" | awk -F',' 'NR>2 && NF>0 {
        uuid = $1
        gsub(/^[ \t]+|[ \t]+$/, "", uuid)
        if (uuid ~ /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/) {
            print uuid
        }
    }'
}

# 终止所有通道
kill_all_channels() {
    local channels=$(list_channels)
    
    if [ -z "$channels" ]; then
        echo "没有找到活动通道"
        return
    fi
    
    local count=0
    local success=0
    
    while IFS= read -r uuid; do
        [ -z "$uuid" ] && continue
        count=$((count + 1))
        echo -e "\n处理通道 [$count]: $uuid"
        if kill_channel "$uuid" "$FORCE_KILL"; then
            success=$((success + 1))
        fi
    done <<< "$channels"
    
    echo -e "\n${GREEN}完成: 成功终止 $success/$count 个通道${NC}"
}

# 显示使用说明
show_usage() {
    cat << EOF
用法: $0 [选项]

选项:
  -l, --list              列出所有活动通道
  -k, --kill <UUID>       终止指定 UUID 的通道
  -a, --kill-all          终止所有活动通道
  -c, --check <UUID>      检查指定 UUID 的通道是否存在
  -f, --force             强制终止通道
  -h, --help              显示此帮助信息

示例:
  $0 --list                    # 列出所有通道
  $0 --kill <UUID>            # 终止指定通道
  $0 --kill-all               # 终止所有通道
  $0 --check <UUID>           # 检查通道是否存在

EOF
}

# 主函数
main() {
    case "${1:-}" in
        -l|--list)
            list_channels
            ;;
        -k|--kill)
            if [ -z "$2" ]; then
                echo -e "${RED}错误: 请提供 UUID${NC}"
                show_usage
                exit 1
            fi
            kill_channel "$2" "$FORCE_KILL"
            ;;
        -a|--kill-all)
            kill_all_channels
            ;;
        -c|--check)
            if [ -z "$2" ]; then
                echo -e "${RED}错误: 请提供 UUID${NC}"
                show_usage
                exit 1
            fi
            if check_channel_exists "$2"; then
                echo -e "${GREEN}通道 $2 存在${NC}"
            else
                echo -e "${RED}通道 $2 不存在${NC}"
            fi
            ;;
        -f|--force)
            FORCE_KILL=true
            shift
            main "$@"
            ;;
        -h|--help|"")
            show_usage
            ;;
        *)
            echo -e "${RED}未知选项: $1${NC}"
            show_usage
            exit 1
            ;;
    esac
}

main "$@"
