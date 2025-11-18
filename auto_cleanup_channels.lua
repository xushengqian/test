-- FreeSWITCH Lua 脚本：自动清理残留通道
-- 可以放在 FreeSWITCH 的 scripts 目录下，通过定时任务或事件监听调用

-- 配置参数
local MAX_HANGUP_AGE = 60  -- 挂断后最多保留时间（秒）
local CHECK_INTERVAL = 30  -- 检查间隔（秒）

-- 获取所有通道
local function get_all_channels()
    local channels = {}
    local cmd = "show channels"
    local result = api:executeString(cmd)
    
    if result then
        for uuid in result:gmatch("uuid: ([a-f0-9%-]+)") do
            table.insert(channels, uuid)
        end
    end
    
    return channels
end

-- 获取通道状态信息
local function get_channel_info(uuid)
    local info = {}
    local cmd = "uuid_dump " .. uuid
    local result = api:executeString(cmd)
    
    if result then
        -- 解析状态
        local state = result:match("state: ([^\n]+)")
        local callstate = result:match("callstate: ([^\n]+)")
        local hangup_cause = result:match("hangup_cause: ([^\n]+)")
        local created = result:match("created: ([^\n]+)")
        
        info.state = state
        info.callstate = callstate
        info.hangup_cause = hangup_cause
        info.created = tonumber(created) or 0
    end
    
    return info
end

-- 检查通道是否需要清理
local function should_cleanup_channel(uuid, info)
    local now = os.time()
    
    -- 如果通道已经挂断
    if info.state == "CS_HANGUP" or info.callstate == "HANGUP" then
        -- 检查挂断时间
        local hangup_time = now - info.created  -- 简化处理，实际应该获取挂断时间
        if hangup_time > MAX_HANGUP_AGE then
            return true, "通道已挂断超过 " .. MAX_HANGUP_AGE .. " 秒"
        end
    end
    
    -- 如果有挂断原因但通道仍存在
    if info.hangup_cause and info.hangup_cause ~= "NONE_CALLED" and info.hangup_cause ~= "" then
        return true, "通道有挂断原因: " .. info.hangup_cause
    end
    
    return false, nil
end

-- 清理通道
local function cleanup_channel(uuid, reason)
    freeswitch.consoleLog("INFO", "清理残留通道: " .. uuid .. " - " .. reason)
    
    -- 尝试正常挂断
    local result = api:executeString("uuid_kill " .. uuid)
    
    -- 等待一下
    freeswitch.msleep(500)
    
    -- 检查是否还存在
    local exists = api:executeString("uuid_exists " .. uuid)
    if exists == "true" then
        -- 强制销毁
        freeswitch.consoleLog("WARNING", "通道 " .. uuid .. " 仍然存在，强制销毁")
        api:executeString("uuid_destroy " .. uuid)
    end
end

-- 主函数：清理所有残留通道
local function cleanup_lingering_channels()
    freeswitch.consoleLog("INFO", "开始检查残留通道...")
    
    local channels = get_all_channels()
    local cleaned = 0
    
    for _, uuid in ipairs(channels) do
        local info = get_channel_info(uuid)
        
        if info.state then
            local should_clean, reason = should_cleanup_channel(uuid, info)
            
            if should_clean then
                cleanup_channel(uuid, reason)
                cleaned = cleaned + 1
            end
        end
    end
    
    freeswitch.consoleLog("INFO", "清理完成，共清理 " .. cleaned .. " 个残留通道")
    return cleaned
end

-- 事件处理器：监听通道挂断事件
local function on_channel_hangup(session, event)
    local uuid = event:getHeader("Unique-ID")
    local hangup_cause = event:getHeader("Hangup-Cause")
    
    freeswitch.consoleLog("INFO", "通道挂断: " .. uuid .. " - " .. hangup_cause)
    
    -- 设置定时器，在 MAX_HANGUP_AGE 秒后检查并清理
    -- 注意：这需要配合定时任务使用
end

-- 执行清理
if argv[1] == "cleanup" then
    cleanup_lingering_channels()
else
    freeswitch.consoleLog("INFO", "使用方法: lua auto_cleanup_channels.lua cleanup")
end
