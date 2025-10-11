-- voice_intent_processor.lua
-- 语音处理和意图识别的主要脚本

local log = freeswitch.consoleLog

-- 配置参数
local CONFIG = {
    max_retry_attempts = 3,
    speech_timeout = 8000,      -- 8秒语音超时
    dtmf_timeout = 5000,        -- 5秒按键超时  
    silence_threshold = 3000,   -- 3秒静音阈值
    confidence_threshold = 0.6   -- 意图置信度阈值
}

-- 语音菜单选项
local MENU_OPTIONS = {
    ["1"] = {action = "billing_inquiry", desc = "账单查询"},
    ["2"] = {action = "technical_support", desc = "技术支持"},
    ["3"] = {action = "business_consultation", desc = "业务咨询"},  
    ["0"] = {action = "transfer_to_human", desc = "转人工服务"},
    ["*"] = {action = "repeat_menu", desc = "重复菜单"}
}

-- 播放主菜单
function play_main_menu()
    if not session then return false end
    
    log("INFO", "播放主菜单")
    
    -- 播放欢迎语和菜单选项
    session:streamFile("sounds/welcome_menu.wav")
    session:sleep(500)
    session:streamFile("sounds/press_1_billing.wav")
    session:sleep(300)
    session:streamFile("sounds/press_2_technical.wav") 
    session:sleep(300)
    session:streamFile("sounds/press_3_business.wav")
    session:sleep(300)
    session:streamFile("sounds/press_0_agent.wav")
    session:sleep(300)
    session:streamFile("sounds/press_star_repeat.wav")
    
    return true
end

-- 处理菜单选择
function handle_menu_selection(digit)
    if not digit or digit == "" then
        return {success = false, action = "no_input"}
    end
    
    local option = MENU_OPTIONS[digit]
    if option then
        log("INFO", "用户选择: " .. digit .. " (" .. option.desc .. ")")
        session:setVariable("menu_selection", digit)
        session:setVariable("selected_service", option.action)
        
        return {
            success = true, 
            action = option.action,
            description = option.desc
        }
    else
        log("WARN", "无效的菜单选择: " .. digit)
        return {success = false, action = "invalid_selection"}
    end
end

-- 智能语音识别处理
function process_speech_input()
    if not session then return nil end
    
    log("INFO", "开始语音识别处理")
    
    -- 启动语音检测
    session:execute("detect_speech", "pocketsphinx default default")
    session:setVariable("detect_speech_result", "")
    
    -- 播放语音提示
    session:streamFile("sounds/speak_your_request.wav")
    
    -- 开始录音检测
    session:execute("detect_speech", "start")
    
    -- 等待语音输入
    local start_time = os.time()
    local speech_detected = false
    
    while (os.time() - start_time) < (CONFIG.speech_timeout / 1000) do
        local dtmf = session:getDigits(1, "", 1000)  -- 非阻塞检测DTMF
        
        if dtmf and dtmf ~= "" then
            -- 如果检测到按键，优先处理按键
            session:execute("detect_speech", "stop")
            return handle_menu_selection(dtmf)
        end
        
        -- 检查是否检测到语音
        local speech_result = session:getVariable("detect_speech_result")
        if speech_result and speech_result ~= "" then
            speech_detected = true
            break
        end
        
        -- 检查通话是否还活跃
        if not session:ready() then
            break
        end
    end
    
    -- 停止语音检测
    session:execute("detect_speech", "stop")
    
    if speech_detected then
        local speech_text = session:getVariable("detect_speech_result")
        log("INFO", "检测到语音内容: " .. (speech_text or "empty"))
        
        -- 调用意图分析
        local intent_result = analyze_speech_intent(speech_text)
        return intent_result
    else
        log("WARN", "语音识别超时，未检测到有效输入")
        return {success = false, action = "speech_timeout"}
    end
end

-- 语音意图分析
function analyze_speech_intent(speech_text)
    if not speech_text or speech_text == "" then
        return {success = false, action = "no_speech"}
    end
    
    local text_lower = string.lower(speech_text)
    log("INFO", "分析语音意图: " .. speech_text)
    
    -- 转人工意图关键词
    local transfer_keywords = {
        "人工", "客服", "转人工", "找人", "manual", "agent", "human",
        "投诉", "问题", "故障", "不满", "退款"
    }
    
    -- 业务类型关键词  
    local service_keywords = {
        billing = {"账单", "费用", "话费", "余额", "充值", "billing"},
        technical = {"故障", "不通", "网络", "信号", "technical", "support"},
        business = {"办理", "开通", "套餐", "业务", "咨询", "business"}
    }
    
    -- 检测转人工意图
    for _, keyword in ipairs(transfer_keywords) do
        if string.find(text_lower, keyword) then
            log("NOTICE", "检测到转人工关键词: " .. keyword)
            session:setVariable("transfer_reason", "speech_intent_" .. keyword)
            return {
                success = true,
                action = "transfer_to_human", 
                confidence = 0.9,
                keyword = keyword,
                method = "speech"
            }
        end
    end
    
    -- 检测具体业务意图
    for service_type, keywords in pairs(service_keywords) do
        for _, keyword in ipairs(keywords) do
            if string.find(text_lower, keyword) then
                log("INFO", "检测到业务类型: " .. service_type .. ", 关键词: " .. keyword)
                return {
                    success = true,
                    action = service_type .. "_service",
                    confidence = 0.8,
                    service_type = service_type,
                    keyword = keyword,
                    method = "speech"
                }
            end
        end
    end
    
    log("INFO", "未识别出明确意图，建议重新输入")
    return {
        success = false, 
        action = "unclear_intent",
        original_text = speech_text
    }
