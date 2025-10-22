#!/bin/bash

# Java并行执行示例编译脚本

echo "=== 编译Java并行执行示例 ==="

# 创建输出目录
mkdir -p out

# 编译Java文件
echo "正在编译Java文件..."
javac -d out src/main/java/com/parallel/*.java

if [ $? -eq 0 ]; then
    echo "✅ 编译成功！"
    echo "输出目录: out/"
    echo ""
    echo "可以运行以下命令执行示例："
    echo "1. 多个主方法并行执行:"
    echo "   java -cp out com.parallel.ParallelMainExecutor"
    echo ""
    echo "2. 主方法内部逻辑并行执行:"
    echo "   java -cp out com.parallel.InternalParallelMain"
else
    echo "❌ 编译失败！"
    exit 1
fi