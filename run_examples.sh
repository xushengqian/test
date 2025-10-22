#!/bin/bash

echo "======================================"
echo "Java 双线程池并行执行示例"
echo "======================================"
echo ""

# 检查Java是否安装
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java，请先安装Java"
    exit 1
fi

echo "Java版本:"
java -version
echo ""

# 编译Java文件
echo "正在编译Java文件..."
javac DualThreadPoolExample.java
if [ $? -eq 0 ]; then
    echo "✓ DualThreadPoolExample.java 编译成功"
else
    echo "✗ DualThreadPoolExample.java 编译失败"
    exit 1
fi

javac AdvancedThreadPoolExample.java
if [ $? -eq 0 ]; then
    echo "✓ AdvancedThreadPoolExample.java 编译成功"
else
    echo "✗ AdvancedThreadPoolExample.java 编译失败"
    exit 1
fi

echo ""
echo "======================================"
echo "运行基础示例"
echo "======================================"
echo ""
java DualThreadPoolExample

echo ""
echo "======================================"
echo "运行高级示例"
echo "======================================"
echo ""
java AdvancedThreadPoolExample

echo ""
echo "======================================"
echo "示例运行完成！"
echo "======================================"