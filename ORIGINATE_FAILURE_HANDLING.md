# FreeSWITCH Originate 失败处理说明

## 概述

本文档说明如何使用 `FreeSwitchOriginateFailureHandler` 处理 FreeSWITCH originate 命令失败的场景。

## 错误分析

### 您遇到的错误

```
-ERR DESTINATION_OUT_OF_ORDER
```

**错误含义**：目标号码不可达或已停机

**可能原因**：
1. 被叫号码已停机或注销
2. 被叫号码不存在
3. 运营商返回的错误状态
4. 网络或路由问题

### 呼叫详情

- **被叫号码**: 13716763135
- **主叫号码**: gw113120002026
- **网关**: gwopensips
- **Job UUID**: 26f93d9e-6eb5-43b7-8f59-7cee547e6eba
- **Session UUID**: yrRobot_202511141035370016696

## 使用方法

### 1. 在 FreeSwitchClientService 中集成

```java
@Service
@Slf4j
public class FreeSwitchClientService {
    
    @Autowired
    private FreeSwitchOriginateFailureHandler failureHandler;
    
    public void backgroundJobResultReceived(String jobUuid, EslEvent event) {
        log.info("backgroundJobResultReceived :{},event:{}", jobUuid, event);
        
        // 使用失败处理器处理结果
        failureHandler.handleBackgroundJobResult(jobUuid, event);
    }
}
```

### 2. 实现业务逻辑

在 `FreeSwitchOriginateFailureHandler` 中的 TODO 部分实现您的业务逻辑：

#### 目标不可达处理

```java
private void handleDestinationOutOfOrder(String jobUuid, Map<String, String> callInfo) {
    log.warn("目标号码不可达 - jobUuid: {}, 被叫号码: {}, 网关: {}", 
             jobUuid, callInfo.get("callee"), callInfo.get("gateway"));
    
    // 实现您的业务逻辑：
    // 1. 标记该号码为无效号码
    String callee = callInfo.get("callee");
    invalidNumberService.markAsInvalid(callee, "DESTINATION_OUT_OF_ORDER");
    
    // 2. 更新呼叫记录状态
    String uuid = callInfo.get("uuid");
    callRecordService.updateStatus(uuid, CallStatus.FAILED, "目标不可达");
    
    // 3. 不进行重试（号码无效）
    retryService.cancelRetry(uuid);
    
    // 4. 发送通知
    notificationService.sendAlert("呼叫失败", 
        String.format("号码 %s 不可达", callee));
}
```

#### 用户忙处理

```java
private void handleUserBusy(String jobUuid, Map<String, String> callInfo) {
    log.info("用户忙 - jobUuid: {}, 被叫号码: {}", jobUuid, callInfo.get("callee"));
    
    // 1. 安排稍后重拨（例如 5 分钟后）
    String uuid = callInfo.get("uuid");
    retryService.scheduleRetry(uuid, 5, TimeUnit.MINUTES);
    
    // 2. 更新呼叫记录状态为"忙"
    callRecordService.updateStatus(uuid, CallStatus.BUSY, "用户忙");
}
```

#### 无应答处理

```java
private void handleNoUserResponse(String jobUuid, Map<String, String> callInfo) {
    log.info("用户无响应 - jobUuid: {}, 被叫号码: {}", jobUuid, callInfo.get("callee"));
    
    // 1. 安排重试（例如 10 分钟后）
    String uuid = callInfo.get("uuid");
    retryService.scheduleRetry(uuid, 10, TimeUnit.MINUTES);
    
    // 2. 更新呼叫记录状态为"无应答"
    callRecordService.updateStatus(uuid, CallStatus.NO_ANSWER, "用户无响应");
}
```

## 支持的错误类型

