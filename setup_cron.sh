#!/bin/bash

# Cron定时任务设置脚本
# 用于配置每天凌晨自动执行日志清理

SCRIPT_PATH="/workspace/clear_logs.sh"
CRON_TIME="0 2 * * *"  # 默认每天凌晨2点执行

echo "======================================"
echo "日志清理定时任务配置脚本"
echo "======================================"
echo ""

# 检查脚本是否存在
if [ ! -f "$SCRIPT_PATH" ]; then
    echo "错误: 清理脚本不存在 - $SCRIPT_PATH"
    echo "请先确保 clear_logs.sh 脚本已创建"
    exit 1
fi

# 显示当前cron任务
echo "当前的cron任务列表："
crontab -l 2>/dev/null || echo "（暂无定时任务）"
echo ""

# 询问用户执行时间
echo "请选择日志清理的执行时间："
echo "1) 每天凌晨 0:00"
echo "2) 每天凌晨 1:00"
echo "3) 每天凌晨 2:00 (默认)"
echo "4) 每天凌晨 3:00"
echo "5) 每天凌晨 4:00"
echo "6) 自定义时间"
echo ""

read -p "请输入选项 (1-6) [默认: 3]: " choice
choice=${choice:-3}

case $choice in
    1)
        CRON_TIME="0 0 * * *"
        TIME_DESC="每天凌晨 0:00"
        ;;
    2)
        CRON_TIME="0 1 * * *"
        TIME_DESC="每天凌晨 1:00"
        ;;
    3)
        CRON_TIME="0 2 * * *"
        TIME_DESC="每天凌晨 2:00"
        ;;
    4)
        CRON_TIME="0 3 * * *"
        TIME_DESC="每天凌晨 3:00"
        ;;
    5)
        CRON_TIME="0 4 * * *"
        TIME_DESC="每天凌晨 4:00"
        ;;
    6)
        echo "请输入cron表达式（格式: 分 时 日 月 星期）"
        echo "例如: 30 3 * * * 表示每天凌晨3:30"
        read -p "cron表达式: " CRON_TIME
        TIME_DESC="自定义时间: $CRON_TIME"
        ;;
    *)
        echo "无效的选项，使用默认值"
        CRON_TIME="0 2 * * *"
        TIME_DESC="每天凌晨 2:00"
        ;;
esac

echo ""
echo "将配置定时任务: $TIME_DESC"
echo ""

# 创建新的cron任务
CRON_JOB="$CRON_TIME $SCRIPT_PATH >> /var/log/clear_logs_cron.log 2>&1"

# 备份现有的crontab
crontab -l > /tmp/current_cron 2>/dev/null || touch /tmp/current_cron

# 检查是否已存在相同的任务
if grep -q "$SCRIPT_PATH" /tmp/current_cron; then
    echo "发现已存在的日志清理任务，是否要替换？"
    read -p "输入 y 替换, n 取消 [y/n]: " replace
    if [ "$replace" != "y" ]; then
        echo "取消操作"
        exit 0
    fi
    # 删除旧任务
    grep -v "$SCRIPT_PATH" /tmp/current_cron > /tmp/new_cron
    mv /tmp/new_cron /tmp/current_cron
fi

# 添加新任务
echo "$CRON_JOB" >> /tmp/current_cron

# 安装新的crontab
crontab /tmp/current_cron

if [ $? -eq 0 ]; then
    echo "✓ 定时任务配置成功！"
    echo ""
    echo "任务详情："
    echo "  执行时间: $TIME_DESC"
    echo "  执行脚本: $SCRIPT_PATH"
    echo "  日志文件: /var/log/clear_logs_cron.log"
    echo ""
    echo "查看当前定时任务："
    crontab -l | grep "$SCRIPT_PATH"
    echo ""
    echo "提示："
    echo "  - 使用 'crontab -l' 查看所有定时任务"
    echo "  - 使用 'crontab -e' 手动编辑定时任务"
    echo "  - 使用 'crontab -r' 删除所有定时任务"
    echo "  - 查看执行日志: tail -f /var/log/clear_logs_script.log"
else
    echo "✗ 定时任务配置失败"
    exit 1
fi

# 清理临时文件
rm -f /tmp/current_cron /tmp/new_cron

exit 0