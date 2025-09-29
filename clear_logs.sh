#!/bin/bash

# 日志清理脚本 - 每天凌晨清空目标目录的日志内容
# 作者: AI Assistant
# 创建时间: $(date)

# 配置部分 - 请根据实际需求修改以下路径
LOG_DIRECTORIES=(
    "/var/log"           # 系统日志目录
    "/workspace/logs"    # 工作空间日志目录
    "/tmp/logs"          # 临时日志目录
)

# 日志文件扩展名模式
LOG_PATTERNS=(
    "*.log"
    "*.out"
    "*.err"
    "access_log*"
    "error_log*"
)

# 日志记录函数
log_message() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> /workspace/log_cleanup.log
}

# 清空日志文件内容（保留文件但清空内容）
clear_log_content() {
    local file="$1"
    if [ -f "$file" ] && [ -w "$file" ]; then
        # 获取文件大小（清理前）
        local size_before=$(stat -f%z "$file" 2>/dev/null || stat -c%s "$file" 2>/dev/null || echo "0")
        
        # 清空文件内容
        > "$file"
        
        log_message "已清空日志文件: $file (原大小: ${size_before} bytes)"
        return 0
    else
        log_message "跳过文件 $file (不存在或无写权限)"
        return 1
    fi
}

# 主清理函数
cleanup_logs() {
    log_message "开始执行日志清理任务"
    
    local total_files=0
    local cleared_files=0
    
    # 遍历每个日志目录
    for log_dir in "${LOG_DIRECTORIES[@]}"; do
        if [ ! -d "$log_dir" ]; then
            log_message "目录不存在，跳过: $log_dir"
            continue
        fi
        
        log_message "处理目录: $log_dir"
        
        # 遍历每个日志文件模式
        for pattern in "${LOG_PATTERNS[@]}"; do
            # 使用find命令查找匹配的日志文件
            while IFS= read -r -d '' file; do
                total_files=$((total_files + 1))
                if clear_log_content "$file"; then
                    cleared_files=$((cleared_files + 1))
                fi
            done < <(find "$log_dir" -maxdepth 2 -name "$pattern" -type f -print0 2>/dev/null)
        done
    done
    
    log_message "日志清理完成。总文件数: $total_files, 成功清理: $cleared_files"
}

# 创建日志目录（如果不存在）
create_log_directories() {
    for log_dir in "${LOG_DIRECTORIES[@]}"; do
        if [ ! -d "$log_dir" ]; then
            mkdir -p "$log_dir" 2>/dev/null
            log_message "创建日志目录: $log_dir"
        fi
    done
}

# 主程序
main() {
    # 确保日志清理记录目录存在
    mkdir -p "$(dirname /workspace/log_cleanup.log)" 2>/dev/null
    
    log_message "========== 日志清理脚本启动 =========="
    
    # 创建必要的日志目录
    create_log_directories
    
    # 执行清理
    cleanup_logs
    
    log_message "========== 日志清理脚本结束 =========="
}

# 如果脚本被直接执行（不是被source）
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi