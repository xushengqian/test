# PCM to MP3 Converter

Java工具库：将PCM原始音频数据转换为MP3格式。

## 特性

- 纯Java实现，无需外部依赖（使用Jump3r，LAME的Java移植版本）
- 支持多种采样率（8000Hz, 16000Hz, 22050Hz, 44100Hz, 48000Hz等）
- 支持单声道和立体声
- 支持自定义MP3比特率（64kbps - 320kbps）
- 提供文件、流、字节数组多种转换方式
- Builder模式，灵活配置
- 支持批量转换

## 快速开始

### Maven依赖

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>pcm-to-mp3</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 基本用法

#### 1. 使用工具类（最简单）

```java
import com.example.audio.AudioUtils;

// 文件转换（使用默认参数：16000Hz, 单声道, 128kbps）
AudioUtils.pcmToMp3("input.pcm", "output.mp3");

// 指定采样率和声道数
AudioUtils.pcmToMp3("input.pcm", "output.mp3", 8000, 1);

// 指定采样率、声道数和比特率
AudioUtils.pcmToMp3("input.pcm", "output.mp3", 44100, 2, 192);

// 字节数组转换
byte[] pcmData = ...; // 您的PCM数据
byte[] mp3Data = AudioUtils.pcmToMp3(pcmData);
byte[] mp3Data = AudioUtils.pcmToMp3(pcmData, 16000, 1, 128);
```

#### 2. 使用转换器类（更灵活）

```java
import com.example.audio.PcmToMp3Converter;

// 使用Builder模式配置
PcmToMp3Converter converter = PcmToMp3Converter.builder()
        .sampleRate(16000)    // 采样率
        .channels(1)          // 声道数
        .sampleSizeInBits(16) // 位深度
        .bitrate(128)         // MP3比特率
        .quality(5)           // 编码质量 (0-9, 0最好)
        .build();

// 文件转换
converter.convert("input.pcm", "output.mp3");

// 字节数组转换
byte[] mp3Data = converter.convert(pcmData);

// 流转换
converter.convert(inputStream, outputStream);
```

#### 3. 批量转换

```java
// 将目录下所有.pcm文件转换为.mp3
AudioUtils.batchConvert("input_dir", "output_dir", 16000, 1);
```

## 常用参数参考

### 采样率

| 采样率 | 说明 |
|--------|------|
| 8000 Hz | 电话语音质量 |
| 16000 Hz | VoIP/语音识别常用 |
| 22050 Hz | FM广播质量 |
| 44100 Hz | CD音质 |
| 48000 Hz | 专业音频/视频 |

### MP3比特率

| 比特率 | 说明 |
|--------|------|
| 64 kbps | 低质量，适合语音 |
| 128 kbps | 标准质量 |
| 192 kbps | 高质量 |
| 256 kbps | 很高质量 |
| 320 kbps | 最高质量 |

### 声道

| 声道数 | 说明 |
|--------|------|
| 1 | 单声道（Mono） |
| 2 | 立体声（Stereo） |

## 命令行使用

```bash
# 编译
mvn clean package

# 运行演示
java -jar target/pcm-to-mp3-1.0.0.jar

# 转换文件
java -jar target/pcm-to-mp3-1.0.0.jar input.pcm output.mp3

# 带参数转换
java -jar target/pcm-to-mp3-1.0.0.jar input.pcm output.mp3 -r=8000 -c=1 -b=64
```

### 命令行参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `-r=<采样率>` | 设置采样率(Hz) | 16000 |
| `-c=<声道数>` | 设置声道数 | 1 |
| `-b=<比特率>` | 设置MP3比特率(kbps) | 128 |

## PCM格式说明

本工具支持的PCM格式：

- **编码**: PCM有符号整数（PCM_SIGNED）
- **位深度**: 16位
- **字节序**: 小端（Little Endian）

如果您的PCM数据是其他格式，需要先进行格式转换。

## 项目结构

```
pcm-to-mp3/
├── pom.xml
├── README.md
└── src/
    ├── main/java/com/example/audio/
    │   ├── PcmToMp3Converter.java  # 核心转换器
    │   ├── AudioUtils.java         # 工具类
    │   └── PcmToMp3Demo.java       # 演示程序
    └── test/java/com/example/audio/
        ├── PcmToMp3ConverterTest.java
        └── AudioUtilsTest.java
```

## 依赖

- **Jump3r** (de.sciss:jump3r:1.0.5) - 纯Java的LAME MP3编码器
- **SLF4J** - 日志框架
- **JUnit 4** - 单元测试

## 构建

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 打包（包含所有依赖）
mvn clean package

# 生成的JAR文件
# target/pcm-to-mp3-1.0.0.jar
```

## 示例代码

### 从麦克风录制PCM并转换为MP3

```java
// 假设您已经有了PCM数据（例如从AudioInputStream获取）
byte[] pcmData = recordFromMicrophone(); // 您的录音逻辑

// 转换为MP3
byte[] mp3Data = AudioUtils.pcmToMp3(pcmData, 44100, 1, 128);

// 保存到文件
Files.write(Paths.get("recording.mp3"), mp3Data);
```

### 实时流式转换

```java
PcmToMp3Converter converter = PcmToMp3Converter.builder()
        .sampleRate(16000)
        .channels(1)
        .bitrate(64)
        .build();

try (InputStream pcmInput = new FileInputStream("stream.pcm");
     OutputStream mp3Output = new FileOutputStream("stream.mp3")) {
    converter.convert(pcmInput, mp3Output);
}
```

## 许可证

MIT License
