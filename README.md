# FreeSWITCH 通道残留问题调查

## 问题描述
电话已挂断，但是 `show channels` 命令显示会话一直存在，通道没有被正确清理。

## 文件说明

### 1. 问题分析.md
详细分析了可能导致通道残留的原因，包括：
- 通道状态机卡住
- 事件监听器问题
- 应用程序层问题
- 媒体流问题
- 桥接问题
- 定时器未清理

### 2. diagnose_channels.sh
诊断脚本，用于：
- 检查所有通道的状态
- 分析通道详细信息
- 识别可能的僵尸通道
- 生成诊断报告

**使用方法：**
```bash
./diagnose_channels.sh
```

### 3. cleanup_zombie_channels.sh
自动清理脚本，用于：
- 检测超过指定时长的通道
- 自动清理僵尸通道
- 支持预览模式（不实际执行）

**使用方法：**
```bash
# 预览模式（默认，不会实际清理）
./cleanup_zombie_channels.sh

# 预览模式，自定义最大持续时间（秒）
./cleanup_zombie_channels.sh --duration 1800

# 实际执行清理
./cleanup_zombie_channels.sh --execute

# 实际执行清理，自定义最大持续时间
./cleanup_zombie_channels.sh --duration 1800 --execute
```

### 4. 配置建议.md
包含预防通道残留的配置建议：
- 拨号计划配置
- 配置文件优化
- 事件监听和监控
- 定时清理任务
- 应用程序层最佳实践

## 快速开始

### 步骤 1: 诊断问题
```bash
./diagnose_channels.sh
```

### 步骤 2: 查看分析结果
查看生成的日志文件，了解哪些通道可能是僵尸通道。

### 步骤 3: 清理僵尸通道（谨慎操作）
```bash
# 先预览
./cleanup_zombie_channels.sh --duration 3600

# 确认无误后执行
./cleanup_zombie_channels.sh --duration 3600 --execute
```

### 步骤 4: 应用预防措施
参考 `配置建议.md` 中的建议，修改你的 FreeSWITCH 配置和应用程序代码。

## 常见问题

### Q: 如何手动清理单个通道？
A: 在 fs_cli 中执行：
```bash
uuid_kill <channel-uuid>
# 或
uuid_break <channel-uuid> all
```

### Q: 如何查看通道详细信息？
A: 在 fs_cli 中执行：
```bash
show channels <uuid>
show channels as json
```

### Q: 如何监控通道挂断事件？
A: 在 fs_cli 中执行：
```bash
/event plain CHANNEL_HANGUP CHANNEL_DESTROY
```

### Q: 脚本找不到 fs_cli 怎么办？
A: 修改脚本中的 `FS_CLI` 变量，指向你的 fs_cli 实际路径。

## 注意事项

1. **清理通道前请谨慎**：确保通道确实是僵尸通道，避免误杀正常通话
2. **备份配置**：修改 FreeSWITCH 配置前请先备份
3. **测试环境**：建议先在测试环境验证脚本和配置
4. **监控日志**：清理后观察日志，确保系统正常运行

## 相关资源

- [FreeSWITCH 官方文档](https://freeswitch.org/confluence/)
- [FreeSWITCH Wiki](https://wiki.freeswitch.org/)
