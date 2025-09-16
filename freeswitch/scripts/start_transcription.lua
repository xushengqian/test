-- start_transcription.lua
-- 开始实时语音转写

local uuid = session:getVariable("uuid")
local call_id = session:getVariable("call_id")
local phone_number = session:getVariable("phone_number")
local agent_id = session:getVariable("agent_id")

-- 记录通话开始
freeswitch.consoleLog("info", "Starting transcription for call: " .. call_id)

-- 设置录音参数
session:setVariable("record_sample_rate", "16000")
session:setVariable("record_channels", "1")

-- 启动录音
local record_path = "/tmp/call_" .. uuid .. ".wav"
session:execute("record_session", record_path)

-- 启动实时转写服务
local transcription_url = "ws://app:8000/ws/" .. call_id
local transcription_script = "/usr/local/freeswitch/scripts/real_time_transcription.py"

-- 在后台启动转写进程
local cmd = "python3 " .. transcription_script .. " " .. record_path .. " " .. transcription_url .. " &"
os.execute(cmd)

-- 设置通话变量
session:setVariable("transcription_active", "true")
session:setVariable("transcription_url", transcription_url)

freeswitch.consoleLog("info", "Transcription started for call: " .. call_id)