| 错误码 | 中文描述 | 建议处理方式 |
|--------|---------|-------------|
| DESTINATION_OUT_OF_ORDER | 目标号码不可达或已停机 | 标记为无效号码，不再重试 |
| NO_ROUTE_DESTINATION | 无路由到目标 | 检查网关配置，尝试备用网关 |
| CALL_REJECTED | 呼叫被拒绝 | 记录拒绝原因，可安排重试 |
| NO_USER_RESPONSE | 用户无响应 | 安排延时重试 |
| USER_BUSY | 用户忙 | 安排短时间后重试 |
| NORMAL_TEMPORARY_FAILURE | 临时故障 | 立即或短时间后重试 |
| RECOVERY_ON_TIMER_EXPIRE | 超时恢复 | 检查超时配置，安排重试 |
| ORIGINATOR_CANCEL | 发起方取消 | 记录取消原因，不重试 |

## 最佳实践

### 1. 重试策略

```java
// 根据错误类型制定不同的重试策略
public class RetryStrategy {
    public static int getMaxRetries(String errorCode) {
        return switch (errorCode) {
            case "DESTINATION_OUT_OF_ORDER" -> 0;  // 不重试
            case "USER_BUSY" -> 3;                  // 最多3次
            case "NO_USER_RESPONSE" -> 2;           // 最多2次
            case "NORMAL_TEMPORARY_FAILURE" -> 5;   // 最多5次
            default -> 1;
        };
    }
    
    public static Duration getRetryDelay(String errorCode, int attemptNumber) {
        return switch (errorCode) {
            case "USER_BUSY" -> Duration.ofMinutes(5);
            case "NO_USER_RESPONSE" -> Duration.ofMinutes(10);
            case "NORMAL_TEMPORARY_FAILURE" -> Duration.ofMinutes(1);
            default -> Duration.ofMinutes(5);
        };
    }
}
```

### 2. 监控和告警

```java
// 统计失败率
@Component
public class OriginateMetrics {
    private final Counter failureCounter;
    private final Counter successCounter;
    
    public void recordFailure(String errorCode) {
        failureCounter.labels(errorCode).inc();
        
        // 如果失败率过高，发送告警
        double failureRate = getFailureRate();
        if (failureRate > 0.5) {  // 失败率超过 50%
            alertService.sendCriticalAlert("Originate 失败率过高: " + failureRate);
        }
    }
}
```

### 3. 日志记录

```java
// 记录详细的失败日志，便于后续分析
log.error("Originate 失败详情 - " +
          "jobUuid: {}, " +
          "sessionUuid: {}, " +
          "被叫号码: {}, " +
          "主叫号码: {}, " +
          "网关: {}, " +
          "错误码: {}, " +
          "错误描述: {}, " +
          "时间: {}",
          jobUuid, sessionUuid, callee, caller, gateway, 
          errorCode, errorDesc, LocalDateTime.now());
```

## 故障排查

### 1. DESTINATION_OUT_OF_ORDER

**排查步骤**：
1. 确认被叫号码是否有效
2. 使用其他渠道（如手机）拨打该号码验证
3. 检查号码是否在黑名单中
4. 查看运营商是否有限制

### 2. NO_ROUTE_DESTINATION

**排查步骤**：
1. 检查 FreeSWITCH 网关配置
2. 验证网关是否在线：`sofia status gateway gwopensips`
3. 检查路由表配置
4. 查看防火墙规则

### 3. 频繁失败

**排查步骤**：
1. 查看 FreeSWITCH 日志：`fs_cli -x "console loglevel debug"`
2. 检查网络连接
3. 验证 SIP 账号和密码
4. 查看运营商账户余额和状态

## 配置建议

### application.yml 配置示例

```yaml
freeswitch:
  originate:
    # 超时配置（秒）
    timeout: 60
    # 默认重试次数
    max-retries: 3
    # 重试间隔（分钟）
    retry-interval: 5
    # 失败告警阈值（百分比）
    failure-alert-threshold: 50
    # 无效号码自动标记
    auto-mark-invalid: true
```

## 相关资源

- [FreeSWITCH 官方文档](https://freeswitch.org/confluence/)
- [Originate 命令文档](https://freeswitch.org/confluence/display/FREESWITCH/mod_commands#mod_commands-originate)
- [挂断原因代码](https://freeswitch.org/confluence/display/FREESWITCH/Hangup+Cause+Code+Table)
