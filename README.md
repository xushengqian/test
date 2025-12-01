# FreeSWITCH Lua 转接人工判断方案

## 🎯 核心问题

**问题**: 在 FreeSWITCH 中使用 Lua 进行转接人工时，如何判断转接是否成功？特别是当坐席软电话未登录时，系统会直接挂断。

**答案**: 有三种方法可以判断转接结果：

1. ✅ **事前判断（推荐）**: 转接前使用 `sofia_contact` 检查坐席是否注册
2. ✅ **事后判断**: 转接后检查 `bridge_hangup_cause` 变量
3. ✅ **综合方案**: 结合事前检查和事后判断，提供最佳体验

## 问题详解

## 解决方案

### 核心问题

当坐席软电话未登录时，FreeSWITCH 的 `bridge` 操作会返回 `USER_NOT_REGISTERED` 的挂断原因，导致呼叫直接结束。

### 判断方法

#### 方法1: 事后判断（检查 bridge_hangup_cause）

```lua
-- 执行转接
session:execute("bridge", "user/1001")

-- 获取挂断原因
local hangup_cause = session:getVariable("bridge_hangup_cause")

if hangup_cause == "SUCCESS" then
    -- 转接成功
elseif hangup_cause == "USER_NOT_REGISTERED" then
    -- 坐席未注册/未登录
elseif hangup_cause == "NO_ANSWER" then
    -- 坐席无应答
elseif hangup_cause == "USER_BUSY" then
    -- 坐席忙线
end
```

#### 方法2: 事前判断（检查注册状态）**推荐**

```lua
local api = freeswitch.API()
local result = api:executeString("sofia_contact 1001")

if result == nil or result == "" or string.find(result, "error") then
    -- 坐席未注册，不执行转接
    return false
else
    -- 坐席已注册，执行转接
    session:execute("bridge", "user/1001")
end
```

## 关键的挂断原因代码

| 挂断原因 | 说明 | 处理建议 |
|---------|------|---------|
| `SUCCESS` | 转接成功并正常结束 | 无需处理 |
| `NORMAL_CLEARING` | 正常挂断 | 无需处理 |
| `USER_NOT_REGISTERED` | **坐席未注册（软电话未登录）** | 提示坐席不在线，不重试 |
| `NO_ANSWER` | 坐席无应答 | 可以重试或转其他坐席 |
| `USER_BUSY` | 坐席忙线 | 转其他坐席或语音留言 |
| `CALL_REJECTED` | 坐席拒接 | 转其他坐席 |

## 文件说明

### 1. transfer_to_agent.lua
完整的转接脚本，包含：
- 坐席注册状态检查
- 转接重试机制
- 详细的错误处理
- 失败原因分类处理

### 2. transfer_simple.lua
简化版本，展示三种核心判断方法：
- 方法1: bridge 后检查
- 方法2: 使用 originate API
- 方法3: 先检查注册再转接（推荐）

### 3. dialplan_example.xml
Dialplan 配置示例，展示如何在 XML 中处理转接失败

## 使用步骤

### 1. 部署 Lua 脚本

```bash
# 复制脚本到 FreeSWITCH 脚本目录
cp transfer_to_agent.lua /usr/share/freeswitch/scripts/

# 或者
cp transfer_to_agent.lua /usr/local/freeswitch/scripts/
```

### 2. 在 Dialplan 中调用

```xml
<action application="set" data="agent_number=1001"/>
<action application="lua" data="transfer_to_agent.lua"/>
```

### 3. 通过通道变量传递参数

在 Lua 脚本中可以接收的变量：
- `agent_number`: 坐席分机号
- `call_timeout`: 振铃超时时间
- `max_retry`: 最大重试次数

## 最佳实践

### 1. 先检查再转接

**推荐做法**：转接前先检查坐席是否在线

```lua
-- 检查坐席注册状态
local api = freeswitch.API()
local contact = api:executeString("sofia_contact " .. agent_number)

if not contact or contact == "" or string.find(contact, "error") then
    -- 坐席离线，直接返回
    session:streamFile("ivr/ivr-no_user_response.wav")
    return false
end

-- 坐席在线，执行转接
session:execute("bridge", "user/" .. agent_number)
```

