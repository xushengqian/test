-- FreeSWITCH Lua Script for Main IVR with Agent Transfer
-- 主IVR脚本，包含转接人工坐席功能

-- IVR配置
local max_attempts = 3  -- 最大尝试次数
local digit_timeout = 3000  -- 按键超时（毫秒）
local inter_digit_timeout = 2000  -- 按键间隔超时（毫秒）

-- 菜单选项配置
local menu_options = {
    ["1"] = {action = "sales", description = "销售咨询"},
    ["2"] = {action = "support", description = "技术支持"},
    ["3"] = {action = "billing", description = "账单查询"},
    ["9"] = {action = "agent", description = "转接人工坐席"},
    ["0"] = {action = "repeat", description = "重听菜单"},
    ["*"] = {action = "return", description = "返回上级菜单"}
}

-- 初始化会话
session:answer()
session:execute("sleep", "500")

-- 获取来电信息
local caller_id = session:getVariable("caller_id_number") or "unknown"
local caller_name = session:getVariable("caller_id_name") or "Unknown Caller"

freeswitch.consoleLog("info", "IVR呼入: " .. caller_id .. " (" .. caller_name .. ")\n")

-- 函数：播放菜单
function play_menu()
    session:execute("playback", "ivr/ivr-welcome.wav")
    session:sleep(500)
    
    -- 播放菜单选项
    session:streamFile("ivr/ivr-menu_options.wav")
    
    -- 或者使用TTS播放自定义菜单
    -- session:speak("欢迎致电客服中心。请选择：销售咨询请按1，技术支持请按2，账单查询请按3，转接人工坐席请按9，重听菜单请按0")
end

-- 函数：获取用户输入
function get_dtmf(max_digits, timeout)
    local digits = session:playAndGetDigits(
        1,                      -- 最小位数
        max_digits,             -- 最大位数
        max_attempts,           -- 最大尝试次数
        timeout,                -- 超时时间
        "#",                    -- 终止键
        "",                     -- 播放文件（空表示静默）
        "",                     -- 错误播放文件
        "\\d+",                 -- 正则表达式
        "digits",               -- 变量名
        inter_digit_timeout,    -- 按键间隔超时
        ""                      -- 转义字符
    )
    
    return digits
end

-- 函数：处理销售咨询
function handle_sales()
    freeswitch.consoleLog("info", "用户选择：销售咨询\n")
    session:execute("playback", "ivr/ivr-sales_menu.wav")
    
    -- 可以添加子菜单或直接转接到销售队列
    session:execute("transfer", "sales_queue XML default")
end

-- 函数：处理技术支持
function handle_support()
    freeswitch.consoleLog("info", "用户选择：技术支持\n")
    session:execute("playback", "ivr/ivr-support_menu.wav")
    
    -- 转接到技术支持队列
    session:execute("transfer", "support_queue XML default")
end

-- 函数：处理账单查询
function handle_billing()
    freeswitch.consoleLog("info", "用户选择：账单查询\n")
    session:execute("playback", "ivr/ivr-billing_menu.wav")
    
    -- 可以添加自助查询功能或转接到账单队列
    session:execute("transfer", "billing_queue XML default")
end

-- 函数：转接到人工坐席
function transfer_to_agent()
    freeswitch.consoleLog("info", "用户请求转接人工坐席\n")
    
    session:execute("playback", "ivr/ivr-transferring_to_agent.wav")
    
    -- 设置呼叫变量，供队列脚本使用
    session:setVariable("queue_priority", "normal")
    session:setVariable("queue_caller_id", caller_id)
    session:setVariable("queue_caller_name", caller_name)
    
    -- 执行队列脚本
    session:execute("lua", "agent_queue.lua")
end

