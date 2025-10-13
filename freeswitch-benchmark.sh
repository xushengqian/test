#!/bin/bash

# FreeSWITCH 性能基准测试脚本
# 使用 SIPp 进行压力测试

# 配置参数
FREESWITCH_IP="127.0.0.1"
FREESWITCH_PORT="5060"
TEST_DURATION="60"  # 测试持续时间（秒）
MAX_CALLS="1000"    # 最大并发呼叫数
CALL_RATE="10"      # 每秒呼叫速率

echo "FreeSWITCH 性能基准测试"
echo "========================"
echo "目标服务器: $FREESWITCH_IP:$FREESWITCH_PORT"
echo "测试持续时间: $TEST_DURATION 秒"
echo "最大并发呼叫: $MAX_CALLS"
echo "呼叫速率: $CALL_RATE cps"
echo ""

# 检查 SIPp 是否安装
if ! command -v sipp &> /dev/null; then
    echo "错误: SIPp 未安装"
    echo "请使用以下命令安装:"
    echo "Ubuntu/Debian: apt-get install sip-tester"
    echo "CentOS/RHEL: yum install sip-tester"
    exit 1
fi

# 检查 FreeSWITCH 是否运行
if ! pgrep freeswitch > /dev/null; then
    echo "警告: FreeSWITCH 进程未检测到"
    echo "请确保 FreeSWITCH 正在运行"
fi

# 创建 SIP 场景文件
cat > /tmp/uac_benchmark.xml << 'EOF'
<?xml version="1.0" encoding="ISO-8859-1" ?>
<!DOCTYPE scenario SYSTEM "sipp.dtd">

<scenario name="UAC Benchmark">
  <send retrans="500">
    <![CDATA[
      INVITE sip:[service]@[remote_ip]:[remote_port] SIP/2.0
      Via: SIP/2.0/[transport] [local_ip]:[local_port];branch=[branch]
      From: sipp <sip:sipp@[local_ip]:[local_port]>;tag=[pid]SIPpTag00[call_number]
      To: sut <sip:[service]@[remote_ip]:[remote_port]>
      Call-ID: [call_id]
      CSeq: 1 INVITE
      Contact: sip:sipp@[local_ip]:[local_port]
      Max-Forwards: 70
      Subject: Performance Test
      Content-Type: application/sdp
      Content-Length: [len]

      v=0
      o=user1 53655765 2353687637 IN IP[local_ip_type] [local_ip]
      s=-
      c=IN IP[local_ip_type] [local_ip]
      t=0 0
      a=sendrecv
      m=audio [auto_media_port] RTP/AVP 0
      a=rtpmap:0 PCMU/8000
    ]]>
  </send>

  <recv response="100" optional="true">
  </recv>

  <recv response="180" optional="true">
  </recv>

  <recv response="183" optional="true">
  </recv>

  <recv response="200" rtd="true">
  </recv>

  <send>
    <![CDATA[
      ACK sip:[service]@[remote_ip]:[remote_port] SIP/2.0
      Via: SIP/2.0/[transport] [local_ip]:[local_port];branch=[branch]
      From: sipp <sip:sipp@[local_ip]:[local_port]>;tag=[pid]SIPpTag00[call_number]
      To: sut <sip:[service]@[remote_ip]:[remote_port]>[peer_tag_param]
      Call-ID: [call_id]
      CSeq: 1 ACK
      Contact: sip:sipp@[local_ip]:[local_port]
      Max-Forwards: 70
      Subject: Performance Test
      Content-Length: 0
    ]]>
  </send>

  <!-- 保持呼叫一段时间 -->
  <pause milliseconds="5000"/>

  <send retrans="500">
    <![CDATA[
      BYE sip:[service]@[remote_ip]:[remote_port] SIP/2.0
      Via: SIP/2.0/[transport] [local_ip]:[local_port];branch=[branch]
      From: sipp <sip:sipp@[local_ip]:[local_port]>;tag=[pid]SIPpTag00[call_number]
      To: sut <sip:[service]@[remote_ip]:[remote_port]>[peer_tag_param]
      Call-ID: [call_id]
      CSeq: 2 BYE
      Contact: sip:sipp@[local_ip]:[local_port]
      Max-Forwards: 70
      Subject: Performance Test
      Content-Length: 0
    ]]>
  </send>

  <recv response="200" crlf="true">
  </recv>

  <ResponseTimeRepartition value="10, 20, 30, 40, 50, 100, 150, 200"/>
  <CallLengthRepartition value="10, 50, 100, 500, 1000, 5000, 10000"/>

</scenario>
EOF

echo "开始性能测试..."
echo ""

# 获取测试开始时的系统资源使用情况
echo "测试开始前的系统状态:"
echo "CPU 使用率: $(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | sed 's/%us,//')"
echo "内存使用: $(free | awk 'FNR==2{printf "%.2f%%", $3/($3+$4)*100}')"
echo "FreeSWITCH 状态:"
fs_cli -x "status" 2>/dev/null || echo "无法连接到 FreeSWITCH CLI"
echo ""

# 运行性能测试
echo "正在执行 SIPp 压力测试..."
sipp -sf /tmp/uac_benchmark.xml \
     -s 1000 \
     $FREESWITCH_IP:$FREESWITCH_PORT \
     -l $MAX_CALLS \
     -r $CALL_RATE \
     -d $TEST_DURATION \
     -rtp_echo \
     -trace_stat \
     -stf /tmp/sipp_stats.csv \
     -trace_screen \
     > /tmp/sipp_output.log 2>&1

# 分析测试结果
echo ""
echo "测试完成！正在分析结果..."
echo ""

if [ -f /tmp/sipp_stats.csv ]; then
    echo "SIPp 统计信息:"
    echo "=============="
    
    # 解析统计文件
    TOTAL_CALLS=$(tail -n 1 /tmp/sipp_stats.csv | cut -d';' -f2)
    SUCCESSFUL_CALLS=$(tail -n 1 /tmp/sipp_stats.csv | cut -d';' -f3)
    FAILED_CALLS=$(tail -n 1 /tmp/sipp_stats.csv | cut -d';' -f4)
    
    echo "总呼叫数: $TOTAL_CALLS"
    echo "成功呼叫数: $SUCCESSFUL_CALLS"
    echo "失败呼叫数: $FAILED_CALLS"
    
    if [ "$TOTAL_CALLS" -gt 0 ]; then
        SUCCESS_RATE=$(echo "scale=2; $SUCCESSFUL_CALLS * 100 / $TOTAL_CALLS" | bc -l)
        echo "成功率: $SUCCESS_RATE%"
    fi
fi

# 显示测试后的系统状态
echo ""
echo "测试结束后的系统状态:"
echo "CPU 使用率: $(top -bn1 | grep "Cpu(s)" | awk '{print $2}' | sed 's/%us,//')"
echo "内存使用: $(free | awk 'FNR==2{printf "%.2f%%", $3/($3+$4)*100}')"

echo ""
echo "FreeSWITCH 最终状态:"
fs_cli -x "status" 2>/dev/null || echo "无法连接到 FreeSWITCH CLI"

echo ""
echo "详细日志文件:"
echo "- SIPp 输出: /tmp/sipp_output.log"
echo "- SIPp 统计: /tmp/sipp_stats.csv"

# 清理临时文件
rm -f /tmp/uac_benchmark.xml

echo ""
echo "性能测试完成！"