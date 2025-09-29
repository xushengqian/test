-- 机器人外呼处理脚本
-- 处理语音识别、对话逻辑和人工转接

local json = require("json")
local socket = require("socket")

-- 全局变量
local session = nil
local customer_number = ""
local robot_session_id = ""
local call_state = "robot_active"
local conversation_log = {}

-- 初始化会话
function init_session()
    session = freeswitch.Session()
    if session:ready() then
        customer_number = session:getVariable("customer_number") or ""
        robot_session_id = session:getVariable("robot_session_id") or ""
        call_state = session:getVariable("call_state") or "robot_active"
        
        freeswitch.consoleLog("INFO", "Robot call initialized for customer: " .. customer_number)
        return true
    end
    return false
end

-- 语音识别配置
function setup_asr()
    session:execute("detect_speech", "pocketsphinx default default")
    session:execute("detect_speech", "grammar default {builtin:grammar/boolean}")
    session:execute("detect_speech", "grammar transfer {builtin:grammar/transfer}")
    session:execute("detect_speech", "start")
end

-- 文本转语音播放
function speak_text(text)
    if not session:ready() then return false end
    
    -- 记录对话日志
    table.insert(conversation_log, {
        timestamp = os.time(),
        speaker = "robot",
        text = text
    })
    
    -- 使用TTS播放
    local tts_file = "/tmp/tts_" .. robot_session_id .. "_" .. os.time() .. ".wav"
    
    -- 这里可以集成各种TTS引擎，如百度、阿里云等
    -- 示例使用espeak（需要安装）
    os.execute("espeak -v zh -s 150 -w " .. tts_file .. " '" .. text .. "'")
    
    session:streamFile(tts_file)
    os.remove(tts_file)
    
    return true
end

-- 语音识别处理
function process_speech_input()
    local speech_result = ""
    local dtmf_digit = ""
    
    -- 等待语音输入或按键
    while session:ready() and call_state == "robot_active" do
        session:sleep(100)
        
        -- 检查语音识别结果
        speech_result = session:getVariable("detect_speech_result")
        if speech_result and speech_result ~= "" then
            freeswitch.consoleLog("INFO", "Speech recognized: " .. speech_result)
            
            -- 记录客户语音
            table.insert(conversation_log, {
                timestamp = os.time(),
                speaker = "customer",
                text = speech_result
            })
            
            -- 清除识别结果
            session:setVariable("detect_speech_result", "")
            return speech_result
        end
        
        -- 检查按键输入
        dtmf_digit = session:getDigits(1, "", 100)
        if dtmf_digit and dtmf_digit ~= "" then
            return "dtmf:" .. dtmf_digit
        end
        
        -- 检查是否需要转人工
        local transfer_request = session:getVariable("transfer_to_human")
        if transfer_request == "true" then
            return "transfer_human"
        end
    end
    
    return ""
end

-- 对话逻辑处理
function process_conversation(user_input)
    local response = ""
    
    -- 简单的对话逻辑（实际项目中可以集成NLP服务）
    if string.find(user_input, "人工") or string.find(user_input, "转接") or string.find(user_input, "客服") then
        return "transfer_human"
    elseif string.find(user_input, "你好") or string.find(user_input, "hello") then
        response = "您好！我是智能客服机器人，很高兴为您服务。请问有什么可以帮助您的吗？"
    elseif string.find(user_input, "产品") or string.find(user_input, "服务") then
        response = "我们有多种优质产品和服务，如果您需要详细了解，我可以为您转接专业的人工客服。"
    elseif string.find(user_input, "价格") or string.find(user_input, "费用") then
        response = "关于价格信息，我为您转接专业顾问为您详细介绍，请稍等。"
        return "transfer_human"
    else
        response = "抱歉，我没有完全理解您的意思。您可以说'转人工'来获得更专业的服务。"
    end
    
    return response
end

-- 转接人工处理
function transfer_to_human()
    freeswitch.consoleLog("INFO", "Transferring to human agent for customer: " .. customer_number)
    
    -- 设置转接状态
    session:setVariable("call_state", "transferring")
    call_state = "transferring"
    
    -- 通知客户
    speak_text("正在为您转接人工客服，请稍等...")
    
    -- 保存对话记录
    save_conversation_log()
    
    -- 发送转接请求到坐席系统
    notify_agent_system()
    
    -- 执行转接
    session:transfer("human_takeover", "XML", "outbound_robot")
end

-- 保存对话记录
function save_conversation_log()
    local log_file = "/var/log/robot_calls/" .. robot_session_id .. "_conversation.json"
    local file = io.open(log_file, "w")
    if file then
        file:write(json.encode({
            session_id = robot_session_id,
            customer_number = customer_number,
            start_time = session:getVariable("start_epoch") or os.time(),
            conversation = conversation_log
        }))
        file:close()
    end
end

-- 通知坐席系统
function notify_agent_system()
    -- 这里可以通过HTTP API、消息队列等方式通知坐席系统
    local notification = {
        type = "transfer_request",
        session_id = robot_session_id,
        customer_number = customer_number,
        conversation_summary = get_conversation_summary(),
        timestamp = os.time()
    }
    
    -- 示例：通过HTTP POST发送通知
    local http_client = require("socket.http")
    local ltn12 = require("ltn12")
    
    local response_body = {}
    local result, status = http_client.request{
        url = "http://localhost:8080/api/transfer_request",
        method = "POST",
        headers = {
            ["Content-Type"] = "application/json",
            ["Content-Length"] = string.len(json.encode(notification))
        },
        source = ltn12.source.string(json.encode(notification)),
        sink = ltn12.sink.table(response_body)
    }
    
    freeswitch.consoleLog("INFO", "Agent notification sent, status: " .. (status or "unknown"))
end

-- 获取对话摘要
function get_conversation_summary()
    local summary = ""
    for i, entry in ipairs(conversation_log) do
        if i <= 3 then  -- 只取前3条对话
            summary = summary .. entry.speaker .. ": " .. entry.text .. "; "
        end
    end
    return summary
end

-- 主处理流程
function main()
    if not init_session() then
        freeswitch.consoleLog("ERROR", "Failed to initialize robot session")
        return
    end
    
    -- 设置语音识别
    setup_asr()
    
    -- 开场白
    speak_text("您好！我是智能客服机器人，很高兴为您服务。请问有什么可以帮助您的吗？")
    
    -- 主对话循环
    while session:ready() and call_state == "robot_active" do
        local user_input = process_speech_input()
        
        if user_input == "" then
            -- 超时或无输入
            speak_text("您还在吗？如果需要帮助，请说话或按任意键。")
        elseif user_input == "transfer_human" then
            -- 转人工
            transfer_to_human()
            break
        elseif string.sub(user_input, 1, 5) == "dtmf:" then
            -- 按键处理
            local digit = string.sub(user_input, 6)
            if digit == "0" then
                transfer_to_human()
                break
            else
                speak_text("您按了" .. digit .. "键。按0键可转人工客服。")
            end
        else
            -- 处理对话
            local response = process_conversation(user_input)
            if response == "transfer_human" then
                transfer_to_human()
                break
            else
                speak_text(response)
            end
        end
    end
    
    -- 清理资源
    session:execute("detect_speech", "stop")
    save_conversation_log()
end

-- 执行主流程
main()