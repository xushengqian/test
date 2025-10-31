--[[
FreeSWITCH Lua脚本 - 机器人呼叫处理脚本
处理机器人外呼流程、语音识别和转人工
]]

-- 获取呼叫UUID
local uuid = argv[1] or session:get_uuid()

-- 记录日志
local log_level = "info"
local function log(message)
    freeswitch.consoleLog(log_level, "[RobotCall] " .. message)
end

log("开始处理机器人呼叫: " .. uuid)

-- 获取会话对象（如果从dialplan调用，使用session）
local function handle_call(session)
    if not session then
        log("错误: 没有会话对象")
        return
    end
    
    local caller_id = session:getVariable("caller_id_number")
    local callee_number = session:getVariable("destination_number")
    local human_agent_number = session:getVariable("human_agent_number") or "1002"
    
    log("呼叫信息 - 主叫: " .. caller_id .. ", 被叫: " .. callee_number)
    
    -- 播放欢迎语
    session:answer()
    session:sleep(500)
    
    -- 播放机器人问候语
    local greeting = "您好，我是智能客服机器人，请问有什么可以帮您？"
    -- 如果配置了TTS，使用TTS播放
    -- session:execute("tts", greeting)
    -- 或者使用预设的语音文件
    session:streamFile("/usr/local/freeswitch/sounds/custom/greeting.wav")
    
    -- 设置超时
    session:setVariable("playback_terminators", "#")
    session:setVariable("recording", "true")
    
    -- 开始语音识别循环
    local max_interactions = 10  -- 最大交互次数
    local interaction_count = 0
    local transfer_requested = false
    
    while interaction_count < max_interactions and not transfer_requested do
        interaction_count = interaction_count + 1
        
        log("第 " .. interaction_count .. " 轮交互")
        
        -- 录音（用于语音识别）
        local recording_file = "/tmp/recording_" .. uuid .. "_" .. interaction_count .. ".wav"
        local recording_duration = 5000  -- 5秒录音
        
        -- 使用mod_pocketsphinx或mod_vosk进行本地语音识别
        -- 或者使用mod_dptools的detect_speech
        session:execute("detect_speech", "pocketsphinx default default default")
        
        -- 开始录音
        session:execute("record_session", recording_file)
        session:sleep(recording_duration)
        
        -- 停止录音和识别
        session:stopRecord()
        session:execute("stop_detect_speech")
        
        -- 获取识别结果
        local recognized_text = session:getVariable("detect_speech_result") or ""
        log("识别到的文本: " .. recognized_text)
        
        -- 检查是否识别到转人工意图
        local transfer_keywords = {
            "转人工", "人工服务", "人工客服", "转接人工",
            "我要人工", "找人工", "人工坐席"
        }
        
        local should_transfer = false
        local recognized_lower = string.lower(recognized_text)
        
        for _, keyword in ipairs(transfer_keywords) do
            if string.find(recognized_lower, keyword) then
                should_transfer = true
                transfer_requested = true
                log("检测到转人工意图: " .. recognized_text)
                break
            end
        end
        
        if should_transfer then
            -- 播放转接提示
            session:streamFile("/usr/local/freeswitch/sounds/custom/transfer.wav")
            session:sleep(1000)
            
            -- 执行转接
            log("执行转接到人工坐席: " .. human_agent_number)
            
            -- 方法1: 使用bridge转接
            local bridge_string = "user/" .. human_agent_number
            session:execute("bridge", bridge_string)
            
            -- 方法2: 使用transfer应用（如果在dialplan中）
            -- session:execute("transfer", human_agent_number .. " XML default")
            
            -- 方法3: 使用uuid_bridge（需要ESL）
            -- 这种情况下需要通过ESL从外部调用
            
            log("转接完成")
            break
        else
            -- 机器人回复（可以根据意图识别结果选择回复）
            local response = "收到，我继续为您服务。"
            -- session:execute("tts", response)
            session:streamFile("/usr/local/freeswitch/sounds/custom/response.wav")
        end
        
        -- 检查是否挂断
        if session:ready() == false then
            log("对方已挂断")
            break
        end
    end
    
    -- 如果达到最大交互次数或没有转人工，正常结束
    if not transfer_requested then
        log("达到最大交互次数，结束呼叫")
        session:streamFile("/usr/local/freeswitch/sounds/custom/goodbye.wav")
    end
    
    -- 挂断
    session:hangup("NORMAL_CLEARING")
    log("呼叫处理完成")
end

-- 如果从dialplan调用
if session then
    handle_call(session)
else
    log("错误: 无法获取会话对象，请确保从dialplan或originate调用")
end
