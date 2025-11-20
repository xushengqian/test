#!/bin/bash
# FreeSWITCH 诊断脚本
# 用于全面检查 FreeSWITCH 配置和状态，生成诊断报告

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 输出文件
REPORT_FILE="/tmp/freeswitch_diagnostic_$(date +%Y%m%d_%H%M%S).txt"

echo "FreeSWITCH 诊断脚本"
echo "生成诊断报告: ${REPORT_FILE}"
echo ""

# 开始生成报告
exec > >(tee -a "$REPORT_FILE")
exec 2>&1

echo "========================================="
echo "FreeSWITCH 诊断报告"
echo "生成时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "服务器: $(hostname)"
echo "IP 地址: $(hostname -I | awk '{print $1}')"
echo "========================================="
echo ""

# 1. 系统信息
echo "[1] 系统信息"
echo "-----------------------------------------"
echo "操作系统: $(cat /etc/os-release | grep PRETTY_NAME | cut -d'"' -f2)"
echo "内核版本: $(uname -r)"
echo "CPU 核心: $(nproc)"
echo "总内存: $(free -h | grep Mem | awk '{print $2}')"
echo "可用内存: $(free -h | grep Mem | awk '{print $7}')"
echo ""

# 2. FreeSWITCH 进程状态
echo "[2] FreeSWITCH 进程状态"
echo "-----------------------------------------"
FS_PID=$(pgrep -x freeswitch)
if [ ! -z "$FS_PID" ]; then
    echo "✓ FreeSWITCH 正在运行 (PID: $FS_PID)"
    ps aux | grep freeswitch | grep -v grep
    echo ""
    echo "进程启动时间:"
    ps -p $FS_PID -o lstart=
    echo ""
else
    echo "✗ FreeSWITCH 未运行"
    echo ""
fi

# 3. FreeSWITCH 版本
echo "[3] FreeSWITCH 版本"
echo "-----------------------------------------"
fs_cli -x "version" 2>/dev/null || echo "无法获取版本信息"
echo ""

# 4. 系统资源限制
echo "[4] 系统资源限制"
echo "-----------------------------------------"
echo "当前用户文件描述符限制:"
ulimit -n
echo ""
if [ ! -z "$FS_PID" ]; then
    echo "FreeSWITCH 进程限制:"
    cat /proc/$FS_PID/limits | grep -E "Max open files|Max processes"
    echo ""
    echo "当前文件描述符使用:"
    lsof -p $FS_PID 2>/dev/null | wc -l
fi
echo ""

# 5. 网关状态
echo "[5] SIP 网关状态"
echo "-----------------------------------------"
fs_cli -x "sofia status" 2>/dev/null || echo "无法获取网关状态"
echo ""

# 6. SIP Profile 状态
echo "[6] SIP Profile 状态"
echo "-----------------------------------------"
fs_cli -x "sofia status profile external" 2>/dev/null || echo "无法获取 Profile 状态"
echo ""

# 7. 活跃通道
echo "[7] 活跃通道统计"
echo "-----------------------------------------"
fs_cli -x "show channels count" 2>/dev/null || echo "无法获取通道信息"
echo ""
echo "详细通道信息:"
fs_cli -x "show channels" 2>/dev/null | head -20
echo ""

# 8. 通话统计
echo "[8] 通话统计"
echo "-----------------------------------------"
fs_cli -x "show calls count" 2>/dev/null || echo "无法获取通话统计"
echo ""

# 9. 网络端口监听
echo "[9] 网络端口监听"
echo "-----------------------------------------"
echo "SIP 端口 (5060, 5080):"
netstat -tlnp 2>/dev/null | grep -E ":5060|:5080" || ss -tlnp | grep -E ":5060|:5080"
echo ""
echo "RTP 端口范围使用情况:"
netstat -an | grep -E ":(1638[4-9]|163[9-9][0-9]|1[7-9][0-9]{3}|2[0-9]{4}|3[0-2][0-9]{3})" | wc -l
echo ""

# 10. 关键配置文件
echo "[10] 关键配置文件检查"
echo "-----------------------------------------"
CONFIG_FILES=(
    "/etc/freeswitch/autoload_configs/switch.conf.xml"
    "/etc/freeswitch/sip_profiles/external.xml"
    "/etc/freeswitch/sip_profiles/external/gwopensips.xml"
)

for conf in "${CONFIG_FILES[@]}"; do
    if [ -f "$conf" ]; then
        echo "✓ $conf 存在"
        
        # 提取关键参数
        if [[ "$conf" == *"switch.conf.xml"* ]]; then
            echo "  关键参数:"
            grep -E "max-sessions|sessions-per-second" "$conf" | sed 's/^/    /'
        elif [[ "$conf" == *"external.xml"* ]]; then
            echo "  关键参数:"
            grep -E "sip-port|rtp-start-port|rtp-end-port|max-calls" "$conf" | sed 's/^/    /'
        fi
    else
        echo "✗ $conf 不存在"
    fi
    echo ""
