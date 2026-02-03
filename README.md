# FreeSWITCH Lua 音频播放状态检测

本文档介绍在 FreeSWITCH Lua 脚本中判断音频是否正在播放的多种方法。

## 核心方法汇总

### 方法1: 使用 streamFile() 返回值（最常用）

```lua
local digit = session:streamFile("/path/to/audio.wav")
if digit and digit ~= "" then
    -- 播放被 DTMF 按键中断
else
    -- 播放正常完成
end
```

### 方法2: 检查通道变量

```lua
-- 检查播放是否被终止符中断
local terminator = session:getVariable("playback_terminators_used")

-- 获取已播放的秒数
local playback_seconds = session:getVariable("playback_seconds")

-- 获取播放偏移位置
local last_offset = session:getVariable("playback_last_offset_pos")
```

### 方法3: 使用 session:ready() 检查会话状态

```lua
if session:ready() then
    session:streamFile(audio_file)
    -- 播放后检查
    if session:ready() then
        -- 会话仍然有效，播放完成
    else
        -- 播放期间会话断开（对方挂机）
    end
end
```

### 方法4: 使用 execute 并检查状态

```lua
session:execute("playback", audio_file)
local hangup_cause = session:hangupCause()
if hangup_cause and hangup_cause ~= "NONE" then
    -- 播放期间挂断
end
```

### 方法5: 通过 API 查询 UUID 状态

```lua
local api = freeswitch.API()
local uuid = session:getVariable("uuid")
local current_app = api:executeString("uuid_getvar " .. uuid .. " current_application")

if current_app == "playback" then
    -- 正在播放中
end
```

### 方法6: 检查当前应用

```lua
local current_app = session:getVariable("current_application")
local is_playing = (current_app == "playback" or 
                    current_app == "play_and_get_digits")
```

## 重要通道变量

| 变量名 | 描述 |
|--------|------|
| `playback_terminators_used` | 用于终止播放的按键 |
| `playback_seconds` | 已播放的秒数 |
| `playback_last_offset_pos` | 播放文件的偏移位置 |
| `current_application` | 当前正在执行的应用 |
| `current_application_data` | 当前应用的参数数据 |

## 注意事项

1. **阻塞式播放**: `streamFile()` 和 `execute("playback")` 都是阻塞式的，会等待播放完成
2. **非阻塞播放**: 如需异步播放，可使用 `uuid_broadcast` 或 `displace`
3. **中断播放**: 可通过 DTMF 按键或 ESL 命令中断播放
4. **设置终止符**: 使用 `session:setVariable("playback_terminators", "#*0123456789")` 设置哪些按键可以中断播放

## 示例文件

详细代码示例请查看 [freeswitch_audio_playback.lua](./freeswitch_audio_playback.lua)