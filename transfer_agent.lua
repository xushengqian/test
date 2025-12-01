-- FreeSWITCH Lua 脚本：转接人工坐席
-- 判断转接是否成功，处理坐席未登录的情况

-- 转接人工坐席函数
function transfer_to_agent(session, agent_extension)
    local result = {}
    
    -- 设置转接前的变量
    session:setVariable("transfer_attempted", "true")
    session:setVariable("transfer_time", tostring(os.time()))
    
    -- 方法1：使用 bridge 转接，通过事件监听判断
    -- 先检查坐席是否在线（通过 sofia_contact 或注册状态）
    local agent_status = check_agent_status(agent_extension)
    
    if not agent_status.online then
        freeswitch.consoleLog("WARNING", "坐席 " .. agent_extension .. " 未登录或不在线")
        result.success = false
        result.reason = "agent_offline"
        result.message = "坐席未登录，无法转接"
        return result
    end
    
    -- 执行转接
    freeswitch.consoleLog("INFO", "开始转接到坐席: " .. agent_extension)
    
    -- 设置转接超时时间（秒）
    session:setVariable("call_timeout", "30")
    
    -- 设置挂断原因监听
    session:setVariable("hangup_hook", "transfer_hangup_handler")
    
    -- 方法1：使用 originate 转接（推荐，可以更好地控制）
    local transfer_result = session:execute("bridge", 
        "{ignore_early_media=true,hangup_after_bridge=true}user/" .. agent_extension)
    
    -- 获取挂断原因
    local hangup_cause = session:getVariable("hangup_cause")
    local bridge_hangup_cause = session:getVariable("bridge_hangup_cause")
    
    freeswitch.consoleLog("INFO", "转接结果 - hangup_cause: " .. (hangup_cause or "nil"))
    freeswitch.consoleLog("INFO", "转接结果 - bridge_hangup_cause: " .. (bridge_hangup_cause or "nil"))
    
    -- 判断转接是否成功
    -- 成功的情况：
    -- 1. bridge_hangup_cause 为 NORMAL_CLEARING (16) - 正常挂断
    -- 2. bridge_hangup_cause 为 NORMAL_CLEARING 且通话时长 > 0
    
    local success = false
    local call_duration = session:getVariable("billsec") or "0"
    
    if bridge_hangup_cause then
        local cause_code = tonumber(bridge_hangup_cause)
        
        -- NORMAL_CLEARING (16) 表示正常通话后挂断
        if cause_code == 16 and tonumber(call_duration) > 0 then
            success = true
            result.success = true
            result.reason = "normal_clearing"
            result.message = "转接成功，通话正常结束"
            result.duration = call_duration
        -- USER_BUSY (17) 或 NO_ANSWER (18) 表示坐席未接听
        elseif cause_code == 17 then
            result.success = false
            result.reason = "user_busy"
            result.message = "坐席忙线"
        elseif cause_code == 18 then
            result.success = false
            result.reason = "no_answer"
            result.message = "坐席未接听"
        -- NO_USER_RESPONSE (19) 或其他错误
        else
            result.success = false
            result.reason = "transfer_failed"
            result.message = "转接失败，原因码: " .. bridge_hangup_cause
        end
    else
        -- 如果没有 bridge_hangup_cause，检查其他原因
        if hangup_cause then
            local cause_code = tonumber(hangup_cause)
            if cause_code == 16 and tonumber(call_duration) > 0 then
                success = true
                result.success = true
            else
                result.success = false
                result.reason = "hangup_cause_" .. hangup_cause
            end
        else
            result.success = false
            result.reason = "unknown"
            result.message = "无法确定转接状态"
        end
    end
    
    result.hangup_cause = hangup_cause
    result.bridge_hangup_cause = bridge_hangup_cause
    result.call_duration = call_duration
    
    return result
end

