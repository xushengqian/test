--[[
    FreeSWITCH Lua 音频播放状态检测示例
    
    本文件展示了在 FreeSWITCH Lua 脚本中判断音频是否正在播放的多种方法
]]--

-- ============================================
-- 方法1: 使用 streamFile() 的返回值
-- ============================================
function check_playback_with_streamfile(session, audio_file)
    -- streamFile() 会阻塞直到播放完成或被中断
    -- 返回值表示播放的字符数（通常是 DTMF 输入）
    local digit = session:streamFile(audio_file)
    
    if digit and digit ~= "" then
        freeswitch.consoleLog("INFO", "播放被 DTMF 按键中断: " .. digit .. "\n")
        return "interrupted", digit
    else
        freeswitch.consoleLog("INFO", "播放正常完成\n")
        return "completed", nil
    end
end

-- ============================================
-- 方法2: 检查通道变量判断播放状态
-- ============================================
function check_playback_variables(session)
    -- 检查播放是否被终止符中断
    local terminator = session:getVariable("playback_terminators_used")
    if terminator then
        freeswitch.consoleLog("INFO", "播放被终止符中断: " .. terminator .. "\n")
    end
    
    -- 获取已播放的秒数
    local playback_seconds = session:getVariable("playback_seconds")
    if playback_seconds then
        freeswitch.consoleLog("INFO", "已播放秒数: " .. playback_seconds .. "\n")
    end
    
    -- 获取播放文件的最后偏移位置
    local last_offset = session:getVariable("playback_last_offset_pos")
    if last_offset then
        freeswitch.consoleLog("INFO", "播放偏移位置: " .. last_offset .. "\n")
    end
    
    -- 获取当前播放的文件路径
    local current_file = session:getVariable("current_application_data")
    if current_file then
        freeswitch.consoleLog("INFO", "当前播放文件: " .. current_file .. "\n")
    end
    
    return {
        terminator = terminator,
        playback_seconds = playback_seconds,
        last_offset = last_offset,
        current_file = current_file
    }
end

-- ============================================
-- 方法3: 使用 session:ready() 检查会话状态
-- ============================================
function safe_playback(session, audio_file)
    -- 播放前检查会话是否有效
    if not session:ready() then
        freeswitch.consoleLog("WARNING", "会话已断开，无法播放音频\n")
        return false
    end
    
    -- 执行播放
    session:streamFile(audio_file)
    
    -- 播放后再次检查会话状态
    if session:ready() then
        freeswitch.consoleLog("INFO", "播放完成，会话仍然有效\n")
        return true
    else
        freeswitch.consoleLog("WARNING", "播放期间会话断开\n")
        return false
    end
end

-- ============================================
-- 方法4: 使用 execute 并检查返回状态
-- ============================================
function check_playback_with_execute(session, audio_file)
    -- 设置播放终止符
    session:setVariable("playback_terminators", "#*0123456789")
    
    -- 执行播放
    session:execute("playback", audio_file)
    
    -- 检查通道状态
    local hangup_cause = session:hangupCause()
    if hangup_cause and hangup_cause ~= "NONE" then
        freeswitch.consoleLog("INFO", "播放期间通道挂断: " .. hangup_cause .. "\n")
        return "hangup", hangup_cause
    end
    
    -- 检查是否有 DTMF 输入
    local terminator = session:getVariable("playback_terminators_used")
    if terminator then
        return "dtmf", terminator
    end
    
    return "completed", nil
end

-- ============================================
-- 方法5: 使用事件监听（高级用法）
-- ============================================
function playback_with_event_monitoring(session, audio_file)
    local playback_finished = false
    local playback_result = nil
    
    -- 设置事件钩子
    session:setInputCallback("on_dtmf_input", "")
    
    -- 异步播放并监听事件
    session:execute("playback", audio_file)
    
    -- 检查播放状态
    local status = session:getVariable("playback_terminators_used")
    
    return status
end

-- DTMF 输入回调函数
function on_dtmf_input(session, type, obj, arg)
    if type == "dtmf" then
        freeswitch.consoleLog("INFO", "收到 DTMF: " .. obj.digit .. "\n")
        -- 返回 "break" 可以中断播放
        return "break"
    end
    return "true"
