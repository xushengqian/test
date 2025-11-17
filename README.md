# FreeSWITCH + MRCP 实时语音流处理系统

这个项目演示如何使用FreeSWITCH结合MRCP（Media Resource Control Protocol）获取和处理实时语音流。

## 功能特性

- ✅ FreeSWITCH与MRCP服务器集成
- ✅ 实时语音流捕获和处理
- ✅ 支持ASR（自动语音识别）
- ✅ 支持TTS（文本转语音）
- ✅ WebSocket实时流传输
- ✅ 音频格式转换（PCM/WAV）

## 系统架构

```
┌─────────────┐      MRCP       ┌──────────────┐
│ FreeSWITCH  │ ◄─────────────► │ MRCP Server  │
└──────┬──────┘                 └──────────────┘
       │
       │ RTP/Audio Stream
       │
       ▼
┌──────────────────────────────┐
│  实时语音流处理器             │
│  - 音频捕获                   │
│  - 格式转换                   │
│  - 流式传输                   │
└──────────────────────────────┘
```

## 技术栈

- **FreeSWITCH**: 开源电信软交换平台
- **MRCP**: 媒体资源控制协议
- **Python 3.8+**: 主要编程语言
- **asyncio**: 异步I/O处理
- **websockets**: WebSocket服务器
- **pydub**: 音频处理

## 快速开始

### 1. 安装依赖

```bash
# 安装FreeSWITCH
sudo apt-get update
sudo apt-get install -y freeswitch freeswitch-mod-unimrcp

# 安装Python依赖
pip install -r requirements.txt
```

### 2. 配置FreeSWITCH

将配置文件复制到FreeSWITCH配置目录：

```bash
cp config/freeswitch/mrcp_profiles.xml /etc/freeswitch/mrcp_profiles/
cp config/freeswitch/dialplan.xml /etc/freeswitch/dialplan/default/
```

### 3. 启动服务

```bash
# 启动FreeSWITCH
sudo systemctl start freeswitch

# 启动语音流处理服务
python src/stream_processor.py
```

### 4. 测试

```bash
# 拨打测试号码
fs_cli -x "originate user/1000 &echo"
```

## 项目结构

```
.
├── config/                      # 配置文件
│   ├── freeswitch/             # FreeSWITCH配置
│   │   ├── mrcp_profiles.xml   # MRCP配置文件
│   │   └── dialplan.xml        # 拨号计划
│   └── mrcp_config.ini         # MRCP客户端配置
├── src/                        # 源代码
│   ├── mrcp_client.py          # MRCP客户端
│   ├── stream_processor.py     # 语音流处理器
│   ├── audio_handler.py        # 音频处理模块
│   └── websocket_server.py     # WebSocket服务器
├── examples/                   # 示例代码
│   ├── basic_asr.py           # 基础ASR示例
│   └── realtime_stream.py     # 实时流示例
├── requirements.txt           # Python依赖
└── README.md                  # 项目文档
```

## 使用示例

### 实时ASR（语音识别）

```python
from src.mrcp_client import MRCPClient
from src.stream_processor import AudioStreamProcessor

async def recognize_speech():
    # 初始化MRCP客户端
    client = MRCPClient(host='localhost', port=1544)
    await client.connect()
    
    # 创建语音流处理器
    processor = AudioStreamProcessor()
    
    # 开始识别
    async for audio_chunk in processor.get_audio_stream():
        result = await client.recognize(audio_chunk)
        print(f"识别结果: {result}")
```

### 实时TTS（文本转语音）

```python
async def text_to_speech(text):
    client = MRCPClient(host='localhost', port=1544)
    await client.connect()
    
    # 生成语音流
    async for audio_chunk in client.synthesize(text):
        # 处理音频数据
        process_audio(audio_chunk)
```

## 配置说明

### MRCP服务器配置

编辑 `config/mrcp_config.ini`:

```ini
[MRCP]
server_ip = 127.0.0.1
server_port = 1544
protocol_version = 2
resource_location = speechrecog

[ASR]
engine = google
language = zh-CN
sample_rate = 16000

[TTS]
engine = google
voice = zh-CN-Standard-A
```

### FreeSWITCH MRCP配置

编辑 `config/freeswitch/mrcp_profiles.xml` 配置MRCP连接参数。

## API文档

### MRCPClient

```python
class MRCPClient:
    async def connect() -> None
    async def recognize(audio_data: bytes) -> str
    async def synthesize(text: str) -> AsyncIterator[bytes]
    async def disconnect() -> None
```

### AudioStreamProcessor

```python
class AudioStreamProcessor:
    async def get_audio_stream() -> AsyncIterator[bytes]
    def set_format(sample_rate: int, channels: int) -> None
    async def save_to_file(filename: str) -> None
```

## 性能优化

- 使用低延迟音频编解码器（如Opus）
- 启用RTP缓冲优化
- 配置适当的音频采样率（建议16kHz）
- 使用异步I/O处理提高并发性能

## 故障排除

### FreeSWITCH无法连接MRCP服务器

1. 检查MRCP服务器是否运行
2. 验证防火墙规则
3. 查看FreeSWITCH日志：`/var/log/freeswitch/freeswitch.log`

### 音频质量问题

1. 调整采样率配置
2. 检查网络延迟
3. 优化RTP缓冲区大小

## 开发和贡献

欢迎提交问题和拉取请求！

## 许可证

MIT License

## 联系方式

如有问题，请提交Issue。