-- 函数：高级转接到人工坐席（带技能组选择）
function advanced_transfer_to_agent()
    freeswitch.consoleLog("info", "高级人工坐席转接\n")
    
    -- 询问用户需要哪种服务
    session:execute("playback", "ivr/ivr-select_department.wav")
    session:streamFile("ivr/ivr-for_sales_press_1.wav")
    session:streamFile("ivr/ivr-for_support_press_2.wav")
    session:streamFile("ivr/ivr-for_billing_press_3.wav")
    
    local department = get_dtmf(1, digit_timeout)
    
    local skill_group = "general"
    if department == "1" then
        skill_group = "sales"
    elseif department == "2" then
        skill_group = "support"
    elseif department == "3" then
        skill_group = "billing"
    end
    
    -- 设置技能组变量
    session:setVariable("required_skill", skill_group)
    session:setVariable("queue_priority", "normal")
    
    -- 查找具有相应技能的坐席
    local agents = find_agents_with_skill(skill_group)
    
    if #agents > 0 then
        -- 有可用坐席，执行转接
        session:execute("playback", "ivr/ivr-transferring_to_agent.wav")
        session:execute("lua", "agent_queue.lua " .. skill_group)
    else
        -- 无可用坐席
        session:execute("playback", "ivr/ivr-no_agents_available.wav")
        session:execute("playback", "ivr/ivr-please_leave_message.wav")
        
        -- 转到语音信箱
        transfer_to_voicemail(skill_group)
    end
end

-- 函数：查找具有特定技能的坐席
function find_agents_with_skill(skill)
    local agents = {}
    
    -- 从全局变量获取技能组坐席
    local skill_agents = freeswitch.getGlobalVariable("skill:" .. skill .. ":agents")
    
    if skill_agents then
        for agent in string.gmatch(skill_agents, "([^,]+)") do
            local status = freeswitch.getGlobalVariable("agent:" .. agent .. ":status")
            if status == "available" then
                table.insert(agents, agent)
            end
        end
    end
    
    return agents
end

-- 函数：转到语音信箱
function transfer_to_voicemail(mailbox)
    freeswitch.consoleLog("info", "转接到语音信箱: " .. mailbox .. "\n")
    
    -- 设置语音信箱参数
    session:setVariable("voicemail_box", mailbox)
    session:setVariable("voicemail_domain", session:getVariable("domain_name"))
    
    -- 转接到语音信箱
    session:execute("voicemail", "default ${domain_name} " .. mailbox)
end

-- 函数：主IVR循环
function main_ivr_loop()
    local attempts = 0
    local continue_ivr = true
    
    while continue_ivr and attempts < max_attempts and session:ready() do
        play_menu()
        
        local dtmf = get_dtmf(1, digit_timeout)
        
        if dtmf and dtmf ~= "" then
            freeswitch.consoleLog("info", "用户按键: " .. dtmf .. "\n")
            
            local option = menu_options[dtmf]
            
            if option then
                if option.action == "sales" then
                    handle_sales()
                    continue_ivr = false
                elseif option.action == "support" then
                    handle_support()
                    continue_ivr = false
                elseif option.action == "billing" then
                    handle_billing()
                    continue_ivr = false
                elseif option.action == "agent" then
                    transfer_to_agent()
                    continue_ivr = false
                elseif option.action == "repeat" then
                    attempts = 0  -- 重置尝试次数
                elseif option.action == "return" then
                    -- 返回上级菜单逻辑
                    session:execute("playback", "ivr/ivr-returning_to_main_menu.wav")
                    attempts = 0
                end
            else
                session:execute("playback", "ivr/ivr-invalid_option.wav")
                attempts = attempts + 1
            end
        else
            session:execute("playback", "ivr/ivr-no_input_detected.wav")
            attempts = attempts + 1
        end
    end
    
    if attempts >= max_attempts then
        session:execute("playback", "ivr/ivr-too_many_attempts.wav")
        -- 可以选择转接到坐席或挂断
        transfer_to_agent()
    end
end

-- 主程序
if session:ready() then
    -- 播放欢迎语
    session:execute("playback", "silence_stream://500")
    
    -- 执行主IVR循环
    main_ivr_loop()
end

-- 清理
if session:ready() then
    session:hangup()
end