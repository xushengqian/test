#!/bin/bash

# FreeSWITCH 服务器诊断脚本
# 用于诊断 DESTINATION_OUT_OF_ORDER 错误

echo "=========================================="
echo "FreeSWITCH 服务器诊断工具"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查函数
check_status() {
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓${NC} $1"
    else
        echo -e "${RED}✗${NC} $1"
    fi
}

# 1. 检查 FreeSWITCH 进程
echo "1. 检查 FreeSWITCH 进程状态"
echo "----------------------------------------"
FS_PID=$(pgrep -f freeswitch | head -1)
if [ -z "$FS_PID" ]; then
    echo -e "${RED}✗ FreeSWITCH 进程未找到${NC}"
else
    echo -e "${GREEN}✓ FreeSWITCH 进程 ID: $FS_PID${NC}"
    echo "进程详情:"
    ps aux | grep freeswitch | grep -v grep
fi
echo ""

# 2. 检查文件描述符
echo "2. 检查文件描述符使用情况"
echo "----------------------------------------"
if [ ! -z "$FS_PID" ]; then
    FD_COUNT=$(lsof -p $FS_PID 2>/dev/null | wc -l)
    FD_LIMIT=$(cat /proc/$FS_PID/limits 2>/dev/null | grep "open files" | awk '{print $4}')
    FD_SOFT=$(cat /proc/$FS_PID/limits 2>/dev/null | grep "open files" | awk '{print $3}')
    
    echo "当前使用: $FD_COUNT"
    echo "软限制: $FD_SOFT"
    echo "硬限制: $FD_LIMIT"
    
    if [ ! -z "$FD_LIMIT" ] && [ "$FD_COUNT" -gt $((FD_SOFT * 80 / 100)) ]; then
        echo -e "${YELLOW}⚠ 警告: 文件描述符使用率超过 80%${NC}"
    fi
else
    echo -e "${RED}✗ 无法检查（FreeSWITCH 未运行）${NC}"
fi
echo ""

# 3. 检查内存使用
echo "3. 检查内存使用情况"
echo "----------------------------------------"
if [ ! -z "$FS_PID" ]; then
    MEM_INFO=$(ps -p $FS_PID -o rss,vsz --no-headers 2>/dev/null)
    if [ ! -z "$MEM_INFO" ]; then
        RSS=$(echo $MEM_INFO | awk '{print $1}')
        VSZ=$(echo $MEM_INFO | awk '{print $2}')
        RSS_MB=$((RSS / 1024))
        VSZ_MB=$((VSZ / 1024))
        echo "物理内存 (RSS): ${RSS_MB} MB"
        echo "虚拟内存 (VSZ): ${VSZ_MB} MB"
    fi
fi
echo "系统内存:"
free -h
echo ""

# 4. 检查 CPU 使用
echo "4. 检查 CPU 使用情况"
echo "----------------------------------------"
if [ ! -z "$FS_PID" ]; then
    CPU_USAGE=$(ps -p $FS_PID -o %cpu --no-headers 2>/dev/null)
    if [ ! -z "$CPU_USAGE" ]; then
        echo "FreeSWITCH CPU 使用率: ${CPU_USAGE}%"
        if (( $(echo "$CPU_USAGE > 90" | bc -l) )); then
            echo -e "${YELLOW}⚠ 警告: CPU 使用率过高${NC}"
        fi
    fi
fi
echo "系统 CPU 负载:"
uptime
echo ""

# 5. 检查网络连接
echo "5. 检查网络连接数"
echo "----------------------------------------"
if [ ! -z "$FS_PID" ]; then
    CONN_COUNT=$(netstat -an 2>/dev/null | grep ESTABLISHED | wc -l)
    FS_CONN_COUNT=$(netstat -anp 2>/dev/null | grep $FS_PID | grep ESTABLISHED | wc -l)
    echo "系统总连接数: $CONN_COUNT"
    echo "FreeSWITCH 连接数: $FS_CONN_COUNT"
fi
echo ""

# 6. 检查系统限制
echo "6. 检查系统资源限制"
echo "----------------------------------------"
echo "文件描述符限制:"
ulimit -n
echo ""
echo "进程数限制:"
ulimit -u
echo ""
echo "系统连接数限制:"
echo "somaxconn: $(sysctl -n net.core.somaxconn 2>/dev/null || echo 'N/A')"
echo "tcp_max_syn_backlog: $(sysctl -n net.ipv4.tcp_max_syn_backlog 2>/dev/null || echo 'N/A')"
echo ""

# 7. 检查 FreeSWITCH 状态（如果 fs_cli 可用）
echo "7. 检查 FreeSWITCH 内部状态"
echo "----------------------------------------"
if command -v fs_cli &> /dev/null; then
    echo "当前会话数:"
    fs_cli -x "show calls count" 2>/dev/null || echo "无法连接 fs_cli"
    echo ""
    echo "网关状态:"
    fs_cli -x "sofia status" 2>/dev/null | head -20 || echo "无法获取网关状态"
    echo ""
    echo "gwopensips 网关详情:"
    fs_cli -x "sofia status gateway gwopensips" 2>/dev/null || echo "无法获取网关详情"
else
    echo -e "${YELLOW}⚠ fs_cli 命令不可用，跳过 FreeSWITCH 内部状态检查${NC}"
fi
echo ""

# 8. 检查最近的错误日志
echo "8. 检查最近的错误日志"
echo "----------------------------------------"
LOG_PATHS=(
    "/usr/local/freeswitch/log/freeswitch.log"
    "/var/log/freeswitch/freeswitch.log"
    "/opt/freeswitch/log/freeswitch.log"
)

for LOG_PATH in "${LOG_PATHS[@]}"; do
    if [ -f "$LOG_PATH" ]; then
        echo "检查日志文件: $LOG_PATH"
        echo "最近的 DESTINATION_OUT_OF_ORDER 错误:"
        grep -i "DESTINATION_OUT_OF_ORDER" "$LOG_PATH" | tail -5 || echo "未找到相关错误"
        echo ""
        echo "最近的错误 (最后 10 行):"
        tail -10 "$LOG_PATH" | grep -i "error\|err\|fail" || echo "未找到错误信息"
        break
    fi
done
echo ""

# 9. 生成对比报告
echo "9. 生成对比数据"
echo "----------------------------------------"
echo "请将以下信息与正常服务器进行对比:"
echo ""
echo "=== 系统信息 ==="
echo "主机名: $(hostname)"
echo "IP 地址: $(hostname -I | awk '{print $1}')"
echo "操作系统: $(cat /etc/os-release 2>/dev/null | grep PRETTY_NAME | cut -d'"' -f2 || uname -a)"
echo ""
echo "=== 资源使用 ==="
if [ ! -z "$FS_PID" ]; then
    echo "文件描述符: $FD_COUNT / $FD_SOFT"
    echo "内存使用: ${RSS_MB} MB"
    echo "CPU 使用: ${CPU_USAGE}%"
fi
echo ""

echo "=========================================="
echo "诊断完成"
echo "=========================================="
echo ""
echo "建议:"
echo "1. 将此输出与正常服务器进行对比"
echo "2. 重点关注文件描述符、内存和 CPU 使用情况"
echo "3. 检查网关状态和连接数"
echo "4. 查看错误日志中的详细错误信息"
