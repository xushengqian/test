-- 暂停机器人脚本
-- 当人工坐席接入时暂停机器人对话

local session = freeswitch.Session()

if session:ready() then
    local robot_session_id = session:getVariable("robot_session_id") or ""
    local customer_number = session:getVariable("customer_number") or ""
    
    freeswitch.consoleLog("INFO", "Pausing robot for session: " .. robot_session_id)
    
    -- 停止语音识别
    session:execute("detect_speech", "stop")
    
    -- 设置暂停状态
    session:setVariable("call_state", "robot_paused")
    
    -- 通知客户等待
    session:streamFile("/usr/share/freeswitch/sounds/en/us/callie/misc/8000/please_hold_while_party_contacted.wav")
    
    freeswitch.consoleLog("INFO", "Robot paused successfully for customer: " .. customer_number)
else
    freeswitch.consoleLog("ERROR", "Session not ready in pause_robot.lua")
end