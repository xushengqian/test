-- FreeSWITCH Lua脚本: 处理转人工失败
-- 提供失败后的处理选项，如回调、留言等

-- 配置参数
local config = {
    max_retry_count = 2,           -- 最大重试次数
    callback_enabled = true,       -- 是否启用回调
    voicemail_enabled = true,      -- 是否启用留言
    alternative_ivr = true,        -- 是否提供替代IVR选项
    business_hours = {             -- 工作时间
        start_hour = 9,
        end_hour = 18,
        work_days = {1, 2, 3, 4, 5} -- 周一到周五
    }
}

-- 获取会话信息
local uuid = session:get_uuid()
local caller_id = session:getVariable("caller_id_number") or "unknown"
local transfer_reason = session:getVariable("transfer_reason") or "unknown"
local retry_count = tonumber(session:getVariable("transfer_retry_count") or "0")

-- 日志函数
local function log_info(message)
    freeswitch.consoleLog("info", "[TransferFailure] " .. message .. "\n")
end

local function log_error(message)
    freeswitch.consoleLog("err", "[TransferFailure] " .. message .. "\n")
end

-- 检查是否在工作时间
local function is_business_hours()
    local now = os.date("*t")
    local hour = now.hour
    local day = now.wday  -- 1=Sunday, 2=Monday, etc.
    
    -- 转换为周一=1的格式
    local work_day = day - 1
    if work_day == 0 then work_day = 7 end
    
    -- 检查是否工作日
    local is_work_day = false
    for _, d in ipairs(config.business_hours.work_days) do
        if d == work_day then
            is_work_day = true
            break
        end
    end
    
    -- 检查是否在工作时间内
    if is_work_day and hour >= config.business_hours.start_hour 
        and hour < config.business_hours.end_hour then
        return true
    end
    
    return false
end

-- 处理回调请求
local function handle_callback_request()
    log_info("Processing callback request for: " .. caller_id)
    
    -- 播放回调选项提示
    session:execute("playback", "ivr/ivr-would_you_like_callback.wav")
    session:execute("playback", "ivr/ivr-press_1_for_callback.wav")
    
    local digits = session:playAndGetDigits(
        1, 1, 1, 5000, "#",
        "silence_stream://250",
        "ivr/ivr-invalid_entry.wav",
        "\\d"
    )
    
    if digits == "1" then
        -- 确认电话号码
        session:execute("playback", "ivr/ivr-confirm_callback_number.wav")
        session:execute("say", "en number pronounced " .. caller_id)
        session:execute("playback", "ivr/ivr-press_1_to_confirm.wav")
        
        local confirm = session:playAndGetDigits(
            1, 1, 1, 5000, "#",
            "silence_stream://250",
            "ivr/ivr-invalid_entry.wav",
            "\\d"
        )
        
        if confirm == "1" then
            -- 记录回调请求
            save_callback_request(caller_id)
            
            -- 播放确认信息
            session:execute("playback", "ivr/ivr-callback_scheduled.wav")
            
            -- 预计回调时间
            local callback_time = calculate_callback_time()
            session:execute("say", "en time pronounced " .. callback_time)
            
            log_info("Callback scheduled for: " .. caller_id .. " at " .. callback_time)
            return true
        else
            -- 输入新号码
            session:execute("playback", "ivr/ivr-enter_callback_number.wav")
            
            local new_number = session:playAndGetDigits(
                10, 11, 1, 10000, "#",
                "ivr/ivr-enter_phone_number.wav",
                "ivr/ivr-invalid_number.wav",
                "\\d+"
            )
            
            if new_number and #new_number >= 10 then
                save_callback_request(new_number)
                session:execute("playback", "ivr/ivr-callback_scheduled.wav")
                log_info("Callback scheduled for new number: " .. new_number)
                return true
            end
        end
    end
    
    return false
end

-- 保存回调请求
function save_callback_request(phone_number)
    local callback_data = {
        phone_number = phone_number,
        original_caller = caller_id,
        request_time = os.date("%Y-%m-%d %H:%M:%S"),
        transfer_reason = transfer_reason,
        priority = session:getVariable("queue_priority") or "3",
        uuid = uuid
    }
    
    -- 发送事件
    local event = freeswitch.Event("CUSTOM", "callback::request")
    event:addHeader("Callback-Number", phone_number)
    event:addHeader("Request-Time", callback_data.request_time)
    event:addHeader("Priority", callback_data.priority)
    event:addBody(freeswitch.serialize(callback_data))
    event:fire()
    
    -- 可以保存到数据库
    -- save_to_database("callback_requests", callback_data)
    
    log_info("Callback request saved: " .. freeswitch.serialize(callback_data))
end

-- 计算预计回调时间
function calculate_callback_time()
    local api = freeswitch.API()
    
    -- 获取队列长度
    local queue_info = api:executeString("fifo list agent_queue")
    local queue_length = 0
    
    if queue_info then
        for line in string.gmatch(queue_info, "[^\r\n]+") do
            if string.find(line, "waiting") then
                queue_length = queue_length + 1
            end
        end
    end
    
    -- 估算回调时间（每个客户平均5分钟）
    local wait_minutes = queue_length * 5
    local callback_time = os.time() + (wait_minutes * 60)
    
    return os.date("%H:%M", callback_time)
end

