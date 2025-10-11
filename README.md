# FreeSwitch 转人工功能实现指南

## 项目概述

本项目基于 FreeSwitch 实现了智能的用户转人工意图识别和呼叫转移功能。系统通过多种方式（语音识别、DTMF按键、关键词检测）识别用户的转人工意图，并智能路由到合适的人工客服队列。

## 功能特性

### 1. 多模态意图识别
- **语音识别**: 识别用户语音中的转人工关键词
- **DTMF检测**: 支持按键快速转人工（如按0）
- **关键词匹配**: 识别"人工"、"客服"、"投诉"等关键词
- **情绪检测**: 识别用户情绪化表达，自动提升服务优先级

### 2. 智能队列路由
- **意图分类路由**: 根据用户意图自动选择合适队列
- **负载均衡**: 动态分配到负载较轻的队列
- **VIP识别**: 自动识别VIP客户并优先处理
- **工作时间判断**: 非工作时间自动路由到值班队列

### 3. 多层级队列管理
- **普通人工队列**: 处理一般咨询和转人工请求
- **优先级队列**: VIP客户和紧急情况
- **技术支持队列**: 专门处理技术问题
- **投诉处理队列**: 专门处理投诉和意见
- **夜间值班队列**: 非工作时间服务

### 4. 完整的失败处理机制
- **重试机制**: 转移失败时自动重试
- **溢出处理**: 队列满载时的处理策略
- **回呼服务**: 无法即时处理时提供回呼选项
- **语音留言**: 备选的留言服务

### 5. 全面监控和日志
- **实时监控**: 队列状态、客服状态实时监控
- **性能统计**: 转移成功率、等待时间等指标
- **告警系统**: 异常情况自动告警
- **结构化日志**: 便于分析的JSON格式日志

## 系统架构

```
来电 → 意图识别 → 智能路由 → 队列分配 → 人工客服
  ↓         ↓         ↓         ↓         ↓
语音识别  关键词检测  负载均衡  优先级处理  状态监控
  ↓         ↓         ↓         ↓         ↓
DTMF检测  情绪分析   VIP识别   失败处理   日志记录
```

## 文件结构

```
/workspace/
├── dialplan/                    # 拨号计划配置
│   ├── transfer_to_human.xml   # 主要转人工拨号计划
│   └── queue_management.xml    # 队列管理拨号计划
├── conf/autoload_configs/       # FreeSwitch配置文件
│   └── fifo.conf.xml           # 队列配置文件
├── scripts/                     # Lua脚本
│   ├── intent_detection.lua    # 意图识别脚本
│   ├── voice_intent_processor.lua # 语音处理脚本
│   ├── smart_queue_router.lua  # 智能路由脚本
│   ├── agent_authentication.lua # 客服认证脚本
│   ├── call_transfer_controller.lua # 转移控制脚本
│   ├── logging_and_monitoring.lua # 日志监控脚本
│   └── callback_request.lua    # 回呼请求脚本
└── sounds/                      # 语音文件（需要录制）
    ├── welcome.wav
    ├── please_speak.wav
    ├── transferring_to_agent.wav
    └── ... (其他提示音)
```

## 安装配置

### 1. 拷贝配置文件

```bash
# 拷贝拨号计划
cp dialplan/*.xml /usr/local/freeswitch/conf/dialplan/

# 拷贝FIFO配置
cp conf/autoload_configs/fifo.conf.xml /usr/local/freeswitch/conf/autoload_configs/

# 拷贝脚本文件
cp scripts/*.lua /usr/local/freeswitch/scripts/
```

### 2. 准备语音文件

需要录制以下语音文件并放置在 `/usr/local/freeswitch/sounds/` 目录：

- `welcome.wav` - 欢迎语
- `please_speak.wav` - 请说出您的需求
- `transferring_to_agent.wav` - 正在为您转接人工客服
- `press_0_for_agent.wav` - 按0转人工
- `queue_announcement.wav` - 队列通告
- `hold_music.wav` - 等候音乐
- 其他提示音文件...

### 3. 配置用户分机

在 `/usr/local/freeswitch/conf/directory/default.xml` 中配置客服分机：

