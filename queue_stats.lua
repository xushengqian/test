--[[
FreeSWITCH 队列统计查询脚本
功能：提供实时队列统计信息，坐席状态统计
作者：AI Assistant
版本：1.0
]]--

require "luasql.sqlite3"
require "json"

-- 配置
local config = {
    db_path = "/usr/local/freeswitch/db/agent_queue.db"
}

-- 数据库连接
local env = luasql.sqlite3()
local conn = nil

-- 初始化数据库
function init_database()
    conn = env:connect(config.db_path)
    return conn ~= nil
end

-- 获取实时队列统计
function get_realtime_queue_stats()
    if not init_database() then
        return nil, "数据库连接失败"
    end
    
    local query = [[
        SELECT 
            name,
            description,
            total_calls,
            completed_calls,
            timeout_calls,
            abandoned_calls,
            avg_wait_time,
            avg_talk_time,
            max_wait_time
        FROM queue_stats
        ORDER BY name
    ]]
    
    local cursor = conn:execute(query)
    if not cursor then
        conn:close()
        env:close()
        return nil, "查询失败"
    end
    
    local stats = {}
    local row = cursor:fetch({}, "a")
    while row do
        -- 格式化数值
        row.avg_wait_time = math.floor((row.avg_wait_time or 0) + 0.5)
        row.avg_talk_time = math.floor((row.avg_talk_time or 0) + 0.5)
        row.max_wait_time = row.max_wait_time or 0
        
        table.insert(stats, row)
        row = cursor:fetch({}, "a")
    end
    
    cursor:close()
    conn:close()
    env:close()
    
    return stats, "查询成功"
end

-- 获取坐席统计信息
function get_agent_stats()
    if not init_database() then
        return nil, "数据库连接失败"
    end
    
    local query = [[
        SELECT 
            extension,
            name,
            status,
            skill_groups,
            total_calls,
            today_calls,
            avg_talk_time,
            completed_calls,
            last_call_time
        FROM agent_stats
        ORDER BY extension
    ]]
    
    local cursor = conn:execute(query)
    if not cursor then
        conn:close()
        env:close()
        return nil, "查询失败"
    end
    
    local stats = {}
    local row = cursor:fetch({}, "a")
    while row do
        -- 格式化数值
        row.avg_talk_time = math.floor((row.avg_talk_time or 0) + 0.5)
        row.today_calls = row.today_calls or 0
        row.completed_calls = row.completed_calls or 0
        
        table.insert(stats, row)
        row = cursor:fetch({}, "a")
    end
    
    cursor:close()
    conn:close()
    env:close()
    
    return stats, "查询成功"
end

-- 获取当前等待队列信息
function get_current_queue_waiting()
    if not init_database() then
        return nil, "数据库连接失败"
    end
    
    local query = [[
        SELECT 
            queue_name,
            COUNT(*) as waiting_count,
            MIN(julianday('now') - julianday(queue_time)) * 86400 as min_wait_time,
            MAX(julianday('now') - julianday(queue_time)) * 86400 as max_wait_time,
            AVG(julianday('now') - julianday(queue_time)) * 86400 as avg_wait_time
        FROM call_logs 
        WHERE status = 'queued' 
        AND queue_time IS NOT NULL
        GROUP BY queue_name
        ORDER BY queue_name
    ]]
    
    local cursor = conn:execute(query)
    if not cursor then
        conn:close()
        env:close()
        return nil, "查询失败"
    end
    
    local waiting_stats = {}
    local row = cursor:fetch({}, "a")
    while row do
        -- 格式化时间（秒）
        row.min_wait_time = math.floor((row.min_wait_time or 0) + 0.5)
        row.max_wait_time = math.floor((row.max_wait_time or 0) + 0.5)
        row.avg_wait_time = math.floor((row.avg_wait_time or 0) + 0.5)
        
        table.insert(waiting_stats, row)
        row = cursor:fetch({}, "a")
    end
    
    cursor:close()
    conn:close()
    env:close()
    
    return waiting_stats, "查询成功"
end