### 2. 设置合理的超时时间

```lua
session:setVariable("call_timeout", "30")  -- 30秒超时
session:setVariable("leg_timeout", "30")
```

### 3. 使用 continue_on_fail

```lua
-- 转接失败后继续执行，而不是直接挂断
session:setVariable("continue_on_fail", "true")
```

### 4. 记录详细日志

```lua
local function log(level, msg)
    freeswitch.consoleLog(level, "[Transfer] " .. msg .. "\n")
end

log("info", "开始转接到坐席: " .. agent_number)
log("warning", "转接失败: " .. hangup_cause)
```

## 常见问题

### Q1: 坐席未登录时如何避免直接挂断？

**A**: 使用 `continue_on_fail=true` 并在转接前检查注册状态

```lua
session:setVariable("continue_on_fail", "true")
if check_agent_registered(agent_number) then
    session:execute("bridge", "user/" .. agent_number)
else
    -- 处理坐席离线的情况
end
```

### Q2: 如何区分坐席拒接和未登录？

**A**: 通过 `bridge_hangup_cause` 区分

- `USER_NOT_REGISTERED`: 未登录
- `CALL_REJECTED`: 拒接
- `NO_ANSWER`: 无应答（可能是忙或未接听）

### Q3: 如何实现转接失败后的重试？

**A**: 使用循环结构，但要注意坐席未登录时不应重试

```lua
local retry_count = 0
local max_retry = 3

while retry_count < max_retry do
    if not check_agent_registered(agent_number) then
        break  -- 未登录，停止重试
    end
    
    session:execute("bridge", "user/" .. agent_number)
    local cause = session:getVariable("bridge_hangup_cause")
    
    if cause == "SUCCESS" then
        break
    end
    
    retry_count = retry_count + 1
end
```

### Q4: 如何检测坐席的实时状态（空闲/忙碌）？

**A**: 使用 FreeSWITCH 的 callcenter 模块或自定义状态管理

```lua
-- 使用 callcenter 模块
local api = freeswitch.API()
local result = api:executeString("callcenter_config agent get status " .. agent_id)

-- 或使用 show channels 检查坐席是否在通话中
local channels = api:executeString("show channels like " .. agent_number)
```

## 调试技巧

### 1. 启用详细日志

在 FreeSWITCH 控制台：

```
freeswitch> console loglevel 7
freeswitch> lua transfer_to_agent.lua
```

### 2. 查看通道变量

```lua
-- 打印所有通道变量
session:execute("info", "")

-- 或在脚本中打印
freeswitch.consoleLog("info", "bridge_hangup_cause: " .. 
    tostring(session:getVariable("bridge_hangup_cause")) .. "\n")
```

### 3. 使用 FreeSWITCH CLI 命令

```bash
# 检查用户注册状态
fs_cli -x "sofia_contact 1001"

# 查看 Sofia 状态
fs_cli -x "sofia status profile internal"

# 查看注册用户
fs_cli -x "sofia status profile internal reg"
```

## 扩展功能

### 1. 智能路由到可用坐席

```lua
local agent_list = {"1001", "1002", "1003"}

for _, agent in ipairs(agent_list) do
    if check_agent_registered(agent) then
        local success = transfer_to_agent(agent)
        if success then
            break
        end
    end
end
```

### 2. 记录转接历史到数据库

```lua
local dbh = freeswitch.Dbh("odbc://freeswitch")
local sql = string.format(
    "INSERT INTO call_transfers (call_id, agent, result, timestamp) VALUES ('%s', '%s', '%s', NOW())",
    session:get_uuid(), agent_number, hangup_cause
)
dbh:query(sql)
```

### 3. 集成坐席状态管理系统

结合外部系统（如呼叫中心平台）管理坐席状态。

## 参考资源

- [FreeSWITCH 官方文档](https://freeswitch.org/confluence/)
- [Lua API 参考](https://freeswitch.org/confluence/display/FREESWITCH/Lua+API+Reference)
- [Hangup Cause Code 列表](https://freeswitch.org/confluence/display/FREESWITCH/Hangup+Cause+Code+Table)
