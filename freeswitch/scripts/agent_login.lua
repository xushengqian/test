-- FreeSWITCH Lua脚本: 坐席登录/签入
-- 处理坐席登录、状态管理和权限验证

-- 坐席配置
local agent_config = {
    -- 坐席认证信息
    agents = {
        ["agent_001"] = {password = "1234", extension = "1001", name = "张三"},
        ["agent_002"] = {password = "1234", extension = "1002", name = "李四"},
        ["agent_003"] = {password = "1234", extension = "1003", name = "王五"},
        ["agent_004"] = {password = "1234", extension = "1004", name = "赵六"},
        ["agent_005"] = {password = "1234", extension = "1005", name = "钱七"}
    },
    
    -- 坐席状态
    status = {
        LOGGED_OUT = 0,
        LOGGED_IN = 1,
        AVAILABLE = 2,
        ON_CALL = 3,
        WRAP_UP = 4,
        ON_BREAK = 5
    }
}

-- 获取参数
local agent_id = argv[1]
if not agent_id then
    freeswitch.consoleLog("err", "[AgentLogin] No agent ID provided\n")
    return
end

-- 日志函数
local function log_info(message)
    freeswitch.consoleLog("info", "[AgentLogin] " .. message .. "\n")
end

local function log_error(message)
    freeswitch.consoleLog("err", "[AgentLogin] " .. message .. "\n")
end

-- 验证坐席身份
local function authenticate_agent(agent_id, password)
    local agent = agent_config.agents[agent_id]
    
    if not agent then
        log_error("Agent not found: " .. agent_id)
        return false
    end
    
    -- 如果提供了密码，进行验证
    if password then
        if agent.password ~= password then
            log_error("Authentication failed for agent: " .. agent_id)
            return false
        end
    end
    
    log_info("Agent authenticated: " .. agent_id)
    return true
end

-- 获取坐席当前状态
local function get_agent_status(agent_id)
    local api = freeswitch.API()
    local status = api:executeString("global_getvar agent_status_" .. agent_id)
    
    if status and status ~= "" then
        return tonumber(status) or agent_config.status.LOGGED_OUT
    end
    
    return agent_config.status.LOGGED_OUT
end

-- 设置坐席状态
local function set_agent_status(agent_id, status)
    local api = freeswitch.API()
    api:executeString("global_setvar agent_status_" .. agent_id .. "=" .. status)
    
    -- 记录状态变更时间
    api:executeString("global_setvar agent_status_time_" .. agent_id .. "=" .. os.time())
    
    log_info(string.format("Agent %s status changed to: %d", agent_id, status))
end

-- 加入坐席队列
local function join_agent_queue(agent_id)
    local agent = agent_config.agents[agent_id]
    if not agent then
        return false
    end
    
    -- 使用FIFO添加坐席到队列
    local api = freeswitch.API()
    local fifo_member = string.format("user/%s@${domain_name}", agent.extension)
    
    -- 添加到不同技能队列
    local queues = {"general_queue", "sales_queue", "support_queue"}
    
    for _, queue in ipairs(queues) do
        local cmd = string.format("fifo_member add %s %s", queue, fifo_member)
        api:executeString(cmd)
        log_info(string.format("Agent %s added to queue: %s", agent_id, queue))
    end
    
    return true
end

-- 从坐席队列移除
local function leave_agent_queue(agent_id)
    local agent = agent_config.agents[agent_id]
    if not agent then
        return false
    end
    
    local api = freeswitch.API()
    local fifo_member = string.format("user/%s@${domain_name}", agent.extension)
    
    -- 从所有队列移除
    local queues = {"general_queue", "sales_queue", "support_queue"}
    
    for _, queue in ipairs(queues) do
        local cmd = string.format("fifo_member del %s %s", queue, fifo_member)
        api:executeString(cmd)
        log_info(string.format("Agent %s removed from queue: %s", agent_id, queue))
    end
    
    return true
end

-- 记录登录事件
local function log_login_event(agent_id, action)
    local event_data = {
        agent_id = agent_id,
        action = action,
        timestamp = os.date("%Y-%m-%d %H:%M:%S"),
        ip_address = session and session:getVariable("network_addr") or "unknown"
    }
    
    -- 发送自定义事件
    if session then
        local event = freeswitch.Event("CUSTOM", "agent::login")
        event:addHeader("Agent-ID", agent_id)
        event:addHeader("Action", action)
        event:addHeader("Timestamp", event_data.timestamp)
        event:fire()
    end
    
    log_info(string.format("Login event: agent=%s, action=%s", agent_id, action))
    
    -- 可以保存到数据库
    -- save_login_event(event_data)
end

-- 播放登录提示音
local function play_login_prompt(success)
    if not session or not session:ready() then
        return
    end
    
    if success then
        session:execute("playback", "ivr/ivr-you_are_now_logged_in.wav")
    else
        session:execute("playback", "ivr/ivr-login_failed.wav")
    end
end

-- 处理坐席登录
local function agent_login(agent_id)
    -- 检查是否已登录
    local current_status = get_agent_status(agent_id)
    
    if current_status > agent_config.status.LOGGED_OUT then
        log_info("Agent already logged in: " .. agent_id)
        play_login_prompt(false)
        return false
    end
    
    -- 验证坐席
    if not authenticate_agent(agent_id, nil) then
        play_login_prompt(false)
        return false
    end
    
    -- 设置登录状态
    set_agent_status(agent_id, agent_config.status.AVAILABLE)
    
    -- 加入队列
    join_agent_queue(agent_id)
    
    -- 记录登录事件
    log_login_event(agent_id, "login")
    
    -- 设置坐席信息到会话变量
    if session and session:ready() then
        local agent = agent_config.agents[agent_id]
        session:setVariable("agent_id", agent_id)
        session:setVariable("agent_name", agent.name)
        session:setVariable("agent_extension", agent.extension)
        session:setVariable("agent_login_time", os.date("%Y-%m-%d %H:%M:%S"))
    end
    
    play_login_prompt(true)
    log_info("Agent login successful: " .. agent_id)
    
    return true
end

-- 处理坐席登出
local function agent_logout(agent_id)
    -- 检查当前状态
    local current_status = get_agent_status(agent_id)
    
    if current_status == agent_config.status.LOGGED_OUT then
        log_info("Agent already logged out: " .. agent_id)
        return false
    end
    
    -- 检查是否在通话中
    if current_status == agent_config.status.ON_CALL then
        log_error("Cannot logout while on call: " .. agent_id)
        if session and session:ready() then
            session:execute("playback", "ivr/ivr-please_end_call_first.wav")
        end
        return false
    end
    
    -- 从队列移除
    leave_agent_queue(agent_id)
    
    -- 设置登出状态
    set_agent_status(agent_id, agent_config.status.LOGGED_OUT)
    
    -- 记录登出事件
    log_login_event(agent_id, "logout")
    
    if session and session:ready() then
        session:execute("playback", "ivr/ivr-you_are_now_logged_out.wav")
    end
    
    log_info("Agent logout successful: " .. agent_id)
    
    return true
end

-- 切换坐席状态
local function toggle_agent_status(agent_id)
    local current_status = get_agent_status(agent_id)
    
    if current_status == agent_config.status.LOGGED_OUT then
        -- 登录
        agent_login(agent_id)
    else
        -- 登出
        agent_logout(agent_id)
    end
end

-- 主函数
local function main()
    log_info("Processing agent login for: " .. agent_id)
    
    -- 默认执行登录操作
    agent_login(agent_id)
end

-- 执行主函数
main()