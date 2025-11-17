#!/bin/bash

echo "========================================="
echo "并行指标调度系统 - 快速安装脚本"
echo "========================================="

# 检查Python版本
if ! command -v python3 &> /dev/null; then
    echo "❌ 错误: 未找到Python3，请先安装Python 3.8+"
    exit 1
fi

PYTHON_VERSION=$(python3 --version | cut -d' ' -f2 | cut -d'.' -f1,2)
echo "✓ 检测到Python版本: $PYTHON_VERSION"

# 检查MySQL
if ! command -v mysql &> /dev/null; then
    echo "⚠ 警告: 未找到MySQL客户端"
    echo "  请确保MySQL已安装并可访问"
fi

# 安装依赖
echo ""
echo "1. 安装Python依赖..."
pip3 install -r requirements.txt
if [ $? -eq 0 ]; then
    echo "✓ 依赖安装成功"
else
    echo "❌ 依赖安装失败"
    exit 1
fi

# 初始化数据库
echo ""
echo "2. 初始化数据库..."
read -p "是否立即初始化数据库? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    read -p "MySQL主机 [localhost]: " MYSQL_HOST
    MYSQL_HOST=${MYSQL_HOST:-localhost}
    
    read -p "MySQL端口 [3306]: " MYSQL_PORT
    MYSQL_PORT=${MYSQL_PORT:-3306}
    
    read -p "MySQL用户 [root]: " MYSQL_USER
    MYSQL_USER=${MYSQL_USER:-root}
    
    read -sp "MySQL密码: " MYSQL_PASSWORD
    echo
    
    mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASSWORD < scripts/create_database.sql
    
    if [ $? -eq 0 ]; then
        echo "✓ 数据库初始化成功"
        
        read -p "是否插入测试数据? (y/n): " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            mysql -h $MYSQL_HOST -P $MYSQL_PORT -u $MYSQL_USER -p$MYSQL_PASSWORD < scripts/insert_test_data.sql
            echo "✓ 测试数据插入成功"
        fi
    else
        echo "❌ 数据库初始化失败"
        exit 1
    fi
fi

# 创建配置文件
echo ""
echo "3. 创建配置文件..."
if [ ! -f .env ]; then
    cp .env.example .env
    echo "✓ 已创建 .env 配置文件"
    echo "⚠ 请编辑 .env 文件，配置数据库连接信息"
else
    echo "⚠ .env 文件已存在，跳过创建"
fi

# 创建日志目录
echo ""
echo "4. 创建日志目录..."
mkdir -p logs
echo "✓ 日志目录创建成功"

echo ""
echo "========================================="
echo "✓ 安装完成！"
echo "========================================="
echo ""
echo "下一步："
echo "1. 编辑 .env 文件，配置数据库和Redis连接"
echo "2. 运行 'python main.py' 启动调度器"
echo ""
echo "查看文档："
echo "- README.md - 完整使用文档"
echo "- PERFORMANCE.md - 性能测试报告"
echo ""
