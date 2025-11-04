# 使用指南

## 快速开始

### 1. 构建项目

```bash
# 使用Maven构建
mvn clean package

# 或使用提供的脚本
./build.sh
```

### 2. 运行示例

```bash
# 直接运行示例程序
mvn exec:java -Dexec.mainClass="com.originate.call.client.freeswitch.OriginateErrorExample"

# 或者运行编译后的JAR
java -cp target/freeswitch-originate-error-handler-1.0.0.jar \
  com.originate.call.client.freeswitch.OriginateErrorExample
```

## 核心用法

### 场景1：在FreeSwitchClientService中集成

```java
import com.originate.call.client.freeswitch.*;

public class FreeSwitchClientService {
    
    // 创建处理器（单例）
    private final BackgroundJobEventProcessor eventProcessor = 
        new BackgroundJobEventProcessor();
    
    /**
     * ESL客户端回调方法
     */
    public void backgroundJobResultReceived(String jobUuid, EslEvent event) {
        // 提取事件信息
        Map<String, String> eventHeaders = event.getEventHeaders();
        String eventBody = getEventBody(event); // 获取事件体
        
        // 处理事件
        eventProcessor.processBackgroundJobResult(jobUuid, eventHeaders, eventBody);
        
        // 获取结果并处理
        OriginateErrorResult result = eventProcessor.getJobResult(jobUuid);
        if (result != null) {
            handleResult(result);
        }
    }
    
    private void handleResult(OriginateErrorResult result) {
        if (result.getErrorType() == null) {
            // 成功场景
            log.info("呼叫成功 - UUID: {}", result.getCallUuid());
            return;
        }
        
        // 失败场景
        log.error("呼叫失败 - 错误: {} ({}), 号码: {}, 网关: {}",
                result.getErrorType().getDescription(),
                result.getErrorType().getErrorCode(),
                result.getCalledNumber(),
                result.getGateway());
        
        // 根据是否可重试决定操作
        if (result.isRetryable()) {
            scheduleRetry(result);
        } else {
            notifyFailure(result);
        }
    }
}
```

### 场景2：解析日志中的错误信息

如果您从日志文件中读取事件数据：

```java
import com.originate.call.client.freeswitch.*;

public class LogParser {
    
    public void parseLogLine(String logLine) {
        // 假设日志是JSON格式
        JSONObject eventJson = new JSONObject(logLine);
        
        // 解析事件头
        Map<String, String> eventHeaders = 
            FreeSwitchEventParser.parseEventHeaders(eventJson.toMap());
        
        // 解析事件体
        String eventBody = FreeSwitchEventParser.parseEventBody(eventJson.toMap());
        
        // 提取Job UUID
        String jobUuid = FreeSwitchEventParser.extractJobUuid(eventHeaders);
        
        // 创建错误处理器
        OriginateErrorHandler handler = new OriginateErrorHandler();
        
        // 处理错误
        if (eventBody.startsWith("-ERR")) {
            OriginateErrorResult result = handler.handleOriginateError(
                    jobUuid, eventBody, eventHeaders);
            
            System.out.println("错误分析结果：");
            System.out.println("  错误类型：" + result.getErrorType().getDescription());
            System.out.println("  被叫号码：" + result.getCalledNumber());
            System.out.println("  可否重试：" + result.isRetryable());
            System.out.println("  建议操作：" + result.getSuggestedAction());
        }
    }
}
```

### 场景3：自定义错误类型和处理

```java
// 扩展错误类型枚举
public enum CustomOriginateErrorType {
    // 可以添加自定义错误类型
    CUSTOM_ERROR("CUSTOM_ERROR", "自定义错误", false);
    
    // ... 实现与OriginateErrorType相同的接口
}

// 自定义错误处理器
public class CustomErrorHandler extends OriginateErrorHandler {
    
    @Override
    public OriginateErrorResult handleOriginateError(
            String jobUuid, 
            String errorBody,
            Map<String, String> eventHeaders) {
        
        // 先调用父类处理
        OriginateErrorResult result = super.handleOriginateError(
                jobUuid, errorBody, eventHeaders);
        
        // 添加自定义逻辑
        if (result.getErrorType() == OriginateErrorType.DESTINATION_OUT_OF_ORDER) {
            // 例如：检查是否需要切换备用网关
            checkAndSwitchGateway(result);
        }
        
        return result;
    }
}
```

## 实际场景处理示例

### 示例1：处理您提供的日志

```java
// 原始日志数据
String logLine = "[2025-10-23 16:51:45.010] [EslBackgroundJobNotifier-1] INFO m: " +
                 "c.o.c.c.f.FreeSwitchClientService - backgroundJobResultReceived :" +
                 "1550d668-8d83-4593-985e-492ca034c02c,event:{...}";

// 提取关键信息
String jobUuid = "1550d668-8d83-4593-985e-492ca034c02c";
String errorBody = "-ERR DESTINATION_OUT_OF_ORDER";

Map<String, String> eventHeaders = new HashMap<>();
eventHeaders.put("Job-Command", "originate");
eventHeaders.put("Job-UUID", jobUuid);
eventHeaders.put("Job-Command-Arg", 
    "{session_type=3,object_type=1,ignore_early_media=false," +
    "origination_caller_id_number='gw113120002026'," +
    "uuid='yrRobot_202510231651440005437'," +
    "media_bug_answer_req=false," +
    "execute_on_media=lua::pre_media_event.lua}" +
    "sofia/gateway/gwopensips/13390118999 &lua(ivrbot-nopause.lua)");

// 使用处理器
BackgroundJobEventProcessor processor = new BackgroundJobEventProcessor();
processor.processBackgroundJobResult(jobUuid, eventHeaders, errorBody);

// 获取结果
OriginateErrorResult result = processor.getJobResult(jobUuid);

// 输出分析
System.out.println("错误分析结果：");
System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
System.out.println("任务UUID：" + result.getJobUuid());
System.out.println("呼叫UUID：" + result.getCallUuid());
System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
System.out.println("错误类型：" + result.getErrorType().getErrorCode());
System.out.println("错误描述：" + result.getErrorType().getDescription());
System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
System.out.println("被叫号码：" + result.getCalledNumber());
System.out.println("使用网关：" + result.getGateway());
System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
System.out.println("可否重试：" + (result.isRetryable() ? "是" : "否"));
System.out.println("建议操作：" + result.getSuggestedAction());
System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
result.getDetails().forEach((k, v) -> 
    System.out.println(k + "：" + v));
```

**输出结果：**
```
错误分析结果：
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
任务UUID：1550d668-8d83-4593-985e-492ca034c02c
呼叫UUID：yrRobot_202510231651440005437
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
错误类型：DESTINATION_OUT_OF_ORDER
错误描述：目标不可达
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
被叫号码：13390118999
使用网关：gwopensips
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
可否重试：是
建议操作：建议重试，可能是临时性网络问题
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
检查项：1.确认被叫号码是否正常 2.检查网络连接 3.尝试其他网关
```

### 示例2：实现重试逻辑

