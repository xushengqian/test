-- FreeSWITCH MRCP 实时语音流处理脚本
-- 功能：获取实时语音流并通过 MRCP 进行处理

-- 导入必要的模块
local freeswitch = require "freeswitch"
local log = freeswitch.consoleLog

-- 配置参数
local MRCP_PROFILE = "uniMRCP-server"
local SAMPLE_RATE = 8000
local CHUNK_SIZE = 160  -- 20ms @ 8kHz
local BUFFER_SIZE = 3200  -- 400ms buffer

-- 全局变量
local audio_buffer = {}
local stream_active = true

-- 日志函数
local function log_info(msg)
    log("INFO", "[MRCP Stream] " .. msg)
end

local function log_error(msg)
    log("ERR", "[MRCP Stream] " .. msg)
end

-- 初始化 MRCP 会话
local function init_mrcp_session(session)
    log_info("初始化 MRCP 会话...")
    
    -- 设置 RTP 参数
    session:setVariable("rtp_secure_media", "false")
    session:setVariable("rtp_ip", "auto")
    
    -- 启动 MRCP 连接
    local result = session:execute("mrcp", "asr:" .. MRCP_PROFILE)
    if result then
        log_info("MRCP 会话初始化成功")
        return true
    else
        log_error("MRCP 会话初始化失败")
        return false
    end
end

-- 处理音频数据块
local function process_audio_chunk(session, audio_data)
    if not audio_data or #audio_data == 0 then
        return
    end
    
    -- 将音频数据添加到缓冲区
    table.insert(audio_buffer, audio_data)
    
    -- 当缓冲区达到一定大小时，发送到 MRCP
    if #audio_buffer >= (BUFFER_SIZE / CHUNK_SIZE) then
        local combined_audio = table.concat(audio_buffer)
        audio_buffer = {}
        
        -- 通过 MRCP 发送音频数据
        log_info("发送音频数据到 MRCP，大小: " .. #combined_audio .. " 字节")
        
        -- 这里可以调用 MRCP API 发送音频流
        -- 实际实现取决于使用的 MRCP 客户端库
    end
end

-- 实时语音流处理主函数
local function handle_realtime_stream(session)
    log_info("开始处理实时语音流...")
    
    -- 初始化 MRCP
    if not init_mrcp_session(session) then
        return
    end
    
    -- 设置事件监听
    session:setAutoHangup(false)
    
    -- 启动语音识别
    session:execute("speech_recognition", MRCP_PROFILE .. " default")
    
    -- 实时音频流处理循环
    local frame_count = 0
    while stream_active do
        -- 从 RTP 流中读取音频数据
        local audio_data = session:readFrame()
        
        if audio_data then
            frame_count = frame_count + 1
            
            -- 处理音频数据
            process_audio_chunk(session, audio_data)
            
            -- 每 100 帧记录一次
            if frame_count % 100 == 0 then
                log_info("已处理 " .. frame_count .. " 帧音频数据")
            end
        else
            -- 没有数据，短暂休眠
            freeswitch.msleep(10)
        end
        
        -- 检查会话是否仍然活跃
        if not session:ready() then
            log_info("会话已结束")
            stream_active = false
            break
        end
    end
    
    log_info("实时语音流处理结束，共处理 " .. frame_count .. " 帧")
end

-- 主入口
local function main()
    local session = freeswitch.Session
    if not session then
        log_error("无法获取 FreeSWITCH 会话")
        return
    end
    
    -- 处理实时语音流
    handle_realtime_stream(session)
end

-- 运行主函数
main()
