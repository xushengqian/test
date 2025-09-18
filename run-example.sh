#!/bin/bash

echo "=== Java AviatorScript 优化示例 ==="
echo

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java环境，请先安装Java 8+"
    exit 1
fi

# 检查Maven环境
if ! command -v mvn &> /dev/null; then
    echo "错误: 未找到Maven环境，请先安装Maven 3.6+"
    exit 1
fi

echo "1. 编译项目..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "编译失败，请检查代码"
    exit 1
fi

echo "编译成功！"
echo

echo "2. 运行基础优化示例..."
mvn exec:java -Dexec.mainClass="com.example.aviator.AviatorOptimizationExample" -q

echo
echo "3. 运行性能基准测试..."
echo "注意: 基准测试可能需要几分钟时间"
mvn exec:java -Dexec.mainClass="com.example.aviator.AviatorOptimizationBenchmark" -q

echo
echo "=== 示例运行完成 ==="