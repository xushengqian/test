#!/bin/bash

# FreeSWITCH 通道检查和杀死脚本
# 解决 show channels 显示通道但 uuid_kill 报 "no such channel" 的问题

FS_CLI="${FS_CLI:-fs_cli}"  # FreeSWITCH CLI 命令，可通过环境变量覆盖

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 显示使用说明
usage() {
    echo "用法: $0 [选项] [UUID]"
    echo ""
    echo "选项:"
    echo "  -u, --uuid UUID        指定要杀死的通道 UUID"
    echo "  -a, --all              杀死所有活动通道"
    echo "  -l, --list             仅列出所有活动通道"
    echo "  -c, --check UUID       检查指定 UUID 的通道是否存在"
    echo "  -h, --help             显示此帮助信息"
    echo ""
    echo "示例:"
    echo "  $0 -u abc123-def456-789"
    echo "  $0 -a"
    echo "  $0 -l"
    echo "  $0 -c abc123-def456-789"
}

# 检查通道是否存在
check_channel_exists() {
    local uuid=$1
    if [ -z "$uuid" ]; then
        echo -e "${RED}错误: UUID 不能为空${NC}" >&2
        return 1
    fi
    
    # 使用 uuid_exists 命令检查通道是否存在
    local result=$($FS_CLI -x "uuid_exists $uuid" 2>/dev/null)
    
    if echo "$result" | grep -q "true"; then
        return 0  # 通道存在
    else
        return 1  # 通道不存在
    fi
}

# 获取通道详细信息
get_channel_info() {
    local uuid=$1
    $FS_CLI -x "uuid_dump $uuid" 2>/dev/null
}

# 安全地杀死通道
kill_channel() {
    local uuid=$1
    
    if [ -z "$uuid" ]; then
        echo -e "${RED}错误: UUID 不能为空${NC}" >&2
        return 1
    fi
    
    # 先检查通道是否存在
    echo -e "${YELLOW}检查通道 $uuid 是否存在...${NC}"
    if ! check_channel_exists "$uuid"; then
        echo -e "${RED}错误: 通道 $uuid 不存在或已关闭${NC}" >&2
        return 1
    fi
    
    # 显示通道信息
    echo -e "${YELLOW}通道信息:${NC}"
    get_channel_info "$uuid" | head -5
    
    # 执行杀死操作
    echo -e "${YELLOW}正在杀死通道 $uuid...${NC}"
    local result=$($FS_CLI -x "uuid_kill $uuid" 2>/dev/null)
    
    # 检查结果
    if [ $? -eq 0 ] && ! echo "$result" | grep -qi "error\|no such channel"; then
        echo -e "${GREEN}成功: 通道 $uuid 已被杀死${NC}"
        return 0
    else
        echo -e "${RED}错误: 无法杀死通道 $uuid${NC}" >&2
        echo "$result" >&2
        return 1
    fi
}

# 列出所有活动通道
list_channels() {
    echo -e "${YELLOW}获取活动通道列表...${NC}"
    local channels=$($FS_CLI -x "show channels" 2>/dev/null)
    
    if [ -z "$channels" ]; then
        echo -e "${YELLOW}没有活动通道${NC}"
        return 1
    fi
    
    echo "$channels"
    
    # 提取 UUID 列表
    echo ""
    echo -e "${YELLOW}通道 UUID 列表:${NC}"
    echo "$channels" | grep -oE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | sort -u
}

# 杀死所有活动通道
kill_all_channels() {
    echo -e "${YELLOW}警告: 将杀死所有活动通道！${NC}"
    read -p "确认继续? (y/N): " confirm
    
    if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
        echo "操作已取消"
        return 1
    fi
    
    # 获取所有通道 UUID
    local channels=$($FS_CLI -x "show channels" 2>/dev/null)
    local uuids=$(echo "$channels" | grep -oE '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}' | sort -u)
    
    if [ -z "$uuids" ]; then
        echo -e "${YELLOW}没有活动通道${NC}"
        return 0
    fi
    
    local count=0
    local success=0
    local failed=0
    
    while IFS= read -r uuid; do
        if [ -n "$uuid" ]; then
            count=$((count + 1))
            echo ""
            echo -e "${YELLOW}[$count] 处理通道: $uuid${NC}"
            if kill_channel "$uuid"; then
                success=$((success + 1))
            else
                failed=$((failed + 1))
            fi
            sleep 0.1  # 短暂延迟，避免过快操作
        fi
    done <<< "$uuids"
    
    echo ""
    echo -e "${GREEN}完成: 成功 $success, 失败 $failed, 总计 $count${NC}"
}

# 主函数
main() {
    # 检查 fs_cli 是否可用
    if ! command -v $FS_CLI &> /dev/null; then
        echo -e "${RED}错误: $FS_CLI 命令未找到${NC}" >&2
        echo "请确保 FreeSWITCH 已安装或设置 FS_CLI 环境变量" >&2
        exit 1
    fi
    
    # 解析参数
    case "${1:-}" in
        -u|--uuid)
            if [ -z "$2" ]; then
                echo -e "${RED}错误: 请提供 UUID${NC}" >&2
                usage
                exit 1
            fi
            kill_channel "$2"
            ;;
        -a|--all)
            kill_all_channels
            ;;
        -l|--list)
            list_channels
            ;;
        -c|--check)
            if [ -z "$2" ]; then
                echo -e "${RED}错误: 请提供 UUID${NC}" >&2
                usage
                exit 1
            fi
            if check_channel_exists "$2"; then
                echo -e "${GREEN}通道 $2 存在${NC}"
                get_channel_info "$2"
                exit 0
            else
                echo -e "${RED}通道 $2 不存在或已关闭${NC}"
                exit 1
            fi
            ;;
        -h|--help)
            usage
            ;;
        "")
            # 如果没有参数，显示使用说明
            usage
            ;;
        *)
            # 如果直接提供 UUID（不带选项）
            kill_channel "$1"
            ;;
    esac
}

# 执行主函数
main "$@"
