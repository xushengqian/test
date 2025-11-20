#!/bin/bash
# FreeSWITCH 快速修复脚本
# 用于在出现 DESTINATION_OUT_OF_ORDER 错误时快速恢复

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 配置
GATEWAY_NAME="gwopensips"
LOG_FILE="/tmp/freeswitch_quickfix_$(date +%Y%m%d_%H%M%S).log"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}FreeSWITCH 快速修复脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo "日志文件: ${LOG_FILE}"
echo ""

# 记录日志函数
log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a "$LOG_FILE"
}

# 检查 FreeSWITCH 是否运行
check_freeswitch() {
    if pgrep -x freeswitch >/dev/null; then
        log "✓ FreeSWITCH 正在运行"
        return 0
    else
        log "✗ FreeSWITCH 未运行"
        return 1
    fi
}

# 检查网关状态
check_gateway() {
    log "检查网关状态: ${GATEWAY_NAME}"
    GATEWAY_STATUS=$(fs_cli -x "sofia status gateway ${GATEWAY_NAME}" 2>/dev/null)
    
    if echo "$GATEWAY_STATUS" | grep -q "State.*REGED"; then
        log "✓ 网关已注册"
        return 0
    else
        log "✗ 网关未注册或异常"
        return 1
    fi
}

# 修复选项菜单
show_menu() {
    echo ""
    echo -e "${YELLOW}请选择修复操作:${NC}"
    echo "1) 重启指定网关 (推荐)"
    echo "2) 重启 SIP Profile"
    echo "3) 清理异常通道"
    echo "4) 检查并优化系统资源"
    echo "5) 完全重启 FreeSWITCH (谨慎)"
    echo "6) 执行全部修复操作"
    echo "0) 退出"
    echo ""
    echo -n "请输入选项 [0-6]: "
}

# 1. 重启网关
restart_gateway() {
    log "开始重启网关: ${GATEWAY_NAME}"
    
    # 杀掉网关
    fs_cli -x "sofia profile external killgw ${GATEWAY_NAME}" 2>&1 | tee -a "$LOG_FILE"
    sleep 2
    
    # 重新扫描配置
    fs_cli -x "sofia profile external rescan" 2>&1 | tee -a "$LOG_FILE"
    sleep 3
    
    # 检查网关状态
    if check_gateway; then
        log "✓ 网关重启成功"
        return 0
    else
        log "✗ 网关重启失败"
        return 1
    fi
}

# 2. 重启 SIP Profile
restart_profile() {
    log "开始重启 SIP Profile: external"
    
    fs_cli -x "sofia profile external restart" 2>&1 | tee -a "$LOG_FILE"
    sleep 5
    
    # 检查 Profile 状态
    PROFILE_STATUS=$(fs_cli -x "sofia status profile external" 2>/dev/null)
    if echo "$PROFILE_STATUS" | grep -q "RUNNING"; then
        log "✓ SIP Profile 重启成功"
        return 0
    else
        log "✗ SIP Profile 重启失败"
        return 1
    fi
}

# 3. 清理异常通道
clear_channels() {
    log "检查异常通道..."
    
    # 获取通道列表
    CHANNELS=$(fs_cli -x "show channels" 2>/dev/null)
    
    if [ -z "$CHANNELS" ] || echo "$CHANNELS" | grep -q "0 total"; then
        log "✓ 没有活跃通道"
        return 0
    fi
    
    # 统计通道数
    CHANNEL_COUNT=$(echo "$CHANNELS" | grep "total" | awk '{print $1}')
    log "当前活跃通道数: $CHANNEL_COUNT"
    
    # 查找长时间挂起的通道
    log "查找异常通道..."
    STUCK_CHANNELS=$(fs_cli -x "show channels" 2>/dev/null | grep -E "CS_HANGUP|CS_DESTROY" | awk '{print $1}')
    
    if [ ! -z "$STUCK_CHANNELS" ]; then
        log "发现 $(echo "$STUCK_CHANNELS" | wc -l) 个异常通道，准备清理"
        
        # 杀掉异常通道
        echo "$STUCK_CHANNELS" | while read uuid; do
            if [ ! -z "$uuid" ]; then
                log "清理通道: $uuid"
                fs_cli -x "uuid_kill $uuid" 2>&1 | tee -a "$LOG_FILE"
            fi
        done
        
        sleep 2
        log "✓ 异常通道清理完成"
        return 0
    else
        log "✓ 未发现异常通道"
        return 0
    fi
}

