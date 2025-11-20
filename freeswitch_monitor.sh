#!/bin/bash
# FreeSWITCH 实时监控脚本
# 用于监控网关状态、通道数、系统资源等关键指标

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置
GATEWAY_NAME="gwopensips"
INTERVAL=10  # 监控间隔（秒）
LOG_FILE="/tmp/freeswitch_monitor.log"

echo "FreeSWITCH 监控脚本启动..."
echo "日志文件: ${LOG_FILE}"
echo "按 Ctrl+C 停止监控"
echo ""

# 创建日志文件
touch ${LOG_FILE}

while true; do
    TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
    
    # 清屏并显示标题
    clear
    echo -e "${BLUE}========================================${NC}"
    echo -e "${BLUE}  FreeSWITCH 实时监控 - ${TIMESTAMP}${NC}"
    echo -e "${BLUE}========================================${NC}"
    echo ""
    
    # 1. 网关状态检查
    echo -e "${YELLOW}[1] 网关状态: ${GATEWAY_NAME}${NC}"
    GATEWAY_STATUS=$(fs_cli -x "sofia status gateway ${GATEWAY_NAME}" 2>/dev/null)
    
    if [ $? -eq 0 ]; then
        echo "$GATEWAY_STATUS" | grep -E "State|Status|Calls-IN|Calls-OUT|Ping"
        
        # 检查网关是否注册成功
        if echo "$GATEWAY_STATUS" | grep -q "State.*REGED"; then
            echo -e "${GREEN}✓ 网关已注册${NC}"
        else
            echo -e "${RED}✗ 网关未注册或离线${NC}"
            echo "${TIMESTAMP} - 警告: 网关 ${GATEWAY_NAME} 未注册" >> ${LOG_FILE}
        fi
    else
        echo -e "${RED}✗ 无法连接到 FreeSWITCH CLI${NC}"
        echo "${TIMESTAMP} - 错误: 无法连接到 FreeSWITCH CLI" >> ${LOG_FILE}
    fi
    echo ""
    
    # 2. 活跃通道数
    echo -e "${YELLOW}[2] 活跃通道统计${NC}"
    CHANNELS=$(fs_cli -x "show channels count" 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "$CHANNELS"
        CHANNEL_COUNT=$(echo "$CHANNELS" | grep "total" | awk '{print $1}')
        
        # 如果通道数超过阈值，记录警告
        if [ ! -z "$CHANNEL_COUNT" ] && [ "$CHANNEL_COUNT" -gt 800 ]; then
            echo -e "${RED}⚠ 警告: 通道数较高 (${CHANNEL_COUNT})${NC}"
            echo "${TIMESTAMP} - 警告: 通道数较高 ${CHANNEL_COUNT}" >> ${LOG_FILE}
        else
            echo -e "${GREEN}✓ 通道数正常${NC}"
        fi
    fi
    echo ""
    
    # 3. 系统负载
    echo -e "${YELLOW}[3] 系统负载${NC}"
    uptime
    echo ""
    
    # 4. CPU 和内存使用
    echo -e "${YELLOW}[4] FreeSWITCH 进程资源${NC}"
    FS_PID=$(pgrep -x freeswitch)
    if [ ! -z "$FS_PID" ]; then
        ps aux | grep freeswitch | grep -v grep | head -1 | awk '{printf "CPU: %s%%  内存: %s%%  VSZ: %s  RSS: %s\n", $3, $4, $5, $6}'
        
        # 内存使用检查
        MEM_PERCENT=$(ps aux | grep freeswitch | grep -v grep | head -1 | awk '{print $4}' | cut -d. -f1)
        if [ ! -z "$MEM_PERCENT" ] && [ "$MEM_PERCENT" -gt 80 ]; then
            echo -e "${RED}⚠ 警告: 内存使用率过高 (${MEM_PERCENT}%)${NC}"
            echo "${TIMESTAMP} - 警告: 内存使用率 ${MEM_PERCENT}%" >> ${LOG_FILE}
        fi
    else
        echo -e "${RED}✗ FreeSWITCH 进程未运行${NC}"
        echo "${TIMESTAMP} - 严重: FreeSWITCH 进程未运行" >> ${LOG_FILE}
    fi
    echo ""
    
    # 5. 文件描述符使用情况
    echo -e "${YELLOW}[5] 文件描述符${NC}"
    if [ ! -z "$FS_PID" ]; then
        FD_COUNT=$(lsof -p $FS_PID 2>/dev/null | wc -l)
        FD_LIMIT=$(cat /proc/$FS_PID/limits | grep "open files" | awk '{print $4}')
        
        if [ ! -z "$FD_COUNT" ] && [ ! -z "$FD_LIMIT" ]; then
            FD_USAGE=$((FD_COUNT * 100 / FD_LIMIT))
            echo "使用: ${FD_COUNT} / ${FD_LIMIT} (${FD_USAGE}%)"
            
            if [ $FD_USAGE -gt 80 ]; then
                echo -e "${RED}⚠ 警告: 文件描述符使用率过高 (${FD_USAGE}%)${NC}"
                echo "${TIMESTAMP} - 警告: 文件描述符使用率 ${FD_USAGE}%" >> ${LOG_FILE}
            else
                echo -e "${GREEN}✓ 文件描述符正常${NC}"
            fi
        fi
    fi
    echo ""
    
    # 6. 最近的错误日志
    echo -e "${YELLOW}[6] 最近的错误 (DESTINATION_OUT_OF_ORDER)${NC}"
    RECENT_ERRORS=$(grep "DESTINATION_OUT_OF_ORDER" /var/log/freeswitch/freeswitch.log 2>/dev/null | tail -3)
    if [ ! -z "$RECENT_ERRORS" ]; then
        echo "$RECENT_ERRORS" | while read line; do
            echo -e "${RED}${line}${NC}"
        done
    else
        echo -e "${GREEN}✓ 近期无此类错误${NC}"
    fi
    echo ""
    
    # 7. 网络连接统计
    echo -e "${YELLOW}[7] SIP 连接统计${NC}"
    NETSTAT_5060=$(netstat -an | grep ":5060" | wc -l)
    echo "端口 5060 连接数: ${NETSTAT_5060}"
    echo ""
    
    echo -e "${BLUE}========================================${NC}"
    echo -e "下次更新: ${INTERVAL} 秒后 (按 Ctrl+C 停止)"
    echo ""
    
    # 等待指定间隔
    sleep $INTERVAL
done
