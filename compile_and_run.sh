#!/bin/bash

# 创建必要的目录
mkdir -p src/main/java/com/example/aviator
mkdir -p lib

# 下载AviatorScript JAR文件
echo "正在下载AviatorScript JAR文件..."
wget -q https://repo1.maven.org/maven2/com/googlecode/aviator/aviator/5.3.3/aviator-5.3.3.jar -O lib/aviator-5.3.3.jar

if [ $? -eq 0 ]; then
    echo "AviatorScript JAR下载成功"
else
    echo "AviatorScript JAR下载失败，尝试备用源..."
    wget -q https://search.maven.org/remotecontent?filepath=com/googlecode/aviator/aviator/5.3.3/aviator-5.3.3.jar -O lib/aviator-5.3.3.jar
fi

# 编译Java文件
echo "正在编译Java文件..."
javac -cp "lib/*" -d . src/main/java/com/example/aviator/*.java

if [ $? -eq 0 ]; then
    echo "编译成功！"
    echo "运行示例程序..."
    java -cp ".:lib/*" com.example.aviator.UsageExample
else
    echo "编译失败"
    exit 1
fi