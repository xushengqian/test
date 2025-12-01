--[[
    简化版本：核心转接判断逻辑
]]--

-- 方法1: 使用 bridge 并检查 hangup cause
function method1_bridge_check(session, agent_number)
    if not session:ready() then
        return false
    end
    
    -- 执行bridge
    session:execute("bridge", "user/" .. agent_number)
    
    -- 检查桥接结果的几个关键变量
    local bridge_hangup_cause = session:getVariable("bridge_hangup_cause")
    local originate_disposition = session:getVariable("originate_disposition")
    
    freeswitch.consoleLog("info", "bridge_hangup_cause: " .. tostring(bridge_hangup_cause) .. "\n")
    freeswitch.consoleLog("info", "originate_disposition: " .. tostring(originate_disposition) .. "\n")
    
    -- 判断转接结果
    if bridge_hangup_cause == "SUCCESS" or 
       bridge_hangup_cause == "NORMAL_CLEARING" then
        return true, "转接成功并正常结束"
    elseif bridge_hangup_cause == "USER_NOT_REGISTERED" then
        return false, "坐席未注册（软电话未登录）"
    elseif bridge_hangup_cause == "NO_ANSWER" then
        return false, "坐席无应答"
    elseif bridge_hangup_cause == "USER_BUSY" then
        return false, "坐席忙线"
    elseif bridge_hangup_cause == "CALL_REJECTED" then
        return false, "坐席拒接"
    else
        return false, "转接失败: " .. tostring(bridge_hangup_cause)
    end
end

-- 方法2: 使用 originate API
function method2_originate_api(agent_number, timeout)
    local api = freeswitch.API()
    
    -- 构造originate命令
    local cmd = string.format(
        "originate {origination_caller_id_number=%s,ignore_early_media=true,return_ring_ready=true}user/%s &park",
        session:getVariable("caller_id_number") or "unknown",
        agent_number
    )
    
    -- 执行originate
    local result = api:executeString(cmd)
    
    freeswitch.consoleLog("info", "Originate result: " .. tostring(result) .. "\n")
    
    -- 检查结果
    if result and not string.find(result, "ERR") then
        -- 获取新的channel UUID
        local uuid = result:match("^%+OK%s+(.+)")
        if uuid then
            return true, uuid
        end
    end
    
    -- 解析错误信息
    if string.find(result, "USER_NOT_REGISTERED") then
        return false, "坐席未注册"
    elseif string.find(result, "NO_ANSWER") then
        return false, "坐席无应答"
    else
        return false, "转接失败"
    end
end

-- 方法3: 先检查注册状态再转接（推荐）
function method3_check_and_bridge(session, agent_number)
    local api = freeswitch.API()
    
    -- 步骤1: 检查坐席是否注册
    local sofia_contact = api:executeString("sofia_contact " .. agent_number)
    
    if not sofia_contact or 
       sofia_contact == "" or 
       string.find(sofia_contact, "error") or
       string.find(sofia_contact, "not found") then
        freeswitch.consoleLog("warning", "坐席 " .. agent_number .. " 未注册\n")
        return false, "AGENT_OFFLINE"
    end
    
    freeswitch.consoleLog("info", "坐席已注册: " .. sofia_contact .. "\n")
    
    -- 步骤2: 执行转接
    session:setVariable("call_timeout", "30")
    session:setVariable("continue_on_fail", "true")
    session:execute("bridge", "user/" .. agent_number)
    
    -- 步骤3: 检查结果
    local hangup_cause = session:getVariable("bridge_hangup_cause")
    
    if hangup_cause == "SUCCESS" or 
       hangup_cause == "NORMAL_CLEARING" then
        return true, "SUCCESS"
    else
        return false, hangup_cause
    end
end

-- 主程序
if session and session:ready() then
    session:answer()
    
    local agent_number = session:getVariable("agent_number") or "1001"
    
    -- 使用方法3（推荐）
    local success, reason = method3_check_and_bridge(session, agent_number)
    
    if success then
        freeswitch.consoleLog("info", "转接成功\n")
    else
        freeswitch.consoleLog("warning", "转接失败: " .. reason .. "\n")
        
        -- 根据失败原因处理
        if reason == "AGENT_OFFLINE" then
            session:streamFile("ivr/ivr-no_user_response.wav")
        end
        
        session:hangup()
    end
end
