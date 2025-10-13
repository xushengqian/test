#!/bin/bash
# FreeSWITCH 系统级性能优化脚本
# 请使用 root 权限运行此脚本

set -e

echo "==================================================="
echo "FreeSWITCH 系统级性能优化脚本"
echo "==================================================="

# 检查是否为 root 用户
if [ "$EUID" -ne 0 ]; then 
   echo "请使用 root 权限运行此脚本"
   exit 1
fi

# 1. 内核参数优化
echo "1. 优化内核参数..."
cat >> /etc/sysctl.conf << 'EOF'

# FreeSWITCH 性能优化参数
# 网络核心参数
net.core.rmem_max = 134217728
net.core.wmem_max = 134217728
net.core.rmem_default = 16777216
net.core.wmem_default = 16777216
net.core.optmem_max = 40960
net.core.netdev_max_backlog = 50000
net.core.somaxconn = 65535

# TCP 参数优化
net.ipv4.tcp_rmem = 4096 87380 134217728
net.ipv4.tcp_wmem = 4096 65536 134217728
net.ipv4.tcp_mem = 786432 1048576 26777216
net.ipv4.tcp_congestion_control = htcp
net.ipv4.tcp_mtu_probing = 1
net.ipv4.tcp_timestamps = 1
net.ipv4.tcp_sack = 1
net.ipv4.tcp_fin_timeout = 15
net.ipv4.tcp_keepalive_time = 300
net.ipv4.tcp_keepalive_intvl = 30
net.ipv4.tcp_keepalive_probes = 5
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_moderate_rcvbuf = 1
net.ipv4.tcp_syncookies = 1
net.ipv4.tcp_max_syn_backlog = 8192
net.ipv4.tcp_max_tw_buckets = 2000000
net.ipv4.tcp_tw_recycle = 0
net.ipv4.tcp_slow_start_after_idle = 0
net.ipv4.tcp_no_metrics_save = 1

# UDP 缓冲区（RTP 流量优化）
net.ipv4.udp_rmem_min = 8192
net.ipv4.udp_wmem_min = 8192
net.core.netdev_budget = 600
net.core.netdev_budget_usecs = 20000

# IP 参数优化
net.ipv4.ip_forward = 1
net.ipv4.ip_local_port_range = 10000 65000
net.ipv4.conf.all.accept_source_route = 0
net.ipv4.conf.all.accept_redirects = 0
net.ipv4.conf.all.send_redirects = 0
net.ipv4.conf.all.rp_filter = 1
net.ipv4.conf.default.rp_filter = 1
net.ipv4.icmp_echo_ignore_broadcasts = 1
net.ipv4.icmp_ignore_bogus_error_responses = 1
net.ipv4.route.gc_timeout = 100

# IPv6 优化（如果不使用可以禁用）
net.ipv6.conf.all.disable_ipv6 = 1
net.ipv6.conf.default.disable_ipv6 = 1
net.ipv6.conf.lo.disable_ipv6 = 1

# 文件系统优化
fs.file-max = 2097152
fs.nr_open = 2097152
fs.aio-max-nr = 1048576

# 虚拟内存优化
vm.swappiness = 10
vm.dirty_ratio = 15
vm.dirty_background_ratio = 5
vm.dirty_expire_centisecs = 12000
vm.overcommit_memory = 1

# 进程调度优化
kernel.sched_min_granularity_ns = 10000000
kernel.sched_wakeup_granularity_ns = 15000000
kernel.sched_migration_cost_ns = 5000000

# 共享内存优化
kernel.shmmax = 68719476736
kernel.shmall = 4294967296
kernel.msgmax = 65536
kernel.msgmnb = 65536

# 实时性能优化
kernel.sched_rt_runtime_us = -1
kernel.sched_rt_period_us = 1000000

EOF

# 应用内核参数
sysctl -p

# 2. 文件描述符限制优化
echo "2. 优化文件描述符限制..."
cat >> /etc/security/limits.conf << 'EOF'

# FreeSWITCH 用户限制
freeswitch soft nofile 1000000
freeswitch hard nofile 1000000
freeswitch soft nproc 32768
freeswitch hard nproc 32768
freeswitch soft memlock unlimited
freeswitch hard memlock unlimited
freeswitch soft core unlimited
freeswitch hard core unlimited
freeswitch soft priority -11
freeswitch hard priority -11

# 默认限制
* soft nofile 65536
* hard nofile 65536
* soft nproc 32768
* hard nproc 32768

EOF

# 3. systemd 服务优化（如果使用 systemd）
if [ -d /etc/systemd/system ]; then
    echo "3. 创建优化的 systemd 服务文件..."
    cat > /etc/systemd/system/freeswitch.service << 'EOF'
[Unit]
Description=FreeSWITCH
After=network-online.target
Wants=network-online.target

[Service]
Type=forking
PIDFile=/run/freeswitch/freeswitch.pid
Environment="DAEMON_OPTS=-nonat -nc"
ExecStart=/usr/local/freeswitch/bin/freeswitch -u freeswitch -g freeswitch -ncwait $DAEMON_OPTS
ExecReload=/usr/bin/kill -HUP $MAINPID
ExecStop=/usr/bin/kill $MAINPID
TimeoutStopSec=45s
Restart=always
RestartSec=5

