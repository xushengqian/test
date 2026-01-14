# IVR System - 交互式语音应答系统

基于 Spring Boot 和 FreeSWITCH 的企业级 IVR（Interactive Voice Response）系统。

## 功能特性

- **可视化流程设计**: 使用 YAML 文件定义 IVR 流程，支持热加载
- **多种节点类型**: 欢迎语、菜单、播放、收集输入、转人工、录音、ASR等
- **FreeSWITCH 集成**: 通过 ESL（Event Socket Library）与 FreeSWITCH 通信
- **DTMF 支持**: 完整的按键识别和处理
- **转接功能**: 支持转人工坐席、转外线、队列等待
- **语音识别**: 支持 ASR 语音识别集成
- **TTS 支持**: 文本转语音功能
- **REST API**: 提供完整的 HTTP API 接口
- **Webhook 回调**: 支持 FreeSWITCH 事件回调

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                      IVR System                              │
├─────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  REST API    │  │   Webhook    │  │   Health     │      │
│  │  Controller  │  │  Controller  │  │  Controller  │      │
│  └──────┬───────┘  └──────┬───────┘  └──────────────┘      │
│         │                  │                                 │
│  ┌──────▼──────────────────▼───────┐                        │
│  │          IVR Service             │                        │
│  └──────────────┬──────────────────┘                        │
│                 │                                            │
│  ┌──────────────▼──────────────────┐                        │
│  │       IVR Flow Engine            │                        │
│  ├──────────────────────────────────┤                        │
│  │  ┌────────────────────────────┐ │                        │
│  │  │     Node Handlers          │ │                        │
│  │  │  • Welcome • Menu • Play   │ │                        │
│  │  │  • Collect • Transfer      │ │                        │
│  │  │  • Record  • ASR • Queue   │ │                        │
│  │  └────────────────────────────┘ │                        │
│  └──────────────┬──────────────────┘                        │
│                 │                                            │
│  ┌──────────────▼──────────────────┐                        │
│  │     FreeSWITCH ESL Client        │                        │
│  └──────────────┬──────────────────┘                        │
│                 │                                            │
└─────────────────┼───────────────────────────────────────────┘
                  │ ESL Protocol
┌─────────────────▼───────────────────────────────────────────┐
│                    FreeSWITCH                                │
└─────────────────────────────────────────────────────────────┘
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- FreeSWITCH 1.10+

### 构建项目

```bash
mvn clean package
```

### 运行服务

```bash
java -jar target/ivr-system-1.0.0-SNAPSHOT.jar
```

### 配置说明

主要配置文件 `application.yml`:

```yaml
# FreeSWITCH ESL配置
freeswitch:
  host: 127.0.0.1
  port: 8021
  password: ClueCon

# IVR配置
ivr:
  default-flow: default
  default-timeout: 10000
  default-max-retries: 3
```

## IVR 流程定义

流程使用 YAML 格式定义，放置在 `src/main/resources/flows/` 目录下。

### 流程示例

```yaml
id: default
name: 客户服务热线
enabled: true
entryNodeId: welcome
defaultTimeout: 10000
globalMaxRetries: 3

nodes:
  - id: welcome
    name: 欢迎语
    type: WELCOME
    prompt: "欢迎致电客户服务热线"
    useTts: true
    defaultNextNode: main_menu

  - id: main_menu
    name: 主菜单
    type: MENU
    prompt: "账户查询请按1，业务办理请按2，转人工请按0"
    useTts: true
    dtmfMapping:
      "1": account_query
      "2": business
      "0": transfer_agent
```

### 节点类型

| 类型 | 说明 |
|------|------|
| WELCOME | 欢迎语节点，播放欢迎信息 |
| MENU | 菜单节点，播放菜单并等待DTMF输入 |
| PLAY | 播放节点，播放音频文件或TTS |
| COLLECT | 收集节点，收集多位DTMF输入 |
| TRANSFER_AGENT | 转人工节点，转接到坐席 |
| TRANSFER_EXTERNAL | 转外线节点，转接到外部号码 |
| QUEUE | 队列节点，进入等待队列 |
| RECORD | 录音节点，录制用户语音 |
| ASR | 语音识别节点 |
| CONDITION | 条件判断节点 |
| ACTION | 自定义动作节点 |
| HANGUP | 挂断节点 |

## REST API

