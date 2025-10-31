# FreeSWITCH 机器人外呼识别转人工方案

## 1. 方案概述
- 机器人外呼负责首轮问候与信息采集，当语音识别结果或意图解析判断用户要求“转人工”时，通过 FreeSWITCH 会话控制把通话无缝转接到人工坐席或队列。
- 实现核心：`play_and_detect_speech`/`detect_speech` 捕获语音、外部 NLU 解析意图、Lua/ESL 逻辑根据意图触发 `transfer` 或 `uuid_transfer`。

## 2. 前提准备
- 已部署 FreeSWITCH，并启用语音识别（如 `mod_unimrcp`、`mod_google_transcribe`、`mod_ali_asr` 等）。
- 机器人语音应用可播放提示音或 TTS，且能接收识别结果（JSON/XML）。
- 人工坐席侧已配置分机或呼叫队列（`mod_callcenter`）。

## 3. 呼叫流程示意
1. 机器人外呼并接通用户，播放问候或提问音频。
2. `play_and_detect_speech` 捕获用户语音 → 识别 → NLU → 产出意图标签，如 `transfer_agent`。
3. Lua/ESL 脚本读取识别结果变量，若判定需要人工，则执行转接至指定队列/分机；否则继续机器人对话。
4. 转人工后，清理会话变量，停止语音识别，进入人工服务流程。

## 4. 配置示例
### 4.1 Dialplan 片段（`/etc/freeswitch/dialplan/bot.xml`）

```xml
<?xml version="1.0"?>
<extension name="bot-outbound">
  <condition field="destination_number" expression="^(.+)$">
    <action application="answer"/>
    <action application="set" data="bot_agent_queue=support_queue"/>
    <action application="detect_speech" data="unimrcp default"/>
    <action application="play_and_detect_speech" data="ivr/bot_prompt.wav detect:grammar=transfer;no-input-timeout=5000;recognition-timeout=10000"/>
    <action application="lua" data="handle_intent.lua"/>
    <action application="detect_speech" data="stop"/>
    <action application="hangup"/>
  </condition>
</extension>
```

> `detect_speech` 配置根据所用 ASR/NLU 引擎调整（如 `engine=mrcp`、`profile=google`、`grammar=` 等）。

### 4.2 Lua 脚本（`/usr/local/freeswitch/scripts/handle_intent.lua`）

```lua
local cjson = require "cjson.safe"

if not session:ready() then return end

local raw_result = session:getVariable("detect_speech_result") or session:getVariable("last_asr_result")
freeswitch.consoleLog("INFO", "ASR Result: " .. tostring(raw_result) .. "\n")

local intent
if raw_result then
  local decoded = cjson.decode(raw_result)
  if decoded then
    intent = decoded.intent or decoded.interpretation or decoded.semantic_tag
  end
end

if intent == "transfer_agent" or intent == "人工服务" then
  local queue = session:getVariable("bot_agent_queue") or "support_queue"
  local fsapi = freeswitch.API()
  local uuid = session:get_uuid()
  fsapi:execute("uuid_transfer", string.format("%s -both inline::%s XML callcenter", uuid, queue))
else
  -- 继续机器人流程，可设置下一步提示或重试
  session:setVariable("bot_continue", "true")
end
```

要点：
- `detect_speech_result` 里通常包含 JSON，字段名称依据具体 ASR/NLU 结果调整。
- `uuid_transfer` 可保持同一通道并发起转队列；若直接转分机，可改为 `session:execute("transfer", "1000 XML default")`。
- 使用 `-both inline::` 可保留媒体与变量，必要时可去掉。

## 5. 意图与关键词策略
- 语法/意图模型需要覆盖常见表达，例如“转人工”“找客服”“人工服务”等。
- 可以在 NLU 中设置置信度阈值（如 ≥0.6），低于阈值时继续机器人澄清，避免误转。
- 若使用语法（SRGS/BNF），将关键词映射为 `transfer_agent`，解析后直接判断。

## 6. 转接队列/坐席配置
- 如果使用 `mod_callcenter`：
  - 在 `callcenter.conf.xml` 中配置 `support_queue`、坐席、策略（技能、优先级、溢出）。
  - 机器人转入队列前可设置 `effective_caller_id_number`、`caller_id_name` 方便坐席识别。
- 若是直接转 SIP 分机或外线，确保提前 `bridge` 目标并处理失败重试逻辑。

## 7. 测试与排查
- 打开 `fs_cli`，使用 `uuid_debug_media on <uuid>` 查看识别与转接过程。
- 检查变量：`show channels`、`uuid_getvar <uuid> detect_speech_result`。
- 若未触发转接，确认 ASR/NLU 输出字段、置信度、Lua 判定逻辑。
- 如需回滚机器人语句，可在 Lua 内设置会话变量并在 Dialplan 中分支控制。

## 8. 扩展建议
- 引入上下文对话管理（Redis/HTTP API）提升机器人转接前收集信息的完整性。
- 结合 CRM 或工单系统，在转人工时通过 `export` 变量携带客户意图、关键信息。
- 对接坐席工作台，展示机器人阶段的对话文字记录，帮助人工快速接手。