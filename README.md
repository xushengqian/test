# FreeSWITCH Lua 音频播放状态检测

本仓库包含在 FreeSWITCH Lua 脚本中判断音频是否正在播放的各种方法和示例。

## 核心方法

### 1. 通过通道变量检查

播放完成后检查 `playback_status` 变量：

```lua
session:execute("playback", audio_file)
local status = session:getVariable("playback_status")
if status == "success" then
    -- 播放成功完成
end
```

### 2. 使用 streamFile 配合回调（推荐）

```lua
session:setInputCallback("input_callback", "")
session:streamFile(audio_file)
session:unsetInputCallback()
```

### 3. 使用 API 异步检测

```lua
local api = freeswitch.API()
api:executeString("uuid_broadcast " .. uuid .. " " .. audio_file .. " aleg")
-- 检查是否存在
local exists = api:executeString("uuid_exists " .. uuid)
```

## 关键通道变量

| 变量名 | 说明 |
|--------|------|
| `playback_status` | 播放状态 (success/failed) |
| `playback_terminators_used` | 用户按下的终止键 |
| `playback_ms` | 播放的毫秒数 |
| `playback_seconds` | 播放的秒数 |
| `playback_last_offset_pos` | 最后的播放位置 |

## 关键 API 命令

| 命令 | 说明 |
|------|------|
| `uuid_broadcast <uuid> <path> [aleg\|bleg\|both]` | 异步播放音频 |
| `uuid_break <uuid> [all]` | 中断当前播放 |
| `uuid_getvar <uuid> <varname>` | 获取通道变量 |
| `uuid_exists <uuid>` | 检查通道是否存在 |

## 文件说明

- `freeswitch_audio_playback_check.lua` - 包含多种检测音频播放状态的示例代码

## 使用方式

将 Lua 脚本放入 FreeSWITCH 的 scripts 目录，然后在拨号计划中调用：

```xml
<action application="lua" data="freeswitch_audio_playback_check.lua"/>
```
