-- 通话状态管理脚本
-- 管理机器人外呼的各种状态转换和会话转移

local json = require("json")
local socket = require("socket")

-- 状态常量
local CALL_STATES = {
    ROBOT_ACTIVE = "robot_active",
    ROBOT_PAUSED = "robot_paused", 
    TRANSFERRING = "transferring",
    IN_CONFERENCE = "in_conference",
    AGENT_ONLY = "agent_only",
    ENDED = "ended"
}

-- 全局变量
local session = nil
local call_state = ""
local session_id = ""
local customer_number = ""
local agent_id = ""

-- 初始化状态管理器
function init_state_manager()
    session = freeswitch.Session()
    if session:ready() then
        session_id = session:getVariable("robot_session_id") or session:getVariable("uuid") or ""
        customer_number = session:getVariable("customer_number") or ""
        call_state = session:getVariable("call_state") or CALL_STATES.ROBOT_ACTIVE
        agent_id = session:getVariable("agent_id") or ""
        
        freeswitch.consoleLog("INFO", "State manager initialized for session: " .. session_id)
        return true
    end
    return false
end

-- 更新通话状态
function update_call_state(new_state, additional_data)
    if not session:ready() then return false end
    
    local old_state = call_state
    call_state = new_state
    
    -- 设置会话变量
    session:setVariable("call_state", new_state)
    session:setVariable("state_change_time", os.time())
    
    -- 记录状态变化日志
    local state_log = {
        session_id = session_id,
        customer_number = customer_number,
        old_state = old_state,
        new_state = new_state,
        timestamp = os.time(),
        additional_data = additional_data or {}
    }
    
    log_state_change(state_log)
    
    -- 根据新状态执行相应操作
    handle_state_change(old_state, new_state, additional_data)
    
    freeswitch.consoleLog("INFO", "State changed from " .. old_state .. " to " .. new_state .. " for session: " .. session_id)
    return true
end

-- 处理状态变化
function handle_state_change(old_state, new_state, data)
    if new_state == CALL_STATES.ROBOT_PAUSED then
        handle_robot_pause()
    elseif new_state == CALL_STATES.TRANSFERRING then
        handle_transfer_start(data)
    elseif new_state == CALL_STATES.IN_CONFERENCE then
        handle_conference_join(data)
    elseif new_state == CALL_STATES.AGENT_ONLY then
        handle_robot_exit()
    elseif new_state == CALL_STATES.ENDED then
        handle_call_end(data)
    end
end

-- 处理机器人暂停
function handle_robot_pause()
    -- 停止语音识别
    session:execute("detect_speech", "stop")
    
    -- 播放等待音乐
    session:execute("set", "hold_music=local_stream://moh")
    session:execute("endless_playback", "local_stream://moh")
    
    -- 通知数据库更新状态
    update_database_state(CALL_STATES.ROBOT_PAUSED)
end

-- 处理转接开始
function handle_transfer_start(data)
    agent_id = data.agent_id or ""
    
    -- 停止当前播放
    session:execute("break")
    
    -- 播放转接提示
    session:streamFile("/usr/share/freeswitch/sounds/en/us/callie/misc/8000/call_being_transferred.wav")
    
    -- 通知数据库
    update_database_state(CALL_STATES.TRANSFERRING, {agent_id = agent_id})
end

-- 处理会议加入
function handle_conference_join(data)
    local conference_id = data.conference_id or ("robot_call_" .. session_id)
    
    -- 设置会议参数
    session:setVariable("conference_id", conference_id)
    session:setVariable("conference_profile", "robot_call_profile")
    
    -- 客户端会议设置（初始静音，只能听）
    session:execute("set", "conference_member_flags=mute")
    
    -- 播放欢迎信息
    session:streamFile("/usr/share/freeswitch/sounds/en/us/callie/misc/8000/welcome.wav")
    
    -- 进入会议室
    session:execute("conference", conference_id .. "@robot_call_profile")
    
    -- 通知数据库
    update_database_state(CALL_STATES.IN_CONFERENCE, {
        conference_id = conference_id,
        agent_id = agent_id
    })
end

-- 处理机器人退出
function handle_robot_exit()
    -- 清理机器人相关资源
    session:execute("detect_speech", "stop")
    
    -- 取消客户静音，允许与坐席对话
    session:execute("conference", session:getVariable("conference_id") .. " unmute " .. session:getVariable("uuid"))
    
    -- 通知数据库
    update_database_state(CALL_STATES.AGENT_ONLY, {agent_id = agent_id})
end

-- 处理通话结束
function handle_call_end(data)
    local end_reason = data.end_reason or "normal"
    local call_duration = calculate_call_duration()
    
    -- 停止录音
    session:execute("stop_record_session")
    
    -- 清理资源
    cleanup_session_resources()
    
    -- 通知数据库
    update_database_state(CALL_STATES.ENDED, {
        end_reason = end_reason,
        call_duration = call_duration,
        end_time = os.time()
    })
    
    -- 释放坐席状态
    if agent_id ~= "" then
        release_agent(agent_id)
    end
end

-- 计算通话时长
function calculate_call_duration()
    local start_time = session:getVariable("start_epoch") or os.time()
    return os.time() - tonumber(start_time)
