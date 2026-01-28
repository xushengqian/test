package com.example.audio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * PCM转MP3演示程序
 *
 * @author example
 * @version 1.0.0
 */
public class PcmToMp3Demo {

    public static void main(String[] args) {
        System.out.println("=== PCM转MP3转换器演示 ===\n");

        // 检查命令行参数
        if (args.length >= 2) {
            // 命令行模式：转换指定文件
            convertFromCommandLine(args);
        } else {
            // 演示模式：生成测试PCM并转换
            runDemo();
        }
    }

    /**
     * 从命令行参数转换文件
     */
    private static void convertFromCommandLine(String[] args) {
        String pcmFile = args[0];
        String mp3File = args[1];

        // 默认参数
        int sampleRate = 16000;
        int channels = 1;
        int bitrate = 128;

        // 解析可选参数
        for (int i = 2; i < args.length; i++) {
            if (args[i].startsWith("-r=")) {
                sampleRate = Integer.parseInt(args[i].substring(3));
            } else if (args[i].startsWith("-c=")) {
                channels = Integer.parseInt(args[i].substring(3));
            } else if (args[i].startsWith("-b=")) {
                bitrate = Integer.parseInt(args[i].substring(3));
            }
        }

        System.out.println("转换参数:");
        System.out.println("  输入文件: " + pcmFile);
        System.out.println("  输出文件: " + mp3File);
        System.out.println("  采样率: " + sampleRate + " Hz");
        System.out.println("  声道数: " + channels);
        System.out.println("  比特率: " + bitrate + " kbps");
        System.out.println();

        try {
            PcmToMp3Converter converter = PcmToMp3Converter.builder()
                    .sampleRate(sampleRate)
                    .channels(channels)
                    .bitrate(bitrate)
                    .build();

            long startTime = System.currentTimeMillis();
            converter.convert(pcmFile, mp3File);
            long endTime = System.currentTimeMillis();

            File input = new File(pcmFile);
            File output = new File(mp3File);

            System.out.println("转换完成!");
            System.out.println("  输入大小: " + input.length() + " 字节");
            System.out.println("  输出大小: " + output.length() + " 字节");
            System.out.println("  压缩比: " + String.format("%.2f%%", (output.length() * 100.0 / input.length())));
            System.out.println("  耗时: " + (endTime - startTime) + " ms");

        } catch (IOException e) {
            System.err.println("转换失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 运行演示模式
     */
    private static void runDemo() {
        System.out.println("演示模式：生成测试音频并转换\n");

        try {
            // 1. 生成测试PCM数据（440Hz正弦波，1秒）
            System.out.println("1. 生成测试PCM数据（440Hz正弦波，1秒）");
            byte[] pcmData = AudioUtils.generateTestPcm();
            System.out.println("   PCM数据大小: " + pcmData.length + " 字节");

            // 保存PCM文件
            File pcmFile = new File("test_audio.pcm");
            try (FileOutputStream fos = new FileOutputStream(pcmFile)) {
                fos.write(pcmData);
            }
            System.out.println("   已保存: " + pcmFile.getAbsolutePath());

            // 2. 使用默认参数转换
            System.out.println("\n2. 使用默认参数转换 (16000Hz, 单声道, 128kbps)");
            File mp3File1 = new File("test_audio_default.mp3");

            long startTime = System.currentTimeMillis();
            AudioUtils.pcmToMp3(pcmFile.getPath(), mp3File1.getPath());
            long endTime = System.currentTimeMillis();

            System.out.println("   MP3文件大小: " + mp3File1.length() + " 字节");
            System.out.println("   压缩比: " + String.format("%.2f%%", (mp3File1.length() * 100.0 / pcmData.length)));
            System.out.println("   转换耗时: " + (endTime - startTime) + " ms");
            System.out.println("   已保存: " + mp3File1.getAbsolutePath());

            // 3. 使用自定义参数转换
            System.out.println("\n3. 使用自定义参数转换 (16000Hz, 单声道, 64kbps)");
            File mp3File2 = new File("test_audio_64kbps.mp3");

            PcmToMp3Converter converter = PcmToMp3Converter.builder()
                    .sampleRate(16000)
                    .channels(1)
                    .bitrate(64)
                    .build();

            startTime = System.currentTimeMillis();
            converter.convert(pcmFile, mp3File2);
            endTime = System.currentTimeMillis();

            System.out.println("   MP3文件大小: " + mp3File2.length() + " 字节");
            System.out.println("   压缩比: " + String.format("%.2f%%", (mp3File2.length() * 100.0 / pcmData.length)));
            System.out.println("   转换耗时: " + (endTime - startTime) + " ms");
            System.out.println("   已保存: " + mp3File2.getAbsolutePath());

            // 4. 内存中转换
            System.out.println("\n4. 内存中转换（字节数组）");
            startTime = System.currentTimeMillis();
            byte[] mp3Data = AudioUtils.pcmToMp3(pcmData);
            endTime = System.currentTimeMillis();

            System.out.println("   MP3数据大小: " + mp3Data.length + " 字节");
            System.out.println("   转换耗时: " + (endTime - startTime) + " ms");

            // 5. 打印使用说明
            System.out.println("\n=== 使用说明 ===\n");
            System.out.println("命令行用法:");
            System.out.println("  java -jar pcm-to-mp3.jar <输入PCM文件> <输出MP3文件> [选项]");
            System.out.println();
            System.out.println("选项:");
            System.out.println("  -r=<采样率>   设置采样率，默认16000 Hz");
            System.out.println("  -c=<声道数>   设置声道数，默认1（单声道）");
            System.out.println("  -b=<比特率>   设置MP3比特率，默认128 kbps");
            System.out.println();
            System.out.println("示例:");
            System.out.println("  java -jar pcm-to-mp3.jar input.pcm output.mp3");
            System.out.println("  java -jar pcm-to-mp3.jar input.pcm output.mp3 -r=8000 -c=1 -b=64");
            System.out.println("  java -jar pcm-to-mp3.jar input.pcm output.mp3 -r=44100 -c=2 -b=192");

            System.out.println("\n=== 演示完成 ===");

        } catch (IOException e) {
            System.err.println("演示失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