### 系统状态

```bash
GET /api/ivr/status
```

### 发起呼出

```bash
POST /api/ivr/call/outbound
Content-Type: application/json

{
  "destination": "13800138000",
  "callerIdNumber": "4001234567",
  "flowId": "outbound",
  "gateway": "default"
}
```

### 挂断呼叫

```bash
POST /api/ivr/call/{sessionId}/hangup?reason=NORMAL_CLEARING
```

### 转人工

```bash
POST /api/ivr/call/{sessionId}/transfer/agent
Content-Type: application/json

{
  "agentGroup": "customer_service",
  "skillGroup": "general"
}
```

### 获取流程列表

```bash
GET /api/ivr/flows
```

### 重新加载流程

```bash
POST /api/ivr/flows/reload
```

## Webhook 接口

系统提供 Webhook 接口供 FreeSWITCH 回调：

| 接口 | 说明 |
|------|------|
| POST /api/webhook/inbound | 呼入回调 |
| POST /api/webhook/dtmf | DTMF回调 |
| POST /api/webhook/hangup | 挂断回调 |
| POST /api/webhook/playback-complete | 播放完成回调 |
| POST /api/webhook/timeout | 超时回调 |
| POST /api/webhook/asr-result | ASR结果回调 |

## 健康检查

```bash
# 健康检查
GET /api/health

# 存活检查
GET /api/health/live

# 就绪检查
GET /api/health/ready
```

## 项目结构

```
src/main/java/com/ivr/system/
├── IvrApplication.java          # 应用入口
├── config/                      # 配置类
│   ├── FreeSwitchConfig.java
│   ├── IvrConfig.java
│   └── AsyncConfig.java
├── controller/                  # REST控制器
│   ├── IvrController.java
│   ├── WebhookController.java
│   └── HealthController.java
├── service/                     # 服务层
│   ├── IvrService.java
│   └── ActionHandlerRegistry.java
├── engine/                      # 流程引擎
│   ├── IvrFlowEngine.java
│   └── IvrFlowRepository.java
├── handler/                     # 节点处理器
│   ├── NodeHandler.java
│   ├── NodeHandlerFactory.java
│   └── impl/
│       ├── WelcomeNodeHandler.java
│       ├── MenuNodeHandler.java
│       ├── PlayNodeHandler.java
│       └── ...
├── esl/                         # FreeSWITCH ESL
│   ├── FreeSwitchClient.java
│   ├── EslResponse.java
│   └── EslEventHandler.java
└── model/                       # 数据模型
    ├── IvrNode.java
    ├── IvrFlow.java
    ├── CallSession.java
    ├── IvrEvent.java
    └── IvrResult.java
```

## 扩展开发

### 自定义节点处理器

实现 `NodeHandler` 接口：

```java
@Component
public class CustomNodeHandler implements NodeHandler {
    
    @Override
    public IvrNode.NodeType getNodeType() {
        return IvrNode.NodeType.ACTION;
    }
    
    @Override
    public IvrResult execute(CallSession session, IvrNode node, IvrFlow flow) {
        // 实现自定义逻辑
        return IvrResult.success();
    }
}
```

### 自定义动作处理器

注册到 `ActionHandlerRegistry`：

```java
@Component
public class CustomActionHandlers {
    
    @Autowired
    private ActionHandlerRegistry registry;
    
    @PostConstruct
    public void init() {
        registry.registerHandler("myAction", (session, params) -> {
            // 实现业务逻辑
            return IvrResult.success();
        });
    }
}
```

## FreeSWITCH 配置

### ESL 配置

在 FreeSWITCH 的 `event_socket.conf.xml` 中配置：

```xml
<configuration name="event_socket.conf" description="Socket Client">
  <settings>
    <param name="nat-map" value="false"/>
    <param name="listen-ip" value="0.0.0.0"/>
    <param name="listen-port" value="8021"/>
    <param name="password" value="ClueCon"/>
  </settings>
</configuration>
```

### Dialplan 配置

```xml
<extension name="ivr-inbound">
  <condition field="destination_number" expression="^(\d{10,11})$">
    <action application="answer"/>
    <action application="curl" data="http://localhost:8080/api/webhook/inbound post uuid=${uuid}&caller_id_number=${caller_id_number}&destination_number=${destination_number}"/>
  </condition>
</extension>
```

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！