# 资源限制
LimitNOFILE=1000000
LimitNPROC=60000
LimitCORE=infinity
LimitMEMLOCK=infinity
LimitRTPRIO=infinity

# 性能优化
CPUSchedulingPolicy=fifo
CPUSchedulingPriority=89
IOSchedulingClass=realtime
IOSchedulingPriority=0
Nice=-11

# CPU 亲和性（根据实际 CPU 核心数调整）
# CPUAffinity=0-7

# 内存和任务限制
TasksMax=infinity
MemoryLimit=infinity

# 安全选项
PrivateTmp=false
NoNewPrivileges=false

[Install]
WantedBy=multi-user.target
EOF
    
    systemctl daemon-reload
fi

# 4. 创建 tmpfs 挂载点
echo "4. 创建 tmpfs 挂载点..."
mkdir -p /dev/shm/freeswitch/{db,recordings,cache,temp,logs}
chown -R freeswitch:freeswitch /dev/shm/freeswitch

# 添加到 fstab
if ! grep -q "freeswitch/db" /etc/fstab; then
    cat >> /etc/fstab << 'EOF'
# FreeSWITCH tmpfs 挂载
tmpfs /dev/shm/freeswitch/db tmpfs defaults,size=1G 0 0
tmpfs /dev/shm/freeswitch/recordings tmpfs defaults,size=2G 0 0
tmpfs /dev/shm/freeswitch/cache tmpfs defaults,size=512M 0 0
tmpfs /dev/shm/freeswitch/temp tmpfs defaults,size=512M 0 0
EOF
    mount -a
fi

# 5. CPU 频率调节优化
echo "5. 优化 CPU 频率调节..."
if [ -f /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor ]; then
    for cpu in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
        echo "performance" > $cpu
    done
fi

# 禁用 CPU 节能特性
if [ -f /sys/devices/system/cpu/cpufreq/boost ]; then
    echo 1 > /sys/devices/system/cpu/cpufreq/boost
fi

# 6. 中断处理优化
echo "6. 优化中断处理..."
# 禁用 irqbalance 服务（手动绑定中断）
if systemctl is-active --quiet irqbalance; then
    systemctl stop irqbalance
    systemctl disable irqbalance
fi

# 将网卡中断绑定到特定 CPU 核心
# 注意：需要根据实际网卡和 CPU 配置调整
NIC="eth0"  # 修改为实际网卡名称
if [ -d /sys/class/net/$NIC ]; then
    # 获取网卡中断号
    IRQ=$(cat /proc/interrupts | grep $NIC | awk '{print $1}' | sed 's/://')
    if [ ! -z "$IRQ" ]; then
        # 将中断绑定到 CPU 0-3
        echo "f" > /proc/irq/$IRQ/smp_affinity
    fi
fi

# 7. 透明大页面（THP）优化
echo "7. 配置透明大页面..."
if [ -f /sys/kernel/mm/transparent_hugepage/enabled ]; then
    echo "never" > /sys/kernel/mm/transparent_hugepage/enabled
    echo "never" > /sys/kernel/mm/transparent_hugepage/defrag
fi

# 8. 网络接口优化
echo "8. 优化网络接口..."
NIC="eth0"  # 修改为实际网卡名称
if [ -d /sys/class/net/$NIC ]; then
    # 增加网卡队列长度
    ip link set $NIC txqueuelen 10000
    
    # 启用网卡优化功能
    ethtool -G $NIC rx 4096 tx 4096 2>/dev/null || true
    ethtool -K $NIC gro on gso on tso on 2>/dev/null || true
    ethtool -K $NIC rx-vlan-offload on tx-vlan-offload on 2>/dev/null || true
    
    # 设置中断合并参数
    ethtool -C $NIC rx-usecs 100 2>/dev/null || true
fi

# 9. 防火墙优化（iptables）
echo "9. 配置防火墙规则..."
# 清除现有规则
iptables -F
iptables -X
iptables -t nat -F
iptables -t nat -X
iptables -t mangle -F
iptables -t mangle -X

# 设置默认策略
iptables -P INPUT DROP
iptables -P FORWARD DROP
iptables -P OUTPUT ACCEPT

# 允许本地回环
iptables -A INPUT -i lo -j ACCEPT

# 允许已建立的连接
iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT

# 允许 SIP 端口
iptables -A INPUT -p udp --dport 5060 -j ACCEPT
iptables -A INPUT -p tcp --dport 5060 -j ACCEPT
iptables -A INPUT -p udp --dport 5080 -j ACCEPT
iptables -A INPUT -p tcp --dport 5080 -j ACCEPT

# 允许 RTP 端口范围
iptables -A INPUT -p udp --dport 16384:32768 -j ACCEPT

# 允许 WebSocket（如果使用 WebRTC）
# iptables -A INPUT -p tcp --dport 8021 -j ACCEPT
# iptables -A INPUT -p tcp --dport 8082 -j ACCEPT

