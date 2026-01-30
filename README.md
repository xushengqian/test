# FreeSWITCH ESL Core

FreeSWITCH Event Socket Library (ESL) Java 客户端，用于与 FreeSWITCH 电话系统进行交互。

## 功能特性

- **ESL 连接管理**：支持连接、认证、自动重连
- **事件处理**：异步事件监听和处理
- **呼叫控制**：发起呼叫、转接、桥接、挂断等
- **媒体控制**：播放音频、录音、DTMF 发送等
- **通道变量**：设置和获取通道变量
- **Spring Boot 集成**：自动配置，开箱即用

## 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>com.freeswitch</groupId>
    <artifactId>freeswitch-esl-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 配置

在 `application.yml` 中配置 FreeSWITCH 连接参数：

```yaml
freeswitch:
  esl:
    host: 127.0.0.1
    port: 8021
    password: ClueCon
    connect-timeout: 5000
    read-timeout: 30000
    heartbeat-interval: 25
    auto-reconnect: true
    reconnect-interval: 5000
    subscribe-events:
      - all
```

### 使用示例

#### 1. 注入客户端

```java
@Autowired
private FreeSwitchClient freeSwitchClient;
```

#### 2. 发起呼叫

```java
// 简单呼叫
String uuid = freeSwitchClient.originate(
    "sofia/gateway/mygateway/13800138000",  // 目标
    "10086",                                  // 主叫号码
    "Service",                               // 主叫名称
    "1000",                                  // 分机号
    "XML",                                   // 拨号计划
    "default"                                // 上下文
);

// 带变量的呼叫
Map<String, String> variables = new HashMap<>();
variables.put("call_type", "robot");
variables.put("tenant_id", "1001");

String uuid = freeSwitchClient.originate(
    "sofia/gateway/mygateway/13800138000",
    "10086",
    "Service",
    "1000",
    "XML",
    "default",
    variables,
    60  // 超时秒数
);
```

#### 3. 桥接呼叫

```java
// 桥接两个通道
freeSwitchClient.bridge(uuid1, uuid2);

// 将通道桥接到目标
freeSwitchClient.bridgeTo(uuid, "user/1001");
```

#### 4. 转接呼叫

```java
freeSwitchClient.transfer(uuid, "1002", "XML", "default");
```

#### 5. 挂断呼叫

```java
// 默认原因
freeSwitchClient.hangup(uuid);

// 指定原因
freeSwitchClient.hangup(uuid, "NORMAL_CLEARING");
```

#### 6. 播放音频

```java
// 同步播放
freeSwitchClient.playback(uuid, "/sounds/welcome.wav");

// 异步播放（可打断）
freeSwitchClient.playbackAsync(uuid, "/sounds/music.wav");

// 停止播放
freeSwitchClient.stopPlayback(uuid);
```

#### 7. 录音

```java
// 开始录音
freeSwitchClient.startRecord(uuid, "/recordings/call_001.wav");

// 停止录音
freeSwitchClient.stopRecord(uuid, "/recordings/call_001.wav");
```

#### 8. 发送 DTMF

```java
freeSwitchClient.sendDtmf(uuid, "1234#");
```

#### 9. 通道变量

```java
// 设置变量
freeSwitchClient.setVariable(uuid, "my_var", "value");

// 批量设置变量
Map<String, String> vars = new HashMap<>();
vars.put("var1", "value1");
vars.put("var2", "value2");
freeSwitchClient.setVariables(uuid, vars);

// 获取变量
String value = freeSwitchClient.getVariable(uuid, "my_var");
```

#### 10. 事件监听

```java
// 监听特定事件类型
freeSwitchClient.addEventListener("CHANNEL_HANGUP", new EslEventListener() {
    @Override
    public void onEvent(EslEvent event) {
        String uuid = event.getUniqueId();
        String hangupCause = event.getHangupCause();
        log.info("Channel {} hangup: {}", uuid, hangupCause);
    }
});

// 监听特定 UUID 的事件
freeSwitchClient.addUuidListener(uuid, new EslEventListener() {
    @Override
    public void onEvent(EslEvent event) {
        log.info("Event for {}: {}", uuid, event.getEventName());
    }
});
```

## 项目结构

```
src/main/java/com/freeswitch/esl/
├── client/                     # ESL 客户端
│   ├── EslConnection.java      # ESL 连接管理
│   ├── EslMessage.java         # ESL 消息对象
│   ├── FreeSwitchClient.java   # 高级 API 客户端
│   └── FreeSwitchClientAutoConfiguration.java
├── config/                     # 配置
│   └── FreeSwitchConfig.java   # 连接配置
├── event/                      # 事件处理
│   ├── EslEvent.java           # 事件对象
│   ├── EslEventHandler.java    # 事件处理器
│   ├── EslEventListener.java   # 事件监听器接口
│   └── EslEventType.java       # 事件类型枚举
├── exception/                  # 异常
│   ├── EslException.java       # 基础异常
│   ├── EslAuthenticationException.java
│   ├── EslCommandException.java
│   └── EslConnectionException.java
└── util/                       # 工具类
    └── EslMessageParser.java   # 消息解析器
```

## 常用 FreeSWITCH 事件

| 事件名称 | 说明 |
|---------|------|
| CHANNEL_CREATE | 通道创建 |
| CHANNEL_ANSWER | 通道应答 |
| CHANNEL_HANGUP | 通道挂断 |
| CHANNEL_HANGUP_COMPLETE | 通道挂断完成 |
| CHANNEL_BRIDGE | 通道桥接 |
| CHANNEL_UNBRIDGE | 通道解除桥接 |
| DTMF | DTMF 按键 |
| RECORD_START | 录音开始 |
| RECORD_STOP | 录音停止 |
| PLAYBACK_START | 播放开始 |
| PLAYBACK_STOP | 播放停止 |
| BACKGROUND_JOB | 后台任务完成 |

## 常用挂断原因

| 挂断原因 | 说明 |
|---------|------|
| NORMAL_CLEARING | 正常挂断 |
| USER_BUSY | 用户忙 |
| NO_ANSWER | 无人接听 |
| CALL_REJECTED | 呼叫被拒绝 |
| ORIGINATOR_CANCEL | 主叫取消 |
| NO_USER_RESPONSE | 用户无响应 |
| UNALLOCATED_NUMBER | 空号 |
| NORMAL_UNSPECIFIED | 未指定原因 |

## 注意事项

1. 确保 FreeSWITCH 的 ESL 模块已启用
2. 检查防火墙是否允许 ESL 端口（默认 8021）连接
3. 生产环境建议修改默认密码
4. 建议使用连接池以提高性能

## License

MIT License
