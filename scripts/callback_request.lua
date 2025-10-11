-- callback_request.lua
-- 回呼请求处理脚本

local log = freeswitch.consoleLog

-- 回呼配置
local CALLBACK_CONFIG = {
    max_callback_attempts = 3,
    callback_retry_interval = 3600,  -- 1小时重试间隔
    callback_timeout = 30,           -- 30秒回呼超时
    business_hours = {
        start_hour = 9,
        end_hour = 18
    },
    callback_queue_limit = 100       -- 最大回呼队列长度
}

-- 验证回呼号码
function validate_callback_number(phone_number)
    if not phone_number or phone_number == "" then
        return false, "号码为空"
    end
    
    -- 简单的号码格式验证
    if not string.match(phone_number, "^1[3-9]%d{9}$") then
        return false, "号码格式不正确"
    end
    
    return true, "号码有效"
end

-- 获取回呼号码
function get_callback_number()
    local caller_number = session:getVariable("caller_id_number")
    
    -- 首先询问是否使用当前号码
    if caller_number and caller_number ~= "" and caller_number ~= "unknown" then
        session:streamFile("sounds/confirm_callback_number.wav")
        session:streamFile("sounds/your_number_is.wav")
        
        -- 播报号码
        for i = 1, #caller_number do
            local digit = caller_number:sub(i, i)
            session:streamFile("sounds/digits/" .. digit .. ".wav")
            session:sleep(200)
        end
        
        session:streamFile("sounds/press_1_confirm_2_change.wav")
        
        local choice = session:getDigits(1, "#", 10000)
        
        if choice == "1" then
            return caller_number
        end
    end
    
    -- 输入新号码
    session:streamFile("sounds/enter_callback_number.wav")
    
    local new_number = ""
    local attempts = 0
    local max_attempts = 3
    
    while attempts < max_attempts do
        attempts = attempts + 1
        
        new_number = session:getDigits(11, "#", 15000)
        
        if new_number and new_number ~= "" then
            local valid, msg = validate_callback_number(new_number)
            
            if valid then
                -- 确认号码
                session:streamFile("sounds/confirm_entered_number.wav")
                for i = 1, #new_number do
                    local digit = new_number:sub(i, i)
                    session:streamFile("sounds/digits/" .. digit .. ".wav")
                    session:sleep(200)
                end
                
                session:streamFile("sounds/press_1_confirm_2_retry.wav")
                local confirm = session:getDigits(1, "#", 5000)
                
                if confirm == "1" then
                    return new_number
                end
            else
                log("WARN", "无效的回呼号码: " .. new_number .. " (" .. msg .. ")")
                session:streamFile("sounds/invalid_number_retry.wav")
            end
        else
            session:streamFile("sounds/no_number_entered.wav")
        end
    end
    
    log("ERROR", "获取回呼号码失败，达到最大尝试次数")
    return nil
end

-- 选择回呼时间
function select_callback_time()
    session:streamFile("sounds/select_callback_time.wav")
    session:streamFile("sounds/press_1_now_2_later.wav")
    
    local choice = session:getDigits(1, "#", 10000)
    
    if choice == "1" then
        -- 立即回呼
        return {
            type = "immediate",
            scheduled_time = os.time() + 300,  -- 5分钟后
            description = "立即回呼"
        }
    elseif choice == "2" then
        -- 预约回呼时间
        return select_scheduled_time()
    else
        -- 默认立即回呼
        return {
            type = "immediate", 
            scheduled_time = os.time() + 300,
            description = "默认立即回呼"
        }
    end
end

-- 选择预约时间
function select_scheduled_time()
    session:streamFile("sounds/select_callback_hour.wav")
    
    local hour = session:getDigits(2, "#", 10000)
    hour = tonumber(hour)
    
    if not hour or hour < CALLBACK_CONFIG.business_hours.start_hour or 
       hour >= CALLBACK_CONFIG.business_hours.end_hour then
        log("WARN", "选择的时间超出工作时间: " .. (hour or "invalid"))
        session:streamFile("sounds/outside_business_hours.wav")
        
        -- 提供工作时间内的选择
        session:streamFile("sounds/business_hours_callback.wav")
        
        return {
            type = "business_hours",
            scheduled_time = get_next_business_hour(),
            description = "工作时间回呼"
        }
    end
    
    -- 计算今天的回呼时间
    local current_time = os.time()
    local current_date = os.date("*t", current_time)
    local scheduled_time = os.time({
        year = current_date.year,
        month = current_date.month, 
        day = current_date.day,
        hour = hour,
        min = 0,
        sec = 0
    })
    
    -- 如果时间已经过去，安排到明天
    if scheduled_time <= current_time then
        scheduled_time = scheduled_time + 24 * 3600  -- 加一天
    end
    
    return {
        type = "scheduled",
        scheduled_time = scheduled_time,
        description = string.format("预约 %02d:00 回呼", hour)
    }
end

-- 获取下一个工作时间
function get_next_business_hour()
    local current_time = os.time()
    local current_date = os.date("*t", current_time)
    
    -- 计算明天工作时间开始
    local next_business = os.time({
        year = current_date.year,
        month = current_date.month,
        day = current_date.day + 1,
        hour = CALLBACK_CONFIG.business_hours.start_hour,
        min = 0,
        sec = 0
    })
    
    return next_business
