# Linux 每天凌晨清空日志内容 - 使用说明

## 概述
本解决方案提供了一个自动化的日志清理系统，每天凌晨0点自动清空指定目录中的日志文件内容（保留文件但清空内容）。

## 文件说明

### 1. `clear_logs.sh` - 主要清理脚本
- **功能**: 清空指定目录中的日志文件内容
- **特点**: 保留文件结构，只清空内容，不删除文件
- **日志**: 所有操作记录在 `/workspace/log_cleanup.log`

### 2. `log_cleanup_config.conf` - 配置文件
- **功能**: 配置需要清理的目录和文件模式
- **可自定义**: 根据实际需求修改目录和文件模式

## 当前配置

### Crontab 定时任务
```bash
0 0 * * * sudo /workspace/clear_logs.sh
```
- **执行时间**: 每天凌晨0点0分
- **权限**: 使用sudo执行（可清理系统日志）

### 默认清理目录
- `/var/log` - 系统日志目录
- `/workspace/logs` - 工作空间日志目录  
- `/tmp/logs` - 临时日志目录

### 默认清理文件模式
- `*.log` - 标准日志文件
- `*.out` - 输出文件
- `*.err` - 错误文件
- `access_log*` - Web服务器访问日志
- `error_log*` - Web服务器错误日志

## 使用方法

### 1. 查看当前定时任务
```bash
crontab -l
```

### 2. 手动执行清理（测试）
```bash
sudo /workspace/clear_logs.sh
```

### 3. 查看清理日志
```bash
cat /workspace/log_cleanup.log
```

### 4. 自定义配置
编辑 `clear_logs.sh` 文件中的以下部分：
```bash
LOG_DIRECTORIES=(
    "/your/custom/log/directory"
    # 添加更多目录...
)

LOG_PATTERNS=(
    "your_app*.log"
    # 添加更多模式...
)
```

## 管理命令

### 停用定时任务
```bash
crontab -r
```

### 修改执行时间
```bash
# 编辑crontab
crontab -e

# 例如：每天凌晨2点执行
0 2 * * * sudo /workspace/clear_logs.sh
```

### 查看cron服务状态
```bash
sudo service cron status
```

### 重启cron服务
```bash
sudo service cron restart
```

## 注意事项

1. **权限要求**: 清理系统日志需要sudo权限
2. **文件保留**: 脚本只清空文件内容，不删除文件本身
3. **日志记录**: 所有操作都会记录在清理日志中
4. **安全性**: 建议在生产环境使用前充分测试

## 故障排除

### 1. 权限问题
如果遇到权限错误，确保：
- 脚本有执行权限：`chmod +x /workspace/clear_logs.sh`
- crontab中使用了sudo：`0 0 * * * sudo /workspace/clear_logs.sh`

### 2. cron服务未运行
```bash
sudo service cron start
sudo service cron enable  # 开机自启
```

### 3. 查看cron日志
```bash
sudo tail -f /var/log/syslog | grep CRON
```

## 自定义示例

### 只清理应用日志
```bash
LOG_DIRECTORIES=(
    "/home/user/myapp/logs"
    "/opt/myapp/logs"
)

LOG_PATTERNS=(
    "application*.log"
    "debug*.log"
)
```

### 每周执行一次
```bash
# 每周日凌晨执行
0 0 * * 0 sudo /workspace/clear_logs.sh
```

---
**创建时间**: $(date)  
**版本**: 1.0  
**作者**: AI Assistant