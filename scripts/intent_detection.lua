-- intent_detection.lua
-- FreeSwitch 用户转人工意图识别脚本

-- 初始化
local log = freeswitch.consoleLog

-- 转人工关键词配置
local TRANSFER_KEYWORDS = {
    -- 直接转人工请求
    ["人工"] = {weight = 10, category = "direct"},
    ["客服"] = {weight = 10, category = "direct"},
    ["转人工"] = {weight = 10, category = "direct"},
    ["找客服"] = {weight = 10, category = "direct"},
    ["manual"] = {weight = 10, category = "direct"},
    ["agent"] = {weight = 10, category = "direct"},
    ["human"] = {weight = 10, category = "direct"},
    
    -- 问题解决请求
    ["投诉"] = {weight = 8, category = "complaint"},
    ["问题"] = {weight = 7, category = "issue"},
    ["故障"] = {weight = 8, category = "issue"},
    ["不能用"] = {weight = 8, category = "issue"},
    ["用不了"] = {weight = 8, category = "issue"},
    ["坏了"] = {weight = 8, category = "issue"},
    ["退款"] = {weight = 9, category = "financial"},
    ["赔偿"] = {weight = 9, category = "complaint"},
    
    -- 情绪化表达
    ["生气"] = {weight = 6, category = "emotional"},
    ["愤怒"] = {weight = 7, category = "emotional"},
    ["不满意"] = {weight = 7, category = "emotional"},
    ["太差"] = {weight = 6, category = "emotional"},
    ["垃圾"] = {weight = 5, category = "emotional"},
    
    -- 否定机器人
    ["不要机器人"] = {weight = 9, category = "reject_bot"},
    ["机器人听不懂"] = {weight = 8, category = "reject_bot"},
    ["机器人太笨"] = {weight = 7, category = "reject_bot"},
    ["听不懂"] = {weight = 6, category = "reject_bot"}
}

-- 设置全局变量用于记录
function set_session_variable(name, value)
    if session then
        session:setVariable(name, tostring(value))
    end
end

-- 语音转文字并检测意图
function detect_intent_from_speech()
    log("INFO", "开始语音意图检测...")
    
    -- 配置语音识别参数
    local asr_grammar = "grammar/intent_detection.gram"
    local timeout = 10000  -- 10秒超时
    
    -- 播放提示音
    if session then
        session:streamFile("sounds/please_speak.wav")
        
        -- 开始语音识别
        session:execute("detect_speech", "pocketsphinx default default")
        session:setVariable("detect_speech_result", "")
        
        -- 等待语音输入
        local speech_result = session:getVariable("detect_speech_result")
        
        if speech_result and speech_result ~= "" then
            log("INFO", "识别到语音: " .. speech_result)
            return analyze_text_intent(speech_result)
        else
            log("WARN", "未检测到有效语音输入")
            return {intent = "no_input", confidence = 0}
        end
    end
    
    return {intent = "unknown", confidence = 0}
end

-- 分析文本意图
function analyze_text_intent(text)
    if not text or text == "" then
        return {intent = "no_input", confidence = 0}
    end
    
    local text_lower = string.lower(text)
    local total_weight = 0
    local matched_categories = {}
    local matched_keywords = {}
    
    log("INFO", "分析文本意图: " .. text)
    
    -- 遍历关键词进行匹配
    for keyword, info in pairs(TRANSFER_KEYWORDS) do
        if string.find(text_lower, keyword) then
            total_weight = total_weight + info.weight
            table.insert(matched_keywords, keyword)
            
            -- 记录匹配的类别
            if not matched_categories[info.category] then
                matched_categories[info.category] = 0
            end
            matched_categories[info.category] = matched_categories[info.category] + info.weight
        end
    end
    
    -- 计算置信度
    local confidence = math.min(total_weight / 10, 1.0)  -- 标准化到0-1
    
    -- 确定主要意图
    local main_intent = "no_transfer"
    if confidence >= 0.7 then
        main_intent = "transfer_to_human"
    elseif confidence >= 0.4 then
        main_intent = "possible_transfer"
    end
    
    -- 找出主要类别
    local main_category = "general"
    local max_category_weight = 0
    for category, weight in pairs(matched_categories) do
        if weight > max_category_weight then
            max_category_weight = weight
            main_category = category
        end
    end
    
    local result = {
        intent = main_intent,
        confidence = confidence,
        category = main_category,
        keywords = matched_keywords,
        total_weight = total_weight,
        original_text = text
    }
    
    log("INFO", string.format("意图分析结果 - 意图: %s, 置信度: %.2f, 类别: %s", 
        main_intent, confidence, main_category))
    
    return result