end

-- 保存回呼请求
function save_callback_request(callback_data)
    -- 生成回呼请求ID
    local request_id = "CB_" .. os.date("%Y%m%d_%H%M%S") .. "_" .. math.random(1000, 9999)
    
    callback_data.request_id = request_id
    callback_data.created_time = os.time()
    callback_data.status = "pending"
    callback_data.attempts = 0
    
    -- 在实际环境中，这里应该保存到数据库
    -- 目前使用会话变量模拟
    session:setVariable("callback_request_id", request_id)
    session:setVariable("callback_number", callback_data.phone_number)
    session:setVariable("callback_scheduled_time", callback_data.scheduled_time)
    session:setVariable("callback_type", callback_data.type)
    
    log("INFO", string.format("保存回呼请求: ID=%s, 号码=%s, 时间=%s", 
        request_id, callback_data.phone_number, 
        os.date("%Y-%m-%d %H:%M:%S", callback_data.scheduled_time)))
    
    -- 发送事件通知回呼系统
    local event = freeswitch.Event("CUSTOM", "callback::request_created")
    event:addHeader("Request-ID", request_id)
    event:addHeader("Callback-Number", callback_data.phone_number)
    event:addHeader("Scheduled-Time", tostring(callback_data.scheduled_time))
    event:addHeader("Request-Type", callback_data.type)
    event:addHeader("Original-Caller", callback_data.original_caller or "unknown")
    event:fire()
    
    return request_id
end

-- 播放回呼确认信息
function play_callback_confirmation(callback_data)
    session:streamFile("sounds/callback_request_confirmed.wav")
    
    -- 播报回呼时间
    if callback_data.type == "immediate" then
        session:streamFile("sounds/callback_within_minutes.wav")
    else
        session:streamFile("sounds/callback_at_scheduled_time.wav")
        local scheduled_date = os.date("*t", callback_data.scheduled_time)
        
        -- 播报日期时间（简化版）
        session:streamFile("sounds/on.wav")
        session:streamFile("sounds/digits/" .. scheduled_date.hour .. ".wav")
        session:streamFile("sounds/oclock.wav")
    end
    
    session:streamFile("sounds/callback_request_id.wav")
    
    -- 播报请求ID的最后4位
    local id_suffix = string.sub(callback_data.request_id, -4)
    for i = 1, #id_suffix do
        local digit = id_suffix:sub(i, i)
        session:streamFile("sounds/digits/" .. digit .. ".wav")
        session:sleep(200)
    end
    
    session:streamFile("sounds/thank_you_goodbye.wav")
end

-- 执行立即回呼测试
function execute_immediate_callback_test(phone_number)
    log("INFO", "执行立即回呼测试: " .. phone_number)
    
    -- 简单的回呼可达性测试
    local api = freeswitch.API()
    local originate_string = string.format("user/callback_test@$${domain} %s XML default", phone_number)
    
    -- 发起测试呼叫（短时间）
    local result = api:executeString("bgapi originate " .. originate_string .. " &playback(sounds/callback_test.wav)")
    
    if result and string.find(result, "SUCCESS") then
        log("INFO", "回呼测试成功")
        return true
    else
        log("WARN", "回呼测试失败: " .. (result or "unknown"))
        return false
    end
end

-- 主函数
function main()
    log("INFO", "========== 启动回呼请求处理 ==========")
    
    if not session then
        log("ERROR", "无法获取session对象")
        return
    end
    
    local original_caller = session:getVariable("caller_id_number") or "unknown"
    
    -- 获取回呼号码
    local callback_number = get_callback_number()
    if not callback_number then
        log("ERROR", "无法获取有效的回呼号码")
        session:streamFile("sounds/callback_failed.wav")
        return
    end
    
    -- 选择回呼时间
    local callback_time = select_callback_time()
    
    -- 准备回呼数据
    local callback_data = {
        phone_number = callback_number,
        original_caller = original_caller,
        type = callback_time.type,
        scheduled_time = callback_time.scheduled_time,
        description = callback_time.description,
        priority = session:getVariable("caller_priority") or "normal",
        intent_category = session:getVariable("intent_category") or "general"
    }
    
    -- 对于立即回呼，执行可达性测试
    if callback_time.type == "immediate" then
        local test_success = execute_immediate_callback_test(callback_number)
        callback_data.reachability_test = test_success
        
        if not test_success then
            session:streamFile("sounds/callback_test_failed.wav")
            -- 询问是否改为预约回呼
            session:streamFile("sounds/schedule_callback_instead.wav")
            local choice = session:getDigits(1, "#", 5000)
            
            if choice == "1" then
                callback_time = select_scheduled_time()
                callback_data.type = callback_time.type
                callback_data.scheduled_time = callback_time.scheduled_time
            end
        end
    end
    
    -- 保存回呼请求
    local request_id = save_callback_request(callback_data)
    
    -- 播放确认信息
    play_callback_confirmation(callback_data)
    
    log("NOTICE", string.format("回呼请求处理完成: ID=%s, 号码=%s", request_id, callback_number))
    
    log("INFO", "========== 回呼请求处理完成 ==========")
end

-- 执行主函数
main()