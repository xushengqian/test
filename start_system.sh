#!/bin/bash
# FreeSWITCH 机器人外呼系统启动脚本

echo "======================================"
echo "  FreeSWITCH 机器人外呼系统启动器"
echo "======================================"
echo ""

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 检查Python
echo -e "${YELLOW}[1/4]${NC} 检查Python环境..."
if ! command -v python3 &> /dev/null; then
    echo -e "${RED}错误: Python3未安装${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Python3已安装${NC}"

# 检查依赖
echo -e "${YELLOW}[2/4]${NC} 检查Python依赖..."
if ! python3 -c "import ESL" 2>/dev/null; then
    echo -e "${YELLOW}正在安装依赖...${NC}"
    pip3 install -r requirements.txt
fi
echo -e "${GREEN}✓ 依赖已安装${NC}"

# 检查FreeSWITCH
echo -e "${YELLOW}[3/4]${NC} 检查FreeSWITCH..."
if ! command -v fs_cli &> /dev/null; then
    echo -e "${RED}警告: FreeSWITCH未安装或不在PATH中${NC}"
    echo "请确保FreeSWITCH正在运行"
else
    # 测试FreeSWITCH连接
    if fs_cli -x "status" &> /dev/null; then
        echo -e "${GREEN}✓ FreeSWITCH正在运行${NC}"
    else
        echo -e "${RED}警告: 无法连接到FreeSWITCH${NC}"
        echo "请检查FreeSWITCH是否正在运行"
    fi
fi

# 启动服务
echo -e "${YELLOW}[4/4]${NC} 启动系统组件..."
echo ""

# 启动ESL控制器（后台运行）
echo "正在启动ESL控制器和WebSocket服务器..."
cd scripts
python3 esl_controller.py &
ESL_PID=$!
cd ..

# 等待ESL控制器启动
sleep 2

# 检查ESL控制器是否运行
if ps -p $ESL_PID > /dev/null; then
    echo -e "${GREEN}✓ ESL控制器已启动 (PID: $ESL_PID)${NC}"
else
    echo -e "${RED}✗ ESL控制器启动失败${NC}"
    exit 1
fi

# 启动Web服务器
echo "正在启动Web控制面板..."
python3 start_server.py &
WEB_PID=$!

# 等待Web服务器启动
sleep 1

# 显示状态
echo ""
echo "======================================"
echo -e "${GREEN}系统启动成功！${NC}"
echo "======================================"
echo ""
echo "组件状态:"
echo "  • ESL控制器 (PID: $ESL_PID)"
echo "  • Web服务器 (PID: $WEB_PID)"
echo ""
echo "访问地址:"
echo "  • Web控制面板: http://localhost:8080"
echo "  • WebSocket: ws://localhost:8765"
echo ""
echo "停止系统: 按 Ctrl+C"
echo "======================================"
echo ""

# 创建停止函数
cleanup() {
    echo ""
    echo "正在停止系统..."
    kill $ESL_PID 2>/dev/null
    kill $WEB_PID 2>/dev/null
    echo "系统已停止"
    exit 0
}

# 捕获退出信号
trap cleanup INT TERM

# 等待进程
wait $ESL_PID $WEB_PID