end

-- ============================================
-- 方法6: 检查媒体状态（检测是否有音频流）
-- ============================================
function check_media_status(session)
    -- 检查通道是否有媒体
    local has_media = session:getVariable("channel_has_media")
    
    -- 检查当前应用
    local current_app = session:getVariable("current_application")
    
    -- 判断是否正在播放
    local is_playing = (current_app == "playback" or 
                        current_app == "play_and_get_digits" or
                        current_app == "read")
    
    freeswitch.consoleLog("INFO", "当前应用: " .. (current_app or "none") .. "\n")
    freeswitch.consoleLog("INFO", "是否在播放: " .. tostring(is_playing) .. "\n")
    
    return is_playing, current_app
end

-- ============================================
-- 方法7: 使用 UUID 命令查询播放状态
-- ============================================
function check_playback_by_uuid(session)
    local uuid = session:getVariable("uuid")
    
    -- 获取通道信息
    local api = freeswitch.API()
    local result = api:executeString("uuid_getvar " .. uuid .. " current_application")
    
    if result then
        freeswitch.consoleLog("INFO", "通过 API 查询到当前应用: " .. result .. "\n")
        return result == "playback"
    end
    
    return false
end

-- ============================================
-- 完整示例: 播放音频并等待 DTMF
-- ============================================
function play_and_wait_for_input(session, audio_file, timeout_ms)
    timeout_ms = timeout_ms or 5000
    
    -- 检查会话
    if not session:ready() then
        return nil, "session_not_ready"
    end
    
    -- 设置播放参数
    session:setVariable("playback_terminators", "#")
    
    -- 播放并获取输入
    local digits = session:playAndGetDigits(
        1,                  -- min_digits
        10,                 -- max_digits
        3,                  -- max_tries
        timeout_ms,         -- timeout (毫秒)
        "#",                -- terminators
        audio_file,         -- audio_file
        "",                 -- bad_input_audio
        "\\d+",             -- digit_regex
        "",                 -- variable_name
        timeout_ms,         -- digit_timeout
        ""                  -- transfer_on_failure
    )
    
    if digits and digits ~= "" then
        freeswitch.consoleLog("INFO", "用户输入: " .. digits .. "\n")
        return digits, "got_input"
    else
        freeswitch.consoleLog("INFO", "用户未输入或超时\n")
        return nil, "timeout"
    end
end

-- ============================================
-- 主函数示例
-- ============================================
function main_example(session)
    local audio_file = "/usr/local/freeswitch/sounds/en/us/callie/ivr/ivr-welcome.wav"
    
    -- 应答通话
    session:answer()
    
    -- 等待媒体建立
    session:sleep(500)
    
    -- 方法1: 简单播放并检查
    freeswitch.consoleLog("INFO", "===== 开始播放测试 =====\n")
    
    local status, result = check_playback_with_streamfile(session, audio_file)
    freeswitch.consoleLog("INFO", "播放状态: " .. status .. "\n")
    
    -- 检查播放后的变量
    local vars = check_playback_variables(session)
    
    -- 如果会话仍然有效，继续处理
    if session:ready() then
        freeswitch.consoleLog("INFO", "会话有效，可以继续后续操作\n")
    end
end

--[[
    使用说明:
    
    1. 最简单的方法是使用 streamFile()，它会阻塞直到播放完成
       session:streamFile("/path/to/audio.wav")
    
    2. 检查通道变量来获取播放详情:
       - playback_terminators_used: 用于终止播放的按键
       - playback_seconds: 已播放的秒数
       - playback_last_offset_pos: 播放偏移位置
    
    3. 使用 session:ready() 检查会话是否仍然有效
    
    4. 对于需要用户输入的场景，使用 playAndGetDigits()
    
    常见问题:
    - streamFile() 和 execute("playback") 都是阻塞式的
    - 如果需要非阻塞播放，考虑使用 displace 或 uuid_broadcast
    - 播放期间可以通过 DTMF 或 ESL 命令中断
]]--
