-- smart_queue_router.lua
-- 智能队列路由脚本

local log = freeswitch.consoleLog

-- 路由配置
local ROUTING_CONFIG = {
    -- 工作时间配置 (24小时制)
    business_hours = {
        weekdays = {start_hour = 9, end_hour = 18},  -- 周一到周五 9:00-18:00
        saturday = {start_hour = 9, end_hour = 17},  -- 周六 9:00-17:00
        sunday = {start_hour = 10, end_hour = 16}    -- 周日 10:00-16:00
    },
    
    -- 队列容量阈值
    queue_thresholds = {
        human_agents_queue = {normal = 10, high = 20, critical = 30},
        priority_agents_queue = {normal = 5, high = 10, critical = 15},
        technical_support_queue = {normal = 8, high = 15, critical = 25},
        complaint_queue = {normal = 3, high = 8, critical = 12}
    },
    
    -- VIP客户列表（示例）
    vip_customers = {
        ["138****0001"] = {level = "platinum", priority = 10},
        ["139****0002"] = {level = "gold", priority = 8},
        ["150****0003"] = {level = "silver", priority = 6}
    }
}

-- 获取队列当前状态
function get_queue_status(queue_name)
    local api = freeswitch.API()
    local result = api:executeString("fifo list " .. queue_name)
    
    if not result or result == "" then
        return {waiting = 0, agents = 0, available = false}
    end
    
    -- 解析FIFO状态信息
    local waiting_count = 0
    local agent_count = 0
    local available_agents = 0
    
    -- 简化的解析逻辑（实际需要根据fifo list的输出格式调整）
    for line in result:gmatch("[^\n]+") do
        if string.find(line, "Waiting") then
            waiting_count = tonumber(string.match(line, "(%d+)")) or 0
        elseif string.find(line, "Total") then
            agent_count = tonumber(string.match(line, "(%d+)")) or 0
        end
    end
    
    return {
        waiting = waiting_count,
        agents = agent_count,
        available = agent_count > 0,
        load_level = calculate_load_level(queue_name, waiting_count)
    }
end

-- 计算队列负载水平
function calculate_load_level(queue_name, waiting_count)
    local thresholds = ROUTING_CONFIG.queue_thresholds[queue_name]
    if not thresholds then
        return "unknown"
    end
    
    if waiting_count >= thresholds.critical then
        return "critical"
    elseif waiting_count >= thresholds.high then
        return "high"
    elseif waiting_count >= thresholds.normal then
        return "normal"
    else
        return "low"
    end
end

-- 检查是否在工作时间
function is_business_hours()
    local current_hour = tonumber(os.date("%H"))
    local current_dow = tonumber(os.date("%w"))  -- 0=Sunday, 1=Monday, ..., 6=Saturday
    
    local business_hours = ROUTING_CONFIG.business_hours
    
    if current_dow >= 1 and current_dow <= 5 then
        -- 工作日
        return current_hour >= business_hours.weekdays.start_hour and 
               current_hour < business_hours.weekdays.end_hour
    elseif current_dow == 6 then
        -- 周六
        return current_hour >= business_hours.saturday.start_hour and 
               current_hour < business_hours.saturday.end_hour
    else
        -- 周日
        return current_hour >= business_hours.sunday.start_hour and 
               current_hour < business_hours.sunday.end_hour
    end
end

-- 检查客户VIP状态
function get_customer_priority(caller_number)
    local vip_info = ROUTING_CONFIG.vip_customers[caller_number]
    if vip_info then
        return {
            is_vip = true,
            level = vip_info.level,
            priority = vip_info.priority
        }
    end
    
    return {is_vip = false, level = "normal", priority = 1}
end

-- 基于意图选择合适的队列
function select_queue_by_intent()
    local detected_intent = session:getVariable("detected_intent") or "unknown"
    local intent_category = session:getVariable("intent_category") or "general"
    local caller_priority = session:getVariable("caller_priority") or "normal"
    
    log("INFO", string.format("路由决策 - 意图: %s, 类别: %s, 优先级: %s", 
        detected_intent, intent_category, caller_priority))
    
    -- 紧急情况或VIP客户
    if caller_priority == "high" or caller_priority == "vip" or caller_priority == "emergency" then
        return "priority_agents_queue@$${domain}"
    end
    
    -- 基于意图类别路由
    if intent_category == "technical" or intent_category == "issue" then
        return "technical_support_queue@$${domain}"
    elseif intent_category == "complaint" or intent_category == "emotional" then
        return "complaint_queue@$${domain}"
    elseif detected_intent == "transfer_to_human" or intent_category == "direct" then
        return "human_agents_queue@$${domain}"
    else
        -- 默认路由
        return "human_agents_queue@$${domain}"
    end
end

