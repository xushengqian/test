#!/bin/bash
# FreeSWITCH 转人工功能测试脚本

echo "==================================="
echo "FreeSWITCH 转人工功能测试"
echo "==================================="

# FreeSWITCH连接配置
FS_HOST="127.0.0.1"
FS_PORT="8021"
FS_PASSWORD="ClueCon"

# 测试场景
run_test() {
    local test_name="$1"
    local test_cmd="$2"
    
    echo ""
    echo "测试: $test_name"
    echo "-----------------------------------"
    
    # 执行测试命令
    fs_cli -H $FS_HOST -P $FS_PORT -p $FS_PASSWORD -x "$test_cmd"
    
    sleep 2
}

# 1. 测试DTMF转人工
echo ""
echo "场景1: DTMF按键转人工"
run_test "用户按0转人工" \
    "originate {dtmf_digits=0,caller_id_number=13800138000}loopback/detect_intent/transfer_to_agent &park()"

# 2. 测试关键词识别
echo ""
echo "场景2: 语音关键词识别"
run_test "用户说'转人工'" \
    "originate {asr_result='我要转人工',caller_id_number=13900139000}loopback/detect_intent/transfer_to_agent &park()"

# 3. 测试情绪检测
echo ""
echo "场景3: 负面情绪检测"
run_test "用户情绪激动" \
    "originate {emotion_type=anger,emotion_score=0.8,caller_id_number=13700137000}loopback/detect_intent/transfer_to_agent &park()"

# 4. 测试多次失败
echo ""
echo "场景4: 多次识别失败"
run_test "识别失败3次" \
    "originate {recognition_failures=3,caller_id_number=13600136000}loopback/detect_intent/transfer_to_agent &park()"

# 5. 测试超时转人工
echo ""
echo "场景5: IVR超时"
run_test "IVR时长超过3分钟" \
    "originate {billsec=185,caller_id_number=13500135000}loopback/detect_intent/transfer_to_agent &park()"

# 6. 测试VIP客户
echo ""
echo "场景6: VIP客户优先"
run_test "VIP客户转人工" \
    "originate {customer_type=vip,caller_id_number=18888888888}loopback/transfer_agent/transfer_to_agent &park()"

# 7. 测试坐席登录
echo ""
echo "场景7: 坐席登录"
run_test "坐席001登录" \
    "lua scripts/agent_login.lua agent_001"

# 8. 测试队列状态
echo ""
echo "场景8: 查看队列状态"
run_test "查看所有队列" \
    "fifo list"

# 9. 测试坐席状态
echo ""
echo "场景9: 查看坐席状态"
for i in {1..5}; do
    agent_id=$(printf "agent_%03d" $i)
    echo "坐席 $agent_id 状态:"
    fs_cli -H $FS_HOST -P $FS_PORT -p $FS_PASSWORD -x "global_getvar agent_status_$agent_id"
done

# 10. 测试转接失败处理
echo ""
echo "场景10: 转接失败处理"
run_test "无可用坐席" \
    "originate {no_agents_available=true,caller_id_number=13400134000}loopback/transfer_failed/transfer_to_agent &park()"

echo ""
echo "==================================="
echo "测试完成"
echo "==================================="

# 生成测试报告
echo ""
echo "生成测试报告..."
python3 /workspace/transfer_agent_manager.py