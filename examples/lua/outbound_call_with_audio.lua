--[[
    FreeSWITCH Lua 脚本：外呼通话 + HTTP 音频播放
    
    功能：
    - 发起外呼
    - 播放 HTTP 欢迎语音
    - 支持用户按键交互
    - 转接人工坐席
    
    使用场景：
    - 智能外呼机器人
    - 语音通知系统
    - IVR 外呼
    
    调用方式：
    1. 通过 Dialplan：
       <action application="lua" data="outbound_call_with_audio.lua"/>
    
    2. 通过 ESL originate：
       originate {myvar=value}sofia/gateway/xxx/phone &lua(outbound_call_with_audio.lua)
    
    作者：FreeSWITCH HTTP Audio Demo
    版本：1.0.0
]]--

-- 配置
local CONFIG = {
    -- 音频服务器
    audio_server = "http://audio.example.com",
    
    -- TTS 服务器
    tts_server = "http://tts.example.com",
    
    -- 默认超时时间
    playback_timeout = 30000,
    dtmf_timeout = 5000,
    
    -- 最大重试次数
    max_retries = 3,
    
    -- 坐席转接号码
    agent_extension = "operator",
    
    -- 日志级别
    log_level = "INFO"
}

-- 全局变量
local call_info = {}
local playback_interrupted = false

-- 日志函数
local function log(level, message)
    local uuid = call_info.uuid or "unknown"
    freeswitch.consoleLog(level, string.format("[OUTBOUND-%s] %s\n", uuid, message))
end

-- 获取通道变量
local function get_var(name, default)
    if not session then return default end
    local value = session:getVariable(name)
    if value and value ~= "" then
        return value
    end
    return default
end

-- 设置通道变量
local function set_var(name, value)
    if session then
        session:setVariable(name, tostring(value))
    end
end

-- 获取播放 URL
local function get_playback_url(url)
    local lower_url = string.lower(url)
    
    -- 如果已经有协议前缀，直接返回
    if string.match(url, "^shout://") or string.match(url, "^http_cache://") then
        return url
    end
    
    -- MP3 格式使用 shout 协议
    if string.match(lower_url, "%.mp3") then
        return "shout://" .. url
    end
    
    -- 其他格式使用 http_cache
    if string.match(url, "^https?://") then
        return "http_cache://" .. url
    end
    
    return "http_cache://http://" .. url
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
local function build_tts_url(text, voice, rate)
    voice = voice or "zh-CN-XiaoxiaoNeural"
    rate = rate or 1.0
    
    return string.format(
        "%s/api/speak?text=%s&voice=%s&rate=%s&format=mp3",
        CONFIG.tts_server,
        url_encode(text),
        url_encode(voice),
        rate
    )
end

-- 播放 HTTP 音频
local function play_audio(url, allow_interrupt)
    if not session or not session:ready() then
        log("WARNING", "Session not ready, cannot play audio")
        return false
    end
    
    allow_interrupt = allow_interrupt ~= false
    
    local playback_url = get_playback_url(url)
    log("INFO", "Playing: " .. playback_url)
    
    if allow_interrupt then
        session:setVariable("playback_terminators", "any")
    else
        session:setVariable("playback_terminators", "none")
    end
    
    local result = session:streamFile(playback_url)
    
    return result
end

-- 播放 TTS 音频
local function play_tts(text, voice, rate, allow_interrupt)
    if not text or text == "" then
        log("WARNING", "Empty TTS text")
        return false
    end
    
    local tts_url = build_tts_url(text, voice, rate)
    return play_audio(tts_url, allow_interrupt)
end

