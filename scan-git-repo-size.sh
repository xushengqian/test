#!/bin/bash

# Git仓库存储空间统计脚本
# 统计指定目录下所有Git仓库的.git目录占用空间

# 默认扫描目录，可通过命令行参数覆盖
ROOT_DIR="${1:-$HOME}"
TOTAL_KB=0

echo "正在扫描 Git 仓库..."
echo "扫描目录: $ROOT_DIR"
echo "=========================================="

# 查找所有 .git 目录
while IFS= read -r git_dir; do
    # 获取仓库路径（去掉 .git 部分）
    repo_path=$(dirname "$git_dir")
    repo_name=$(basename "$repo_path")
    
    # 计算 .git 目录大小（单位KB）
    size_kb=$(du -sk "$git_dir" 2>/dev/null | awk '{print $1}')
    
    if [ -n "$size_kb" ] && [ "$size_kb" -gt 0 ]; then
        # 转换为人类可读格式
        size_human=$(du -sh "$git_dir" 2>/dev/null | awk '{print $1}')
        
        # 累加总大小
        TOTAL_KB=$((TOTAL_KB + size_kb))
        
        # 显示单个仓库信息
        printf "%-60s %10s\n" "$repo_name" "$size_human"
    fi
done < <(find "$ROOT_DIR" -type d -name ".git" -prune 2>/dev/null)

echo "=========================================="

# 计算并显示总大小
if [ $TOTAL_KB -gt 0 ]; then
    # 转换为人类可读格式（使用 awk 替代 bc，兼容性更好）
    if [ $TOTAL_KB -lt 1024 ]; then
        total_display="${TOTAL_KB}KB"
    elif [ $TOTAL_KB -lt 1048576 ]; then
        total_mb=$(awk "BEGIN {printf \"%.2f\", $TOTAL_KB / 1024}")
        total_display="${total_mb}MB"
    elif [ $TOTAL_KB -lt 1073741824 ]; then
        total_gb=$(awk "BEGIN {printf \"%.2f\", $TOTAL_KB / 1048576}")
        total_display="${total_gb}GB"
    else
        total_tb=$(awk "BEGIN {printf \"%.2f\", $TOTAL_KB / 1073741824}")
        total_display="${total_tb}TB"
    fi
    
    echo "总计: $total_display (${TOTAL_KB} KB)"
    echo ""
    echo "说明："
    echo "- 仅统计 .git 目录（包含所有历史记录、分支、标签等）"
    echo "- 不包含工作区文件（代码文件本身）"
    echo "- 如果使用 Git LFS，LFS 文件也会被统计在内"
else
    echo "未找到任何 Git 仓库"
fi