-- 智能负载均衡
function smart_load_balancing(primary_queue)
    local primary_status = get_queue_status(primary_queue)
    
    log("INFO", string.format("主队列 %s 状态 - 等待: %d, 客服: %d, 负载: %s", 
        primary_queue, primary_status.waiting, primary_status.agents, primary_status.load_level))
    
    -- 如果主队列负载过高，考虑分流
    if primary_status.load_level == "critical" then
        log("WARN", "主队列负载过高，尝试分流")
        
        -- 尝试其他队列
        local alternative_queues = {
            "human_agents_queue@$${domain}",
            "technical_support_queue@$${domain}",
            "priority_agents_queue@$${domain}"
        }
        
        for _, queue in ipairs(alternative_queues) do
            if queue ~= primary_queue then
                local alt_status = get_queue_status(queue)
                if alt_status.available and alt_status.load_level ~= "critical" then
                    log("INFO", "分流到替代队列: " .. queue)
                    return queue
                end
            end
        end
    end
    
    -- 如果主队列没有可用客服
    if not primary_status.available then
        log("WARN", "主队列无可用客服，检查其他队列")
        
        -- 检查所有队列的可用性
        local all_queues = {
            "priority_agents_queue@$${domain}",
            "human_agents_queue@$${domain}",
            "technical_support_queue@$${domain}",
            "complaint_queue@$${domain}"
        }
        
        for _, queue in ipairs(all_queues) do
            if queue ~= primary_queue then
                local status = get_queue_status(queue)
                if status.available then
                    log("INFO", "路由到可用队列: " .. queue)
                    return queue
                end
            end
        end
        
        -- 如果都不可用，检查是否在工作时间
        if not is_business_hours() then
            log("INFO", "非工作时间，路由到值班队列")
            return "after_hours_queue@$${domain}"
        end
    end
    
    return primary_queue
end

-- 设置队列优先级和参数
function set_queue_parameters(queue_name, customer_priority)
    -- 基于客户优先级设置队列参数
    if customer_priority.is_vip then
        session:setVariable("fifo_priority", tostring(customer_priority.priority))
        session:setVariable("queue_timeout", "45")  -- VIP客户减少等待时间
        
        if customer_priority.level == "platinum" then
            session:setVariable("fifo_announce_frequency", "15")  -- 更频繁的位置通告
        end
    else
        session:setVariable("fifo_priority", "1")
        session:setVariable("queue_timeout", "60")
        session:setVariable("fifo_announce_frequency", "30")
    end
    
    -- 设置队列特定参数
    if string.find(queue_name, "complaint") then
        session:setVariable("record_session", "true")  -- 投诉需要录音
        session:setVariable("queue_timeout", "30")     -- 投诉快速处理
    elseif string.find(queue_name, "technical") then
        session:setVariable("queue_timeout", "90")     -- 技术支持允许更长等待
    end
end

-- 记录路由决策
function log_routing_decision(queue_name, decision_factors)
    local caller_number = session:getVariable("caller_id_number") or "unknown"
    local routing_log = string.format(
        "路由决策 - 主叫: %s, 目标队列: %s, 决策因素: %s",
        caller_number, queue_name, table.concat(decision_factors, ", ")
    )
    
    log("INFO", routing_log)
    
    -- 设置会话变量用于后续分析
    session:setVariable("routing_decision", routing_log)
    session:setVariable("final_queue", queue_name)
    session:setVariable("routing_timestamp", os.time())
end

-- 主路由函数
function main()
    log("INFO", "========== 启动智能队列路由 ==========")
    
    if not session then
        log("ERROR", "无法获取session对象")
        return
    end
    
    local caller_number = session:getVariable("caller_id_number") or "unknown"
    local decision_factors = {}
    
    -- 1. 检查客户VIP状态
    local customer_priority = get_customer_priority(caller_number)
    if customer_priority.is_vip then
        session:setVariable("caller_priority", "vip")
        session:setVariable("vip_level", customer_priority.level)
        table.insert(decision_factors, "VIP客户(" .. customer_priority.level .. ")")
    end
    
    -- 2. 基于意图选择初始队列
    local primary_queue = select_queue_by_intent()
    table.insert(decision_factors, "意图路由")
    
    -- 3. 进行智能负载均衡
    local final_queue = smart_load_balancing(primary_queue)
    if final_queue ~= primary_queue then
        table.insert(decision_factors, "负载均衡分流")
    end
    
    -- 4. 检查工作时间
    if not is_business_hours() then
        table.insert(decision_factors, "非工作时间")
        -- 可以在这里添加特殊处理逻辑
    end
    
    -- 5. 设置队列参数
    set_queue_parameters(final_queue, customer_priority)
    
    -- 6. 记录路由决策
    log_routing_decision(final_queue, decision_factors)
    
    -- 7. 执行队列转移
    log("NOTICE", string.format("将呼叫 %s 路由到队列: %s", caller_number, final_queue))
    
    -- 播放进入队列提示
    session:streamFile("sounds/entering_queue.wav")
    
    -- 转移到选定的队列
    session:execute("fifo", final_queue .. " in")
    
    log("INFO", "========== 队列路由完成 ==========")
end

-- 执行主函数
main()