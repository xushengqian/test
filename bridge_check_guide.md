# FreeSWITCH Bridge 操作成功判断指南

## 核心方法

在 FreeSWITCH 中，判断 `session:execute("bridge", ...)` 是否成功，主要通过检查**通道变量**来实现。

## 关键通道变量

### 1. `bridge_result` (最常用)
- **值**: `SUCCESS` / `FAILURE` / `NONEXISTENT` / `TIMEOUT`
- **说明**: bridge 操作的直接结果

### 2. `originate_disposition` (最详细)
- **值**: 
  - `ANSWER` - 成功接通 ✅
  - `NOANSWER` - 无应答
  - `BUSY` - 忙线
  - `CHANUNAVAIL` - 通道不可用
  - `CONGESTION` - 网络拥塞
  - `TIMEOUT` - 超时
  - `FAILURE` - 失败

### 3. `hangup_cause` (失败原因)
- **说明**: 如果失败，这里会有具体的挂断原因代码

### 4. `answer_state` (应答状态)
- **值**: `answered` / `unanswered`

## 判断方法

### 方法1: 检查 bridge_result (最简单)

```lua
session:execute("bridge", "{hangup_after_bridge=false,session_type=4,object_type=2,origination_caller_id_number='88888888',uuid="..uuid.."}sofia/gateway/gwopensips/"..data)

session:sleep(100)  -- 等待操作完成

local bridge_result = session:getVariable("bridge_result")

if bridge_result == "SUCCESS" then
    -- 呼叫成功
    session:consoleLog("INFO", "呼叫成功")
else
    -- 呼叫失败
    local cause = session:getVariable("hangup_cause") or "未知原因"
    session:consoleLog("WARNING", "呼叫失败: " .. cause)
end
```

### 方法2: 检查 originate_disposition (推荐)

```lua
session:execute("bridge", "{hangup_after_bridge=false,session_type=4,object_type=2,origination_caller_id_number='88888888',uuid="..uuid.."}sofia/gateway/gwopensips/"..data)

session:sleep(100)

local disposition = session:getVariable("originate_disposition")

if disposition == "ANSWER" then
    -- 呼叫成功接通
    session:consoleLog("INFO", "呼叫成功")
else
    -- 根据不同的失败原因处理
    session:consoleLog("WARNING", "呼叫失败: " .. (disposition or "未知"))
end
```

### 方法3: 综合判断 (最可靠)

```lua
session:execute("bridge", "{hangup_after_bridge=false,session_type=4,object_type=2,origination_caller_id_number='88888888',uuid="..uuid.."}sofia/gateway/gwopensips/"..data)

session:sleep(100)

local bridge_result = session:getVariable("bridge_result")
local disposition = session:getVariable("originate_disposition")

-- 双重检查，确保准确性
if bridge_result == "SUCCESS" or disposition == "ANSWER" then
    -- 呼叫成功
    return true
else
    -- 呼叫失败
    local hangup_cause = session:getVariable("hangup_cause")
    session:consoleLog("WARNING", "呼叫失败 - Cause: " .. (hangup_cause or "未知"))
    return false
end
```

## 注意事项

1. **必须等待**: bridge 操作是异步的，需要等待操作完成后再检查变量
   ```lua
   session:sleep(100)  -- 至少等待100ms
   ```

2. **变量获取时机**: 在 bridge 操作完成后立即获取变量，不要延迟太久

3. **hangup_after_bridge=false**: 你的配置中设置了 `hangup_after_bridge=false`，这意味着 bridge 完成后不会自动挂断，可以继续执行后续操作

4. **错误处理**: 建议同时检查多个变量，以提高判断的准确性

## 完整示例

```lua
local function bridge_and_check(session, uuid, data)
    -- 执行 bridge
    session:execute("bridge", "{hangup_after_bridge=false,session_type=4,object_type=2,origination_caller_id_number='88888888',uuid="..uuid.."}sofia/gateway/gwopensips/"..data)
    
    -- 等待完成
    session:sleep(100)
    
    -- 检查结果
    local bridge_result = session:getVariable("bridge_result")
    local disposition = session:getVariable("originate_disposition")
    
    if bridge_result == "SUCCESS" or disposition == "ANSWER" then
        session:consoleLog("INFO", "Bridge成功: " .. data)
        return true
    else
        local cause = session:getVariable("hangup_cause") or disposition or "未知"
        session:consoleLog("WARNING", "Bridge失败: " .. cause)
        return false
    end
end

-- 使用
local success = bridge_and_check(session, uuid, data)
if success then
    -- 处理成功逻辑
else
    -- 处理失败逻辑
end
```
