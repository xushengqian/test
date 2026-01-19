--[[
    FreeSWITCH Lua 脚本：动态 TTS 音频播放器
    
    功能：从 TTS 服务获取音频并播放
    
    使用场景：
    - 呼叫中心动态播放欢迎语
    - IVR 系统播放动态内容
    - 验证码语音通知
    
    作者：FreeSWITCH HTTP Audio Demo
    版本：1.0.0
]]--

local cjson = require("cjson.safe")

-- 配置
local CONFIG = {
    -- TTS 服务地址
    tts_server = "http://tts.example.com",
    
    -- TTS API 路径
    tts_api_path = "/api/v1/synthesize",
    
    -- 默认语音
    default_voice = "zh-CN-XiaoxiaoNeural",
    
    -- 默认语速 (0.5 - 2.0)
    default_rate = 1.0,
    
    -- 音频格式
    audio_format = "mp3",
    
    -- 超时时间（秒）
    timeout = 30,
    
    -- 缓存时间（秒）
    cache_time = 3600
}

-- 日志函数
local function log(level, message)
    freeswitch.consoleLog(level, "[TTS-PLAYER] " .. message .. "\n")
end

-- URL 编码
local function url_encode(str)
    if str then
        str = string.gsub(str, "\n", "\r\n")
        str = string.gsub(str, "([^%w %-%_%.%~])",
            function(c)
                return string.format("%%%02X", string.byte(c))
            end)
        str = string.gsub(str, " ", "+")
    end
    return str
end

-- 构建 TTS URL
local function build_tts_url(text, options)
    options = options or {}
    
    local voice = options.voice or CONFIG.default_voice
    local rate = options.rate or CONFIG.default_rate
    local format = options.format or CONFIG.audio_format
    
    local url = string.format(
        "%s%s?text=%s&voice=%s&rate=%s&format=%s",
        CONFIG.tts_server,
        CONFIG.tts_api_path,
        url_encode(text),
        url_encode(voice),
        rate,
        format
    )
    
    return url
end

-- 播放 TTS 音频
local function play_tts(session, text, options)
    options = options or {}
    
    if not text or text == "" then
        log("WARNING", "Empty text, nothing to play")
        return false
    end
    
    log("INFO", "TTS text: " .. text)
    
    -- 构建 TTS URL
    local tts_url = build_tts_url(text, options)
    log("INFO", "TTS URL: " .. tts_url)
    
    -- 使用 shout 协议播放 MP3
    local playback_url
    if CONFIG.audio_format == "mp3" then
        playback_url = "shout://" .. tts_url
    else
        playback_url = "http_cache://" .. tts_url
    end
    
    -- 播放音频
    local result = session:streamFile(playback_url)
    
    if result then
        log("INFO", "TTS playback completed")
        return true
    else
        log("WARNING", "TTS playback failed")
        return false
    end
end

-- 播放带 DTMF 检测的 TTS
local function play_tts_with_dtmf(session, text, options)
    options = options or {}
    
    local min_digits = options.min_digits or 1
    local max_digits = options.max_digits or 1
    local timeout = options.timeout or 5000
    local terminators = options.terminators or "#"
    
    -- 构建 TTS URL
    local tts_url = build_tts_url(text, options)
    
    local playback_url
    if CONFIG.audio_format == "mp3" then
        playback_url = "shout://" .. tts_url
    else
        playback_url = "http_cache://" .. tts_url
    end
    
    log("INFO", "Playing TTS with DTMF: " .. text)
    
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

-- IVR 菜单示例
local function ivr_menu(session)
    session:answer()
    session:sleep(500)
    
    -- 播放欢迎语
    play_tts(session, "欢迎致电，请按1查询余额，按2人工服务，按0重听")
    
    -- 等待用户输入
    local max_tries = 3
    
    for i = 1, max_tries do
        local digit = play_tts_with_dtmf(
            session,
            "请输入您的选择",
            {
                min_digits = 1,
                max_digits = 1,
                timeout = 5000
            }
        )
        
        if digit then
            if digit == "1" then
                play_tts(session, "您的账户余额为100元")
                break
            elseif digit == "2" then
                play_tts(session, "正在为您转接人工服务，请稍候")
                -- 转接人工
                session:transfer("operator", "XML", "default")
                return
            elseif digit == "0" then
                -- 重听
                play_tts(session, "请按1查询余额，按2人工服务，按0重听")
            else
                play_tts(session, "输入无效，请重新输入")
            end
        else
            if i < max_tries then
                play_tts(session, "没有收到输入，请重新选择")
            end
        end
    end
    
    play_tts(session, "感谢您的来电，再见")
    session:hangup()
end

-- 验证码播放示例
local function play_verification_code(session, code)
    session:answer()
    session:sleep(500)
    
    -- 将验证码拆分为单个数字
    local spoken_code = ""
    for digit in string.gmatch(code, ".") do
        spoken_code = spoken_code .. digit .. "，"
    end
    
    local text = string.format("您的验证码是：%s，重复一遍：%s", spoken_code, spoken_code)
    
    -- 播放两遍
    play_tts(session, text, {rate = 0.8})  -- 语速稍慢
    
    session:sleep(1000)
    
    -- 再播放一遍
    play_tts(session, "再说一遍，您的验证码是：" .. spoken_code, {rate = 0.8})
    
    session:hangup()
end

-- 动态通知播放
local function play_notification(session, notification)
    session:answer()
    session:sleep(500)
    
    -- notification 结构示例:
    -- {
    --     greeting = "您好，张先生",
    --     content = "您有一个快递已到达，请及时领取",
    --     ending = "谢谢"
    -- }
    
    if notification.greeting then
        play_tts(session, notification.greeting)
        session:sleep(300)
    end
    
    if notification.content then
        play_tts(session, notification.content)
        session:sleep(300)
    end
    
    if notification.ending then
        play_tts(session, notification.ending)
    end
    
    session:hangup()
end

-- 主函数
local function main()
    if not session then
        freeswitch.consoleLog("ERROR", "No session object available\n")
        return
    end
    
    -- 获取通道变量
    local action = session:getVariable("tts_action") or "ivr"
    local text = session:getVariable("tts_text")
    local code = session:getVariable("verification_code")
    
    log("INFO", "TTS action: " .. action)
    
    if action == "ivr" then
        ivr_menu(session)
    elseif action == "code" and code then
        play_verification_code(session, code)
    elseif action == "tts" and text then
        session:answer()
        session:sleep(500)
        play_tts(session, text)
        session:hangup()
    else
        session:answer()
        session:sleep(500)
        play_tts(session, "参数错误")
        session:hangup()
    end
end

-- 导出
return {
    play_tts = play_tts,
    play_tts_with_dtmf = play_tts_with_dtmf,
    build_tts_url = build_tts_url,
    ivr_menu = ivr_menu,
    play_verification_code = play_verification_code,
    play_notification = play_notification,
    main = main,
    CONFIG = CONFIG
}
