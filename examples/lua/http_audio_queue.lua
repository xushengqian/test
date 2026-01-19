--[[
    FreeSWITCH Lua 脚本：HTTP 音频队列播放器
    
    功能：支持音频队列、优先级、中断等高级功能
    
    使用场景：
    - 呼叫中心排队等待音乐
    - 广播系统
    - 多段音频顺序播放
    
    作者：FreeSWITCH HTTP Audio Demo
    版本：1.0.0
]]--

-- 音频队列管理器
local AudioQueueManager = {}
AudioQueueManager.__index = AudioQueueManager

function AudioQueueManager.new(session)
    local self = setmetatable({}, AudioQueueManager)
    self.session = session
    self.queue = {}
    self.current_index = 0
    self.is_playing = false
    self.is_paused = false
    self.loop_mode = false  -- 是否循环播放
    self.shuffle_mode = false  -- 是否随机播放
    return self
end

-- 日志
function AudioQueueManager:log(level, message)
    freeswitch.consoleLog(level, "[AUDIO-QUEUE] " .. message .. "\n")
end

-- 添加音频到队列
function AudioQueueManager:add(url, priority, metadata)
    priority = priority or 0
    metadata = metadata or {}
    
    local item = {
        url = url,
        priority = priority,
        metadata = metadata,
        added_at = os.time()
    }
    
    table.insert(self.queue, item)
    self:log("INFO", "Added to queue: " .. url .. " (priority: " .. priority .. ")")
    
    -- 按优先级排序（高优先级在前）
    table.sort(self.queue, function(a, b)
        if a.priority ~= b.priority then
            return a.priority > b.priority
        end
        return a.added_at < b.added_at
    end)
    
    return #self.queue
end

-- 批量添加音频
function AudioQueueManager:addBatch(urls, priority)
    for _, url in ipairs(urls) do
        self:add(url, priority)
    end
end

-- 清空队列
function AudioQueueManager:clear()
    self.queue = {}
    self.current_index = 0
    self:log("INFO", "Queue cleared")
end

-- 获取队列长度
function AudioQueueManager:length()
    return #self.queue
end

-- 获取播放 URL
function AudioQueueManager:_getPlaybackUrl(url)
    local lower_url = string.lower(url)
    
    if string.match(lower_url, "%.mp3") then
        return "shout://" .. url
    elseif string.match(url, "^https?://") then
        return "http_cache://" .. url
    else
        return "http_cache://http://" .. url
    end
end

-- 播放单个音频
function AudioQueueManager:_playOne(item)
    if not self.session:ready() then
        self:log("WARNING", "Session not ready")
        return false
    end
    
    local playback_url = self:_getPlaybackUrl(item.url)
    self:log("INFO", "Playing: " .. playback_url)
    
    self.is_playing = true
    local result = self.session:streamFile(playback_url)
    self.is_playing = false
    
    return result
end

