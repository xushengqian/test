-- FreeSWITCH Lua Script for Database Operations
-- 数据库操作辅助脚本

-- 加载必要的模块
require "luasql.mysql"  -- 需要安装 luasql-mysql

-- 数据库配置
local db_config = {
    host = "localhost",
    port = 3306,
    database = "freeswitch_agents",
    user = "freeswitch",
    password = "your_password_here"
}

-- 创建数据库连接
function create_db_connection()
    local env = luasql.mysql()
    local conn, err = env:connect(
        db_config.database,
        db_config.user,
        db_config.password,
        db_config.host,
        db_config.port
    )
    
    if not conn then
        freeswitch.consoleLog("error", "数据库连接失败: " .. (err or "unknown error") .. "\n")
        return nil
    end
    
    return conn, env
end

-- 关闭数据库连接
function close_db_connection(conn, env)
    if conn then conn:close() end
    if env then env:close() end
end

-- 函数：获取坐席信息
function get_agent_info(agent_id)
    local conn, env = create_db_connection()
    if not conn then return nil end
    
    local query = string.format(
        "SELECT * FROM agents WHERE agent_id = '%s'",
        conn:escape(agent_id)
    )
    
    local cursor = conn:execute(query)
    local row = cursor:fetch({}, "a")
    cursor:close()
    close_db_connection(conn, env)
    
    return row
end

-- 函数：更新坐席状态（使用存储过程）
function update_agent_status_db(agent_id, new_status, reason)
    local conn, env = create_db_connection()
    if not conn then return false end
    
    local query = string.format(
        "CALL update_agent_status('%s', '%s', '%s')",
        conn:escape(agent_id),
        conn:escape(new_status),
        conn:escape(reason or "")
    )
    
    local result, err = conn:execute(query)
    close_db_connection(conn, env)
    
    if err then
        freeswitch.consoleLog("error", "更新坐席状态失败: " .. err .. "\n")
        return false
    end
    
    return true
end

-- 函数：记录通话记录
function log_call_record(call_data)
    local conn, env = create_db_connection()
    if not conn then return false end
    
    local query = string.format([[
        INSERT INTO call_records 
        (call_uuid, caller_id, caller_name, called_number, queue_name, 
         agent_id, call_start, call_answer, call_end, duration, 
         wait_time, talk_time, disposition, recording_path)
        VALUES ('%s', '%s', '%s', '%s', '%s', '%s', NOW(), %s, %s, %d, %d, %d, '%s', '%s')
    ]],
        conn:escape(call_data.call_uuid or ""),
        conn:escape(call_data.caller_id or ""),
        conn:escape(call_data.caller_name or ""),
        conn:escape(call_data.called_number or ""),
        conn:escape(call_data.queue_name or ""),
        conn:escape(call_data.agent_id or ""),
        call_data.call_answer and "NOW()" or "NULL",
        call_data.call_end and "NOW()" or "NULL",
        call_data.duration or 0,
        call_data.wait_time or 0,
        call_data.talk_time or 0,
        conn:escape(call_data.disposition or "no-answer"),
        conn:escape(call_data.recording_path or "")
    )
    
    local result, err = conn:execute(query)
    close_db_connection(conn, env)
    
    if err then
        freeswitch.consoleLog("error", "记录通话失败: " .. err .. "\n")
        return false
    end
    
    return true
end

-- 函数：获取可用坐席列表（从数据库）
function get_available_agents_db(queue_name, skill_required)
    local conn, env = create_db_connection()
    if not conn then return {} end
    
    local query
    if skill_required then
        query = string.format([[
            SELECT DISTINCT a.agent_id, a.extension, a.agent_name, ask.skill_level
            FROM agents a
            JOIN queue_members qm ON a.agent_id = qm.agent_id
            LEFT JOIN agent_skills ask ON a.agent_id = ask.agent_id AND ask.skill_name = '%s'
            WHERE a.status = 'available' 
            AND qm.queue_name = '%s'
            ORDER BY qm.priority DESC, ask.skill_level DESC
        ]],
            conn:escape(skill_required),
            conn:escape(queue_name)
        )
    else
        query = string.format([[
            SELECT a.agent_id, a.extension, a.agent_name
            FROM agents a
            JOIN queue_members qm ON a.agent_id = qm.agent_id
            WHERE a.status = 'available' 
            AND qm.queue_name = '%s'
            ORDER BY qm.priority DESC
        ]],
            conn:escape(queue_name)
        )
    end
    
    local cursor = conn:execute(query)
    local agents = {}
    local row = cursor:fetch({}, "a")
    
    while row do
        table.insert(agents, row)
        row = cursor:fetch(row, "a")
    end
    
    cursor:close()
    close_db_connection(conn, env)
    
    return agents
end

