# FreeSWITCH + MRCP 实时语音流解决方案

本项目实现了 FreeSWITCH 结合 MRCP 获取实时语音流的完整解决方案。

## 功能特性

- ✅ FreeSWITCH MRCP 模块配置
- ✅ 实时语音流获取和处理
- ✅ MRCP ASR (语音识别) 集成
- ✅ MRCP TTS (语音合成) 集成
- ✅ Python 客户端实现
- ✅ Lua 脚本处理

## 项目结构

```
.
├── freeswitch/
│   ├── mrcp.conf.xml          # FreeSWITCH MRCP 配置文件
│   └── dialplan/
│       └── mrcp_stream.xml    # Dialplan 配置
├── lua/
│   └── mrcp_realtime_stream.lua  # 实时语音流处理脚本
├── python/
│   ├── mrcp_client.py         # MRCP 客户端实现
│   ├── freeswitch_esl_client.py # FreeSWITCH ESL 客户端
│   └── requirements.txt       # Python 依赖
├── examples/
│   └── example_usage.py       # 使用示例代码
├── scripts/
│   ├── setup.sh               # 安装配置脚本
│   └── test_connection.sh     # 连接测试脚本
└── README.md                  # 本文档
```

## 安装和配置

### 1. FreeSWITCH 配置

#### 安装 MRCP 模块

确保 FreeSWITCH 已安装 `mod_mrcp` 模块：

```bash
# 在 FreeSWITCH 源码目录
cd /usr/src/freeswitch
make mod_mrcp-install
```

#### 配置 MRCP

1. 将 `freeswitch/mrcp.conf.xml` 复制到 FreeSWITCH 配置目录：
```bash
cp freeswitch/mrcp.conf.xml /etc/freeswitch/autoload_configs/
```

2. 将 Dialplan 配置复制到相应目录：
```bash
cp freeswitch/dialplan/mrcp_stream.xml /etc/freeswitch/dialplan/
```

3. 将 Lua 脚本复制到脚本目录：
```bash
cp lua/mrcp_realtime_stream.lua /usr/share/freeswitch/scripts/
```

4. 在 `modules.conf.xml` 中启用 MRCP 模块：
```xml
<load module="mod_mrcp"/>
```

### 2. MRCP 服务器配置

确保已安装并运行 MRCP 服务器（如 uniMRCP）。配置服务器监听在 `127.0.0.1:1544`。

### 3. Python 环境

```bash
# 安装 Python 依赖（如果需要）
pip install -r python/requirements.txt
```

### 4. 快速安装（使用脚本）

```bash
# 使用安装脚本自动配置
sudo bash scripts/setup.sh

# 测试连接
bash scripts/test_connection.sh
```

## 使用方法

### 方法一：通过 FreeSWITCH Dialplan

1. 拨打配置的号码：
   - `mrcp_stream` - 实时语音流处理
   - `mrcp_asr` - MRCP ASR 实时识别
   - `mrcp_tts` - MRCP TTS 实时合成

2. 通过 ESL 或 SIP 客户端连接并拨打相应号码。

### 方法二：使用 Python 客户端

```python
from python.mrcp_client import RealtimeVoiceStreamProcessor

# 创建处理器
processor = RealtimeVoiceStreamProcessor(
    rtp_ip="127.0.0.1",
    rtp_port=16384,
    mrcp_ip="127.0.0.1",
    mrcp_port=1544
)

# 启动处理
processor.start()

# 处理会自动进行，直到调用 stop()
# processor.stop()
```

运行示例：

```bash
cd python
python3 mrcp_client.py
```

### 方法三：使用 ESL 客户端

```python
from python.freeswitch_esl_client import MRCPStreamController

# 创建流控制器
controller = MRCPStreamController(
    esl_host="127.0.0.1",
    esl_port=8021,
    mrcp_ip="127.0.0.1",
    mrcp_port=1544
)

# 启动流
controller.start_stream("1000")  # 目标号码

# 获取状态
status = controller.get_stream_status()

# 停止流
controller.stop_stream()
```

