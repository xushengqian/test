# FreeSWITCH Originate 错误处理

本项目实现了对 FreeSWITCH `originate` 命令错误的处理，特别是 `DESTINATION_OUT_OF_ORDER` 错误。

## 功能说明

### FreeSwitchClientService

主要服务类，负责处理 FreeSWITCH 的 BACKGROUND_JOB 事件结果。

#### 主要方法

- `backgroundJobResultReceived(String jobUuid, EslEvent event)`: 处理后台作业结果
  - 自动检测错误响应（以 `-ERR` 开头）
  - 根据错误类型进行相应的处理

#### 错误处理

- **DESTINATION_OUT_OF_ORDER**: 目标号码不可用或线路故障
  - 记录详细的错误信息
  - 提取作业命令参数以获取更多上下文
  - 可扩展实现：数据库记录、告警通知、重试机制等

### FreeSwitchErrorType

错误类型枚举，用于分类和处理不同类型的 FreeSWITCH 错误。

#### 支持的错误类型

- `DESTINATION_OUT_OF_ORDER`: 目标号码不可用
- `USER_NOT_FOUND`: 用户不存在
- `INVALID_COMMAND`: 无效的命令
- `GATEWAY_ERROR`: 网关错误
- `UNKNOWN_ERROR`: 未知错误

## 使用示例

```java
FreeSwitchClientService service = new FreeSwitchClientService();
service.backgroundJobResultReceived(jobUuid, eslEvent);
```

## 错误日志示例

```
[2025-10-23 16:51:45.010] [EslBackgroundJobNotifier-1] INFO m: c.o.c.c.f.FreeSwitchClientService - backgroundJobResultReceived :1550d668-8d83-4593-985e-492ca034c02c,event:{...}
```

错误信息：`-ERR DESTINATION_OUT_OF_ORDER`

## 扩展建议

在 `handleDestinationOutOfOrderError` 方法中可以扩展以下功能：

1. 记录错误到数据库
2. 发送告警通知（邮件、短信、系统通知）
3. 实现自动重试机制
4. 更新呼叫状态
5. 统计错误率并触发监控告警