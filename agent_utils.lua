--[[
    坐席工具函数库
    提供坐席状态检查、转接等通用功能
]]--

local AgentUtils = {}

-- API对象（可复用）
local api = freeswitch.API()

-- 日志函数
function AgentUtils.log(level, msg)
    freeswitch.consoleLog(level, "[AgentUtils] " .. msg .. "\n")
end

-- 1. 检查坐席是否注册（在线）
function AgentUtils.isRegistered(agent_number)
    local result = api:executeString("sofia_contact " .. agent_number)
    
    if not result or result == "" then
        return false
    end
    
    -- 检查是否包含错误信息
    if string.find(result, "error") or 
       string.find(result, "not found") or
       string.find(result, "sofia/") == nil then
        return false
    end
    
    return true, result  -- 返回true和联系地址
end

-- 2. 检查坐席是否在通话中
function AgentUtils.isBusy(agent_number)
    -- 查询该坐席的活动通道
    local result = api:executeString("show channels like " .. agent_number .. " as xml")
    
    if not result or result == "" then
        return false
    end
    
    -- 解析XML结果，查找活动通道
    -- 简单方法：检查是否包含通道信息
    if string.find(result, "<uuid>") then
        return true
    end
    
    return false
end

-- 3. 获取坐席状态（综合）
function AgentUtils.getStatus(agent_number)
    local registered = AgentUtils.isRegistered(agent_number)
    
    if not registered then
        return "OFFLINE"  -- 未注册/离线
    end
    
    local busy = AgentUtils.isBusy(agent_number)
    
    if busy then
        return "BUSY"  -- 忙线中
    else
        return "IDLE"  -- 空闲
    end
end

-- 4. 转接到坐席（带状态检查）
function AgentUtils.transfer(session, agent_number, timeout)
    timeout = timeout or 30
    
    -- 检查session是否有效
    if not session or not session:ready() then
        AgentUtils.log("err", "Session not ready")
        return false, "SESSION_ERROR"
    end
    
    -- 检查坐席状态
    local status = AgentUtils.getStatus(agent_number)
    
    if status == "OFFLINE" then
        AgentUtils.log("warning", "坐席 " .. agent_number .. " 离线")
        return false, "AGENT_OFFLINE"
    end
    
    if status == "BUSY" then
        AgentUtils.log("warning", "坐席 " .. agent_number .. " 忙线")
        -- 可以选择是否继续尝试转接
        -- return false, "AGENT_BUSY"
    end
    
    -- 设置转接参数
    session:setVariable("call_timeout", timeout)
    session:setVariable("continue_on_fail", "true")
    session:setVariable("hangup_after_bridge", "true")
    
    -- 执行转接
    AgentUtils.log("info", "正在转接到坐席 " .. agent_number)
    session:execute("bridge", "user/" .. agent_number)
    
    -- 获取转接结果
    local hangup_cause = session:getVariable("bridge_hangup_cause") or
                         session:getVariable("originate_disposition")
    
    AgentUtils.log("info", "转接结果: " .. tostring(hangup_cause))
    
    -- 判断是否成功
    if hangup_cause == "SUCCESS" or hangup_cause == "NORMAL_CLEARING" then
        return true, "SUCCESS"
    else
        return false, hangup_cause
    end
end

