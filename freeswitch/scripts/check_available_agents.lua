-- FreeSWITCH Lua脚本: 检查可用坐席
-- 实时监控坐席状态，智能分配呼叫

-- 坐席状态定义
local AGENT_STATUS = {
    AVAILABLE = "available",     -- 可用
    BUSY = "busy",              -- 忙碌
    BREAK = "break",            -- 休息
    OFFLINE = "offline",        -- 离线
    WRAP_UP = "wrap_up"         -- 话后处理
}

-- 获取所有坐席列表
local function get_all_agents()
    local agents = {}
    
    -- 从数据库或配置文件获取坐席列表
    -- 这里使用示例数据
    local agent_list = {
        {id = "agent_001", name = "张三", skills = {"sales", "general"}, level = 3},
        {id = "agent_002", name = "李四", skills = {"support", "general"}, level = 2},
        {id = "agent_003", name = "王五", skills = {"complaint", "general"}, level = 5},
        {id = "agent_004", name = "赵六", skills = {"sales", "support"}, level = 4},
        {id = "agent_005", name = "钱七", skills = {"general"}, level = 1}
    }
    
    return agent_list
end

-- 获取坐席当前状态
local function get_agent_current_status(agent_id)
    local api = freeswitch.API()
    
    -- 检查坐席是否在线
    local presence = api:executeString("presence " .. agent_id .. "@${domain_name}")
    
    if presence and presence ~= "" then
        -- 检查是否在通话中
        local channels = api:executeString("show channels like " .. agent_id)
        if channels and string.find(channels, agent_id) then
            return AGENT_STATUS.BUSY
        end
        
        -- 检查是否在队列中等待
        local fifo_status = api:executeString("fifo list")
        if fifo_status and string.find(fifo_status, agent_id) then
            return AGENT_STATUS.AVAILABLE
        end
        
        -- 检查自定义状态变量
        local custom_status = api:executeString("global_getvar agent_status_" .. agent_id)
        if custom_status and custom_status ~= "" then
            return custom_status
        end
        
        return AGENT_STATUS.AVAILABLE
    else
        return AGENT_STATUS.OFFLINE
    end
end

-- 获取坐席统计信息
local function get_agent_statistics(agent_id)
    local stats = {
        calls_today = 0,
        avg_handle_time = 0,
        last_call_time = nil,
        current_idle_time = 0
    }
    
    -- 从数据库获取统计信息
    -- 这里使用模拟数据
    stats.calls_today = math.random(0, 20)
    stats.avg_handle_time = math.random(120, 300)
    stats.current_idle_time = math.random(0, 600)
    
    return stats
end

-- 计算坐席得分(用于智能分配)
local function calculate_agent_score(agent, skill_required, priority)
    local score = 100
    
    -- 技能匹配度
    local has_skill = false
    for _, skill in ipairs(agent.skills) do
        if skill == skill_required then
            has_skill = true
            score = score + 50
            break
        end
    end
    
    if not has_skill and skill_required ~= "general" then
        score = score - 50
    end
    
    -- 坐席等级
    score = score + (agent.level * 10)
    
    -- 获取统计信息
    local stats = get_agent_statistics(agent.id)
    
    -- 今日通话数量(负载均衡)
    score = score - stats.calls_today
    
    -- 空闲时间(越久越优先)
    score = score + (stats.current_idle_time / 10)
    
    -- VIP客户优先分配高级坐席
    if priority == 1 and agent.level >= 4 then
        score = score + 100
    end
    
    return score
end

-- 查找最佳可用坐席
local function find_best_available_agent(skill_required, priority)
    local agents = get_all_agents()
    local available_agents = {}
    
    -- 筛选可用坐席
    for _, agent in ipairs(agents) do
        local status = get_agent_current_status(agent.id)
        if status == AGENT_STATUS.AVAILABLE then
            agent.status = status
            agent.score = calculate_agent_score(agent, skill_required, priority)
            table.insert(available_agents, agent)
        end
    end
    
    -- 按得分排序
    table.sort(available_agents, function(a, b) 
        return a.score > b.score 
    end)
    
    if #available_agents > 0 then
        return available_agents[1]
    else
        return nil
    end
end

-- 更新坐席状态
local function update_agent_status(agent_id, status)
    local api = freeswitch.API()
    api:executeString("global_setvar agent_status_" .. agent_id .. "=" .. status)
    
    freeswitch.consoleLog("info", 
        string.format("[AgentStatus] Agent %s status updated to: %s\n", agent_id, status))
end

-- 发送坐席通知
local function notify_agent(agent_id, message)
    -- 可以通过WebSocket、事件或其他方式通知坐席
    local event = freeswitch.Event("CUSTOM", "agent::notification")
    event:addHeader("Agent-ID", agent_id)
    event:addHeader("Message", message)
    event:addHeader("Timestamp", os.date("%Y-%m-%d %H:%M:%S"))
    event:fire()
end

-- 主函数
local function main()
    local uuid = session:get_uuid()
    local caller_id = session:getVariable("caller_id_number") or "unknown"
    local skill_required = session:getVariable("skill_required") or "general"
    local priority = tonumber(session:getVariable("queue_priority") or "3")
    
    freeswitch.consoleLog("info", 
        string.format("[CheckAgents] Checking agents for caller %s, skill: %s, priority: %d\n",
            caller_id, skill_required, priority))
    
    -- 查找最佳可用坐席
    local best_agent = find_best_available_agent(skill_required, priority)
    
    if best_agent then
        -- 找到可用坐席
        session:setVariable("assigned_agent_id", best_agent.id)
        session:setVariable("assigned_agent_name", best_agent.name)
        
        -- 更新坐席状态为忙碌
        update_agent_status(best_agent.id, AGENT_STATUS.BUSY)
        
        -- 通知坐席
        notify_agent(best_agent.id, 
            string.format("Incoming call from %s (Priority: %d)", caller_id, priority))
        
        freeswitch.consoleLog("info", 
            string.format("[CheckAgents] Assigned agent %s (%s) to caller %s\n",
                best_agent.id, best_agent.name, caller_id))
    else
        -- 没有可用坐席
        session:setVariable("no_agents_available", "true")
        
        -- 获取队列等待人数
        local api = freeswitch.API()
        local queue_info = api:executeString("fifo list agent_queue")
        local queue_count = 0
        
        if queue_info then
            -- 解析队列人数
            for line in string.gmatch(queue_info, "[^\r\n]+") do
                if string.find(line, "waiting") then
                    queue_count = queue_count + 1
                end
            end
        end
        
        session:setVariable("queue_position", tostring(queue_count + 1))
        
        freeswitch.consoleLog("info", 
            string.format("[CheckAgents] No agents available. Caller %s queued at position %d\n",
                caller_id, queue_count + 1))
    end
    
    -- 记录检查结果
    local check_result = {
        uuid = uuid,
        caller_id = caller_id,
        skill_required = skill_required,
        priority = priority,
        agent_assigned = best_agent and best_agent.id or "none",
        timestamp = os.date("%Y-%m-%d %H:%M:%S")
    }
    
    -- 可以将结果保存到数据库或发送到监控系统
    -- save_check_result(check_result)
end

-- 执行主函数
if session:ready() then
    main()
else
    freeswitch.consoleLog("err", "[CheckAgents] Session not ready\n")
end