-- 获取系统总体统计
function get_system_overview()
    if not init_database() then
        return nil, "数据库连接失败"
    end
    
    -- 获取坐席状态统计
    local agent_status_query = [[
        SELECT 
            status,
            COUNT(*) as count
        FROM agents
        GROUP BY status
    ]]
    
    local cursor = conn:execute(agent_status_query)
    local agent_status = {}
    if cursor then
        local row = cursor:fetch({}, "a")
        while row do
            agent_status[row.status] = row.count
            row = cursor:fetch({}, "a")
        end
        cursor:close()
    end
    
    -- 获取今日呼叫统计
    local today_calls_query = [[
        SELECT 
            COUNT(*) as total_calls,
            COUNT(CASE WHEN status = 'completed' THEN 1 END) as completed_calls,
            COUNT(CASE WHEN status = 'timeout' THEN 1 END) as timeout_calls,
            COUNT(CASE WHEN status = 'abandoned' THEN 1 END) as abandoned_calls,
            COUNT(CASE WHEN status = 'queued' THEN 1 END) as current_queued,
            AVG(wait_time) as avg_wait_time,
            AVG(talk_time) as avg_talk_time
        FROM call_logs 
        WHERE DATE(start_time) = DATE('now')
    ]]
    
    cursor = conn:execute(today_calls_query)
    local today_stats = {}
    if cursor then
        today_stats = cursor:fetch({}, "a") or {}
        cursor:close()
        
        -- 格式化数值
        today_stats.avg_wait_time = math.floor((today_stats.avg_wait_time or 0) + 0.5)
        today_stats.avg_talk_time = math.floor((today_stats.avg_talk_time or 0) + 0.5)
    end
    
    conn:close()
    env:close()
    
    local overview = {
        agent_status = agent_status,
        today_stats = today_stats,
        timestamp = os.date("%Y-%m-%d %H:%M:%S")
    }
    
    return overview, "查询成功"
end

-- 播放语音统计信息
function play_voice_stats(session)
    if not session:ready() then
        return
    end
    
    session:answer()
    session:sleep(1000)
    
    -- 获取系统概览
    local overview, msg = get_system_overview()
    if not overview then
        session:execute("playback", "ivr/ivr-system_error.wav")
        return
    end
    
    -- 播放统计信息
    session:execute("playback", "ivr/ivr-system_statistics.wav")
    session:sleep(500)
    
    -- 坐席状态统计
    local available_agents = overview.agent_status.available or 0
    local busy_agents = overview.agent_status.busy or 0
    local offline_agents = overview.agent_status.offline or 0
    
    session:execute("playback", "ivr/ivr-available_agents.wav")
    session:execute("say", "number " .. available_agents)
    session:sleep(500)
    
    session:execute("playback", "ivr/ivr-busy_agents.wav")
    session:execute("say", "number " .. busy_agents)
    session:sleep(500)
    
    -- 今日呼叫统计
    local total_calls = overview.today_stats.total_calls or 0
    local completed_calls = overview.today_stats.completed_calls or 0
    local current_queued = overview.today_stats.current_queued or 0
    
    session:execute("playback", "ivr/ivr-total_calls_today.wav")
    session:execute("say", "number " .. total_calls)
    session:sleep(500)
    
    session:execute("playback", "ivr/ivr-completed_calls.wav")
    session:execute("say", "number " .. completed_calls)
    session:sleep(500)
    
    session:execute("playback", "ivr/ivr-current_queued.wav")
    session:execute("say", "number " .. current_queued)
    session:sleep(500)
    
    -- 平均等待时间
    local avg_wait_time = overview.today_stats.avg_wait_time or 0
    if avg_wait_time > 0 then
        session:execute("playback", "ivr/ivr-average_wait_time.wav")
        session:execute("say", "number " .. avg_wait_time)
        session:execute("playback", "ivr/ivr-seconds.wav")
    end
end

