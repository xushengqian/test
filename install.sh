#!/bin/bash

#####################################################################
# FreeSWITCH Lua 转接脚本安装工具
# 用于快速部署转接相关的 Lua 脚本
#####################################################################

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印函数
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查是否以root运行
check_root() {
    if [ "$EUID" -ne 0 ]; then 
        print_error "请使用 root 权限运行此脚本"
        exit 1
    fi
}

# 检测 FreeSWITCH 安装路径
detect_freeswitch() {
    print_info "检测 FreeSWITCH 安装路径..."
    
    # 可能的安装路径
    local paths=(
        "/usr/share/freeswitch"
        "/usr/local/freeswitch"
        "/opt/freeswitch"
    )
    
    for path in "${paths[@]}"; do
        if [ -d "$path" ]; then
            FREESWITCH_BASE="$path"
            FREESWITCH_SCRIPTS="$path/scripts"
            print_info "找到 FreeSWITCH: $FREESWITCH_BASE"
            return 0
        fi
    done
    
    print_error "未找到 FreeSWITCH 安装目录"
    return 1
}

# 创建备份
create_backup() {
    local file=$1
    if [ -f "$file" ]; then
        local backup="${file}.bak.$(date +%Y%m%d_%H%M%S)"
        cp "$file" "$backup"
        print_info "已备份: $backup"
    fi
}

# 安装 Lua 脚本
install_scripts() {
    print_info "安装 Lua 脚本到 $FREESWITCH_SCRIPTS ..."
    
    local scripts=(
        "transfer_to_agent.lua"
        "transfer_simple.lua"
        "agent_utils.lua"
        "transfer_example.lua"
        "test_agent_status.lua"
    )
    
    for script in "${scripts[@]}"; do
        if [ -f "$script" ]; then
            # 备份已存在的文件
            if [ -f "$FREESWITCH_SCRIPTS/$script" ]; then
                create_backup "$FREESWITCH_SCRIPTS/$script"
            fi
            
            # 复制文件
            cp "$script" "$FREESWITCH_SCRIPTS/"
            chmod 644 "$FREESWITCH_SCRIPTS/$script"
            print_info "  ✓ $script"
        else
            print_warn "  ✗ $script (文件不存在)"
        fi
    done
}

# 安装 Dialplan 配置（可选）
install_dialplan() {
    print_info "是否安装 Dialplan 配置？[y/N]"
    read -r response
    
    if [[ "$response" =~ ^[Yy]$ ]]; then
        local dialplan_dir
        
        # 检测配置目录
        if [ -d "/etc/freeswitch/dialplan" ]; then
            dialplan_dir="/etc/freeswitch/dialplan"
        elif [ -d "/usr/local/freeswitch/conf/dialplan" ]; then
            dialplan_dir="/usr/local/freeswitch/conf/dialplan"
        else
            print_error "未找到 Dialplan 目录"
            return 1
        fi
        
        print_info "安装 Dialplan 配置到 $dialplan_dir ..."
        
        if [ -f "dialplan_example.xml" ]; then
            local target="$dialplan_dir/transfer_agent.xml"
            
            if [ -f "$target" ]; then
                create_backup "$target"
            fi
            
            cp "dialplan_example.xml" "$target"
            chmod 644 "$target"
            print_info "  ✓ transfer_agent.xml"
            
            print_warn "请手动检查并修改 Dialplan 配置"
        fi
    fi
}

# 初始化数据库
init_database() {
    print_info "是否初始化数据库？[y/N]"
    read -r response
    
    if [[ "$response" =~ ^[Yy]$ ]]; then
        if [ -f "setup_database.sql" ]; then
            local db_path="/var/lib/freeswitch/freeswitch.db"
            
            print_info "初始化数据库: $db_path"
            
            if command -v sqlite3 &> /dev/null; then
                sqlite3 "$db_path" < setup_database.sql
                print_info "  ✓ 数据库初始化完成"
            else
                print_error "sqlite3 未安装"
                return 1
            fi
        fi
    fi
}

