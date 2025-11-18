# FreeSWITCH 通道残留问题调查

## 问题描述

电话已挂断，但 `show channels` 命令显示会话一直存在，通道没有被正确清理。

## 快速开始

### 1. 诊断问题

```bash
# 运行诊断脚本
./diagnose_channels.sh

# 如果 fs_cli 不在 PATH 中
FS_CLI=/usr/local/freeswitch/bin/fs_cli ./diagnose_channels.sh
```

### 2. 清理残留通道

```bash
# 先进行干运行（不实际删除）
DRY_RUN=true ./cleanup_channels.sh

# 确认后执行实际清理
./cleanup_channels.sh
```

### 3. 自动清理（Lua 脚本）

将 `auto_cleanup_channels.lua` 放到 FreeSWITCH 的 scripts 目录，然后：

```bash
fs_cli -x "luarun auto_cleanup_channels.lua cleanup"
```

## 文件说明

- `diagnose_channels.sh` - 通道诊断脚本，检查残留通道并分析原因
- `cleanup_channels.sh` - 通道清理脚本，强制挂断残留通道
- `auto_cleanup_channels.lua` - Lua 自动清理脚本，可集成到 FreeSWITCH
- `CHANNEL_CLEANUP_GUIDE.md` - 详细的问题分析和解决方案文档

## 详细文档

请查看 [CHANNEL_CLEANUP_GUIDE.md](./CHANNEL_CLEANUP_GUIDE.md) 获取完整的问题分析、解决方案和预防措施。