-- 检查坐席状态
function check_agent_status(agent_extension)
    local result = { online = false, registered = false }
    
    -- 方法1：通过 sofia_contact 检查注册状态
    local api = freeswitch.API()
    local cmd = "sofia_contact " .. agent_extension
    local contact = api:executeString(cmd)
    
    if contact and contact ~= "" and contact ~= "NOT_FOUND" then
        result.online = true
        result.registered = true
        result.contact = contact
        freeswitch.consoleLog("INFO", "坐席 " .. agent_extension .. " 在线: " .. contact)
    else
        -- 方法2：通过注册表检查
        local reg_cmd = "sofia status profile internal reg " .. agent_extension
        local reg_status = api:executeString(reg_cmd)
        
        if reg_status and reg_status:match("Reg") then
            result.online = true
            result.registered = true
            freeswitch.consoleLog("INFO", "坐席 " .. agent_extension .. " 已注册")
        else
            freeswitch.consoleLog("WARNING", "坐席 " .. agent_extension .. " 未注册")
        end
    end
    
    return result
end

-- 使用示例：在主脚本中调用
function main_transfer_handler(session)
    local agent_extension = session:getVariable("agent_extension") or "1001"
    
    -- 先检查坐席状态
    local agent_status = check_agent_status(agent_extension)
    
    if not agent_status.online then
        -- 坐席未登录，播放提示音并挂断
        session:streamFile("/path/to/agent_offline.wav")
        session:hangup("NO_USER_RESPONSE")
        return
    end
    
    -- 执行转接
    local transfer_result = transfer_to_agent(session, agent_extension)
    
    -- 根据转接结果处理
    if transfer_result.success then
        freeswitch.consoleLog("INFO", "转接成功，通话时长: " .. transfer_result.duration .. " 秒")
        -- 转接成功，正常结束
    else
        freeswitch.consoleLog("WARNING", "转接失败: " .. transfer_result.message)
        
        -- 根据失败原因处理
        if transfer_result.reason == "agent_offline" then
            session:streamFile("/path/to/agent_offline.wav")
        elseif transfer_result.reason == "user_busy" then
            session:streamFile("/path/to/agent_busy.wav")
        elseif transfer_result.reason == "no_answer" then
            session:streamFile("/path/to/no_answer.wav")
        end
        
        -- 可以尝试转接到其他坐席或队列
        -- transfer_to_queue(session, "support_queue")
    end
    
    session:hangup()
end

-- 方法2：使用事件监听判断转接状态（更可靠）
function transfer_with_event_monitoring(session, agent_extension)
    -- 设置事件监听
    local event_sub = freeswitch.Event("CHANNEL_BRIDGE")
    
    -- 执行转接
    local bridge_result = session:execute("bridge", 
        "{ignore_early_media=true,hangup_after_bridge=true}user/" .. agent_extension)
    
    -- 监听桥接事件
    local event = session:waitForAnswer(30000) -- 等待30秒
    
    if event then
        local event_name = event:getHeader("Event-Name")
        if event_name == "CHANNEL_BRIDGE" then
            -- 桥接成功
            freeswitch.consoleLog("INFO", "桥接成功建立")
            return { success = true }
        end
    end
    
    -- 检查挂断原因
    local hangup_cause = session:getVariable("bridge_hangup_cause")
    return { success = false, hangup_cause = hangup_cause }
end

-- 方法3：使用 originate 转接（推荐用于更精确的控制）
function transfer_with_originate(session, agent_extension)
    local caller_id = session:getVariable("caller_id_number")
    local uuid = session:getVariable("uuid")
    
    -- 创建新的呼叫到坐席
    local api = freeswitch.API()
    local originate_cmd = string.format(
        "originate {origination_uuid=%s,origination_caller_id_number=%s}user/%s &park",
        uuid, caller_id, agent_extension
    )
    
    local result = api:executeString(originate_cmd)
    
    if result and result:match("+OK") then
        -- 等待坐席接听
        session:execute("park")
        
        -- 检查是否成功桥接
        local bridge_state = session:getVariable("bridge_state")
        if bridge_state == "CS_CONSUME_MEDIA" then
            return { success = true }
        end
    end
    
    return { success = false }
end

-- 导出函数供其他脚本使用
return {
    transfer_to_agent = transfer_to_agent,
    check_agent_status = check_agent_status,
    transfer_with_event_monitoring = transfer_with_event_monitoring,
    transfer_with_originate = transfer_with_originate
}
