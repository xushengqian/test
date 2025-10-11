-- FreeSWITCH Lua脚本: 检测用户转人工意图
-- 通过语音识别或DTMF输入检测用户是否想要转人工

-- 转人工关键词列表
local transfer_keywords = {
    "转人工",
    "人工服务",
    "转接客服",
    "找人工",
    "我要人工",
    "帮我转人工",
    "需要人工",
    "客服",
    "转坐席",
    "agent",
    "human",
    "operator",
    "representative"
}

-- 情绪检测阈值
local emotion_thresholds = {
    anger = 0.7,      -- 愤怒
    frustration = 0.6, -- 沮丧
    impatience = 0.5   -- 不耐烦
}

-- 获取会话变量
local uuid = session:get_uuid()
local caller_id = session:getVariable("caller_id_number")
local call_duration = session:getVariable("billsec") or 0

-- 日志函数
local function log_info(message)
    freeswitch.consoleLog("info", "[TransferIntent] " .. message .. "\n")
end

-- 检测DTMF输入
local function check_dtmf_input()
    local dtmf = session:getVariable("dtmf_digits")
    
    -- 按0通常表示转人工
    if dtmf == "0" then
        log_info("DTMF detected: User pressed 0 for agent transfer")
        return true
    end
    
    return false
end

-- 分析语音文本中的转人工意图
local function analyze_text_intent(text)
    if not text or text == "" then
        return false
    end
    
    text = string.lower(text)
    
    -- 检查关键词
    for _, keyword in ipairs(transfer_keywords) do
        if string.find(text, keyword) then
            log_info("Transfer keyword detected: " .. keyword)
            return true
        end
    end
    
    return false
end

-- 检测用户情绪
local function detect_emotion()
    -- 这里可以集成情绪识别API
    -- 示例：通过音频特征判断用户情绪
    
    local emotion_score = tonumber(session:getVariable("emotion_score") or "0")
    local emotion_type = session:getVariable("emotion_type") or "neutral"
    
    -- 检查负面情绪
    if emotion_type == "anger" and emotion_score >= emotion_thresholds.anger then
        log_info("High anger level detected: " .. emotion_score)
        return true
    elseif emotion_type == "frustration" and emotion_score >= emotion_thresholds.frustration then
        log_info("High frustration level detected: " .. emotion_score)
        return true
    end
    
    return false
end

-- 检查重复失败次数
local function check_failure_count()
    local failure_count = tonumber(session:getVariable("recognition_failures") or "0")
    local max_failures = 3
    
    if failure_count >= max_failures then
        log_info("Recognition failures exceeded threshold: " .. failure_count)
        return true
    end
    
    return false
end

-- 检查通话时长
local function check_call_duration()
    local duration = tonumber(call_duration)
    local max_ivr_duration = 180 -- 3分钟
    
    if duration >= max_ivr_duration then
        log_info("IVR duration exceeded: " .. duration .. " seconds")
        return true
    end
    
    return false
end

-- 主函数
local function main()
    log_info("Starting transfer intent detection for caller: " .. (caller_id or "unknown"))
    
    -- 获取ASR识别结果
    local asr_text = session:getVariable("asr_result") or ""
    
    local should_transfer = false
    local transfer_reason = ""
    
    -- 1. 检查DTMF输入
    if check_dtmf_input() then
        should_transfer = true
        transfer_reason = "dtmf_request"
    
    -- 2. 检查语音文本意图
    elseif analyze_text_intent(asr_text) then
        should_transfer = true
        transfer_reason = "voice_request"
    
    -- 3. 检查用户情绪
    elseif detect_emotion() then
        should_transfer = true
        transfer_reason = "negative_emotion"
    
    -- 4. 检查失败次数
    elseif check_failure_count() then
        should_transfer = true
        transfer_reason = "multiple_failures"
    
    -- 5. 检查通话时长
    elseif check_call_duration() then
        should_transfer = true
        transfer_reason = "timeout"
    end
    
    -- 设置转人工标志
    if should_transfer then
        session:setVariable("transfer_to_agent", "true")
        session:setVariable("transfer_reason", transfer_reason)
        session:setVariable("transfer_request_time", os.date("%Y-%m-%d %H:%M:%S"))
        
        log_info("Transfer to agent triggered. Reason: " .. transfer_reason)
        
        -- 执行转人工
        session:execute("transfer", "agent_queue XML transfer_to_agent")
    else
        session:setVariable("transfer_to_agent", "false")
        log_info("No transfer intent detected")
    end
end

-- 执行主函数
if session:ready() then
    main()
else
    log_info("Session not ready")
end