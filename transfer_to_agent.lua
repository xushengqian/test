-- FreeSWITCH 呼入转人工 Lua 脚本
-- 功能：处理呼入电话转接到人工坐席

-- 获取会话信息
local session = freeswitch.Session
if not session then
    freeswitch.consoleLog("ERR", "No active session found\n")
    return
end

-- 配置参数
local AGENT_EXTENSION = "1001"  -- 人工坐席分机号
local QUEUE_NAME = "support"    -- 队列名称
local MAX_WAIT_TIME = 30        -- 最大等待时间（秒）
local RING_TIMEOUT = 20         -- 振铃超时时间（秒）

-- 播放等待音频的函数
local function play_waiting_audio()
    local wait_audio_files = {
        "ivr/ivr-please_hold_while_party_answered.wav",
        "ivr/ivr-hold_music.wav",
        "ivr/ivr-please_wait.wav"
    }
    
    -- 随机选择等待音频
    local audio_file = wait_audio_files[math.random(#wait_audio_files)]
    session:execute("playback", audio_file)
end

-- 检查坐席是否可用
local function check_agent_availability(extension)
    local api = freeswitch.API()
    local result = api:execute("user_exists", extension)
    return result and result:find("true") ~= nil
end

-- 尝试转接到指定坐席
local function transfer_to_agent(extension)
    freeswitch.consoleLog("INFO", "Attempting to transfer to agent: " .. extension .. "\n")
    
    -- 设置转接参数
    session:setVariable("hangup_after_bridge", "true")
    session:setVariable("continue_on_fail", "true")
    session:setVariable("ringback", "%(2000,4000,440,480)")
    
    -- 尝试转接
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
    freeswitch.consoleLog("INFO", "Attempting to transfer to queue: " .. queue_name .. "\n")
    
    -- 设置队列参数
    session:setVariable("hangup_after_bridge", "true")
    session:setVariable("continue_on_fail", "true")
    
    -- 尝试转接到队列
    local result = session:execute("fifo", queue_name .. "@default")
    
    if result then
        freeswitch.consoleLog("INFO", "Successfully transferred to queue: " .. queue_name .. "\n")
        return true
    else
        freeswitch.consoleLog("WARN", "Failed to transfer to queue: " .. queue_name .. "\n")
        return false
    end
end

-- 主处理逻辑
local function main()
    freeswitch.consoleLog("INFO", "Starting transfer to agent process\n")
    
    -- 播放初始等待提示
    session:execute("playback", "ivr/ivr-please_hold_while_party_answered.wav")
    
    local transfer_success = false
    local start_time = os.time()
    
    -- 循环尝试转接，直到成功或超时
    while not transfer_success and (os.time() - start_time) < MAX_WAIT_TIME do
        -- 检查坐席是否可用
        if check_agent_availability(AGENT_EXTENSION) then
            freeswitch.consoleLog("INFO", "Agent is available, attempting transfer\n")
            
            -- 尝试转接到坐席
            if transfer_to_agent(AGENT_EXTENSION) then
                transfer_success = true
                break
            end
        else
            freeswitch.consoleLog("INFO", "Agent not available, trying queue\n")
            
            -- 尝试转接到队列
            if transfer_to_queue(QUEUE_NAME) then
                transfer_success = true
                break
            end
        end
        
        -- 如果转接失败，播放等待音频并等待
        if not transfer_success then
            freeswitch.consoleLog("INFO", "Transfer failed, playing waiting audio\n")
            play_waiting_audio()
            
            -- 等待一段时间后重试
            session:sleep(5000)  -- 等待5秒
        end
    end
    
    -- 如果所有尝试都失败了
    if not transfer_success then
        freeswitch.consoleLog("WARN", "All transfer attempts failed, playing no one available message\n")
        session:execute("playback", "ivr/ivr-no_one_available.wav")
        session:execute("playback", "ivr/ivr-goodbye.wav")
        session:execute("hangup", "NORMAL_CLEARING")
    end
end

-- 执行主函数
main()