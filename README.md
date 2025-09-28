# Lua 人工接入服务模块

一个功能完整的 Lua 人工客服接入系统，用于在自动化客服系统中呼叫人工客服介入。

## 功能特性

### 核心功能
- ✅ **人工接入请求**：支持用户请求转接人工客服
- ✅ **优先级队列**：支持紧急、高、中、低多级优先级
- ✅ **队列管理**：实时查询排队状态和预计等待时间
- ✅ **会话管理**：完整的会话生命周期管理
- ✅ **消息传递**：用户与客服之间的消息传递
- ✅ **客服分配**：基于技能、负载、语言的智能分配
- ✅ **工作时间**：支持配置工作时间和节假日
- ✅ **评分系统**：会话结束后的满意度评分

### 高级特性
- 🔧 **配置管理**：灵活的配置系统，支持环境变量
- 📊 **监控告警**：队列长度、等待时间、错误率监控
- 🔒 **安全机制**：API签名、SSL、IP白名单、速率限制
- 📝 **日志记录**：详细的操作日志和错误追踪
- 🔄 **重试机制**：支持指数退避的自动重试
- 🌐 **多语言支持**：支持中文、英文、日文等多种语言
- 📧 **通知系统**：支持邮件、Webhook、短信通知

## 文件结构

```
/workspace/
├── human_intervention.lua  # 主模块文件
├── config.lua             # 配置文件
├── example_usage.lua      # 使用示例
└── README.md             # 说明文档
```

## 快速开始

### 1. 基本使用

```lua
-- 加载模块
local human_intervention = require("human_intervention")

-- 创建人工接入请求
local params = {
    user_id = "user_12345",
    user_name = "张三",
    reason = "需要人工协助",
    priority = 2,  -- HIGH priority
    context = {
        issue_type = "账户问题"
    }
}

local result = human_intervention.request_human_agent(params)

if result.success then
    print("会话ID: " .. result.session_id)
    print("排队位置: " .. result.queue_position)
else
    print("错误: " .. result.error)
end
```

### 2. 检查队列状态

```lua
local status = human_intervention.check_queue_status(session_id)
print("状态: " .. status.status)
print("排队位置: " .. status.queue_position)
```

### 3. 发送消息

```lua
human_intervention.send_message(session_id, "您好，我需要帮助")
```

### 4. 结束会话

```lua
human_intervention.end_session(session_id, 5)  -- 5星评分
```

## API 参考

### 主要函数

#### `request_human_agent(params)`
创建人工接入请求

**参数：**
- `user_id` (string, 必需): 用户ID
- `user_name` (string, 可选): 用户名称
- `reason` (string, 必需): 接入原因
- `priority` (number, 可选): 优先级 (1=紧急, 2=高, 3=中, 4=低)
- `context` (table, 可选): 上下文信息

**返回：**
- `success` (boolean): 是否成功
- `session_id` (string): 会话ID
- `queue_position` (number): 排队位置
- `estimated_wait_time` (number): 预计等待时间（秒）

#### `check_queue_status(session_id)`
检查队列状态

**参数：**
- `session_id` (string): 会话ID

**返回：**
- `status` (string): 状态 (waiting/connected/completed/cancelled)
- `queue_position` (number): 当前排队位置
- `estimated_wait_time` (number): 预计等待时间

#### `send_message(session_id, message)`
发送消息给客服

**参数：**
- `session_id` (string): 会话ID
- `message` (string): 消息内容

#### `end_session(session_id, rating)`
结束会话并评分

**参数：**
- `session_id` (string): 会话ID
- `rating` (number, 可选): 评分 (1-5)

#### `cancel_request(session_id)`
取消人工接入请求

#### `get_available_agents()`
获取可用客服列表

## 配置说明

配置文件 `config.lua` 包含以下主要配置项：

### API配置
```lua
api = {
    primary_endpoint = "https://api.customer-service.com/v1",
    api_key = "your-api-key"
}
```

