-- FreeSWITCH Lua脚本: 执行转人工操作
-- 处理用户转人工请求，分配坐席，记录日志

-- 配置参数
local config = {
    max_wait_time = 300,        -- 最大等待时间(秒)
    priority_levels = {         -- 优先级配置
        vip = 1,
        high = 2,
        normal = 3,
        low = 4
    },
    agent_skills = {            -- 坐席技能分组
        sales = {"agent_001", "agent_002", "agent_003"},
        support = {"agent_004", "agent_005", "agent_006"},
        complaint = {"agent_007", "agent_008"},
        general = {"agent_009", "agent_010"}
    }
}

-- 获取会话信息
local uuid = session:get_uuid()
local caller_id = session:getVariable("caller_id_number") or "unknown"
local transfer_reason = session:getVariable("transfer_reason") or "user_request"
local customer_type = session:getVariable("customer_type") or "normal"

-- 日志函数
local function log_info(message)
    freeswitch.consoleLog("info", "[TransferAgent] " .. message .. "\n")
end

local function log_error(message)
    freeswitch.consoleLog("err", "[TransferAgent] " .. message .. "\n")
end

-- 获取客户优先级
local function get_customer_priority()
    -- 可以从数据库或CRM系统获取客户等级
    local priority = config.priority_levels.normal
    
    if customer_type == "vip" then
        priority = config.priority_levels.vip
    elseif customer_type == "complaint" then
        priority = config.priority_levels.high
    end
    
    log_info("Customer priority set to: " .. priority)
    return priority
end

-- 选择合适的坐席组
local function select_agent_group()
    local skill_required = "general"
    
    -- 根据转人工原因选择坐席组
    if transfer_reason == "complaint" or transfer_reason == "negative_emotion" then
        skill_required = "complaint"
    elseif string.find(transfer_reason or "", "sales") then
        skill_required = "sales"
    elseif string.find(transfer_reason or "", "support") then
        skill_required = "support"
    end
    
    log_info("Selected agent group: " .. skill_required)
    return skill_required
end

-- 查找可用坐席
local function find_available_agent(agent_group)
    local agents = config.agent_skills[agent_group] or config.agent_skills.general
    
    for _, agent_id in ipairs(agents) do
        -- 检查坐席状态
        local agent_status = get_agent_status(agent_id)
        
        if agent_status == "available" then
            log_info("Found available agent: " .. agent_id)
            return agent_id
        end
    end
    
    return nil
end

-- 获取坐席状态(示例函数，实际应查询数据库或状态系统)
function get_agent_status(agent_id)
    -- 使用FreeSWITCH API检查坐席状态
    local api = freeswitch.API()
    local response = api:executeString("fifo list agent_queue")
    
    -- 简化示例：随机返回状态
    local random = math.random()
    if random > 0.5 then
        return "available"
    else
        return "busy"
    end
end

-- 记录转接日志
local function log_transfer_event(agent_id, status)
    local log_data = {
        uuid = uuid,
        caller_id = caller_id,
        transfer_reason = transfer_reason,
        agent_id = agent_id or "none",
        status = status,
        timestamp = os.date("%Y-%m-%d %H:%M:%S")
    }
    
    -- 可以将日志发送到外部系统
    local log_string = string.format(
        "Transfer Event: caller=%s, reason=%s, agent=%s, status=%s",
        log_data.caller_id,
        log_data.transfer_reason,
        log_data.agent_id,
        log_data.status
    )
    
    log_info(log_string)
    
    -- 保存到数据库(示例)
    -- save_to_database(log_data)
end

-- 播放等待音乐
local function play_hold_music()
    session:execute("playback", "local_stream://moh")
end

-- 处理转接超时
local function handle_timeout()
    log_error("Transfer timeout for caller: " .. caller_id)
    
    -- 播放超时提示
    session:execute("playback", "ivr/ivr-call_back_later.wav")
    
    -- 提供回调选项
    session:execute("playback", "ivr/ivr-press_1_to_leave_callback.wav")
    
    local digits = session:playAndGetDigits(1, 1, 1, 5000, "#", 
        "silence_stream://250", "ivr/ivr-invalid_entry.wav", "\\d")
    
    if digits == "1" then
        -- 记录回调请求
        session:setVariable("callback_requested", "true")
        session:setVariable("callback_number", caller_id)
        session:execute("playback", "ivr/ivr-callback_registered.wav")
    end
end

-- 执行转接
local function execute_transfer(agent_id)
    log_info("Executing transfer to agent: " .. agent_id)
    
    -- 设置转接相关变量
    session:setVariable("transfer_agent_id", agent_id)
    session:setVariable("transfer_start_time", os.date("%Y-%m-%d %H:%M:%S"))
    
    -- 获取坐席分机号
    local agent_extension = "1" .. string.sub(agent_id, -3)  -- 示例：agent_001 -> 1001
    
    -- 设置呼叫参数
    session:setVariable("origination_caller_id_name", "Customer " .. caller_id)
    session:setVariable("origination_caller_id_number", caller_id)
    session:setVariable("effective_caller_id_name", "Transfer from IVR")
    session:setVariable("effective_caller_id_number", caller_id)
    
    -- 桥接到坐席
    local bridge_string = string.format(
        "{ignore_early_media=true,originate_timeout=30}user/%s@${domain_name}",
        agent_extension
    )
    
    session:execute("bridge", bridge_string)
    
    -- 检查桥接结果
    local hangup_cause = session:getVariable("bridge_hangup_cause")
    
    if hangup_cause == "SUCCESS" then
        log_info("Transfer successful to agent: " .. agent_id)
        log_transfer_event(agent_id, "success")
        return true
    else
        log_error("Transfer failed: " .. (hangup_cause or "unknown"))
        log_transfer_event(agent_id, "failed")
        return false
    end
end

-- 主函数
local function main()
    log_info("Starting transfer to agent for caller: " .. caller_id)
    
    -- 获取优先级
    local priority = get_customer_priority()
    session:setVariable("queue_priority", tostring(priority))
    
    -- 选择坐席组
    local agent_group = select_agent_group()
    
    -- 查找可用坐席
    local available_agent = find_available_agent(agent_group)
    
    if available_agent then
        -- 立即转接
        if execute_transfer(available_agent) then
            return
        end
    end
    
    -- 没有可用坐席，加入队列
    log_info("No available agents, adding to queue")
    
    -- 播放排队提示
    session:execute("playback", "ivr/ivr-you_are_in_queue.wav")
    
    -- 设置队列参数
    session:setVariable("fifo_priority", tostring(priority))
    session:setVariable("fifo_music", "local_stream://moh")
    session:setVariable("fifo_chime_freq", "30")
    session:setVariable("fifo_chime_list", "ivr/ivr-you_are_number.wav")
    
    -- 加入FIFO队列
    local queue_name = agent_group .. "_queue@${domain_name}"
    session:execute("fifo", queue_name .. " in")
    
    -- 检查是否成功连接
    local fifo_status = session:getVariable("fifo_status")
    
    if fifo_status == "TIMEOUT" then
        handle_timeout()
    elseif fifo_status == "SUCCESS" then
        local connected_agent = session:getVariable("fifo_agent")
        log_transfer_event(connected_agent, "success")
    else
        log_error("Queue error: " .. (fifo_status or "unknown"))
        log_transfer_event(nil, "error")
    end
end

-- 执行主函数
if session:ready() then
    main()
else
    log_error("Session not ready")
end