-- FreeSWITCH bridge 操作成功判断示例

-- 方法1: 检查 bridge_result 通道变量
local function check_bridge_by_variable(session, uuid, data)
    -- 执行 bridge 操作
    session:execute("bridge", "{hangup_after_bridge=false,session_type=4,object_type=2,origination_caller_id_number='88888888',uuid="..uuid.."}sofia/gateway/gwopensips/"..data)
    
    -- 等待 bridge 完成
    session:sleep(100)  -- 等待100ms让bridge操作完成
    
    -- 检查 bridge_result 变量
    local bridge_result = session:getVariable("bridge_result")
    
    if bridge_result == "SUCCESS" then
        return true, "呼叫成功"
    elseif bridge_result == "FAILURE" then
        return false, "呼叫失败: " .. (session:getVariable("bridge_hangup_cause") or "未知原因")
    elseif bridge_result == "NONEXISTENT" then
        return false, "目标不存在"
    elseif bridge_result == "TIMEOUT" then
        return false, "呼叫超时"
    else
        return false, "未知状态: " .. (bridge_result or "nil")
    end
end

-- 方法2: 检查 originate_disposition 通道变量（更详细）
local function check_bridge_by_disposition(session, uuid, data)
    session:execute("bridge", "{hangup_after_bridge=false,session_type=4,object_type=2,origination_caller_id_number='88888888',uuid="..uuid.."}sofia/gateway/gwopensips/"..data)
    
    session:sleep(100)
    
    local disposition = session:getVariable("originate_disposition")
    
    -- 常见的 disposition 值：
    -- ANSWER: 成功接通
    -- CHANUNAVAIL: 通道不可用
    -- CONGESTION: 拥塞
    -- NOANSWER: 无应答
    -- BUSY: 忙线
    -- FAILURE: 失败
    -- TIMEOUT: 超时
    
    if disposition == "ANSWER" then
        return true, "呼叫成功接通"
    elseif disposition == "NOANSWER" then
        return false, "无应答"
    elseif disposition == "BUSY" then
        return false, "忙线"
    elseif disposition == "CHANUNAVAIL" then
        return false, "通道不可用"
    elseif disposition == "CONGESTION" then
        return false, "网络拥塞"
    elseif disposition == "TIMEOUT" then
        return false, "呼叫超时"
    elseif disposition == "FAILURE" then
        local cause = session:getVariable("hangup_cause") or session:getVariable("originate_disposition")
        return false, "呼叫失败: " .. (cause or "未知")
    else
        return false, "未知状态: " .. (disposition or "nil")
    end
end

-- 方法3: 综合判断（推荐使用）
local function check_bridge_comprehensive(session, uuid, data)
    -- 执行 bridge 操作
    local result = session:execute("bridge", "{hangup_after_bridge=false,session_type=4,object_type=2,origination_caller_id_number='88888888',uuid="..uuid.."}sofia/gateway/gwopensips/"..data)
    
    -- 等待操作完成
    session:sleep(100)
    
    -- 获取多个关键变量
    local bridge_result = session:getVariable("bridge_result")
    local disposition = session:getVariable("originate_disposition")
    local hangup_cause = session:getVariable("hangup_cause")
    local answer_state = session:getVariable("answer_state")
    
    -- 判断是否成功
    if bridge_result == "SUCCESS" or disposition == "ANSWER" then
        return true, {
            success = true,
            message = "呼叫成功",
            bridge_result = bridge_result,
            disposition = disposition,
            answer_state = answer_state
        }
    else
        return false, {
            success = false,
            message = "呼叫失败",
            bridge_result = bridge_result,
            disposition = disposition,
            hangup_cause = hangup_cause,
            answer_state = answer_state
        }
    end
end

-- 使用示例
local function example_usage(session, uuid, data)
    -- 方式1: 简单判断
    local success, message = check_bridge_by_variable(session, uuid, data)
    if success then
        session:consoleLog("INFO", "呼叫成功: " .. message)
    else
        session:consoleLog("WARNING", "呼叫失败: " .. message)
    end
    
    -- 方式2: 详细判断
    local success2, message2 = check_bridge_by_disposition(session, uuid, data)
    if success2 then
        session:consoleLog("INFO", message2)
    else
        session:consoleLog("WARNING", message2)
    end
    
    -- 方式3: 综合判断（推荐）
    local success3, result = check_bridge_comprehensive(session, uuid, data)
    if success3 then
        session:consoleLog("INFO", "呼叫成功 - " .. result.message)
        session:consoleLog("INFO", "Disposition: " .. (result.disposition or "N/A"))
    else
        session:consoleLog("WARNING", "呼叫失败 - " .. result.message)
        session:consoleLog("WARNING", "Hangup Cause: " .. (result.hangup_cause or "N/A"))
    end
end

-- 实际使用时的简化版本
local function simple_bridge_check(session, uuid, data)
    session:execute("bridge", "{hangup_after_bridge=false,session_type=4,object_type=2,origination_caller_id_number='88888888',uuid="..uuid.."}sofia/gateway/gwopensips/"..data)
    
    -- 等待bridge完成
    session:sleep(100)
    
    -- 最简单的方式：检查 bridge_result
    local bridge_result = session:getVariable("bridge_result")
    
    if bridge_result == "SUCCESS" then
        return true  -- 呼叫成功
    else
        return false  -- 呼叫失败
    end
end
