-- 人工接入使用示例
-- Example Usage of Human Intervention Module

-- 加载模块
local human_intervention = require("human_intervention")
local config = require("config")

-- 设置随机种子
math.randomseed(os.time())

-- 分隔线函数
local function print_separator()
    print(string.rep("-", 50))
end

-- 示例1: 基本的人工接入请求
local function example_basic_request()
    print("\n示例1: 基本的人工接入请求")
    print_separator()
    
    local params = {
        user_id = "user_12345",
        user_name = "张三",
        reason = "无法解决账户登录问题",
        priority = 2,  -- HIGH priority
        context = {
            previous_messages = 5,
            issue_type = "账户问题",
            attempted_solutions = {"重置密码", "清除缓存"}
        }
    }
    
    local result = human_intervention.request_human_agent(params)
    
    if result.success then
        print("✅ 人工接入请求成功！")
        print("会话ID: " .. result.session_id)
        print("排队位置: " .. result.queue_position)
        print("预计等待时间: " .. result.estimated_wait_time .. "秒")
        print("消息: " .. result.message)
        return result.session_id
    else
        print("❌ 人工接入请求失败")
        print("错误: " .. result.error)
        return nil
    end
end

-- 示例2: 紧急优先级请求
local function example_urgent_request()
    print("\n示例2: 紧急优先级请求")
    print_separator()
    
    local params = {
        user_id = "vip_user_001",
        user_name = "VIP客户李四",
        reason = "紧急：大额交易失败需要立即处理",
        priority = 1,  -- URGENT priority
        context = {
            vip_level = "platinum",
            transaction_amount = 50000,
            error_code = "TXN_FAILED_001"
        }
    }
    
    local result = human_intervention.request_human_agent(params)
    
    if result.success then
        print("🚨 紧急请求已提交！")
        print("会话ID: " .. result.session_id)
        print("优先处理中...")
        return result.session_id
    else
        print("❌ 请求失败: " .. result.error)
        return nil
    end
end

-- 示例3: 检查队列状态
local function example_check_status(session_id)
    print("\n示例3: 检查队列状态")
    print_separator()
    
    if not session_id then
        print("需要有效的会话ID")
        return
    end
    
    local status = human_intervention.check_queue_status(session_id)
    
    if status.success then
        print("📊 队列状态:")
        print("会话ID: " .. status.session_id)
        print("状态: " .. status.status)
        print("排队位置: " .. status.queue_position)
        print("预计等待: " .. status.estimated_wait_time .. "秒")
        
        if status.agent_info then
            print("已分配客服: " .. status.agent_info.name)
        end
    else
        print("❌ 查询失败: " .. status.error)
    end
end

-- 示例4: 获取可用客服列表
local function example_get_agents()
    print("\n示例4: 获取可用客服列表")
    print_separator()
    
    local result = human_intervention.get_available_agents()
    
    if result.success then
        print("👥 可用客服数量: " .. result.total_available)
        print("\n客服列表:")
        
        for i, agent in ipairs(result.agents) do
            print(string.format("%d. %s (%s)", i, agent.name, agent.id))
            print("   状态: " .. agent.status)
            print("   当前负载: " .. agent.current_load .. "/" .. agent.max_load)
            print("   专长: " .. table.concat(agent.specialties, ", "))
            print()
        end
    else
        print("❌ 获取失败")
    end
end

-- 示例5: 发送消息
local function example_send_message(session_id)
    print("\n示例5: 发送消息给客服")
    print_separator()
    
    if not session_id then
        print("需要有效的会话ID")
        return
    end
    
    local messages = {
        "你好，我需要帮助解决登录问题",
        "我已经尝试重置密码但还是无法登录",
        "用户名是: test@example.com"
    }
    
    for _, message in ipairs(messages) do
        local result = human_intervention.send_message(session_id, message)
        if result.success then
            print("✉️ 消息已发送: " .. message)
            print("   消息ID: " .. result.message_id)
        else
            print("❌ 发送失败: " .. result.error)
        end
    end
end

-- 示例6: 取消请求
local function example_cancel_request(session_id)
    print("\n示例6: 取消人工接入请求")
    print_separator()
    
    if not session_id then
        print("需要有效的会话ID")
        return
    end
    
    local result = human_intervention.cancel_request(session_id)
    
    if result.success then
        print("✅ " .. result.message)
    else
        print("❌ 取消失败: " .. result.error)
    end
end

-- 示例7: 结束会话并评分
local function example_end_session(session_id)
    print("\n示例7: 结束会话并评分")
    print_separator()
    
    if not session_id then
        print("需要有效的会话ID")
        return
    end
    
    local rating = 5  -- 5星好评
    local result = human_intervention.end_session(session_id, rating)
    
    if result.success then
        print("✅ " .. result.message)
        print("评分: " .. string.rep("⭐", rating))
    else
        print("❌ 结束失败: " .. result.error)
    end
end

