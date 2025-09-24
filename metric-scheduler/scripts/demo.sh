#!/bin/bash
# 演示脚本

echo "=== 指标调度系统演示 ==="
echo ""

# 检查Python环境
echo "1. 检查Python环境..."
python --version

# 安装依赖
echo ""
echo "2. 安装依赖包..."
pip install -r requirements.txt

# 提示配置
echo ""
echo "3. 请确保已经："
echo "   - 创建MySQL数据库并执行 scripts/create_database.sql"
echo "   - 配置 .env 文件（参考 .env.example）"
echo ""
read -p "确认已完成上述配置？(y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "请先完成配置后再运行演示"
    exit 1
fi

# 查看指标列表
echo ""
echo "4. 查看已配置的指标："
python main.py metric list

# 查看调度列表
echo ""
echo "5. 查看已配置的调度："
python main.py schedule list

# 查看绑定关系
echo ""
echo "6. 绑定指标和调度示例："
echo "python main.py schedule bind --metric DAILY_USER_COUNT --schedule DAILY_0AM"

# 启动调度器
echo ""
echo "7. 启动调度器（按Ctrl+C停止）："
echo "python main.py start"
echo ""
echo "提示：可以在另一个终端窗口运行监控命令查看执行情况："
echo "  - python main.py monitor summary"
echo "  - python main.py monitor running"
echo "  - python main.py monitor performance"