-- 开始播放队列
function AudioQueueManager:play()
    if #self.queue == 0 then
        self:log("WARNING", "Queue is empty")
        return false
    end
    
    self.current_index = 1
    
    while self.current_index <= #self.queue and self.session:ready() do
        if self.is_paused then
            self.session:sleep(100)
            goto continue
        end
        
        local item = self.queue[self.current_index]
        local success = self:_playOne(item)
        
        if success then
            self:log("INFO", string.format("Completed %d/%d", self.current_index, #self.queue))
        else
            self:log("WARNING", "Playback failed for: " .. item.url)
        end
        
        self.current_index = self.current_index + 1
        
        -- 循环模式
        if self.loop_mode and self.current_index > #self.queue then
            self.current_index = 1
        end
        
        ::continue::
    end
    
    self:log("INFO", "Queue playback finished")
    return true
end

-- 跳到下一个
function AudioQueueManager:next()
    if self.is_playing then
        -- 中断当前播放
        local api = freeswitch.API()
        local uuid = self.session:getVariable("uuid")
        api:executeString("uuid_break " .. uuid .. " all")
    end
    
    -- 索引会在 play() 循环中自动增加
end

-- 跳到上一个
function AudioQueueManager:previous()
    if self.current_index > 2 then
        self.current_index = self.current_index - 2
    else
        self.current_index = 0
    end
    
    if self.is_playing then
        local api = freeswitch.API()
        local uuid = self.session:getVariable("uuid")
        api:executeString("uuid_break " .. uuid .. " all")
    end
end

-- 暂停/恢复
function AudioQueueManager:togglePause()
    self.is_paused = not self.is_paused
    
    if self.is_paused then
        self:log("INFO", "Playback paused")
    else
        self:log("INFO", "Playback resumed")
    end
    
    return self.is_paused
end

-- 设置循环模式
function AudioQueueManager:setLoop(enabled)
    self.loop_mode = enabled
    self:log("INFO", "Loop mode: " .. tostring(enabled))
end

-- 获取当前播放信息
function AudioQueueManager:getCurrentInfo()
    if self.current_index > 0 and self.current_index <= #self.queue then
        local item = self.queue[self.current_index]
        return {
            index = self.current_index,
            total = #self.queue,
            url = item.url,
            metadata = item.metadata,
            is_playing = self.is_playing,
            is_paused = self.is_paused
        }
    end
    return nil
end


-- 等待队列播放器（用于排队等待场景）
local WaitingQueuePlayer = {}
WaitingQueuePlayer.__index = WaitingQueuePlayer

function WaitingQueuePlayer.new(session, options)
    local self = setmetatable({}, WaitingQueuePlayer)
    self.session = session
    self.options = options or {}
    
    -- 默认配置
    self.music_urls = self.options.music_urls or {
        "http://audio.example.com/moh/track1.mp3",
        "http://audio.example.com/moh/track2.mp3",
        "http://audio.example.com/moh/track3.mp3"
    }
    
    self.announcement_url = self.options.announcement_url or 
        "http://audio.example.com/announcement/please_wait.mp3"
    
    self.announcement_interval = self.options.announcement_interval or 30  -- 秒
    self.position_announcement = self.options.position_announcement or true
    
    self.queue_position = 0
    self.is_running = false
    self.stop_flag = false
    
    return self
end

function WaitingQueuePlayer:log(level, message)
    freeswitch.consoleLog(level, "[WAITING-QUEUE] " .. message .. "\n")
end

function WaitingQueuePlayer:_getPlaybackUrl(url)
    local lower_url = string.lower(url)
    if string.match(lower_url, "%.mp3") then
        return "shout://" .. url
    else
        return "http_cache://" .. url
    end
end

-- 设置队列位置
function WaitingQueuePlayer:setPosition(position)
    self.queue_position = position
    self:log("INFO", "Queue position updated: " .. position)
end

-- 停止播放
function WaitingQueuePlayer:stop()
    self.stop_flag = true
    if self.is_running then
        local api = freeswitch.API()
        local uuid = self.session:getVariable("uuid")
        api:executeString("uuid_break " .. uuid .. " all")
    end
end

-- 开始等待循环
function WaitingQueuePlayer:start()
    self.is_running = true
    self.stop_flag = false
    
    local music_index = 1
    local last_announcement = os.time()
    
    while not self.stop_flag and self.session:ready() do
        -- 检查是否需要播放通知
        local now = os.time()
        if now - last_announcement >= self.announcement_interval then
            self:log("INFO", "Playing announcement")
            
            local ann_url = self:_getPlaybackUrl(self.announcement_url)
            self.session:streamFile(ann_url)
            
            -- 播放队列位置
            if self.position_announcement and self.queue_position > 0 then
                -- 这里可以调用 TTS 服务
                local tts_url = string.format(
                    "http://tts.example.com/api/speak?text=您前面还有%d位客户在等待",
                    self.queue_position
                )
                self.session:streamFile("shout://" .. tts_url)
            end
            
            last_announcement = now
        end
        
        if self.stop_flag then break end
        
        -- 播放背景音乐
        local music_url = self.music_urls[music_index]
        if music_url then
            self:log("INFO", "Playing music: " .. music_url)
            local playback_url = self:_getPlaybackUrl(music_url)
            self.session:streamFile(playback_url)
        end
        
        -- 切换到下一首
        music_index = music_index + 1
        if music_index > #self.music_urls then
            music_index = 1
        end
        
        -- 短暂间隔
        self.session:sleep(500)
    end
    
    self.is_running = false
    self:log("INFO", "Waiting queue player stopped")
end


-- 主函数示例
local function main()
    if not session then
        freeswitch.consoleLog("ERROR", "No session object available\n")
        return
    end
    
    session:answer()
    session:sleep(500)
    
    -- 示例 1：使用队列管理器
    local qm = AudioQueueManager.new(session)
    
    -- 添加音频到队列
    qm:add("http://audio.example.com/welcome.mp3", 10)  -- 高优先级
    qm:add("http://audio.example.com/message1.mp3", 5)
    qm:add("http://audio.example.com/message2.mp3", 5)
    qm:add("http://audio.example.com/goodbye.mp3", 1)   -- 低优先级
    
    -- 设置循环模式（可选）
    -- qm:setLoop(true)
    
    -- 开始播放
    qm:play()
    
    session:hangup()
end

-- 等待队列示例
local function waiting_example()
    if not session then
        freeswitch.consoleLog("ERROR", "No session object available\n")
        return
    end
    
    session:answer()
    session:sleep(500)
    
    local player = WaitingQueuePlayer.new(session, {
        music_urls = {
            "http://audio.example.com/moh/jazz1.mp3",
            "http://audio.example.com/moh/jazz2.mp3"
        },
        announcement_url = "http://audio.example.com/announcement/wait.mp3",
        announcement_interval = 30
    })
    
    -- 设置初始队列位置
    player:setPosition(3)
    
    -- 开始播放（会一直循环直到被中断）
    -- 在实际使用中，可以在另一个线程中调用 player:stop() 来停止
    player:start()
    
    session:hangup()
end

-- 导出
return {
    AudioQueueManager = AudioQueueManager,
    WaitingQueuePlayer = WaitingQueuePlayer,
    main = main,
    waiting_example = waiting_example
}
