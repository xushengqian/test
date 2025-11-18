# FreeSWITCH 通道管理工具

这个工具用于解决 FreeSWITCH 中常见的问题：`show channels` 显示通道存在，但 `uuid_kill` 却提示 "no such channel"。

## 问题描述

在使用 FreeSWITCH 时，有时会遇到以下情况：

1. 执行 `show channels` 可以看到通道列表
2. 但执行 `uuid_kill <UUID>` 却提示 `-ERR no such channel!`

这通常是由于：
- 通道状态异常（僵尸通道）
- 通道在两个命令之间已经关闭
- UUID 复制时包含了额外的空格或不可见字符
- 通道处于特殊状态，无法通过常规方法终止

## 解决方案

本工具提供了一个 Python 脚本 `freeswitch_channel_killer.py`，它可以：

✓ 列出所有活动通道并验证其有效性  
✓ 在终止前检查通道是否真实存在  
✓ 尝试多种方法强制终止顽固的通道  
✓ 批量终止所有通道  

## 安装要求

- Python 3.6+
- FreeSWITCH 已安装并运行
- `fs_cli` 命令可用（通常随 FreeSWITCH 一起安装）
- 运行脚本的用户需要有权限执行 `fs_cli` 命令

## 使用方法

### 1. 列出所有通道并检查有效性

```bash
python3 freeswitch_channel_killer.py --list
```

输出示例：
```
找到 2 个通道:

1. UUID: 12345678-1234-1234-1234-123456789abc
   状态: ✓ 有效
   详情: ...

2. UUID: 87654321-4321-4321-4321-cba987654321
   状态: ✗ 无效
   详情: ...
```

### 2. 检查特定通道是否存在

```bash
python3 freeswitch_channel_killer.py --check <UUID>
```

示例：
```bash
python3 freeswitch_channel_killer.py --check 12345678-1234-1234-1234-123456789abc
```

### 3. 终止指定通道

```bash
python3 freeswitch_channel_killer.py --kill <UUID>
```

示例：
```bash
python3 freeswitch_channel_killer.py --kill 12345678-1234-1234-1234-123456789abc
```

### 4. 强制终止通道（推荐）

当普通的 `uuid_kill` 失败时，使用强制终止方法：

```bash
python3 freeswitch_channel_killer.py --force-kill <UUID>
```

强制终止会尝试以下方法：
1. `uuid_kill` - 标准终止
2. `uuid_break` - 中断通道媒体
3. `uuid_park` + `uuid_kill` - 先停泊再终止

### 5. 终止所有通道

```bash
python3 freeswitch_channel_killer.py --kill-all
```

⚠️ **警告**: 这会终止所有活动通道，请谨慎使用！

### 6. 指定 fs_cli 路径

如果 `fs_cli` 不在 PATH 中，可以指定完整路径：

```bash
python3 freeswitch_channel_killer.py --list --fs-cli-path /usr/local/freeswitch/bin/fs_cli
```

## 使用场景

### 场景 1: 清理僵尸通道

```bash
# 1. 列出所有通道，查看哪些是无效的
python3 freeswitch_channel_killer.py --list

# 2. 强制终止无效通道
python3 freeswitch_channel_killer.py --force-kill <UUID>
```

### 场景 2: 批量清理

```bash
# 终止所有通道（重启服务前的清理）
python3 freeswitch_channel_killer.py --kill-all
```

### 场景 3: 调试通道问题

```bash
# 先检查通道是否存在
python3 freeswitch_channel_killer.py --check <UUID>

# 如果存在但无法通过 uuid_kill 终止，使用强制终止
python3 freeswitch_channel_killer.py --force-kill <UUID>
```

## 命令行参数

| 参数 | 说明 |
|------|------|
| `--list` | 列出所有活动通道并验证有效性 |
| `--check <UUID>` | 检查指定通道是否存在 |
| `--kill <UUID>` | 终止指定通道 |
| `--force-kill <UUID>` | 强制终止通道（尝试多种方法） |
| `--kill-all` | 终止所有活动通道 |
| `--fs-cli-path <PATH>` | 指定 fs_cli 的路径 |

## 工作原理

脚本通过以下方式解决 "no such channel" 问题：

1. **双重验证**: 先用 `uuid_exists` 验证通道是否真实存在，再执行终止操作
2. **多重方法**: 如果 `uuid_kill` 失败，会尝试 `uuid_break` 和 `uuid_park` 等其他方法
3. **UUID 清理**: 自动清理 UUID 中的空格和不可见字符
4. **状态检查**: 在操作前后都会验证通道状态

## 故障排除

### 问题: 找不到 fs_cli 命令

**解决方案**: 
- 确保 FreeSWITCH 已正确安装
- 将 fs_cli 添加到 PATH: `export PATH=$PATH:/usr/local/freeswitch/bin`
- 或使用 `--fs-cli-path` 参数指定完整路径

### 问题: 权限被拒绝

**解决方案**:
- 确保当前用户有权限执行 fs_cli
- 可能需要添加用户到 freeswitch 组: `sudo usermod -a -G freeswitch $USER`
- 或使用 sudo 运行: `sudo python3 freeswitch_channel_killer.py --list`

### 问题: 通道仍然无法终止

**解决方案**:
- 尝试重启 FreeSWITCH 服务
- 检查 FreeSWITCH 日志: `/var/log/freeswitch/freeswitch.log`
- 确认通道不是由外部系统（如 SIP 网关）保持活动

## 许可证

MIT License

## 贡献

欢迎提交 Issue 和 Pull Request！