-- 生成HTML统计报告
function generate_html_report()
    local overview, msg1 = get_system_overview()
    local queue_stats, msg2 = get_realtime_queue_stats()
    local agent_stats, msg3 = get_agent_stats()
    local waiting_stats, msg4 = get_current_queue_waiting()
    
    if not overview or not queue_stats or not agent_stats then
        return nil, "获取统计数据失败"
    end
    
    local html = [[
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>FreeSWITCH 呼叫中心统计报告</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
        .container { max-width: 1200px; margin: 0 auto; }
        .header { background: #2c3e50; color: white; padding: 20px; border-radius: 8px; margin-bottom: 20px; }
        .stats-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 20px; }
        .stat-card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        .stat-title { font-size: 18px; font-weight: bold; color: #2c3e50; margin-bottom: 15px; }
        .stat-value { font-size: 24px; font-weight: bold; color: #3498db; }
        .stat-label { font-size: 14px; color: #7f8c8d; }
        table { width: 100%; border-collapse: collapse; margin-top: 10px; }
        th, td { padding: 8px; text-align: left; border-bottom: 1px solid #ddd; }
        th { background-color: #f8f9fa; font-weight: bold; }
        .status-available { color: #27ae60; font-weight: bold; }
        .status-busy { color: #e74c3c; font-weight: bold; }
        .status-offline { color: #95a5a6; font-weight: bold; }
        .timestamp { text-align: center; color: #7f8c8d; margin-top: 20px; }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>FreeSWITCH 呼叫中心统计报告</h1>
            <p>实时数据更新时间: ]] .. overview.timestamp .. [[</p>
        </div>
        
        <div class="stats-grid">
            <!-- 系统概览 -->
            <div class="stat-card">
                <div class="stat-title">系统概览</div>
                <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 10px;">
                    <div>
                        <div class="stat-value">]] .. (overview.agent_status.available or 0) .. [[</div>
                        <div class="stat-label">可用坐席</div>
                    </div>
                    <div>
                        <div class="stat-value">]] .. (overview.agent_status.busy or 0) .. [[</div>
                        <div class="stat-label">忙碌坐席</div>
                    </div>
                    <div>
                        <div class="stat-value">]] .. (overview.today_stats.total_calls or 0) .. [[</div>
                        <div class="stat-label">今日总呼叫</div>
                    </div>
                    <div>
                        <div class="stat-value">]] .. (overview.today_stats.current_queued or 0) .. [[</div>
                        <div class="stat-label">当前排队</div>
                    </div>
                </div>
            </div>
            
            <!-- 队列统计 -->
            <div class="stat-card">
                <div class="stat-title">队列统计</div>
                <table>
                    <tr><th>队列名称</th><th>总呼叫</th><th>完成</th><th>超时</th><th>平均等待</th></tr>
    ]]
    
    for _, queue in ipairs(queue_stats) do
        html = html .. string.format([[
                    <tr>
                        <td>%s</td>
                        <td>%d</td>
                        <td>%d</td>
                        <td>%d</td>
                        <td>%ds</td>
                    </tr>
        ]], queue.name, queue.total_calls or 0, queue.completed_calls or 0, 
            queue.timeout_calls or 0, queue.avg_wait_time or 0)
    end
    
    html = html .. [[
                </table>
            </div>
            
            <!-- 坐席状态 -->
            <div class="stat-card">
                <div class="stat-title">坐席状态</div>
                <table>
                    <tr><th>分机</th><th>姓名</th><th>状态</th><th>今日呼叫</th><th>完成率</th></tr>
    ]]
    
    for _, agent in ipairs(agent_stats) do
        local status_class = "status-" .. agent.status
        local completion_rate = 0
        if agent.today_calls > 0 then
            completion_rate = math.floor((agent.completed_calls / agent.today_calls) * 100)
        end
        
        html = html .. string.format([[
                    <tr>
                        <td>%s</td>
                        <td>%s</td>
                        <td class="%s">%s</td>
                        <td>%d</td>
                        <td>%d%%</td>
                    </tr>
        ]], agent.extension, agent.name, status_class, agent.status, 
            agent.today_calls, completion_rate)
    end
    
    html = html .. [[
                </table>
            </div>
        </div>
        
        <div class="timestamp">
            报告生成时间: ]] .. os.date("%Y-%m-%d %H:%M:%S") .. [[
        </div>
    </div>
</body>
</html>
    ]]
    
    return html, "报告生成成功"
end

-- API处理函数
function handle_api_request(cmd, args)
    local action = args[1]
    
    if action == "overview" then
        local data, msg = get_system_overview()
        if data then
            return json.encode(data)
        else
            return "ERROR: " .. msg
        end
        
    elseif action == "queues" then
        local data, msg = get_realtime_queue_stats()
        if data then
            return json.encode(data)
        else
            return "ERROR: " .. msg
        end
        
    elseif action == "agents" then
        local data, msg = get_agent_stats()
        if data then
            return json.encode(data)
        else
            return "ERROR: " .. msg
        end
        
    elseif action == "waiting" then
        local data, msg = get_current_queue_waiting()
        if data then
            return json.encode(data)
        else
            return "ERROR: " .. msg
        end
        
    elseif action == "html" then
        local html, msg = generate_html_report()
        if html then
            return html
        else
            return "ERROR: " .. msg
        end
    end
    
    return "ERROR: 未知操作"
end

-- 主函数
function main(session, action)
    if not session then
        -- API调用模式
        return handle_api_request(action, {})
    end
    
    -- 会话模式 - 播放语音统计
    play_voice_stats(session)
end

-- 如果直接运行脚本
if session then
    main(session, argv[1])
end