### 工作时间配置
```lua
working_hours = {
    timezone = "Asia/Shanghai",
    weekdays = {
        monday = {start = "09:00", ["end"] = "18:00", enabled = true}
        -- ...
    }
}
```

### 队列配置
```lua
queue = {
    max_queue_size = 1000,
    max_requests_per_user = 3,
    queue_timeout = 600
}
```

## 运行示例

运行完整的示例程序：

```bash
lua example_usage.lua
```

这将演示所有主要功能，包括：
1. 基本的人工接入请求
2. 紧急优先级请求
3. 检查队列状态
4. 获取可用客服
5. 发送消息
6. 批量请求处理
7. 配置管理
8. 错误处理
9. 完整的会话流程

## 集成指南

### 1. 与现有系统集成

```lua
-- 在您的客服机器人中
if need_human_help(user_message) then
    local result = human_intervention.request_human_agent({
        user_id = current_user.id,
        user_name = current_user.name,
        reason = "机器人无法处理的问题",
        context = {
            chat_history = get_chat_history(),
            bot_version = "1.0"
        }
    })
    
    if result.success then
        -- 通知用户已转接人工
        send_to_user("正在为您转接人工客服...")
    end
end
```

### 2. 关键词触发

```lua
-- 检测触发关键词
local trigger_keywords = {"人工", "客服", "投诉", "退款"}

for _, keyword in ipairs(trigger_keywords) do
    if string.find(user_message, keyword) then
        -- 触发人工接入
        human_intervention.request_human_agent(...)
        break
    end
end
```

### 3. 自定义优先级规则

```lua
-- 根据用户等级设置优先级
local function get_priority(user)
    if user.vip_level == "platinum" then
        return 1  -- URGENT
    elseif user.vip_level == "gold" then
        return 2  -- HIGH
    else
        return 3  -- MEDIUM
    end
end
```

## 错误处理

系统会返回详细的错误信息：

```lua
local result = human_intervention.request_human_agent(params)

if not result.success then
    if result.error == "当前不在客服工作时间内" then
        -- 处理非工作时间
        show_working_hours(result.working_hours)
    elseif result.error == "用户ID不能为空" then
        -- 处理参数错误
        request_user_login()
    else
        -- 通用错误处理
        log_error(result.error)
    end
end
```

## 性能优化建议

1. **缓存会话信息**：避免频繁查询状态
2. **批量处理**：使用批量接口处理多个请求
3. **异步处理**：使用协程处理长时间操作
4. **连接池**：复用HTTP连接提高性能

## 安全建议

1. **使用环境变量**：存储敏感信息如API密钥
2. **启用SSL**：确保数据传输安全
3. **实施速率限制**：防止滥用
4. **验证输入**：对所有用户输入进行验证
5. **日志脱敏**：不记录敏感信息

## 监控和维护

### 关键指标
- 平均等待时间
- 队列长度
- 成功率
- 客服利用率
- 用户满意度

### 日志分析
```lua
-- 分析日志示例
grep "ERROR" /var/log/human_intervention.log
grep "session_id" /var/log/human_intervention.log | wc -l
```

## 故障排除

### 常见问题

1. **连接超时**
   - 检查网络连接
   - 增加超时时间
   - 使用备用服务器

2. **队列已满**
   - 增加队列大小
   - 优化客服分配
   - 启用自动扩容

3. **认证失败**
   - 检查API密钥
   - 验证签名算法
   - 检查IP白名单

## 扩展开发

系统支持以下扩展：

1. **自定义分配算法**
2. **第三方集成**（CRM、工单系统）
3. **AI辅助功能**（情感分析、意图识别）
4. **多渠道支持**（网页、APP、微信）

## 依赖项

- Lua 5.1+ 或 LuaJIT
- (可选) luasocket - HTTP请求
- (可选) lua-cjson - JSON处理
- (可选) redis-lua - Redis支持

## 许可证

MIT License

## 支持

如需技术支持，请联系技术团队或查看在线文档。

---

**版本**: 1.0.0  
**更新日期**: 2025-09-28  
**作者**: AI Assistant