end

-- DTMF 按键检测
function detect_dtmf_intent()
    if not session then
        return {intent = "no_session", confidence = 0}
    end
    
    log("INFO", "等待按键输入...")
    
    -- 播放按键提示
    session:streamFile("sounds/press_0_for_agent.wav")
    
    -- 等待DTMF输入
    local dtmf = session:getDigits(1, "#", 5000)  -- 等待1位数字，5秒超时
    
    if dtmf == "0" then
        log("INFO", "用户按0转人工")
        return {
            intent = "transfer_to_human",
            confidence = 1.0,
            category = "dtmf",
            method = "keypress"
        }
    elseif dtmf == "*" then
        -- 重复菜单
        return {intent = "repeat_menu", confidence = 1.0}
    elseif dtmf and dtmf ~= "" then
        log("INFO", "用户按键: " .. dtmf)
        return {
            intent = "other_option",
            confidence = 1.0,
            dtmf_pressed = dtmf
        }
    else
        log("INFO", "未检测到按键输入")
        return {intent = "no_input", confidence = 0}
    end
end

-- 综合意图检测（语音 + DTMF）
function comprehensive_intent_detection()
    local results = {}
    
    -- 首先尝试DTMF检测
    local dtmf_result = detect_dtmf_intent()
    table.insert(results, dtmf_result)
    
    -- 如果DTMF没有明确结果，尝试语音识别
    if dtmf_result.intent == "no_input" or dtmf_result.confidence < 0.5 then
        local speech_result = detect_intent_from_speech()
        table.insert(results, speech_result)
    end
    
    -- 选择最佳结果
    local best_result = {intent = "no_transfer", confidence = 0}
    for _, result in ipairs(results) do
        if result.confidence > best_result.confidence then
            best_result = result
        end
    end
    
    return best_result
end

-- 主函数
function main()
    log("INFO", "========== 开始用户意图检测 ==========")
    
    if not session then
        log("ERROR", "无法获取 session 对象")
        return
    end
    
    -- 获取呼叫信息
    local caller_number = session:getVariable("caller_id_number") or "unknown"
    local called_number = session:getVariable("destination_number") or "unknown"
    
    log("INFO", string.format("呼叫信息 - 主叫: %s, 被叫: %s", caller_number, called_number))
    
    -- 设置初始变量
    set_session_variable("intent_detection_start", os.time())
    set_session_variable("caller_number", caller_number)
    
    -- 执行意图检测
    local intent_result = comprehensive_intent_detection()
    
    -- 记录检测结果
    set_session_variable("detected_intent", intent_result.intent)
    set_session_variable("intent_confidence", intent_result.confidence)
    set_session_variable("intent_category", intent_result.category or "general")
    set_session_variable("detection_method", intent_result.method or "mixed")
    
    -- 根据意图执行相应动作
    if intent_result.intent == "transfer_to_human" then
        log("NOTICE", string.format("检测到转人工意图 - 置信度: %.2f", intent_result.confidence))
        set_session_variable("transfer_reason", "user_intent_detected")
        
        -- 设置优先级
        if intent_result.category == "complaint" or intent_result.category == "financial" then
            set_session_variable("queue_priority", "high")
        elseif intent_result.category == "direct" then
            set_session_variable("queue_priority", "normal")
        end
        
        -- 播放转人工提示
        session:streamFile("sounds/transferring_to_agent.wav")
        
    elseif intent_result.intent == "possible_transfer" then
        log("INFO", "检测到可能的转人工需求，进行确认")
        set_session_variable("transfer_reason", "possible_intent")
        
        -- 播放确认提示
        session:streamFile("sounds/confirm_transfer.wav")
        
        -- 再次检测确认
        local confirm_result = detect_dtmf_intent()
        if confirm_result.intent == "transfer_to_human" then
            set_session_variable("detected_intent", "transfer_to_human")
            set_session_variable("transfer_reason", "user_confirmed")
            session:streamFile("sounds/transferring_to_agent.wav")
        end
        
    else
        log("INFO", "未检测到转人工意图，继续自动服务")
        set_session_variable("transfer_reason", "no_intent")
    end
    
    set_session_variable("intent_detection_end", os.time())
    
    log("INFO", "========== 意图检测完成 ==========")
end

-- 执行主函数
main()