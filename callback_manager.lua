-- FreeSWITCH Lua Script for Callback Management
-- 回拨管理脚本

-- 获取参数
local action = argv and argv[1] or "request"  -- request, process, cancel
local phone_number = argv and argv[2] or (session and session:getVariable("caller_id_number"))
local queue_name = argv and argv[3] or "general"

-- 初始化会话
if session then
    session:answer()
end

-- 函数：创建回拨请求
function create_callback_request(phone_number, queue_name)
    freeswitch.consoleLog("info", "创建回拨请求: " .. phone_number .. " 队列: " .. queue_name .. "\n")
    
    if session then
        -- 询问客户姓名
        session:execute("playback", "ivr/ivr-please_say_your_name.wav")
        session:execute("playback", "tone_stream://%(1000,0,500)")
        
        -- 录制姓名（最长3秒）
        local name_recording = "/tmp/callback_name_" .. phone_number .. "_" .. os.time() .. ".wav"
        session:execute("record", name_recording .. " 3 100 3")
        
        -- 询问希望回拨的时间
        session:execute("playback", "ivr/ivr-callback_time_preference.wav")
        session:streamFile("ivr/ivr-press_1_for_asap.wav")
        session:streamFile("ivr/ivr-press_2_for_30_minutes.wav")
        session:streamFile("ivr/ivr-press_3_for_1_hour.wav")
        session:streamFile("ivr/ivr-press_4_for_2_hours.wav")
        
        local time_choice = session:playAndGetDigits(1, 1, 3, 3000, "#", "", "", "\\d", "time_choice")
        
        local callback_time = nil
        local wait_minutes = 0
        
        if time_choice == "1" then
            wait_minutes = 0
        elseif time_choice == "2" then
            wait_minutes = 30
        elseif time_choice == "3" then
            wait_minutes = 60
        elseif time_choice == "4" then
            wait_minutes = 120
        else
            wait_minutes = 0
        end
        
        if wait_minutes > 0 then
            callback_time = os.time() + (wait_minutes * 60)
        end
        
        -- 保存回拨请求
        local request_id = save_callback_request(phone_number, queue_name, callback_time, name_recording)
        
        if request_id then
            session:execute("playback", "ivr/ivr-callback_request_received.wav")
            
            if wait_minutes == 0 then
                session:execute("playback", "ivr/ivr-callback_asap.wav")
            else
                session:speak("我们将在" .. wait_minutes .. "分钟后回拨给您")
            end
            
            session:execute("playback", "ivr/ivr-thank_you.wav")
            
            -- 将请求加入处理队列
            schedule_callback(request_id, phone_number, queue_name, callback_time)
        else
            session:execute("playback", "ivr/ivr-callback_request_failed.wav")
        end
    else
        -- 非会话模式，直接创建请求
        local request_id = save_callback_request(phone_number, queue_name, nil, nil)
        if request_id then
            schedule_callback(request_id, phone_number, queue_name, nil)
        end
    end
end

-- 函数：保存回拨请求
function save_callback_request(phone_number, queue_name, callback_time, name_recording)
    local request_id = "CB_" .. os.time() .. "_" .. math.random(1000, 9999)
    
    -- 保存到全局变量
    freeswitch.setGlobalVariable("callback:" .. request_id .. ":phone", phone_number)
    freeswitch.setGlobalVariable("callback:" .. request_id .. ":queue", queue_name)
    freeswitch.setGlobalVariable("callback:" .. request_id .. ":time", callback_time or os.time())
    freeswitch.setGlobalVariable("callback:" .. request_id .. ":status", "pending")
    freeswitch.setGlobalVariable("callback:" .. request_id .. ":attempts", "0")
    
    if name_recording then
        freeswitch.setGlobalVariable("callback:" .. request_id .. ":name_recording", name_recording)
    end
    
    -- 添加到待处理列表
    local pending_list = freeswitch.getGlobalVariable("callback:pending_list") or ""
    if pending_list == "" then
        pending_list = request_id
    else
        pending_list = pending_list .. "," .. request_id
    end
    freeswitch.setGlobalVariable("callback:pending_list", pending_list)
    
    freeswitch.consoleLog("info", "回拨请求已保存: " .. request_id .. "\n")
    
    return request_id
