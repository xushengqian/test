# FreeSWITCH 通道检查和杀死工具

解决 `freeswitch show channels` 显示通道但 `uuid_kill` 报 "no such channel" 的问题。

## 问题描述

当执行 `freeswitch show channels` 可以看到通道列表，但执行 `uuid_kill <uuid>` 时却提示 "no such channel" 错误。这通常是因为：

1. 通道在查询和杀死之间已经关闭
2. 需要先验证通道是否存在再执行 kill 操作
3. UUID 格式或提取方式有问题

## 解决方案

本工具提供了两个脚本（Bash 和 Python），在杀死通道前会先验证通道是否存在，避免 "no such channel" 错误。

## 使用方法

### Bash 脚本

```bash
# 杀死指定 UUID 的通道
./check_and_kill_channel.sh -u <uuid>

# 列出所有活动通道
./check_and_kill_channel.sh -l

# 检查通道是否存在
./check_and_kill_channel.sh -c <uuid>

# 杀死所有活动通道（会提示确认）
./check_and_kill_channel.sh -a

# 直接使用 UUID（不带选项）
./check_and_kill_channel.sh <uuid>
```

### Python 脚本

```bash
# 杀死指定 UUID 的通道
python3 check_and_kill_channel.py -u <uuid>

# 列出所有活动通道
python3 check_and_kill_channel.py -l

# 检查通道是否存在
python3 check_and_kill_channel.py -c <uuid>

# 杀死所有活动通道（会提示确认）
python3 check_and_kill_channel.py -a

# 不验证直接杀死（不推荐）
python3 check_and_kill_channel.py -u <uuid> --no-verify

# 指定 fs_cli 路径
python3 check_and_kill_channel.py -u <uuid> --fs-cli /usr/local/freeswitch/bin/fs_cli
```

## 功能特性

- ✅ 在杀死前验证通道是否存在
- ✅ 显示通道详细信息
- ✅ 支持批量操作（杀死所有通道）
- ✅ 友好的错误提示和颜色输出
- ✅ 支持列出所有活动通道
- ✅ 支持检查通道是否存在

## 环境变量

可以通过环境变量 `FS_CLI` 指定 FreeSWITCH CLI 命令路径：

```bash
export FS_CLI=/usr/local/freeswitch/bin/fs_cli
./check_and_kill_channel.sh -u <uuid>
```

## 示例

```bash
# 1. 先列出所有通道
./check_and_kill_channel.sh -l

# 2. 检查特定通道是否存在
./check_and_kill_channel.sh -c abc123-def456-789

# 3. 安全地杀死通道
./check_and_kill_channel.sh -u abc123-def456-789
```

## 工作原理

1. 使用 `uuid_exists <uuid>` 命令检查通道是否存在
2. 如果存在，使用 `uuid_dump <uuid>` 显示通道信息
3. 最后执行 `uuid_kill <uuid>` 杀死通道
4. 验证操作结果，确保成功执行

这样可以避免在通道已经关闭的情况下尝试杀死它，从而避免 "no such channel" 错误。