# FreeSWITCH Originate 失败处理方案

## 项目说明

本项目提供了一个完整的 FreeSWITCH originate 命令失败处理解决方案，用于处理外呼场景中各种失败情况。

## 功能特性

✅ **全面的错误处理**
- 支持 8 种常见错误类型的识别和处理
- 自动解析 FreeSWITCH 事件和错误信息
- 提取呼叫关键信息（号码、网关、UUID等）

✅ **灵活的业务扩展**
- 提供清晰的业务逻辑扩展点
- 支持自定义重试策略
- 易于集成到现有系统

✅ **完善的测试覆盖**
- 包含单元测试用例
- 真实场景测试
- 异常情况处理测试

## 文件说明

- `FreeSwitchOriginateFailureHandler.java` - 核心失败处理器
- `FreeSwitchOriginateFailureHandlerTest.java` - 单元测试
- `ORIGINATE_FAILURE_HANDLING.md` - 详细使用文档

## 快速开始

### 1. 添加到您的项目

将 `FreeSwitchOriginateFailureHandler.java` 复制到您的项目中：

```
src/main/java/com/openaibot/callcenter/core/freeswitch/
```

### 2. 在服务中使用

```java
@Service
public class FreeSwitchClientService {
    @Autowired
    private FreeSwitchOriginateFailureHandler failureHandler;
    
    public void backgroundJobResultReceived(String jobUuid, EslEvent event) {
        failureHandler.handleBackgroundJobResult(jobUuid, event);
    }
}
```

### 3. 实现业务逻辑

在处理器的 TODO 部分实现您的具体业务逻辑。

## 支持的错误类型

| 错误码 | 描述 | 处理建议 |
|--------|------|---------|
| DESTINATION_OUT_OF_ORDER | 目标不可达 | 标记无效，不重试 |
| USER_BUSY | 用户忙 | 延时重试 |
| NO_USER_RESPONSE | 无应答 | 延时重试 |
| NO_ROUTE_DESTINATION | 无路由 | 检查网关 |
| NORMAL_TEMPORARY_FAILURE | 临时故障 | 立即重试 |
| CALL_REJECTED | 呼叫被拒 | 记录原因 |
| RECOVERY_ON_TIMER_EXPIRE | 超时 | 调整配置 |
| ORIGINATOR_CANCEL | 取消 | 记录日志 |

## 详细文档

请查看 [ORIGINATE_FAILURE_HANDLING.md](./ORIGINATE_FAILURE_HANDLING.md) 获取：
- 详细的错误分析
- 完整的使用示例
- 最佳实践建议
- 故障排查指南

## 技术栈

- Java 11+
- Spring Boot
- Lombok
- JUnit 5
- FreeSWITCH ESL

## 示例场景

### 真实失败日志

```
-ERR DESTINATION_OUT_OF_ORDER
被叫号码: 13716763135
网关: gwopensips
Job UUID: 26f93d9e-6eb5-43b7-8f59-7cee547e6eba
```

### 处理流程

1. 接收 BACKGROUND_JOB 事件
2. 解析错误信息和呼叫参数
3. 根据错误类型调用对应处理方法
4. 执行业务逻辑（更新数据库、重试、告警等）
5. 记录详细日志

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可证

MIT License