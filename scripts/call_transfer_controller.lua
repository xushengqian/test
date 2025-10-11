-- call_transfer_controller.lua
-- 呼叫转移主控制脚本

local log = freeswitch.consoleLog

-- 转移配置
local TRANSFER_CONFIG = {
    max_transfer_attempts = 3,
    transfer_timeout = 45000,  -- 45秒转移超时
    retry_delay = 2000,        -- 2秒重试延迟
    fallback_actions = {
        "voicemail",
        "callback_request", 
        "emergency_queue"
    }
}

-- 转移结果跟踪
local transfer_session_data = {}

-- 记录转移开始
function log_transfer_start()
    local caller_number = session:getVariable("caller_id_number") or "unknown"
    local transfer_reason = session:getVariable("transfer_reason") or "unknown"
    local detected_intent = session:getVariable("detected_intent") or "unknown"
    
    local transfer_data = {
        caller_number = caller_number,
        transfer_reason = transfer_reason, 
        detected_intent = detected_intent,
        start_time = os.time(),
        start_timestamp = os.date("%Y-%m-%d %H:%M:%S"),
        attempts = 0,
        success = false
    }
    
    -- 存储到会话变量
    session:setVariable("transfer_start_time", transfer_data.start_time)
    session:setVariable("transfer_caller", caller_number)
    
    log("INFO", string.format("开始转移处理 - 主叫: %s, 原因: %s, 意图: %s", 
        caller_number, transfer_reason, detected_intent))
    
    return transfer_data
end

-- 执行队列转移
function execute_queue_transfer(queue_name, priority)
    log("INFO", "准备转移到队列: " .. queue_name)
    
    -- 设置转移参数
    session:setVariable("transfer_queue", queue_name)
    session:setVariable("queue_priority", priority or "normal")
    
    -- 播放转移提示音
    session:streamFile("sounds/transferring_please_wait.wav")
    
    -- 设置转移超时
    session:execute("set", "call_timeout=" .. (TRANSFER_CONFIG.transfer_timeout / 1000))
    
    -- 执行FIFO队列转移
    local transfer_result = session:execute("fifo", queue_name .. " in")
    
    -- 检查转移结果
    local hangup_cause = session:getVariable("hangup_cause")
    local transfer_success = (hangup_cause == nil or hangup_cause == "NORMAL_CLEARING")
    
    log("INFO", string.format("队列转移结果 - 成功: %s, 挂断原因: %s", 
        tostring(transfer_success), hangup_cause or "none"))
    
    return transfer_success
end

-- 处理转移失败
function handle_transfer_failure(attempt_count)
    log("WARN", string.format("转移失败，尝试次数: %d/%d", attempt_count, TRANSFER_CONFIG.max_transfer_attempts))
    
    if attempt_count >= TRANSFER_CONFIG.max_transfer_attempts then
        log("ERROR", "达到最大转移尝试次数，执行失败回退处理")
        return execute_fallback_actions()
    end
    
    -- 播放重试提示
    session:streamFile("sounds/transfer_retry.wav")
    session:sleep(TRANSFER_CONFIG.retry_delay)
    
    return false  -- 继续重试
end

-- 执行失败回退操作
function execute_fallback_actions()
    log("WARN", "执行转移失败的回退操作")
    
    -- 询问用户偏好
    session:streamFile("sounds/transfer_failed_options.wav")
    
    local user_choice = session:getDigits(1, "#", 10000)
    
    if user_choice == "1" then
        -- 留言
        log("INFO", "用户选择留言")
        session:setVariable("fallback_action", "voicemail") 
        session:execute("voicemail", "default $${domain} 1000")
        
    elseif user_choice == "2" then
        -- 回呼请求
        log("INFO", "用户选择回呼请求")
        session:setVariable("fallback_action", "callback_request")
        session:execute("lua", "callback_request.lua")
        
    elseif user_choice == "3" then
        -- 紧急队列
        log("INFO", "用户选择紧急处理")
        session:setVariable("fallback_action", "emergency_queue")
        return execute_queue_transfer("priority_agents_queue@$${domain}", "emergency")
        
    else
        -- 默认操作 - 留言
        log("INFO", "默认选择留言")
        session:setVariable("fallback_action", "voicemail_default")
        session:execute("voicemail", "default $${domain} 1000")
    end
    
    return true
