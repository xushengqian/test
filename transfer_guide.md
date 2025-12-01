# FreeSWITCH Lua 转接人工判断成功的方法

## 问题说明

在 FreeSWITCH Lua 脚本中转接人工坐席时，需要判断：
1. 转接是否成功
2. 坐席软电话未登录时会直接挂断的情况

## 判断转接成功的关键指标

### 1. 挂断原因码（Hangup Cause）

转接成功后，关键变量：
- `bridge_hangup_cause`: 桥接挂断原因码
- `hangup_cause`: 通道挂断原因码
- `billsec`: 通话时长（秒）

**成功标志：**
- `bridge_hangup_cause = 16` (NORMAL_CLEARING) 且 `billsec > 0`
- 表示正常通话后挂断

**失败标志：**
- `bridge_hangup_cause = 17` (USER_BUSY) - 坐席忙线
- `bridge_hangup_cause = 18` (NO_ANSWER) - 坐席未接听
- `bridge_hangup_cause = 19` (NO_USER_RESPONSE) - 无用户响应（通常表示未登录）
- `bridge_hangup_cause = 20` (SUBSCRIBER_ABSENT) - 用户不存在

### 2. 坐席未登录的处理

坐席未登录时，转接会立即失败，通常返回：
- `NO_USER_RESPONSE` (19)
- `SUBSCRIBER_ABSENT` (20)
- 或者直接挂断，`billsec = 0`

## 实现方法

### 方法1：转接前检查坐席状态（推荐）

```lua
-- 转接前先检查坐席是否在线
local agent_status = check_agent_status(agent_extension)
if not agent_status.online then
    -- 坐席未登录，直接处理，不执行转接
    session:streamFile("agent_offline.wav")
    session:hangup("NO_USER_RESPONSE")
    return
end

-- 执行转接
local result = session:execute("bridge", "user/" .. agent_extension)
```

### 方法2：转接后判断结果

```lua
-- 执行转接
session:execute("bridge", "{hangup_after_bridge=true}user/" .. agent_extension)

-- 获取挂断原因
local bridge_hangup_cause = session:getVariable("bridge_hangup_cause")
local call_duration = tonumber(session:getVariable("billsec") or "0")

-- 判断是否成功
if bridge_hangup_cause == "16" and call_duration > 0 then
    -- 转接成功
elseif bridge_hangup_cause == "19" or bridge_hangup_cause == "20" then
    -- 坐席未登录或不存在
elseif bridge_hangup_cause == "17" then
    -- 坐席忙线
elseif bridge_hangup_cause == "18" then
    -- 坐席未接听
end
```

## 检查坐席状态的方法

### 方法1：使用 sofia_contact

```lua
local api = freeswitch.API()
local contact = api:executeString("sofia_contact " .. agent_extension)
if contact and contact ~= "" and contact ~= "NOT_FOUND" then
    -- 坐席在线
end
```

### 方法2：检查注册表

```lua
local api = freeswitch.API()
local reg_status = api:executeString("sofia status profile internal reg " .. agent_extension)
if reg_status and reg_status:match("Reg") then
    -- 坐席已注册
end
```

## 完整的转接流程示例

```lua
function transfer_to_agent(session, agent_extension)
    -- 1. 检查坐席状态
    local agent_status = check_agent_status(agent_extension)
    if not agent_status.online then
        return {success = false, reason = "agent_offline"}
    end
    
    -- 2. 执行转接
    session:execute("bridge", "{hangup_after_bridge=true}user/" .. agent_extension)
    
    -- 3. 判断结果
    local bridge_hangup_cause = session:getVariable("bridge_hangup_cause")
    local call_duration = tonumber(session:getVariable("billsec") or "0")
    
    if bridge_hangup_cause == "16" and call_duration > 0 then
        return {success = true, duration = call_duration}
    else
        return {
            success = false, 
            reason = bridge_hangup_cause,
            duration = call_duration
        }
    end
end
```

## 常见挂断原因码

| 原因码 | 名称 | 说明 |
|--------|------|------|
| 16 | NORMAL_CLEARING | 正常挂断（成功） |
| 17 | USER_BUSY | 用户忙线 |
| 18 | NO_ANSWER | 无应答 |
| 19 | NO_USER_RESPONSE | 无用户响应（通常未登录） |
| 20 | SUBSCRIBER_ABSENT | 用户不存在 |
| 21 | CALL_REJECTED | 呼叫被拒绝 |

## 注意事项

1. **转接前检查**：建议在转接前先检查坐席状态，避免无效转接
2. **超时设置**：设置合理的转接超时时间
3. **通话时长**：判断成功时，确保 `billsec > 0`，避免误判
4. **错误处理**：根据不同的失败原因，给用户播放相应的提示音
5. **日志记录**：记录转接结果，便于问题排查
