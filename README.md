# Linux 自动日志清理工具

这是一个用于在Linux系统中每天凌晨自动清空指定目录日志文件的解决方案。

## 文件说明

- `clear_logs.sh` - 主要的日志清理脚本
- `setup_cron.sh` - 自动配置cron定时任务的脚本
- `crontab_example` - crontab配置示例文件
- `README.md` - 使用说明文档

## 快速开始

### 1. 配置日志目录

编辑 `clear_logs.sh` 文件，修改要清理的日志目录：

```bash
# 编辑脚本
vi clear_logs.sh

# 修改 LOG_DIRS 数组，添加您的日志目录路径
LOG_DIRS=(
    "/var/log/myapp"
    "/home/user/logs"
    "/opt/application/logs"
)
```

### 2. 设置定时任务（方法一：自动配置）

运行自动配置脚本：

```bash
sudo ./setup_cron.sh
```

按照提示选择执行时间即可。

### 3. 设置定时任务（方法二：手动配置）

手动编辑crontab：

```bash
# 编辑当前用户的crontab
crontab -e

# 添加以下行（每天凌晨2点执行）
0 2 * * * /workspace/clear_logs.sh >> /var/log/clear_logs_cron.log 2>&1
```

## 功能特性

### 主要功能
- ✅ 自动清空指定目录的日志文件
- ✅ 支持多个目录同时处理
- ✅ 支持多种文件类型匹配（*.log, *.txt, *.out, *.err）
- ✅ 保留文件结构，只清空内容
- ✅ 详细的执行日志记录

### 可选功能
- 📦 清空前自动备份（默认关闭）
- 🗑️ 自动清理过期备份
- 📝 完整的操作日志

## 配置选项

### 基本配置

```bash
# 日志目录列表
LOG_DIRS=(
    "/path/to/logs1"
    "/path/to/logs2"
)

# 要清理的文件类型
LOG_PATTERNS=(
    "*.log"
    "*.txt"
    "*.out"
)
```

### 备份配置

```bash
# 启用备份
ENABLE_BACKUP=true

# 备份保存目录
BACKUP_DIR="/backup/logs"

# 备份保留天数
BACKUP_RETENTION_DAYS=7
```

## 日志查看

### 查看脚本执行日志

```bash
# 实时查看日志
tail -f /var/log/clear_logs_script.log

# 查看最近100行
tail -n 100 /var/log/clear_logs_script.log
```

### 查看cron执行日志

```bash
# 查看cron日志
tail -f /var/log/clear_logs_cron.log
```

## Cron时间配置说明

```
# Cron表达式格式
分钟 小时 日期 月份 星期 命令

# 常用示例
0 0 * * *    # 每天凌晨0:00
0 2 * * *    # 每天凌晨2:00
30 3 * * *   # 每天凌晨3:30
0 1 * * 1    # 每周一凌晨1:00
0 4 1 * *    # 每月1号凌晨4:00
*/30 * * * * # 每30分钟执行一次
```

## 管理命令

### Crontab管理

```bash
# 查看当前定时任务
crontab -l

# 编辑定时任务
crontab -e

# 删除所有定时任务
crontab -r

# 查看cron服务状态
systemctl status cron
# 或
service cron status
```

### 手动执行清理

```bash
# 立即执行日志清理
sudo ./clear_logs.sh

# 测试运行（查看将要清理的文件）
find /your/log/dir -name "*.log" -type f
```

## 注意事项

1. **权限问题**
   - 确保脚本有执行权限：`chmod +x clear_logs.sh`
   - 如果清理系统日志，可能需要root权限
   - 使用 `sudo crontab -e` 配置root用户的定时任务

2. **路径配置**
   - 使用绝对路径，避免相对路径
   - 确保配置的目录存在

3. **安全建议**
   - 首次使用建议开启备份功能
   - 先在测试环境验证
   - 定期检查执行日志

4. **性能考虑**
   - 大量文件时可能需要较长时间
   - 建议在系统负载较低的凌晨执行

## 故障排查

### 定时任务没有执行

1. 检查cron服务是否运行：
```bash
systemctl status cron
```

2. 检查crontab配置：
```bash
crontab -l
```

3. 查看系统日志：
```bash
grep CRON /var/log/syslog
```

### 清理失败

1. 检查权限：
```bash
ls -la /path/to/logs
```

2. 检查脚本日志：
```bash
cat /var/log/clear_logs_script.log
```

3. 手动测试脚本：
```bash
bash -x ./clear_logs.sh
```

## 卸载

如需停止自动清理，执行以下步骤：

```bash
# 1. 删除cron任务
crontab -e
# 删除相关行

# 2. 删除脚本文件（可选）
rm -f /workspace/clear_logs.sh
rm -f /workspace/setup_cron.sh
```

## 支持

如有问题，请检查：
- 脚本执行日志：`/var/log/clear_logs_script.log`
- Cron执行日志：`/var/log/clear_logs_cron.log`
- 系统日志：`/var/log/syslog`

---

**版本**: 1.0.0  
**更新日期**: 2025-09-29