```xml
<user id="agent_001">
  <params>
    <param name="password" value="1234"/>
  </params>
  <variables>
    <variable name="user_context" value="default"/>
    <variable name="agent_level" value="junior"/>
  </variables>
</user>
```

### 4. 重启FreeSwitch

```bash
fs_cli -x "reloadxml"
# 或者重启服务
systemctl restart freeswitch
```

## 使用方法

### 客服上线/下线

```bash
# 客服上线
fs_cli -x "originate user/agent_001 agent_login_001"

# 客服下线  
fs_cli -x "originate user/agent_001 agent_logout_001"

# 高级客服上线
fs_cli -x "originate user/agent_001 senior_agent_login_001"
```

### 测试转人工功能

1. **拨打测试号码**: 呼叫配置的服务号码
2. **语音测试**: 说出"我要找人工"、"转客服"等
3. **按键测试**: 按0键直接转人工
4. **投诉测试**: 说出"我要投诉"测试投诉队列路由

### 监控命令

```bash
# 查看队列状态
fs_cli -x "fifo list human_agents_queue@your-domain.com"

# 查看活跃通话
fs_cli -x "show calls"

# 查看日志
tail -f /usr/local/freeswitch/log/freeswitch.log
```

## 配置参数说明

### 意图识别参数

在 `intent_detection.lua` 中可以调整：

```lua
-- 转人工关键词权重配置
local TRANSFER_KEYWORDS = {
    ["人工"] = {weight = 10, category = "direct"},
    ["投诉"] = {weight = 8, category = "complaint"},
    -- 可以添加更多关键词...
}

-- 置信度阈值（0.7表示70%以上置信度才转人工）
if confidence >= 0.7 then
    main_intent = "transfer_to_human"
end
```

### 队列配置参数

在 `fifo.conf.xml` 中可以调整：

```xml
<!-- 队列超时时间 -->
<param name="timeout" value="60"/>

<!-- 通告频率（秒） -->
<param name="announce-frequency" value="30"/>

<!-- 等候音乐 -->
<param name="moh-sound" value="sounds/hold_music.wav"/>
```

### 路由策略配置

在 `smart_queue_router.lua` 中可以调整：

```lua
-- 工作时间配置
business_hours = {
    weekdays = {start_hour = 9, end_hour = 18},
    saturday = {start_hour = 9, end_hour = 17},
    sunday = {start_hour = 10, end_hour = 16}
}

-- 队列容量阈值
queue_thresholds = {
    human_agents_queue = {normal = 10, high = 20, critical = 30}
}
```

## 扩展功能

### 1. 添加新的意图类别

在 `intent_detection.lua` 中添加新的关键词：

```lua
["新业务"] = {weight = 8, category = "new_service"},
```

### 2. 集成外部CRM系统

在脚本中添加API调用来获取客户信息：

```lua
function get_customer_info(phone_number)
    -- 调用CRM API获取客户信息
    -- return customer_info
end
```

### 3. 添加新的队列

在 `fifo.conf.xml` 中添加新队列配置，并在路由脚本中添加相应逻辑。

## 故障排除

### 常见问题

1. **转人工不成功**: 检查客服是否在线，队列配置是否正确
2. **语音识别不准确**: 调整关键词权重，优化语音文件质量
3. **队列无响应**: 检查FIFO模块是否加载，配置文件是否正确

### 调试方法

```bash
# 开启详细日志
fs_cli -x "console loglevel debug"

# 查看特定模块日志
fs_cli -x "log mod_fifo debug"

# 测试脚本执行
fs_cli -x "luarun intent_detection.lua"
```

## 性能优化建议

1. **语音识别优化**: 使用高质量的语音识别引擎
2. **数据库优化**: 将客服信息、VIP列表存储到高性能数据库
3. **负载均衡**: 在多台FreeSwitch服务器间进行负载均衡
4. **缓存机制**: 对频繁查询的数据进行缓存

## 监控和维护

1. **定期检查队列状态**: 确保客服正常在线
2. **分析转移成功率**: 优化意图识别准确性
3. **监控系统资源**: 确保系统稳定运行
4. **定期备份配置**: 防止配置丢失

## 联系支持

如需技术支持或功能定制，请联系系统管理员。