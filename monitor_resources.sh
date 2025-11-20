#!/bin/bash

# FreeSWITCH 资源监控脚本
# 实时监控服务器资源使用情况，帮助定位问题

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 默认监控间隔（秒）
INTERVAL=${1:-5}

# 获取 FreeSWITCH PID
FS_PID=$(pgrep -f freeswitch | head -1)

if [ -z "$FS_PID" ]; then
    echo -e "${RED}错误: 未找到 FreeSWITCH 进程${NC}"
    exit 1
fi

echo -e "${GREEN}开始监控 FreeSWITCH (PID: $FS_PID)${NC}"
echo "监控间隔: ${INTERVAL} 秒"
echo "按 Ctrl+C 停止监控"
echo ""
echo "时间戳 | FD使用/限制 | 内存(MB) | CPU(%) | 连接数 | 会话数"
echo "------------------------------------------------------------"

# 清理函数
cleanup() {
    echo ""
    echo -e "${YELLOW}监控已停止${NC}"
    exit 0
}

trap cleanup INT TERM

# 监控循环
while true; do
    TIMESTAMP=$(date '+%H:%M:%S')
    
    # 文件描述符
    if [ ! -z "$FS_PID" ]; then
        FD_COUNT=$(lsof -p $FS_PID 2>/dev/null | wc -l)
        FD_SOFT=$(cat /proc/$FS_PID/limits 2>/dev/null | grep "open files" | awk '{print $4}')
        FD_USAGE=""
        if [ ! -z "$FD_SOFT" ] && [ "$FD_SOFT" != "unlimited" ]; then
            FD_PERCENT=$((FD_COUNT * 100 / FD_SOFT))
            if [ $FD_PERCENT -gt 80 ]; then
                FD_USAGE="${RED}${FD_COUNT}/${FD_SOFT}${NC}"
            elif [ $FD_PERCENT -gt 60 ]; then
                FD_USAGE="${YELLOW}${FD_COUNT}/${FD_SOFT}${NC}"
            else
                FD_USAGE="${GREEN}${FD_COUNT}/${FD_SOFT}${NC}"
            fi
        else
            FD_USAGE="${FD_COUNT}/unlimited"
        fi
        
        # 内存
        MEM_INFO=$(ps -p $FS_PID -o rss --no-headers 2>/dev/null)
        MEM_MB="N/A"
        if [ ! -z "$MEM_INFO" ]; then
            MEM_MB=$((MEM_INFO / 1024))
        fi
        
        # CPU
        CPU_USAGE=$(ps -p $FS_PID -o %cpu --no-headers 2>/dev/null)
        CPU_USAGE=${CPU_USAGE:-"N/A"}
        
        # 连接数
        CONN_COUNT=$(netstat -anp 2>/dev/null | grep $FS_PID | grep ESTABLISHED | wc -l)
        
        # 会话数（如果 fs_cli 可用）
        SESSION_COUNT="N/A"
        if command -v fs_cli &> /dev/null; then
            SESSION_COUNT=$(fs_cli -x "show calls count" 2>/dev/null | grep "total" | awk '{print $1}' || echo "N/A")
        fi
        
        # 输出（使用 printf 保持格式）
        printf "%-8s | %-15s | %-8s | %-6s | %-6s | %s\n" \
            "$TIMESTAMP" \
            "$FD_USAGE" \
            "$MEM_MB" \
            "$CPU_USAGE" \
            "$CONN_COUNT" \
            "$SESSION_COUNT"
        
        # 警告检查
        if [ ! -z "$FD_SOFT" ] && [ "$FD_SOFT" != "unlimited" ]; then
            FD_PERCENT=$((FD_COUNT * 100 / FD_SOFT))
            if [ $FD_PERCENT -gt 90 ]; then
                echo -e "${RED}⚠ 警告: 文件描述符使用率 ${FD_PERCENT}%${NC}"
            fi
        fi
        
        if [ "$CPU_USAGE" != "N/A" ] && (( $(echo "$CPU_USAGE > 90" | bc -l 2>/dev/null || echo 0) )); then
            echo -e "${RED}⚠ 警告: CPU 使用率 ${CPU_USAGE}%${NC}"
        fi
    else
        echo -e "${RED}错误: FreeSWITCH 进程已停止${NC}"
        break
    fi
    
    sleep $INTERVAL
done