end

-- 函数：计划回拨
function schedule_callback(request_id, phone_number, queue_name, callback_time)
    local delay = 0
    
    if callback_time and callback_time > os.time() then
        delay = callback_time - os.time()
    end
    
    -- 创建计划任务
    local task_command = string.format(
        "lua callback_manager.lua process %s %s",
        request_id,
        queue_name
    )
    
    if delay > 0 then
        -- 延迟执行
        local api = freeswitch.API()
        api:executeString(string.format("sched_api +%d none %s", delay, task_command))
        
        freeswitch.consoleLog("info", 
            string.format("回拨已计划: %s 将在 %d 秒后执行\n", request_id, delay)
        )
    else
        -- 立即加入队列
        add_to_callback_queue(request_id)
    end
end

-- 函数：加入回拨队列
function add_to_callback_queue(request_id)
    local callback_queue = freeswitch.getGlobalVariable("callback:queue") or ""
    
    if callback_queue == "" then
        callback_queue = request_id
    else
        callback_queue = callback_queue .. "," .. request_id
    end
    
    freeswitch.setGlobalVariable("callback:queue", callback_queue)
    freeswitch.setGlobalVariable("callback:" .. request_id .. ":status", "queued")
    
    freeswitch.consoleLog("info", "回拨请求已加入队列: " .. request_id .. "\n")
end

-- 函数：处理回拨
function process_callback(request_id)
    local phone_number = freeswitch.getGlobalVariable("callback:" .. request_id .. ":phone")
    local queue_name = freeswitch.getGlobalVariable("callback:" .. request_id .. ":queue")
    local attempts = tonumber(freeswitch.getGlobalVariable("callback:" .. request_id .. ":attempts") or "0")
    
    if not phone_number then
        freeswitch.consoleLog("error", "回拨请求无效: " .. request_id .. "\n")
        return false
    end
    
    freeswitch.consoleLog("info", "处理回拨: " .. request_id .. " 电话: " .. phone_number .. "\n")
    
    -- 更新状态
    freeswitch.setGlobalVariable("callback:" .. request_id .. ":status", "processing")
    attempts = attempts + 1
    freeswitch.setGlobalVariable("callback:" .. request_id .. ":attempts", attempts)
    
    -- 查找可用坐席
    local available_agent = find_available_agent_for_callback(queue_name)
    
    if available_agent then
        -- 发起回拨
        local success = initiate_callback(request_id, phone_number, available_agent)
        
        if success then
            freeswitch.setGlobalVariable("callback:" .. request_id .. ":status", "completed")
            remove_from_callback_queue(request_id)
            return true
        end
    end
    
    -- 如果失败，检查重试次数
    if attempts < 3 then
        -- 5分钟后重试
        freeswitch.consoleLog("info", "回拨失败，将在5分钟后重试: " .. request_id .. "\n")
        
        local api = freeswitch.API()
        api:executeString(string.format(
            "sched_api +300 none lua callback_manager.lua process %s %s",
            request_id,
            queue_name
        ))
        
        freeswitch.setGlobalVariable("callback:" .. request_id .. ":status", "retry_scheduled")
    else
        -- 超过最大重试次数
        freeswitch.consoleLog("warning", "回拨失败，已达最大重试次数: " .. request_id .. "\n")
        freeswitch.setGlobalVariable("callback:" .. request_id .. ":status", "failed")
        remove_from_callback_queue(request_id)
    end
    
    return false
end

-- 函数：查找可用坐席进行回拨
function find_available_agent_for_callback(queue_name)
    local agents = freeswitch.getGlobalVariable("queue:" .. queue_name .. ":agents") or ""
    
    if agents ~= "" then
        for agent in string.gmatch(agents, "([^,]+)") do
            local status = freeswitch.getGlobalVariable("agent:" .. agent .. ":status")
            if status == "available" then
                return agent
            end
        end
    end
    
    return nil
