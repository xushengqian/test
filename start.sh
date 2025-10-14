#!/bin/bash

# FreeSWITCH 机器人呼出系统启动脚本

# 设置颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查函数
check_service() {
    service_name=$1
    if systemctl is-active --quiet $service_name; then
        echo -e "${GREEN}✓${NC} $service_name 正在运行"
        return 0
    else
        echo -e "${RED}✗${NC} $service_name 未运行"
        return 1
    fi
}

echo "======================================"
echo "FreeSWITCH 机器人呼出系统"
echo "======================================"
echo ""

# 检查依赖服务
echo "检查依赖服务..."
echo "---------------------"

# 检查 FreeSWITCH
if command -v fs_cli &> /dev/null; then
    if fs_cli -x "status" &> /dev/null; then
        echo -e "${GREEN}✓${NC} FreeSWITCH 正在运行"
    else
        echo -e "${YELLOW}!${NC} FreeSWITCH 未运行，尝试启动..."
        sudo systemctl start freeswitch
        sleep 3
    fi
else
    echo -e "${RED}✗${NC} FreeSWITCH 未安装"
    exit 1
fi

# 检查 MySQL
if check_service mysql || check_service mysqld; then
    echo -e "${GREEN}✓${NC} MySQL 数据库正常"
else
    echo -e "${YELLOW}!${NC} MySQL 未运行，尝试启动..."
    sudo systemctl start mysql || sudo systemctl start mysqld
    sleep 2
fi

# 检查 Redis
if check_service redis || check_service redis-server; then
    echo -e "${GREEN}✓${NC} Redis 正常"
else
    echo -e "${YELLOW}!${NC} Redis 未运行，尝试启动..."
    sudo systemctl start redis || sudo systemctl start redis-server
    sleep 2
fi

echo ""
echo "创建虚拟环境..."
echo "---------------------"

# 检查或创建 Python 虚拟环境
if [ ! -d "venv" ]; then
    echo "创建虚拟环境..."
    python3 -m venv venv
    
    # 激活虚拟环境
    source venv/bin/activate
    
    # 安装依赖
    echo "安装依赖包..."
    pip install --upgrade pip
    pip install -r requirements.txt
else
    echo -e "${GREEN}✓${NC} 虚拟环境已存在"
    source venv/bin/activate
fi

echo ""
echo "初始化数据库..."
echo "---------------------"

# 检查数据库是否存在
if mysql -u root -e "USE freeswitch_bot" 2>/dev/null; then
    echo -e "${GREEN}✓${NC} 数据库已存在"
else
    echo "创建数据库..."
    mysql -u root < scripts/init_db.sql
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓${NC} 数据库创建成功"
    else
        echo -e "${RED}✗${NC} 数据库创建失败，请手动执行: mysql -u root < scripts/init_db.sql"
    fi
fi

echo ""
echo "启动应用服务..."
echo "---------------------"

# 创建日志目录
mkdir -p logs

# 检查是否已经有实例在运行
if pgrep -f "python src/main.py" > /dev/null; then
    echo -e "${YELLOW}!${NC} 应用已在运行"
    echo "如需重启，请先执行: pkill -f 'python src/main.py'"
else
    # 启动主程序
    echo "启动主程序..."
    nohup python src/main.py > logs/startup.log 2>&1 &
    
    # 等待启动
    sleep 3
    
    # 检查是否启动成功
    if pgrep -f "python src/main.py" > /dev/null; then
        echo -e "${GREEN}✓${NC} 应用启动成功"
        
        # 显示访问信息
        echo ""
        echo "======================================"
        echo -e "${GREEN}系统启动成功！${NC}"
        echo "======================================"
        echo ""
        echo "访问地址:"
        echo "  Web API: http://localhost:5000"
        echo "  健康检查: http://localhost:5000/health"
        echo ""
        echo "测试命令:"
        echo "  python src/test_call.py call 13800138000  # 测试呼叫"
        echo "  python src/test_call.py intent            # 测试意图识别"
        echo "  python src/test_call.py status            # 查看系统状态"
        echo ""
        echo "日志文件:"
        echo "  tail -f logs/outbound_bot.log"
        echo ""
        echo "停止服务:"
        echo "  pkill -f 'python src/main.py'"
    else
        echo -e "${RED}✗${NC} 应用启动失败"
        echo "请查看日志: cat logs/startup.log"
        exit 1
    fi
fi