done

# 11. 最近的错误日志
echo "[11] 最近的错误日志 (最近 50 条)"
echo "-----------------------------------------"
if [ -f /var/log/freeswitch/freeswitch.log ]; then
    echo "DESTINATION_OUT_OF_ORDER 错误:"
    grep "DESTINATION_OUT_OF_ORDER" /var/log/freeswitch/freeswitch.log | tail -10
    echo ""
    
    echo "其他错误 (ERR):"
    grep "\[ERR\]" /var/log/freeswitch/freeswitch.log | tail -10
    echo ""
    
    echo "网关相关错误:"
    grep -i "gateway.*error\|gateway.*fail" /var/log/freeswitch/freeswitch.log | tail -10
else
    echo "日志文件不存在或无权限访问"
fi
echo ""

# 12. 系统负载历史
echo "[12] 系统负载"
echo "-----------------------------------------"
uptime
echo ""
echo "最近 5 分钟的 CPU 使用率:"
top -bn2 -d 0.5 | grep "Cpu(s)" | tail -1
echo ""

# 13. 磁盘空间
echo "[13] 磁盘空间"
echo "-----------------------------------------"
df -h | grep -E "Filesystem|/var|/$"
echo ""

# 14. 网络连接统计
echo "[14] 网络连接统计"
echo "-----------------------------------------"
echo "SIP 端口连接数:"
netstat -an 2>/dev/null | grep ":5060" | awk '{print $6}' | sort | uniq -c || \
ss -an | grep ":5060" | awk '{print $2}' | sort | uniq -c
echo ""

# 15. 检查防火墙
echo "[15] 防火墙状态"
echo "-----------------------------------------"
if command -v iptables >/dev/null 2>&1; then
    echo "iptables 规则 (INPUT):"
    iptables -L INPUT -n | grep -E "5060|5080|16384:32768" | head -10
else
    echo "iptables 未安装"
fi
echo ""

# 16. DNS 解析检查
echo "[16] DNS 解析检查"
echo "-----------------------------------------"
echo "DNS 服务器:"
cat /etc/resolv.conf | grep nameserver
echo ""

# 总结和建议
echo "========================================="
echo "诊断总结"
echo "========================================="
echo ""

# 检查问题并给出建议
HAS_ISSUES=0

# 检查 FreeSWITCH 是否运行
if [ -z "$FS_PID" ]; then
    echo "✗ 严重: FreeSWITCH 未运行"
    HAS_ISSUES=1
fi

# 检查文件描述符
if [ ! -z "$FS_PID" ]; then
    FD_COUNT=$(lsof -p $FS_PID 2>/dev/null | wc -l)
    FD_LIMIT=$(cat /proc/$FS_PID/limits | grep "open files" | awk '{print $4}')
    if [ ! -z "$FD_COUNT" ] && [ ! -z "$FD_LIMIT" ]; then
        FD_USAGE=$((FD_COUNT * 100 / FD_LIMIT))
        if [ $FD_USAGE -gt 80 ]; then
            echo "⚠ 警告: 文件描述符使用率过高 (${FD_USAGE}%)"
            echo "   建议: 增加文件描述符限制 (ulimit -n)"
            HAS_ISSUES=1
        fi
    fi
fi

# 检查内存使用
if [ ! -z "$FS_PID" ]; then
    MEM_PERCENT=$(ps aux | grep freeswitch | grep -v grep | head -1 | awk '{print $4}' | cut -d. -f1)
    if [ ! -z "$MEM_PERCENT" ] && [ "$MEM_PERCENT" -gt 80 ]; then
        echo "⚠ 警告: 内存使用率过高 (${MEM_PERCENT}%)"
        echo "   建议: 检查是否有内存泄漏，考虑增加内存或优化配置"
        HAS_ISSUES=1
    fi
fi

# 检查错误日志
ERROR_COUNT=$(grep "DESTINATION_OUT_OF_ORDER" /var/log/freeswitch/freeswitch.log 2>/dev/null | wc -l)
if [ ! -z "$ERROR_COUNT" ] && [ "$ERROR_COUNT" -gt 0 ]; then
    echo "⚠ 发现 $ERROR_COUNT 个 DESTINATION_OUT_OF_ORDER 错误"
    echo "   建议: 检查网关配置和网络连接"
    HAS_ISSUES=1
fi

if [ $HAS_ISSUES -eq 0 ]; then
    echo "✓ 未发现明显问题"
fi

echo ""
echo "========================================="
echo "诊断报告已保存至: ${REPORT_FILE}"
echo "========================================="
