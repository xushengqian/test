-- 查找可用人工坐席脚本
local json = require("json")
local http = require("socket.http")

-- 获取会话信息
local session = session
local customer_phone = session:getVariable("customer_phone")
local call_uuid = session:get_uuid()
local transfer_reason = session:getVariable("transfer_reason") or "unknown"

-- API服务器配置
local api_server = session:getVariable("api_server") or "http://localhost:8000"

-- 日志函数
local function log(level, message)
    freeswitch.consoleLog(level, "[AgentFinder] " .. message .. "\n")
end

-- HTTP请求函数
local function http_request(url, data)
    local response_body = {}
    local result, status = http.request{
        url = url,
        method = "POST",
        headers = {
            ["Content-Type"] = "application/json",
            ["Content-Length"] = string.len(data)
        },
        source = ltn12.source.string(data),
        sink = ltn12.sink.table(response_body)
    }
    
    if status == 200 then
        return json.decode(table.concat(response_body))
    else
        log("ERROR", "HTTP request failed: " .. tostring(status))
        return nil
    end
end

-- 查找可用坐席
local function find_available_agent()
    local data = json.encode({
        customer_phone = customer_phone,
        call_uuid = call_uuid,
        transfer_reason = transfer_reason,
        priority = "normal"
    })
    
    local response = http_request(api_server .. "/api/agent/find", data)
    return response
end

-- 通知坐席有新通话
local function notify_agent(agent_info)
    local data = json.encode({
        agent_id = agent_info.agent_id,
        customer_phone = customer_phone,
        call_uuid = call_uuid,
        transfer_reason = transfer_reason,
        customer_info = agent_info.customer_info or {}
    })
    
    http_request(api_server .. "/api/agent/notify", data)
end

-- 设置通话变量
local function set_call_variables(agent_info)
    session:setVariable("assigned_agent_id", agent_info.agent_id)
    session:setVariable("agent_name", agent_info.agent_name or "客服")
    session:setVariable("agent_extension", agent_info.extension or "")
    session:setVariable("queue_position", agent_info.queue_position or "1")
    session:setVariable("estimated_wait_time", agent_info.estimated_wait_time or "30")
end

-- 播放等待音乐或提示
local function play_wait_message(wait_time)
    if wait_time and tonumber(wait_time) > 0 then
        local message = string.format("当前排队人数较多，预计等待时间%d秒，请耐心等待", wait_time)
        session:speak("tts_commandline|" .. message)
    else
        session:speak("tts_commandline|正在为您分配客服，请稍等")
    end
end

-- 主处理函数
local function main()
    log("INFO", "Finding available agent for customer: " .. customer_phone)
    
    -- 查找可用坐席
    local agent_info = find_available_agent()
    
    if not agent_info then
        log("ERROR", "Failed to get agent information")
        session:speak("tts_commandline|抱歉，暂时无法为您分配客服，请稍后再试")
        session:hangup()
        return
    end
    
    if agent_info.status == "available" then
        -- 有可用坐席
        log("INFO", "Found available agent: " .. (agent_info.agent_name or "unknown"))
        
        set_call_variables(agent_info)
        notify_agent(agent_info)
        
        session:speak("tts_commandline|已为您分配客服" .. (agent_info.agent_name or "") .. "，正在接通")
        
    elseif agent_info.status == "queue" then
        -- 需要排队等待
        log("INFO", "Customer queued, position: " .. (agent_info.queue_position or "unknown"))
        
        set_call_variables(agent_info)
        play_wait_message(agent_info.estimated_wait_time)
        
        -- 设置排队回调
        session:setVariable("queue_callback", "true")
        
    else
        -- 没有可用坐席
        log("WARN", "No agents available")
        session:speak("tts_commandline|抱歉，当前所有客服都在忙碌中，请稍后再试或留下您的联系方式")
        
        -- 可以在这里添加留言功能
        session:setVariable("no_agents_available", "true")
    end
end

-- 错误处理
local function safe_main()
    local success, error = pcall(main)
    if not success then
        log("ERROR", "Agent finder error: " .. tostring(error))
        session:speak("tts_commandline|系统繁忙，请稍后再试")
        session:hangup()
    end
end

-- 程序入口
if session:ready() then
    safe_main()
else
    log("ERROR", "Session not ready")
end