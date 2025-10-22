#!/bin/bash

# Java并行执行示例运行脚本

# 设置类路径
CLASSPATH="target/classes"

# 检查是否已编译
if [ ! -d "target/classes" ]; then
    echo "正在编译项目..."
    mkdir -p target/classes
    javac -d target/classes src/main/java/com/example/parallel/*.java
    echo "编译完成！"
fi

# 显示菜单
echo "======================================"
echo "    Java 并行执行示例程序"
echo "======================================"
echo ""
echo "请选择要运行的示例："
echo "1. 基础并行执行（Thread和线程池）"
echo "2. CompletableFuture异步并行执行"
echo "3. Fork/Join框架并行执行"
echo "4. 嵌套并行执行（主方法内部继续并行）"
echo "5. 交互式主程序"
echo ""

# 读取用户输入
read -p "请输入选项 (1-5): " choice

case $choice in
    1)
        echo "运行基础并行执行示例..."
        java -cp $CLASSPATH com.example.parallel.BasicParallelExecution
        ;;
    2)
        echo "运行CompletableFuture示例..."
        java -cp $CLASSPATH com.example.parallel.CompletableFutureExample
        ;;
    3)
        echo "运行Fork/Join框架示例..."
        java -cp $CLASSPATH com.example.parallel.ForkJoinExample
        ;;
    4)
        echo "运行嵌套并行执行示例..."
        java -cp $CLASSPATH com.example.parallel.NestedParallelExample
        ;;
    5)
        echo "运行交互式主程序..."
        java -cp $CLASSPATH com.example.parallel.MainApplication
        ;;
    *)
        echo "无效选项"
        exit 1
        ;;
esac