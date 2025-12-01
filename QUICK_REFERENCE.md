# FreeSWITCH 转接判断快速参考

## 核心代码片段

### 1. 最简单的判断方法

```lua
session:execute("bridge", "user/1001")
local cause = session:getVariable("bridge_hangup_cause")

if cause == "SUCCESS" then
    -- 成功
elseif cause == "USER_NOT_REGISTERED" then
    -- 坐席未登录
end
```

### 2. 转接前检查坐席是否在线

```lua
local api = freeswitch.API()
local contact = api:executeString("sofia_contact 1001")

if contact and contact ~= "" and not string.find(contact, "error") then
    -- 坐席在线，可以转接
    session:execute("bridge", "user/1001")
else
    -- 坐席离线
    session:streamFile("ivr/ivr-no_user_response.wav")
end
```

### 3. 完整的转接函数

```lua
function transfer_with_check(agent_number)
    -- 检查注册
    local api = freeswitch.API()
    local contact = api:executeString("sofia_contact " .. agent_number)
    
    if not contact or contact == "" or string.find(contact, "error") then
        return false, "AGENT_OFFLINE"
    end
    
    -- 设置参数
    session:setVariable("call_timeout", "30")
    session:setVariable("continue_on_fail", "true")
    
    -- 执行转接
    session:execute("bridge", "user/" .. agent_number)
    
    -- 检查结果
    local cause = session:getVariable("bridge_hangup_cause")
    
    if cause == "SUCCESS" then
        return true, "SUCCESS"
    else
        return false, cause
    end
end
```

## 关键挂断原因速查表

| 代码 | 含义 | 建议处理 |
|------|------|---------|
| `SUCCESS` | 成功 | ✅ 无需处理 |
| `USER_NOT_REGISTERED` | **坐席未登录** | ❌ 提示离线，不重试 |
| `NO_ANSWER` | 无应答 | 🔄 可重试 |
| `USER_BUSY` | 忙线 | ⏭️ 换其他坐席 |
| `CALL_REJECTED` | 拒接 | ⏭️ 换其他坐席 |

## 常用 API 命令

### FreeSWITCH CLI

```bash
# 检查坐席注册状态
fs_cli -x "sofia_contact 1001"

# 查看所有注册用户
fs_cli -x "sofia status profile internal reg"

# 查看坐席是否在通话
fs_cli -x "show channels like 1001"

# 查看呼叫中心坐席状态（如启用callcenter模块）
fs_cli -x "callcenter_config agent list"
```

### Lua API

```lua
local api = freeswitch.API()

-- 检查注册
local contact = api:executeString("sofia_contact 1001")

-- 查看通道
local channels = api:executeString("show channels like 1001")

-- 获取变量
local value = session:getVariable("variable_name")

-- 设置变量
session:setVariable("variable_name", "value")
```

## 必须设置的变量

```lua
-- 避免转接失败直接挂断
session:setVariable("continue_on_fail", "true")

-- 设置振铃超时
session:setVariable("call_timeout", "30")

-- 转接后是否挂断
session:setVariable("hangup_after_bridge", "true")  -- 默认
```

## Dialplan XML 示例

```xml
<action application="set" data="continue_on_fail=true"/>
<action application="set" data="call_timeout=30"/>
<action application="bridge" data="user/1001"/>
<action application="log" data="INFO 转接结果: ${bridge_hangup_cause}"/>
```

## 调试技巧

### 1. 启用详细日志

```bash
fs_cli
> console loglevel 7
```

### 2. 打印所有变量

```lua
session:execute("info", "")
```

### 3. 日志输出

```lua
freeswitch.consoleLog("info", "消息内容\n")
freeswitch.consoleLog("warning", "警告信息\n")
freeswitch.consoleLog("err", "错误信息\n")
```

## 常见问题解决

### 问题：坐席未登录时直接挂断

**解决**：设置 `continue_on_fail=true`

```lua
session:setVariable("continue_on_fail", "true")
```

### 问题：如何区分坐席拒接和未登录

**解决**：检查 `bridge_hangup_cause`

```lua
local cause = session:getVariable("bridge_hangup_cause")
-- USER_NOT_REGISTERED = 未登录
-- CALL_REJECTED = 拒接
-- NO_ANSWER = 未接听
```

### 问题：如何实现转接重试

**解决**：使用循环，但坐席离线时不重试

```lua
for i = 1, 3 do
    if not check_registered(agent) then
        break  -- 离线则停止
    end
    
    session:execute("bridge", "user/" .. agent)
    
    if session:getVariable("bridge_hangup_cause") == "SUCCESS" then
        break
    end
end
```

### 问题：如何转接到多个坐席

**解决**：使用循环或 bridge 的多目标语法

```lua
-- 方法1: 循环
for _, agent in ipairs({"1001", "1002", "1003"}) do
    if check_registered(agent) then
        session:execute("bridge", "user/" .. agent)
        if session:getVariable("bridge_hangup_cause") == "SUCCESS" then
            break
        end
    end
end

-- 方法2: 同时振铃
session:execute("bridge", "user/1001,user/1002,user/1003")
```

## 文件部署位置

```bash
# Lua脚本目录
/usr/share/freeswitch/scripts/
# 或
/usr/local/freeswitch/scripts/

# Dialplan目录
/etc/freeswitch/dialplan/
# 或
/usr/local/freeswitch/conf/dialplan/

# 音频文件目录
/usr/share/freeswitch/sounds/
```

## 测试步骤

1. **部署脚本**
   ```bash
   cp transfer_to_agent.lua /usr/share/freeswitch/scripts/
   ```

2. **配置 Dialplan**
   ```xml
   <action application="lua" data="transfer_to_agent.lua"/>
   ```

3. **重载配置**
   ```bash
   fs_cli -x "reloadxml"
   ```

4. **测试呼叫**
   - 拨打配置的号码
   - 观察 FreeSWITCH 日志
   - 验证转接行为

5. **调试日志**
   ```bash
   tail -f /var/log/freeswitch/freeswitch.log
   ```

## 性能优化建议

1. **复用 API 对象**
   ```lua
   local api = freeswitch.API()  -- 只创建一次
   ```

2. **避免频繁数据库操作**
   - 使用连接池
   - 批量写入日志

3. **合理设置超时时间**
   ```lua
   session:setVariable("call_timeout", "30")  -- 不要太长
   ```

4. **使用缓存**
   - 缓存坐席状态
   - 定期更新

## 相关资源

- 完整脚本：`transfer_to_agent.lua`
- 简化示例：`transfer_simple.lua`
- 工具库：`agent_utils.lua`
- 使用示例：`transfer_example.lua`
- 详细文档：`README.md`
