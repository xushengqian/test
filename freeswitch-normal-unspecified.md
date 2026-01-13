# FreeSWITCH NORMAL_UNSPECIFIED 挂断原因码详解

## 1. 概述

`NORMAL_UNSPECIFIED` 是 FreeSWITCH 中的一个挂断原因码（Hangup Cause），对应 ITU-T Q.850 标准中的原因值 **31**。

| 属性 | 值 |
|------|-----|
| 挂断原因名称 | NORMAL_UNSPECIFIED |
| Q.850 代码 | 31 |
| SIP 响应码 | 通常对应 480、487 或无特定映射 |
| 类别 | 正常挂断类 |

## 2. 含义说明

`NORMAL_UNSPECIFIED` 表示呼叫正常结束，但挂断的具体原因没有被明确指定。这是一个"兜底"类型的挂断原因，当系统无法确定更具体的挂断原因时会使用此值。

### 2.1 Q.850 标准定义

根据 ITU-T Q.850 建议书，原因值 31 的官方定义为：

> "Normal, unspecified: This cause is used to report a normal event only when no other cause in the normal class applies."

翻译：此原因用于报告正常事件，仅当正常类别中没有其他原因适用时使用。

## 3. 常见触发场景

### 3.1 正常呼叫结束

```
场景：用户A呼叫用户B，通话完成后任一方挂断
原因：对端发送 BYE 消息但未携带具体挂断原因
结果：FreeSWITCH 记录 NORMAL_UNSPECIFIED
```

### 3.2 网关/运营商返回

```
场景：通过 SIP 网关呼出到 PSTN 网络
原因：运营商网关返回通用的挂断响应
结果：无法映射到具体原因，使用 NORMAL_UNSPECIFIED
```

### 3.3 被叫拒接

```
场景：呼叫被对方拒绝
原因：对端返回 480/487 等 SIP 响应码
结果：可能被映射为 NORMAL_UNSPECIFIED
```

### 3.4 超时或无响应

```
场景：呼叫超时未接听
原因：没有收到明确的失败原因
结果：默认使用 NORMAL_UNSPECIFIED
```

## 4. 与其他挂断原因的区别

| 挂断原因 | Q.850 代码 | 说明 |
|----------|-----------|------|
| NORMAL_CLEARING | 16 | 正常呼叫清除（最常见的正常挂断） |
| NORMAL_UNSPECIFIED | 31 | 正常挂断但原因未指定 |
| USER_BUSY | 17 | 用户忙 |
| NO_ANSWER | 18 | 无应答 |
| CALL_REJECTED | 21 | 呼叫被拒绝 |
| NO_USER_RESPONSE | 19 | 用户无响应 |
| ORIGINATOR_CANCEL | 487 | 主叫取消 |

## 5. 代码处理示例

### 5.1 Lua 脚本示例

```lua
-- 获取挂断原因
local hangup_cause = session:getVariable("hangup_cause")

-- 处理 NORMAL_UNSPECIFIED
if hangup_cause == "NORMAL_UNSPECIFIED" then
    freeswitch.consoleLog("INFO", "呼叫正常结束，原因未指定\n")
    
    -- 记录详细信息以便排查
    local sip_term_status = session:getVariable("sip_term_status")
    local originate_disposition = session:getVariable("originate_disposition")
    
    freeswitch.consoleLog("INFO", 
        string.format("SIP状态: %s, 呼叫结果: %s\n", 
            sip_term_status or "N/A",
            originate_disposition or "N/A"))
end
```

### 5.2 Java ESL 处理示例

```java
import org.freeswitch.esl.client.inbound.Client;
import org.freeswitch.esl.client.transport.event.EslEvent;

public class HangupCauseHandler {
    
    // Q.850 原因码常量
    public static final int NORMAL_UNSPECIFIED = 31;
    public static final String HANGUP_CAUSE_NORMAL_UNSPECIFIED = "NORMAL_UNSPECIFIED";
    
    /**
     * 处理挂断事件
     */
    public void handleHangupEvent(EslEvent event) {
        String hangupCause = event.getEventHeaders().get("Hangup-Cause");
        String channelUuid = event.getEventHeaders().get("Unique-ID");
        
        if (HANGUP_CAUSE_NORMAL_UNSPECIFIED.equals(hangupCause)) {
            // NORMAL_UNSPECIFIED 通常表示正常结束
            handleNormalUnspecified(event, channelUuid);
        }
    }
    
    /**
     * 处理 NORMAL_UNSPECIFIED 挂断
     */
    private void handleNormalUnspecified(EslEvent event, String channelUuid) {
        // 获取更多上下文信息
        String sipTermStatus = event.getEventHeaders().get("variable_sip_term_status");
        String originateDisposition = event.getEventHeaders().get("variable_originate_disposition");
        String billSec = event.getEventHeaders().get("variable_billsec");
        
        // 判断是否是真正的正常通话结束
        int billSeconds = 0;
        try {
            billSeconds = Integer.parseInt(billSec);
        } catch (NumberFormatException e) {
            // 忽略解析错误
        }
        
        if (billSeconds > 0) {
            // 有通话时长，说明是正常通话后挂断
            log.info("Channel {} 通话正常结束，时长: {}秒", channelUuid, billSeconds);
        } else {
            // 无通话时长，可能需要进一步分析
            log.warn("Channel {} 挂断原因: NORMAL_UNSPECIFIED, SIP状态: {}, 呼叫结果: {}",
                    channelUuid, sipTermStatus, originateDisposition);
        }
    }
    
    /**
     * 判断挂断原因是否为正常结束类
     */
    public boolean isNormalHangup(String hangupCause) {
        return "NORMAL_CLEARING".equals(hangupCause) 
            || "NORMAL_UNSPECIFIED".equals(hangupCause)
            || "ORIGINATOR_CANCEL".equals(hangupCause);
    }
}
```