# 4. 检查并优化系统资源
optimize_resources() {
    log "检查系统资源..."
    
    FS_PID=$(pgrep -x freeswitch)
    if [ -z "$FS_PID" ]; then
        log "✗ FreeSWITCH 未运行"
        return 1
    fi
    
    # 检查文件描述符
    FD_COUNT=$(lsof -p $FS_PID 2>/dev/null | wc -l)
    FD_LIMIT=$(cat /proc/$FS_PID/limits | grep "open files" | awk '{print $4}')
    
    log "文件描述符: $FD_COUNT / $FD_LIMIT"
    
    if [ ! -z "$FD_COUNT" ] && [ ! -z "$FD_LIMIT" ]; then
        FD_USAGE=$((FD_COUNT * 100 / FD_LIMIT))
        
        if [ $FD_USAGE -gt 80 ]; then
            log "⚠ 警告: 文件描述符使用率过高 (${FD_USAGE}%)"
            log "建议: 增加文件描述符限制"
            
            # 提示用户是否增加限制
            echo -e "${YELLOW}是否尝试增加文件描述符限制? (需要 root 权限) [y/N]${NC}"
            read -r response
            if [[ "$response" =~ ^[Yy]$ ]]; then
                log "尝试增加文件描述符限制..."
                prlimit --pid $FS_PID --nofile=65536:65536 2>&1 | tee -a "$LOG_FILE"
                
                if [ $? -eq 0 ]; then
                    log "✓ 文件描述符限制已增加"
                else
                    log "✗ 无法增加文件描述符限制，可能需要修改系统配置"
                fi
            fi
        else
            log "✓ 文件描述符使用正常 (${FD_USAGE}%)"
        fi
    fi
    
    # 检查内存
    MEM_USAGE=$(ps aux | grep freeswitch | grep -v grep | head -1 | awk '{print $4}')
    log "内存使用率: ${MEM_USAGE}%"
    
    # 检查 CPU
    CPU_USAGE=$(ps aux | grep freeswitch | grep -v grep | head -1 | awk '{print $3}')
    log "CPU 使用率: ${CPU_USAGE}%"
    
    return 0
}

# 5. 完全重启 FreeSWITCH
restart_freeswitch() {
    echo -e "${RED}警告: 此操作将重启 FreeSWITCH，会中断所有通话!${NC}"
    echo -e "${YELLOW}确认要继续吗? [y/N]${NC}"
    read -r response
    
    if [[ ! "$response" =~ ^[Yy]$ ]]; then
        log "用户取消重启操作"
        return 1
    fi
    
    log "开始重启 FreeSWITCH..."
    
    # 使用 systemctl 重启
    if command -v systemctl >/dev/null 2>&1; then
        systemctl restart freeswitch 2>&1 | tee -a "$LOG_FILE"
        sleep 5
        
        if check_freeswitch; then
            log "✓ FreeSWITCH 重启成功"
            
            # 等待网关注册
            log "等待网关注册..."
            sleep 10
            
            if check_gateway; then
                log "✓ 网关注册成功"
                return 0
            else
                log "⚠ 网关尚未注册，可能需要更多时间"
                return 1
            fi
        else
            log "✗ FreeSWITCH 重启失败"
            return 1
        fi
    else
        log "✗ 找不到 systemctl 命令"
        return 1
    fi
}

# 6. 执行全部修复操作
full_repair() {
    log "开始执行全部修复操作..."
    
    # 1. 清理异常通道
    log "步骤 1/3: 清理异常通道"
    clear_channels
    
    # 2. 重启网关
    log "步骤 2/3: 重启网关"
    restart_gateway
    
    # 3. 优化资源
    log "步骤 3/3: 优化系统资源"
    optimize_resources
    
    log "✓ 全部修复操作完成"
    
    # 显示当前状态
    echo ""
    log "当前状态:"
    check_gateway
    fs_cli -x "show channels count" 2>&1 | tee -a "$LOG_FILE"
    
    return 0
}

# 主程序
main() {
    # 检查权限
    if [ "$EUID" -ne 0 ]; then
        echo -e "${YELLOW}提示: 某些操作可能需要 root 权限${NC}"
        echo ""
    fi
    
    # 检查 FreeSWITCH
    if ! check_freeswitch; then
        echo -e "${RED}错误: FreeSWITCH 未运行，无法执行修复操作${NC}"
        exit 1
    fi
    
    # 显示当前状态
    log "当前状态:"
    check_gateway
    fs_cli -x "show channels count" 2>&1 | tee -a "$LOG_FILE"
    
    # 主循环
    while true; do
        show_menu
        read -r choice
        
        case $choice in
            1)
                restart_gateway
                ;;
            2)
                restart_profile
                ;;
            3)
                clear_channels
                ;;
            4)
                optimize_resources
                ;;
            5)
                restart_freeswitch
                ;;
            6)
                full_repair
                ;;
            0)
                log "退出修复脚本"
                exit 0
                ;;
            *)
                echo -e "${RED}无效选项，请重新选择${NC}"
                ;;
        esac
        
        echo ""
        echo -e "${GREEN}操作完成，按任意键继续...${NC}"
        read -n 1 -s
    done
}

# 运行主程序
main
