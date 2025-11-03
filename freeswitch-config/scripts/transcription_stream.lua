--[[
  Freeswitch Lua?? - ???????WebSocket?????????
  ????: /usr/share/freeswitch/scripts/transcription_stream.lua
]]--

-- ????
local session_id = argv[1]
local mode = argv[2] or "customer_only"  -- customer_only, agent_transfer, bidirectional

-- WebSocket?????
local WS_SERVER = "ws://localhost:8765"

-- ????
local function log(level, message)
    freeswitch.consoleLog(level, "[TranscriptionStream] " .. message .. "\n")
end

log("INFO", "??????????ID: " .. session_id .. ", ??: " .. mode)

-- ????????
local function send_session_start(ws, session)
    local call_info = {
        caller_number = session:getVariable("caller_id_number"),
        callee_number = session:getVariable("destination_number"),
        caller_name = session:getVariable("caller_id_name"),
        direction = session:getVariable("call_direction")
    }
    
    local message = {
        type = "session_start",
        session_id = session_id,
        call_info = call_info
    }
    
    -- ??JSON??
    local json_str = cjson.encode(message)
    ws:write(json_str)
    
    log("INFO", "????????: " .. json_str)
end

-- ????????
local function send_session_end(ws)
    local message = {
        type = "session_end",
        session_id = session_id
    }
    
    local json_str = cjson.encode(message)
    ws:write(json_str)
    
    log("INFO", "????????")
end

-- ??????
local function send_audio_data(ws, channel, audio_data)
    -- ????: [session_id_length][session_id][channel][audio_data]
    local session_id_bytes = string.pack("I4", #session_id)
    local packet = session_id_bytes .. session_id .. string.char(channel) .. audio_data
    
    ws:write(packet, "binary")
end

-- ???
local function main()
    -- ??JSON?
    cjson = require("cjson")
    
    -- ???WebSocket???
    local WebSocket = require("websocket")
    local ws = WebSocket.new(WS_SERVER)
    
    if not ws:connect() then
        log("ERROR", "?????WebSocket???: " .. WS_SERVER)
        return
    end
    
    log("INFO", "?????WebSocket???")
    
    -- ??????
    local session = freeswitch.Session()
    
    if not session:ready() then
        log("ERROR", "?????")
        ws:close()
        return
    end
    
    -- ????????
    send_session_start(ws, session)
    
    -- ????hook
    -- ????hook??????
    local function audio_hook(session, type, obj, arg)
        if type == "audio" then
            local audio_data = obj:get_data()
            
            if audio_data and #audio_data > 0 then
                -- ??????
                local channel_type = arg  -- 0=??, 1=??
                send_audio_data(ws, channel_type, audio_data)
            end
        end
    end
    
    -- ?????????hook
    if mode == "bidirectional" then
        -- ?????????????????
        session:setInputCallback("audio_hook", 0)  -- ????
        session:setOutputCallback("audio_hook", 1) -- ????
        
    elseif mode == "agent_transfer" then
        -- ??????????????
        session:setInputCallback("audio_hook", 0)
        
    else
        -- ?????
        session:setInputCallback("audio_hook", 0)
    end
    
    log("INFO", "??hook??????: " .. mode)
    
    -- ??????
    while session:ready() do
        session:sleep(100)
    end
    
    -- ????????
    send_session_end(ws)
    
    -- ??WebSocket??
    ws:close()
    
    log("INFO", "???????")
end

-- ????
local status, err = pcall(main)
if not status then
    log("ERROR", "??????: " .. tostring(err))
end