### 方法四：运行示例程序

```bash
# 运行交互式示例程序
python3 examples/example_usage.py
```

示例程序包含：
- 基本 MRCP 客户端使用
- RTP 客户端使用
- 实时语音流处理器
- ESL 流控制器
- 自定义音频处理

## 配置说明

### MRCP 配置参数

- `server-ip`: MRCP 服务器 IP 地址
- `server-port`: MRCP 服务器端口（默认 1544）
- `codec`: 音频编码格式（PCMU, PCMA 等）
- `sample-rate`: 采样率（8000, 16000 等）

### RTP 配置参数

- `rtp-ip`: RTP IP 地址（auto 表示自动）
- `rtp-port-min`: RTP 端口范围最小值
- `rtp-port-max`: RTP 端口范围最大值

## 工作原理

1. **语音流获取**：FreeSWITCH 通过 RTP 接收实时语音流
2. **数据缓冲**：将音频数据缓冲到一定大小（如 400ms）
3. **MRCP 处理**：将缓冲的音频数据发送到 MRCP 服务器进行处理
4. **结果返回**：MRCP 处理结果返回给应用

## 实时语音流处理流程

```
[语音输入] 
    ↓
[FreeSWITCH RTP 接收]
    ↓
[音频数据缓冲] (400ms chunks)
    ↓
[MRCP 客户端发送]
    ↓
[MRCP 服务器处理] (ASR/TTS)
    ↓
[处理结果返回]
    ↓
[应用层处理]
```

## 注意事项

1. **网络配置**：确保 FreeSWITCH、MRCP 服务器和客户端之间的网络连通
2. **端口配置**：确保 RTP 和 MRCP 端口未被占用
3. **编码格式**：确保音频编码格式与 MRCP 服务器兼容
4. **性能优化**：根据实际需求调整缓冲区大小和处理频率

## 故障排查

### MRCP 连接失败
- 检查 MRCP 服务器是否运行
- 检查 IP 和端口配置是否正确
- 查看 FreeSWITCH 日志：`fs_cli -x "console loglevel debug"`

### 音频流中断
- 检查 RTP 端口范围是否足够
- 检查网络带宽和延迟
- 查看 RTP 统计：`fs_cli -x "rtp status"`

### 处理延迟过高
- 减小缓冲区大小
- 优化网络配置
- 检查 MRCP 服务器性能

## 扩展开发

### 自定义音频处理

修改 `lua/mrcp_realtime_stream.lua` 中的 `process_audio_chunk` 函数来实现自定义处理逻辑。

### 添加新的 MRCP 功能

在 `python/mrcp_client.py` 中添加新的 MRCP 协议处理方法。

### 使用 ESL 进行高级控制

`python/freeswitch_esl_client.py` 提供了完整的 ESL 客户端实现，可以：
- 监听 FreeSWITCH 事件
- 动态控制呼叫
- 获取 RTP 信息
- 管理会话生命周期

## 测试和调试

### 连接测试

```bash
# 运行连接测试脚本
bash scripts/test_connection.sh
```

测试脚本会检查：
- FreeSWITCH ESL 连接
- MRCP 服务器连接
- RTP 端口配置
- 模块加载状态
- 配置文件存在性

### 日志查看

```bash
# FreeSWITCH 控制台日志
fs_cli -x "console loglevel debug"

# 查看特定会话日志
fs_cli -x "uuid_dump <session-uuid>"

# RTP 状态
fs_cli -x "rtp status"
```

## 参考资料

- [FreeSWITCH 官方文档](https://freeswitch.org/confluence/)
- [MRCP 协议规范](https://tools.ietf.org/html/rfc4463)
- [uniMRCP 文档](https://www.unimrcp.org/)

## 许可证

本项目仅供学习和参考使用。