### 5.3 拨号计划示例

```xml
<!-- dialplan 中处理 NORMAL_UNSPECIFIED -->
<extension name="handle_hangup">
  <condition field="variable_hangup_cause" expression="^NORMAL_UNSPECIFIED$">
    <action application="log" data="INFO Normal unspecified hangup for ${uuid}"/>
    <action application="set" data="call_result=normal"/>
  </condition>
</extension>
```

## 6. 排查方法

### 6.1 查看 CDR 记录

```bash
# 查看 CDR 中的挂断原因
grep "hangup_cause.*NORMAL_UNSPECIFIED" /var/log/freeswitch/cdr-csv/*.csv
```

### 6.2 启用 SIP 日志

```xml
<!-- 在 sofia 配置中启用详细日志 -->
<param name="sip-trace" value="true"/>
<param name="log-level" value="7"/>
```

### 6.3 实时监控

```bash
# 在 fs_cli 中监控挂断事件
/event plain CHANNEL_HANGUP

# 或使用过滤器
/filter Hangup-Cause NORMAL_UNSPECIFIED
```

### 6.4 检查相关变量

在呼叫过程中或挂断后，检查以下变量获取更多信息：

| 变量名 | 说明 |
|--------|------|
| `hangup_cause` | 挂断原因 |
| `hangup_cause_q850` | Q.850 数字代码 |
| `sip_term_status` | SIP 终止状态码 |
| `sip_hangup_disposition` | SIP 挂断处置 |
| `originate_disposition` | 呼叫发起结果 |
| `proto_specific_hangup_cause` | 协议特定挂断原因 |
| `last_bridge_hangup_cause` | 最后桥接挂断原因 |

## 7. 常见问题

### Q1: NORMAL_UNSPECIFIED 是否表示呼叫失败？

**答**：不一定。NORMAL_UNSPECIFIED 属于"正常类"挂断原因，大多数情况下表示呼叫正常结束。需要结合 `billsec`（计费秒数）等指标判断呼叫是否成功。

### Q2: 为什么会出现 NORMAL_UNSPECIFIED 而不是 NORMAL_CLEARING？

**答**：这通常取决于对端设备或网关的行为。当对端没有明确指定挂断原因时，FreeSWITCH 会使用 NORMAL_UNSPECIFIED 作为默认值。

### Q3: 如何区分正常结束和异常结束的 NORMAL_UNSPECIFIED？

**答**：检查以下指标：
- `billsec > 0`：表示有实际通话，通常是正常结束
- `sip_term_status`：查看 SIP 状态码
- `originate_disposition`：查看呼叫发起结果

### Q4: 如何减少 NORMAL_UNSPECIFIED 的出现？

**答**：
1. 检查并更新 SIP 网关配置
2. 确保对端设备正确发送挂断原因
3. 在拨号计划中主动设置 `hangup_cause`

## 8. 相关 FreeSWITCH 命令

```bash
# 查看所有挂断原因列表
fs_cli -x "show hangup_causes"

# 查看特定通道的挂断原因
fs_cli -x "uuid_getvar <uuid> hangup_cause"

# 手动设置挂断原因
fs_cli -x "uuid_setvar <uuid> hangup_cause NORMAL_CLEARING"

# 主动挂断并指定原因
fs_cli -x "uuid_kill <uuid> NORMAL_CLEARING"
```

## 9. 参考资料

- [ITU-T Q.850 - Usage of cause and location in the Digital Subscriber Signalling System No. 1 and the Signalling System No. 7 ISDN User Part](https://www.itu.int/rec/T-REC-Q.850)
- [FreeSWITCH 官方文档 - Hangup Causes](https://freeswitch.org/confluence/display/FREESWITCH/Hangup+Causes)
- [SIP 响应码与 Q.850 映射](https://freeswitch.org/confluence/display/FREESWITCH/SIP+Protocol+Messages)

## 10. 总结

`NORMAL_UNSPECIFIED` 是一个常见且通常无害的挂断原因。在大多数场景下，它表示呼叫正常结束但对端未提供具体原因。处理此挂断原因时：

1. **不要将其视为错误** - 它属于正常类挂断
2. **结合其他指标判断** - 使用 `billsec`、`sip_term_status` 等辅助判断
3. **记录详细日志** - 便于后续排查和统计分析
4. **合理分类处理** - 在代码中与 `NORMAL_CLEARING` 等一起归类为正常结束

---
*文档版本：1.0*  
*最后更新：2026-01-13*
