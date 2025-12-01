--[[
    FreeSWITCH Lua 转接人工脚本
    功能：判断转接是否成功，处理坐席未登录等异常情况
]]--

-- 获取session对象
-- session参数由FreeSWITCH自动传入
if not session:ready() then
    freeswitch.consoleLog("err", "Session not ready\n")
    return
end

-- 应答呼叫（如果还未应答）
if not session:answered() then
    session:answer()
end

-- 配置参数
local agent_number = session:getVariable("agent_number") or "1001"  -- 坐席分机号
local ring_timeout = 30  -- 振铃超时时间（秒）
local max_retry = 3  -- 最大重试次数

-- 日志函数
local function log(level, msg)
    freeswitch.consoleLog(level, "[Transfer Agent] " .. msg .. "\n")
end

-- 检查坐席是否在线（注册）
local function check_agent_registered(agent_ext)
    local api = freeswitch.API()
    -- 使用sofia status profile命令检查用户注册状态
    local result = api:executeString("sofia_contact " .. agent_ext)
    
    log("info", "检查坐席 " .. agent_ext .. " 注册状态: " .. tostring(result))
    
    -- 如果返回包含error或者为空，说明坐席未注册
    if result == nil or result == "" or string.find(result, "error") then
        return false
    end
    
    return true
end

-- 转接到坐席
local function transfer_to_agent(agent_ext, timeout)
    log("info", "开始转接到坐席: " .. agent_ext)
    
    -- 先检查坐席是否在线
    if not check_agent_registered(agent_ext) then
        log("warning", "坐席 " .. agent_ext .. " 未注册/未登录")
        return false, "AGENT_OFFLINE"
    end
    
    -- 设置转接参数
    session:setVariable("call_timeout", timeout)
    session:setVariable("continue_on_fail", "true")
    session:setVariable("hangup_after_bridge", "true")
    
    -- 构造转接字符串
    local dial_string = string.format("user/%s", agent_ext)
    
    -- 执行bridge（桥接）
    log("info", "正在桥接到: " .. dial_string)
    session:execute("bridge", dial_string)
    
    -- 获取桥接结果
    local hangup_cause = session:getVariable("originate_disposition") or 
                         session:getVariable("bridge_hangup_cause") or
                         "UNKNOWN"
    
    log("info", "桥接结果: " .. hangup_cause)
    
    -- 判断转接是否成功
    if hangup_cause == "SUCCESS" then
        log("info", "转接成功")
        return true, "SUCCESS"
    elseif hangup_cause == "USER_NOT_REGISTERED" then
        log("warning", "坐席未注册")
        return false, "AGENT_OFFLINE"
    elseif hangup_cause == "NO_ANSWER" or hangup_cause == "CALL_REJECTED" then
        log("warning", "坐席无应答或拒接")
        return false, "NO_ANSWER"
    elseif hangup_cause == "USER_BUSY" then
        log("warning", "坐席忙线")
        return false, "AGENT_BUSY"
    else
        log("warning", "转接失败: " .. hangup_cause)
        return false, hangup_cause
    end
end

-- 播放语音提示
local function play_prompt(file)
    if session:ready() then
        session:streamFile(file)
    end
end

-- 主流程
log("info", "开始转接流程")

local retry_count = 0
local success = false
local last_reason = ""

while retry_count < max_retry and not success and session:ready() do
    retry_count = retry_count + 1
    log("info", "第 " .. retry_count .. " 次尝试转接")
    
    -- 播放等待提示音
    play_prompt("ivr/ivr-please_hold_while_party_contacted.wav")
    
    -- 尝试转接
    success, last_reason = transfer_to_agent(agent_number, ring_timeout)
    
    if not success then
        if last_reason == "AGENT_OFFLINE" then
            -- 坐席未登录，直接退出重试
            log("warning", "坐席未登录，停止重试")
            break
        elseif last_reason == "NO_ANSWER" then
            -- 坐席无应答，可以重试
            if retry_count < max_retry then
                play_prompt("ivr/ivr-call_cannot_be_completed_as_dialed.wav")
                session:sleep(1000)  -- 等待1秒后重试
            end
        elseif last_reason == "AGENT_BUSY" then
            -- 坐席忙线
            log("warning", "坐席忙线")
            break
        end
    end
end

-- 处理最终结果
if not success then
    log("warning", "转接失败，原因: " .. last_reason)
    
    -- 根据失败原因播放不同的提示音
    if last_reason == "AGENT_OFFLINE" then
        play_prompt("ivr/ivr-no_user_response.wav")
        session:setVariable("transfer_result", "agent_offline")
    elseif last_reason == "NO_ANSWER" then
        play_prompt("ivr/ivr-no_user_response.wav")
        session:setVariable("transfer_result", "no_answer")
    elseif last_reason == "AGENT_BUSY" then
        play_prompt("ivr/ivr-user_busy.wav")
        session:setVariable("transfer_result", "agent_busy")
    else
        play_prompt("ivr/ivr-call_cannot_be_completed_as_dialed.wav")
        session:setVariable("transfer_result", "failed")
    end
    
    -- 可以选择：
    -- 1. 转接到语音信箱
    -- 2. 转接到其他坐席
    -- 3. 返回IVR菜单
    -- 4. 挂断
    
    session:sleep(2000)
    session:hangup("NORMAL_CLEARING")
else
    log("info", "转接成功完成")
    session:setVariable("transfer_result", "success")
end
