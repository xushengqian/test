-- agent_authentication.lua
-- 客服认证和状态管理脚本

local log = freeswitch.consoleLog

-- 客服数据库（实际环境中应该连接真实数据库）
local AGENT_DATABASE = {
    ["001"] = {
        name = "张三",
        level = "junior",
        password = "1234",
        skills = {"general", "billing"},
        max_concurrent_calls = 1,
        active = true
    },
    ["002"] = {
        name = "李四", 
        level = "senior",
        password = "2345",
        skills = {"general", "technical", "complaint"},
        max_concurrent_calls = 2,
        active = true
    },
    ["003"] = {
        name = "王五",
        level = "expert",
        password = "3456", 
        skills = {"technical", "complaint", "vip"},
        max_concurrent_calls = 3,
        active = true
    }
}

-- 验证客服身份
function authenticate_agent()
    local agent_id = session:getVariable("agent_id")
    if not agent_id then
        log("ERROR", "缺少客服ID")
        return false
    end
    
    local agent_info = AGENT_DATABASE[agent_id]
    if not agent_info then
        log("WARN", "客服ID不存在: " .. agent_id)
        session:streamFile("sounds/invalid_agent_id.wav")
        return false
    end
    
    if not agent_info.active then
        log("WARN", "客服账号已停用: " .. agent_id)
        session:streamFile("sounds/agent_account_disabled.wav") 
        return false
    end
    
    -- 密码验证
    session:streamFile("sounds/enter_password.wav")
    local entered_password = session:getDigits(4, "#", 10000)
    
    if entered_password ~= agent_info.password then
        log("WARN", "客服密码错误: " .. agent_id)
        session:streamFile("sounds/invalid_password.wav")
        return false
    end
    
    -- 验证成功，设置客服信息
    session:setVariable("agent_name", agent_info.name)
    session:setVariable("agent_level", agent_info.level)
    session:setVariable("agent_skills", table.concat(agent_info.skills, ","))
    session:setVariable("max_concurrent_calls", tostring(agent_info.max_concurrent_calls))
    session:setVariable("auth_time", os.time())
    
    log("INFO", string.format("客服 %s (%s) 认证成功", agent_id, agent_info.name))
    session:streamFile("sounds/login_successful.wav")
    
    return true
end

-- 主函数
function main()
    if not session then
        log("ERROR", "无法获取session对象")
        return
    end
    
    if authenticate_agent() then
        -- 认证成功后的处理逻辑
        local agent_id = session:getVariable("agent_id")
        log("NOTICE", "客服 " .. agent_id .. " 已成功登录系统")
    else
        -- 认证失败
        log("WARN", "客服认证失败")
        session:hangup()
    end
end

main()