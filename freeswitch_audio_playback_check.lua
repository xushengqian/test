--[[
    FreeSWITCH Lua 脚本：判断音频是否在播放
    
    在 FreeSWITCH 中，有多种方式可以检测和控制音频播放状态
]]

-- ============================================
-- 方法1：使用通道变量检查播放状态
-- ============================================
function check_playback_status_by_variable(session)
    -- 检查 playback_status 通道变量
    -- 值为 "success" 表示播放完成，"failed" 表示播放失败
    local playback_status = session:getVariable("playback_status")
    
    if playback_status == "success" then
        freeswitch.consoleLog("INFO", "音频播放成功完成\n")
        return true
    elseif playback_status == "failed" then
        freeswitch.consoleLog("WARNING", "音频播放失败\n")
        return false
    end
    
    return nil -- 未知状态
end

-- ============================================
-- 方法2：使用 CHANNEL_EXECUTE_COMPLETE 事件
-- ============================================
function playback_with_event_check(session, audio_file)
    -- 设置事件回调来监听播放完成
    session:execute("set", "playback_terminators=none")
    
    -- 开始播放
    session:execute("playback", audio_file)
    
    -- 播放完成后检查结果
    local last_app = session:getVariable("current_application")
    local term_used = session:getVariable("playback_terminators_used")
    
    freeswitch.consoleLog("INFO", "播放终止符: " .. (term_used or "none") .. "\n")
    
    return term_used
end

-- ============================================
-- 方法3：使用 streamFile 配合输入回调（推荐方式）
-- ============================================
function playback_with_callback(session, audio_file)
    local is_playing = true
    local was_interrupted = false
    
    -- 定义输入回调函数
    local function input_callback(s, type, obj)
        if type == "dtmf" then
            -- 用户按键，可以选择中断播放
            freeswitch.consoleLog("INFO", "检测到DTMF按键: " .. obj.digit .. "\n")
            was_interrupted = true
            return "break"  -- 返回 "break" 中断播放
        end
        return ""
    end
    
    -- 使用 streamFile 播放，支持回调
    session:setInputCallback("input_callback", "")
    session:streamFile(audio_file)
    session:unsetInputCallback()
    
    is_playing = false
    
    if was_interrupted then
        freeswitch.consoleLog("INFO", "播放被用户中断\n")
    else
        freeswitch.consoleLog("INFO", "播放正常完成\n")
    end
    
    return not was_interrupted
end

-- ============================================
-- 方法4：检查 uuid_exists 和 media 状态
-- ============================================
function check_channel_media_status(session)
    local uuid = session:get_uuid()
    
    -- 检查通道是否存在
    if not session:ready() then
        freeswitch.consoleLog("WARNING", "会话已结束\n")
        return false
    end
    
    -- 获取通道状态
    local state = session:getVariable("state")
    local channel_state = session:getVariable("channel_state")
    
    freeswitch.consoleLog("INFO", "通道状态: " .. (state or "unknown") .. "\n")
    freeswitch.consoleLog("INFO", "Channel State: " .. (channel_state or "unknown") .. "\n")
    
    return true
end

-- ============================================
-- 方法5：使用 uuid_broadcast 检查（API方式）
-- ============================================
function check_broadcast_status(uuid)
    local api = freeswitch.API()
    
    -- 获取通道变量
    local result = api:executeString("uuid_getvar " .. uuid .. " playback_status")
    
    if result and result ~= "" then
        freeswitch.consoleLog("INFO", "播放状态: " .. result .. "\n")
        return result
    end
    
    return nil
end

-- ============================================
-- 方法6：使用 read 应用读取时检测（边播放边检测）
-- ============================================
function playback_and_collect_digits(session, audio_file, max_digits, timeout)
    max_digits = max_digits or 1
    timeout = timeout or 5000
    
    -- read 应用会播放音频同时收集按键
    -- 格式: read <min> <max> <sound file> <variable name> <timeout> [terminators]
    session:execute("read", max_digits .. " " .. max_digits .. " " .. audio_file .. " digits " .. timeout .. " #")
    
    local digits = session:getVariable("digits")
    local read_result = session:getVariable("read_result")
    
    freeswitch.consoleLog("INFO", "收集到的按键: " .. (digits or "none") .. "\n")
    freeswitch.consoleLog("INFO", "Read结果: " .. (read_result or "unknown") .. "\n")
    
    -- read_result 可能的值: success, timeout, failure
    return digits, read_result
