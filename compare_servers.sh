#!/bin/bash
# 两台 FreeSWITCH 服务器配置对比脚本
# 用于找出两台服务器之间的配置差异

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 使用方法
usage() {
    echo "用法: $0 <远程服务器IP>"
    echo "示例: $0 10.181.10.159"
    echo ""
    echo "说明: 此脚本将对比本地服务器与远程服务器的 FreeSWITCH 配置"
    echo "      需要配置 SSH 免密登录到远程服务器"
    exit 1
}

# 检查参数
if [ $# -ne 1 ]; then
    usage
fi

REMOTE_SERVER=$1
LOCAL_SERVER=$(hostname -I | awk '{print $1}')
REPORT_FILE="/tmp/freeswitch_comparison_$(date +%Y%m%d_%H%M%S).txt"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}FreeSWITCH 服务器配置对比${NC}"
echo -e "${BLUE}========================================${NC}"
echo "本地服务器: $LOCAL_SERVER"
echo "远程服务器: $REMOTE_SERVER"
echo "报告文件: $REPORT_FILE"
echo ""

# 测试 SSH 连接
echo "测试 SSH 连接..."
if ! ssh -o ConnectTimeout=5 -o BatchMode=yes root@$REMOTE_SERVER "echo 2>&1" >/dev/null 2>&1; then
    echo -e "${RED}✗ 无法连接到远程服务器 $REMOTE_SERVER${NC}"
    echo "请确保:"
    echo "  1. 远程服务器可访问"
    echo "  2. 已配置 SSH 免密登录"
    exit 1
fi
echo -e "${GREEN}✓ SSH 连接成功${NC}"
echo ""

# 开始生成报告
exec > >(tee -a "$REPORT_FILE")
exec 2>&1

echo "========================================="
echo "FreeSWITCH 服务器配置对比报告"
echo "生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "本地服务器: $LOCAL_SERVER ($(hostname))"
echo "远程服务器: $REMOTE_SERVER"
echo "========================================="
echo ""

# 1. FreeSWITCH 版本对比
echo "[1] FreeSWITCH 版本"
echo "-----------------------------------------"
echo "本地版本:"
fs_cli -x "version" 2>/dev/null | head -1 || echo "无法获取"
echo ""
echo "远程版本:"
ssh root@$REMOTE_SERVER "fs_cli -x 'version'" 2>/dev/null | head -1 || echo "无法获取"
echo ""

# 2. 系统资源限制对比
echo "[2] 系统资源限制"
echo "-----------------------------------------"
echo "本地服务器:"
echo "  文件描述符限制: $(ulimit -n)"
LOCAL_FS_PID=$(pgrep -x freeswitch)
if [ ! -z "$LOCAL_FS_PID" ]; then
    echo "  FreeSWITCH 进程限制:"
    cat /proc/$LOCAL_FS_PID/limits | grep "Max open files" | awk '{print "    "$1" "$2" "$3": "$4" "$5" "$6}'
fi
echo ""

echo "远程服务器:"
ssh root@$REMOTE_SERVER "ulimit -n" 2>/dev/null | awk '{print "  文件描述符限制: "$0}'
REMOTE_FS_PID=$(ssh root@$REMOTE_SERVER "pgrep -x freeswitch" 2>/dev/null)
if [ ! -z "$REMOTE_FS_PID" ]; then
    echo "  FreeSWITCH 进程限制:"
    ssh root@$REMOTE_SERVER "cat /proc/$REMOTE_FS_PID/limits | grep 'Max open files'" | awk '{print "    "$1" "$2" "$3": "$4" "$5" "$6}'
fi
echo ""

# 3. 关键配置文件对比
echo "[3] 关键配置文件对比"
echo "-----------------------------------------"

CONFIG_FILES=(
    "/etc/freeswitch/autoload_configs/switch.conf.xml"
    "/etc/freeswitch/sip_profiles/external.xml"
    "/etc/freeswitch/sip_profiles/external/gwopensips.xml"
)

