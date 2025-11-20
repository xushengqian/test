# FreeSWITCH 呼叫异常诊断分析

## 问题概述

**错误信息**: `-ERR DESTINATION_OUT_OF_ORDER`  
**问题服务器**: 10-181-10-158  
**场景**: 高并发呼叫量时出现，另一台服务器正常  
**呼叫方向**: 呼出（originate）到网关 gwopensips

---

## 日志关键信息

```
Job-Command: originate
Job-UUID: 7f27cf05-08db-4d59-b938-b80bc02af25e
Gateway: gwopensips
被叫号码: 40000260
主叫号码: 88888888
错误: -ERR DESTINATION_OUT_OF_ORDER
```

---

## 错误原因分析

### DESTINATION_OUT_OF_ORDER 错误通常表示：

1. **网关不可达或离线**
   - SIP 网关连接断开
   - 网关注册失败
   - 网络连接问题

2. **网关资源耗尽**（高并发场景最可能）
   - 并发通道数达到上限
   - 网关侧拒绝新呼叫
   - TPS（每秒事务数）超限

3. **FreeSWITCH 资源不足**
   - 文件描述符（ulimit）耗尽
   - 内存不足
   - CPU 负载过高
   - Session 数量达到上限

4. **配置差异**
   - 两台服务器配置不一致
   - 网关配置参数不同
   - SIP Profile 配置差异

---

## 诊断步骤

### 1. 检查网关状态

在 FreeSWITCH 控制台执行：

```bash
# 查看网关状态
fs_cli -x "sofia status gateway gwopensips"

# 查看所有网关
fs_cli -x "sofia status"

# 查看 Profile 状态
fs_cli -x "sofia status profile external"
```

**关键指标**：
- State: 是否为 REGED（已注册）
- Status: 是否为 UP
- Ping: 网关响应时间

### 2. 检查并发通道数

```bash
# 查看当前活跃通道数
fs_cli -x "show channels count"

# 查看详细通道信息
fs_cli -x "show channels"

# 查看每个网关的通道数
fs_cli -x "show channels as xml" | grep gwopensips
```

### 3. 检查系统资源

```bash
# 检查文件描述符限制
ulimit -n

# 检查 FreeSWITCH 进程的文件描述符使用情况
lsof -p $(pgrep freeswitch) | wc -l

# 检查系统资源
top -p $(pgrep freeswitch)
free -m
df -h
```

### 4. 检查 SIP 日志

```bash
# 启用 SIP 跟踪（会产生大量日志，谨慎使用）
fs_cli -x "sofia global siptrace on"

# 查看 SIP 消息
tail -f /var/log/freeswitch/freeswitch.log | grep -i "gwopensips"

# 查看特定时间段的错误
grep "DESTINATION_OUT_OF_ORDER" /var/log/freeswitch/freeswitch.log
```

### 5. 对比两台服务器配置

```bash
# 网关配置文件
/etc/freeswitch/sip_profiles/external/gwopensips.xml

# SIP Profile 配置
/etc/freeswitch/sip_profiles/external.xml

# 核心配置
/etc/freeswitch/autoload_configs/switch.conf.xml
```

**重点对比参数**：
- `max-sessions`: 最大会话数
- `sessions-per-second`: 每秒会话数限制
- Gateway 的 `retry-seconds`, `ping` 参数
- 网络相关的超时配置

---

## 常见解决方案

### 方案 1: 增加并发限制

编辑 `/etc/freeswitch/autoload_configs/switch.conf.xml`:

```xml
<param name="max-sessions" value="2000"/>
<param name="sessions-per-second" value="100"/>
```

### 方案 2: 增加系统文件描述符限制

编辑 `/etc/security/limits.conf`:

```bash
freeswitch soft nofile 65536
freeswitch hard nofile 65536
```

编辑 `/etc/systemd/system/freeswitch.service.d/override.conf`:

```ini
[Service]
LimitNOFILE=65536
```

重启服务：
```bash
systemctl daemon-reload
systemctl restart freeswitch
```

### 方案 3: 优化网关配置

编辑网关配置文件，添加以下参数：

```xml
<gateway name="gwopensips">
  <!-- 其他配置 -->
  <param name="retry-seconds" value="10"/>
  <param name="ping" value="30"/>
  <param name="register" value="true"/>
  <param name="caller-id-in-from" value="true"/>
  <param name="extension-in-contact" value="true"/>
  
  <!-- 增加超时时间 -->
  <param name="register-timeout" value="60"/>
</gateway>
```

### 方案 4: 使用多个网关实现负载均衡

如果单个网关有并发限制，配置多个网关：

```xml
<gateway name="gwopensips1">...</gateway>
<gateway name="gwopensips2">...</gateway>
```

在拨号计划中轮询使用：

```xml
<action application="bridge" data="{ignore_early_media=true}sofia/gateway/gwopensips1/40000260|sofia/gateway/gwopensips2/40000260"/>
```

### 方案 5: 调整 SIP Profile 参数

编辑 `/etc/freeswitch/sip_profiles/external.xml`:

```xml
<!-- 增加并发连接数 -->
<param name="max-calls" value="2000"/>

<!-- 调整 RTP 端口范围 -->
<param name="rtp-start-port" value="16384"/>
<param name="rtp-end-port" value="32768"/>

<!-- 优化 SIP 超时 -->
<param name="sip-session-timeout" value="1800"/>
<param name="sip-invite-timeout" value="60"/>
```

---

## 监控脚本

创建一个监控脚本来实时监控网关状态：

```bash
#!/bin/bash
# freeswitch_monitor.sh

while true; do
    echo "========== $(date) =========="
    
    # 网关状态
    echo "=== Gateway Status ==="
    fs_cli -x "sofia status gateway gwopensips" | grep -E "State|Calls-IN|Calls-OUT"
    
    # 活跃通道数
    echo "=== Active Channels ==="
    fs_cli -x "show channels count"
    
    # 系统负载
    echo "=== System Load ==="
    uptime
    
    # 文件描述符
    echo "=== File Descriptors ==="
    lsof -p $(pgrep freeswitch) 2>/dev/null | wc -l
    
    echo ""
    sleep 10
done
```

---

## 应急处理

如果问题正在发生：

1. **立即重启网关**
```bash
fs_cli -x "sofia profile external killgw gwopensips"
fs_cli -x "sofia profile external rescan"
```

2. **重启 SIP Profile**
```bash
fs_cli -x "sofia profile external restart"
```

3. **如果需要，重启 FreeSWITCH**
```bash
systemctl restart freeswitch
```

---

## 预防措施

1. **设置呼叫速率限制**：在应用层控制呼叫速率，避免瞬间并发过高

2. **实施健康检查**：定期检查网关状态，自动切换故障网关

3. **配置告警**：监控关键指标（通道数、网关状态、系统资源），及时告警

4. **负载均衡**：使用多个网关分散流量

5. **容量规划**：根据业务需求合理配置系统资源和并发限制

---

## 下一步行动

1. ✅ 在问题服务器（10-181-10-158）上执行诊断步骤
2. ✅ 对比两台服务器的配置文件，找出差异
3. ✅ 检查高峰期的资源使用情况（CPU、内存、文件描述符）
4. ✅ 查看 FreeSWITCH 日志中的详细 SIP 消息
5. ✅ 验证网关供应商侧是否有并发限制
6. ✅ 实施上述解决方案并测试

---

## 联系信息

如需进一步协助，请提供：
- 两台服务器的完整配置文件
- 高峰期的系统资源监控数据
- 完整的 FreeSWITCH 日志片段
- 网关供应商的并发规格说明
