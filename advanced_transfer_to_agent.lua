-- FreeSWITCH 高级呼入转人工脚本
-- 集成等待音频播放功能

-- 加载等待音频配置
local waiting_audio = require("waiting_audio_config")

-- 获取会话
local session = freeswitch.Session
if not session then
    freeswitch.consoleLog("ERR", "No active session found\n")
    return
end

-- 配置参数
local CONFIG = {
    AGENT_EXTENSIONS = {"1001", "1002", "1003"},  -- 多个坐席分机
    QUEUE_NAME = "support",
    MAX_WAIT_TIME = 60,        -- 最大等待时间
    RING_TIMEOUT = 15,         -- 振铃超时
    PROMPT_INTERVAL = 15,      -- 提示间隔
    BACKGROUND_MUSIC = true    -- 是否播放背景音乐
}

-- 创建等待音频播放器
local waiting_player = waiting_audio.create_player()

-- 检查坐席可用性
local function check_agent_availability(extension)
    local api = freeswitch.API()
    local result = api:execute("user_exists", extension)
    return result and result:find("true") ~= nil
end

-- 获取可用坐席列表
local function get_available_agents()
    local available_agents = {}
    for _, extension in ipairs(CONFIG.AGENT_EXTENSIONS) do
        if check_agent_availability(extension) then
            table.insert(available_agents, extension)
        end
    end
    return available_agents
end

-- 尝试转接到坐席
local function transfer_to_agent(extension)
    freeswitch.consoleLog("INFO", "Transferring to agent: " .. extension .. "\n")
    
    -- 设置转接参数
    session:setVariable("hangup_after_bridge", "true")
    session:setVariable("continue_on_fail", "true")
    session:setVariable("ringback", "%(2000,4000,440,480)")
    session:setVariable("transfer_ringback", "%(2000,4000,440,480)")
    
    -- 播放转接提示
    session:execute("playback", "ivr/ivr-call_being_transferred.wav")
    
    -- 执行转接
    local result = session:execute("bridge", "user/" .. extension .. "@$${domain}")
    
    if result then
        freeswitch.consoleLog("INFO", "Successfully transferred to agent: " .. extension .. "\n")
        return true
    else
        freeswitch.consoleLog("WARN", "Failed to transfer to agent: " .. extension .. "\n")
        return false
    end
end

-- 尝试转接到队列
local function transfer_to_queue(queue_name)
    freeswitch.consoleLog("INFO", "Transferring to queue: " .. queue_name .. "\n")
    
    -- 设置队列参数
    session:setVariable("hangup_after_bridge", "true")
    session:setVariable("continue_on_fail", "true")
    
    -- 播放队列提示
    session:execute("playback", "ivr/ivr-connecting_you.wav")
    
    -- 执行队列转接
    local result = session:execute("fifo", queue_name .. "@default")
    
    if result then
        freeswitch.consoleLog("INFO", "Successfully transferred to queue: " .. queue_name .. "\n")
        return true
    else
        freeswitch.consoleLog("WARN", "Failed to transfer to queue: " .. queue_name .. "\n")
        return false
    end
end

-- 智能等待和转接
local function smart_transfer()
    local start_time = os.time()
    local last_prompt_time = 0
    local transfer_attempts = 0
    local max_attempts = 3
    
    freeswitch.consoleLog("INFO", "Starting smart transfer process\n")
    
    while (os.time() - start_time) < CONFIG.MAX_WAIT_TIME and transfer_attempts < max_attempts do
        local current_time = os.time()
        
        -- 定期播放等待提示
        if (current_time - last_prompt_time) >= CONFIG.PROMPT_INTERVAL then
            waiting_player.play_prompt(session, math.random(1, 3))
            last_prompt_time = current_time
        end
        
        -- 获取可用坐席
        local available_agents = get_available_agents()
        
        if #available_agents > 0 then
            -- 随机选择一个可用坐席
            local selected_agent = available_agents[math.random(#available_agents)]
            freeswitch.consoleLog("INFO", "Selected agent: " .. selected_agent .. "\n")
            
            -- 尝试转接
            if transfer_to_agent(selected_agent) then
                return true
            else
                transfer_attempts = transfer_attempts + 1
                freeswitch.consoleLog("WARN", "Transfer attempt " .. transfer_attempts .. " failed\n")
            end
        else
            freeswitch.consoleLog("INFO", "No agents available, trying queue\n")
            
            -- 尝试转接到队列
            if transfer_to_queue(CONFIG.QUEUE_NAME) then
                return true
            else
                transfer_attempts = transfer_attempts + 1
            end
        end
        
        -- 播放背景音乐
        if CONFIG.BACKGROUND_MUSIC then
            session:execute("playback", "ivr/ivr-hold_music.wav")
        end
        
        -- 等待后重试
        session:sleep(3000)
    end
    
    return false
end

-- 主处理函数
local function main()
    freeswitch.consoleLog("INFO", "Starting advanced transfer to agent process\n")
    
    -- 播放欢迎提示
    session:execute("playback", "ivr/ivr-welcome.wav")
    session:execute("playback", "ivr/ivr-please_hold_while_party_answered.wav")
    
    -- 执行智能转接
    local success = smart_transfer()
    
    if not success then
        freeswitch.consoleLog("WARN", "All transfer attempts failed\n")
        waiting_player.play_timeout(session)
        session:execute("hangup", "NORMAL_CLEARING")
    end
end

-- 执行主函数
main()