for conf in "${CONFIG_FILES[@]}"; do
    echo "文件: $conf"
    echo ""
    
    # 检查本地文件
    if [ -f "$conf" ]; then
        echo "本地: ✓ 存在"
        LOCAL_MD5=$(md5sum "$conf" | awk '{print $1}')
        echo "MD5: $LOCAL_MD5"
    else
        echo "本地: ✗ 不存在"
        LOCAL_MD5=""
    fi
    
    # 检查远程文件
    REMOTE_EXISTS=$(ssh root@$REMOTE_SERVER "test -f $conf && echo 'yes' || echo 'no'" 2>/dev/null)
    if [ "$REMOTE_EXISTS" == "yes" ]; then
        echo "远程: ✓ 存在"
        REMOTE_MD5=$(ssh root@$REMOTE_SERVER "md5sum $conf" 2>/dev/null | awk '{print $1}')
        echo "MD5: $REMOTE_MD5"
    else
        echo "远程: ✗ 不存在"
        REMOTE_MD5=""
    fi
    
    # 对比 MD5
    if [ ! -z "$LOCAL_MD5" ] && [ ! -z "$REMOTE_MD5" ]; then
        if [ "$LOCAL_MD5" == "$REMOTE_MD5" ]; then
            echo -e "${GREEN}结果: ✓ 文件相同${NC}"
        else
            echo -e "${RED}结果: ✗ 文件不同${NC}"
            echo "详细差异:"
            
            # 创建临时文件
            LOCAL_TEMP="/tmp/local_$(basename $conf)"
            REMOTE_TEMP="/tmp/remote_$(basename $conf)"
            
            cp "$conf" "$LOCAL_TEMP"
            ssh root@$REMOTE_SERVER "cat $conf" > "$REMOTE_TEMP" 2>/dev/null
            
            # 显示差异（只显示关键参数）
            echo "关键参数差异:"
            if [[ "$conf" == *"switch.conf.xml"* ]]; then
                diff -y --suppress-common-lines \
                    <(grep -E "max-sessions|sessions-per-second" "$LOCAL_TEMP" 2>/dev/null) \
                    <(grep -E "max-sessions|sessions-per-second" "$REMOTE_TEMP" 2>/dev/null) \
                    | head -20
            elif [[ "$conf" == *"external.xml"* ]]; then
                diff -y --suppress-common-lines \
                    <(grep -E "sip-port|rtp-start-port|rtp-end-port|max-calls|sip-.*-timeout" "$LOCAL_TEMP" 2>/dev/null) \
                    <(grep -E "sip-port|rtp-start-port|rtp-end-port|max-calls|sip-.*-timeout" "$REMOTE_TEMP" 2>/dev/null) \
                    | head -20
            elif [[ "$conf" == *"gwopensips.xml"* ]]; then
                diff -y --suppress-common-lines "$LOCAL_TEMP" "$REMOTE_TEMP" | head -30
            fi
            
            # 清理临时文件
            rm -f "$LOCAL_TEMP" "$REMOTE_TEMP"
        fi
    fi
    echo ""
    echo "---"
    echo ""
done

# 4. 网关状态对比
echo "[4] SIP 网关状态"
echo "-----------------------------------------"
echo "本地网关状态:"
fs_cli -x "sofia status gateway gwopensips" 2>/dev/null | grep -E "State|Status|Calls" || echo "无法获取"
echo ""
echo "远程网关状态:"
ssh root@$REMOTE_SERVER "fs_cli -x 'sofia status gateway gwopensips'" 2>/dev/null | grep -E "State|Status|Calls" || echo "无法获取"
echo ""

# 5. 当前负载对比
echo "[5] 当前系统负载"
echo "-----------------------------------------"
echo "本地服务器:"
echo "  负载: $(uptime | awk -F'load average:' '{print $2}')"
echo "  通道数: $(fs_cli -x 'show channels count' 2>/dev/null | grep 'total' | awk '{print $1}')"
echo "  内存: $(free -h | grep Mem | awk '{print "使用 "$3" / "$2}')"
echo ""
echo "远程服务器:"
ssh root@$REMOTE_SERVER "uptime" 2>/dev/null | awk -F'load average:' '{print "  负载:"$2}'
ssh root@$REMOTE_SERVER "fs_cli -x 'show channels count'" 2>/dev/null | grep 'total' | awk '{print "  通道数: "$1}'
ssh root@$REMOTE_SERVER "free -h | grep Mem" 2>/dev/null | awk '{print "  内存: 使用 "$3" / "$2}'
echo ""