end

-- 智能转移策略
function intelligent_transfer_strategy()
    -- 获取之前的意图分析结果
    local detected_intent = session:getVariable("detected_intent") or "unknown"
    local intent_category = session:getVariable("intent_category") or "general"
    local caller_priority = session:getVariable("caller_priority") or "normal"
    
    -- 执行智能路由
    session:execute("lua", "smart_queue_router.lua")
    
    -- 获取路由结果
    local final_queue = session:getVariable("final_queue")
    local routing_decision = session:getVariable("routing_decision")
    
    log("INFO", "智能路由完成: " .. (routing_decision or "无决策信息"))
    
    return final_queue or "human_agents_queue@$${domain}"
end

-- 监控转移过程
function monitor_transfer_progress()
    local start_time = session:getVariable("transfer_start_time")
    if not start_time then return end
    
    local elapsed_time = os.time() - tonumber(start_time)
    local caller_number = session:getVariable("caller_id_number")
    
    -- 每30秒记录一次进度
    if elapsed_time % 30 == 0 then
        log("INFO", string.format("转移进度监控 - 主叫: %s, 已等待: %d秒", 
            caller_number, elapsed_time))
            
        -- 如果等待时间过长，提供额外选项
        if elapsed_time > 120 then  -- 2分钟
            session:streamFile("sounds/long_wait_options.wav")
        end
    end
end

-- 记录转移完成
function log_transfer_completion(success, final_action)
    local start_time = tonumber(session:getVariable("transfer_start_time") or "0")
    local end_time = os.time()
    local total_time = end_time - start_time
    
    local caller_number = session:getVariable("caller_id_number") or "unknown"
    local transfer_queue = session:getVariable("transfer_queue") or "unknown"
    
    session:setVariable("transfer_end_time", end_time)
    session:setVariable("transfer_total_time", total_time)
    session:setVariable("transfer_success", tostring(success))
    session:setVariable("final_action", final_action or "unknown")
    
    local result_log = string.format(
        "转移完成 - 主叫: %s, 成功: %s, 队列: %s, 总时长: %d秒, 最终动作: %s",
        caller_number, tostring(success), transfer_queue, total_time, final_action or "unknown"
    )
    
    log("NOTICE", result_log)
    
    -- 发送自定义事件用于统计分析
    local event = freeswitch.Event("CUSTOM", "transfer::completed")
    event:addHeader("Caller-Number", caller_number)
    event:addHeader("Transfer-Success", tostring(success))
    event:addHeader("Transfer-Queue", transfer_queue)
    event:addHeader("Transfer-Duration", tostring(total_time))
    event:addHeader("Final-Action", final_action or "unknown")
    event:fire()
end

-- 主转移控制函数
function main()
    log("INFO", "========== 启动呼叫转移控制器 ==========")
    
    if not session then
        log("ERROR", "无法获取session对象")
        return
    end
    
    -- 记录转移开始
    local transfer_data = log_transfer_start()
    
    -- 检查是否真的需要转移
    local detected_intent = session:getVariable("detected_intent") or "unknown"
    if detected_intent == "no_transfer" then
        log("INFO", "检测结果表明无需转移，退出转移流程")
        session:setVariable("transfer_skipped", "true")
        return
    end
    
    local success = false
    local final_action = "transfer_attempt"
    
    -- 开始转移尝试循环
    for attempt = 1, TRANSFER_CONFIG.max_transfer_attempts do
        log("INFO", string.format("转移尝试 %d/%d", attempt, TRANSFER_CONFIG.max_transfer_attempts))
        
        -- 确定目标队列
        local target_queue = intelligent_transfer_strategy()
        
        -- 执行转移
        success = execute_queue_transfer(target_queue, session:getVariable("caller_priority"))
        
        if success then
            log("NOTICE", "转移成功完成")
            final_action = "transfer_success"
            break
        else
            -- 处理转移失败
            local should_continue = not handle_transfer_failure(attempt)
            if not should_continue then
                final_action = "fallback_executed"
                success = true  -- 回退操作视为成功处理
                break
            end
        end
    end
    
    -- 记录转移完成
    log_transfer_completion(success, final_action)
    
    log("INFO", "========== 呼叫转移控制完成 ==========")
end

-- 执行主函数
main()