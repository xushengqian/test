# FreeSWITCH 残留通道问题解决方案

## 问题概述

在 FreeSWITCH 使用过程中，可能会遇到电话已经挂断，但通过 `show channels` 命令仍能看到会话存在的问题。这些残留通道会占用系统资源，影响系统性能，严重时可能导致新呼叫无法建立。

本项目提供了一套完整的解决方案，包括问题诊断、自动清理和预防措施。

## 项目结构

```
.
├── README.md                                    # 本文件
├── freeswitch_lingering_channels_investigation.md  # 详细的问题调查文档
├── diagnose_channels.sh                         # 通道诊断脚本
├── cleanup_lingering_channels.sh                # 自动清理脚本
├── monitor_channels.sh                          # 监控脚本
└── freeswitch_timeout_config.xml               # 配置示例
```

## 快速开始

### 1. 诊断当前状态

运行诊断脚本查看系统中是否存在残留通道：

```bash
chmod +x diagnose_channels.sh
./diagnose_channels.sh
```

脚本会：
- 统计当前通道数量
- 识别长时间运行的通道
- 检查通道执行的应用状态
- 生成详细的诊断报告

### 2. 清理残留通道

如果发现残留通道，可以使用清理脚本：

```bash
chmod +x cleanup_lingering_channels.sh

# 演练模式（不实际挂断，先看看会清理哪些）
./cleanup_lingering_channels.sh --dry-run

# 清理超过2小时的通道（默认）
./cleanup_lingering_channels.sh

# 清理超过1小时的通道
./cleanup_lingering_channels.sh -d 3600

# 查看帮助
./cleanup_lingering_channels.sh --help
```

### 3. 设置定期监控

运行监控脚本检查系统状态：

```bash
chmod +x monitor_channels.sh
./monitor_channels.sh
```

## 自动化部署

### 使用 Cron 定时任务

编辑 crontab：

```bash
sudo crontab -e
```

添加以下任务：

```cron
# 每10分钟检查一次通道状态
*/10 * * * * /path/to/monitor_channels.sh >> /var/log/freeswitch/monitor.log 2>&1

# 每小时清理超过2小时的残留通道
0 * * * * /path/to/cleanup_lingering_channels.sh -d 7200 >> /var/log/freeswitch/cleanup.log 2>&1

# 每天凌晨2点生成诊断报告
0 2 * * * /path/to/diagnose_channels.sh >> /var/log/freeswitch/diagnosis.log 2>&1
```

### 使用 Systemd Timer（推荐）

创建监控服务：

```bash
sudo nano /etc/systemd/system/freeswitch-monitor.service
```

```ini
[Unit]
Description=FreeSWITCH Channel Monitor
After=freeswitch.service

[Service]
Type=oneshot
ExecStart=/path/to/monitor_channels.sh
User=freeswitch
StandardOutput=journal

[Install]
WantedBy=multi-user.target
```

创建定时器：

```bash
sudo nano /etc/systemd/system/freeswitch-monitor.timer
```

```ini
[Unit]
Description=Run FreeSWITCH Channel Monitor every 10 minutes
Requires=freeswitch-monitor.service

[Timer]
OnBootSec=5min
OnUnitActiveSec=10min
Unit=freeswitch-monitor.service

[Install]
WantedBy=timers.target
```

启用并启动定时器：

```bash
sudo systemctl daemon-reload
sudo systemctl enable freeswitch-monitor.timer
sudo systemctl start freeswitch-monitor.timer

# 查看状态
sudo systemctl status freeswitch-monitor.timer
sudo systemctl list-timers
```

## FreeSWITCH 配置优化

### 1. 设置 RTP 超时

编辑 `conf/vars.xml`：

```xml
<X-PRE-PROCESS cmd="set" data="rtp_timeout_sec=300"/>
<X-PRE-PROCESS cmd="set" data="rtp_hold_timeout_sec=1800"/>
```

### 2. SIP Profile 配置

编辑 `conf/sip_profiles/internal.xml`：

```xml
<param name="enable-timer" value="true"/>
<param name="session-timeout" value="1800"/>
<param name="minimum-session-expires" value="90"/>
<param name="rtp-timeout-sec" value="300"/>
<param name="rtp-hold-timeout-sec" value="1800"/>
```

### 3. 拨号计划中使用超时保护

编辑拨号计划文件：

