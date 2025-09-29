-- 设置三方会议脚本
-- 将客户和坐席加入同一个会议室

local session = freeswitch.Session()

if session:ready() then
    local robot_session_id = session:getVariable("robot_session_id") or ""
    local customer_number = session:getVariable("customer_number") or ""
    local agent_id = session:getVariable("agent_id") or "default"
    
    -- 生成会议室ID
    local conference_id = "robot_call_" .. robot_session_id
    
    freeswitch.consoleLog("INFO", "Setting up conference: " .. conference_id)
    
    -- 设置会议变量
    session:setVariable("conference_id", conference_id)
    session:setVariable("call_state", "in_conference")
    
    -- 客户进入会议室（静音状态，只能听）
    session:execute("set", "conference_member_flags=mute")
    
    -- 播放转接成功提示
    session:streamFile("/usr/share/freeswitch/sounds/en/us/callie/misc/8000/call_being_transferred.wav")
    
    -- 进入会议室
    session:execute("conference", conference_id .. "@robot_call_profile")
    
    freeswitch.consoleLog("INFO", "Customer joined conference: " .. conference_id)
    
else
    freeswitch.consoleLog("ERROR", "Session not ready in setup_conference.lua")
end