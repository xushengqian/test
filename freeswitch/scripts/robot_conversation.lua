-- 机器人对话处理脚本
local json = require("json")
local http = require("socket.http")

-- 获取会话信息
local session = session
local customer_phone = session:getVariable("customer_phone")
local call_uuid = session:get_uuid()

-- API服务器配置
local api_server = session:getVariable("api_server") or "http://localhost:8000"
local tts_server = session:getVariable("tts_server") or "http://localhost:8001"
local asr_server = session:getVariable("asr_server") or "http://localhost:8002"

-- 日志函数
local function log(level, message)
    freeswitch.consoleLog(level, "[Robot] " .. message .. "\n")
end

-- HTTP请求函数
local function http_request(url, data)
    local response_body = {}
    local result, status = http.request{
        url = url,
        method = "POST",
        headers = {
            ["Content-Type"] = "application/json",
            ["Content-Length"] = string.len(data)
        },
        source = ltn12.source.string(data),
        sink = ltn12.sink.table(response_body)
    }
    
    if status == 200 then
        return json.decode(table.concat(response_body))
    else
        log("ERROR", "HTTP request failed: " .. tostring(status))
        return nil
    end
end

-- 语音识别函数
local function speech_recognition(audio_file)
    local data = json.encode({
        audio_file = audio_file,
        language = "zh-CN"
    })
    
    local response = http_request(asr_server .. "/api/asr", data)
    if response and response.text then
        return response.text
    end
    return nil
end

-- 语音合成函数
local function text_to_speech(text)
    local data = json.encode({
        text = text,
        voice = "zh-CN-XiaoxiaoNeural",
        speed = 1.0
    })
    
    local response = http_request(tts_server .. "/api/tts", data)
    if response and response.audio_file then
        return response.audio_file
    end
    return nil
end

-- 获取机器人回复
local function get_robot_response(user_input, context)
    local data = json.encode({
        user_input = user_input,
        customer_phone = customer_phone,
        call_uuid = call_uuid,
        context = context or {}
    })
    
    local response = http_request(api_server .. "/api/robot/chat", data)
    return response
end

-- 检查是否需要转人工
local function should_transfer_to_human(response)
    if response and response.action then
        return response.action == "transfer_human"
    end
    return false
end

-- 主对话循环
local function conversation_loop()
    local context = {}
    local conversation_count = 0
    local max_conversations = 10
    
    log("INFO", "Starting robot conversation with " .. customer_phone)
    
    -- 通知后端开始对话
    http_request(api_server .. "/api/call/start", json.encode({
        customer_phone = customer_phone,
        call_uuid = call_uuid,
        start_time = os.time()
    }))
    
    while session:ready() and conversation_count < max_conversations do
        conversation_count = conversation_count + 1
        
        -- 录制用户语音
        local temp_file = "/tmp/user_input_" .. call_uuid .. "_" .. conversation_count .. ".wav"
        session:execute("record", temp_file .. " 5 200 3")
        
        -- 检查录音是否成功
        if not session:ready() then
            break
        end
        
        -- 语音识别
        local user_input = speech_recognition(temp_file)
        if not user_input or user_input == "" then
            -- 没有识别到内容，播放提示
            session:speak("tts_commandline|抱歉，我没有听清楚，请您再说一遍")
            goto continue
        end
        
        log("INFO", "User input: " .. user_input)
        
        -- 获取机器人回复
        local response = get_robot_response(user_input, context)
        if not response then
            session:speak("tts_commandline|抱歉，系统出现问题，正在为您转接人工客服")
            session:execute("transfer", "transfer_human_" .. customer_phone)
            break
        end
        
        -- 更新上下文
        if response.context then
            context = response.context
        end
        
        -- 检查是否需要转人工
        if should_transfer_to_human(response) then
            log("INFO", "Transferring to human agent")
            session:setVariable("transfer_reason", response.transfer_reason or "user_request")
            session:speak("tts_commandline|" .. (response.message or "正在为您转接人工客服，请稍等"))
            session:execute("transfer", "transfer_human_" .. customer_phone)
            break
        end
        
        -- 播放机器人回复
        if response.message then
            session:speak("tts_commandline|" .. response.message)
        end
        
        -- 检查对话是否结束
        if response.action == "end_call" then
            log("INFO", "Conversation ended by robot")
            break
        end
        
        ::continue::
        
        -- 清理临时文件
        os.remove(temp_file)
    end
    
    -- 通知后端对话结束
    http_request(api_server .. "/api/call/end", json.encode({
        customer_phone = customer_phone,
        call_uuid = call_uuid,
        end_time = os.time(),
        conversation_count = conversation_count
    }))
    
    log("INFO", "Robot conversation ended")
end

-- 错误处理
local function safe_conversation()
    local success, error = pcall(conversation_loop)
    if not success then
        log("ERROR", "Conversation error: " .. tostring(error))
        session:speak("tts_commandline|抱歉，系统出现问题，通话即将结束")
    end
end

-- 主程序入口
if session:ready() then
    safe_conversation()
else
    log("ERROR", "Session not ready")
end