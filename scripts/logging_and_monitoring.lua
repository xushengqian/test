-- logging_and_monitoring.lua
-- 日志记录和监控系统

local log = freeswitch.consoleLog

-- 监控配置
local MONITORING_CONFIG = {
    -- 日志级别
    log_levels = {
        DEBUG = 7,
        INFO = 6, 
        NOTICE = 5,
        WARNING = 4,
        ERROR = 3,
        CRITICAL = 2,
        ALERT = 1,
        EMERGENCY = 0
    },
    
    -- 统计指标
    metrics = {
        call_volume = true,
        transfer_rate = true,
        queue_performance = true,
        agent_performance = true,
        intent_accuracy = true
    },
    
    -- 告警阈值
    alert_thresholds = {
        queue_wait_time = 300,      -- 5分钟等待告警
        transfer_failure_rate = 0.1, -- 10%转移失败率告警
        agent_response_time = 30,   -- 30秒响应时间告警
        call_abandonment_rate = 0.15 -- 15%放弃率告警
    }
}

-- 初始化日志记录器
function init_logger()
    local caller_number = session:getVariable("caller_id_number") or "unknown"
    local session_uuid = session:getVariable("uuid") or "unknown"
    local call_start = session:getVariable("call_start_time") or os.date("%Y-%m-%d %H:%M:%S")
    
    -- 设置日志上下文
    session:setVariable("log_context", string.format("[%s|%s]", caller_number, session_uuid:sub(1,8)))
    session:setVariable("log_session_start", call_start)
    
    log("INFO", session:getVariable("log_context") .. " 初始化日志记录器")
end

-- 结构化日志记录
function structured_log(level, event_type, message, additional_data)
    if not session then return end
    
    local log_context = session:getVariable("log_context") or "[unknown]"
    local timestamp = os.date("%Y-%m-%d %H:%M:%S")
    
    -- 构建结构化日志
    local log_entry = {
        timestamp = timestamp,
        level = level,
        event_type = event_type,
        message = message,
        caller_number = session:getVariable("caller_id_number") or "unknown",
        session_uuid = session:getVariable("uuid") or "unknown",
        additional_data = additional_data or {}
    }
    
    -- 序列化为JSON格式（简化版）
    local json_log = string.format(
        '{"timestamp":"%s","level":"%s","event_type":"%s","message":"%s","caller":"%s","uuid":"%s"}',
        log_entry.timestamp, log_entry.level, log_entry.event_type, 
        log_entry.message, log_entry.caller_number, log_entry.session_uuid
    )
    
    -- 输出到FreeSwitch日志
    freeswitch.consoleLog(level, log_context .. " " .. json_log)
    
    -- 可以在这里添加发送到外部日志系统的逻辑
    -- send_to_external_logging_system(json_log)
end

-- 记录呼叫事件
function log_call_event(event_type, details)
    local event_data = {
        caller_number = session:getVariable("caller_id_number") or "unknown",
        destination_number = session:getVariable("destination_number") or "unknown",
        call_direction = session:getVariable("call_direction") or "inbound",
        event_timestamp = os.time(),
        details = details or {}
    }
    
    structured_log("INFO", event_type, "呼叫事件记录", event_data)
    
    -- 设置事件变量用于后续分析
    session:setVariable("last_event_type", event_type)
    session:setVariable("last_event_time", event_data.event_timestamp)
end

-- 记录意图检测结果
function log_intent_detection(intent_result)
    local intent_data = {
        detected_intent = intent_result.intent or "unknown",
        confidence = intent_result.confidence or 0,
        category = intent_result.category or "general",
        keywords = intent_result.keywords or {},
        method = intent_result.method or "unknown",
        detection_time = os.time()
    }
    
    structured_log("INFO", "intent_detection", "意图检测完成", intent_data)
    
    -- 记录意图准确性（如果有人工验证结果）
    local human_verified_intent = session:getVariable("human_verified_intent")
    if human_verified_intent then
        local accuracy = (intent_data.detected_intent == human_verified_intent) and 1 or 0
        session:setVariable("intent_accuracy", accuracy)
        
        structured_log("NOTICE", "intent_accuracy", "意图准确性验证", {
            predicted = intent_data.detected_intent,
            actual = human_verified_intent,
            accuracy = accuracy
        })
    end