-- 函数：获取队列统计
function get_queue_stats(queue_name)
    local conn, env = create_db_connection()
    if not conn then return nil end
    
    local query = string.format([[
        SELECT 
            q.queue_name,
            q.description,
            COUNT(DISTINCT CASE WHEN a.status = 'available' THEN qm.agent_id END) as available_agents,
            COUNT(DISTINCT CASE WHEN a.status = 'busy' THEN qm.agent_id END) as busy_agents,
            COUNT(DISTINCT qm.agent_id) as total_agents,
            COALESCE(qs.calls_waiting, 0) as calls_waiting,
            COALESCE(qs.calls_answered, 0) as calls_answered,
            COALESCE(qs.calls_abandoned, 0) as calls_abandoned,
            COALESCE(qs.avg_wait_time, 0) as avg_wait_time,
            COALESCE(qs.avg_talk_time, 0) as avg_talk_time,
            COALESCE(qs.service_level, 0) as service_level
        FROM queues q
        LEFT JOIN queue_members qm ON q.queue_name = qm.queue_name
        LEFT JOIN agents a ON qm.agent_id = a.agent_id
        LEFT JOIN queue_stats qs ON q.queue_name = qs.queue_name
        WHERE q.queue_name = '%s'
        GROUP BY q.queue_name
    ]],
        conn:escape(queue_name)
    )
    
    local cursor = conn:execute(query)
    local stats = cursor:fetch({}, "a")
    cursor:close()
    close_db_connection(conn, env)
    
    return stats
end

-- 函数：检查VIP客户
function check_vip_customer(phone_number)
    local conn, env = create_db_connection()
    if not conn then return nil end
    
    local query = string.format(
        "SELECT * FROM vip_customers WHERE phone_number = '%s'",
        conn:escape(phone_number)
    )
    
    local cursor = conn:execute(query)
    local vip_info = cursor:fetch({}, "a")
    cursor:close()
    close_db_connection(conn, env)
    
    return vip_info
end

-- 函数：创建回拨请求
function create_callback_request(phone_number, customer_name, queue_name, preferred_time)
    local conn, env = create_db_connection()
    if not conn then return false end
    
    local query = string.format([[
        INSERT INTO callback_requests 
        (phone_number, customer_name, queue_name, preferred_time, status)
        VALUES ('%s', '%s', '%s', %s, 'pending')
    ]],
        conn:escape(phone_number),
        conn:escape(customer_name or ""),
        conn:escape(queue_name or "general"),
        preferred_time and string.format("'%s'", conn:escape(preferred_time)) or "NULL"
    )
    
    local result, err = conn:execute(query)
    close_db_connection(conn, env)
    
    if err then
        freeswitch.consoleLog("error", "创建回拨请求失败: " .. err .. "\n")
        return false
    end
    
    return true
end

-- 函数：获取坐席日统计
function get_agent_daily_stats(agent_id, date)
    local conn, env = create_db_connection()
    if not conn then return nil end
    
    local query = string.format([[
        SELECT * FROM agent_daily_stats 
        WHERE agent_id = '%s' AND stat_date = '%s'
    ]],
        conn:escape(agent_id),
        conn:escape(date or os.date("%Y-%m-%d"))
    )
    
    local cursor = conn:execute(query)
    local stats = cursor:fetch({}, "a")
    cursor:close()
    close_db_connection(conn, env)
    
    return stats
end

-- 函数：更新队列等待人数
function update_queue_waiting(queue_name, delta)
    local conn, env = create_db_connection()
    if not conn then return false end
    
    local query = string.format([[
        INSERT INTO queue_stats (queue_name, calls_waiting)
        VALUES ('%s', GREATEST(0, %d))
        ON DUPLICATE KEY UPDATE
            calls_waiting = GREATEST(0, calls_waiting + %d),
            last_update = NOW()
    ]],
        conn:escape(queue_name),
        delta,
        delta
    )
    
    local result, err = conn:execute(query)
    close_db_connection(conn, env)
    
    return not err
end

-- 函数：获取坐席通话历史
function get_agent_call_history(agent_id, limit)
    local conn, env = create_db_connection()
    if not conn then return {} end
    
    local query = string.format([[
        SELECT * FROM call_records 
        WHERE agent_id = '%s'
        ORDER BY call_start DESC
        LIMIT %d
    ]],
        conn:escape(agent_id),
        limit or 10
    )
    
    local cursor = conn:execute(query)
    local calls = {}
    local row = cursor:fetch({}, "a")
    
    while row do
        table.insert(calls, row)
        row = cursor:fetch(row, "a")
    end
    
    cursor:close()
    close_db_connection(conn, env)
    
    return calls
end

-- 导出函数供其他脚本使用
return {
    get_agent_info = get_agent_info,
    update_agent_status_db = update_agent_status_db,
    log_call_record = log_call_record,
    get_available_agents_db = get_available_agents_db,
    get_queue_stats = get_queue_stats,
    check_vip_customer = check_vip_customer,
    create_callback_request = create_callback_request,
    get_agent_daily_stats = get_agent_daily_stats,
    update_queue_waiting = update_queue_waiting,
    get_agent_call_history = get_agent_call_history
}