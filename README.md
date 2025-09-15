# FreeSWITCH 呼入转人工功能实现

本项目实现了FreeSWITCH的呼入转人工功能，包含等待音频播放、智能坐席分配和队列管理。

## 功能特性

- ✅ 呼入电话自动转接到人工坐席
- ✅ 智能等待音频播放系统
- ✅ 多坐席轮询和负载均衡
- ✅ 队列转接功能
- ✅ 超时处理和友好提示
- ✅ 可配置的等待时间和音频

## 文件结构

```
/workspace/
├── dialplan.xml                    # FreeSWITCH拨号计划配置
├── transfer_to_agent.lua          # 基础转人工脚本
├── advanced_transfer_to_agent.lua # 高级转人工脚本（推荐）
├── waiting_audio_config.lua       # 等待音频配置
└── README.md                      # 说明文档
```

## 安装配置

### 1. 部署文件

将以下文件复制到FreeSWITCH相应目录：

```bash
# 复制拨号计划
cp dialplan.xml /usr/local/freeswitch/conf/dialplan/default/

# 复制Lua脚本
cp *.lua /usr/local/freeswitch/scripts/

# 设置执行权限
chmod +x /usr/local/freeswitch/scripts/*.lua
```

### 2. 配置坐席分机

在 `advanced_transfer_to_agent.lua` 中配置坐席分机号：

```lua
local CONFIG = {
    AGENT_EXTENSIONS = {"1001", "1002", "1003"},  -- 修改为实际坐席分机
    QUEUE_NAME = "support",
    MAX_WAIT_TIME = 60,        -- 最大等待时间（秒）
    RING_TIMEOUT = 15,         -- 振铃超时（秒）
    PROMPT_INTERVAL = 15,      -- 提示间隔（秒）
    BACKGROUND_MUSIC = true    -- 是否播放背景音乐
}
```

### 3. 准备音频文件

确保以下音频文件存在于FreeSWITCH的音频目录：

```
/usr/local/freeswitch/sounds/en/us/callie/ivr/
├── ivr-please_hold_while_party_answered.wav
├── ivr-hold_music.wav
├── ivr-please_wait.wav
├── ivr-call_being_transferred.wav
├── ivr-connecting_you.wav
├── ivr-no_one_available.wav
├── ivr-try_again_later.wav
├── ivr-goodbye.wav
└── ivr-welcome.wav
```

## 使用方法

### 基础使用

1. **配置拨号计划**：将 `dialplan.xml` 中的规则添加到FreeSWITCH拨号计划
2. **重启FreeSWITCH**：`fs_cli -x "reloadxml"`
3. **测试功能**：拨打配置的号码进行测试

### 高级配置

#### 修改等待音频

编辑 `waiting_audio_config.lua`：

```lua
local waiting_audio_config = {
    audio_files = {
        "ivr/ivr-please_hold_while_party_answered.wav",
        "ivr/ivr-hold_music.wav",
        "ivr/ivr-please_wait.wav",
        -- 添加更多音频文件
    },
    prompt_interval = 10,  -- 修改提示间隔
    background_music = "ivr/ivr-hold_music.wav"
}
```

#### 自定义转接逻辑

在 `advanced_transfer_to_agent.lua` 中修改转接策略：

```lua
-- 修改坐席选择策略
local function select_agent(available_agents)
    -- 实现自定义选择逻辑
    -- 例如：轮询、随机、负载均衡等
    return available_agents[1]
end
```

## 拨号计划说明

### 主要扩展

1. **inbound_to_agent**: 呼入转人工主规则
2. **agent_extension**: 人工坐席分机
3. **queue_transfer**: 队列转接

### 配置示例

```xml
<!-- 呼入转人工 -->
<extension name="inbound_to_agent">
    <condition field="destination_number" expression="^(.*)$">
        <action application="lua" data="advanced_transfer_to_agent.lua"/>
    </condition>
</extension>
```

## 功能说明

### 转接流程

1. **呼入检测**：系统检测到呼入电话
2. **播放欢迎音**：播放欢迎和等待提示
3. **坐席检查**：检查可用坐席状态
4. **智能转接**：选择最佳坐席进行转接
5. **等待处理**：如无坐席则进入队列等待
6. **超时处理**：超时后播放提示并挂断

### 等待音频系统

- **定期提示**：每隔指定时间播放等待提示
- **背景音乐**：循环播放背景音乐
- **随机选择**：从多个音频文件中随机选择
- **超时提示**：转接失败时播放友好提示

### 坐席管理

- **多坐席支持**：支持多个坐席同时工作
- **负载均衡**：智能分配呼入电话
- **状态检测**：实时检测坐席可用性
- **故障转移**：坐席不可用时自动转队列

## 故障排除

### 常见问题

1. **脚本无法执行**
   - 检查文件权限：`chmod +x *.lua`
   - 检查Lua模块路径

2. **音频文件无法播放**
   - 检查音频文件路径
   - 确认文件格式正确（WAV格式）

3. **转接失败**
   - 检查坐席分机配置
   - 确认坐席用户存在且在线

4. **队列不工作**
   - 检查FIFO队列配置
   - 确认队列名称正确

### 调试方法

启用详细日志：

```bash
# 在fs_cli中执行
console loglevel debug
console loglevel info
```

查看Lua脚本日志：

```bash
# 查看FreeSWITCH日志
tail -f /usr/local/freeswitch/log/freeswitch.log | grep lua
```

## 扩展功能

### 可添加的功能

1. **坐席技能组**：根据技能分配坐席
2. **优先级队列**：VIP客户优先转接
3. **统计报表**：转接成功率和等待时间统计
4. **Web管理界面**：坐席状态管理
5. **录音功能**：通话录音和回放

### 集成建议

- 与CRM系统集成
- 与工单系统对接
- 添加实时监控面板
- 实现坐席工作状态管理

## 技术支持

如有问题，请检查：

1. FreeSWITCH版本兼容性
2. Lua模块安装
3. 音频文件格式和路径
4. 网络连接状态
5. 坐席分机配置

## 更新日志

- v1.0.0: 基础转人工功能
- v1.1.0: 添加等待音频系统
- v1.2.0: 智能坐席分配
- v1.3.0: 队列管理和超时处理