end

-- 记录转移过程
function log_transfer_process(transfer_stage, transfer_data)
    local process_data = {
        stage = transfer_stage,
        target_queue = transfer_data.target_queue or "unknown",
        attempt_number = transfer_data.attempt or 1,
        elapsed_time = transfer_data.elapsed_time or 0,
        success = transfer_data.success or false
    }
    
    structured_log("INFO", "transfer_process", "转移流程记录", process_data)
    
    -- 记录转移性能指标
    if transfer_stage == "completed" then
        update_transfer_metrics(process_data)
    end
end

-- 更新转移性能指标
function update_transfer_metrics(transfer_data)
    -- 转移成功率
    local total_transfers = tonumber(session:getVariable("total_transfers")) or 0
    local successful_transfers = tonumber(session:getVariable("successful_transfers")) or 0
    
    total_transfers = total_transfers + 1
    if transfer_data.success then
        successful_transfers = successful_transfers + 1
    end
    
    local success_rate = successful_transfers / total_transfers
    
    session:setVariable("total_transfers", total_transfers)
    session:setVariable("successful_transfers", successful_transfers)
    session:setVariable("transfer_success_rate", success_rate)
    
    -- 检查是否需要告警
    if success_rate < (1 - MONITORING_CONFIG.alert_thresholds.transfer_failure_rate) then
        send_alert("transfer_failure_rate", {
            current_rate = 1 - success_rate,
            threshold = MONITORING_CONFIG.alert_thresholds.transfer_failure_rate,
            total_transfers = total_transfers
        })
    end
end

-- 记录队列性能
function log_queue_performance(queue_name, performance_data)
    local queue_data = {
        queue_name = queue_name,
        waiting_count = performance_data.waiting_count or 0,
        average_wait_time = performance_data.average_wait_time or 0,
        available_agents = performance_data.available_agents or 0,
        service_level = performance_data.service_level or 0,
        abandonment_rate = performance_data.abandonment_rate or 0
    }
    
    structured_log("INFO", "queue_performance", "队列性能记录", queue_data)
    
    -- 检查队列告警
    check_queue_alerts(queue_name, queue_data)
end

-- 检查队列告警
function check_queue_alerts(queue_name, queue_data)
    -- 等待时间告警
    if queue_data.average_wait_time > MONITORING_CONFIG.alert_thresholds.queue_wait_time then
        send_alert("queue_wait_time", {
            queue_name = queue_name,
            current_wait_time = queue_data.average_wait_time,
            threshold = MONITORING_CONFIG.alert_thresholds.queue_wait_time
        })
    end
    
    -- 放弃率告警
    if queue_data.abandonment_rate > MONITORING_CONFIG.alert_thresholds.call_abandonment_rate then
        send_alert("call_abandonment_rate", {
            queue_name = queue_name,
            current_rate = queue_data.abandonment_rate,
            threshold = MONITORING_CONFIG.alert_thresholds.call_abandonment_rate
        })
    end
end

-- 发送告警
function send_alert(alert_type, alert_data)
    local alert_message = string.format("告警: %s - %s", alert_type, 
        table.concat(alert_data, ", "))
    
    structured_log("CRITICAL", "system_alert", alert_message, alert_data)
    
    -- 发送自定义事件
    local event = freeswitch.Event("CUSTOM", "monitoring::alert")
    event:addHeader("Alert-Type", alert_type)
    event:addHeader("Alert-Message", alert_message)
    event:addHeader("Timestamp", os.date("%Y-%m-%d %H:%M:%S"))
    
    for key, value in pairs(alert_data) do
        event:addHeader("Alert-Data-" .. key, tostring(value))
    end
    
    event:fire()
    
    -- 这里可以添加发送到外部监控系统的逻辑
    -- send_to_monitoring_system(alert_type, alert_data)
