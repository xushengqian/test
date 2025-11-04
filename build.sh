#!/bin/bash

echo "==================================="
echo "FreeSWITCH Originate错误处理框架"
echo "==================================="

# 检查Maven是否安装
if command -v mvn &> /dev/null; then
    echo "✓ 发现Maven，开始构建..."
    mvn clean package
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "✓ 构建成功！"
        echo ""
        echo "运行示例程序："
        echo "  java -cp target/freeswitch-originate-error-handler-1.0.0.jar com.originate.call.client.freeswitch.OriginateErrorExample"
    else
        echo "✗ 构建失败"
        exit 1
    fi
else
    echo "✗ 未找到Maven，请先安装Maven："
    echo "  - Ubuntu/Debian: sudo apt-get install maven"
    echo "  - CentOS/RHEL: sudo yum install maven"
    echo "  - macOS: brew install maven"
    echo ""
    echo "或者访问: https://maven.apache.org/download.cgi"
fi
