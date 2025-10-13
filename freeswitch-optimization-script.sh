#!/bin/bash

# FreeSWITCH 性能优化脚本
# 自动应用系统级优化配置

echo "开始 FreeSWITCH 性能优化..."

# 1. 系统内核参数优化
echo "正在优化内核参数..."
cat >> /etc/sysctl.conf << EOF

# FreeSWITCH 性能优化参数
# 网络缓冲区优化
net.core.rmem_max = 134217728
net.core.wmem_max = 134217728
net.core.rmem_default = 262144
net.core.wmem_default = 262144

# UDP 内存优化
net.ipv4.udp_mem = 102400 873800 16777216
net.ipv4.udp_rmem_min = 8192
net.ipv4.udp_wmem_min = 8192

# 网络队列优化
net.core.netdev_max_backlog = 5000
net.core.netdev_budget = 600

# TCP 优化
net.ipv4.tcp_rmem = 4096 65536 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216
net.ipv4.tcp_congestion_control = cubic

# 连接跟踪优化
net.netfilter.nf_conntrack_max = 1048576
net.netfilter.nf_conntrack_tcp_timeout_established = 7200

# 文件描述符优化
fs.file-max = 2097152

# 共享内存优化
kernel.shmmax = 68719476736
kernel.shmall = 4294967296

EOF

# 应用内核参数
sysctl -p

# 2. 用户限制优化
echo "正在优化用户限制..."
cat >> /etc/security/limits.conf << EOF

# FreeSWITCH 用户限制优化
freeswitch soft nofile 999999
freeswitch hard nofile 999999
freeswitch soft nproc 999999
freeswitch hard nproc 999999
freeswitch soft memlock unlimited
freeswitch hard memlock unlimited

# 通用优化
* soft nofile 65536
* hard nofile 65536

EOF

# 3. CPU 调度器优化
echo "正在优化 CPU 调度器..."
# 设置 CPU 调度策略
if [ -f /proc/sys/kernel/sched_rt_runtime_us ]; then
    echo 950000 > /proc/sys/kernel/sched_rt_runtime_us
fi

# 4. 磁盘 I/O 优化
echo "正在优化磁盘 I/O..."
# 设置磁盘调度器为 deadline（适合实时应用）
for disk in /sys/block/sd*; do
    if [ -f "$disk/queue/scheduler" ]; then
        echo deadline > "$disk/queue/scheduler" 2>/dev/null || true
    fi
done

# 5. 创建 FreeSWITCH 启动脚本
echo "正在创建优化的启动脚本..."
cat > /usr/local/bin/freeswitch-optimized << 'EOF'
#!/bin/bash

# FreeSWITCH 优化启动脚本

# 设置环境变量
export FREESWITCH_USER=freeswitch
export FREESWITCH_GROUP=freeswitch

# 设置进程优先级
NICE_LEVEL=-10

# 设置 CPU 亲和性（如果是多核系统）
CPU_CORES=$(nproc)
if [ "$CPU_CORES" -gt 1 ]; then
    CPU_MASK="0-$((CPU_CORES-1))"
    TASKSET_CMD="taskset -c $CPU_MASK"
else
    TASKSET_CMD=""
fi

# 启动 FreeSWITCH
exec $TASKSET_CMD nice -n $NICE_LEVEL /usr/local/freeswitch/bin/freeswitch \
    -u $FREESWITCH_USER \
    -g $FREESWITCH_GROUP \
    -ncwait \
    -nonat \
    "$@"
EOF

chmod +x /usr/local/bin/freeswitch-optimized

# 6. 创建性能监控脚本
echo "正在创建性能监控脚本..."
cat > /usr/local/bin/freeswitch-monitor << 'EOF'
#!/bin/bash

# FreeSWITCH 性能监控脚本

echo "FreeSWITCH 性能监控报告 - $(date)"
echo "========================================"

# 基本系统信息
echo "系统负载:"
uptime

echo -e "\n内存使用:"
free -h

echo -e "\nCPU 使用率:"
top -bn1 | grep "Cpu(s)" | head -1

# FreeSWITCH 进程信息
if pgrep freeswitch > /dev/null; then
    echo -e "\nFreeSWITCH 进程状态:"
    ps aux | grep freeswitch | grep -v grep
    
    echo -e "\nFreeSWITCH 状态:"
    fs_cli -x "status" 2>/dev/null || echo "无法连接到 FreeSWITCH"
    
    echo -e "\n当前会话数:"
    fs_cli -x "show channels count" 2>/dev/null || echo "无法获取会话信息"
    
    echo -e "\n注册用户数:"
    fs_cli -x "show registrations count" 2>/dev/null || echo "无法获取注册信息"
else
    echo -e "\nFreeSWITCH 进程未运行"
fi

echo -e "\n网络连接统计:"
ss -tuln | grep -E ':(5060|5080|8021)' | wc -l | awk '{print "活动连接数: " $1}'

echo -e "\n磁盘使用率:"
df -h | grep -vE '^Filesystem|tmpfs|cdrom'

echo "========================================"
EOF

chmod +x /usr/local/bin/freeswitch-monitor

# 7. 创建定时清理脚本
echo "正在创建清理脚本..."
cat > /usr/local/bin/freeswitch-cleanup << 'EOF'
#!/bin/bash

# FreeSWITCH 清理脚本

LOG_DIR="/usr/local/freeswitch/log"
RECORDINGS_DIR="/usr/local/freeswitch/recordings"
DB_DIR="/usr/local/freeswitch/db"

# 清理旧日志文件（保留7天）
find $LOG_DIR -name "*.log.*" -mtime +7 -delete 2>/dev/null

# 清理旧录音文件（保留30天）
find $RECORDINGS_DIR -name "*.wav" -mtime +30 -delete 2>/dev/null

# 压缩大日志文件
find $LOG_DIR -name "*.log" -size +100M -exec gzip {} \; 2>/dev/null

# 清理临时文件
rm -f /tmp/core.db-* 2>/dev/null
rm -f /tmp/freeswitch.* 2>/dev/null

echo "清理完成: $(date)"
EOF

chmod +x /usr/local/bin/freeswitch-cleanup

# 8. 添加 cron 任务
echo "正在设置定时任务..."
(crontab -l 2>/dev/null; echo "0 2 * * * /usr/local/bin/freeswitch-cleanup") | crontab -
(crontab -l 2>/dev/null; echo "*/5 * * * * /usr/local/bin/freeswitch-monitor >> /var/log/freeswitch-monitor.log") | crontab -

echo "FreeSWITCH 性能优化完成！"
echo ""
echo "优化内容包括:"
echo "- 内核参数优化"
echo "- 用户限制优化"  
echo "- CPU 调度优化"
echo "- 磁盘 I/O 优化"
echo "- 优化的启动脚本"
echo "- 性能监控脚本"
echo "- 自动清理脚本"
echo "- 定时任务设置"
echo ""
echo "请重启系统以使所有优化生效。"
echo "使用 /usr/local/bin/freeswitch-optimized 启动 FreeSWITCH"
echo "使用 /usr/local/bin/freeswitch-monitor 查看性能状态"