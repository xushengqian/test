-- FreeSWITCH Lua Script for Queue Statistics
-- 队列统计脚本

-- 初始化会话
if session then
    session:answer()
end

-- 获取所有队列的统计信息
function get_all_queues_stats()
    local queues = {"support_queue", "sales_queue", "billing_queue", "vip_queue"}
    local stats = {}
    
    for _, queue_name in ipairs(queues) do
        local queue_stats = {
            name = queue_name,
            agents_total = 0,
            agents_available = 0,
            agents_busy = 0,
            agents_break = 0,
            calls_waiting = 0,
            longest_wait = 0
        }
        
        -- 获取队列成员
        local members = freeswitch.getGlobalVariable("queue:" .. queue_name .. ":agents") or ""
        
        if members ~= "" then
            for agent in string.gmatch(members, "([^,]+)") do
                queue_stats.agents_total = queue_stats.agents_total + 1
                
                local status = freeswitch.getGlobalVariable("agent:" .. agent .. ":status")
                if status == "available" then
                    queue_stats.agents_available = queue_stats.agents_available + 1
                elseif status == "busy" then
                    queue_stats.agents_busy = queue_stats.agents_busy + 1
                elseif status == "break" then
                    queue_stats.agents_break = queue_stats.agents_break + 1
                end
            end
        end
        
        -- 获取等待呼叫数
        queue_stats.calls_waiting = tonumber(freeswitch.getGlobalVariable("queue:" .. queue_name .. ":waiting") or "0")
        
        -- 获取最长等待时间
        queue_stats.longest_wait = tonumber(freeswitch.getGlobalVariable("queue:" .. queue_name .. ":longest_wait") or "0")
        
        table.insert(stats, queue_stats)
    end
    
    return stats
end

-- 函数：播报队列统计
function announce_queue_stats()
    local stats = get_all_queues_stats()
    
    if session then
        session:execute("playback", "ivr/ivr-queue_statistics.wav")
        session:sleep(500)
    end
    
    for _, queue in ipairs(stats) do
        local message = string.format(
            "队列 %s: 总坐席 %d 人，可用 %d 人，忙碌 %d 人，休息 %d 人，等待呼叫 %d 个",
            queue.name:gsub("_queue", ""),
            queue.agents_total,
            queue.agents_available,
            queue.agents_busy,
            queue.agents_break,
            queue.calls_waiting
        )
        
        freeswitch.consoleLog("info", message .. "\n")
        
        if session then
            -- 使用TTS播报统计信息
            session:speak(message)
            session:sleep(1000)
        end
    end
end

-- 函数：生成统计报告
function generate_stats_report()
    local report = {
        timestamp = os.date("%Y-%m-%d %H:%M:%S"),
        queues = get_all_queues_stats(),
        summary = {
            total_agents = 0,
            total_available = 0,
            total_busy = 0,
            total_calls_waiting = 0
        }
    }
    
    -- 计算总计
    for _, queue in ipairs(report.queues) do
        report.summary.total_agents = report.summary.total_agents + queue.agents_total
        report.summary.total_available = report.summary.total_available + queue.agents_available
        report.summary.total_busy = report.summary.total_busy + queue.agents_busy
        report.summary.total_calls_waiting = report.summary.total_calls_waiting + queue.calls_waiting
    end
    
    -- 输出报告
    freeswitch.consoleLog("info", "=== 队列统计报告 ===\n")
    freeswitch.consoleLog("info", "时间: " .. report.timestamp .. "\n")
    freeswitch.consoleLog("info", "-------------------\n")
    
    for _, queue in ipairs(report.queues) do
        freeswitch.consoleLog("info", string.format(
            "%s - 坐席: %d/%d 可用, %d 忙碌 | 等待: %d\n",
            queue.name,
            queue.agents_available,
            queue.agents_total,
            queue.agents_busy,
            queue.calls_waiting
        ))
    end
    
    freeswitch.consoleLog("info", "-------------------\n")
    freeswitch.consoleLog("info", string.format(
        "总计 - 坐席: %d/%d 可用, %d 忙碌 | 总等待: %d\n",
        report.summary.total_available,
        report.summary.total_agents,
        report.summary.total_busy,
        report.summary.total_calls_waiting
    ))
    freeswitch.consoleLog("info", "===================\n")
    
    return report
end

-- 函数：实时监控（用于后台任务）
function realtime_monitoring()
    while true do
        local stats = get_all_queues_stats()
        
        for _, queue in ipairs(stats) do
            -- 检查是否需要报警
            if queue.calls_waiting > 10 then
                freeswitch.consoleLog("warning", 
                    string.format("警告: %s 等待呼叫过多: %d\n", queue.name, queue.calls_waiting)
                )
            end
            
            if queue.agents_available == 0 and queue.calls_waiting > 0 then
                freeswitch.consoleLog("warning", 
                    string.format("警告: %s 无可用坐席，但有 %d 个呼叫等待\n", queue.name, queue.calls_waiting)
                )
            end
            
            -- 更新全局变量
            freeswitch.setGlobalVariable("queue:" .. queue.name .. ":stats_agents_total", queue.agents_total)
            freeswitch.setGlobalVariable("queue:" .. queue.name .. ":stats_agents_available", queue.agents_available)
            freeswitch.setGlobalVariable("queue:" .. queue.name .. ":stats_agents_busy", queue.agents_busy)
        end
        
        -- 每30秒更新一次
        os.execute("sleep 30")
    end
end

-- 主程序
local action = argv and argv[1] or "report"

if action == "announce" then
    announce_queue_stats()
elseif action == "monitor" then
    realtime_monitoring()
else
    generate_stats_report()
end

-- 清理
if session and session:ready() then
    session:hangup()
end