```xml
<extension name="outbound_with_timeout">
  <condition field="destination_number" expression="^9(\d+)$">
    <action application="set" data="call_timeout=30"/>
    <action application="set" data="hangup_after_bridge=true"/>
    <action application="set" data="rtp_timeout_sec=300"/>
    
    <!-- 设置最大通话时长为1小时 -->
    <action application="sched_hangup" data="+3600 alloted_timeout"/>
    
    <action application="bridge" data="sofia/gateway/mygateway/$1"/>
  </condition>
</extension>
```

### 4. 重新加载配置

```bash
fs_cli -x "reloadxml"
fs_cli -x "sofia profile internal restart"
```

详细的配置说明请查看 `freeswitch_timeout_config.xml`。

## 手动排查步骤

### 1. 查看所有通道

```bash
fs_cli -x "show channels"
fs_cli -x "show channels verbose"
```

### 2. 查看特定通道详情

```bash
fs_cli -x "uuid_dump <UUID>"
```

### 3. 强制挂断通道

```bash
# 挂断单个通道
fs_cli -x "uuid_kill <UUID>"

# 挂断所有通道（谨慎使用！）
fs_cli -x "hupall"

# 挂断超过指定时长的通道（单位：秒）
fs_cli -x "hupall MANAGER_REQUEST 7200"
```

### 4. 查看日志

```bash
# 实时查看日志
tail -f /var/log/freeswitch/freeswitch.log

# 提高日志级别
fs_cli -x "fsctl loglevel DEBUG"

# 启用 SIP 跟踪
fs_cli -x "sofia profile internal siptrace on"
```

## 常见原因和解决方案

### 1. 应用程序卡住

**原因**：通话被 park、处于死循环等
**解决**：检查拨号计划，确保每个路径都有明确的结束点

### 2. 信令问题

**原因**：SIP BYE 消息未收到或处理失败
**解决**：
- 检查网络连接
- 启用 session timer
- 查看 SIP 跟踪日志

### 3. RTP 超时未配置

**原因**：媒体流中断但通道未释放
**解决**：设置 `rtp_timeout_sec` 和 `rtp_hold_timeout_sec`

### 4. 脚本错误

**原因**：ESL、Lua、Python 脚本未正确释放通道
**解决**：
- 使用 try-finally 确保资源释放
- 添加超时保护
- 完善错误处理

详细的原因分析和解决方案请查看 `freeswitch_lingering_channels_investigation.md`。

## 监控告警配置

### 邮件告警

编辑 `monitor_channels.sh`，设置：

```bash
ENABLE_EMAIL=true
EMAIL_TO="admin@example.com"
```

确保系统已安装并配置邮件工具（如 mailutils）。

### Webhook 告警

```bash
ENABLE_WEBHOOK=true
WEBHOOK_URL="https://hooks.example.com/alert"
```

### Slack 告警

```bash
ENABLE_SLACK=true
SLACK_WEBHOOK_URL="https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
```

## 最佳实践

1. **始终设置超时参数**：在所有外呼、桥接操作中设置适当的超时
2. **使用 hangup_after_bridge**：确保桥接结束后通道被释放
3. **启用 session timer**：防止 SIP 会话僵死
4. **定期监控**：建立自动化监控机制
5. **完善日志**：记录挂断原因便于排查
6. **定期清理**：自动清理超时通道
7. **容量规划**：根据业务量设置合理的 max-sessions 限制

## 性能指标

建议的监控指标：
- 当前通道数
- 峰值通道数
- 平均通话时长
- 长时间运行通道数（>1小时）
- 每日清理的残留通道数

监控脚本会生成 Prometheus 格式的指标文件：`/tmp/freeswitch_metrics.txt`

## 故障排查

如果问题持续存在：

1. 查看 FreeSWITCH 日志：`/var/log/freeswitch/freeswitch.log`
2. 启用调试日志：`fs_cli -x "fsctl loglevel DEBUG"`
3. 检查系统资源：`top`, `free -h`, `df -h`
4. 查看 FreeSWITCH 状态：`fs_cli -x "status"`
5. 检查 Sofia 状态：`fs_cli -x "sofia status"`
6. 运行诊断脚本生成完整报告

## 参考文档

- [详细问题调查报告](./freeswitch_lingering_channels_investigation.md)
- [配置示例文件](./freeswitch_timeout_config.xml)
- [FreeSWITCH 官方文档](https://freeswitch.org/confluence/)
- [Channel Variables](https://freeswitch.org/confluence/display/FREESWITCH/Channel+Variables)
- [Sofia SIP](https://freeswitch.org/confluence/display/FREESWITCH/Sofia+SIP+Stack)

## 贡献

欢迎提交 Issue 和 Pull Request！

## 许可

MIT License

## 联系方式

如有问题或建议，请提交 Issue。