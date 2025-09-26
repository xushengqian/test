--[[
FreeSWITCH 人工坐席队列管理脚本
功能：管理呼叫队列，分配坐席，处理排队逻辑
作者：AI Assistant
版本：1.0
]]--

-- 引入必要的模块
require "luasql.sqlite3"

-- 全局配置
local config = {
    db_path = "/usr/local/freeswitch/db/agent_queue.db",
    max_queue_size = 100,
    max_wait_time = 300, -- 最大等待时间（秒）
    ring_timeout = 30,   -- 坐席振铃超时时间
    music_on_hold = "local_stream://moh", -- 等待音乐
    queue_announcement = "/usr/local/freeswitch/sounds/queue_announcement.wav"
}

-- 数据库连接
local env = luasql.sqlite3()
local conn = nil

-- 初始化数据库连接
function init_database()
    conn = env:connect(config.db_path)
    if not conn then
        freeswitch.consoleLog("ERROR", "无法连接到数据库: " .. config.db_path)
        return false
    end
    
    -- 创建必要的表
    create_tables()
    return true
end

-- 创建数据库表
function create_tables()
    -- 坐席表
    local agent_table = [[
        CREATE TABLE IF NOT EXISTS agents (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            extension VARCHAR(20) UNIQUE NOT NULL,
            name VARCHAR(100) NOT NULL,
            status VARCHAR(20) DEFAULT 'offline',
            last_call_time DATETIME,
            total_calls INTEGER DEFAULT 0,
            skill_groups VARCHAR(200),
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    ]]
    
    -- 队列表
    local queue_table = [[
        CREATE TABLE IF NOT EXISTS queues (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name VARCHAR(50) UNIQUE NOT NULL,
            description VARCHAR(200),
            max_wait_time INTEGER DEFAULT 300,
            ring_timeout INTEGER DEFAULT 30,
            strategy VARCHAR(20) DEFAULT 'round_robin',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    ]]
    
    -- 呼叫记录表
    local call_log_table = [[
        CREATE TABLE IF NOT EXISTS call_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            uuid VARCHAR(100) UNIQUE NOT NULL,
            caller_number VARCHAR(20),
            queue_name VARCHAR(50),
            agent_extension VARCHAR(20),
            start_time DATETIME,
            answer_time DATETIME,
            end_time DATETIME,
            status VARCHAR(20),
            wait_time INTEGER,
            talk_time INTEGER
        )
    ]]
    
    conn:execute(agent_table)
    conn:execute(queue_table)
    conn:execute(call_log_table)
end

-- 获取可用坐席
function get_available_agent(queue_name, skill_groups)
    local query = [[
        SELECT extension, name FROM agents 
        WHERE status = 'available' 
        AND (skill_groups LIKE '%]] .. (skill_groups or '') .. [[%' OR skill_groups IS NULL)
        ORDER BY last_call_time ASC, total_calls ASC 
        LIMIT 1
    ]]
    
    local cursor = conn:execute(query)
    if cursor then
        local row = cursor:fetch({}, "a")
        cursor:close()
        if row then
            return row.extension, row.name
        end
    end
    return nil, nil
end

-- 更新坐席状态
function update_agent_status(extension, status)
    local query = string.format(
        "UPDATE agents SET status = '%s', last_call_time = datetime('now') WHERE extension = '%s'",
        status, extension
    )
    conn:execute(query)
end

-- 记录呼叫日志
function log_call(uuid, caller_number, queue_name, status, agent_extension)
    local query = string.format([[
        INSERT OR REPLACE INTO call_logs 
        (uuid, caller_number, queue_name, agent_extension, start_time, status)
        VALUES ('%s', '%s', '%s', '%s', datetime('now'), '%s')
    ]], uuid, caller_number or '', queue_name or '', agent_extension or '', status)
    
    conn:execute(query)
end

-- 更新呼叫结束时间
function update_call_end(uuid, status)
    local query = string.format([[
        UPDATE call_logs 
        SET end_time = datetime('now'), status = '%s'
        WHERE uuid = '%s'
    ]], status, uuid)
    
    conn:execute(query)
end

-- 播放队列提示音
function play_queue_announcement(session)
    if session:ready() then
        session:answer()
        session:sleep(500)
        session:streamFile(config.queue_announcement)
    end
end

-- 转接到坐席
function transfer_to_agent(session, agent_extension, caller_number, queue_name)
    local uuid = session:get_uuid()
    
    freeswitch.consoleLog("INFO", string.format(
        "正在将呼叫 %s 从队列 %s 转接到坐席 %s", 
        caller_number, queue_name, agent_extension
    ))
    
    -- 更新坐席状态为忙碌
    update_agent_status(agent_extension, "busy")
    
    -- 记录转接日志
    log_call(uuid, caller_number, queue_name, "transferred", agent_extension)
    
    -- 执行转接
    session:execute("bridge", string.format(
        "{call_timeout=%d,origination_caller_id_number=%s}user/%s",
        config.ring_timeout, caller_number, agent_extension
    ))
    
    -- 检查通话结果
    local cause = session:getVariable("bridge_hangup_cause") or session:hangupCause()
    
    if cause == "NORMAL_CLEARING" then
        update_call_end(uuid, "completed")
        freeswitch.consoleLog("INFO", "通话正常结束")
    else
        update_call_end(uuid, "failed")
        freeswitch.consoleLog("WARNING", "通话异常结束: " .. cause)
    end
    
    -- 恢复坐席状态
    update_agent_status(agent_extension, "available")
end

-- 处理队列等待
function handle_queue_wait(session, queue_name, skill_groups)
    local uuid = session:get_uuid()
    local caller_number = session:getVariable("caller_id_number")
    local start_time = os.time()
    local max_wait = config.max_wait_time
    
    freeswitch.consoleLog("INFO", string.format(
        "呼叫 %s 进入队列 %s 等待", caller_number, queue_name
    ))
    
    -- 播放欢迎提示
    play_queue_announcement(session)
    
    -- 记录进入队列
    log_call(uuid, caller_number, queue_name, "queued", nil)
    
    -- 开始等待循环
    while session:ready() and (os.time() - start_time) < max_wait do
        -- 查找可用坐席
        local agent_extension, agent_name = get_available_agent(queue_name, skill_groups)
        
        if agent_extension then
            freeswitch.consoleLog("INFO", string.format(
                "找到可用坐席 %s (%s)，准备转接", agent_extension, agent_name
            ))
            
            transfer_to_agent(session, agent_extension, caller_number, queue_name)
            return
        end
        
        -- 播放等待音乐
        session:execute("playback", config.music_on_hold)
        session:sleep(1000)
        
        -- 每30秒播放一次排队提示
        if (os.time() - start_time) % 30 == 0 then
            session:execute("playback", "ivr/ivr-please_hold_while_we_try.wav")
        end
    end
    
    -- 等待超时处理
    freeswitch.consoleLog("WARNING", string.format(
        "呼叫 %s 在队列 %s 中等待超时", caller_number, queue_name
    ))
    
    update_call_end(uuid, "timeout")
    session:execute("playback", "ivr/ivr-call_cannot_be_completed_as_dialed.wav")
    session:hangup()
end

-- 主入口函数
function main(session, queue_name, skill_groups)
    -- 初始化数据库
    if not init_database() then
        session:execute("playback", "ivr/ivr-system_error.wav")
        session:hangup()
        return
    end
    
    -- 参数验证
    queue_name = queue_name or "default"
    skill_groups = skill_groups or ""
    
    local caller_number = session:getVariable("caller_id_number")
    
    freeswitch.consoleLog("INFO", string.format(
        "处理队列呼叫: 主叫=%s, 队列=%s, 技能组=%s", 
        caller_number, queue_name, skill_groups
    ))
    
    -- 处理队列等待
    handle_queue_wait(session, queue_name, skill_groups)
    
    -- 清理数据库连接
    if conn then
        conn:close()
    end
    env:close()
end

-- 如果直接运行脚本
if session then
    main(session, argv[1], argv[2])
end