-- FreeSWITCH Lua Script for Agent Queue Management
-- 人工坐席队列管理脚本

-- 配置参数
local queue_name = "support_queue"
local max_wait_time = 300  -- 最大等待时间（秒）
local agent_ring_timeout = 20  -- 坐席振铃超时（秒）
local music_on_hold = "local_stream://moh"  -- 等待音乐

-- 数据库配置（可选，用于持久化存储）
local use_database = false  -- 设置为true启用数据库
local db_dsn = "freeswitch_db"  -- ODBC DSN名称

-- 全局变量存储（使用FreeSWITCH的全局变量）
-- 坐席状态：available, busy, offline, break
-- 队列状态存储在: queue:<queue_name>:agents
-- 坐席状态存储在: agent:<agent_id>:status

-- 初始化会话
session:answer()
session:execute("playback", "ivr/ivr-hold_connect_call.wav")

-- 获取来电信息
local caller_id = session:getVariable("caller_id_number") or "unknown"
local caller_name = session:getVariable("caller_id_name") or "Unknown Caller"
local uuid = session:getVariable("uuid")

freeswitch.consoleLog("info", "来电加入队列: " .. caller_id .. " (" .. caller_name .. ")\n")

-- 函数：获取可用坐席列表
function get_available_agents()
    local agents = {}
    
    -- 从全局变量获取坐席列表
    local agent_list = freeswitch.getGlobalVariable("queue:" .. queue_name .. ":agents")
    
    if agent_list then
        -- 解析坐席列表（格式: agent1,agent2,agent3）
        for agent in string.gmatch(agent_list, "([^,]+)") do
            local status = freeswitch.getGlobalVariable("agent:" .. agent .. ":status")
            if status == "available" then
                table.insert(agents, agent)
                freeswitch.consoleLog("info", "可用坐席: " .. agent .. "\n")
            end
        end
    end
    
    return agents
end

-- 函数：尝试连接到坐席
function try_agent(agent_id)
    freeswitch.consoleLog("info", "尝试连接坐席: " .. agent_id .. "\n")
    
    -- 设置坐席状态为忙碌
    freeswitch.setGlobalVariable("agent:" .. agent_id .. ":status", "busy")
    
    -- 获取坐席的分机号或SIP地址
    local agent_extension = freeswitch.getGlobalVariable("agent:" .. agent_id .. ":extension")
    if not agent_extension then
        agent_extension = agent_id  -- 如果没有设置，使用agent_id作为分机号
    end
    
    -- 构建呼叫字符串
    local dial_string = "user/" .. agent_extension
    
    -- 设置呼叫参数
    session:execute("set", "ringback=%(2000,4000,440,480)")
    session:execute("set", "call_timeout=" .. agent_ring_timeout)
    session:execute("set", "hangup_after_bridge=true")
    session:execute("set", "continue_on_fail=true")
    
    -- 播放提示音给坐席
    local agent_announce = "{ignore_early_media=true}loopback/app=playback:ivr/ivr-incoming_call.wav"
    
    -- 桥接呼叫
    session:execute("bridge", dial_string)
    
    -- 检查桥接结果
    local cause = session:getVariable("originate_disposition")
    
    if cause == "SUCCESS" then
        freeswitch.consoleLog("info", "成功连接到坐席: " .. agent_id .. "\n")
        
        -- 记录通话开始时间
        freeswitch.setGlobalVariable("agent:" .. agent_id .. ":call_start", os.time())
        freeswitch.setGlobalVariable("agent:" .. agent_id .. ":current_call", uuid)
        
        return true
    else
        freeswitch.consoleLog("warning", "无法连接到坐席 " .. agent_id .. ": " .. (cause or "unknown") .. "\n")
        
        -- 恢复坐席状态为可用
        freeswitch.setGlobalVariable("agent:" .. agent_id .. ":status", "available")
        
        return false
    end
end

-- 函数：将呼叫加入队列
function queue_call()
    local start_time = os.time()
    local connected = false
    
    -- 播放队列欢迎语
    session:execute("playback", "ivr/ivr-welcome_to_queue.wav")
    
    while session:ready() and not connected and (os.time() - start_time) < max_wait_time do
        -- 获取可用坐席
        local available_agents = get_available_agents()
        
        if #available_agents > 0 then
            -- 按顺序尝试连接坐席（可以改为随机或轮询）
            for _, agent in ipairs(available_agents) do
                if try_agent(agent) then
                    connected = true
                    break
                end
            end
        end
        
        if not connected then
            -- 播放等待音乐
            session:execute("playback", music_on_hold)
            
            -- 定期播放队列位置提示（可选）
            if (os.time() - start_time) % 30 == 0 then
                session:execute("playback", "ivr/ivr-you_are_still_in_queue.wav")
            end
            
            -- 短暂等待后重试
            session:sleep(5000)
        end
    end
    
    if not connected then
        -- 超时或无可用坐席
        session:execute("playback", "ivr/ivr-no_agents_available.wav")
        session:execute("playback", "ivr/ivr-please_try_again_later.wav")
    end
end

-- 主程序
if session:ready() then
    -- 将呼叫加入队列
    queue_call()
end

-- 清理
if session:ready() then
    session:hangup()
end