#!/bin/bash

# demo.sh
# FreeSwitch 转人工系统功能演示脚本

echo "=========================================="
echo "FreeSwitch 转人工系统功能演示"
echo "=========================================="

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}本演示将展示以下功能：${NC}"
echo "1. 用户意图识别（语音关键词检测）"
echo "2. 智能队列路由"  
echo "3. 转人工流程控制"
echo "4. 回呼请求处理"
echo "5. 日志记录和监控"
echo ""

read -p "按Enter键开始演示..."

echo -e "\n${YELLOW}=== 演示1: 用户意图识别 ===${NC}"
echo "模拟用户说出不同的话语，系统识别转人工意图："
echo ""

# 模拟意图识别测试
test_phrases=(
    "我要找人工客服"
    "这个机器人听不懂我说话"
    "我要投诉你们的服务"
    "账单查询"
    "我很生气，必须找到负责人"
)

echo -e "${GREEN}测试短语及识别结果：${NC}"
for phrase in "${test_phrases[@]}"; do
    echo -n "输入: \"$phrase\" -> "
    
    # 简化的意图识别逻辑演示
    if [[ "$phrase" == *"人工"* ]] || [[ "$phrase" == *"客服"* ]]; then
        echo -e "${GREEN}转人工意图 (置信度: 90%)${NC}"
    elif [[ "$phrase" == *"投诉"* ]] || [[ "$phrase" == *"生气"* ]]; then
        echo -e "${RED}投诉意图 -> 优先队列 (置信度: 85%)${NC}"
    elif [[ "$phrase" == *"听不懂"* ]] || [[ "$phrase" == *"机器人"* ]]; then
        echo -e "${YELLOW}拒绝机器人 -> 转人工 (置信度: 75%)${NC}"
    else
        echo -e "${BLUE}业务查询 -> 自动服务 (置信度: 60%)${NC}"
    fi
    sleep 1
done

read -p "按Enter继续下一个演示..."

echo -e "\n${YELLOW}=== 演示2: 智能队列路由 ===${NC}"
echo "根据用户意图和客户等级，智能选择合适的服务队列："
echo ""

customers=(
    "普通客户:138****1234:一般咨询"
    "VIP客户:139****5678:技术问题"  
    "投诉客户:150****9999:服务投诉"
    "紧急客户:186****0000:系统故障"
)

echo -e "${GREEN}客户路由结果：${NC}"
for customer in "${customers[@]}"; do
    IFS=':' read -r level phone issue <<< "$customer"
    echo -n "$level ($phone) - $issue -> "
    
    case "$level" in
        "VIP客户")
            echo -e "${GREEN}优先级队列 (最高优先级)${NC}"
            ;;
        "投诉客户")
            echo -e "${RED}投诉处理队列 (录音监控)${NC}"
            ;;
        "紧急客户") 
            echo -e "${RED}紧急队列 (立即处理)${NC}"
            ;;
        *)
            echo -e "${BLUE}普通人工队列 (正常排队)${NC}"
            ;;
    esac
    sleep 1
done

read -p "按Enter继续下一个演示..."

echo -e "\n${YELLOW}=== 演示3: 转移流程控制 ===${NC}"
echo "演示完整的呼叫转移流程："
echo ""

echo -e "${GREEN}步骤1:${NC} 接收来电 (138****1234)"
sleep 1
echo -e "${GREEN}步骤2:${NC} 播放欢迎语音"
sleep 1  
echo -e "${GREEN}步骤3:${NC} 启动意图检测..."
sleep 2
echo -e "${GREEN}步骤4:${NC} 检测到转人工意图 (关键词: '人工客服')"
sleep 1
echo -e "${GREEN}步骤5:${NC} 执行智能路由 -> 普通人工队列"
sleep 1
echo -e "${GREEN}步骤6:${NC} 检查队列状态..."
echo "         当前等待: 3人"
echo "         可用客服: 2人"
echo "         预计等待: 2分钟"
sleep 2
echo -e "${GREEN}步骤7:${NC} 播放等待提示，进入队列"
sleep 1
echo -e "${GREEN}步骤8:${NC} 客服接听，转移成功！"
sleep 1

read -p "按Enter继续下一个演示..."

echo -e "\n${YELLOW}=== 演示4: 失败处理和回呼 ===${NC}"
echo "演示转移失败时的处理机制："
echo ""

echo -e "${RED}模拟场景:${NC} 所有客服都忙，转移失败"
sleep 1
echo -e "${YELLOW}系统响应:${NC} 提供替代选择"
echo "1. 继续等待"
echo "2. 申请回呼" 
echo "3. 语音留言"
sleep 2

echo -e "${GREEN}用户选择:${NC} 申请回呼"
sleep 1
echo -e "${BLUE}回呼处理:${NC}"
echo "  - 确认回呼号码: 138****1234"
echo "  - 选择回呼时间: 30分钟内"
echo "  - 生成请求ID: CB_20241011_1523_8888"
echo "  - 测试号码可达性: 成功"
echo "  - 加入回呼队列"
sleep 3

read -p "按Enter继续下一个演示..."

echo -e "\n${YELLOW}=== 演示5: 监控和统计 ===${NC}"
echo "系统实时监控和性能统计："
echo ""

echo -e "${GREEN}实时队列状态:${NC}"
echo "┌─────────────────┬──────┬──────┬────────┐"
echo "│ 队列名称        │ 等待 │ 客服 │ 负载   │"
echo "├─────────────────┼──────┼──────┼────────┤"
echo "│ 普通人工队列    │  5   │  3   │ 正常   │"
echo "│ 优先级队列      │  2   │  2   │ 正常   │" 
echo "│ 技术支持队列    │  8   │  2   │ 高     │"
echo "│ 投诉处理队列    │  1   │  2   │ 低     │"
echo "└─────────────────┴──────┴──────┴────────┘"
sleep 2

echo -e "\n${GREEN}性能指标 (今日):${NC}"
echo "• 总来电数: 1,247"
echo "• 转人工率: 23.5%"
echo "• 转移成功率: 95.2%"
echo "• 平均等待时间: 1分32秒"
echo "• 客户满意度: 4.3/5.0"
sleep 2

echo -e "\n${GREEN}意图识别准确率:${NC}"
echo "• 直接转人工: 96.8%"
echo "• 投诉识别: 89.2%"
echo "• 技术支持: 91.5%"
echo "• 业务咨询: 87.3%"
sleep 2

read -p "按Enter查看告警信息..."

echo -e "\n${RED}系统告警 (如有):${NC}"
echo "⚠ 技术支持队列等待时间超过阈值 (当前: 4分30秒)"
echo "⚠ 客服 agent_003 响应时间较长 (当前: 45秒)"
sleep 2

echo -e "\n${YELLOW}=== 演示完成 ===${NC}"
echo ""
echo -e "${BLUE}系统特点总结:${NC}"
echo "✓ 多模态意图识别 (语音+按键)"
echo "✓ 智能路由和负载均衡"
echo "✓ 完善的失败处理机制"  
echo "✓ 实时监控和告警"
echo "✓ 结构化日志记录"
echo "✓ 可扩展的配置管理"
echo ""

echo -e "${GREEN}部署提示:${NC}"
echo "1. 运行 ./system_check.sh 检查系统配置"
echo "2. 录制必要的语音提示文件"
echo "3. 配置客服分机和用户信息"
echo "4. 测试各项功能和流程"
echo "5. 监控系统运行状态"
echo ""

echo -e "${BLUE}如需技术支持，请查看 README.md 文档${NC}"
echo "=========================================="