-- 播放音频并获取 DTMF
local function play_and_get_dtmf(url, options)
    options = options or {}
    
    local min_digits = options.min_digits or 1
    local max_digits = options.max_digits or 1
    local tries = options.tries or 1
    local timeout = options.timeout or CONFIG.dtmf_timeout
    local terminators = options.terminators or "#"
    local invalid_url = options.invalid_url or ""
    
    local playback_url = get_playback_url(url)
    local invalid_playback_url = invalid_url ~= "" and get_playback_url(invalid_url) or ""
    
    log("INFO", "Play and get DTMF: " .. playback_url)
    
    local digits = session:playAndGetDigits(
        min_digits,
        max_digits,
        tries,
        timeout,
        terminators,
        playback_url,
        invalid_playback_url,
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

-- 播放 TTS 并获取 DTMF
local function play_tts_and_get_dtmf(text, options)
    local tts_url = build_tts_url(text, options and options.voice, options and options.rate)
    return play_and_get_dtmf(tts_url, options)
end

-- 等待 DTMF（不播放音频）
local function wait_dtmf(timeout, max_digits)
    timeout = timeout or CONFIG.dtmf_timeout
    max_digits = max_digits or 1
    
    local digits = session:getDigits(max_digits, "#", timeout)
    
    if digits and digits ~= "" then
        log("INFO", "Got DTMF: " .. digits)
        return digits
    end
    
    return nil
end

-- 转接到坐席
local function transfer_to_agent(agent_number, announce_text)
    agent_number = agent_number or CONFIG.agent_extension
    
    log("INFO", "Transferring to agent: " .. agent_number)
    
    -- 播放转接提示
    if announce_text then
        play_tts(announce_text)
    else
        play_tts("正在为您转接人工客服，请稍候")
    end
    
    -- 设置转接相关变量
    set_var("transfer_to_agent", "true")
    set_var("agent_number", agent_number)
    
    -- 执行转接
    session:transfer(agent_number, "XML", "default")
end

-- 挂断电话
local function hangup_call(cause)
    cause = cause or "NORMAL_CLEARING"
    
    log("INFO", "Hanging up with cause: " .. cause)
    
    if session and session:ready() then
        session:hangup(cause)
    end
end

-- 初始化通话信息
local function init_call_info()
    call_info = {
        uuid = get_var("uuid", "unknown"),
        caller_id = get_var("caller_id_number", "unknown"),
        destination = get_var("destination_number", "unknown"),
        direction = get_var("direction", "outbound"),
        start_time = os.time(),
        
        -- 自定义变量
        customer_name = get_var("customer_name", ""),
        campaign_id = get_var("campaign_id", ""),
        script_id = get_var("script_id", "default"),
        
        -- 状态
        answered = false,
        dtmf_history = {},
        transfer_requested = false
    }
    
    log("INFO", string.format(
        "Call initialized: uuid=%s, caller=%s, dest=%s",
        call_info.uuid, call_info.caller_id, call_info.destination
    ))
end

-- 记录 DTMF
local function record_dtmf(digit, context)
    table.insert(call_info.dtmf_history, {
        digit = digit,
        context = context or "unknown",
        timestamp = os.time()
    })
end

-- 外呼脚本 1：简单通知
local function script_simple_notification()
    log("INFO", "Running script: simple_notification")
    
    local message = get_var("notification_message", "您好，这是一条测试通知")
    
    -- 播放通知内容
    play_tts(message, nil, 0.9)
    
    -- 等待一会儿
    session:sleep(1000)
    
    -- 播放结束语
    play_tts("感谢您的收听，再见")
    
    -- 挂断
    hangup_call()
end

-- 外呼脚本 2：IVR 菜单
local function script_ivr_menu()
    log("INFO", "Running script: ivr_menu")
    
    local max_tries = CONFIG.max_retries
    
    -- 播放欢迎语
    play_tts("您好，欢迎致电。请按1确认，按2取消，按0转接人工客服")
    
    for i = 1, max_tries do
        if not session:ready() then
            log("WARNING", "Session ended during IVR")
            return
        end
        
        local digit = wait_dtmf(CONFIG.dtmf_timeout, 1)
        
        if digit then
            record_dtmf(digit, "ivr_menu")
            
            if digit == "1" then
                play_tts("您已确认，谢谢")
                set_var("user_choice", "confirmed")
                break
                
            elseif digit == "2" then
                play_tts("您已取消，再见")
                set_var("user_choice", "cancelled")
                break
                
            elseif digit == "0" then
                transfer_to_agent()
                return  -- transfer 会结束此脚本
                
            else
                if i < max_tries then
                    play_tts("输入无效，请重新选择")
                end
            end
        else
            if i < max_tries then
                play_tts("没有收到您的输入，请按键选择")
            end
        end
    end
    
    -- 结束语
    play_tts("感谢您的来电，再见")
    hangup_call()
end

-- 外呼脚本 3：验证码通知
local function script_verification_code()
    log("INFO", "Running script: verification_code")
    
    local code = get_var("verification_code", "123456")
    
    -- 将验证码拆分为单个数字
    local spoken_code = ""
    for digit in string.gmatch(code, ".") do
        spoken_code = spoken_code .. digit .. "，"
    end
    
    -- 播放验证码
    play_tts("您好，您的验证码是：", nil, 0.9)
    session:sleep(500)
    play_tts(spoken_code, nil, 0.7)  -- 慢速播放
    
    session:sleep(1000)
    
    -- 重复一遍
    play_tts("重复一遍，您的验证码是：", nil, 0.9)
    session:sleep(500)
    play_tts(spoken_code, nil, 0.7)
    
    session:sleep(500)
    play_tts("请妥善保管，切勿泄露给他人，再见")
    
    hangup_call()
end

-- 外呼脚本 4：满意度调查
local function script_satisfaction_survey()
    log("INFO", "Running script: satisfaction_survey")
    
    -- 播放欢迎语
    play_tts("您好，我们正在进行服务满意度调查，耽误您一分钟时间")
    session:sleep(500)
    
    local questions = {
        {
            text = "请问您对我们的服务是否满意？满意请按1，不满意请按2",
            var = "q1_satisfaction"
        },
        {
            text = "请问您是否愿意向朋友推荐我们的服务？愿意请按1，不愿意请按2",
            var = "q2_recommend"
        }
    }
    
    for i, q in ipairs(questions) do
        if not session:ready() then
            log("WARNING", "Session ended during survey")
            return
        end
        
        local digit = play_tts_and_get_dtmf(q.text, {
            max_digits = 1,
            tries = 2,
            timeout = 8000
        })
        
        if digit then
            record_dtmf(digit, "survey_q" .. i)
            set_var(q.var, digit)
            
            if digit == "1" then
                play_tts("好的，感谢您的认可")
            else
                play_tts("好的，我们会努力改进")
            end
        else
            play_tts("没有收到您的回答，跳过此问题")
        end
        
        session:sleep(500)
    end
    
    play_tts("感谢您参与我们的调查，祝您生活愉快，再见")
    hangup_call()
end

-- 外呼脚本 5：带人工介入的机器人外呼
local function script_robot_with_human()
    log("INFO", "Running script: robot_with_human")
    
    local customer_name = call_info.customer_name
    local greeting = customer_name ~= "" and ("您好，" .. customer_name .. "先生/女士") or "您好"
    
    -- 播放欢迎语
    play_tts(greeting .. "，我是智能客服助手")
    session:sleep(300)
    
    -- 播放主要内容
    local content_url = get_var("content_audio_url")
    if content_url and content_url ~= "" then
        play_audio(content_url, true)
    else
        play_tts("这是一条重要通知，请注意查收")
    end
    
    session:sleep(500)
    
    -- 询问是否需要人工服务
    local digit = play_tts_and_get_dtmf(
        "如需人工服务，请按0，否则请直接挂机",
        {
            max_digits = 1,
            tries = 1,
            timeout = 10000
        }
    )
    
    if digit == "0" then
        record_dtmf(digit, "request_human")
        transfer_to_agent()
        return
    end
    
    play_tts("感谢您的收听，再见")
    hangup_call()
end

-- 主入口
local function main()
    if not session then
        freeswitch.consoleLog("ERROR", "No session object available\n")
        return
    end
    
    -- 检查通道是否已接通
    local channel_state = session:getVariable("Channel-State")
    if channel_state ~= "CS_EXECUTE" then
        -- 接听
        session:answer()
        session:sleep(500)
    end
    
    -- 初始化通话信息
    init_call_info()
    call_info.answered = true
    
    -- 获取脚本 ID
    local script_id = call_info.script_id
    log("INFO", "Running script: " .. script_id)
    
    -- 根据脚本 ID 执行不同的流程
    if script_id == "notification" then
        script_simple_notification()
    elseif script_id == "ivr" then
        script_ivr_menu()
    elseif script_id == "verification" then
        script_verification_code()
    elseif script_id == "survey" then
        script_satisfaction_survey()
    elseif script_id == "robot_human" then
        script_robot_with_human()
    else
        -- 默认 IVR 菜单
        script_ivr_menu()
    end
    
    -- 记录通话时长
    local call_duration = os.time() - call_info.start_time
    log("INFO", string.format("Call completed, duration: %d seconds", call_duration))
end

-- 导出函数（供其他脚本调用）
return {
    main = main,
    play_audio = play_audio,
    play_tts = play_tts,
    play_and_get_dtmf = play_and_get_dtmf,
    play_tts_and_get_dtmf = play_tts_and_get_dtmf,
    transfer_to_agent = transfer_to_agent,
    hangup_call = hangup_call,
    get_playback_url = get_playback_url,
    build_tts_url = build_tts_url,
    CONFIG = CONFIG
}