end

-- 多模态输入处理（DTMF + 语音）
function multimodal_input_handler()
    local attempts = 0
    local max_attempts = CONFIG.max_retry_attempts
    
    while attempts < max_attempts do
        attempts = attempts + 1
        log("INFO", string.format("输入处理尝试 %d/%d", attempts, max_attempts))
        
        -- 播放菜单（首次或重复）
        if attempts == 1 or last_action == "repeat_menu" then
            play_main_menu()
        end
        
        -- 同时监听DTMF和语音
        session:setInputCallback("input_callback", "")
        session:setVariable("input_mode", "multimodal")
        
        -- 等待用户输入
        local input_start = os.time()
        local input_received = false
        local result = nil
        
        while (os.time() - input_start) < (CONFIG.dtmf_timeout / 1000) and not input_received do
            -- 检测DTMF按键
            local dtmf = session:getDigits(1, "", 1000)
            if dtmf and dtmf ~= "" then
                result = handle_menu_selection(dtmf)
                result.method = "dtmf"
                input_received = true
                break
            end
            
            -- 检查通话状态
            if not session:ready() then
                return {success = false, action = "call_ended"}
            end
        end
        
        -- 如果没有DTMF输入，尝试语音识别
        if not input_received then
            log("INFO", "未检测到按键，尝试语音识别")
            result = process_speech_input()
        end
        
        -- 处理输入结果
        if result and result.success then
            log("INFO", "成功处理用户输入: " .. result.action)
            session:setVariable("final_user_action", result.action)
            session:setVariable("input_method", result.method or "unknown")
            return result
            
        elseif result and result.action == "repeat_menu" then
            log("INFO", "用户请求重复菜单")
            last_action = "repeat_menu"
            -- 继续循环重复菜单
            
        elseif result and result.action == "invalid_selection" then
            log("WARN", "用户输入无效")
            session:streamFile("sounds/invalid_selection.wav")
            
        else
            log("WARN", "输入处理失败，重试")
            session:streamFile("sounds/no_input_retry.wav")
        end
        
        -- 短暂停顿后重试
        session:sleep(1000)
    end
    
    -- 达到最大尝试次数
    log("WARN", "达到最大重试次数，转到默认处理")
    session:streamFile("sounds/transfer_to_default.wav")
    
    return {
        success = true,
        action = "transfer_to_human", 
        reason = "max_attempts_reached"
    }
end

-- 主处理函数
function main()
    log("INFO", "========== 启动语音意图处理器 ==========")
    
    if not session then
        log("ERROR", "无法获取session对象")
        return
    end
    
    -- 记录处理开始时间
    session:setVariable("voice_processing_start", os.time())
    session:setVariable("caller_number", session:getVariable("caller_id_number") or "unknown")
    
    -- 设置语音识别参数
    session:execute("set", "detect_speech_result=")
    
    -- 执行多模态输入处理
    local final_result = multimodal_input_handler()
    
    -- 根据最终结果执行相应操作
    if final_result.action == "transfer_to_human" then
        log("NOTICE", "准备转接人工客服")
        session:setVariable("transfer_requested", "true")
        session:setVariable("transfer_source", "voice_intent_processor")
        
        -- 设置转接优先级
        if final_result.reason == "max_attempts_reached" then
            session:setVariable("transfer_priority", "high")
        else
            session:setVariable("transfer_priority", "normal")
        end
        
        session:streamFile("sounds/connecting_to_agent.wav")
        
    elseif string.find(final_result.action, "_service$") then
        log("INFO", "处理具体业务: " .. final_result.action)
        session:setVariable("service_type", final_result.action)
        -- 这里可以转到相应的业务处理流程
        
    else
        log("INFO", "其他处理结果: " .. (final_result.action or "unknown"))
    end
    
    -- 记录处理结束时间
    session:setVariable("voice_processing_end", os.time())
    
    log("INFO", "========== 语音意图处理完成 ==========")
end

-- 执行主函数
main()