-- 处理语音留言
local function handle_voicemail()
    log_info("Processing voicemail for: " .. caller_id)
    
    -- 播放留言提示
    session:execute("playback", "ivr/ivr-leave_message_after_beep.wav")
    
    -- 设置录音参数
    local max_len = 180  -- 最长3分钟
    local silence_threshold = 3  -- 3秒静音结束
    local voicemail_file = string.format(
        "/tmp/voicemail_%s_%s.wav",
        caller_id,
        os.date("%Y%m%d%H%M%S")
    )
    
    -- 播放提示音
    session:execute("playback", "tone_stream://%(1000,0,640)")
    
    -- 开始录音
    session:execute("record", voicemail_file .. " " .. max_len .. " " .. silence_threshold)
    
    -- 检查录音结果
    local record_result = session:getVariable("record_seconds")
    
    if tonumber(record_result or "0") > 0 then
        -- 保存留言信息
        local voicemail_data = {
            caller_id = caller_id,
            file_path = voicemail_file,
            duration = record_result,
            timestamp = os.date("%Y-%m-%d %H:%M:%S"),
            transfer_reason = transfer_reason
        }
        
        save_voicemail(voicemail_data)
        
        -- 播放确认信息
        session:execute("playback", "ivr/ivr-message_saved.wav")
        
        log_info("Voicemail saved: " .. voicemail_file .. " (" .. record_result .. " seconds)")
        return true
    else
        session:execute("playback", "ivr/ivr-no_message_recorded.wav")
        return false
    end
end

-- 保存留言信息
function save_voicemail(voicemail_data)
    -- 发送事件
    local event = freeswitch.Event("CUSTOM", "voicemail::new")
    event:addHeader("Caller-ID", voicemail_data.caller_id)
    event:addHeader("File-Path", voicemail_data.file_path)
    event:addHeader("Duration", voicemail_data.duration)
    event:addHeader("Timestamp", voicemail_data.timestamp)
    event:fire()
    
    -- 可以保存到数据库并发送通知
    -- save_to_database("voicemails", voicemail_data)
    -- send_voicemail_notification(voicemail_data)
end

-- 提供替代IVR选项
local function provide_alternative_options()
    log_info("Providing alternative IVR options")
    
    -- 播放替代选项菜单
    session:execute("playback", "ivr/ivr-alternative_options.wav")
    session:execute("playback", "ivr/ivr-press_1_for_faq.wav")
    session:execute("playback", "ivr/ivr-press_2_for_order_status.wav")
    session:execute("playback", "ivr/ivr-press_3_for_business_hours.wav")
    
    local choice = session:playAndGetDigits(
        1, 1, 3, 5000, "#",
        "silence_stream://250",
        "ivr/ivr-invalid_entry.wav",
        "[1-3]"
    )
    
    if choice == "1" then
        -- 转到FAQ IVR
        session:execute("transfer", "faq_ivr XML default")
        return true
    elseif choice == "2" then
        -- 转到订单查询IVR
        session:execute("transfer", "order_status XML default")
        return true
    elseif choice == "3" then
        -- 播放营业时间
        session:execute("playback", "ivr/ivr-business_hours.wav")
        return true
    end
    
    return false
end

-- 重试转人工
local function retry_transfer()
    if retry_count < config.max_retry_count then
        log_info("Retrying transfer, attempt " .. (retry_count + 1))
        
        session:setVariable("transfer_retry_count", tostring(retry_count + 1))
        
        -- 播放重试提示
        session:execute("playback", "ivr/ivr-please_wait_retrying.wav")
        
        -- 重新尝试转人工
        session:execute("transfer", "agent_queue XML transfer_to_agent")
        return true
    else
        log_error("Max retry count exceeded")
        return false
    end
end

-- 主函数
local function main()
    log_error("Transfer to agent failed for caller: " .. caller_id)
    
    -- 检查是否在工作时间
    local in_business_hours = is_business_hours()
    
    if not in_business_hours then
        -- 非工作时间提示
        session:execute("playback", "ivr/ivr-outside_business_hours.wav")
        
        -- 只提供留言和回调选项
        if config.voicemail_enabled then
            handle_voicemail()
        end
    else
        -- 工作时间内，提供所有选项
        session:execute("playback", "ivr/ivr-no_agents_available.wav")
        
        -- 播放选项菜单
        session:execute("playback", "ivr/ivr-press_1_to_wait.wav")
        
        if config.callback_enabled then
            session:execute("playback", "ivr/ivr-press_2_for_callback.wav")
        end
        
        if config.voicemail_enabled then
            session:execute("playback", "ivr/ivr-press_3_for_voicemail.wav")
        end
        
        if config.alternative_ivr then
            session:execute("playback", "ivr/ivr-press_4_for_other_options.wav")
        end
        
        local choice = session:playAndGetDigits(
            1, 1, 3, 10000, "#",
            "silence_stream://250",
            "ivr/ivr-invalid_entry.wav",
            "[1-4]"
        )
        
        if choice == "1" then
            -- 继续等待或重试
            retry_transfer()
        elseif choice == "2" and config.callback_enabled then
            -- 请求回调
            handle_callback_request()
        elseif choice == "3" and config.voicemail_enabled then
            -- 留言
            handle_voicemail()
        elseif choice == "4" and config.alternative_ivr then
            -- 其他选项
            provide_alternative_options()
        else
            -- 默认操作
            session:execute("playback", "ivr/ivr-thank_you_for_calling.wav")
        end
    end
    
    -- 记录处理结果
    local failure_data = {
        uuid = uuid,
        caller_id = caller_id,
        failure_time = os.date("%Y-%m-%d %H:%M:%S"),
        transfer_reason = transfer_reason,
        retry_count = retry_count,
        in_business_hours = in_business_hours
    }
    
    log_info("Transfer failure handled: " .. freeswitch.serialize(failure_data))
end

-- 执行主函数
if session:ready() then
    main()
else
    log_error("Session not ready")
end