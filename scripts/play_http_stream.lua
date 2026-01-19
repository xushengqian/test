-- play_http_stream.lua
-- FreeSWITCH Lua 脚本示例：播放 HTTP 音频流
-- 该脚本可以在 Dialplan 中通过 'lua play_http_stream.lua' 调用

-- 确保会话已接通
if session:ready() then
    session:answer()
    
    -- 设置日志前缀
    freeswitch.consoleLog("INFO", "[StreamDemo] 开始处理呼叫\n")

    -- 设置流媒体缓冲 (重要：防止网络卡顿)
    -- shout_buffer_seconds 控制预缓冲的秒数
    session:setVariable("shout_buffer_seconds", "5")

    -- 定义音频流地址
    -- 注意使用 shout:// 协议头来确保使用 mod_shout 进行流式传输
    local stream_url = "shout://icecast.stream.example.com/radio.mp3"

    freeswitch.consoleLog("INFO", "[StreamDemo] 正在播放流: " .. stream_url .. "\n")

    -- 播放音频流
    -- streamFile 是 Lua API 中用于播放音频的方法
    session:streamFile(stream_url)

    -- 如果需要捕获 DTMF (例如用户按键停止播放)，可以使用:
    -- session:streamFile(stream_url, function(type, digits, arg)
    --     if (digits == "*") then
    --         return "break"
    --     end
    -- end)

    freeswitch.consoleLog("INFO", "[StreamDemo] 播放结束\n")
    
    -- 挂断通话
    session:hangup()
end
