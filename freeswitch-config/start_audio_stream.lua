--[[
  Lua脚本: 启动音频流
  
  该脚本用于在通话中启动实时音频流
  
  使用方法:
  在dialplan中调用: <action application="lua" data="start_audio_stream.lua"/>
  
  脚本位置: /usr/local/freeswitch/scripts/
]]

-- 获取通道变量
local uuid = session:getVariable("uuid")
local enable_audio_stream = session:getVariable("enable_audio_stream")
local audio_stream_url = session:getVariable("audio_stream_url")
local direction = session:getVariable("audio_stream_direction") or "both"

-- 日志输出
freeswitch.consoleLog("INFO", "Audio Stream Script - UUID: " .. uuid .. "\n")

-- 检查是否启用音频流
if enable_audio_stream ~= "true" then
    freeswitch.consoleLog("INFO", "Audio stream not enabled for this call\n")
    return
end

-- 设置默认URL
if audio_stream_url == nil or audio_stream_url == "" then
    audio_stream_url = "ws://127.0.0.1:8080/ws/audio-stream/" .. uuid
end

-- 设置流方向标志
local flags = ""
if direction == "read" then
    flags = "-r"
elseif direction == "write" then
    flags = "-w"
else
    flags = "-b"  -- both
end

-- 构建API命令
local api_cmd = "uuid_audio_stream " .. uuid .. " start " .. audio_stream_url .. " " .. flags

freeswitch.consoleLog("INFO", "Executing: " .. api_cmd .. "\n")

-- 执行API命令
local api = freeswitch.API()
local result = api:execute("uuid_audio_stream", uuid .. " start " .. audio_stream_url .. " " .. flags)

freeswitch.consoleLog("INFO", "Audio stream result: " .. (result or "nil") .. "\n")

-- 设置挂断时停止音频流
session:setVariable("api_hangup_hook", "uuid_audio_stream " .. uuid .. " stop")
