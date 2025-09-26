--[[
FreeSWITCH 人工坐席系统数据库初始化脚本
功能：创建数据库表结构，插入初始数据
作者：AI Assistant
版本：1.0
]]--

require "luasql.sqlite3"

-- 配置
local config = {
    db_path = "/usr/local/freeswitch/db/agent_queue.db"
}

-- 初始化数据库
function init_database()
    local env = luasql.sqlite3()
    local conn = env:connect(config.db_path)
    
    if not conn then
        print("错误：无法连接到数据库 " .. config.db_path)
        return false
    end
    
    print("正在初始化数据库...")
    
    -- 创建坐席表
    local agent_table = [[
        CREATE TABLE IF NOT EXISTS agents (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            extension VARCHAR(20) UNIQUE NOT NULL,
            name VARCHAR(100) NOT NULL,
            password VARCHAR(100) DEFAULT '',
            status VARCHAR(20) DEFAULT 'offline',
            last_call_time DATETIME,
            total_calls INTEGER DEFAULT 0,
            skill_groups VARCHAR(200),
            max_concurrent_calls INTEGER DEFAULT 1,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    ]]
    
    -- 创建队列表
    local queue_table = [[
        CREATE TABLE IF NOT EXISTS queues (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name VARCHAR(50) UNIQUE NOT NULL,
            description VARCHAR(200),
            max_wait_time INTEGER DEFAULT 300,
            ring_timeout INTEGER DEFAULT 30,
            strategy VARCHAR(20) DEFAULT 'round_robin',
            max_queue_size INTEGER DEFAULT 100,
            announcement_file VARCHAR(200),
            moh_sound VARCHAR(200) DEFAULT 'local_stream://moh',
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    ]]
    
    -- 创建呼叫记录表
    local call_log_table = [[
        CREATE TABLE IF NOT EXISTS call_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            uuid VARCHAR(100) UNIQUE NOT NULL,
            caller_number VARCHAR(20),
            caller_name VARCHAR(100),
            queue_name VARCHAR(50),
            agent_extension VARCHAR(20),
            start_time DATETIME,
            queue_time DATETIME,
            answer_time DATETIME,
            end_time DATETIME,
            status VARCHAR(20),
            wait_time INTEGER DEFAULT 0,
            talk_time INTEGER DEFAULT 0,
            hangup_cause VARCHAR(50),
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    ]]
    
    -- 创建坐席状态历史表
    local agent_status_history_table = [[
        CREATE TABLE IF NOT EXISTS agent_status_history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            agent_extension VARCHAR(20) NOT NULL,
            old_status VARCHAR(20),
            new_status VARCHAR(20),
            change_time DATETIME DEFAULT CURRENT_TIMESTAMP,
            reason VARCHAR(200)
        )
    ]]
    
    -- 创建队列成员表
    local queue_members_table = [[
        CREATE TABLE IF NOT EXISTS queue_members (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            queue_name VARCHAR(50) NOT NULL,
            agent_extension VARCHAR(20) NOT NULL,
            priority INTEGER DEFAULT 1,
            penalty INTEGER DEFAULT 0,
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            UNIQUE(queue_name, agent_extension)
        )
    ]]
    
    -- 创建系统配置表
    local system_config_table = [[
        CREATE TABLE IF NOT EXISTS system_config (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            config_key VARCHAR(100) UNIQUE NOT NULL,
            config_value TEXT,
            description VARCHAR(200),
            created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    ]]
    
    -- 执行表创建
    local tables = {
        {"agents", agent_table},
        {"queues", queue_table},
        {"call_logs", call_log_table},
        {"agent_status_history", agent_status_history_table},
        {"queue_members", queue_members_table},
        {"system_config", system_config_table}
    }
    
    for _, table_info in ipairs(tables) do
        local table_name, table_sql = table_info[1], table_info[2]
        local result = conn:execute(table_sql)
        if result then
            print("✓ 创建表: " .. table_name)
        else
            print("✗ 创建表失败: " .. table_name)
        end
    end
    
    -- 插入初始队列数据
    print("\n正在插入初始数据...")
    
    local initial_queues = {
        {"default", "默认客服队列", 300, 30, "round_robin"},
        {"tech_support", "技术支持队列", 600, 45, "longest_idle"},
        {"sales", "销售队列", 180, 20, "round_robin"},
        {"vip", "VIP客户队列", 120, 15, "least_recent"}
    }
    
    for _, queue in ipairs(initial_queues) do
        local insert_queue = string.format([[
            INSERT OR IGNORE INTO queues 
            (name, description, max_wait_time, ring_timeout, strategy)
            VALUES ('%s', '%s', %d, %d, '%s')
        ]], queue[1], queue[2], queue[3], queue[4], queue[5])
        
        conn:execute(insert_queue)
        print("✓ 插入队列: " .. queue[1])
    end
    
    -- 插入示例坐席
    local initial_agents = {
        {"1001", "张三", "客服,技术支持"},
        {"1002", "李四", "客服,销售"},
        {"1003", "王五", "技术支持"},
        {"1004", "赵六", "销售,VIP"},
        {"1005", "钱七", "VIP"}
    }
    
    for _, agent in ipairs(initial_agents) do
        local insert_agent = string.format([[
            INSERT OR IGNORE INTO agents 
            (extension, name, skill_groups, status)
            VALUES ('%s', '%s', '%s', 'offline')
        ]], agent[1], agent[2], agent[3])
        
        conn:execute(insert_agent)
        print("✓ 插入坐席: " .. agent[1] .. " (" .. agent[2] .. ")")
    end
    
    -- 插入队列成员关系
    local queue_assignments = {
        {"default", "1001", 1},
        {"default", "1002", 1},
        {"tech_support", "1001", 1},
        {"tech_support", "1003", 1},
        {"sales", "1002", 1},
        {"sales", "1004", 1},
        {"vip", "1004", 1},
        {"vip", "1005", 1}
    }
    
    for _, assignment in ipairs(queue_assignments) do
        local insert_member = string.format([[
            INSERT OR IGNORE INTO queue_members 
            (queue_name, agent_extension, priority)
            VALUES ('%s', '%s', %d)
        ]], assignment[1], assignment[2], assignment[3])
        
        conn:execute(insert_member)
        print("✓ 分配坐席 " .. assignment[2] .. " 到队列 " .. assignment[1])
    end
    
    -- 插入系统配置
    local system_configs = {
        {"max_queue_size", "100", "队列最大长度"},
        {"default_ring_timeout", "30", "默认振铃超时时间（秒）"},
        {"default_max_wait_time", "300", "默认最大等待时间（秒）"},
        {"moh_sound", "local_stream://moh", "默认等待音乐"},
        {"announcement_path", "/usr/local/freeswitch/sounds/", "提示音文件路径"},
        {"recording_path", "/usr/local/freeswitch/recordings/", "录音文件路径"},
        {"auto_logout_time", "3600", "坐席自动登出时间（秒）"},
        {"queue_stats_interval", "60", "队列统计更新间隔（秒）"}
    }
    
    for _, config in ipairs(system_configs) do
        local insert_config = string.format([[
            INSERT OR IGNORE INTO system_config 
            (config_key, config_value, description)
            VALUES ('%s', '%s', '%s')
        ]], config[1], config[2], config[3])
        
        conn:execute(insert_config)
        print("✓ 插入配置: " .. config[1])
    end
    
    -- 创建索引以提高查询性能
    print("\n正在创建索引...")
    
    local indexes = {
        "CREATE INDEX IF NOT EXISTS idx_agents_extension ON agents(extension)",
        "CREATE INDEX IF NOT EXISTS idx_agents_status ON agents(status)",
        "CREATE INDEX IF NOT EXISTS idx_call_logs_uuid ON call_logs(uuid)",
        "CREATE INDEX IF NOT EXISTS idx_call_logs_queue ON call_logs(queue_name)",
        "CREATE INDEX IF NOT EXISTS idx_call_logs_agent ON call_logs(agent_extension)",
        "CREATE INDEX IF NOT EXISTS idx_call_logs_start_time ON call_logs(start_time)",
        "CREATE INDEX IF NOT EXISTS idx_queue_members_queue ON queue_members(queue_name)",
        "CREATE INDEX IF NOT EXISTS idx_queue_members_agent ON queue_members(agent_extension)",
        "CREATE INDEX IF NOT EXISTS idx_agent_status_history_agent ON agent_status_history(agent_extension)",
        "CREATE INDEX IF NOT EXISTS idx_agent_status_history_time ON agent_status_history(change_time)"
    }
    
    for _, index_sql in ipairs(indexes) do
        conn:execute(index_sql)
        print("✓ 创建索引")
    end
    
    -- 创建视图
    print("\n正在创建视图...")
    
    local agent_stats_view = [[
        CREATE VIEW IF NOT EXISTS agent_stats AS
        SELECT 
            a.extension,
            a.name,
            a.status,
            a.skill_groups,
            a.total_calls,
            a.last_call_time,
            COUNT(cl.id) as today_calls,
            AVG(cl.talk_time) as avg_talk_time,
            SUM(CASE WHEN cl.status = 'completed' THEN 1 ELSE 0 END) as completed_calls
        FROM agents a
        LEFT JOIN call_logs cl ON a.extension = cl.agent_extension 
            AND DATE(cl.start_time) = DATE('now')
        GROUP BY a.extension, a.name, a.status, a.skill_groups, a.total_calls, a.last_call_time
    ]]
    
    local queue_stats_view = [[
        CREATE VIEW IF NOT EXISTS queue_stats AS
        SELECT 
            q.name,
            q.description,
            COUNT(cl.id) as total_calls,
            COUNT(CASE WHEN cl.status = 'completed' THEN 1 END) as completed_calls,
            COUNT(CASE WHEN cl.status = 'timeout' THEN 1 END) as timeout_calls,
            COUNT(CASE WHEN cl.status = 'abandoned' THEN 1 END) as abandoned_calls,
            AVG(cl.wait_time) as avg_wait_time,
            AVG(cl.talk_time) as avg_talk_time,
            MAX(cl.wait_time) as max_wait_time
        FROM queues q
        LEFT JOIN call_logs cl ON q.name = cl.queue_name 
            AND DATE(cl.start_time) = DATE('now')
        GROUP BY q.name, q.description
    ]]
    
    conn:execute(agent_stats_view)
    conn:execute(queue_stats_view)
    print("✓ 创建统计视图")
    
    conn:close()
    env:close()
    
    print("\n数据库初始化完成！")
    print("数据库文件位置: " .. config.db_path)
    print("\n初始坐席账号:")
    print("1001 - 张三 (客服,技术支持)")
    print("1002 - 李四 (客服,销售)")
    print("1003 - 王五 (技术支持)")
    print("1004 - 赵六 (销售,VIP)")
    print("1005 - 钱七 (VIP)")
    print("\n队列接入号码:")
    print("8000 - 默认客服队列")
    print("8001 - 技术支持队列")
    print("8002 - 销售队列")
    print("8003 - VIP客户队列")
    print("\n坐席管理号码:")
    print("9000 + 分机号 - 坐席登录")
    print("9001 + 分机号 - 坐席登出")
    print("9999 - 坐席状态管理菜单")
    
    return true
end

-- 如果直接运行脚本
if not session then
    init_database()
end