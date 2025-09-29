-- FreeSWITCH Lua 脚本 - 机器人外呼处理
-- 处理机器人语音交互和坐席接入

local call_uuid = argv[1]

-- 初始化会话
session:answer()
session:setAutoHangup(false)

-- TTS配置
local tts_engine = "flite"  -- 可替换为其他TTS引擎如：baidu_tts, ali_tts等
local tts_voice = "kal"

-- ASR配置  
local asr_engine = "pocketsphinx"  -- 可替换为其他ASR引擎如：baidu_asr, ali_asr等

-- 会议室名称
local conference_name = "conf_" .. call_uuid

-- 机器人状态
local robot_active = true
local agent_joined = false

-- 播放欢迎语
function play_welcome()
    session:speak(tts_engine .. "|" .. tts_voice, "您好，我是智能客服助手，很高兴为您服务。请问有什么可以帮助您的吗？")
end

-- 处理客户语音
function process_customer_speech()
    -- 启动语音识别
    session:execute("detect_speech", "pocketsphinx default default")
    
    -- 获取识别结果
    local speech_result = session:getVariable("detect_speech_result")
    
    if speech_result then
        freeswitch.consoleLog("info", "客户说话内容: " .. speech_result .. "\n")
        
        -- 这里可以接入NLP处理逻辑
        return analyze_intent(speech_result)
    end
    
    return nil
end

-- 意图分析（简化示例）
function analyze_intent(text)
    local intent = {}
    
    -- 检测关键词
    if string.find(text, "人工") or string.find(text, "转接") or string.find(text, "客服") then
        intent.type = "transfer_agent"
        intent.message = "好的，正在为您转接人工客服，请稍等..."
    elseif string.find(text, "查询") or string.find(text, "余额") then
        intent.type = "query"
        intent.message = "正在为您查询相关信息..."
    elseif string.find(text, "投诉") or string.find(text, "建议") then
        intent.type = "complaint"
        intent.message = "非常抱歉给您带来不便，我会记录您的反馈..."
    else
        intent.type = "general"
        intent.message = "我理解了您的需求，让我来为您处理..."
    end
    
    return intent
end

-- 转接人工坐席
function transfer_to_agent()
    freeswitch.consoleLog("info", "开始转接人工坐席...\n")
    
    -- 通知坐席系统
    notify_agent_system(call_uuid)
    
    -- 将客户转入会议室
    session:execute("transfer", "customer_conf_" .. call_uuid .. " XML robot_outbound")
    
    agent_joined = true
    robot_active = false
end

-- 通知坐席系统（通过Event Socket或WebSocket）
function notify_agent_system(call_id)
    local event = freeswitch.Event("CUSTOM", "robot::agent_needed")
    event:addHeader("Call-UUID", call_id)
    event:addHeader("Customer-Number", session:getVariable("customer_number"))
    event:addHeader("Conference-Name", conference_name)
    event:addHeader("Timestamp", os.date("%Y-%m-%d %H:%M:%S"))
    event:fire()
end

-- 主循环
function main_loop()
    play_welcome()
    
    while session:ready() and robot_active do
        -- 等待客户说话
        session:sleep(100)
        
        -- 处理语音输入
        local intent = process_customer_speech()
        
        if intent then
            -- 播放回复
            session:speak(tts_engine .. "|" .. tts_voice, intent.message)
            
            -- 根据意图执行动作
            if intent.type == "transfer_agent" then
                transfer_to_agent()
                break
            end
        end
        
        -- 检查是否有坐席主动接入
        local agent_request = session:getVariable("agent_join_request")
        if agent_request == "true" then
            session:speak(tts_engine .. "|" .. tts_voice, "人工客服正在接入，请稍候...")
            transfer_to_agent()
            break
        end
        
        -- 静音检测
        local silence_count = tonumber(session:getVariable("silence_count")) or 0
        if silence_count > 3 then
            session:speak(tts_engine .. "|" .. tts_voice, "您还在吗？如果需要帮助请随时告诉我。")
            session:setVariable("silence_count", "0")
        end
    end
end

-- 执行主程序
if session:ready() then
    main_loop()
end