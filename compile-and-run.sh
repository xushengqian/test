#!/bin/bash

echo "=== Java AviatorScript 优化示例 (无Maven版本) ==="
echo

# 检查Java环境
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java环境，请先安装Java 8+"
    exit 1
fi

if ! command -v javac &> /dev/null; then
    echo "错误: 未找到javac编译器，请先安装JDK"
    exit 1
fi

# 创建必要的目录
mkdir -p target/classes
mkdir -p lib

echo "1. 下载AviatorScript依赖..."
# 这里需要手动下载aviator-5.3.3.jar到lib目录
# 或者使用wget/curl下载
if [ ! -f "lib/aviator-5.3.3.jar" ]; then
    echo "请手动下载 aviator-5.3.3.jar 到 lib/ 目录"
    echo "下载地址: https://mvnrepository.com/artifact/com.googlecode.aviator/aviator/5.3.3"
    echo "或者运行: wget https://repo1.maven.org/maven2/com/googlecode/aviator/aviator/5.3.3/aviator-5.3.3.jar -O lib/aviator-5.3.3.jar"
    exit 1
fi

echo "2. 编译Java源码..."
javac -cp "lib/*" -d target/classes src/main/java/com/example/aviator/*.java

if [ $? -ne 0 ]; then
    echo "编译失败，请检查代码"
    exit 1
fi

echo "编译成功！"
echo

echo "3. 运行基础优化示例..."
java -cp "target/classes:lib/*" com.example.aviator.AviatorOptimizationExample

echo
echo "=== 示例运行完成 ==="