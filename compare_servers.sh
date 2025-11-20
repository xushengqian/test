#!/bin/bash

# 服务器对比脚本
# 用于对比两台 FreeSWITCH 服务器的配置和状态

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo "=========================================="
echo "FreeSWITCH 服务器对比工具"
echo "=========================================="
echo ""

# 检查参数
if [ $# -lt 2 ]; then
    echo "用法: $0 <异常服务器IP或主机名> <正常服务器IP或主机名>"
    echo "示例: $0 10-181-10-158 normal-server"
    exit 1
fi

ABNORMAL_SERVER=$1
NORMAL_SERVER=$2

echo "异常服务器: $ABNORMAL_SERVER"
echo "正常服务器: $NORMAL_SERVER"
echo ""

# 函数：获取服务器信息
get_server_info() {
    local server=$1
    local label=$2
    
    echo -e "${BLUE}=== $label ($server) ===${NC}"
    
    # 尝试 SSH 连接（如果配置了 SSH 密钥）
    if ssh -o ConnectTimeout=5 -o StrictHostKeyChecking=no $server "echo 'Connected'" &>/dev/null; then
        echo "连接方式: SSH"
        
        # 获取基本信息
        echo "--- 系统信息 ---"
        ssh $server "hostname; hostname -I | awk '{print \$1}'" 2>/dev/null
        
        echo "--- 资源限制 ---"
        ssh $server "echo '文件描述符:'; ulimit -n; echo '进程数:'; ulimit -u" 2>/dev/null
        
        echo "--- FreeSWITCH 进程 ---"
        ssh $server "ps aux | grep freeswitch | grep -v grep | head -1" 2>/dev/null
        
        echo "--- 系统资源 ---"
        ssh $server "free -h | head -2" 2>/dev/null
        ssh $server "uptime" 2>/dev/null
        
        echo "--- 网络连接 ---"
        ssh $server "netstat -an | grep ESTABLISHED | wc -l" 2>/dev/null
        
    else
        echo -e "${YELLOW}⚠ 无法通过 SSH 连接，请手动收集以下信息:${NC}"
        echo ""
        echo "请在 $server 上运行以下命令:"
        echo "  ./diagnose_freeswitch.sh > ${server}_diagnosis.txt"
        echo ""
        echo "或者手动收集:"
        echo "  1. ulimit -n"
        echo "  2. ulimit -u"
        echo "  3. ps aux | grep freeswitch"
        echo "  4. free -h"
        echo "  5. fs_cli -x 'show calls count'"
        echo "  6. fs_cli -x 'sofia status gateway gwopensips'"
    fi
    echo ""
}

# 对比函数
compare_values() {
    local name=$1
    local abnormal=$2
    local normal=$3
    
    echo -n "$name: "
    if [ "$abnormal" == "$normal" ]; then
        echo -e "${GREEN}相同 ($abnormal)${NC}"
    else
        echo -e "${RED}不同 - 异常: $abnormal, 正常: $normal${NC}"
    fi
}

# 获取两台服务器的信息
get_server_info "$ABNORMAL_SERVER" "异常服务器"
get_server_info "$NORMAL_SERVER" "正常服务器"

echo "=========================================="
echo "对比建议"
echo "=========================================="
echo ""
echo "请对比以下关键指标:"
echo ""
echo "1. 文件描述符限制 (ulimit -n)"
echo "   - 异常服务器: [需要手动获取]"
echo "   - 正常服务器: [需要手动获取]"
echo ""
echo "2. FreeSWITCH 配置"
echo "   - 对比 /usr/local/freeswitch/conf/autoload_configs/switch.conf.xml"
echo "   - 重点关注: max-sessions, sessions-per-second, max-db-handles"
echo ""
echo "3. 网关配置"
echo "   - 对比 SIP 网关配置文件"
echo "   - 检查网关连接数和超时设置"
echo ""
echo "4. 系统资源"
echo "   - 内存使用情况"
echo "   - CPU 使用情况"
echo "   - 网络连接数"
echo ""
echo "5. 日志对比"
echo "   - 对比异常时间段的两台服务器日志"
echo "   - 查找错误模式和差异"
echo ""

# 生成对比检查清单
cat > /workspace/对比检查清单.md << 'EOF'
# 服务器对比检查清单

## 系统配置对比

### 资源限制
- [ ] 文件描述符限制 (ulimit -n)
- [ ] 进程数限制 (ulimit -u)
- [ ] 系统连接数限制 (net.core.somaxconn)
- [ ] TCP 连接数限制 (net.ipv4.tcp_max_syn_backlog)

### 系统资源
- [ ] 内存总量和使用率
- [ ] CPU 核心数和负载
- [ ] 磁盘空间和 IO

## FreeSWITCH 配置对比

### switch.conf.xml
- [ ] max-sessions
- [ ] sessions-per-second
- [ ] max-db-handles
- [ ] session-timeout
- [ ] call-timeout

### 网关配置
- [ ] gwopensips 网关配置
- [ ] 网关连接数限制
- [ ] 网关超时设置
- [ ] 网关注册状态

### 其他配置
- [ ] event_socket 配置
- [ ] mod_sofia 配置
- [ ] Lua 脚本配置

## 运行时状态对比

### 资源使用
- [ ] 当前文件描述符使用数
- [ ] 当前内存使用量
- [ ] 当前 CPU 使用率
- [ ] 当前连接数

### FreeSWITCH 状态
- [ ] 当前会话数 (show calls count)
- [ ] 网关状态 (sofia status)
- [ ] 网关连接数 (sofia status gateway gwopensips)

## 日志对比

### 错误日志
- [ ] DESTINATION_OUT_OF_ORDER 错误频率
- [ ] 其他错误信息
- [ ] 错误发生时间模式

### 性能日志
- [ ] 呼叫建立时间
- [ ] 网关响应时间
- [ ] 资源使用峰值

## 网络对比

### 网络配置
- [ ] 网络接口配置
- [ ] 防火墙规则
- [ ] 路由配置

### 网络性能
- [ ] 到网关的延迟
- [ ] 到网关的丢包率
- [ ] 带宽使用情况
EOF

echo "已生成对比检查清单: /workspace/对比检查清单.md"
echo ""