end

-- 记录客服性能
function log_agent_performance(agent_id, performance_data)
    local agent_data = {
        agent_id = agent_id,
        calls_handled = performance_data.calls_handled or 0,
        average_handle_time = performance_data.average_handle_time or 0,
        customer_satisfaction = performance_data.customer_satisfaction or 0,
        availability_rate = performance_data.availability_rate or 0
    }
    
    structured_log("INFO", "agent_performance", "客服性能记录", agent_data)
    
    -- 检查客服性能告警
    if agent_data.average_handle_time > MONITORING_CONFIG.alert_thresholds.agent_response_time then
        send_alert("agent_response_time", {
            agent_id = agent_id,
            current_response_time = agent_data.average_handle_time,
            threshold = MONITORING_CONFIG.alert_thresholds.agent_response_time
        })
    end
end

-- 生成实时统计报告
function generate_realtime_stats()
    local stats = {
        timestamp = os.date("%Y-%m-%d %H:%M:%S"),
        active_calls = get_active_call_count(),
        queue_statistics = get_queue_statistics(),
        agent_statistics = get_agent_statistics(),
        system_health = get_system_health()
    }
    
    structured_log("INFO", "realtime_stats", "实时统计报告", stats)
    
    return stats
end

-- 获取活跃呼叫数量
function get_active_call_count()
    local api = freeswitch.API()
    local result = api:executeString("show calls count")
    return tonumber(result) or 0
end

-- 获取队列统计
function get_queue_statistics()
    local api = freeswitch.API()
    local queues = {"human_agents_queue", "priority_agents_queue", "technical_support_queue", "complaint_queue"}
    local queue_stats = {}
    
    for _, queue in ipairs(queues) do
        local fifo_result = api:executeString("fifo list " .. queue .. "@$${domain}")
        -- 解析FIFO结果（简化版）
        queue_stats[queue] = {
            waiting = 0,  -- 需要解析实际结果
            agents = 0    -- 需要解析实际结果
        }
    end
    
    return queue_stats
end

-- 获取客服统计
function get_agent_statistics()
    -- 这里应该查询实际的客服状态
    return {
        total_agents = 5,
        available_agents = 3,
        busy_agents = 2,
        offline_agents = 0
    }
end

-- 获取系统健康状态
function get_system_health()
    local api = freeswitch.API()
    local status = api:executeString("status")
    
    return {
        uptime = "healthy",  -- 需要解析实际状态
        memory_usage = "normal",
        cpu_usage = "normal",
        disk_usage = "normal"
    }
end

-- 主监控函数
function main()
    log("INFO", "========== 启动日志记录和监控系统 ==========")
    
    if not session then
        log("ERROR", "无法获取session对象")
        return
    end
    
    -- 初始化日志记录器
    init_logger()
    
    -- 记录系统启动事件
    log_call_event("monitoring_start", {
        monitoring_config = MONITORING_CONFIG,
        session_start = os.date("%Y-%m-%d %H:%M:%S")
    })
    
    -- 获取并记录当前呼叫的各种状态
    local intent_result = {
        intent = session:getVariable("detected_intent"),
        confidence = tonumber(session:getVariable("intent_confidence")) or 0,
        category = session:getVariable("intent_category"),
        method = session:getVariable("detection_method")
    }
    
    if intent_result.intent then
        log_intent_detection(intent_result)
    end
    
    -- 如果有转移信息，记录转移过程
    local transfer_requested = session:getVariable("transfer_requested")
    if transfer_requested == "true" then
        local transfer_data = {
            target_queue = session:getVariable("final_queue"),
            success = session:getVariable("transfer_success") == "true",
            elapsed_time = tonumber(session:getVariable("transfer_total_time")) or 0
        }
        log_transfer_process("completed", transfer_data)
    end
    
    -- 生成实时统计
    local stats = generate_realtime_stats()
    
    log("INFO", "========== 监控系统记录完成 ==========")
end

-- 执行主函数
main()