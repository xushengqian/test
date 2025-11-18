# FreeSWITCH 通道检查和终止工具

## 问题描述

当执行 `freeswitch show channels` 显示有通道存在，但使用 `uuid_kill` 时出现 "no such channel" 错误。

## 解决方案

本项目提供了两个脚本来解决这个问题：

1. **Python 版本** (`kill_freeswitch_channel.py`) - 功能更强大，错误处理更完善
2. **Shell 版本** (`kill_freeswitch_channel.sh`) - 轻量级，无需 Python 依赖

## 功能特性

- ✅ 在终止前检查通道是否存在
- ✅ UUID 格式验证
- ✅ 批量终止所有通道
- ✅ 详细的错误提示
- ✅ 避免 "no such channel" 错误

## 使用方法

### Python 版本

```bash
# 安装依赖（如果需要）
chmod +x kill_freeswitch_channel.py

# 列出所有通道
./kill_freeswitch_channel.py --list

# 终止指定通道
./kill_freeswitch_channel.py --kill <UUID>

# 终止所有通道
./kill_freeswitch_channel.py --kill-all

# 检查通道是否存在
./kill_freeswitch_channel.py --check <UUID>
```

### Shell 版本

```bash
# 添加执行权限
chmod +x kill_freeswitch_channel.sh

# 列出所有通道
./kill_freeswitch_channel.sh --list

# 终止指定通道
./kill_freeswitch_channel.sh --kill <UUID>

# 终止所有通道
./kill_freeswitch_channel.sh --kill-all

# 检查通道是否存在
./kill_freeswitch_channel.sh --check <UUID>
```

## 工作原理

1. **通道检查**: 使用 `uuid_exists` 命令验证通道是否真实存在
2. **UUID 验证**: 验证 UUID 格式是否正确（36 字符，包含连字符）
3. **安全终止**: 只有在确认通道存在后才执行 `uuid_kill` 命令

这样可以避免在通道已经断开但仍在列表中显示时出现 "no such channel" 错误。

## 常见问题

**Q: 为什么会出现 "no such channel" 错误？**

A: 这通常发生在以下情况：
- 通道已经断开，但 `show channels` 输出还未更新
- UUID 格式不正确
- 通道正在断开过程中

**Q: 如何强制终止通道？**

A: 使用 `--force` 参数（Python 版本）或 `--force` 选项（Shell 版本）

## 注意事项

- 确保已安装 FreeSWITCH 且 `fs_cli` 命令可用
- 终止通道操作不可逆，请谨慎使用
- 建议先使用 `--check` 或 `--list` 确认通道状态