-- 人工接入模块
-- Human Intervention Module for Customer Service

local human_intervention = {}

-- 配置参数
local config = {
    -- API配置
    api_endpoint = "https://api.example.com/human-intervention",
    api_key = "your-api-key-here",
    
    -- 超时设置（秒）
    timeout = 30,
    
    -- 重试配置
    max_retries = 3,
    retry_delay = 2, -- 秒
    
    -- 队列配置
    queue_priority = {
        HIGH = 1,
        MEDIUM = 2,
        LOW = 3
    },
    
    -- 工作时间配置
    working_hours = {
        start_hour = 9,  -- 9:00 AM
        end_hour = 18,   -- 6:00 PM
        timezone = "Asia/Shanghai"
    }
}

-- 日志记录函数
local function log(level, message)
    local timestamp = os.date("%Y-%m-%d %H:%M:%S")
    print(string.format("[%s] [%s] %s", timestamp, level, message))
end

-- 检查是否在工作时间内
local function is_working_hours()
    local current_hour = tonumber(os.date("%H"))
    return current_hour >= config.working_hours.start_hour and 
           current_hour < config.working_hours.end_hour
end

-- 生成会话ID
local function generate_session_id()
    local timestamp = os.time()
    local random_num = math.random(1000, 9999)
    return string.format("SESSION_%d_%d", timestamp, random_num)
end

-- 验证请求参数
local function validate_request(params)
    if not params then
        return false, "参数不能为空"
    end
    
    if not params.user_id then
        return false, "用户ID不能为空"
    end
    
    if not params.reason then
        return false, "接入原因不能为空"
    end
    
    return true, nil
end

-- 创建人工接入请求
function human_intervention.create_request(params)
    local request = {
        session_id = generate_session_id(),
        user_id = params.user_id,
        user_name = params.user_name or "匿名用户",
        reason = params.reason,
        priority = params.priority or config.queue_priority.MEDIUM,
        context = params.context or {},
        timestamp = os.time(),
        status = "pending"
    }
    
    return request
end

-- 发送人工接入请求（模拟API调用）
function human_intervention.send_request(request)
    log("INFO", string.format("发送人工接入请求: %s", request.session_id))
    
    -- 这里应该是实际的HTTP请求
    -- 使用 luasocket 或其他HTTP库
    -- local http = require("socket.http")
    -- local response = http.request(config.api_endpoint, request_json)
    
    -- 模拟API响应
    local response = {
        success = true,
        queue_position = math.random(1, 10),
        estimated_wait_time = math.random(30, 300), -- 秒
        agent_id = nil,
        message = "您的请求已加入队列"
    }
    
    return response
end

-- 主要的呼出人工接入函数
function human_intervention.request_human_agent(params)
    -- 验证参数
    local valid, error_msg = validate_request(params)
    if not valid then
        log("ERROR", error_msg)
        return {
            success = false,
            error = error_msg
        }
    end
    
    -- 检查工作时间
    if not is_working_hours() then
        log("WARNING", "当前不在工作时间内")
        return {
            success = false,
            error = "当前不在客服工作时间内，请在工作时间 9:00-18:00 联系",
            working_hours = config.working_hours
        }
    end
    
    -- 创建请求
    local request = human_intervention.create_request(params)
    log("INFO", string.format("创建人工接入请求 - 用户: %s, 原因: %s", 
        request.user_id, request.reason))
    
    -- 发送请求
    local response = human_intervention.send_request(request)
    
    if response.success then
        log("SUCCESS", string.format("人工接入请求成功 - 会话ID: %s, 排队位置: %d", 
            request.session_id, response.queue_position))
        
        return {
            success = true,
            session_id = request.session_id,
            queue_position = response.queue_position,
            estimated_wait_time = response.estimated_wait_time,
            message = string.format("您已成功加入人工服务队列，当前排队位置: %d，预计等待时间: %d秒", 
                response.queue_position, response.estimated_wait_time)
        }
    else
        log("ERROR", "人工接入请求失败")
        return {
            success = false,
            error = response.message or "请求失败，请稍后重试"
        }
    end
end

-- 取消人工接入请求
function human_intervention.cancel_request(session_id)
    if not session_id then
        return {
            success = false,
            error = "会话ID不能为空"
        }
    end
    
    log("INFO", string.format("取消人工接入请求: %s", session_id))
    
    -- 这里应该调用实际的取消API
    return {
        success = true,
        message = "人工接入请求已取消"
    }
end

-- 检查队列状态
function human_intervention.check_queue_status(session_id)
    if not session_id then
        return {
            success = false,
            error = "会话ID不能为空"
        }
    end
    
    -- 模拟队列状态查询
    local status = {
        success = true,
        session_id = session_id,
        status = "waiting", -- waiting, connected, completed, cancelled
        queue_position = math.random(1, 5),
        estimated_wait_time = math.random(10, 180),
        agent_info = nil
    }
    
    return status
end

-- 获取可用的人工客服列表
function human_intervention.get_available_agents()
    -- 模拟获取可用客服
    local agents = {
        {
            id = "agent_001",
            name = "客服小王",
            status = "available",
            current_load = 2,
            max_load = 5,
            specialties = {"技术支持", "账户问题"}
        },
        {
            id = "agent_002",
            name = "客服小李",
            status = "busy",
            current_load = 5,
            max_load = 5,
            specialties = {"订单查询", "售后服务"}
        }
    }
    
    return {
        success = true,
        agents = agents,
        total_available = 1
    }
end

-- 发送消息给人工客服
function human_intervention.send_message(session_id, message)
    if not session_id or not message then
        return {
            success = false,
            error = "会话ID和消息内容不能为空"
        }
    end
    
    log("INFO", string.format("发送消息 - 会话: %s, 内容: %s", session_id, message))
    
    return {
        success = true,
        message_id = string.format("MSG_%d", os.time()),
        timestamp = os.time()
    }
end

-- 结束人工服务会话
function human_intervention.end_session(session_id, rating)
    if not session_id then
        return {
            success = false,
            error = "会话ID不能为空"
        }
    end
    
    log("INFO", string.format("结束会话: %s, 评分: %s", session_id, rating or "无"))
    
    return {
        success = true,
        message = "感谢您的使用，会话已结束",
        rating = rating
    }
end

-- 设置配置
function human_intervention.set_config(new_config)
    for key, value in pairs(new_config) do
        if config[key] ~= nil then
            config[key] = value
            log("INFO", string.format("配置更新: %s = %s", key, tostring(value)))
        end
    end
end

-- 获取配置
function human_intervention.get_config()
    return config
end

return human_intervention