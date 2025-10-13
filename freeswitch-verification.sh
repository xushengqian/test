#!/bin/bash

# FreeSWITCH 性能优化验证脚本
# 验证优化措施是否正确应用

echo "FreeSWITCH 性能优化验证工具"
echo "============================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 检查函数
check_pass() {
    echo -e "✅ ${GREEN}$1${NC}"
}

check_fail() {
    echo -e "❌ ${RED}$1${NC}"
}

check_warning() {
    echo -e "⚠️  ${YELLOW}$1${NC}"
}

check_info() {
    echo -e "ℹ️  ${BLUE}$1${NC}"
}

# 1. 检查系统内核参数
echo "1. 检查系统内核参数优化"
echo "------------------------"

# 检查网络参数
rmem_max=$(sysctl -n net.core.rmem_max 2>/dev/null)
if [ "$rmem_max" -ge 134217728 ]; then
    check_pass "网络接收缓冲区已优化: $rmem_max"
else
    check_fail "网络接收缓冲区需要优化: $rmem_max (建议: 134217728)"
fi

wmem_max=$(sysctl -n net.core.wmem_max 2>/dev/null)
if [ "$wmem_max" -ge 134217728 ]; then
    check_pass "网络发送缓冲区已优化: $wmem_max"
else
    check_fail "网络发送缓冲区需要优化: $wmem_max (建议: 134217728)"
fi

# 检查文件描述符
file_max=$(sysctl -n fs.file-max 2>/dev/null)
if [ "$file_max" -ge 2097152 ]; then
    check_pass "系统文件描述符限制已优化: $file_max"
else
    check_fail "系统文件描述符限制需要优化: $file_max (建议: 2097152)"
fi

echo ""

# 2. 检查用户限制
echo "2. 检查用户限制配置"
echo "-------------------"

# 检查freeswitch用户的nofile限制
if id freeswitch &>/dev/null; then
    nofile_soft=$(sudo -u freeswitch bash -c 'ulimit -Sn' 2>/dev/null)
    nofile_hard=$(sudo -u freeswitch bash -c 'ulimit -Hn' 2>/dev/null)
    
    if [ "$nofile_soft" -ge 999999 ]; then
        check_pass "FreeSWITCH用户软限制已优化: $nofile_soft"
    else
        check_fail "FreeSWITCH用户软限制需要优化: $nofile_soft (建议: 999999)"
    fi
    
    if [ "$nofile_hard" -ge 999999 ]; then
        check_pass "FreeSWITCH用户硬限制已优化: $nofile_hard"
    else
        check_fail "FreeSWITCH用户硬限制需要优化: $nofile_hard (建议: 999999)"
    fi
else
    check_warning "FreeSWITCH用户不存在，跳过用户限制检查"
fi

echo ""

# 3. 检查FreeSWITCH进程状态
echo "3. 检查FreeSWITCH进程状态"
echo "-------------------------"

if pgrep freeswitch > /dev/null; then
    check_pass "FreeSWITCH进程正在运行"
    
    # 检查进程优先级
    fs_pid=$(pgrep freeswitch | head -1)
    fs_nice=$(ps -o ni= -p $fs_pid 2>/dev/null | tr -d ' ')
    
    if [ "$fs_nice" -le -5 ]; then
        check_pass "FreeSWITCH进程优先级已优化: $fs_nice"
    else
        check_warning "FreeSWITCH进程优先级可优化: $fs_nice (建议: -10)"
    fi
    
    # 检查内存使用
    fs_mem=$(ps -o rss= -p $fs_pid 2>/dev/null)
    if [ -n "$fs_mem" ]; then
        fs_mem_mb=$((fs_mem / 1024))
        check_info "FreeSWITCH内存使用: ${fs_mem_mb}MB"
    fi
    
    # 检查CPU使用率
    fs_cpu=$(ps -o %cpu= -p $fs_pid 2>/dev/null | tr -d ' ')
    if [ -n "$fs_cpu" ]; then
        check_info "FreeSWITCH CPU使用率: ${fs_cpu}%"
    fi
    
else
    check_fail "FreeSWITCH进程未运行"
fi

echo ""

# 4. 检查FreeSWITCH配置文件
echo "4. 检查FreeSWITCH配置文件"
echo "-------------------------"

FS_CONF_DIR="/usr/local/freeswitch/conf"
if [ ! -d "$FS_CONF_DIR" ]; then
    FS_CONF_DIR="/etc/freeswitch"
fi

# 检查主配置文件
if [ -f "$FS_CONF_DIR/switch.conf.xml" ]; then
    check_pass "找到FreeSWITCH主配置文件"
    
    # 检查关键参数
    if grep -q "max-sessions.*30000" "$FS_CONF_DIR/switch.conf.xml"; then
        check_pass "最大会话数已优化 (30000)"
    else
        check_warning "最大会话数可能需要优化"
    fi
    
    if grep -q "sessions-per-second.*500" "$FS_CONF_DIR/switch.conf.xml"; then
        check_pass "每秒会话速率已优化 (500)"
    else
        check_warning "每秒会话速率可能需要优化"
    fi
else
    check_fail "未找到FreeSWITCH主配置文件"
fi

# 检查变量配置文件
if [ -f "$FS_CONF_DIR/vars.xml" ]; then
    check_pass "找到FreeSWITCH变量配置文件"
    
    # 检查编解码器配置
    if grep -q "global_codec_prefs.*PCMU" "$FS_CONF_DIR/vars.xml"; then
        check_pass "编解码器优先级已优化 (PCMU优先)"
    else
        check_warning "编解码器优先级可能需要优化"
    fi
