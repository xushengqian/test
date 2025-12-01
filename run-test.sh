#!/bin/bash

# 一键测试脚本

echo "=========================================="
echo "  Java HTTP 文件上传 - 一键测试"
echo "=========================================="
echo ""

# 检查文件是否已编译
if [ ! -f "SimpleUploadServer.class" ] || [ ! -f "QuickTest.class" ]; then
    echo "文件未编译，开始编译..."
    bash build.sh
    echo ""
fi

# 启动测试服务器（后台运行）
echo "正在启动测试服务器..."
java SimpleUploadServer > server.log 2>&1 &
SERVER_PID=$!

# 等待服务器启动
sleep 2

# 检查服务器是否成功启动
if kill -0 $SERVER_PID 2>/dev/null; then
    echo "✅ 测试服务器已启动 (PID: $SERVER_PID)"
    echo ""
    
    # 运行测试
    echo "开始运行测试..."
    echo ""
    echo "" | java QuickTest
    
    # 停止服务器
    echo ""
    echo "正在停止测试服务器..."
    kill $SERVER_PID 2>/dev/null
    sleep 1
    
    echo "✅ 测试完成"
else
    echo "❌ 测试服务器启动失败"
    echo "请查看 server.log 了解详情"
fi

echo ""
echo "=========================================="
