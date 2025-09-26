-- FreeSWITCH Lua Script for Agent Management
-- 坐席管理脚本（登录/登出/状态管理）

-- 获取传入参数
local action = argv[1] or "status"  -- login, logout, break, available, status
local agent_id = argv[2] or session:getVariable("caller_id_number")
local extension = argv[3] or agent_id
local queue_name = argv[4] or "support_queue"

-- 初始化会话（如果存在）
if session then
    session:answer()
end

-- 函数：坐席登录
function agent_login(agent_id, extension)
    freeswitch.consoleLog("info", "坐席登录: " .. agent_id .. " 分机: " .. extension .. "\n")
    
    -- 设置坐席信息
    freeswitch.setGlobalVariable("agent:" .. agent_id .. ":status", "available")
    freeswitch.setGlobalVariable("agent:" .. agent_id .. ":extension", extension)
    freeswitch.setGlobalVariable("agent:" .. agent_id .. ":login_time", os.time())
    
    -- 添加到队列坐席列表
    local current_agents = freeswitch.getGlobalVariable("queue:" .. queue_name .. ":agents") or ""
    local agent_list = {}
    
    -- 解析现有坐席列表
    for agent in string.gmatch(current_agents, "([^,]+)") do
        if agent ~= agent_id then
            table.insert(agent_list, agent)
        end
    end
    
    -- 添加新坐席
    table.insert(agent_list, agent_id)
    
    -- 更新坐席列表
    freeswitch.setGlobalVariable("queue:" .. queue_name .. ":agents", table.concat(agent_list, ","))
    
    if session then
        session:execute("playback", "ivr/ivr-you_are_now_logged_in.wav")
    end
    
    return true
end

-- 函数：坐席登出
function agent_logout(agent_id)
    freeswitch.consoleLog("info", "坐席登出: " .. agent_id .. "\n")
    
    -- 设置坐席状态为离线
    freeswitch.setGlobalVariable("agent:" .. agent_id .. ":status", "offline")
    freeswitch.setGlobalVariable("agent:" .. agent_id .. ":logout_time", os.time())
    
    -- 从队列坐席列表中移除
    local current_agents = freeswitch.getGlobalVariable("queue:" .. queue_name .. ":agents") or ""
    local agent_list = {}
    
    for agent in string.gmatch(current_agents, "([^,]+)") do
        if agent ~= agent_id then
            table.insert(agent_list, agent)
        end
    end
    
    -- 更新坐席列表
    freeswitch.setGlobalVariable("queue:" .. queue_name .. ":agents", table.concat(agent_list, ","))
    
    if session then
        session:execute("playback", "ivr/ivr-you_are_now_logged_out.wav")
    end
    
    return true
end

-- 函数：设置坐席休息状态
function agent_break(agent_id, break_reason)
    freeswitch.consoleLog("info", "坐席休息: " .. agent_id .. " 原因: " .. (break_reason or "未指定") .. "\n")
    
    freeswitch.setGlobalVariable("agent:" .. agent_id .. ":status", "break")
    freeswitch.setGlobalVariable("agent:" .. agent_id .. ":break_time", os.time())
    freeswitch.setGlobalVariable("agent:" .. agent_id .. ":break_reason", break_reason or "")
    
    if session then
        session:execute("playback", "ivr/ivr-on_break.wav")
    end
    
    return true
end

-- 函数：设置坐席可用状态
function agent_available(agent_id)
    freeswitch.consoleLog("info", "坐席就绪: " .. agent_id .. "\n")
    
    local status = freeswitch.getGlobalVariable("agent:" .. agent_id .. ":status")
    
    if status == "offline" then
        if session then
            session:execute("playback", "ivr/ivr-please_login_first.wav")
        end
        return false
    end
    
    freeswitch.setGlobalVariable("agent:" .. agent_id .. ":status", "available")
    freeswitch.setGlobalVariable("agent:" .. agent_id .. ":available_time", os.time())
    
    if session then
        session:execute("playback", "ivr/ivr-you_are_now_available.wav")
    end
    
    return true
end

-- 函数：获取坐席状态
function agent_status(agent_id)
    local status = freeswitch.getGlobalVariable("agent:" .. agent_id .. ":status") or "offline"
    local extension = freeswitch.getGlobalVariable("agent:" .. agent_id .. ":extension") or "未设置"
    local login_time = freeswitch.getGlobalVariable("agent:" .. agent_id .. ":login_time") or "0"
    local current_call = freeswitch.getGlobalVariable("agent:" .. agent_id .. ":current_call") or "无"
    
    freeswitch.consoleLog("info", "坐席状态查询:\n")
    freeswitch.consoleLog("info", "  坐席ID: " .. agent_id .. "\n")
    freeswitch.consoleLog("info", "  状态: " .. status .. "\n")
    freeswitch.consoleLog("info", "  分机: " .. extension .. "\n")
    freeswitch.consoleLog("info", "  登录时间: " .. login_time .. "\n")
    freeswitch.consoleLog("info", "  当前通话: " .. current_call .. "\n")
    
    if session then
        session:execute("playback", "ivr/ivr-your_status_is.wav")
        
        if status == "available" then
            session:execute("playback", "ivr/ivr-available.wav")
        elseif status == "busy" then
            session:execute("playback", "ivr/ivr-busy.wav")
        elseif status == "break" then
            session:execute("playback", "ivr/ivr-on_break.wav")
        else
            session:execute("playback", "ivr/ivr-offline.wav")
        end
    end
    
    return {
        agent_id = agent_id,
        status = status,
        extension = extension,
        login_time = login_time,
        current_call = current_call
    }
end

-- 函数：获取所有坐席状态
function get_all_agents_status()
    local agents_info = {}
    local current_agents = freeswitch.getGlobalVariable("queue:" .. queue_name .. ":agents") or ""
    
    freeswitch.consoleLog("info", "=== 所有坐席状态 ===\n")
    
    for agent in string.gmatch(current_agents, "([^,]+)") do
        local info = agent_status(agent)
        table.insert(agents_info, info)
    end
    
    return agents_info
end

-- 主程序执行
if action == "login" then
    agent_login(agent_id, extension)
elseif action == "logout" then
    agent_logout(agent_id)
elseif action == "break" then
    agent_break(agent_id, argv[5])
elseif action == "available" then
    agent_available(agent_id)
elseif action == "status" then
    agent_status(agent_id)
elseif action == "status_all" then
    get_all_agents_status()
else
    freeswitch.consoleLog("warning", "未知操作: " .. action .. "\n")
    if session then
        session:execute("playback", "ivr/ivr-invalid_option.wav")
    end
end

-- 清理
if session and session:ready() then
    session:hangup()
end