else
    check_warning "未找到FreeSWITCH变量配置文件"
fi

# 检查模块配置
if [ -f "$FS_CONF_DIR/autoload_configs/modules.conf.xml" ]; then
    check_pass "找到FreeSWITCH模块配置文件"
    
    # 检查是否启用了高效定时器
    if grep -q "mod_timerfd" "$FS_CONF_DIR/autoload_configs/modules.conf.xml" && \
       ! grep -q "<!--.*mod_timerfd" "$FS_CONF_DIR/autoload_configs/modules.conf.xml"; then
        check_pass "高效定时器模块已启用 (mod_timerfd)"
    else
        check_warning "建议启用高效定时器模块 (mod_timerfd)"
    fi
else
    check_warning "未找到FreeSWITCH模块配置文件"
fi

echo ""

# 5. 检查网络配置
echo "5. 检查网络配置"
echo "---------------"

# 检查RTP端口范围
if command -v fs_cli &> /dev/null && pgrep freeswitch > /dev/null; then
    rtp_start=$(fs_cli -x "global_getvar rtp_start_port" 2>/dev/null | head -1)
    rtp_end=$(fs_cli -x "global_getvar rtp_end_port" 2>/dev/null | head -1)
    
    if [ "$rtp_start" = "16384" ] && [ "$rtp_end" = "32767" ]; then
        check_pass "RTP端口范围已优化: $rtp_start-$rtp_end"
    else
        check_info "RTP端口范围: $rtp_start-$rtp_end"
    fi
else
    check_warning "无法连接FreeSWITCH CLI检查网络配置"
fi

# 检查网络接口状态
active_interfaces=$(ip link show up | grep -c "state UP")
check_info "活动网络接口数: $active_interfaces"

echo ""

# 6. 性能状态检查
echo "6. 系统性能状态"
echo "---------------"

# CPU使用率
cpu_usage=$(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | sed 's/%us,//')
if [ -n "$cpu_usage" ]; then
    cpu_num=$(echo "$cpu_usage" | cut -d'.' -f1)
    if [ "$cpu_num" -lt 70 ]; then
        check_pass "CPU使用率正常: ${cpu_usage}%"
    elif [ "$cpu_num" -lt 85 ]; then
        check_warning "CPU使用率较高: ${cpu_usage}%"
    else
        check_fail "CPU使用率过高: ${cpu_usage}%"
    fi
fi

# 内存使用率
mem_usage=$(free | awk 'FNR==2{printf "%.1f", $3/($3+$4)*100}')
if [ -n "$mem_usage" ]; then
    mem_num=$(echo "$mem_usage" | cut -d'.' -f1)
    if [ "$mem_num" -lt 80 ]; then
        check_pass "内存使用率正常: ${mem_usage}%"
    elif [ "$mem_num" -lt 90 ]; then
        check_warning "内存使用率较高: ${mem_usage}%"
    else
        check_fail "内存使用率过高: ${mem_usage}%"
    fi
fi

# 系统负载
load_avg=$(uptime | awk '{print $(NF-2)}' | sed 's/,//')
if [ -n "$load_avg" ]; then
    cpu_cores=$(nproc)
    load_ratio=$(echo "scale=2; $load_avg / $cpu_cores" | bc -l 2>/dev/null || echo "0")
    check_info "系统负载: $load_avg (${cpu_cores}核心, 比率: $load_ratio)"
fi

echo ""

# 7. 优化脚本和工具检查
echo "7. 检查优化工具"
echo "---------------"

# 检查优化脚本
if [ -f "/usr/local/bin/freeswitch-optimized" ]; then
    check_pass "优化启动脚本已安装"
else
    check_warning "优化启动脚本未找到"
fi

if [ -f "/usr/local/bin/freeswitch-monitor" ]; then
    check_pass "性能监控脚本已安装"
else
    check_warning "性能监控脚本未找到"
fi

if [ -f "/usr/local/bin/freeswitch-cleanup" ]; then
    check_pass "清理脚本已安装"
else
    check_warning "清理脚本未找到"
fi

# 检查Python监控工具
if [ -f "freeswitch-realtime-monitor.py" ]; then
    check_pass "实时监控工具可用"
else
    check_warning "实时监控工具未找到"
fi

echo ""

# 8. 生成总结报告
echo "8. 优化验证总结"
echo "==============="

echo ""
echo "📊 系统概况:"
echo "   - CPU核心数: $(nproc)"
echo "   - 总内存: $(free -h | awk 'FNR==2{print $2}')"
echo "   - 系统架构: $(uname -m)"
echo "   - 内核版本: $(uname -r)"

if pgrep freeswitch > /dev/null; then
    echo ""
    echo "📞 FreeSWITCH状态:"
    if command -v fs_cli &> /dev/null; then
        fs_cli -x "status" 2>/dev/null | head -10 | while read line; do
            echo "   $line"
        done
    fi
fi

echo ""
echo "🎯 优化建议:"
echo "   1. 定期监控系统资源使用情况"
echo "   2. 根据实际负载调整会话限制"
echo "   3. 启用实时性能监控工具"
echo "   4. 定期执行性能基准测试"
echo "   5. 保持FreeSWITCH版本更新"

echo ""
echo "验证完成！请查看上述检查结果并根据建议进行调整。"