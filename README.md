# PCM to MP3 Converter (Java)

这是一个使用 Java 实现 PCM 音频格式转 MP3 格式的示例项目。
使用了 `jump3r` 库（LAME 的 Java 移植版）进行 MP3 编码。

## 项目结构

- `src/main/java/com/example/audio/PcmToMp3Converter.java`: 转换工具类
- `src/main/java/com/example/audio/Main.java`: 测试演示类，生成 PCM 数据并转换为 MP3
- `pom.xml`: Maven 配置文件

## 依赖

本项目依赖 `jump3r` 库：
```xml
<dependency>
    <groupId>de.sciss</groupId>
    <artifactId>jump3r</artifactId>
    <version>1.0.5</version>
</dependency>
```

## 运行方式

### 使用 Maven (推荐)

1. 编译并运行：
   ```bash
   mvn clean compile exec:java -Dexec.mainClass="com.example.audio.Main"
   ```

### 手动编译 (无 Maven 环境)

1. 下载依赖 `jump3r-1.0.5.jar` 到 `lib/` 目录。
2. 编译：
   ```bash
   mkdir -p bin
   javac -d bin -cp lib/jump3r-1.0.5.jar src/main/java/com/example/audio/*.java
   ```
3. 运行：
   ```bash
   java -cp bin:lib/jump3r-1.0.5.jar com.example.audio.Main
   ```

## 代码示例

```java
File pcmFile = new File("input.pcm");
File mp3File = new File("output.mp3");

// 参数：源文件, 目标文件, 采样率, 通道数, 比特率
PcmToMp3Converter.convertPcmToMp3(pcmFile, mp3File, 44100, 1, 128);
```

## 注意事项

- 输入的 PCM 文件应为 16-bit signed little-endian 格式（Java 默认音频处理格式）。
- 如果 PCM 参数（如采样率）不匹配，转换出来的音频可能会变快或变慢。
