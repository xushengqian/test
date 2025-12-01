#!/bin/bash

# Java HTTP 文件上传示例 - 构建脚本

echo "=========================================="
echo "  编译 Java 文件上传示例项目"
echo "=========================================="

# 检查 Java 版本
java_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "Java 版本: $java_version"

# 编译所有 Java 文件
echo ""
echo "开始编译..."
echo ""

# 编译核心示例文件
files=(
    "HttpFormFileUploadExample.java"
    "HttpClientFileUploadExample.java"
    "FileUploadUtils.java"
    "SimpleUploadServer.java"
    "QuickTest.java"
)

success_count=0
fail_count=0

for file in "${files[@]}"; do
    echo -n "编译 $file ... "
    if javac "$file" 2>/dev/null; then
        echo "✅ 成功"
        ((success_count++))
    else
        echo "❌ 失败"
        ((fail_count++))
    fi
done

echo ""
echo "=========================================="
echo "  编译完成"
echo "=========================================="
echo "成功: $success_count 个文件"
echo "失败: $fail_count 个文件"
echo ""

if [ $fail_count -eq 0 ]; then
    echo "✅ 所有文件编译成功！"
    echo ""
    echo "快速使用:"
    echo "  1. 启动测试服务器: java SimpleUploadServer"
    echo "  2. 运行快速测试: java QuickTest"
    echo ""
else
    echo "⚠️  部分文件编译失败"
    echo "注意: HttpClientFileUploadExample 需要 Java 11+"
    echo ""
fi

echo "=========================================="