```java
public class OriginateRetryManager {
    
    private final BackgroundJobEventProcessor processor;
    private final ScheduledExecutorService scheduler;
    
    public OriginateRetryManager() {
        this.processor = new BackgroundJobEventProcessor();
        this.scheduler = Executors.newScheduledThreadPool(5);
    }
    
    public void handleOriginateResult(String jobUuid, 
                                     Map<String, String> eventHeaders,
                                     String eventBody) {
        // 处理结果
        processor.processBackgroundJobResult(jobUuid, eventHeaders, eventBody);
        
        // 获取处理结果
        OriginateErrorResult result = processor.getJobResult(jobUuid);
        
        if (result != null && result.isRetryable()) {
            // 计算重试次数（从缓存或数据库获取）
            int retryCount = getRetryCount(result.getCallUuid());
            int maxRetries = processor.getErrorHandler().getMaxRetryAttempts();
            
            if (retryCount < maxRetries) {
                // 安排重试
                long delay = calculateRetryDelay(retryCount);
                scheduleRetry(result, delay, retryCount + 1);
            } else {
                // 达到最大重试次数
                log.error("呼叫失败，已达最大重试次数：{}", result.getCallUuid());
                notifyMaxRetriesReached(result);
            }
        }
    }
    
    private void scheduleRetry(OriginateErrorResult result, 
                              long delayMillis, 
                              int retryAttempt) {
        log.info("安排第{}次重试，延迟{}ms - 号码：{}, 网关：{}", 
                retryAttempt, delayMillis, 
                result.getCalledNumber(), result.getGateway());
        
        scheduler.schedule(() -> {
            try {
                // 执行重试
                retryOriginate(result, retryAttempt);
            } catch (Exception e) {
                log.error("重试失败", e);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
    }
    
    private long calculateRetryDelay(int retryCount) {
        // 指数退避策略
        long baseDelay = processor.getErrorHandler().getRetryDelayMillis();
        return (long) (baseDelay * Math.pow(2, retryCount));
    }
    
    private void retryOriginate(OriginateErrorResult result, int retryAttempt) {
        // 实现实际的重试逻辑
        log.info("执行第{}次重试 - 号码：{}", retryAttempt, result.getCalledNumber());
        
        // 调用FreeSWITCH发起呼叫
        // freeswitchClient.originate(...);
    }
}
```

## 错误统计和监控

```java
public class ErrorMonitor {
    
    private final BackgroundJobEventProcessor processor;
    
    // 定期输出统计信息
    @Scheduled(fixedRate = 60000) // 每分钟
    public void printStatistics() {
        Map<String, Integer> stats = processor.getErrorStatistics();
        
        System.out.println("\n【错误统计】（过去一分钟）");
        System.out.println("═══════════════════════════════════");
        
        int total = stats.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("总错误数：" + total);
        System.out.println("───────────────────────────────────");
        
        stats.forEach((errorCode, count) -> {
            OriginateErrorType type = OriginateErrorType.fromErrorCode(errorCode);
            double percentage = (count * 100.0) / total;
            System.out.printf("%-30s: %4d (%.1f%%)%n", 
                    type.getDescription(), count, percentage);
        });
        
        System.out.println("═══════════════════════════════════\n");
        
        // 清空统计，准备下一个周期
        processor.getErrorHandler().clearErrorStatistics();
    }
}
```

## 最佳实践

1. **单例使用**：在应用中保持`BackgroundJobEventProcessor`为单例，避免重复创建
2. **及时清理**：处理完结果后，及时调用`clearJobResult()`清理缓存
3. **错误告警**：对于不可重试的错误，应及时告警人工介入
4. **统计分析**：定期分析错误统计，优化网关和路由策略
5. **日志记录**：保留详细的错误日志，便于问题追踪

## 常见问题

### Q: 如何添加新的错误类型？
A: 在`OriginateErrorType`枚举中添加新的常量即可。

### Q: 如何自定义重试策略？
A: 创建`OriginateErrorHandler`的子类，重写相关方法。

### Q: 是否支持异步处理？
A: 是的，`BackgroundJobEventProcessor`内部使用了`ConcurrentHashMap`，支持并发访问。

### Q: 如何与Spring集成？
A: 将`BackgroundJobEventProcessor`声明为Spring Bean即可：

```java
@Configuration
public class FreeSwitchConfig {
    
    @Bean
    public BackgroundJobEventProcessor eventProcessor() {
        return new BackgroundJobEventProcessor();
    }
}
```

## 技术支持

如有问题，请查看：
- [README.md](README.md) - 项目概述
- [示例代码](src/main/java/com/originate/call/client/freeswitch/OriginateErrorExample.java)

---

Generated by Cursor AI Assistant