# 测试安装
test_installation() {
    print_info "是否运行测试？[y/N]"
    read -r response
    
    if [[ "$response" =~ ^[Yy]$ ]]; then
        print_info "运行坐席状态测试..."
        
        if command -v fs_cli &> /dev/null; then
            fs_cli -x "luarun test_agent_status.lua"
        else
            print_warn "fs_cli 未找到，跳过测试"
        fi
    fi
}

# 重载 FreeSWITCH 配置
reload_config() {
    print_info "是否重载 FreeSWITCH 配置？[y/N]"
    read -r response
    
    if [[ "$response" =~ ^[Yy]$ ]]; then
        if command -v fs_cli &> /dev/null; then
            print_info "重载 XML 配置..."
            fs_cli -x "reloadxml"
            print_info "  ✓ 配置已重载"
        else
            print_warn "fs_cli 未找到，请手动重载配置"
        fi
    fi
}

# 显示帮助信息
show_help() {
    cat << EOF
FreeSWITCH Lua 转接脚本安装工具

使用方法:
    sudo ./install.sh [选项]

选项:
    -h, --help          显示帮助信息
    -y, --yes           自动确认所有问题
    -s, --scripts-only  仅安装脚本
    
示例:
    sudo ./install.sh           # 交互式安装
    sudo ./install.sh -y        # 自动安装所有组件
    sudo ./install.sh -s        # 仅安装脚本文件

EOF
}

# 显示总结
show_summary() {
    echo ""
    print_info "========================================"
    print_info "安装完成！"
    print_info "========================================"
    echo ""
    print_info "已安装的脚本："
    print_info "  - transfer_to_agent.lua    (完整的转接脚本)"
    print_info "  - transfer_simple.lua      (简化示例)"
    print_info "  - agent_utils.lua          (工具库)"
    print_info "  - transfer_example.lua     (使用示例)"
    print_info "  - test_agent_status.lua    (测试脚本)"
    echo ""
    print_info "下一步："
    print_info "  1. 查看文档: cat README.md"
    print_info "  2. 查看快速参考: cat QUICK_REFERENCE.md"
    print_info "  3. 测试脚本: fs_cli -x 'luarun test_agent_status.lua'"
    print_info "  4. 配置 Dialplan 并测试转接功能"
    echo ""
}

# 主函数
main() {
    # 解析参数
    AUTO_YES=false
    SCRIPTS_ONLY=false
    
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                show_help
                exit 0
                ;;
            -y|--yes)
                AUTO_YES=true
                shift
                ;;
            -s|--scripts-only)
                SCRIPTS_ONLY=true
                shift
                ;;
            *)
                print_error "未知选项: $1"
                show_help
                exit 1
                ;;
        esac
    done
    
    echo ""
    print_info "========================================"
    print_info "FreeSWITCH Lua 转接脚本安装工具"
    print_info "========================================"
    echo ""
    
    # 检查 root 权限
    check_root
    
    # 检测 FreeSWITCH
    if ! detect_freeswitch; then
        exit 1
    fi
    
    echo ""
    
    # 安装脚本
    install_scripts
    
    echo ""
    
    # 如果仅安装脚本，跳过其他步骤
    if [ "$SCRIPTS_ONLY" = true ]; then
        show_summary
        exit 0
    fi
    
    # 安装 Dialplan（可选）
    if [ "$AUTO_YES" = false ]; then
        install_dialplan
        echo ""
    fi
    
    # 初始化数据库（可选）
    if [ "$AUTO_YES" = false ]; then
        init_database
        echo ""
    fi
    
    # 重载配置
    if [ "$AUTO_YES" = false ]; then
        reload_config
        echo ""
    fi
    
    # 测试
    if [ "$AUTO_YES" = false ]; then
        test_installation
        echo ""
    fi
    
    # 显示总结
    show_summary
}

# 运行主函数
main "$@"
