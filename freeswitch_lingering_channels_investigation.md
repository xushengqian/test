# FreeSWITCH 残留通道问题调查

## 问题描述

电话已经挂断，但通过 `show channels` 命令仍然能看到会话存在，这些通道没有被正确释放。

## 常见原因

### 1. 应用程序未正常结束
- **park** 应用：通话被park后没有正确恢复或挂断
- **bridge** 应用：桥接异常，某一端挂断但另一端未正确处理
- **endless loop**：拨号计划中的死循环导致通道一直占用

### 2. 媒体处理问题
- RTP 超时未正确配置
- 媒体协商失败但通道未释放
- 编解码器问题导致通道挂起

### 3. 信令问题
- SIP BYE 消息未收到或未正确处理
- ACK 消息缺失
- 网络问题导致挂断信令丢失

### 4. 模块或脚本问题
- ESL (Event Socket Library) 脚本未释放通道
- Lua/Python/JavaScript 脚本中的错误
- 自定义模块的内存泄漏或逻辑错误

### 5. 资源锁定
- 数据库查询挂起
- 外部API调用超时
- 文件操作阻塞

## 诊断步骤

### 1. 查看详细通道信息

```bash
# 列出所有通道
fs_cli -x "show channels"

# 查看详细通道信息
fs_cli -x "show channels verbose"

# 查看特定UUID的通道详情
fs_cli -x "uuid_dump <uuid>"
```

### 2. 检查通道状态和应用

```bash
# 查看通道当前执行的应用
fs_cli -x "uuid_dump <uuid>" | grep -i application

# 查看呼叫流程
fs_cli -x "uuid_dump <uuid>" | grep -i callflow
```

### 3. 查看通话时长

```bash
# 如果通话时长异常长，说明通道可能卡住了
fs_cli -x "show channels" | grep -E "[0-9]{2}:[0-9]{2}:[0-9]{2}"
```

### 4. 检查日志

```bash
# 查看特定UUID的日志
fs_cli -x "uuid_setvar <uuid> log_uuid true"
tail -f /var/log/freeswitch/freeswitch.log | grep <uuid>

# 提高日志级别
fs_cli -x "fsctl loglevel DEBUG"
```

### 5. 检查拨号计划

```bash
# 查看拨号计划执行情况
fs_cli -x "xml_locate dialplan"

# 检查是否有无限循环
```

## 解决方案

### 即时解决方法

#### 1. 强制挂断单个通道

```bash
# 使用 uuid_kill
fs_cli -x "uuid_kill <uuid>"

# 使用 uuid_kill 带原因
fs_cli -x "uuid_kill <uuid> MANAGER_REQUEST"
```

#### 2. 批量清理残留通道

```bash
# 挂断所有通道（谨慎使用！）
fs_cli -x "hupall"

# 挂断特定原因的通道
fs_cli -x "hupall NORMAL_CLEARING"

# 挂断指定时长以上的通道
fs_cli -x "hupall MANAGER_REQUEST 600"  # 挂断超过600秒的通道
```

### 配置优化

#### 1. 设置 RTP 超时

在 `vars.xml` 或 SIP profile 中配置：

```xml
<!-- RTP 超时设置（秒） -->
<variable name="rtp_timeout_sec" value="300"/>
<variable name="rtp_hold_timeout_sec" value="1800"/>
```

在拨号计划中设置：

```xml
<action application="set" data="rtp_timeout_sec=300"/>
<action application="set" data="rtp_hold_timeout_sec=1800"/>
```

#### 2. 设置会话超时

```xml
<!-- 设置最大通话时长（秒） -->
<action application="set" data="call_timeout=3600"/>
<action application="set" data="max_forwards=70"/>
```

#### 3. SIP 配置优化

在 SIP profile (`conf/sip_profiles/internal.xml`) 中：

```xml
<!-- 启用 session timer -->
<param name="enable-timer" value="true"/>
<param name="session-timeout" value="1800"/>
<param name="minimum-session-expires" value="90"/>

<!-- 设置 dialog 超时 -->
<param name="dialog-timeout" value="43200"/>
```

#### 4. 启用通道超时检测

在 `autoload_configs/switch.conf.xml` 中：

