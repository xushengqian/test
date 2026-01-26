-- scripts/play_stream.lua
-- FreeSwitch Lua Script for Audio Streaming
--
-- This script demonstrates how to play an audio stream within a Lua script.
-- It offers more flexibility than the XML dialplan (e.g., error handling, logic).

-- Configuration: Public radio stream URL (FIP Radio)
local stream_url = "shout://icecast.radiofrance.fr/fip-midfi.mp3"

-- Main execution block
if session:ready() then
    freeswitch.consoleLog("INFO", "Lua: Session is ready. Preparing to play audio stream.\n")
    
    -- Set a playback terminator (optional)
    -- This allows the user to stop playback by pressing '#'
    session:setVariable("playback_terminators", "#")
    
    freeswitch.consoleLog("INFO", "Lua: Attempting to stream from: " .. stream_url .. "\n")
    
    -- Play the stream
    -- session:streamFile() is the Lua API equivalent of the 'playback' application
    -- It plays the file or stream to the current session.
    local status = session:streamFile(stream_url)
    
    if status then
        freeswitch.consoleLog("INFO", "Lua: Stream played successfully (or stopped by user).\n")
    else
        freeswitch.consoleLog("ERR", "Lua: Failed to play stream.\n")
    end
    
    -- You can add logic here to handle what happens after playback
    -- For example, play a goodbye message or return to a menu.
    
else
    freeswitch.consoleLog("ERR", "Lua: Session is not ready. Cannot play stream.\n")
end
