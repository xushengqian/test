# 快速开始指南

## 5分钟上手PCMToWAVConverter

### 第一步：查看示例

运行主程序生成一个440Hz的A4音符：

```bash
python3 pcm_to_wav_converter.py
```

这将创建一个名为 `example_output.wav` 的文件。

### 第二步：基本使用

```python
from pcm_to_wav_converter import PCMToWAVConverter

# 1. 创建转换器
converter = PCMToWAVConverter(
    sample_rate=16000,  # 采样率（Hz）
    channels=1,         # 1=单声道, 2=立体声
    bit_depth=16        # 8或16位
)

# 2. 转换PCM数据
pcm_data = b'...'  # 你的PCM原始数据
converter.convert(pcm_data, "output.wav")

# 或者从文件转换
converter.convert_file("input.pcm", "output.wav")
```

### 第三步：运行完整示例

查看所有功能演示：

```bash
python3 example_usage.py
```

这将生成：
- 静音音频
- C大调音阶（8个音符）
- 立体声示例
- 不同采样率的示例

### 第四步：运行测试

验证安装正确：

```bash
python3 test_converter.py
```

应该看到"🎉 所有测试通过！"

## 常见用例

### 用例1：录音数据转换

```python
# 从麦克风或其他设备获取的PCM数据
recording_data = get_audio_from_microphone()  # 你的录音函数

converter = PCMToWAVConverter(sample_rate=44100, channels=2, bit_depth=16)
converter.convert(recording_data, "recording.wav")
```

### 用例2：生成合成音频

```python
import math
import struct

# 生成500Hz正弦波，持续2秒
sample_rate = 48000
frequency = 500
duration = 2.0

samples = []
for i in range(int(sample_rate * duration)):
    sample = int(20000 * math.sin(2 * math.pi * frequency * i / sample_rate))
    samples.append(struct.pack('<h', sample))

pcm_data = b''.join(samples)

converter = PCMToWAVConverter(sample_rate=48000, channels=1, bit_depth=16)
converter.convert(pcm_data, "tone_500hz.wav")
```

### 用例3：批量转换

```python
import glob
import os

converter = PCMToWAVConverter(sample_rate=16000, channels=1, bit_depth=16)

# 转换目录中所有PCM文件
for pcm_file in glob.glob("recordings/*.pcm"):
    wav_file = pcm_file.replace(".pcm", ".wav")
    print(f"转换: {pcm_file} -> {wav_file}")
    converter.convert_file(pcm_file, wav_file)
```

## 参数说明

### sample_rate（采样率）
- **8000**: 电话质量
- **16000**: 宽带语音，适合语音识别
- **22050**: FM广播质量
- **44100**: CD质量，音乐标准
- **48000**: 专业音频/视频制作

### channels（声道数）
- **1**: 单声道（Mono）- 适合语音
- **2**: 立体声（Stereo）- 适合音乐

### bit_depth（位深度）
- **8**: 每个采样8位（0-255）
- **16**: 每个采样16位（-32768到32767）- 推荐

## 故障排除

### 问题：生成的WAV文件无法播放

**解决方案**：检查PCM数据格式是否正确
- 确保位深度匹配（8位或16位）
- 对于立体声，确保数据是交错的（左、右、左、右...）
- 对于16位数据，确保使用小端字节序

### 问题：文件大小不符合预期

**解决方案**：计算公式
```
文件大小 = 44字节（WAV头） + PCM数据大小
PCM数据大小 = 采样率 × 声道数 × (位深度/8) × 时长(秒)
```

示例：1秒，16kHz，单声道，16位
```
PCM大小 = 16000 × 1 × 2 × 1 = 32000字节
WAV大小 = 44 + 32000 = 32044字节
```

## 下一步

- 📖 阅读完整的 [README.md](README.md)
- 🔍 查看 [example_usage.py](example_usage.py) 了解更多示例
- 🧪 运行 [test_converter.py](test_converter.py) 验证功能
- 📝 查看 [CHANGELOG.md](CHANGELOG.md) 了解版本历史

## 需要帮助？

如果遇到问题：
1. 检查PCM数据格式是否正确
2. 验证参数设置（采样率、声道数、位深度）
3. 运行测试脚本确认安装正确
4. 查看示例代码寻找类似用例