end

-- 函数：发起回拨
function initiate_callback(request_id, customer_number, agent_id)
    local agent_extension = freeswitch.getGlobalVariable("agent:" .. agent_id .. ":extension")
    
    if not agent_extension then
        freeswitch.consoleLog("error", "坐席分机未找到: " .. agent_id .. "\n")
        return false
    end
    
    -- 设置坐席为忙碌
    freeswitch.setGlobalVariable("agent:" .. agent_id .. ":status", "busy")
    
    -- 构建呼叫命令
    local originate_string = string.format(
        "{origination_caller_id_number=CallbackSystem," ..
        "origination_caller_id_name=Callback," ..
        "callback_request_id=%s," ..
        "callback_customer=%s}user/%s",
        request_id,
        customer_number,
        agent_extension
    )
    
    -- 先呼叫坐席
    local api = freeswitch.API()
    local result = api:executeString(string.format(
        "originate %s &bridge(user/%s)",
        originate_string,
        customer_number
    ))
    
    if result:match("OK") then
        freeswitch.consoleLog("info", "回拨成功发起: " .. request_id .. "\n")
        return true
    else
        freeswitch.consoleLog("error", "回拨发起失败: " .. result .. "\n")
        -- 恢复坐席状态
        freeswitch.setGlobalVariable("agent:" .. agent_id .. ":status", "available")
        return false
    end
end

-- 函数：从队列中移除
function remove_from_callback_queue(request_id)
    local callback_queue = freeswitch.getGlobalVariable("callback:queue") or ""
    local new_queue = {}
    
    for id in string.gmatch(callback_queue, "([^,]+)") do
        if id ~= request_id then
            table.insert(new_queue, id)
        end
    end
    
    freeswitch.setGlobalVariable("callback:queue", table.concat(new_queue, ","))
end

-- 函数：取消回拨
function cancel_callback(phone_number)
    local pending_list = freeswitch.getGlobalVariable("callback:pending_list") or ""
    local found = false
    
    for request_id in string.gmatch(pending_list, "([^,]+)") do
        local req_phone = freeswitch.getGlobalVariable("callback:" .. request_id .. ":phone")
        local status = freeswitch.getGlobalVariable("callback:" .. request_id .. ":status")
        
        if req_phone == phone_number and (status == "pending" or status == "queued") then
            freeswitch.setGlobalVariable("callback:" .. request_id .. ":status", "cancelled")
            remove_from_callback_queue(request_id)
            found = true
            
            freeswitch.consoleLog("info", "回拨请求已取消: " .. request_id .. "\n")
            
            if session then
                session:execute("playback", "ivr/ivr-callback_cancelled.wav")
            end
            
            break
        end
    end
    
    if not found and session then
        session:execute("playback", "ivr/ivr-no_callback_request_found.wav")
    end
    
    return found
end

-- 函数：处理回拨队列（后台任务）
function process_callback_queue()
    while true do
        local callback_queue = freeswitch.getGlobalVariable("callback:queue") or ""
        
        if callback_queue ~= "" then
            -- 获取队列中的第一个请求
            local request_id = callback_queue:match("([^,]+)")
            
            if request_id then
                local status = freeswitch.getGlobalVariable("callback:" .. request_id .. ":status")
                
                if status == "queued" then
                    process_callback(request_id)
                end
            end
        end
        
        -- 每10秒检查一次
        os.execute("sleep 10")
    end
end

-- 主程序
if action == "request" then
    create_callback_request(phone_number, queue_name)
elseif action == "process" then
    local request_id = argv[2]
    if request_id then
        process_callback(request_id)
    else
        process_callback_queue()
    end
elseif action == "cancel" then
    cancel_callback(phone_number)
elseif action == "queue_processor" then
    -- 后台队列处理器
    process_callback_queue()
else
    freeswitch.consoleLog("warning", "未知操作: " .. action .. "\n")
end

-- 清理
if session and session:ready() then
    session:hangup()
end