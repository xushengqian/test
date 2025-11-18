# FreeSWITCH 通道残留问题诊断和解决方案

## 问题描述

电话已挂断，但 `show channels` 命令显示会话一直存在，通道没有被正确清理。

## 常见原因

### 1. 通道状态异常
- 通道卡在 `CS_HANGUP` 状态，没有进入 `CS_DESTROY`
- 通道转移（transfer）或桥接（bridge）后没有正确清理
- 事件监听器或回调没有正确触发清理逻辑

### 2. 挂断处理不完整
- Lua 脚本中的 `session:hangup()` 调用后没有等待通道完全销毁
- 异步操作（如 `session:transfer()`）后没有检查通道状态
- 异常情况下通道没有正确挂断

### 3. 配置问题
- 通道超时设置不当
- `max_session_time` 设置过大
- `session-timeout` 参数配置错误

### 4. 资源泄漏
- 事件订阅没有正确取消
- 定时器没有清理
- 内存泄漏导致通道无法正常销毁

## 诊断步骤

### 1. 使用诊断脚本

```bash
# 运行诊断脚本
chmod +x diagnose_channels.sh
./diagnose_channels.sh

# 如果 fs_cli 不在 PATH 中
FS_CLI=/usr/local/freeswitch/bin/fs_cli ./diagnose_channels.sh
```

### 2. 手动检查通道状态

```bash
# 查看所有通道
fs_cli -x "show channels"

# 查看特定通道的详细信息
fs_cli -x "uuid_dump <uuid>"

# 检查通道是否存在
fs_cli -x "uuid_exists <uuid>"
```

### 3. 检查日志

```bash
# 查看 FreeSWITCH 日志
tail -f /usr/local/freeswitch/log/freeswitch.log | grep -E "CHANNEL_HANGUP|CHANNEL_DESTROY"

# 查找特定通道的日志
grep "<uuid>" /usr/local/freeswitch/log/freeswitch.log
```

## 解决方案

### 方案 1: 使用清理脚本（临时解决）

```bash
# 干运行模式（不实际删除）
DRY_RUN=true ./cleanup_channels.sh

# 实际清理
chmod +x cleanup_channels.sh
./cleanup_channels.sh
```

### 方案 2: 在 Lua 脚本中正确挂断通道

```lua
-- 正确的挂断方式
function hangup_channel(session)
    if session:ready() then
        -- 设置挂断原因
        session:setVariable("hangup_cause", "NORMAL_CLEARING")
        
        -- 挂断通道
        session:hangup("NORMAL_CLEARING")
        
        -- 等待通道销毁（可选）
        -- 注意：session 对象在 hangup 后可能立即失效
    end
end

-- 在转移后清理原通道
function transfer_and_cleanup(session, extension)
    local result = session:transfer(extension, "XML", "default")
    
    -- 检查转移结果
    if result then
        -- 等待一下确保转移完成
        freeswitch.msleep(1000)
        
        -- 检查通道状态
        local state = session:getState()
        if state == "CS_HANGUP" then
            -- 通道已挂断，等待清理
            freeswitch.msleep(500)
        end
    end
end
```

### 方案 3: 设置定时清理任务

在 FreeSWITCH 的 `autoload_configs/switch.conf.xml` 中添加：

```xml
<param name="enable-core-db" value="true"/>
```

然后创建定时任务（通过 cron 或 FreeSWITCH 的定时器）：

```bash
# 添加到 crontab
*/5 * * * * /path/to/cleanup_channels.sh >> /var/log/freeswitch/cleanup.log 2>&1
```

或者在 Lua 脚本中使用定时器：

```lua
-- 在 dialplan 或应用中设置定时清理
freeswitch.API():executeString("luarun", "auto_cleanup_channels.lua cleanup")
```

### 方案 4: 修复配置

检查并修改 `autoload_configs/switch.conf.xml`：

```xml
<!-- 设置最大会话时间（秒） -->
<param name="max-sessions" value="1000"/>
<param name="sessions-per-second" value="30"/>

<!-- 设置通道超时 -->
<param name="rtp-start-timeout" value="30"/>
<param name="rtp-end-timeout" value="10"/>
```

### 方案 5: 事件监听和自动清理

创建事件监听脚本 `event_listener.lua`：

```lua
-- 监听通道挂断事件
local function on_hangup(event)
    local uuid = event:getHeader("Unique-ID")
    local cause = event:getHeader("Hangup-Cause")
    
    freeswitch.consoleLog("INFO", "通道挂断: " .. uuid .. " - " .. cause)
    
    -- 设置延迟清理任务
    freeswitch.bgapi("sched_api", "+5", "uuid_destroy " .. uuid)
end

-- 订阅事件
freeswitch.EventConsumer("CHANNEL_HANGUP"):bind(on_hangup)
```

## 预防措施

### 1. 代码审查要点

- ✅ 确保所有 `session:transfer()` 调用后检查通道状态
- ✅ 确保异常处理中包含通道清理逻辑
- ✅ 避免在通道挂断后继续操作 session 对象
- ✅ 使用 `session:ready()` 检查通道状态

### 2. 监控和告警

```bash
# 监控通道数量
fs_cli -x "show channels" | grep -c "uuid"

# 如果通道数量超过阈值，发送告警
CHANNEL_COUNT=$(fs_cli -x "show channels" | grep -c "uuid")
if [ $CHANNEL_COUNT -gt 100 ]; then
    echo "警告: 通道数量异常: $CHANNEL_COUNT"
    # 发送告警通知
fi
```

### 3. 定期维护

- 每天检查通道数量
- 每周审查日志中的异常挂断
- 每月检查通道清理脚本的执行情况

## 相关命令参考

```bash
# 强制挂断通道
fs_cli -x "uuid_kill <uuid>"

# 强制销毁通道
fs_cli -x "uuid_destroy <uuid>"

# 挂断所有通道（危险！）
fs_cli -x "hupall"

# 查看通道统计
fs_cli -x "show calls count"
fs_cli -x "show channels count"
```

## 注意事项

⚠️ **警告**: 
- 强制清理通道可能导致正在进行的通话中断
- 在生产环境中使用清理脚本前，务必先进行干运行测试
- 建议在低峰期执行批量清理操作
- 保留日志以便问题追踪

## 联系支持

如果问题持续存在，请提供以下信息：
1. FreeSWITCH 版本
2. 通道诊断脚本的输出
3. 相关日志文件
4. Lua 脚本代码（如果使用）
