#!/bin/bash

#############################################################################
# FreeSWITCH 人工坐席接入系统安装脚本
# 功能：自动安装和配置人工坐席队列管理系统
# 作者：AI Assistant
# 版本：1.0
#############################################################################

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置变量
FREESWITCH_HOME="/usr/local/freeswitch"
SCRIPT_DIR="${FREESWITCH_HOME}/scripts/agent_system"
DIALPLAN_DIR="${FREESWITCH_HOME}/conf/dialplan/default"
DB_DIR="${FREESWITCH_HOME}/db"
SOUNDS_DIR="${FREESWITCH_HOME}/sounds"

# 日志函数
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

log_step() {
    echo -e "${BLUE}[STEP]${NC} $1"
}

# 检查是否为root用户
check_root() {
    if [[ $EUID -ne 0 ]]; then
        log_error "此脚本需要root权限运行"
        exit 1
    fi
}

# 检查FreeSWITCH是否安装
check_freeswitch() {
    log_step "检查FreeSWITCH安装状态..."
    
    if [ ! -d "$FREESWITCH_HOME" ]; then
        log_error "FreeSWITCH未安装或安装路径不正确: $FREESWITCH_HOME"
        exit 1
    fi
    
    if ! command -v fs_cli &> /dev/null; then
        log_error "fs_cli命令未找到，请检查FreeSWITCH安装"
        exit 1
    fi
    
    log_info "FreeSWITCH安装检查通过"
}

# 检测操作系统
detect_os() {
    log_step "检测操作系统..."
    
    if [ -f /etc/redhat-release ]; then
        OS="centos"
        log_info "检测到CentOS/RHEL系统"
    elif [ -f /etc/debian_version ]; then
        OS="ubuntu"
        log_info "检测到Ubuntu/Debian系统"
    else
        log_warn "未知操作系统，将尝试通用安装方式"
        OS="unknown"
    fi
}

# 安装依赖包
install_dependencies() {
    log_step "安装系统依赖包..."
    
    case $OS in
        "centos")
            yum update -y
            yum install -y lua lua-devel sqlite3-devel gcc make wget
            ;;
        "ubuntu")
            apt-get update
            apt-get install -y lua5.1 liblua5.1-dev libsqlite3-dev gcc make wget luarocks
            ;;
        *)
            log_warn "请手动安装以下依赖包: lua, lua-devel, sqlite3-devel, gcc, make"
            ;;
    esac
    
    # 安装LuaRocks（如果未安装）
    if ! command -v luarocks &> /dev/null; then
        log_step "安装LuaRocks..."
        cd /tmp
        wget https://luarocks.org/releases/luarocks-3.9.2.tar.gz
        tar zxpf luarocks-3.9.2.tar.gz
        cd luarocks-3.9.2
        ./configure --prefix=/usr/local
        make && make install
        cd /
        rm -rf /tmp/luarocks-3.9.2*
    fi
    
    # 安装LuaSQL SQLite3模块
    log_step "安装LuaSQL SQLite3模块..."
    luarocks install luasql-sqlite3
    
    # 验证安装
    if lua -e "require 'luasql.sqlite3'; print('LuaSQL SQLite3 安装成功')" 2>/dev/null; then
        log_info "依赖包安装完成"
    else
        log_error "LuaSQL SQLite3模块安装失败"
        exit 1
    fi
}

# 创建目录结构
create_directories() {
    log_step "创建目录结构..."
    
    mkdir -p "$SCRIPT_DIR"
    mkdir -p "$DB_DIR"
    mkdir -p "$SOUNDS_DIR"
    mkdir -p "${FREESWITCH_HOME}/log"
    
    # 设置权限
    chown -R freeswitch:freeswitch "$FREESWITCH_HOME" 2>/dev/null || true
    
    log_info "目录结构创建完成"
}

