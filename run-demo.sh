#!/bin/bash

echo "========================================="
echo "   FieldStrategy 字段策略演示程序"
echo "========================================="
echo ""

# 检查是否安装了Maven
if ! command -v mvn &> /dev/null
then
    echo "错误: Maven未安装，请先安装Maven"
    echo "Ubuntu/Debian: sudo apt-get install maven"
    echo "MacOS: brew install maven"
    exit 1
fi

echo "1. 清理并编译项目..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "编译失败，请检查代码"
    exit 1
fi

echo "2. 运行演示程序..."
echo ""
mvn exec:java -Dexec.mainClass="com.example.fieldstrategy.demo.FieldStrategyDemo" -Dexec.cleanupDaemonThreads=false -q

echo ""
echo "========================================="
echo "演示完成！"
echo ""
echo "其他命令："
echo "  运行测试: mvn test"
echo "  打包项目: mvn package"
echo "========================================="