end

-- ============================================
-- 方法7：异步播放检测（使用 uuid_broadcast）
-- ============================================
function async_playback_check(uuid, audio_file)
    local api = freeswitch.API()
    
    -- 异步播放音频
    api:executeString("uuid_broadcast " .. uuid .. " " .. audio_file .. " aleg")
    
    -- 等待一段时间后检查状态
    freeswitch.msleep(100)
    
    -- 检查播放是否仍在进行
    local result = api:executeString("uuid_getvar " .. uuid .. " playback_status")
    
    return result
end

-- ============================================
-- 方法8：使用 playback_seconds 变量
-- ============================================
function get_playback_duration(session)
    -- 播放完成后可以获取播放时长
    local playback_ms = session:getVariable("playback_ms")
    local playback_seconds = session:getVariable("playback_seconds")
    
    if playback_seconds then
        freeswitch.consoleLog("INFO", "已播放时长: " .. playback_seconds .. " 秒\n")
        return tonumber(playback_seconds)
    end
    
    return 0
end

-- ============================================
-- 实际使用示例
-- ============================================
function main_example(session)
    session:answer()
    
    -- 确保会话准备就绪
    if not session:ready() then
        freeswitch.consoleLog("ERROR", "会话未就绪\n")
        return
    end
    
    local audio_file = "/usr/local/freeswitch/sounds/en/us/callie/ivr/8000/ivr-welcome.wav"
    
    -- 示例1：简单播放并检查状态
    freeswitch.consoleLog("INFO", "开始播放音频...\n")
    session:execute("playback", audio_file)
    
    -- 播放结束后检查状态
    local status = session:getVariable("playback_status")
    if status == "success" then
        freeswitch.consoleLog("INFO", "播放成功完成！\n")
    else
        freeswitch.consoleLog("WARNING", "播放状态: " .. (status or "未知") .. "\n")
    end
    
    -- 获取播放时长
    local duration = get_playback_duration(session)
    freeswitch.consoleLog("INFO", "总播放时长: " .. duration .. " 秒\n")
    
    session:hangup()
end

-- ============================================
-- 循环检测播放状态的高级示例
-- ============================================
function monitor_playback_loop(session, audio_file)
    local uuid = session:get_uuid()
    local api = freeswitch.API()
    
    -- 在单独的线程中异步播放
    api:executeString("uuid_broadcast " .. uuid .. " " .. audio_file .. " aleg")
    
    -- 循环检测播放状态
    local max_checks = 100
    local check_count = 0
    
    while session:ready() and check_count < max_checks do
        check_count = check_count + 1
        
        -- 检查是否还在播放
        local answer = api:executeString("uuid_exists " .. uuid)
        if answer ~= "true" then
            freeswitch.consoleLog("INFO", "通道已关闭\n")
            break
        end
        
        -- 短暂休眠后继续检查
        freeswitch.msleep(100)
    end
    
    freeswitch.consoleLog("INFO", "监控结束\n")
end

--[[
    总结：判断音频播放状态的关键通道变量
    
    1. playback_status        - 播放状态 (success/failed)
    2. playback_terminators_used - 用户按下的终止键
    3. playback_ms            - 播放的毫秒数
    4. playback_seconds       - 播放的秒数
    5. playback_last_offset_pos - 最后的播放位置
    
    关键API命令：
    1. uuid_broadcast <uuid> <path> [aleg|bleg|both]  - 异步播放
    2. uuid_break <uuid> [all]                        - 中断播放
    3. uuid_getvar <uuid> <varname>                   - 获取变量
    4. uuid_exists <uuid>                             - 检查通道是否存在
]]
