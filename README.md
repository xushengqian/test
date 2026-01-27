# PCM to MP3 Converter

Java库，用于将PCM音频流转换为MP3音频流。基于jump3r（LAME MP3编码器的纯Java实现），无需任何本地依赖。

## 特性

- **纯Java实现**：无需安装FFmpeg或其他本地库
- **多种转换方式**：
  - 字节数组转换
  - 输入/输出流转换
  - 文件转换
  - 增量流式编码
  - 实时流转换
- **灵活配置**：支持自定义采样率、声道数、位深度、比特率等参数
- **预设配置**：提供电话、语音、CD、专业等常用质量预设
- **线程安全**：实时转换器支持多线程输入

## 快速开始

### Maven依赖

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>pcm-to-mp3-converter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 基本使用

```java
import com.example.audio.converter.*;

// 方式1：字节数组转换
AudioConfig config = AudioConfig.voiceQuality();
PcmToMp3Converter converter = new PcmToMp3Converter(config);
byte[] mp3Data = converter.convert(pcmData);

// 方式2：流转换
try (InputStream pcmInput = new FileInputStream("audio.pcm");
     OutputStream mp3Output = new FileOutputStream("audio.mp3")) {
    converter.convert(pcmInput, mp3Output);
}

// 方式3：文件转换
converter.convertFile("input.pcm", "output.mp3");
```

## 详细使用说明

### 1. 音频配置

`AudioConfig` 类用于配置PCM输入参数和MP3输出参数：

```java
// 使用预设配置
AudioConfig config = AudioConfig.voiceQuality();  // 16kHz, 单声道, 128kbps

// 或自定义配置
AudioConfig config = new AudioConfig()
    .setSampleRate(44100)    // 采样率
    .setChannels(2)          // 声道数（1=单声道, 2=立体声）
    .setBitDepth(16)         // 位深度（8或16）
    .setBitRate(192)         // MP3比特率（kbps）
    .setQuality(5);          // 编码质量（0=最高, 9=最低）
```

可用的预设配置：
- `telephoneQuality()` - 8kHz, 单声道, 64kbps（电话质量）
- `voiceQuality()` - 16kHz, 单声道, 128kbps（语音质量）
- `cdQuality()` - 44.1kHz, 立体声, 320kbps（CD质量）
- `professionalQuality()` - 48kHz, 立体声, 320kbps（专业质量）

### 2. 基本转换器

`PcmToMp3Converter` 是主要的转换器类：

```java
AudioConfig config = AudioConfig.voiceQuality();
PcmToMp3Converter converter = new PcmToMp3Converter(config);

// 字节数组转换
byte[] mp3Data = converter.convert(pcmData);

// 流转换
converter.convert(inputStream, outputStream);

// 文件转换
converter.convertFile("input.pcm", "output.mp3");
```

### 3. 增量流式编码

使用 `StreamEncoder` 进行增量编码，适用于实时音频流：

```java
PcmToMp3Converter.StreamEncoder encoder = converter.createStreamEncoder();

try {
    while (hasMorePcmData()) {
        byte[] pcmChunk = getPcmChunk();
        byte[] mp3Chunk = encoder.encode(pcmChunk);
        
        if (mp3Chunk.length > 0) {
            sendMp3Chunk(mp3Chunk);
        }
    }
    
    // 获取剩余数据
    byte[] finalMp3 = encoder.flush();
    sendMp3Chunk(finalMp3);
} finally {
    encoder.close();
}
```

### 4. MP3编码输出流

`Mp3EncoderOutputStream` 包装一个输出流，自动将写入的PCM数据编码为MP3：

```java
try (OutputStream fileOut = new FileOutputStream("output.mp3");
     Mp3EncoderOutputStream mp3Out = new Mp3EncoderOutputStream(fileOut, config)) {
    
    // 直接写入PCM数据
    mp3Out.write(pcmData);
    
    // 或分块写入
    while (hasMoreData()) {
        mp3Out.write(getNextChunk());
    }
}
```

### 5. 实时流转换器

`RealtimePcmToMp3Converter` 专为实时音频流设计：

```java
RealtimePcmToMp3Converter converter = new RealtimePcmToMp3Converter(config, mp3Chunk -> {
    // 回调函数，处理编码后的MP3数据
    webSocket.sendBinary(mp3Chunk);
});

// 启动转换器
converter.start();

// 在音频数据到达时输入
audioSource.onData(pcmData -> {
    converter.feed(pcmData);
});

// 完成后停止
converter.stop();
```

## PCM数据格式要求

- **采样格式**：有符号整数（PCM_SIGNED）
- **字节序**：小端序（Little-Endian）
- **位深度**：8位或16位
- **声道数**：单声道(1)或立体声(2)

16位立体声数据的字节排列：
```
[左低字节][左高字节][右低字节][右高字节]...
```

## 编译和测试

```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 打包
mvn package
```

## 运行示例

```bash
mvn exec:java -Dexec.mainClass="com.example.audio.converter.Example"
```

## 依赖

- **jump3r 1.0.5** - 纯Java实现的LAME MP3编码器
- **SLF4J 2.0.9** - 日志框架
- **JUnit 5.10.1** - 测试框架（仅测试时需要）

## 许可证

MIT License
