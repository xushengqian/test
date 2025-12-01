# PCM到WAV转换器

一个简单但功能完整的Python工具，用于将原始PCM音频数据转换为标准WAV格式文件。

## 功能特性

✓ 支持多种采样率（8kHz, 16kHz, 44.1kHz, 48kHz等）  
✓ 支持单声道和立体声  
✓ 支持8位和16位位深度  
✓ 可以直接转换字节数据或文件  
✓ 自动生成标准WAV文件头  
✓ 详细的转换信息输出  

## 📚 文档导航

- **[快速开始指南](QUICKSTART.md)** - 5分钟快速上手
- **[更新日志](CHANGELOG.md)** - 版本历史和更新记录

---

## 快速开始

### 基本用法

```python
from pcm_to_wav_converter import PCMToWAVConverter

# 创建转换器实例
converter = PCMToWAVConverter(
    sample_rate=16000,  # 采样率
    channels=1,         # 声道数（1=单声道，2=立体声）
    bit_depth=16        # 位深度（8或16）
)

# 方法1: 转换PCM字节数据
pcm_data = b'...'  # 你的PCM数据
converter.convert(pcm_data, "output.wav")

# 方法2: 转换PCM文件
converter.convert_file("input.pcm", "output.wav")
```

### 运行示例

```bash
# 运行主程序（生成一个440Hz的示例音频）
python3 pcm_to_wav_converter.py

# 运行所有示例
python3 example_usage.py
```

## 文件说明

- **pcm_to_wav_converter.py** - 核心转换器类
- **example_usage.py** - 详细的使用示例
- **requirements.txt** - 依赖列表（本项目仅使用标准库）

## 使用场景

1. **音频处理** - 将录音设备产生的原始PCM数据转换为可播放的WAV文件
2. **音频生成** - 程序化生成音频后保存为WAV格式
3. **格式转换** - 批量转换PCM文件为WAV格式
4. **音频分析** - 为分析工具准备标准格式的音频文件

## 示例

### 示例1: 生成音调

```python
import math
import struct
from pcm_to_wav_converter import PCMToWAVConverter

converter = PCMToWAVConverter(sample_rate=44100, channels=1, bit_depth=16)

# 生成440Hz的A4音符（1秒）
samples = []
for i in range(44100):
    sample = int(32767 * math.sin(2 * math.pi * 440 * i / 44100))
    samples.append(struct.pack('<h', sample))

pcm_data = b''.join(samples)
converter.convert(pcm_data, "A4_note.wav")
```

### 示例2: 转换立体声

```python
converter = PCMToWAVConverter(
    sample_rate=48000,
    channels=2,  # 立体声
    bit_depth=16
)

# PCM数据应该是交错的：左、右、左、右...
converter.convert(stereo_pcm_data, "stereo_output.wav")
```

### 示例3: 批量转换文件

```python
import glob

converter = PCMToWAVConverter(sample_rate=16000, channels=1, bit_depth=16)

for pcm_file in glob.glob("*.pcm"):
    wav_file = pcm_file.replace(".pcm", ".wav")
    converter.convert_file(pcm_file, wav_file)
```

## WAV文件格式说明

生成的WAV文件遵循标准RIFF WAV格式规范：

- **RIFF块** - 文件标识符
- **fmt块** - 音频格式信息（PCM编码）
- **data块** - 实际的音频采样数据

## 技术参数

### 支持的采样率
- 8000 Hz（电话质量）
- 16000 Hz（宽带语音）
- 22050 Hz（FM广播）
- 44100 Hz（CD质量）
- 48000 Hz（专业音频）
- 以及其他自定义采样率

### 支持的位深度
- 8位（0-255）
- 16位（-32768到32767）

### 支持的声道配置
- 单声道（Mono）
- 立体声（Stereo）

## 许可证

MIT License

## 贡献

欢迎提交问题和拉取请求！