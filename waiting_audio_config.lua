-- FreeSWITCH 等待音频配置脚本
-- 用于配置和管理呼入转人工时的等待音频

local waiting_audio_config = {
    -- 等待音频文件列表
    audio_files = {
        "ivr/ivr-please_hold_while_party_answered.wav",  -- 请稍等，正在为您转接
        "ivr/ivr-hold_music.wav",                        -- 背景音乐
        "ivr/ivr-please_wait.wav",                       -- 请等待
        "ivr/ivr-call_being_transferred.wav",            -- 正在转接中
        "ivr/ivr-connecting_you.wav"                     -- 正在为您接通
    },
    
    -- 等待提示间隔时间（秒）
    prompt_interval = 10,
    
    -- 背景音乐循环播放
    background_music = "ivr/ivr-hold_music.wav",
    
    -- 超时提示
    timeout_messages = {
        "ivr/ivr-no_one_available.wav",                  -- 暂时无人接听
        "ivr/ivr-try_again_later.wav",                   -- 请稍后再试
        "ivr/ivr-goodbye.wav"                            -- 再见
    }
}

-- 播放等待音频的函数
function play_waiting_sequence(session, duration)
    local start_time = os.time()
    local last_prompt_time = 0
    
    while (os.time() - start_time) < duration do
        local current_time = os.time()
        
        -- 定期播放提示音
        if (current_time - last_prompt_time) >= waiting_audio_config.prompt_interval then
            local random_audio = waiting_audio_config.audio_files[math.random(#waiting_audio_config.audio_files)]
            session:execute("playback", random_audio)
            last_prompt_time = current_time
        end
        
        -- 播放背景音乐
        session:execute("playback", waiting_audio_config.background_music)
        
        -- 短暂等待
        session:sleep(1000)
    end
end

-- 播放超时提示
function play_timeout_message(session)
    for _, audio_file in ipairs(waiting_audio_config.timeout_messages) do
        session:execute("playback", audio_file)
    end
end

-- 创建等待音频播放器
function create_waiting_player()
    local player = {
        config = waiting_audio_config,
        
        -- 开始播放等待音频
        start = function(session, duration)
            freeswitch.consoleLog("INFO", "Starting waiting audio playback\n")
            play_waiting_sequence(session, duration or 30)
        end,
        
        -- 播放单次提示
        play_prompt = function(session, prompt_type)
            local audio_file = waiting_audio_config.audio_files[prompt_type] or 
                              waiting_audio_config.audio_files[1]
            session:execute("playback", audio_file)
        end,
        
        -- 播放超时消息
        play_timeout = function(session)
            play_timeout_message(session)
        end
    }
    
    return player
end

-- 导出配置
return {
    config = waiting_audio_config,
    create_player = create_waiting_player
}