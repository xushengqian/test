# FreeSWITCH 性能优化项目

一个全面的 FreeSWITCH 性能优化解决方案，包含配置优化、系统调优和监控工具。

## 🚀 特性

- **自动化部署**: 一键应用所有优化配置
- **智能配置**: 针对性能优化的配置文件
- **实时监控**: 专业的性能监控工具
- **基准测试**: 压力测试和性能评估
- **系统调优**: 操作系统级别的优化
- **中文支持**: 完整的中文文档和界面

## 📁 项目结构

```
freeswitch-performance-optimization/
├── README.md                           # 项目说明
├── freeswitch-performance-optimization.md  # 详细优化指南
├── deployment-guide.md                 # 部署实施指南
├── freeswitch-optimization-script.sh   # 自动优化脚本
├── freeswitch-benchmark.sh             # 性能基准测试
├── freeswitch-realtime-monitor.py     # 实时监控工具
└── freeswitch-config/                  # 优化配置文件
    └── conf/
        ├── switch.conf.xml             # 核心配置优化
        ├── vars.xml                    # 全局变量优化
        ├── autoload_configs/
        │   └── modules.conf.xml        # 模块配置优化
        └── sip_profiles/
            └── internal.xml            # SIP配置优化
```

## ⚡ 快速开始

### 1. 自动化优化部署

```bash
# 克隆或下载项目文件
# 运行自动优化脚本（需要root权限）
sudo chmod +x freeswitch-optimization-script.sh
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

# 重启FreeSWITCH服务
sudo systemctl restart freeswitch
```

### 3. 启动监控

```bash
# 启动实时性能监控
python3 freeswitch-realtime-monitor.py

# 运行性能基准测试
chmod +x freeswitch-benchmark.sh
./freeswitch-benchmark.sh
```

## 📊 性能监控界面

实时监控工具提供以下信息：
- CPU和内存使用率
- FreeSWITCH会话统计
- 系统负载和网络状态
- 智能告警和性能建议

```
============================================================
FreeSWITCH 实时性能监控 - 2024-01-15 14:30:25
============================================================

📊 系统资源:
   CPU 使用率:    45.2%
   内存使用率:    62.8%
   系统负载:      2.15

📞 FreeSWITCH 状态:
   运行状态:      ✅ 运行中
   当前会话:      12,543
   峰值会话:      18,920
   总会话数:      1,245,678
   会话速率:      85 cps
   最大会话:      30,000
   活动通道:      25,086
   注册用户:      5,432

✅ 系统运行正常

💡 性能建议:
   • 系统运行良好，继续监控
============================================================
```

## 🎯 优化重点

### 核心配置优化
- **会话限制**: 最大30,000并发会话
- **编解码器**: 优先选择低CPU占用的PCMU/PCMA
- **RTP优化**: 16384-32767端口范围
- **模块精简**: 只加载必需的功能模块

### 系统级调优
- **内核参数**: 网络缓冲区和连接跟踪优化
- **进程优先级**: 高优先级和CPU亲和性
- **文件描述符**: 提升到200万限制
- **磁盘I/O**: deadline调度器优化

### 性能监控
- **实时指标**: CPU、内存、网络使用率
- **FreeSWITCH状态**: 会话数、通道数、注册数
- **智能告警**: 自动检测性能瓶颈
- **历史分析**: 性能趋势跟踪

## 🏆 性能基准

| 配置级别 | 并发会话 | CPU要求 | 内存要求 | 网络带宽 |
|---------|---------|---------|----------|----------|
| 小型 | 1,000 | 4核 2.4GHz | 4GB | 100Mbps |
| 中型 | 5,000 | 8核 2.8GHz | 8GB | 1Gbps |
| 大型 | 10,000 | 16核 3.0GHz | 16GB | 1Gbps |
| 企业级 | 20,000+ | 32核 3.2GHz | 32GB | 10Gbps |

## 🔧 工具说明

### freeswitch-optimization-script.sh
系统级自动化优化脚本：
- 内核参数调优
- 用户限制优化
- 启动脚本创建
- 定时任务配置

### freeswitch-realtime-monitor.py
实时性能监控工具：
- 图形化监控界面
- 智能告警系统
- 性能建议
- 历史数据记录

### freeswitch-benchmark.sh
性能基准测试工具：
- SIPp压力测试
- 性能指标统计
- 详细结果分析
- 测试报告生成

## 📚 文档说明

- **freeswitch-performance-optimization.md**: 详细的性能优化指南和最佳实践
- **deployment-guide.md**: 完整的部署和实施步骤
- **配置文件**: 所有优化配置都有详细注释说明

## ⚠️ 注意事项

1. **生产环境**: 请在测试环境充分验证后再应用到生产环境
2. **备份配置**: 应用新配置前请备份原有配置文件
3. **硬件要求**: 确保硬件资源满足性能要求
4. **监控告警**: 建议配置监控告警系统

## 🤝 技术支持

如需技术支持，请提供：
- 硬件配置详情
- FreeSWITCH版本信息
- 系统版本和内核信息
- 性能测试结果
- 相关错误日志

## 📝 许可证

本项目采用 MIT 许可证 - 详见 LICENSE 文件

## 🌟 贡献指南

欢迎提交 Issue 和 Pull Request 来改进这个项目！

---

**让你的FreeSWITCH性能飞起来！** 🚁