-- 示例8: 批量请求处理
local function example_batch_requests()
    print("\n示例8: 批量请求处理")
    print_separator()
    
    local users = {
        {id = "user_001", name = "用户A", reason = "订单查询"},
        {id = "user_002", name = "用户B", reason = "退款申请"},
        {id = "user_003", name = "用户C", reason = "技术支持"}
    }
    
    local sessions = {}
    
    for _, user in ipairs(users) do
        local params = {
            user_id = user.id,
            user_name = user.name,
            reason = user.reason,
            priority = 3  -- MEDIUM priority
        }
        
        local result = human_intervention.request_human_agent(params)
        if result.success then
            table.insert(sessions, result.session_id)
            print(string.format("✅ %s 的请求已提交 (会话: %s)", 
                user.name, result.session_id))
        end
    end
    
    return sessions
end

-- 示例9: 配置管理
local function example_config_management()
    print("\n示例9: 配置管理")
    print_separator()
    
    -- 获取当前配置
    local current_config = human_intervention.get_config()
    print("当前配置:")
    print("API端点: " .. current_config.api_endpoint)
    print("超时时间: " .. current_config.timeout .. "秒")
    print("最大重试: " .. current_config.max_retries)
    
    -- 更新配置
    print("\n更新配置...")
    human_intervention.set_config({
        timeout = 60,
        max_retries = 5
    })
    
    -- 验证更新
    local updated_config = human_intervention.get_config()
    print("更新后:")
    print("超时时间: " .. updated_config.timeout .. "秒")
    print("最大重试: " .. updated_config.max_retries)
end

-- 示例10: 错误处理
local function example_error_handling()
    print("\n示例10: 错误处理示例")
    print_separator()
    
    -- 测试无效参数
    print("测试1: 空参数")
    local result = human_intervention.request_human_agent(nil)
    print("结果: " .. (result.error or "成功"))
    
    -- 测试缺少必要字段
    print("\n测试2: 缺少用户ID")
    result = human_intervention.request_human_agent({
        reason = "测试"
    })
    print("结果: " .. (result.error or "成功"))
    
    -- 测试无效会话ID
    print("\n测试3: 无效会话ID")
    result = human_intervention.check_queue_status(nil)
    print("结果: " .. (result.error or "成功"))
end

-- 示例11: 完整的会话流程
local function example_complete_flow()
    print("\n示例11: 完整的会话流程")
    print_separator()
    
    -- 步骤1: 创建请求
    print("步骤1: 创建人工接入请求")
    local params = {
        user_id = "demo_user",
        user_name = "演示用户",
        reason = "需要人工协助处理复杂问题",
        priority = 2,
        context = {
            session_start = os.date("%Y-%m-%d %H:%M:%S"),
            platform = "web",
            browser = "Chrome"
        }
    }
    
    local result = human_intervention.request_human_agent(params)
    if not result.success then
        print("请求失败: " .. result.error)
        return
    end
    
    local session_id = result.session_id
    print("✅ 会话创建成功: " .. session_id)
    
    -- 步骤2: 检查状态
    print("\n步骤2: 检查队列状态")
    os.execute("sleep 1")  -- 模拟等待
    local status = human_intervention.check_queue_status(session_id)
    print("当前状态: " .. status.status)
    
    -- 步骤3: 发送消息
    print("\n步骤3: 发送消息")
    human_intervention.send_message(session_id, "您好，我需要帮助")
    
    -- 步骤4: 模拟对话
    print("\n步骤4: 模拟对话交互")
    local conversation = {
        "我的账户无法登录",
        "用户名是 demo@example.com",
        "已经尝试重置密码",
        "谢谢您的帮助"
    }
    
    for i, msg in ipairs(conversation) do
        os.execute("sleep 0.5")  -- 模拟打字延迟
        local msg_result = human_intervention.send_message(session_id, msg)
        if msg_result.success then
            print("  用户: " .. msg)
        end
    end
    
    -- 步骤5: 结束会话
    print("\n步骤5: 结束会话并评分")
    local end_result = human_intervention.end_session(session_id, 5)
    print("✅ " .. end_result.message)
end

-- 主函数
local function main()
    print("========================================")
    print("     人工接入服务 - 使用示例")
    print("========================================")
    
    -- 可以根据需要注释/取消注释来运行不同的示例
    
    -- 基本示例
    local session_id = example_basic_request()
    
    -- 紧急请求
    -- example_urgent_request()
    
    -- 检查状态
    if session_id then
        example_check_status(session_id)
    end
    
    -- 获取客服列表
    example_get_agents()
    
    -- 发送消息
    if session_id then
        example_send_message(session_id)
    end
    
    -- 批量处理
    -- local sessions = example_batch_requests()
    
    -- 配置管理
    example_config_management()
    
    -- 错误处理
    example_error_handling()
    
    -- 完整流程
    example_complete_flow()
    
    -- 取消或结束会话
    if session_id then
        -- example_cancel_request(session_id)
        example_end_session(session_id)
    end
    
    print("\n========================================")
    print("           示例运行完成")
    print("========================================")
end

-- 运行主函数
main()