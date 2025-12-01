#!/usr/bin/env lua

--[[
    坐席状态测试脚本
    用于测试坐席状态检查功能（独立运行，无需session）
    
    使用方法：
    1. 在 FreeSWITCH CLI 中运行：
       luarun test_agent_status.lua
    
    2. 或通过命令行：
       fs_cli -x "luarun test_agent_status.lua"
]]--

-- 测试配置
local test_agents = {"1001", "1002", "1003", "1004", "1005"}

-- 打印分隔线
local function print_separator()
    freeswitch.consoleLog("info", string.rep("=", 60) .. "\n")
end

-- 打印标题
local function print_header(title)
    print_separator()
    freeswitch.consoleLog("info", "  " .. title .. "\n")
    print_separator()
end

-- 测试1: 检查坐席注册状态
local function test_registration_status()
    print_header("测试1: 坐席注册状态检查")
    
    local api = freeswitch.API()
    
    for _, agent in ipairs(test_agents) do
        local result = api:executeString("sofia_contact " .. agent)
        
        local status = "❌ 未注册"
        if result and result ~= "" and not string.find(result, "error") then
            status = "✅ 已注册"
        end
        
        freeswitch.consoleLog("info", string.format("  坐席 %s: %s\n", agent, status))
        
        if status == "✅ 已注册" then
            freeswitch.consoleLog("info", string.format("    联系地址: %s\n", result))
        end
    end
end

-- 测试2: 检查坐席通话状态
local function test_call_status()
    print_header("测试2: 坐席通话状态检查")
    
    local api = freeswitch.API()
    
    for _, agent in ipairs(test_agents) do
        local result = api:executeString("show channels like " .. agent .. " as json")
        
        local status = "💚 空闲"
        local call_count = 0
        
        if result and result ~= "" then
            -- 简单计数（实际应该解析JSON）
            for uuid in string.gmatch(result, '"uuid"') do
                call_count = call_count + 1
            end
        end
        
        if call_count > 0 then
            status = "📞 通话中 (" .. call_count .. " 个通话)"
        end
        
        freeswitch.consoleLog("info", string.format("  坐席 %s: %s\n", agent, status))
    end
end

-- 测试3: 综合状态检查
local function test_combined_status()
    print_header("测试3: 综合状态检查")
    
    local api = freeswitch.API()
    
    freeswitch.consoleLog("info", string.format("  %-10s %-15s %-20s\n", 
        "坐席号", "注册状态", "通话状态"))
    freeswitch.consoleLog("info", string.rep("-", 60) .. "\n")
    
    for _, agent in ipairs(test_agents) do
        -- 检查注册
        local contact = api:executeString("sofia_contact " .. agent)
        local registered = contact and contact ~= "" and not string.find(contact, "error")
        
        -- 检查通话
        local channels = api:executeString("show channels like " .. agent)
        local in_call = channels and channels ~= "" and string.find(channels, agent)
        
        local reg_status = registered and "已注册" or "未注册"
        local call_status = in_call and "通话中" or "空闲"
        
        if not registered then
            call_status = "N/A"
        end
        
        freeswitch.consoleLog("info", string.format("  %-10s %-15s %-20s\n", 
            agent, reg_status, call_status))
    end
end

-- 测试4: 使用 sofia status 获取详细信息
local function test_sofia_status()
    print_header("测试4: Sofia 配置状态")
    
    local api = freeswitch.API()
    
    -- 获取所有profile
    local profiles = {"internal", "external"}
    
    for _, profile in ipairs(profiles) do
        freeswitch.consoleLog("info", "\n  Profile: " .. profile .. "\n")
        
        -- 获取注册用户数
        local result = api:executeString("sofia status profile " .. profile .. " reg")
        
        if result and result ~= "" then
            -- 统计注册数
            local count = 0
            for line in result:gmatch("[^\r\n]+") do
                if string.find(line, "@") then
                    count = count + 1
                end
            end
            
            freeswitch.consoleLog("info", string.format("    注册用户数: %d\n", count))
        end
    end
end

-- 测试5: 模拟转接测试（不实际执行）
local function test_transfer_simulation()
    print_header("测试5: 转接模拟测试")
    
    local api = freeswitch.API()
    
    freeswitch.consoleLog("info", "  测试场景：转接到各个坐席\n\n")
    
    for _, agent in ipairs(test_agents) do
        local contact = api:executeString("sofia_contact " .. agent)
        local registered = contact and contact ~= "" and not string.find(contact, "error")
        
        local channels = api:executeString("show channels like " .. agent)
        local in_call = channels and channels ~= "" and string.find(channels, agent)
        
        local decision = ""
        local reason = ""
        
        if not registered then
            decision = "❌ 拒绝转接"
            reason = "坐席未注册"
        elseif in_call then
            decision = "⚠️  可尝试"
            reason = "坐席忙线，可能会失败"
        else
            decision = "✅ 允许转接"
            reason = "坐席空闲可用"
        end
        
        freeswitch.consoleLog("info", string.format("  坐席 %s: %s - %s\n", 
            agent, decision, reason))
    end
end

-- 测试6: 性能测试
local function test_performance()
    print_header("测试6: 性能测试")
    
    local api = freeswitch.API()
    local iterations = 100
    local start_time = os.clock()
    
    freeswitch.consoleLog("info", string.format("  执行 %d 次状态查询...\n", iterations))
    
    for i = 1, iterations do
        for _, agent in ipairs(test_agents) do
            api:executeString("sofia_contact " .. agent)
        end
    end
    
    local end_time = os.clock()
    local duration = end_time - start_time
    local avg_time = (duration / (iterations * #test_agents)) * 1000
    
    freeswitch.consoleLog("info", string.format("  总耗时: %.2f 秒\n", duration))
    freeswitch.consoleLog("info", string.format("  平均每次查询: %.2f 毫秒\n", avg_time))
    freeswitch.consoleLog("info", string.format("  查询/秒: %.0f\n", (iterations * #test_agents) / duration))
end

-- 主函数
local function main()
    print_header("FreeSWITCH 坐席状态测试工具")
    
    freeswitch.consoleLog("info", "\n  测试坐席列表: " .. table.concat(test_agents, ", ") .. "\n\n")
    
    -- 运行所有测试
    test_registration_status()
    freeswitch.consoleLog("info", "\n")
    
    test_call_status()
    freeswitch.consoleLog("info", "\n")
    
    test_combined_status()
    freeswitch.consoleLog("info", "\n")
    
    test_sofia_status()
    freeswitch.consoleLog("info", "\n")
    
    test_transfer_simulation()
    freeswitch.consoleLog("info", "\n")
    
    test_performance()
    freeswitch.consoleLog("info", "\n")
    
    print_separator()
    freeswitch.consoleLog("info", "  测试完成！\n")
    print_separator()
end

-- 运行测试
main()
