#!/bin/bash

# system_check.sh
# FreeSwitch 转人工系统配置检查脚本

echo "=========================================="
echo "FreeSwitch 转人工系统配置检查"
echo "=========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查结果统计
PASSED=0
FAILED=0
WARNINGS=0

# 检查函数
check_file() {
    local file_path=$1
    local description=$2
    
    if [ -f "$file_path" ]; then
        echo -e "${GREEN}✓${NC} $description: $file_path"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}✗${NC} $description: $file_path (文件不存在)"
        ((FAILED++))
        return 1
    fi
}

check_directory() {
    local dir_path=$1
    local description=$2
    
    if [ -d "$dir_path" ]; then
        echo -e "${GREEN}✓${NC} $description: $dir_path"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}✗${NC} $description: $dir_path (目录不存在)"
        ((FAILED++))
        return 1
    fi
}

check_command() {
    local command=$1
    local description=$2
    
    if command -v "$command" >/dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} $description: $command 可用"
        ((PASSED++))
        return 0
    else
        echo -e "${RED}✗${NC} $description: $command 不可用"
        ((FAILED++))
        return 1
    fi
}

warn_if_missing() {
    local file_path=$1
    local description=$2
    
    if [ ! -f "$file_path" ]; then
        echo -e "${YELLOW}⚠${NC} $description: $file_path (建议创建)"
        ((WARNINGS++))
    fi
}

echo -e "\n${YELLOW}1. 检查 FreeSwitch 基本环境${NC}"
echo "----------------------------------------"

# 检查FreeSwitch是否安装
check_command "fs_cli" "FreeSwitch CLI工具"

# 检查FreeSwitch配置目录
check_directory "/usr/local/freeswitch" "FreeSwitch安装目录"
check_directory "/usr/local/freeswitch/conf" "FreeSwitch配置目录"
check_directory "/usr/local/freeswitch/scripts" "FreeSwitch脚本目录"
check_directory "/usr/local/freeswitch/sounds" "FreeSwitch语音文件目录"

echo -e "\n${YELLOW}2. 检查项目配置文件${NC}"
echo "----------------------------------------"

# 检查拨号计划配置
check_file "./dialplan/transfer_to_human.xml" "转人工拨号计划"
check_file "./dialplan/queue_management.xml" "队列管理拨号计划"

# 检查FIFO配置
check_file "./conf/autoload_configs/fifo.conf.xml" "FIFO队列配置"

echo -e "\n${YELLOW}3. 检查Lua脚本文件${NC}"
echo "----------------------------------------"

# 检查所有Lua脚本
check_file "./scripts/intent_detection.lua" "意图检测脚本"
check_file "./scripts/voice_intent_processor.lua" "语音处理脚本"
check_file "./scripts/smart_queue_router.lua" "智能路由脚本"
check_file "./scripts/agent_authentication.lua" "客服认证脚本"
check_file "./scripts/call_transfer_controller.lua" "呼叫转移控制脚本"
check_file "./scripts/logging_and_monitoring.lua" "日志监控脚本"
check_file "./scripts/callback_request.lua" "回呼请求脚本"

echo -e "\n${YELLOW}4. 检查语音文件${NC}"
echo "----------------------------------------"

# 检查关键语音文件
warn_if_missing "/usr/local/freeswitch/sounds/welcome.wav" "欢迎语音文件"
warn_if_missing "/usr/local/freeswitch/sounds/please_speak.wav" "语音提示文件"
warn_if_missing "/usr/local/freeswitch/sounds/transferring_to_agent.wav" "转接提示音"
warn_if_missing "/usr/local/freeswitch/sounds/press_0_for_agent.wav" "按键提示音"
warn_if_missing "/usr/local/freeswitch/sounds/hold_music.wav" "等候音乐"

echo -e "\n${YELLOW}5. 检查FreeSwitch服务状态${NC}"
echo "----------------------------------------"

