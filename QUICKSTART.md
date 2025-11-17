# 快速开始指南

本指南将帮助您快速设置和运行 FreeSWITCH + MRCP 实时语音流系统。

## 前置要求

1. **FreeSWITCH** 已安装并运行
2. **MRCP 服务器**（如 uniMRCP）已安装并运行
3. **Python 3.6+** 已安装
4. **root 权限**（用于配置 FreeSWITCH）

## 5 分钟快速设置

### 步骤 1: 安装配置

```bash
# 克隆或下载项目后，进入项目目录
cd /workspace

# 运行安装脚本（需要 root 权限）
sudo bash scripts/setup.sh
```

### 步骤 2: 配置 MRCP 服务器

确保 MRCP 服务器运行在 `127.0.0.1:1544`（或修改配置文件中的地址）。

### 步骤 3: 重启 FreeSWITCH

```bash
sudo systemctl restart freeswitch
# 或
sudo service freeswitch restart
```

### 步骤 4: 测试连接

```bash
# 运行连接测试
bash scripts/test_connection.sh
```

### 步骤 5: 测试功能

#### 方式 A: 通过 SIP 客户端

1. 使用 SIP 客户端（如 X-Lite、Zoiper）连接到 FreeSWITCH
2. 拨打以下号码：
   - `mrcp_stream` - 实时语音流处理
   - `mrcp_asr` - 语音识别
   - `mrcp_tts` - 语音合成

#### 方式 B: 使用 Python 客户端

```bash
cd python
python3 mrcp_client.py
```

#### 方式 C: 运行示例程序

```bash
python3 examples/example_usage.py
```

## 常见问题

### Q: MRCP 连接失败？

**A:** 检查以下几点：
1. MRCP 服务器是否运行：`netstat -tlnp | grep 1544`
2. 防火墙是否允许连接
3. 配置文件中的 IP 和端口是否正确

### Q: FreeSWITCH 无法加载 mod_mrcp？

**A:** 
1. 检查模块是否存在：`ls /usr/lib/freeswitch/mod/mod_mrcp.so`
2. 如果没有，需要编译安装：
   ```bash
   cd /usr/src/freeswitch
   make mod_mrcp-install
   ```
3. 在 `modules.conf.xml` 中添加：`<load module="mod_mrcp"/>`

### Q: 音频流没有声音？

**A:**
1. 检查 RTP 端口范围是否足够
2. 检查网络配置和防火墙
3. 查看 FreeSWITCH 日志：`fs_cli -x "console loglevel debug"`

### Q: 如何查看实时日志？

**A:**
```bash
# FreeSWITCH 控制台
fs_cli

# 在 fs_cli 中执行
console loglevel debug
```

## 下一步

- 阅读完整的 [README.md](README.md) 了解详细配置
- 查看 [examples/example_usage.py](examples/example_usage.py) 学习更多用法
- 根据需求自定义配置和代码

## 获取帮助

如果遇到问题：
1. 查看日志文件
2. 运行测试脚本：`bash scripts/test_connection.sh`
3. 检查 FreeSWITCH 和 MRCP 服务器状态
4. 参考项目文档和示例代码
