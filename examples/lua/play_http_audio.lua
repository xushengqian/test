--[[
    FreeSWITCH Lua 脚本：HTTP 音频流播放
    
    功能：从 HTTP 服务器拉取音频流并播放
    
    使用方法：
    1. 将此脚本放到 /etc/freeswitch/scripts/ 目录
    2. 在 dialplan 中调用：
       <action application="lua" data="play_http_audio.lua"/>
    
    作者：FreeSWITCH HTTP Audio Demo
    版本：1.0.0
]]--

-- 配置
local CONFIG = {
    -- 默认音频服务器
    audio_server = "http://audio.example.com",
    
    -- 默认超时时间（毫秒）
    timeout = 30000,
    
    -- 默认重试次数
    retry_count = 3,
    
    -- 默认重试间隔（毫秒）
    retry_interval = 1000,
    
    -- 是否启用日志
    enable_log = true
}

-- 日志函数
local function log(level, message)
    if CONFIG.enable_log then
        freeswitch.consoleLog(level, "[HTTP-AUDIO] " .. message .. "\n")
    end
end

-- 获取通道变量
local function get_variable(session, name, default)
    local value = session:getVariable(name)
    if value and value ~= "" then
        return value
    end
    return default
end

-- 设置通道变量
local function set_variable(session, name, value)
    session:setVariable(name, value)
end

-- 判断音频格式并返回正确的播放 URL
local function get_playback_url(url)
    local lower_url = string.lower(url)
    
    -- MP3 格式使用 shout 协议
    if string.match(lower_url, "%.mp3$") or string.match(lower_url, "%.mp3%?") then
        return "shout://" .. url
    end
    
    -- 其他格式使用 http_cache
    if string.match(url, "^https?://") then
        return "http_cache://" .. url
    end
    
    -- 默认添加 http:// 前缀
    return "http_cache://http://" .. url
end

-- 播放 HTTP 音频（带重试）
local function play_http_audio(session, url, options)
    options = options or {}
    local retry_count = options.retry_count or CONFIG.retry_count
    local retry_interval = options.retry_interval or CONFIG.retry_interval
    
    local playback_url = get_playback_url(url)
    log("INFO", "Playing audio: " .. playback_url)
    
    for attempt = 1, retry_count do
        if not session:ready() then
            log("WARNING", "Session not ready, aborting playback")
            return false, "session_not_ready"
        end
        
        -- 尝试播放
        local result = session:streamFile(playback_url)
        
        if result then
            log("INFO", "Playback completed successfully")
            return true, "success"
        end
        
        if attempt < retry_count then
            log("WARNING", string.format("Playback failed, attempt %d/%d, retrying...", attempt, retry_count))
            session:sleep(retry_interval)
        end
    end
    
    log("ERROR", "Playback failed after " .. retry_count .. " attempts")
    return false, "playback_failed"
end

-- 播放音频并等待 DTMF 输入
local function play_and_get_dtmf(session, url, options)
    options = options or {}
    local min_digits = options.min_digits or 1
    local max_digits = options.max_digits or 1
    local timeout = options.timeout or 5000
    local terminators = options.terminators or "#"
    
    local playback_url = get_playback_url(url)
    log("INFO", "Playing audio with DTMF detection: " .. playback_url)
    
    local digits = session:playAndGetDigits(
        min_digits,
        max_digits,
        1,
        timeout,
        terminators,
        playback_url,
        "",
        "\\d",
        "",
        timeout
    )
    
    if digits and digits ~= "" then
        log("INFO", "Got DTMF: " .. digits)
        return digits
    end
    
    return nil
end

-- 播放音频列表
local function play_playlist(session, urls, options)
    options = options or {}
    local interval = options.interval or 500
    
    log("INFO", "Playing playlist with " .. #urls .. " items")
    
    for i, url in ipairs(urls) do
        if not session:ready() then
            log("WARNING", "Session ended, stopping playlist")
            return false
        end
        
        log("INFO", string.format("Playing %d/%d: %s", i, #urls, url))
        
        local success, err = play_http_audio(session, url)
        if not success then
            log("WARNING", "Failed to play item " .. i .. ": " .. (err or "unknown"))
        end
        
        if i < #urls and interval > 0 then
            session:sleep(interval)
        end
    end
    
    return true
end

-- 主函数
local function main()
    -- 获取会话
    if not session then
        freeswitch.consoleLog("ERROR", "No session object available\n")
        return
    end
    
    -- 接听电话
    session:answer()
    session:sleep(500)
    
    -- 获取通道变量
    local uuid = get_variable(session, "uuid", "unknown")
    local caller_id = get_variable(session, "caller_id_number", "unknown")
    local audio_url = get_variable(session, "audio_url", nil)
    
    log("INFO", string.format("Call UUID: %s, Caller: %s", uuid, caller_id))
    
    -- 如果没有指定音频 URL，使用默认
    if not audio_url then
        audio_url = CONFIG.audio_server .. "/welcome.mp3"
        log("INFO", "Using default audio URL: " .. audio_url)
    end
    
    -- 播放音频
    local success, err = play_http_audio(session, audio_url)
    
    if not success then
        log("WARNING", "HTTP audio failed, playing local fallback")
        -- 播放本地备用音频
        session:streamFile("local_stream://moh")
    end
    
    -- 挂断
    session:hangup()
end

-- 导出函数供外部调用
return {
    play_http_audio = play_http_audio,
    play_and_get_dtmf = play_and_get_dtmf,
    play_playlist = play_playlist,
    get_playback_url = get_playback_url,
    main = main,
    CONFIG = CONFIG
}
