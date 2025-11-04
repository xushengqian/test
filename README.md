# FreeSWITCH Originate 错误处理

本项目用于处理 FreeSWITCH ESL (Event Socket Library) 中的 originate 命令错误，特别是 `DESTINATION_OUT_OF_ORDER` 错误。

## 错误日志示例

```
[2025-10-23 16:51:45.010] [EslBackgroundJobNotifier-1] INFO m: c.o.c.c.f.FreeSwitchClientService - backgroundJobResultReceived :1550d668-8d83-4593-985e-492ca034c02c,event:{"eventBody":["-ERR DESTINATION_OUT_OF_ORDER"]}
```

## 功能说明

### FreeSwitchClientService

主要的服务类，用于处理 FreeSWITCH 后台任务结果：

- `backgroundJobResultReceived()`: 处理后台任务结果，检测错误并分发到相应的错误处理逻辑
- `handleDestinationOutOfOrderError()`: 专门处理 `DESTINATION_OUT_OF_ORDER` 错误
  - 提取目标号码和网关信息
  - 记录详细的错误日志
  - 预留业务逻辑扩展点（数据库记录、告警通知、重试机制等）

### FreeSwitchErrorType

错误类型枚举类：

- `DESTINATION_OUT_OF_ORDER`: 目标不可用错误
- `UNKNOWN_ERROR`: 未知错误类型

提供 `fromErrorMessage()` 方法用于从错误消息字符串中识别错误类型。

## 错误处理流程

1. `backgroundJobResultReceived()` 接收 ESL 事件
2. 检查事件体是否包含 `-ERR` 错误
3. 根据错误消息识别错误类型
4. 调用相应的错误处理方法
5. 记录错误日志并提取关键信息（目标号码、网关等）

## 扩展建议

在 `handleDestinationOutOfOrderError()` 方法中可以添加：

- 数据库记录：将错误信息保存到数据库
- 告警通知：发送告警到监控系统
- 重试机制：对于可重试的错误进行自动重试
- 统计监控：记录错误频率和趋势
- 状态更新：更新呼叫状态为失败

## 测试

运行单元测试：

```bash
mvn test
```

测试覆盖了以下场景：
- DESTINATION_OUT_OF_ORDER 错误处理
- 成功任务处理
- 空事件体处理
- 错误类型识别