# 复制脚本文件
install_scripts() {
    log_step "安装Lua脚本文件..."
    
    local current_dir=$(dirname "$(readlink -f "$0")")
    
    # 复制Lua脚本
    cp "$current_dir/agent_queue.lua" "$SCRIPT_DIR/"
    cp "$current_dir/agent_management.lua" "$SCRIPT_DIR/"
    cp "$current_dir/queue_stats.lua" "$SCRIPT_DIR/"
    cp "$current_dir/init_database.lua" "$SCRIPT_DIR/"
    
    # 设置执行权限
    chmod +x "$SCRIPT_DIR"/*.lua
    chown -R freeswitch:freeswitch "$SCRIPT_DIR" 2>/dev/null || true
    
    log_info "Lua脚本安装完成"
}

# 安装拨号计划
install_dialplan() {
    log_step "安装拨号计划配置..."
    
    local current_dir=$(dirname "$(readlink -f "$0")")
    
    # 备份现有配置
    if [ -f "$DIALPLAN_DIR/dialplan_agent_queue.xml" ]; then
        cp "$DIALPLAN_DIR/dialplan_agent_queue.xml" "$DIALPLAN_DIR/dialplan_agent_queue.xml.bak.$(date +%Y%m%d_%H%M%S)"
        log_info "已备份现有拨号计划配置"
    fi
    
    # 复制新配置
    cp "$current_dir/dialplan_agent_queue.xml" "$DIALPLAN_DIR/"
    chown freeswitch:freeswitch "$DIALPLAN_DIR/dialplan_agent_queue.xml" 2>/dev/null || true
    
    log_info "拨号计划配置安装完成"
}

# 初始化数据库
init_database() {
    log_step "初始化数据库..."
    
    cd "$SCRIPT_DIR"
    
    # 以freeswitch用户身份运行（如果可能）
    if id "freeswitch" &>/dev/null; then
        sudo -u freeswitch lua init_database.lua
    else
        lua init_database.lua
        chown freeswitch:freeswitch "$DB_DIR/agent_queue.db" 2>/dev/null || true
    fi
    
    log_info "数据库初始化完成"
}

# 创建示例音频文件
create_sample_sounds() {
    log_step "创建示例提示音文件..."
    
    # 这里可以添加创建或下载示例音频文件的逻辑
    # 目前创建一个占位符文件
    touch "$SOUNDS_DIR/queue_announcement.wav"
    chown freeswitch:freeswitch "$SOUNDS_DIR/queue_announcement.wav" 2>/dev/null || true
    
    log_info "示例音频文件创建完成"
    log_warn "请将实际的提示音文件放置到 $SOUNDS_DIR/ 目录"
}

# 重新加载FreeSWITCH配置
reload_freeswitch() {
    log_step "重新加载FreeSWITCH配置..."
    
    if pgrep -x "freeswitch" > /dev/null; then
        fs_cli -x "reloadxml" || log_warn "重新加载配置失败，请手动执行: fs_cli -x 'reloadxml'"
        log_info "FreeSWITCH配置重新加载完成"
    else
        log_warn "FreeSWITCH未运行，请启动后手动执行: fs_cli -x 'reloadxml'"
    fi
}

# 运行测试
run_tests() {
    log_step "运行系统测试..."
    
    cd "$SCRIPT_DIR"
    
    # 测试数据库连接
    if lua -e "
        require 'luasql.sqlite3'
        local env = luasql.sqlite3()
        local conn = env:connect('$DB_DIR/agent_queue.db')
        if conn then
            print('数据库连接测试: 通过')
            conn:close()
        else
            print('数据库连接测试: 失败')
            os.exit(1)
        end
        env:close()
    "; then
        log_info "数据库连接测试通过"
    else
        log_error "数据库连接测试失败"
        return 1
    fi
    
    # 测试脚本语法
    for script in agent_queue.lua agent_management.lua queue_stats.lua; do
        if lua -e "dofile('$script')" 2>/dev/null; then
            log_info "$script 语法检查通过"
        else
            log_error "$script 语法检查失败"
            return 1
        fi
    done
    
    log_info "系统测试完成"
}

# 显示安装结果
show_results() {
    log_step "安装完成！"
    
    echo
    echo "=========================================="
    echo "  FreeSWITCH 人工坐席系统安装完成"
    echo "=========================================="
    echo
    echo "📁 安装路径:"
    echo "   脚本目录: $SCRIPT_DIR"
    echo "   数据库文件: $DB_DIR/agent_queue.db"
    echo "   拨号计划: $DIALPLAN_DIR/dialplan_agent_queue.xml"
    echo
    echo "📞 测试号码:"
    echo "   8000 - 默认客服队列"
    echo "   8001 - 技术支持队列"
    echo "   8002 - 销售队列"
    echo "   8003 - VIP客户队列"
    echo
    echo "👥 坐席管理:"
    echo "   9000 + 分机号 - 坐席登录"
    echo "   9001 + 分机号 - 坐席登出"
    echo "   9999 - 坐席状态菜单"
    echo "   9998 - 队列统计查询"
    echo
    echo "🔧 初始坐席账号:"
    echo "   1001 - 张三 (客服,技术支持)"
    echo "   1002 - 李四 (客服,销售)"
    echo "   1003 - 王五 (技术支持)"
    echo "   1004 - 赵六 (销售,VIP)"
    echo "   1005 - 钱七 (VIP)"
    echo
    echo "📊 API使用示例:"
    echo "   fs_cli -x \"lua $SCRIPT_DIR/agent_management.lua login 1001\""
    echo "   fs_cli -x \"lua $SCRIPT_DIR/queue_stats.lua overview\""
    echo
    echo "📖 详细文档请查看: README.md"
    echo
    log_info "系统已准备就绪，可以开始使用！"
}

# 主安装流程
main() {
    echo "=========================================="
    echo "  FreeSWITCH 人工坐席系统安装程序"
    echo "=========================================="
    echo
    
    check_root
    detect_os
    check_freeswitch
    install_dependencies
    create_directories
    install_scripts
    install_dialplan
    init_database
    create_sample_sounds
    reload_freeswitch
    
    if run_tests; then
        show_results
    else
        log_error "安装过程中发现问题，请检查日志"
        exit 1
    fi
}

# 卸载函数
uninstall() {
    log_step "开始卸载人工坐席系统..."
    
    # 停止相关进程
    log_info "停止相关服务..."
    
    # 删除文件
    log_info "删除安装文件..."
    rm -rf "$SCRIPT_DIR"
    rm -f "$DIALPLAN_DIR/dialplan_agent_queue.xml"
    rm -f "$DB_DIR/agent_queue.db"
    
    # 重新加载配置
    if pgrep -x "freeswitch" > /dev/null; then
        fs_cli -x "reloadxml"
    fi
    
    log_info "卸载完成"
}

# 命令行参数处理
case "${1:-install}" in
    "install")
        main
        ;;
    "uninstall")
        uninstall
        ;;
    "test")
        run_tests
        ;;
    *)
        echo "用法: $0 [install|uninstall|test]"
        echo "  install   - 安装系统（默认）"
        echo "  uninstall - 卸载系统"
        echo "  test      - 运行测试"
        exit 1
        ;;
esac