--[[
    实际使用示例：使用 AgentUtils 工具库进行转接
]]--

-- 加载工具库
local AgentUtils = require("agent_utils")

-- 检查session
if not session or not session:ready() then
    freeswitch.consoleLog("err", "Session not ready\n")
    return
end

-- 应答呼叫
if not session:answered() then
    session:answer()
    session:sleep(500)
end

-- 获取主叫号码
local caller_id = session:getVariable("caller_id_number")
local call_uuid = session:get_uuid()

AgentUtils.log("info", "收到呼叫: " .. caller_id .. " [" .. call_uuid .. "]")

-- 示例1: 单个坐席转接
local function example1_single_agent()
    local agent_number = "1001"
    
    -- 播放提示音
    AgentUtils.playTransferPrompt(session)
    
    -- 执行转接
    local success, result = AgentUtils.transfer(session, agent_number, 30)
    
    if success then
        AgentUtils.log("info", "转接成功")
        -- 记录日志
        AgentUtils.logToDatabase(call_uuid, caller_id, agent_number, true, "SUCCESS")
    else
        AgentUtils.log("warning", "转接失败: " .. result)
        -- 播放失败提示
        AgentUtils.playFailurePrompt(session, result)
        -- 记录日志
        AgentUtils.logToDatabase(call_uuid, caller_id, agent_number, false, result)
    end
end

-- 示例2: 智能转接到多个坐席
local function example2_smart_transfer()
    -- 坐席列表（按优先级排序）
    local agent_list = {"1001", "1002", "1003"}
    
    -- 先检查哪些坐席可用
    local available = AgentUtils.getAvailableAgents(agent_list)
    
    if #available == 0 then
        AgentUtils.log("warning", "没有可用的坐席")
        AgentUtils.playFailurePrompt(session, "ALL_AGENTS_UNAVAILABLE")
        session:hangup()
        return
    end
    
    AgentUtils.log("info", "找到 " .. #available .. " 个可用坐席")
    
    -- 播放提示音
    AgentUtils.playTransferPrompt(session)
    
    -- 智能转接
    local success, agent = AgentUtils.smartTransfer(session, agent_list, 30)
    
    if success then
        AgentUtils.log("info", "成功转接到坐席: " .. agent)
        AgentUtils.logToDatabase(call_uuid, caller_id, agent, true, "SUCCESS")
    else
        AgentUtils.log("warning", "所有坐席转接失败")
        AgentUtils.playFailurePrompt(session, "ALL_AGENTS_UNAVAILABLE")
        AgentUtils.logToDatabase(call_uuid, caller_id, "MULTIPLE", false, agent)
        session:hangup()
    end
end

-- 示例3: 检查坐席状态后再决定
local function example3_check_before_transfer()
    local agent_number = "1001"
    
    -- 先检查状态
    local status = AgentUtils.getStatus(agent_number)
    AgentUtils.log("info", "坐席 " .. agent_number .. " 状态: " .. status)
    
    if status == "OFFLINE" then
        -- 坐席离线，直接返回
        session:streamFile("ivr/ivr-no_user_response.wav")
        session:hangup()
        return
    elseif status == "BUSY" then
        -- 坐席忙碌，询问是否等待
        session:streamFile("ivr/ivr-user_busy.wav")
        session:execute("sleep", "2000")
        -- 可以转语音留言或其他处理
        session:hangup()
        return
    else
        -- 坐席空闲，执行转接
        AgentUtils.playTransferPrompt(session)
        AgentUtils.transfer(session, agent_number, 30)
    end
end

-- 示例4: 带重试的转接
local function example4_transfer_with_retry()
    local agent_number = "1001"
    local max_retry = 3
    local retry_interval = 5  -- 秒
    
    for i = 1, max_retry do
        AgentUtils.log("info", "第 " .. i .. " 次尝试")
        
        -- 检查坐席是否在线
        if not AgentUtils.isRegistered(agent_number) then
            AgentUtils.log("warning", "坐席未注册，停止重试")
            break
        end
        
        -- 播放提示音
        if i == 1 then
            AgentUtils.playTransferPrompt(session)
        else
            session:streamFile("ivr/ivr-please_try_again.wav")
        end
        
        -- 执行转接
        local success, result = AgentUtils.transfer(session, agent_number, 20)
        
        if success then
            AgentUtils.log("info", "转接成功")
            return
        end
        
        -- 如果不是最后一次，等待后重试
        if i < max_retry and session:ready() then
            AgentUtils.log("info", "等待 " .. retry_interval .. " 秒后重试")
            session:sleep(retry_interval * 1000)
        end
    end
    
    -- 所有尝试都失败
    AgentUtils.log("warning", "转接失败，已达最大重试次数")
    AgentUtils.playFailurePrompt(session, "NO_ANSWER")
    session:hangup()
end

-- 主程序：根据配置选择使用哪个示例
local transfer_mode = session:getVariable("transfer_mode") or "smart"

if transfer_mode == "single" then
    example1_single_agent()
elseif transfer_mode == "smart" then
    example2_smart_transfer()
elseif transfer_mode == "check" then
    example3_check_before_transfer()
elseif transfer_mode == "retry" then
    example4_transfer_with_retry()
else
    -- 默认使用智能转接
    example2_smart_transfer()
end
