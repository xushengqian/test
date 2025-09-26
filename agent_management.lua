--[[
FreeSWITCH 坐席状态管理脚本
功能：坐席登录/登出、状态切换、统计信息
作者：AI Assistant
版本：1.0
]]--

require "luasql.sqlite3"
require "json"

-- 配置
local config = {
    db_path = "/usr/local/freeswitch/db/agent_queue.db",
    agent_timeout = 300, -- 坐席超时时间（秒）
}

-- 数据库连接
local env = luasql.sqlite3()
local conn = nil

-- 初始化数据库
function init_database()
    conn = env:connect(config.db_path)
    return conn ~= nil
end

-- 坐席登录
function agent_login(session, extension, password, skill_groups)
    if not init_database() then
        return false, "数据库连接失败"
    end
    
    -- 验证坐席信息（这里简化处理，实际应该有密码验证）
    local query = string.format("SELECT * FROM agents WHERE extension = '%s'", extension)
    local cursor = conn:execute(query)
    
    if not cursor then
        conn:close()
        env:close()
        return false, "坐席不存在"
    end
    
    local row = cursor:fetch({}, "a")
    cursor:close()
    
    if not row then
        -- 创建新坐席
        local insert_query = string.format([[
            INSERT INTO agents (extension, name, status, skill_groups, last_call_time)
            VALUES ('%s', '坐席%s', 'available', '%s', datetime('now'))
        ]], extension, extension, skill_groups or '')
        
        conn:execute(insert_query)
        freeswitch.consoleLog("INFO", "创建新坐席: " .. extension)
    else
        -- 更新现有坐席状态
        local update_query = string.format([[
            UPDATE agents 
            SET status = 'available', skill_groups = '%s', last_call_time = datetime('now')
            WHERE extension = '%s'
        ]], skill_groups or row.skill_groups or '', extension)
        
        conn:execute(update_query)
        freeswitch.consoleLog("INFO", "坐席登录: " .. extension)
    end
    
    -- 设置坐席状态变量
    freeswitch.setGlobalVariable("agent_" .. extension .. "_status", "available")
    freeswitch.setGlobalVariable("agent_" .. extension .. "_login_time", os.time())
    
    conn:close()
    env:close()
    
    return true, "登录成功"
end

-- 坐席登出
function agent_logout(session, extension)
    if not init_database() then
        return false, "数据库连接失败"
    end
    
    -- 更新坐席状态为离线
    local query = string.format(
        "UPDATE agents SET status = 'offline' WHERE extension = '%s'",
        extension
    )
    
    local result = conn:execute(query)
    
    -- 清除全局变量
    freeswitch.setGlobalVariable("agent_" .. extension .. "_status", "offline")
    freeswitch.setGlobalVariable("agent_" .. extension .. "_login_time", "")
    
    conn:close()
    env:close()
    
    freeswitch.consoleLog("INFO", "坐席登出: " .. extension)
    return true, "登出成功"
end

-- 设置坐席状态
function set_agent_status(session, extension, status)
    if not init_database() then
        return false, "数据库连接失败"
    end
    
    -- 验证状态值
    local valid_statuses = {
        available = true,
        busy = true,
        break = true,
        offline = true,
        away = true
    }
    
    if not valid_statuses[status] then
        conn:close()
        env:close()
        return false, "无效的状态值"
    end
    
    -- 更新数据库
    local query = string.format(
        "UPDATE agents SET status = '%s' WHERE extension = '%s'",
        status, extension
    )
    
    conn:execute(query)
    
    -- 更新全局变量
    freeswitch.setGlobalVariable("agent_" .. extension .. "_status", status)
    
    conn:close()
    env:close()
    
    freeswitch.consoleLog("INFO", string.format("坐席 %s 状态更新为: %s", extension, status))
    return true, "状态更新成功"
end

-- 获取坐席状态
function get_agent_status(extension)
    if not init_database() then
        return nil, "数据库连接失败"
    end
    
    local query = string.format([[
        SELECT extension, name, status, skill_groups, 
               total_calls, last_call_time, created_at
        FROM agents WHERE extension = '%s'
    ]], extension)
    
    local cursor = conn:execute(query)
    if not cursor then
        conn:close()
        env:close()
        return nil, "查询失败"
    end
    
    local row = cursor:fetch({}, "a")
    cursor:close()
    conn:close()
    env:close()
    
    return row, "查询成功"
end

-- 获取所有坐席状态
function get_all_agents_status()
    if not init_database() then
        return nil, "数据库连接失败"
    end
    
    local query = [[
        SELECT extension, name, status, skill_groups, 
               total_calls, last_call_time, created_at
        FROM agents ORDER BY extension
    ]]
    
    local cursor = conn:execute(query)
    if not cursor then
        conn:close()
        env:close()
        return nil, "查询失败"
    end
    
    local agents = {}
    local row = cursor:fetch({}, "a")
    while row do
        table.insert(agents, row)
        row = cursor:fetch({}, "a")
    end
    
    cursor:close()
    conn:close()
    env:close()
    
    return agents, "查询成功"
