-- FreeSWITCH 机器人外呼脚本
-- 处理呼叫流程、意图识别和转接人工

-- 日志函数
local function log(level, message)
    freeswitch.consoleLog(level, "[ROBOT_OUTBOUND] " .. message .. "\n")
end

-- 意图识别函数
local function detect_intent(session, audio_file)
    log("INFO", "开始意图识别，音频文件: " .. (audio_file or "none"))
    
    if not audio_file or audio_file == "" then
        log("WARNING", "音频文件为空，返回默认意图")
        return "continue_robot"
    end
    
    -- 调用 Python 意图识别服务
    local python_script = "/workspace/scripts/intent_detection.py"
    local command = "python3 " .. python_script .. " " .. audio_file .. " 2>&1"
    
    log("INFO", "执行命令: " .. command)
    
    local handle = io.popen(command)
    if not handle then
        log("ERROR", "无法执行 Python 脚本")
        return "continue_robot"
    end
    
    local result_json = handle:read("*a")
    handle:close()
    
    log("INFO", "意图识别结果: " .. result_json)
    
    -- 解析 JSON 结果
    -- 简单解析（实际项目中应使用 JSON 库）
    local intent = "continue_robot"
    local confidence = 0.0
    
    if result_json and result_json ~= "" then
        -- 查找 intent 字段
        local intent_match = string.match(result_json, '"intent"%s*:%s*"([^"]+)"')
        if intent_match then
            intent = intent_match
        end
        
        -- 查找 confidence 字段
        local conf_match = string.match(result_json, '"confidence"%s*:%s*([%d%.]+)')
        if conf_match then
            confidence = tonumber(conf_match) or 0.0
        end
    end
    
    log("INFO", "识别意图: " .. intent .. ", 置信度: " .. confidence)
    
    -- 如果置信度足够高且是转人工意图，返回转人工
    if intent == "transfer_to_agent" and confidence > 0.7 then
        return "transfer_to_agent"
    end
    
    return "continue_robot"
end

-- 处理呼叫
function handle_call(session)
    log("INFO", "开始处理机器人外呼...")
    
    local caller_id = session:getVariable("caller_id_number")
    local destination = session:getVariable("destination_number")
    
    log("INFO", "主叫号码: " .. (caller_id or "unknown"))
    log("INFO", "被叫号码: " .. (destination or "unknown"))
    
    -- 播放欢迎语
    session:streamFile("/usr/local/freeswitch/sounds/zh/cn/robot_welcome.wav")
    
    -- 机器人对话循环
    local max_turns = 10  -- 最大对话轮数
    local transfer_flag = false
    
    for turn = 1, max_turns do
        log("INFO", "对话轮次: " .. turn)
        
        -- 录音获取用户语音
        local record_file = "/tmp/robot_record_" .. os.time() .. "_" .. math.random(1000, 9999) .. ".wav"
        
        -- 使用 detect_speech 或 record_session 录音
        -- 方法1: 使用 detect_speech（推荐，可以检测语音活动）
        session:setVariable("RECORD_STEREO", "true")
        session:setVariable("RECORD_READ_ONLY", "false")
        
        -- 等待用户说话，最多等待5秒
        local timeout = 5000  -- 5秒超时
        local start_time = os.time()
        
        -- 开始录音
        session:execute("record_session", record_file)
        
        -- 设置输入回调处理 DTMF
        session:setInputCallback("on_dtmf")
        
        -- 等待语音输入（使用 detect_speech 或简单的 sleep）
        -- 实际应用中可以使用 detect_speech 模块检测语音活动
        session:execute("sleep", "3000")  -- 等待3秒或直到检测到语音
        
        -- 停止录音
        session:execute("stop_record_session", record_file)
        
        -- 检查录音文件是否存在
        local file = io.open(record_file, "r")
        if not file then
            log("WARNING", "录音文件不存在: " .. record_file)
            -- 继续下一轮对话
            goto continue_loop
        end
        file:close()
        
        -- 读取录音文件进行意图识别
        -- 注意：实际实现中需要调用语音识别服务
        local intent = detect_intent(session, record_file)
        
        log("INFO", "识别到的意图: " .. intent)
        
        -- 清理临时录音文件（可选）
        os.remove(record_file)
        
        -- 判断是否需要转人工
        if intent == "transfer_to_agent" then
            log("INFO", "检测到转人工意图，准备转接...")
            transfer_flag = true
            
            -- 设置转接目标
            -- 这里可以从数据库或配置文件获取空闲的人工座席
            local agent_number = get_available_agent(session)
            
            if agent_number then
                session:setVariable("transfer_destination", "agent_" .. agent_number)
                session:setVariable("transfer_to_agent", "true")
                log("INFO", "转接到座席: " .. agent_number)
                
                -- 播放转接提示音
                session:streamFile("/usr/local/freeswitch/sounds/zh/cn/transferring.wav")
                break
            else
                log("WARNING", "没有可用座席，继续机器人对话")
                session:streamFile("/usr/local/freeswitch/sounds/zh/cn/no_agent_available.wav")
            end
        else
            -- 继续机器人对话
            -- 根据意图生成回复
            local response = generate_robot_response(intent)
            -- 使用 TTS 播放回复
            session:execute("tts", "flite|kal|" .. response)
        end
        
        ::continue_loop::
        
        -- 检查用户是否挂断
        if session:ready() == false then
            log("INFO", "用户挂断")
            break
        end
    end
    
    -- 如果没有转接，播放结束语
    if not transfer_flag then
        session:streamFile("/usr/local/freeswitch/sounds/zh/cn/robot_goodbye.wav")
    end
    
    log("INFO", "呼叫处理完成")
end

-- 获取可用座席
function get_available_agent(session)
    log("INFO", "查询可用座席...")
    
    -- 调用 Python 座席管理服务
    local python_script = "/workspace/scripts/agent_manager.py"
    local command = "python3 " .. python_script .. " get_agent 2>&1"
    
    local handle = io.popen(command)
    if not handle then
        log("ERROR", "无法执行座席管理脚本")
        return nil
    end
    
    local result_json = handle:read("*a")
    handle:close()
    
    log("INFO", "座席查询结果: " .. result_json)
    
    -- 解析 JSON 结果
    local agent_id = nil
    if result_json and result_json ~= "" then
        local agent_match = string.match(result_json, '"agent_id"%s*:%s*"?([%d]+)"?')
        if agent_match and agent_match ~= "null" then
            agent_id = agent_match
        end
    end
    
    if agent_id then
        log("INFO", "分配到座席: " .. agent_id)
    else
        log("WARNING", "没有可用座席")
    end
    
    return agent_id
end

-- 生成机器人回复
function generate_robot_response(intent)
    -- 根据意图生成回复内容
    local responses = {
        ["greeting"] = "您好，有什么可以帮您的吗？",
        ["question"] = "让我为您查询一下相关信息。",
        ["transfer_to_agent"] = "正在为您转接人工服务，请稍候。",
        ["default"] = "请再说一遍，我没有听清楚。"
    }
    
    return responses[intent] or responses["default"]
end

-- DTMF 回调函数
function on_dtmf(session, type, data)
    if type == "dtmf" then
        log("INFO", "收到 DTMF: " .. data.digit)
        
        -- 如果用户按 0，直接转人工
        if data.digit == "0" then
            log("INFO", "用户按键 0，请求转人工")
            session:setVariable("transfer_destination", "agent_1001")
            session:setVariable("transfer_to_agent", "true")
            return "break"
        end
    end
    return ""
end
