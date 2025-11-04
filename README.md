# FreeSWITCH Originate错误处理框架

本项目提供了一个完整的FreeSWITCH originate命令错误处理框架，用于解析、分类和处理各种呼叫失败场景。

## 功能特性

- ✅ 支持多种FreeSWITCH错误类型识别
- ✅ 自动判断错误是否可重试
- ✅ 错误统计和监控
- ✅ 详细的错误信息提取（被叫号码、网关等）
- ✅ 灵活的错误处理策略
- ✅ 完整的日志记录

## 核心类说明

### 1. OriginateErrorType（错误类型枚举）

定义了所有支持的FreeSWITCH错误类型：

- `DESTINATION_OUT_OF_ORDER` - 目标不可达（可重试）
- `USER_BUSY` - 用户忙（可重试）
- `NO_ANSWER` - 无应答（可重试）
- `CALL_REJECTED` - 呼叫被拒绝
- `UNALLOCATED_NUMBER` - 号码不存在
- `NORMAL_CLEARING` - 正常挂断
- `ORIGINATOR_CANCEL` - 发起方取消
- `GATEWAY_DOWN` - 网关不可用（可重试）
- `NORMAL_TEMPORARY_FAILURE` - 临时失败（可重试）
- `UNKNOWN` - 未知错误

### 2. OriginateErrorHandler（错误处理器）

核心错误处理类，提供：
- 错误类型解析
- 呼叫参数提取（号码、网关等）
- 错误统计
- 特殊错误类型的处理建议

### 3. OriginateErrorResult（处理结果）

封装错误处理结果，包含：
- 任务UUID和呼叫UUID
- 错误类型和描述
- 被叫号码和网关信息
- 是否可重试标志
- 建议的处理操作
- 详细信息字典

### 4. BackgroundJobEventProcessor（事件处理器）

用于集成到FreeSWITCH客户端，处理BACKGROUND_JOB事件：
- 自动识别originate命令结果
- 区分成功和失败场景
- 缓存处理结果
- 提供错误统计

### 5. FreeSwitchEventParser（事件解析器）

工具类，用于从日志或事件数据中解析：
- 事件头信息
- 事件体内容
- Job UUID和Call UUID
- 被叫号码、网关、主叫号码

## 使用示例

### 基础使用

```java
// 创建事件处理器
BackgroundJobEventProcessor processor = new BackgroundJobEventProcessor();

// 处理后台任务结果（从FreeSWITCH客户端回调中获取）
String jobUuid = "1550d668-8d83-4593-985e-492ca034c02c";
Map<String, String> eventHeaders = ...; // 从ESL事件中获取
String eventBody = "-ERR DESTINATION_OUT_OF_ORDER";

processor.processBackgroundJobResult(jobUuid, eventHeaders, eventBody);

// 获取处理结果
OriginateErrorResult result = processor.getJobResult(jobUuid);
if (result != null && result.isRetryable()) {
    // 执行重试逻辑
    retryOriginate(result);
}
```

### 集成到FreeSwitchClientService

```java
public class FreeSwitchClientService {
    
    private final BackgroundJobEventProcessor eventProcessor = 
        new BackgroundJobEventProcessor();
    
    // ESL事件回调
    public void backgroundJobResultReceived(String jobUuid, EslEvent event) {
        Map<String, String> eventHeaders = event.getEventHeaders();
        String eventBody = event.getEventBodyAsString();
        
        // 使用处理器处理
        eventProcessor.processBackgroundJobResult(jobUuid, eventHeaders, eventBody);
        
        // 获取结果并执行相应操作
        OriginateErrorResult result = eventProcessor.getJobResult(jobUuid);
        if (result != null) {
            handleOriginateResult(result);
        }
    }
    
    private void handleOriginateResult(OriginateErrorResult result) {
        if (result.isRetryable()) {
            // 可重试的错误
            scheduleRetry(result);
        } else {
            // 不可重试的错误，记录并告警
            logAndAlert(result);
        }
    }
}
```

### 自定义错误处理

```java
OriginateErrorHandler handler = new OriginateErrorHandler();

// 配置重试参数
handler.setMaxRetryAttempts(5);
handler.setRetryDelayMillis(3000);

// 创建自定义处理器
BackgroundJobEventProcessor processor = 
    new BackgroundJobEventProcessor(handler);
```

### 查看错误统计

```java
// 获取错误统计
Map<String, Integer> statistics = processor.getErrorStatistics();
for (Map.Entry<String, Integer> entry : statistics.entrySet()) {
    log.info("错误类型: {}, 发生次数: {}", entry.getKey(), entry.getValue());
}

// 清空统计
processor.getErrorHandler().clearErrorStatistics();
```

## 处理的真实日志示例

```
[2025-10-23 16:51:45.010] [EslBackgroundJobNotifier-1] INFO m: c.o.c.c.f.FreeSwitchClientService 
- backgroundJobResultReceived :1550d668-8d83-4593-985e-492ca034c02c,
event:{"log":{"name":"org.freeswitch.esl.client.transport.event.EslEvent"},
"messageHeaders":{"CONTENT_TYPE":"text/event-plain","CONTENT_LENGTH":"907"},
"eventHeaders":{"Event-Name":"BACKGROUND_JOB","Job-Command":"originate",
"Job-UUID":"1550d668-8d83-4593-985e-492ca034c02c",...},
"eventBody":["-ERR DESTINATION_OUT_OF_ORDER"]}
```

处理后输出：
```
错误类型: DESTINATION_OUT_OF_ORDER
错误描述: 目标不可达
被叫号码: 13390118999
使用网关: gwopensips
是否可重试: true
建议操作: 建议重试，可能是临时性网络问题
检查项: 1.确认被叫号码是否正常 2.检查网络连接 3.尝试其他网关
```

## 错误类型判断逻辑

系统会根据错误类型自动判断是否建议重试：

**可重试错误：**
- DESTINATION_OUT_OF_ORDER（网络临时故障）
- USER_BUSY（用户忙线）
- NO_ANSWER（无人接听）
- GATEWAY_DOWN（网关临时故障）
- NORMAL_TEMPORARY_FAILURE（临时失败）

**不建议重试：**
- UNALLOCATED_NUMBER（号码不存在）
- CALL_REJECTED（呼叫被拒绝）
- NORMAL_CLEARING（正常挂断）
- ORIGINATOR_CANCEL（主动取消）

## 依赖要求

```xml
<dependencies>
    <!-- SLF4J日志 -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>1.7.36</version>
    </dependency>
    
    <!-- FreeSWITCH ESL客户端（如果需要） -->
    <dependency>
        <groupId>org.freeswitch.esl.client</groupId>
        <artifactId>org.freeswitch.esl.client</artifactId>
        <version>0.9.2</version>
    </dependency>
</dependencies>
```

## 项目结构

```
src/main/java/com/originate/call/client/freeswitch/
├── OriginateErrorType.java              # 错误类型枚举
├── OriginateErrorHandler.java           # 错误处理器
├── OriginateErrorResult.java            # 处理结果封装
├── BackgroundJobEventProcessor.java     # 事件处理器
├── FreeSwitchEventParser.java           # 事件解析工具
└── OriginateErrorExample.java           # 使用示例
```

## 扩展建议

1. **重试策略**：可以基于`OriginateErrorResult.isRetryable()`实现自动重试
2. **告警集成**：对于不可重试的错误，集成告警系统
3. **指标监控**：基于错误统计实现Prometheus/Grafana监控
4. **负载均衡**：根据网关错误率实现智能路由选择

## License

MIT

## 作者

Generated by Cursor AI Assistant
