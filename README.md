# FreeSWITCH 服务器异常诊断工具集

## 问题描述

服务器 `10-181-10-158` 在高呼叫量时出现 `-ERR DESTINATION_OUT_OF_ORDER` 错误，另一台服务器正常。

## 工具说明

### 1. 诊断脚本 (`diagnose_freeswitch.sh`)
全面诊断脚本，检查：
- FreeSWITCH 进程状态
- 文件描述符使用情况
- 内存和 CPU 使用
- 网络连接数
- 系统资源限制
- FreeSWITCH 内部状态
- 错误日志

**使用方法**:
```bash
./diagnose_freeswitch.sh
```

### 2. 服务器对比脚本 (`compare_servers.sh`)
对比异常服务器和正常服务器的配置和状态。

**使用方法**:
```bash
./compare_servers.sh 10-181-10-158 normal-server
```

### 3. 资源监控脚本 (`monitor_resources.sh`)
实时监控 FreeSWITCH 资源使用情况。

**使用方法**:
```bash
# 每 5 秒更新一次
./monitor_resources.sh 5

# 每 1 秒更新一次（高负载时）
./monitor_resources.sh 1
```

## 文档

- **问题分析.md**: 详细的问题分析和可能原因
- **快速排查指南.md**: 快速排查步骤和解决方案
- **对比检查清单.md**: 服务器对比检查清单（由 compare_servers.sh 生成）

## 快速开始

1. **立即诊断**:
   ```bash
   ./diagnose_freeswitch.sh
   ```

2. **查看快速排查指南**:
   ```bash
   cat 快速排查指南.md
   ```

3. **实时监控**:
   ```bash
   ./monitor_resources.sh 5
   ```

## 常见原因（按可能性排序）

1. ⭐⭐⭐⭐⭐ **文件描述符耗尽** - 最常见原因
2. ⭐⭐⭐⭐ **SIP 网关连接数限制**
3. ⭐⭐⭐ **FreeSWITCH 并发限制**
4. ⭐⭐ **内存不足**
5. ⭐⭐ **网络问题**

## 注意事项

- 诊断脚本需要 FreeSWITCH 正在运行
- 某些检查需要 root 权限或 FreeSWITCH 用户权限
- fs_cli 命令需要正确配置才能使用
- 建议在非高峰期运行完整诊断

## 许可证

本工具集用于诊断和解决 FreeSWITCH 服务器问题。