# 6. 网络配置对比
echo "[6] 网络配置"
echo "-----------------------------------------"
echo "本地服务器:"
echo "  SIP 端口监听:"
netstat -tlnp 2>/dev/null | grep -E ":5060|:5080" | awk '{print "    "$4" "$6" "$7}' || \
ss -tlnp 2>/dev/null | grep -E ":5060|:5080" | awk '{print "    "$4" "$5" "$6}'
echo ""
echo "远程服务器:"
echo "  SIP 端口监听:"
ssh root@$REMOTE_SERVER "netstat -tlnp 2>/dev/null | grep -E ':5060|:5080' || ss -tlnp 2>/dev/null | grep -E ':5060|:5080'" | awk '{print "    "$4" "$5" "$6}'
echo ""

# 7. 错误日志对比
echo "[7] 最近错误统计 (最近 1 小时)"
echo "-----------------------------------------"
SINCE_TIME=$(date -d '1 hour ago' '+%Y-%m-%d %H:%M:%S')
echo "时间范围: $SINCE_TIME 至今"
echo ""

echo "本地服务器:"
LOCAL_ERRORS=$(grep "DESTINATION_OUT_OF_ORDER" /var/log/freeswitch/freeswitch.log 2>/dev/null | \
    awk -v since="$SINCE_TIME" '$0 >= since' | wc -l)
echo "  DESTINATION_OUT_OF_ORDER 错误: $LOCAL_ERRORS 次"
echo ""

echo "远程服务器:"
REMOTE_ERRORS=$(ssh root@$REMOTE_SERVER "grep 'DESTINATION_OUT_OF_ORDER' /var/log/freeswitch/freeswitch.log 2>/dev/null" | \
    awk -v since="$SINCE_TIME" '$0 >= since' | wc -l)
echo "  DESTINATION_OUT_OF_ORDER 错误: $REMOTE_ERRORS 次"
echo ""

# 总结
echo "========================================="
echo "对比总结"
echo "========================================="
echo ""

# 统计差异
DIFF_COUNT=0

# 检查配置文件差异
for conf in "${CONFIG_FILES[@]}"; do
    if [ -f "$conf" ]; then
        LOCAL_MD5=$(md5sum "$conf" 2>/dev/null | awk '{print $1}')
        REMOTE_MD5=$(ssh root@$REMOTE_SERVER "md5sum $conf" 2>/dev/null | awk '{print $1}')
        
        if [ ! -z "$LOCAL_MD5" ] && [ ! -z "$REMOTE_MD5" ] && [ "$LOCAL_MD5" != "$REMOTE_MD5" ]; then
            echo "✗ 配置文件不同: $conf"
            DIFF_COUNT=$((DIFF_COUNT + 1))
        fi
    fi
done

# 检查错误率差异
if [ ! -z "$LOCAL_ERRORS" ] && [ ! -z "$REMOTE_ERRORS" ]; then
    ERROR_DIFF=$((LOCAL_ERRORS - REMOTE_ERRORS))
    if [ ${ERROR_DIFF#-} -gt 10 ]; then  # 差异超过 10 次
        echo "⚠ 错误率差异明显: 本地 $LOCAL_ERRORS 次 vs 远程 $REMOTE_ERRORS 次"
        DIFF_COUNT=$((DIFF_COUNT + 1))
    fi
fi

if [ $DIFF_COUNT -eq 0 ]; then
    echo "✓ 未发现明显配置差异"
    echo "建议检查:"
    echo "  - 网络连接质量和带宽"
    echo "  - 硬件资源（CPU、内存、磁盘 I/O）"
    echo "  - 运营商网关侧的负载均衡策略"
else
    echo "发现 $DIFF_COUNT 处差异，建议进一步检查"
fi

echo ""
echo "========================================="
echo "对比报告已保存至: ${REPORT_FILE}"
echo "========================================="