# 允许 SSH（根据需要调整端口）
iptables -A INPUT -p tcp --dport 22 -j ACCEPT

# 防止 SIP 攻击
iptables -A INPUT -p udp --dport 5060 -m string --algo bm --string "friendly-scanner" -j DROP
iptables -A INPUT -p udp --dport 5060 -m string --algo bm --string "sipcli" -j DROP
iptables -A INPUT -p udp --dport 5060 -m string --algo bm --string "sipvicious" -j DROP

# 限制连接速率
iptables -A INPUT -p udp --dport 5060 -m recent --set --name SIP
iptables -A INPUT -p udp --dport 5060 -m recent --update --seconds 1 --hitcount 20 --name SIP -j DROP

# 保存防火墙规则
if command -v iptables-save >/dev/null 2>&1; then
    iptables-save > /etc/iptables/rules.v4
fi

# 10. 日志优化
echo "10. 优化日志配置..."
# 配置 rsyslog 异步写入
if [ -f /etc/rsyslog.conf ]; then
    sed -i 's/^#$ActionFileEnableSync on/$ActionFileEnableSync off/' /etc/rsyslog.conf
    systemctl restart rsyslog
fi

# 11. 创建性能调优的 cron 任务
echo "11. 创建维护任务..."
cat > /etc/cron.d/freeswitch-maintenance << 'EOF'
# FreeSWITCH 维护任务
# 每天凌晨 3 点清理旧的录音文件（超过 7 天）
0 3 * * * freeswitch find /dev/shm/freeswitch/recordings -type f -mtime +7 -delete

# 每小时同步内存数据库到磁盘（备份）
0 * * * * freeswitch sqlite3 /dev/shm/freeswitch/db/core.db ".backup /var/lib/freeswitch/db/core.db"

# 每 6 小时清理日志
0 */6 * * * freeswitch find /usr/local/freeswitch/log -type f -name "*.log" -mtime +3 -delete
EOF

# 12. 实时优先级配置
echo "12. 配置实时优先级..."
cat > /etc/security/limits.d/freeswitch.conf << 'EOF'
# FreeSWITCH 实时优先级配置
freeswitch - rtprio 99
freeswitch - nice -11
freeswitch - memlock unlimited
EOF

# 13. NUMA 优化（如果系统支持）
if [ -f /proc/sys/kernel/numa_balancing ]; then
    echo "13. 配置 NUMA..."
    echo 0 > /proc/sys/kernel/numa_balancing
fi

# 14. 电源管理优化
echo "14. 优化电源管理..."
# 禁用 CPU 空闲状态
if [ -d /sys/devices/system/cpu/cpu0/cpuidle ]; then
    for state in /sys/devices/system/cpu/cpu*/cpuidle/state*/disable; do
        echo 1 > $state 2>/dev/null || true
    done
fi

# 15. 创建监控脚本
echo "15. 创建系统监控脚本..."
cat > /usr/local/bin/freeswitch-monitor.sh << 'EOF'
#!/bin/bash
# FreeSWITCH 系统资源监控

echo "=== FreeSWITCH 系统资源监控 ==="
echo "时间: $(date)"
echo ""

# CPU 使用率
echo "CPU 使用率:"
top -bn1 | grep "Cpu(s)" | sed "s/.*, *\([0-9.]*\)%* id.*/CPU 使用率: \1%/" | awk '{print 100 - $3"%"}'
echo ""

# 内存使用
echo "内存使用:"
free -h
echo ""

# FreeSWITCH 进程信息
if pgrep freeswitch > /dev/null; then
    echo "FreeSWITCH 进程:"
    ps aux | grep -E "^USER|freeswitch" | grep -v grep
    echo ""
    
    # 连接数
    echo "网络连接数:"
    netstat -anp | grep -E ":5060|:5080" | wc -l
    echo ""
    
    # RTP 端口使用
    echo "RTP 端口使用:"
    netstat -anp | grep -E ":[1-3][0-9]{4}" | grep udp | wc -l
else
    echo "FreeSWITCH 未运行"
fi
echo ""

# 磁盘使用
echo "磁盘使用:"
df -h | grep -E "^/dev/|^tmpfs|Filesystem"
echo ""

# 网络流量
echo "网络流量:"
if command -v ifstat >/dev/null 2>&1; then
    ifstat -i eth0 1 1
else
    cat /proc/net/dev | grep eth0
fi

EOF
chmod +x /usr/local/bin/freeswitch-monitor.sh

echo ""
echo "==================================================="
echo "系统优化完成！"
echo "==================================================="
echo ""
echo "建议操作："
echo "1. 重启系统以应用所有更改"
echo "2. 根据实际硬件配置调整参数"
echo "3. 使用 /usr/local/bin/freeswitch-monitor.sh 监控系统"
echo "4. 定期检查 /var/log/freeswitch/ 日志"
echo ""
echo "注意事项："
echo "- 请根据实际网卡名称修改脚本中的 'eth0'"
echo "- 根据 CPU 核心数调整 CPU 亲和性设置"
echo "- 根据内存大小调整 tmpfs 大小"
echo "- 生产环境中谨慎使用防火墙规则"
echo "==================================================="