```xml
<param name="max-sessions" value="1000"/>
<param name="sessions-per-second" value="30"/>

<!-- 启用看门狗 -->
<param name="crash-protection" value="true"/>
```

### 预防措施

#### 1. 使用 sched_hangup 设置最大通话时长

在拨号计划中：

```xml
<action application="set" data="hangup_after_bridge=true"/>
<action application="sched_hangup" data="+3600 alloted_timeout"/>
<action application="bridge" data="sofia/gateway/xxx/123456"/>
```

#### 2. 在桥接中使用超时参数

```xml
<action application="bridge" data="{call_timeout=30,hangup_after_bridge=true}sofia/gateway/xxx/123456"/>
```

#### 3. 捕获挂断原因并记录

```xml
<action application="set" data="hangup_after_bridge=true"/>
<action application="set" data="continue_on_fail=true"/>
<action application="bridge" data="sofia/gateway/xxx/123456"/>
<action application="log" data="WARNING Hangup Cause: ${hangup_cause}"/>
```

#### 4. 定期清理脚本

创建一个定期运行的脚本来检测和清理长时间存在的通道：

```bash
#!/bin/bash
# cleanup_lingering_channels.sh

# 获取所有通道
CHANNELS=$(fs_cli -x "show channels as json" | jq -r '.rows[] | select(.duration > 7200) | .uuid')

# 清理超过2小时的通道
for UUID in $CHANNELS; do
    echo "Killing channel: $UUID"
    fs_cli -x "uuid_kill $UUID MANAGER_REQUEST"
done
```

#### 5. 监控和告警

创建监控脚本定期检查通道数量：

```bash
#!/bin/bash
# monitor_channels.sh

THRESHOLD=100
CURRENT=$(fs_cli -x "show channels count" | grep -o '[0-9]\+')

if [ "$CURRENT" -gt "$THRESHOLD" ]; then
    echo "WARNING: High channel count: $CURRENT"
    # 发送告警邮件或通知
fi
```

## 高级诊断

### 使用 Sofia 调试

```bash
# 启用 SIP 跟踪
fs_cli -x "sofia profile internal siptrace on"

# 查看 Sofia 状态
fs_cli -x "sofia status"
fs_cli -x "sofia status profile internal"
```

### 检查内存和资源

```bash
# 查看 FreeSWITCH 进程状态
fs_cli -x "status"

# 检查系统资源
top -p $(pidof freeswitch)
```

### 分析核心转储

如果FreeSWITCH崩溃或通道问题严重：

```bash
# 启用核心转储
ulimit -c unlimited

# 分析核心文件
gdb /usr/bin/freeswitch core.xxxxx
```

## 代码层面的检查

### ESL 脚本示例（Python）

确保脚本正确释放通道：

```python
import ESL

con = ESL.ESLconnection("localhost", "8021", "ClueCon")

if con.connected():
    con.events("plain", "all")
    
    # 确保在异常情况下也能清理
    try:
        # 你的逻辑代码
        con.api("originate", "...")
    except Exception as e:
        print(f"Error: {e}")
    finally:
        # 确保断开连接
        con.disconnect()
```

### Lua 脚本检查

```lua
-- 确保会话被正确处理
session = freeswitch.Session("sofia/gateway/xxx/123456")

if session:ready() then
    -- 设置超时
    session:setHangupHook("my_hangup_hook")
    
    -- 你的逻辑
    session:execute("playback", "/tmp/test.wav")
    
    -- 确保挂断
    session:hangup()
end

-- 回调函数
function my_hangup_hook(s, status)
    freeswitch.consoleLog("notice", "Call ended: " .. status .. "\n")
end
```

## 总结

残留通道问题通常是由以下原因造成：
1. 应用程序未正常结束
2. 信令问题导致挂断失败
3. 超时配置不当
4. 脚本或模块错误

**建议的最佳实践：**
- 始终设置适当的超时参数
- 在拨号计划中使用 `hangup_after_bridge=true`
- 定期监控通道数量
- 完善日志记录以便排查问题
- 建立自动清理机制

## 参考资源

- FreeSWITCH 官方文档：https://freeswitch.org/confluence/
- Channel Variables：https://freeswitch.org/confluence/display/FREESWITCH/Channel+Variables
- Sofia SIP：https://freeswitch.org/confluence/display/FREESWITCH/Sofia+SIP+Stack