end

-- 获取队列统计信息
function get_queue_stats(queue_name)
    if not init_database() then
        return nil, "数据库连接失败"
    end
    
    local query = string.format([[
        SELECT 
            COUNT(*) as total_calls,
            COUNT(CASE WHEN status = 'completed' THEN 1 END) as completed_calls,
            COUNT(CASE WHEN status = 'timeout' THEN 1 END) as timeout_calls,
            AVG(wait_time) as avg_wait_time,
            AVG(talk_time) as avg_talk_time
        FROM call_logs 
        WHERE queue_name = '%s' 
        AND DATE(start_time) = DATE('now')
    ]], queue_name or '')
    
    local cursor = conn:execute(query)
    if not cursor then
        conn:close()
        env:close()
        return nil, "查询失败"
    end
    
    local row = cursor:fetch({}, "a")
    cursor:close()
    conn:close()
    env:close()
    
    return row, "查询成功"
end

-- 处理DTMF按键（坐席状态切换）
function handle_agent_dtmf(session, extension)
    if not session:ready() then
        return
    end
    
    session:answer()
    session:sleep(500)
    
    while session:ready() do
        session:execute("playback", "ivr/ivr-please_enter_menu_option.wav")
        
        local digits = session:getDigits(1, "", 5000)
        
        if digits == "1" then
            -- 设置为可用
            set_agent_status(session, extension, "available")
            session:execute("playback", "ivr/ivr-you_are_now_available.wav")
            
        elseif digits == "2" then
            -- 设置为忙碌
            set_agent_status(session, extension, "busy")
            session:execute("playback", "ivr/ivr-you_are_now_busy.wav")
            
        elseif digits == "3" then
            -- 设置为休息
            set_agent_status(session, extension, "break")
            session:execute("playback", "ivr/ivr-you_are_now_on_break.wav")
            
        elseif digits == "9" then
            -- 登出
            agent_logout(session, extension)
            session:execute("playback", "ivr/ivr-goodbye.wav")
            break
            
        elseif digits == "0" then
            -- 播放当前状态
            local agent_info, msg = get_agent_status(extension)
            if agent_info then
                session:execute("playback", "ivr/ivr-your_current_status_is.wav")
                -- 这里可以添加TTS播报状态
            end
            
        elseif digits == "*" then
            -- 退出菜单
            break
        else
            session:execute("playback", "ivr/ivr-invalid_option.wav")
        end
    end
end

-- API接口处理函数
function handle_api_request(cmd, args)
    local action = args[1]
    local extension = args[2]
    
    if action == "login" then
        local password = args[3] or ""
        local skill_groups = args[4] or ""
        local success, msg = agent_login(nil, extension, password, skill_groups)
        return success and "OK" or "ERROR: " .. msg
        
    elseif action == "logout" then
        local success, msg = agent_logout(nil, extension)
        return success and "OK" or "ERROR: " .. msg
        
    elseif action == "status" then
        local new_status = args[3]
        if new_status then
            local success, msg = set_agent_status(nil, extension, new_status)
            return success and "OK" or "ERROR: " .. msg
        else
            local agent_info, msg = get_agent_status(extension)
            if agent_info then
                return json.encode(agent_info)
            else
                return "ERROR: " .. msg
            end
        end
        
    elseif action == "list" then
        local agents, msg = get_all_agents_status()
        if agents then
            return json.encode(agents)
        else
            return "ERROR: " .. msg
        end
        
    elseif action == "stats" then
        local queue_name = extension -- 这里复用extension参数作为队列名
        local stats, msg = get_queue_stats(queue_name)
        if stats then
            return json.encode(stats)
        else
            return "ERROR: " .. msg
        end
    end
    
    return "ERROR: 未知操作"
end

-- 主函数
function main(session, action, extension, param1, param2)
    if not session then
        -- API调用模式
        return handle_api_request(action, {extension, param1, param2})
    end
    
    -- 会话模式
    action = action or "menu"
    extension = extension or session:getVariable("caller_id_number")
    
    if action == "login" then
        local success, msg = agent_login(session, extension, param1, param2)
        if success then
            session:execute("playback", "ivr/ivr-login_successful.wav")
        else
            session:execute("playback", "ivr/ivr-login_failed.wav")
        end
        
    elseif action == "logout" then
        local success, msg = agent_logout(session, extension)
        if success then
            session:execute("playback", "ivr/ivr-goodbye.wav")
        else
            session:execute("playback", "ivr/ivr-system_error.wav")
        end
        
    elseif action == "menu" then
        handle_agent_dtmf(session, extension)
        
    else
        session:execute("playback", "ivr/ivr-invalid_option.wav")
    end
end

-- 如果直接运行脚本
if session then
    main(session, argv[1], argv[2], argv[3], argv[4])
end