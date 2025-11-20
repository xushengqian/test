#!/bin/bash

# FreeSWITCH DESTINATION_OUT_OF_ORDER 错误诊断脚本

echo "=========================================="
echo "FreeSWITCH 诊断脚本"
echo "=========================================="
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 1. 检查 FreeSWITCH 进程
echo -e "${YELLOW}1. 检查 FreeSWITCH 进程状态${NC}"
if pgrep -x "freeswitch" > /dev/null; then
    echo -e "${GREEN}✓ FreeSWITCH 正在运行${NC}"
    ps aux | grep freeswitch | grep -v grep
else
    echo -e "${RED}✗ FreeSWITCH 未运行${NC}"
fi
echo ""

# 2. 检查网关状态
echo -e "${YELLOW}2. 检查网关状态${NC}"
if command -v fs_cli &> /dev/null; then
    echo "检查 gwopensips 网关状态:"
    fs_cli -x "sofia status gateway gwopensips" 2>/dev/null || echo "无法连接到 FreeSWITCH"
else
    echo "fs_cli 命令未找到，跳过网关检查"
fi
echo ""

# 3. 检查网络连接数
echo -e "${YELLOW}3. 检查网络连接数${NC}"
ESTABLISHED=$(netstat -an 2>/dev/null | grep ESTABLISHED | wc -l)
TIME_WAIT=$(netstat -an 2>/dev/null | grep TIME_WAIT | wc -l)
echo "ESTABLISHED 连接数: $ESTABLISHED"
echo "TIME_WAIT 连接数: $TIME_WAIT"
echo ""

# 4. 检查系统资源
echo -e "${YELLOW}4. 检查系统资源使用情况${NC}"
echo "CPU 使用率:"
top -bn1 | grep "Cpu(s)" | sed "s/.*, *\([0-9.]*\)%* id.*/\1/" | awk '{print "  Idle: " 100 - $1 "%"}'
echo ""
echo "内存使用情况:"
free -h
echo ""

# 5. 检查文件描述符
echo -e "${YELLOW}5. 检查文件描述符限制${NC}"
if [ -f /proc/sys/fs/file-max ]; then
    MAX_FILES=$(cat /proc/sys/fs/file-max)
    CURRENT_FILES=$(lsof 2>/dev/null | wc -l)
    echo "系统最大文件描述符: $MAX_FILES"
    echo "当前使用文件描述符: $CURRENT_FILES"
    echo "使用率: $(awk "BEGIN {printf \"%.2f\", ($CURRENT_FILES/$MAX_FILES)*100}")%"
fi
echo ""

# 6. 检查 FreeSWITCH 日志中的错误
echo -e "${YELLOW}6. 检查最近的 DESTINATION_OUT_OF_ORDER 错误${NC}"
LOG_PATHS=(
    "/var/log/freeswitch/freeswitch.log"
    "/usr/local/freeswitch/log/freeswitch.log"
    "/opt/freeswitch/log/freeswitch.log"
)

for log_path in "${LOG_PATHS[@]}"; do
    if [ -f "$log_path" ]; then
        echo "在 $log_path 中查找错误:"
        grep -i "DESTINATION_OUT_OF_ORDER" "$log_path" | tail -10
        break
    fi
done
echo ""

# 7. 检查网关连接
echo -e "${YELLOW}7. 检查到网关的网络连接${NC}"
if command -v netstat &> /dev/null; then
    echo "到 opensips 的连接:"
    netstat -an | grep -i opensips | head -10
fi
echo ""

# 8. 检查 FreeSWITCH 配置
echo -e "${YELLOW}8. 检查 FreeSWITCH 配置${NC}"
CONFIG_PATHS=(
    "/etc/freeswitch"
    "/usr/local/freeswitch/conf"
    "/opt/freeswitch/conf"
)

for config_path in "${CONFIG_PATHS[@]}"; do
    if [ -d "$config_path" ]; then
        echo "配置文件路径: $config_path"
        if [ -f "$config_path/autoload_configs/switch.conf.xml" ]; then
            echo "检查最大会话数配置:"
            grep -i "max-sessions" "$config_path/autoload_configs/switch.conf.xml" || echo "未找到 max-sessions 配置"
        fi
        break
    fi
done
echo ""

# 9. 检查最近的呼叫统计
echo -e "${YELLOW}9. 检查呼叫统计${NC}"
if command -v fs_cli &> /dev/null; then
    echo "当前活跃呼叫数:"
    fs_cli -x "show calls count" 2>/dev/null || echo "无法获取呼叫统计"
    echo ""
    echo "网关统计:"
    fs_cli -x "sofia status" 2>/dev/null | grep -A 5 "gwopensips" || echo "无法获取网关统计"
fi
echo ""

# 10. 生成建议
echo -e "${YELLOW}10. 诊断建议${NC}"
echo "根据检查结果，建议:"
echo "1. 如果连接数接近上限，考虑增加连接池大小或优化连接管理"
echo "2. 如果网关状态异常，检查网关配置和网络连接"
echo "3. 如果资源使用率高，考虑增加服务器资源或优化配置"
echo "4. 检查负载均衡配置，确保负载分布均匀"
echo "5. 实现错误重试和降级机制"
echo ""

echo "=========================================="
echo "诊断完成"
echo "=========================================="