-- 5. 智能转接（自动选择可用坐席）
function AgentUtils.smartTransfer(session, agent_list, timeout)
    timeout = timeout or 30
    
    if not agent_list or #agent_list == 0 then
        AgentUtils.log("err", "坐席列表为空")
        return false, "NO_AGENTS"
    end
    
    -- 遍历坐席列表
    for i, agent_number in ipairs(agent_list) do
        AgentUtils.log("info", "尝试坐席 [" .. i .. "/" .. #agent_list .. "]: " .. agent_number)
        
        local status = AgentUtils.getStatus(agent_number)
        
        if status == "IDLE" then
            -- 尝试转接到该坐席
            local success, result = AgentUtils.transfer(session, agent_number, timeout)
            
            if success then
                AgentUtils.log("info", "成功转接到坐席: " .. agent_number)
                return true, agent_number
            else
                AgentUtils.log("warning", "坐席 " .. agent_number .. " 转接失败: " .. result)
            end
        else
            AgentUtils.log("info", "坐席 " .. agent_number .. " 状态: " .. status .. "，跳过")
        end
        
        -- 检查session是否仍然有效
        if not session:ready() then
            AgentUtils.log("warning", "Session已结束")
            break
        end
    end
    
    return false, "ALL_AGENTS_UNAVAILABLE"
end

-- 6. 获取所有可用坐席列表
function AgentUtils.getAvailableAgents(agent_list)
    local available = {}
    
    for _, agent_number in ipairs(agent_list) do
        local status = AgentUtils.getStatus(agent_number)
        if status == "IDLE" then
            table.insert(available, agent_number)
        end
    end
    
    return available
end

-- 7. 播放等待音乐给主叫
function AgentUtils.playHoldMusic(session, music_file)
    music_file = music_file or "$${hold_music}"
    
    if session and session:ready() then
        session:execute("playback", music_file)
    end
end

-- 8. 转接前的语音提示
function AgentUtils.playTransferPrompt(session, prompt_file)
    prompt_file = prompt_file or "ivr/ivr-please_hold_while_party_contacted.wav"
    
    if session and session:ready() then
        session:streamFile(prompt_file)
    end
end

-- 9. 转接失败的语音提示
function AgentUtils.playFailurePrompt(session, reason)
    if not session or not session:ready() then
        return
    end
    
    local prompt_map = {
        AGENT_OFFLINE = "ivr/ivr-no_user_response.wav",
        NO_ANSWER = "ivr/ivr-no_user_response.wav",
        AGENT_BUSY = "ivr/ivr-user_busy.wav",
        USER_BUSY = "ivr/ivr-user_busy.wav",
        CALL_REJECTED = "ivr/ivr-call_rejected.wav",
        ALL_AGENTS_UNAVAILABLE = "ivr/ivr-no_user_response.wav"
    }
    
    local prompt_file = prompt_map[reason] or "ivr/ivr-call_cannot_be_completed_as_dialed.wav"
    session:streamFile(prompt_file)
end

-- 10. 记录转接日志到数据库（示例）
function AgentUtils.logToDatabase(call_uuid, caller_id, agent_number, result, reason)
    -- 这里需要配置数据库连接
    -- 示例使用SQLite，可以改为MySQL/PostgreSQL
    
    local dbh = freeswitch.Dbh("sqlite://freeswitch.db")
    
    if dbh:connected() then
        local sql = string.format([[
            INSERT INTO transfer_logs 
            (call_uuid, caller_id, agent_number, result, reason, created_at) 
            VALUES ('%s', '%s', '%s', '%s', '%s', datetime('now'))
        ]], 
        call_uuid, 
        caller_id or "unknown", 
        agent_number or "unknown",
        result and "SUCCESS" or "FAILED",
        reason or "UNKNOWN"
        )
        
        local success = dbh:query(sql)
        
        if not success then
            AgentUtils.log("err", "日志写入数据库失败")
        end
        
        dbh:release()
    else
        AgentUtils.log("err", "数据库连接失败")
    end
end

-- 11. 使用callcenter模块检查坐席状态（如果启用）
function AgentUtils.getCallcenterStatus(agent_id)
    local result = api:executeString("callcenter_config agent get status " .. agent_id)
    
    if result and result ~= "" then
        -- 解析结果
        if string.find(result, "Logged Out") then
            return "LOGGED_OUT"
        elseif string.find(result, "Available") then
            return "AVAILABLE"
        elseif string.find(result, "On Break") then
            return "ON_BREAK"
        elseif string.find(result, "In a queue call") then
            return "IN_CALL"
        end
    end
    
    return "UNKNOWN"
end

return AgentUtils
