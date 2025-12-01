# PCMToWAVConverter

PCM 到 WAV 音频格式转换器

## 功能特性

- 将原始 PCM 音频数据转换为标准 WAV 文件格式
- 支持自定义采样率、声道数和位深度
- 支持从文件或字节数据转换
- 简单易用的 Python API

## 使用方法

### 命令行使用

```bash
python PCMToWAVConverter.py <输入PCM文件> <输出WAV文件> [采样率] [声道数] [位深度]
```

示例：
```bash
python PCMToWAVConverter.py input.pcm output.wav 44100 2 16
```

### Python API 使用

```python
from PCMToWAVConverter import PCMToWAVConverter

# 创建转换器（默认参数：44100Hz, 2声道, 16位）
converter = PCMToWAVConverter(sample_rate=44100, channels=2, bits_per_sample=16)

# 从文件转换
converter.convert('input.pcm', 'output.wav')

# 从字节数据转换
pcm_data = b'...'  # PCM 音频数据
converter.convert_from_bytes(pcm_data, 'output.wav')
```

## 测试

运行测试脚本生成示例音频文件：

```bash
python test_pcm_converter.py
```

这将生成一个 440Hz 正弦波的测试音频文件。

## 参数说明

- **sample_rate**: 采样率（Hz），常见值：8000, 16000, 22050, 44100, 48000
- **channels**: 声道数，1=单声道，2=立体声
- **bits_per_sample**: 每样本位数，常见值：8, 16, 24, 32

## 文件结构

- `PCMToWAVConverter.py` - 主转换器类
- `test_pcm_converter.py` - 测试脚本