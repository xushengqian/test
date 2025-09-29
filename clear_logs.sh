#!/bin/bash

# 日志清理脚本 - 每天凌晨自动清空指定目录的日志文件
# 使用方法: ./clear_logs.sh

# 配置区域 - 请根据需要修改这些路径
# 可以添加多个目录路径，用空格分隔
LOG_DIRS=(
    "/var/log/myapp"
    "/home/user/logs"
    "/opt/application/logs"
)

# 日志文件扩展名模式（支持通配符）
LOG_PATTERNS=(
    "*.log"
    "*.txt"
    "*.out"
    "*.err"
)

# 备份目录（如果需要在清空前备份）
BACKUP_DIR="/backup/logs"
# 是否启用备份（true/false）
ENABLE_BACKUP=false
# 备份文件保留天数
BACKUP_RETENTION_DAYS=7

# 日志文件
SCRIPT_LOG="/var/log/clear_logs_script.log"

# 函数：记录日志
log_message() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1" >> "$SCRIPT_LOG"
}

# 函数：创建备份
backup_logs() {
    local dir="$1"
    local backup_subdir="$BACKUP_DIR/$(date '+%Y%m%d')"
    
    if [ ! -d "$backup_subdir" ]; then
        mkdir -p "$backup_subdir"
    fi
    
    local dir_name=$(basename "$dir")
    local backup_path="$backup_subdir/${dir_name}_$(date '+%H%M%S').tar.gz"
    
    # 创建压缩备份
    tar -czf "$backup_path" -C "$dir" . 2>/dev/null
    
    if [ $? -eq 0 ]; then
        log_message "备份成功: $backup_path"
    else
        log_message "备份失败: $dir"
    fi
}

# 函数：清理旧备份
cleanup_old_backups() {
    if [ -d "$BACKUP_DIR" ]; then
        find "$BACKUP_DIR" -type d -mtime +$BACKUP_RETENTION_DAYS -exec rm -rf {} + 2>/dev/null
        log_message "清理了 $BACKUP_RETENTION_DAYS 天前的旧备份"
    fi
}

# 函数：清空日志文件内容
clear_log_files() {
    local dir="$1"
    local pattern="$2"
    local count=0
    
    if [ ! -d "$dir" ]; then
        log_message "警告: 目录不存在 - $dir"
        return
    fi
    
    # 查找并清空匹配的文件
    while IFS= read -r -d '' file; do
        if [ -f "$file" ]; then
            # 清空文件内容但保留文件
            > "$file"
            count=$((count + 1))
        fi
    done < <(find "$dir" -type f -name "$pattern" -print0 2>/dev/null)
    
    if [ $count -gt 0 ]; then
        log_message "在 $dir 中清空了 $count 个 $pattern 文件"
    fi
}

# 主程序开始
main() {
    log_message "========== 开始执行日志清理任务 =========="
    
    # 遍历所有配置的目录
    for dir in "${LOG_DIRS[@]}"; do
        if [ -d "$dir" ]; then
            log_message "处理目录: $dir"
            
            # 如果启用备份，先进行备份
            if [ "$ENABLE_BACKUP" = "true" ]; then
                backup_logs "$dir"
            fi
            
            # 清空日志文件
            for pattern in "${LOG_PATTERNS[@]}"; do
                clear_log_files "$dir" "$pattern"
            done
        else
            log_message "跳过不存在的目录: $dir"
        fi
    done
    
    # 清理旧备份
    if [ "$ENABLE_BACKUP" = "true" ]; then
        cleanup_old_backups
    fi
    
    log_message "========== 日志清理任务完成 =========="
}

# 执行主程序
main

# 退出
exit 0