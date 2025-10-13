# FreeSWITCH 性能优化实施指南

## 快速开始

### 1. 自动化部署优化
```bash
# 运行自动优化脚本（需要root权限）
sudo ./freeswitch-optimization-script.sh

# 重启系统以应用所有优化
sudo reboot
```

### 2. 应用配置文件
```bash
# 备份原有配置
sudo cp -r /usr/local/freeswitch/conf /usr/local/freeswitch/conf.backup

# 应用优化配置
sudo cp -r freeswitch-config/conf/* /usr/local/freeswitch/conf/

# 重启FreeSWITCH
sudo systemctl restart freeswitch
```

### 3. 启用优化启动
```bash
# 使用优化的启动脚本
sudo /usr/local/bin/freeswitch-optimized
```

## 性能监控

### 实时监控
```bash
# 启动实时性能监控（5秒间隔）
python3 freeswitch-realtime-monitor.py

# 自定义监控间隔（2秒间隔）
python3 freeswitch-realtime-monitor.py -i 2
```

### 基准测试
```bash
# 运行性能基准测试
./freeswitch-benchmark.sh

# 查看测试结果
cat /tmp/sipp_output.log
```

### 手动监控
```bash
# 查看性能状态
/usr/local/bin/freeswitch-monitor

# 查看监控日志
tail -f /var/log/freeswitch-monitor.log
```

## 配置说明

### 核心配置优化点

#### 1. switch.conf.xml
- **会话限制**: `max-sessions=30000` - 根据硬件调整
- **每秒会话**: `sessions-per-second=500` - 控制呼叫速率
- **RTP端口范围**: 16384-32767 - 优化端口使用
- **定时器**: 启用软件定时器提高精度

#### 2. vars.xml
- **编解码器优先级**: PCMU > PCMA > G722 - 低CPU占用优先
- **RTP超时**: 300秒 - 合理的超时设置
- **会话超时**: 1800秒 - 30分钟会话超时

#### 3. modules.conf.xml
- **精简模块**: 只加载必需模块
- **高效定时器**: 使用 `mod_timerfd`
- **禁用高耗模块**: 注释掉不需要的功能

#### 4. SIP配置
- **定时器优化**: 禁用会话定时器
- **媒体处理**: 优化RTP处理
- **认证简化**: 减少认证开销

## 系统级优化

### 内核参数
- **网络缓冲区**: 增大到134MB
- **UDP内存**: 优化UDP处理
- **连接跟踪**: 支持100万连接
- **文件描述符**: 提升到200万

### 进程优化
- **优先级**: 设置为-10（高优先级）
- **CPU亲和性**: 绑定到特定CPU核心
- **用户限制**: 提升文件和进程限制

### 磁盘I/O
- **调度器**: 使用deadline调度器
- **SSD优化**: 针对固态硬盘优化

## 性能基准

### 硬件要求
| 并发会话 | CPU | 内存 | 网络 |
|---------|-----|------|------|
| 1,000 | 4核 2.4GHz | 4GB | 100Mbps |
| 5,000 | 8核 2.8GHz | 8GB | 1Gbps |
| 10,000 | 16核 3.0GHz | 16GB | 1Gbps |
| 20,000+ | 32核 3.2GHz | 32GB | 10Gbps |

### 性能指标
- **CPU使用率**: < 80%
- **内存使用率**: < 85%
- **响应时间**: < 100ms
- **丢包率**: < 0.1%

## 故障排除

### 常见问题

#### 1. CPU使用率过高
```bash
# 检查编解码器使用情况
fs_cli -x "show codec"

# 优化措施
- 使用PCMU/PCMA而不是G.729
- 启用bypass_media
- 减少不必要的应用程序
```

#### 2. 内存使用过高
```bash
# 检查内存使用
fs_cli -x "status"

# 优化措施
- 定期重启FreeSWITCH
- 检查内存泄漏
- 调整会话限制
```

#### 3. 网络延迟
```bash
# 检查网络状况
ping -c 10 target_ip
traceroute target_ip

# 优化措施
- 检查网络配置
- 优化QoS设置
- 使用专用网络
```

#### 4. 数据库性能
```bash
# 检查数据库连接
fs_cli -x "show db"

# 优化措施
- 增加连接池大小
- 优化SQL查询
- 使用内存数据库
```

## 维护建议

### 日常维护
1. **每日**: 检查系统资源使用情况
2. **每周**: 运行性能基准测试
3. **每月**: 清理日志文件和临时文件
4. **每季度**: 更新软件版本和安全补丁

### 监控告警
- CPU使用率 > 80%
- 内存使用率 > 85%
- 并发会话 > 25,000
- 响应时间 > 200ms

### 自动化脚本
- **监控脚本**: 每5分钟执行
- **清理脚本**: 每日凌晨2点执行
- **备份脚本**: 每日备份配置文件

## 扩展优化

### 集群部署
- 使用负载均衡器
- 配置主备切换
- 实施分布式架构

### 云优化
- 使用SSD存储
- 启用SR-IOV
- 配置DPDK网络加速

### 安全优化
- 启用TLS加密
- 配置防火墙规则
- 实施访问控制

## 支持与帮助

如需技术支持，请提供以下信息：
1. 硬件配置详情
2. 系统版本信息
3. FreeSWITCH版本
4. 性能测试结果
5. 错误日志文件

执行以下命令收集诊断信息：
```bash
# 生成诊断报告
./freeswitch-realtime-monitor.py > diagnosis_report.txt
fs_cli -x "status" >> diagnosis_report.txt
uname -a >> diagnosis_report.txt
free -h >> diagnosis_report.txt
```