# 检查FreeSwitch进程
if pgrep -x "freeswitch" > /dev/null; then
    echo -e "${GREEN}✓${NC} FreeSwitch服务正在运行"
    ((PASSED++))
    
    # 尝试连接到FreeSwitch
    if timeout 5 fs_cli -x "status" >/dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} FreeSwitch CLI连接正常"
        ((PASSED++))
    else
        echo -e "${RED}✗${NC} 无法连接到FreeSwitch CLI"
        ((FAILED++))
    fi
else
    echo -e "${RED}✗${NC} FreeSwitch服务未运行"
    ((FAILED++))
fi

echo -e "\n${YELLOW}6. 检查模块依赖${NC}"
echo "----------------------------------------"

# 检查必要的FreeSwitch模块
if pgrep -x "freeswitch" > /dev/null; then
    modules=("mod_fifo" "mod_lua" "mod_dptools" "mod_dialplan_xml")
    
    for module in "${modules[@]}"; do
        if timeout 5 fs_cli -x "module_exists $module" | grep -q "true"; then
            echo -e "${GREEN}✓${NC} $module 模块已加载"
            ((PASSED++))
        else
            echo -e "${YELLOW}⚠${NC} $module 模块状态未知"
            ((WARNINGS++))
        fi
    done
else
    echo -e "${YELLOW}⚠${NC} FreeSwitch未运行，跳过模块检查"
    ((WARNINGS++))
fi

echo -e "\n${YELLOW}7. 配置文件语法检查${NC}"
echo "----------------------------------------"

# 检查XML文件语法
xml_files=("./dialplan/transfer_to_human.xml" "./dialplan/queue_management.xml" "./conf/autoload_configs/fifo.conf.xml")

for xml_file in "${xml_files[@]}"; do
    if [ -f "$xml_file" ]; then
        if xmllint --noout "$xml_file" 2>/dev/null; then
            echo -e "${GREEN}✓${NC} $xml_file XML语法正确"
            ((PASSED++))
        else
            echo -e "${RED}✗${NC} $xml_file XML语法错误"
            ((FAILED++))
        fi
    fi
done

# 检查Lua文件语法
lua_files=(./scripts/*.lua)
for lua_file in "${lua_files[@]}"; do
    if [ -f "$lua_file" ]; then
        if luac -p "$lua_file" 2>/dev/null; then
            echo -e "${GREEN}✓${NC} $(basename "$lua_file") Lua语法正确"
            ((PASSED++))
        else
            echo -e "${RED}✗${NC} $(basename "$lua_file") Lua语法错误"
            ((FAILED++))
        fi
    fi
done

echo -e "\n${YELLOW}8. 生成部署建议${NC}"
echo "----------------------------------------"

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}✓${NC} 所有必要组件检查通过，可以部署"
    
    if [ $WARNINGS -gt 0 ]; then
        echo -e "${YELLOW}建议处理以下警告项以获得最佳体验：${NC}"
        echo "  - 录制并放置必要的语音文件"
        echo "  - 配置客服分机用户"
        echo "  - 测试语音识别功能"
    fi
    
    echo -e "\n${GREEN}部署步骤：${NC}"
    echo "1. 复制配置文件到FreeSwitch目录"
    echo "   cp dialplan/*.xml /usr/local/freeswitch/conf/dialplan/"
    echo "   cp conf/autoload_configs/*.xml /usr/local/freeswitch/conf/autoload_configs/"
    echo "   cp scripts/*.lua /usr/local/freeswitch/scripts/"
    echo ""
    echo "2. 重新加载FreeSwitch配置"
    echo "   fs_cli -x 'reloadxml'"
    echo ""
    echo "3. 配置客服分机并测试"
    
else
    echo -e "${RED}✗${NC} 发现 $FAILED 个错误，需要先解决后再部署"
    echo -e "${YELLOW}请检查并修复上述错误项${NC}"
fi

echo -e "\n=========================================="
echo -e "检查结果汇总："
echo -e "${GREEN}✓ 通过: $PASSED${NC}"
echo -e "${RED}✗ 失败: $FAILED${NC}"
echo -e "${YELLOW}⚠ 警告: $WARNINGS${NC}"
echo "=========================================="

# 退出码
if [ $FAILED -eq 0 ]; then
    exit 0
else
    exit 1
fi