-- end_transcription.lua
-- 结束实时语音转写

local uuid = session:getVariable("uuid")
local call_id = session:getVariable("call_id")

freeswitch.consoleLog("info", "Ending transcription for call: " .. call_id)

-- 停止录音
session:execute("stop_record_session")

-- 停止转写进程
local cmd = "pkill -f 'real_time_transcription.py.*" .. call_id .. "'"
os.execute(cmd)

-- 清理录音文件
local record_path = "/tmp/call_" .. uuid .. ".wav"
os.remove(record_path)

-- 更新通话状态
session:setVariable("transcription_active", "false")

freeswitch.consoleLog("info", "Transcription ended for call: " .. call_id)