end

-- 清理会话资源
function cleanup_session_resources()
    -- 清理临时文件
    local temp_files = {
        "/tmp/tts_" .. session_id .. "_*.wav",
        "/tmp/asr_" .. session_id .. "_*.wav"
    }
    
    for _, pattern in ipairs(temp_files) do
        os.execute("rm -f " .. pattern)
    end
    
    -- 清理会话变量
    session:setVariable("robot_session_id", "")
    session:setVariable("call_state", "")
    session:setVariable("agent_id", "")
end

-- 记录状态变化日志
function log_state_change(state_log)
    local log_file = "/var/log/robot_calls/state_changes.log"
    local file = io.open(log_file, "a")
    if file then
        file:write(os.date("%Y-%m-%d %H:%M:%S") .. " " .. json.encode(state_log) .. "\n")
        file:close()
    end
end

-- 更新数据库状态
function update_database_state(new_state, additional_data)
    local update_data = {
        session_id = session_id,
        customer_number = customer_number,
        call_state = new_state,
        timestamp = os.time(),
        additional_data = additional_data or {}
    }
    
    -- 通过HTTP API更新数据库
    local http_client = require("socket.http")
    local ltn12 = require("ltn12")
    
    local response_body = {}
    local result, status = http_client.request{
        url = "http://localhost:8080/api/update_call_state",
        method = "POST",
        headers = {
            ["Content-Type"] = "application/json",
            ["Content-Length"] = string.len(json.encode(update_data))
        },
        source = ltn12.source.string(json.encode(update_data)),
        sink = ltn12.sink.table(response_body)
    }
    
    if status == 200 then
        freeswitch.consoleLog("INFO", "Database state updated successfully")
    else
        freeswitch.consoleLog("ERROR", "Failed to update database state: " .. (status or "unknown"))
    end
end

-- 释放坐席状态
function release_agent(agent_id)
    local release_data = {
        agent_id = agent_id,
        action = "release",
        session_id = session_id,
        timestamp = os.time()
    }
    
    -- 通过HTTP API释放坐席
    local http_client = require("socket.http")
    local ltn12 = require("ltn12")
    
    local response_body = {}
    local result, status = http_client.request{
        url = "http://localhost:8080/api/release_agent",
        method = "POST",
        headers = {
            ["Content-Type"] = "application/json",
            ["Content-Length"] = string.len(json.encode(release_data))
        },
        source = ltn12.source.string(json.encode(release_data)),
        sink = ltn12.sink.table(response_body)
    }
    
    freeswitch.consoleLog("INFO", "Agent " .. agent_id .. " released from session " .. session_id)
end

-- 获取当前状态信息
function get_state_info()
    return {
        session_id = session_id,
        customer_number = customer_number,
        call_state = call_state,
        agent_id = agent_id,
        timestamp = os.time()
    }
end

-- 检查状态转换是否有效
function is_valid_state_transition(from_state, to_state)
    local valid_transitions = {
        [CALL_STATES.ROBOT_ACTIVE] = {
            CALL_STATES.ROBOT_PAUSED,
            CALL_STATES.TRANSFERRING,
            CALL_STATES.ENDED
        },
        [CALL_STATES.ROBOT_PAUSED] = {
            CALL_STATES.ROBOT_ACTIVE,
            CALL_STATES.TRANSFERRING,
            CALL_STATES.ENDED
        },
        [CALL_STATES.TRANSFERRING] = {
            CALL_STATES.IN_CONFERENCE,
            CALL_STATES.ROBOT_ACTIVE,
            CALL_STATES.ENDED
        },
        [CALL_STATES.IN_CONFERENCE] = {
            CALL_STATES.AGENT_ONLY,
            CALL_STATES.ENDED
        },
        [CALL_STATES.AGENT_ONLY] = {
            CALL_STATES.ENDED
        }
    }
    
    local allowed_states = valid_transitions[from_state]
    if allowed_states then
        for _, allowed_state in ipairs(allowed_states) do
            if allowed_state == to_state then
                return true
            end
        end
    end
    
    return false
end

-- 强制状态转换（用于异常情况）
function force_state_transition(new_state, reason)
    freeswitch.consoleLog("WARNING", "Forcing state transition to " .. new_state .. " for reason: " .. reason)
    update_call_state(new_state, {forced = true, reason = reason})
end

-- 主处理函数
function main()
    if not init_state_manager() then
        freeswitch.consoleLog("ERROR", "Failed to initialize state manager")
        return
    end
    
    -- 根据当前状态执行相应处理
    local current_state = call_state
    freeswitch.consoleLog("INFO", "Current call state: " .. current_state)
    
    -- 这里可以根据需要添加状态恢复逻辑
    if current_state == CALL_STATES.ROBOT_PAUSED then
        handle_robot_pause()
    elseif current_state == CALL_STATES.IN_CONFERENCE then
        local conference_id = session:getVariable("conference_id")
        if conference_id then
            session:execute("conference", conference_id .. "@robot_call_profile")
        end
    end
end

-- 如果直接运行此脚本，执行主函数
if arg and arg[0] == "call_state_manager.lua" then
    main()
end